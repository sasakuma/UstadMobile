package com.ustadmobile.test.sharedse.network;

/**
 * Created by mike on 3/11/18.
 */

import com.ustadmobile.core.controller.CatalogPresenter;
import com.ustadmobile.core.db.DbManager;
import com.ustadmobile.core.db.UmLiveData;
import com.ustadmobile.core.db.UmObserver;
import com.ustadmobile.core.fs.db.ContainerFileHelper;
import com.ustadmobile.core.impl.UMLog;
import com.ustadmobile.core.impl.UmCallback;
import com.ustadmobile.core.impl.UstadMobileSystemImpl;
import com.ustadmobile.core.networkmanager.NetworkTask;
import com.ustadmobile.core.util.UMFileUtil;
import com.ustadmobile.lib.db.entities.CrawlJob;
import com.ustadmobile.lib.db.entities.DownloadJob;
import com.ustadmobile.lib.db.entities.DownloadSet;
import com.ustadmobile.lib.db.entities.OpdsEntryStatusCache;
import com.ustadmobile.port.sharedse.networkmanager.DownloadTask;
import com.ustadmobile.port.sharedse.networkmanager.NetworkManager;
import com.ustadmobile.test.core.ResourcesHttpdTestServer;
import com.ustadmobile.test.core.annotation.UmMediumTest;
import com.ustadmobile.test.core.impl.PlatformTestUtil;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the download task that run without requiring a network peer to download entries from
 *
 * Test naming as per https://www.youtube.com/watch?v=wYMIadv9iF8
 */
@UmMediumTest
public class TestDownloadTaskStandalone extends TestWithNetworkService {

    public static final String CRAWL_ROOT_ENTRY_ID = "http://umcloud1.ustadmobile.com/opds/test-crawl";

    public static final String CRAWL_ROOT_ENTRY_ID_SLOW = "http://umcloud1.ustadmobile.com/opds/test-crawl-slow";

    public static final String OPDS_PATH_SPEED_LIMITED = "com/ustadmobile/test/sharedse/crawlme-slow/index.opds";

    /**
     *
     */
    public static final int JOB_CHANGE_WAIT_TIME = 1000;

    @BeforeClass
    public static void startHttpResourcesServer() throws IOException{
        ResourcesHttpdTestServer.startServer();
    }

    @AfterClass
    public static void stopHttpResourcesServer() throws IOException {
        ResourcesHttpdTestServer.stopServer();
    }


    public static CrawlJob startDownloadJob(String opdsPath, String rootEntryId, boolean autoStart,
                                     boolean allowMeteredNetworks) {
        String storageDir = UstadMobileSystemImpl.getInstance().getStorageDirs(
                CatalogPresenter.SHARED_RESOURCE, PlatformTestUtil.getTargetContext())[0].getDirURI();
        DbManager dbManager = DbManager.getInstance(PlatformTestUtil.getTargetContext());

        List<String> childEntries = dbManager.getOpdsEntryWithRelationsDao()
                .findAllChildEntryIdsRecursive(rootEntryId);
        for(String entryId : childEntries) {
            ContainerFileHelper.getInstance().deleteAllContainerFilesByEntryId(PlatformTestUtil.getTargetContext(),
                    entryId);
        }

        CrawlJob crawlJob = new CrawlJob();
        crawlJob.setRootEntryUri(opdsPath);
        crawlJob.setQueueDownloadJobOnDone(autoStart);
        DownloadSet downloadSet = new DownloadSet();
        downloadSet.setDestinationDir(storageDir);

        crawlJob = UstadMobileSystemImpl.getInstance().getNetworkManager().prepareDownload(downloadSet,
                crawlJob, allowMeteredNetworks);

        return crawlJob;
    }

    public CrawlJob startDownloadJob(String opdsPath, String rootEntryId, boolean autoStart) {
        return startDownloadJob(opdsPath, rootEntryId, autoStart, false);
    }

    public static void waitForDownloadStatus(int downloadJobId, int status, int timeout) {
        CountDownLatch downloadJobLatch = new CountDownLatch(1);
        UmLiveData<DownloadJob> downloadJobLiveData = DbManager.getInstance(PlatformTestUtil
                .getTargetContext()).getDownloadJobDao()
                .getByIdLive(downloadJobId);

        UmObserver<DownloadJob> downloadJobObserver = (downloadJobLiveDataUpdate) -> {
            if (downloadJobLiveDataUpdate.getStatus() == status) {
                downloadJobLatch.countDown();
            }
        };
        downloadJobLiveData.observeForever(downloadJobObserver);

        try { downloadJobLatch.await(timeout, TimeUnit.MILLISECONDS); }
        catch(InterruptedException e) {}
    }

    @Test
    public void givenDownloadStarted_whenCancelled_thenShouldBeDeleted() {
        CrawlJob crawlJob = startDownloadJob(UMFileUtil.joinPaths(
                ResourcesHttpdTestServer.getHttpRoot(), "com/ustadmobile/test/sharedse/crawlme-slow/index.opds"),
                CRAWL_ROOT_ENTRY_ID_SLOW, true, true);

        DbManager dbManager = DbManager.getInstance(PlatformTestUtil.getTargetContext());

        UmLiveData<OpdsEntryStatusCache> statusCacheLive = DbManager
                .getInstance(PlatformTestUtil.getTargetContext())
                .getOpdsEntryStatusCacheDao().findByEntryIdLive(CRAWL_ROOT_ENTRY_ID_SLOW);
        CountDownLatch latch = new CountDownLatch(1);
        UmObserver<OpdsEntryStatusCache> observer = (statusCache) -> {
            if(statusCache.getContainersDownloadedIncDescendants() > 0) {
                latch.countDown();
            }
        };
        statusCacheLive.observeForever(observer);
        try { latch.await(10, TimeUnit.SECONDS); }
        catch(InterruptedException e) {}
        statusCacheLive.removeObserver(observer);

        //now cancel the download
        UstadMobileSystemImpl.getInstance().getNetworkManager().cancelDownloadJob(
                crawlJob.getContainersDownloadJobId());

        OpdsEntryStatusCache entryStatus = DbManager.getInstance(
                PlatformTestUtil.getTargetContext()).getOpdsEntryStatusCacheDao().findByEntryId(
                        CRAWL_ROOT_ENTRY_ID_SLOW);
        Assert.assertEquals("After cancel, no entries are downloaded", 0,
                entryStatus.getContainersDownloadedIncDescendants());
        Assert.assertEquals("After cancel, no downloads are pending", 0,
                entryStatus.getContainersDownloadPendingIncAncestors());
        Assert.assertEquals("After cancel, no pending download bytes", 0,
                entryStatus.getPendingDownloadBytesSoFarIncDescendants());
        Assert.assertEquals("After cancel, no entries are active downloads", 0,
                entryStatus.getActiveDownloadsIncAncestors());
    }

    /**
     * Test downloading entries in a recursive manner
     */
    @Test
    public void givenEntriesNotDownloaded_whenDownloaded_thenShouldBeDownloaded(){
        DbManager dbManager = DbManager.getInstance(PlatformTestUtil.getTargetContext());
        String opdsRootIndexUrl = UMFileUtil.joinPaths(
                ResourcesHttpdTestServer.getHttpRoot(), "com/ustadmobile/test/sharedse/crawlme/index.opds");
        CrawlJob crawlJob = startDownloadJob(opdsRootIndexUrl, CRAWL_ROOT_ENTRY_ID, true,
                true);

        CountDownLatch completeLatch = new CountDownLatch(1);
        UmLiveData<DownloadJob> downloadJobLiveData = dbManager.getDownloadJobDao()
                .getByIdLive(crawlJob.getContainersDownloadJobId());
        UmObserver<DownloadJob> downloadJobObserver = (downloadJobLiveDataUpdate) -> {
            if (downloadJobLiveDataUpdate.getStatus() == NetworkTask.STATUS_COMPLETE) {
                completeLatch.countDown();
            }
        };
        downloadJobLiveData.observeForever(downloadJobObserver);

        try { completeLatch.await(120, TimeUnit.SECONDS); }
        catch(InterruptedException e) {}

        DownloadJob completedJob =dbManager.getDownloadJobDao().findById(crawlJob.getContainersDownloadJobId());

        OpdsEntryStatusCache rootStatusCache = dbManager.getOpdsEntryStatusCacheDao()
                .findByEntryId(CRAWL_ROOT_ENTRY_ID);
        Assert.assertEquals("Download job status reported as completed", NetworkTask.STATUS_COMPLETE,
                completedJob.getStatus());
        Assert.assertEquals("Status shows all child entries downloaded",
                rootStatusCache.getSizeIncDescendants(), rootStatusCache.getContainersDownloadedSizeIncDescendants());
        Assert.assertEquals("4 containers downloaded in total",4,
                rootStatusCache.getContainersDownloadedIncDescendants());

        //now delete them all. We need to rerun the find query, if these entries were unknown before they
        // would not have been discovered
        final boolean[] complete = new boolean[1];
        UstadMobileSystemImpl.getInstance().deleteEntries(PlatformTestUtil.getTargetContext(),
                Arrays.asList(CRAWL_ROOT_ENTRY_ID), true);
    }


    @Test
    public void givenDownloadStarted_whenPausedAndResumed_shouldComplete() {
        String opdsRootIndexUrl = UMFileUtil.joinPaths(
                ResourcesHttpdTestServer.getHttpRoot(), "com/ustadmobile/test/sharedse/crawlme-slow/index.opds");
        DbManager dbManager = DbManager.getInstance(PlatformTestUtil.getTargetContext());

        CrawlJob crawlJob = startDownloadJob(opdsRootIndexUrl, CRAWL_ROOT_ENTRY_ID, true, true);
        int downloadJobId = crawlJob.getContainersDownloadJobId();


        UstadMobileSystemImpl.getInstance().getNetworkManager().queueDownloadJob(downloadJobId);
        try { Thread.sleep(2000); }
        catch(InterruptedException e) {}


        CountDownLatch latch = new CountDownLatch(1);
        UstadMobileSystemImpl.getInstance().getNetworkManager().pauseDownloadJobAsync(downloadJobId,
                (pausedOK) -> { latch.countDown(); });

        try { latch.await(12, TimeUnit.SECONDS); }
        catch(InterruptedException e) {}


        DownloadJob pausedJob = dbManager.getDownloadJobDao().findById(downloadJobId);
        Assert.assertEquals("Task status is paused",
                NetworkTask.STATUS_PAUSED, pausedJob.getStatus());

        UstadMobileSystemImpl.l(UMLog.INFO, 0,
            "TestDownloadTaskStandalone: checked task was paused, now queueing download job again");
        UstadMobileSystemImpl.getInstance().getNetworkManager().queueDownloadJob(downloadJobId);

        CountDownLatch downloadJobLatch = new CountDownLatch(1);
        UmLiveData<DownloadJob> downloadJobLiveData = dbManager.getDownloadJobDao()
                .getByIdLive(crawlJob.getContainersDownloadJobId());
        UmObserver<DownloadJob> downloadJobObserver = (downloadJobLiveDataUpdate) -> {
            if (downloadJobLiveDataUpdate.getStatus() == NetworkTask.STATUS_COMPLETE) {
                downloadJobLatch.countDown();
            }
        };
        downloadJobLiveData.observeForever(downloadJobObserver);

        try { downloadJobLatch.await(2, TimeUnit.MINUTES); }
        catch(InterruptedException e) {}

        downloadJobLiveData.removeObserver(downloadJobObserver);


        pausedJob = dbManager.getDownloadJobDao().findById(downloadJobId);
        OpdsEntryStatusCache rootStatusCache = dbManager.getOpdsEntryStatusCacheDao()
                .findByEntryId(CRAWL_ROOT_ENTRY_ID_SLOW);
        Assert.assertEquals("Download job status reported as completed", NetworkTask.STATUS_COMPLETE,
                pausedJob.getStatus());
        Assert.assertEquals("Status shows all child entries downloaded",
                rootStatusCache.getSizeIncDescendants(), rootStatusCache.getContainersDownloadedSizeIncDescendants());
        Assert.assertEquals("4 containers downloaded in total",4,
                rootStatusCache.getContainersDownloadedIncDescendants());

        //now delete it
        UstadMobileSystemImpl.getInstance().deleteEntries(PlatformTestUtil.getTargetContext(),
                Arrays.asList(CRAWL_ROOT_ENTRY_ID_SLOW), true);
    }

    @Test
    public void givenDownloadStarted_whenConnectivityDisconnected_shouldStopAndQueue() {
        String opdsRootIndexUrl = UMFileUtil.joinPaths(
                ResourcesHttpdTestServer.getHttpRoot(), OPDS_PATH_SPEED_LIMITED );
        CrawlJob crawlJob = startDownloadJob(opdsRootIndexUrl, CRAWL_ROOT_ENTRY_ID_SLOW, true,
                true);
        NetworkManager networkManager = (NetworkManager)UstadMobileSystemImpl.getInstance()
                .getNetworkManager();
        waitForDownloadStatus(crawlJob.getContainersDownloadJobId(), NetworkTask.STATUS_RUNNING,
                10000);
        try { Thread.sleep(JOB_CHANGE_WAIT_TIME); }
        catch(InterruptedException e) {}

        //fire the connectivity disconnected event
        DownloadTask createdTask = networkManager.getActiveTask(
                crawlJob.getContainersDownloadJobId(), DownloadTask.class);
        createdTask.onConnectivityChanged(NetworkManager.CONNECTIVITY_STATE_DISCONNECTED);
        try { Thread.sleep(10000); }
        catch(InterruptedException e) {}

        DownloadJob downloadJob = DbManager.getInstance(PlatformTestUtil.getTargetContext())
                .getDownloadJobDao().findById(crawlJob.getContainersDownloadJobId());
        Assert.assertTrue("Task is stopped", createdTask.isStopped());
        Assert.assertEquals("Job status is waiting for connection",
                NetworkTask.STATUS_WAITING_FOR_CONNECTION, downloadJob.getStatus());

        //now delete it
        networkManager.cancelDownloadJob(downloadJob.getDownloadJobId());
    }

//    TODO: make start download work independently of the crawljob
//    @Test
    public void givenDownloadStartedUnmeteredOnly_whenConnectivityChangesToMetered_shouldStopAndQueue() {
        String opdsRootIndexUrl = UMFileUtil.joinPaths(ResourcesHttpdTestServer.getHttpRoot(),
                OPDS_PATH_SPEED_LIMITED);
        CrawlJob crawlJob = startDownloadJob(opdsRootIndexUrl, CRAWL_ROOT_ENTRY_ID_SLOW, true, false);
        NetworkManager networkManager = (NetworkManager)UstadMobileSystemImpl.getInstance()
                .getNetworkManager();

        try { Thread.sleep(JOB_CHANGE_WAIT_TIME); }
        catch(InterruptedException e) {}
        DownloadTask createdTask = networkManager.getActiveTask(
                crawlJob.getContainersDownloadJobId(), DownloadTask.class);
        createdTask.onConnectivityChanged(NetworkManager.CONNECTIVITY_STATE_METERED);

        DownloadJob downloadJob = DbManager.getInstance(PlatformTestUtil.getTargetContext())
                .getDownloadJobDao().findById(crawlJob.getContainersDownloadJobId());
        Assert.assertTrue("Task is stopped", createdTask.isStopped());
        Assert.assertEquals("Job status is waiting for connection",
                NetworkTask.STATUS_WAITING_FOR_CONNECTION, downloadJob.getStatus());

        //now delete it
        networkManager.cancelDownloadJob(downloadJob.getDownloadJobId());
    }

    @Test
    public void givenDownloadStartedAnyNetwork_whenConnectivityChangesToMetered_shouldComplete() {
        String opdsRootIndexUrl = UMFileUtil.joinPaths(ResourcesHttpdTestServer.getHttpRoot(),
                OPDS_PATH_SPEED_LIMITED);
        CrawlJob crawlJob = startDownloadJob(opdsRootIndexUrl, CRAWL_ROOT_ENTRY_ID_SLOW, true, true);
        NetworkManager networkManager = (NetworkManager)UstadMobileSystemImpl.getInstance()
                .getNetworkManager();

        try { Thread.sleep(JOB_CHANGE_WAIT_TIME); }
        catch(InterruptedException e) {}
        DownloadTask createdTask = networkManager.getActiveTask(
                crawlJob.getContainersDownloadJobId(), DownloadTask.class);
        createdTask.onConnectivityChanged(NetworkManager.CONNECTIVITY_STATE_METERED);

        waitForDownloadStatus(crawlJob.getContainersDownloadJobId(), NetworkTask.STATUS_COMPLETE,
                120*1000);

        DownloadJob dlJob = DbManager.getInstance(PlatformTestUtil.getTargetContext())
                .getDownloadJobDao().findById(crawlJob.getCrawlJobId());
        Assert.assertEquals("Download job status = completed",
                NetworkTask.STATUS_COMPLETE, dlJob.getStatus());
        OpdsEntryStatusCache statusCache = DbManager.getInstance(PlatformTestUtil.getTargetContext())
                .getOpdsEntryStatusCacheDao().findByEntryId(CRAWL_ROOT_ENTRY_ID_SLOW);
        Assert.assertTrue("Containers have been downloaded",
                statusCache.getContainersDownloadedIncDescendants() > 0);

        //now delete it
        UstadMobileSystemImpl.getInstance().deleteEntries(PlatformTestUtil.getTargetContext(),
                Arrays.asList(CRAWL_ROOT_ENTRY_ID_SLOW), true);
    }

//    @Test
    public void givenDownloadStarted_whenServerFails_shouldStopAndQueue() throws IOException{
        String opdsRootIndexUrl = UMFileUtil.joinPaths(ResourcesHttpdTestServer.getHttpRoot(),
                OPDS_PATH_SPEED_LIMITED);
        CrawlJob crawlJob = startDownloadJob(opdsRootIndexUrl, CRAWL_ROOT_ENTRY_ID_SLOW, true, true);

        try { Thread.sleep(JOB_CHANGE_WAIT_TIME); }
        catch(InterruptedException e) {}
        ResourcesHttpdTestServer.stopServer();

        waitForDownloadStatus(crawlJob.getContainersDownloadJobId(), NetworkTask.STATUS_WAIT_FOR_RETRY,
                150000);
        DownloadJob dlJob = DbManager.getInstance(PlatformTestUtil.getTargetContext())
                .getDownloadJobDao().findById(crawlJob.getContainersDownloadJobId());
        Assert.assertEquals("Status after server fail should be STATUS_WAIT_FOR_RETRY",
                NetworkTask.STATUS_WAIT_FOR_RETRY, dlJob.getStatus());

        UstadMobileSystemImpl.getInstance().getNetworkManager().cancelDownloadJob(
                dlJob.getDownloadJobId());
    }

}
