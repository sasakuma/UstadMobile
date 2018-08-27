package com.ustadmobile.lib.contentscrapers;

import com.google.gson.GsonBuilder;
import com.ustadmobile.lib.contentscrapers.EdraakK12.ContentResponse;
import com.ustadmobile.lib.contentscrapers.EdraakK12.EdraakK12ContentScraper;
import com.ustadmobile.lib.contentscrapers.EdraakK12.IndexEdraakK12Content;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;

import static com.ustadmobile.lib.contentscrapers.ScraperConstants.ARABIC_FONT_BOLD;
import static com.ustadmobile.lib.contentscrapers.ScraperConstants.ARABIC_FONT_REGULAR;
import static com.ustadmobile.lib.contentscrapers.ScraperConstants.CONTENT_JSON;
import static com.ustadmobile.lib.contentscrapers.ScraperConstants.ComponentType;
import static com.ustadmobile.lib.contentscrapers.ScraperConstants.INDEX_HTML;
import static com.ustadmobile.lib.contentscrapers.ScraperConstants.JQUERY_JS;
import static com.ustadmobile.lib.contentscrapers.ScraperConstants.MATERIAL_CSS;
import static com.ustadmobile.lib.contentscrapers.ScraperConstants.MATERIAL_JS;
import static com.ustadmobile.lib.contentscrapers.ScraperConstants.QUESTIONS_JSON;
import static com.ustadmobile.lib.contentscrapers.ScraperConstants.UTF_ENCODING;
import static com.ustadmobile.lib.contentscrapers.ScraperConstants.VIDEO_MP4;

public class TestEdraakContentScraper {


    private final String MALFORMED_COMPONENT_ID = "eada";

    private final String DETAIL_JSON_CONTENT_FILE = "/com/ustadmobile/lib/contentscrapers/edraakK12/edraak-detail.txt";

    private final String MAIN_CONTENT_CONTENT_FILE = "/com/ustadmobile/lib/contentscrapers/edraakK12/edraak-main-content.txt";

    private final String MAIN_DETAIL_WITHOUT_TARGET_FILE = "/com/ustadmobile/lib/contentscrapers/edraakK12/edraak-detail-without-target.txt";

    private final String MAIN_DETAIL_WITHOUT_CHILDREN_FILE = "/com/ustadmobile/lib/contentscrapers/edraakK12/edraak-detail-without-children.txt";

    private final String MAIN_DETAIL_NO_VIDEO_FOUND = "/com/ustadmobile/lib/contentscrapers/edraakK12/edraak-detail-no-video-info.txt";

    private final String MAIN_DETAIL_NO_QUESTIONS_FOUND = "/com/ustadmobile/lib/contentscrapers/edraakK12/edraak-detail-no-question-set-children.txt";

    private final String VIDEO_LOCATION_FILE = "/com/ustadmobile/lib/contentscrapers/edraakK12/video.mp4";

    private final String RESOURCE_PATH = "/com/ustadmobile/lib/contentscrapers/edraakK12/";

    final String COMPONENT_API_PREFIX = "/api/component/";

    final Dispatcher dispatcher = new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) throws InterruptedException {


            try {

                if (request.getPath().startsWith(COMPONENT_API_PREFIX)) {

                    int prefixLength = COMPONENT_API_PREFIX.length();
                    String fileName = request.getPath().substring(prefixLength,
                            request.getPath().indexOf(".txt", prefixLength));
                    return new MockResponse().setBody(IOUtils.toString(getClass().getResourceAsStream(fileName + ".txt"), UTF_ENCODING));

                } else if (request.getPath().equals("/media/video.mp4")) {
                    InputStream videoIn = getClass().getResourceAsStream(VIDEO_LOCATION_FILE);
                    BufferedSource source = Okio.buffer(Okio.source(videoIn));
                    Buffer buffer = new Buffer();
                    source.readAll(buffer);

                    return new MockResponse().setResponseCode(200).setBody(buffer);
                } else if(request.getPath().contains("picture")){
                    int length = "/media/".length();
                    String fileName = request.getPath().substring(length,
                            request.getPath().indexOf(".png", length));
                    InputStream pictureIn = getClass().getResourceAsStream(RESOURCE_PATH + fileName + ".png");
                    BufferedSource source = Okio.buffer(Okio.source(pictureIn));
                    Buffer buffer = new Buffer();
                    source.readAll(buffer);
                    return new MockResponse().setResponseCode(200).setBody(buffer);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return new MockResponse().setResponseCode(404);
        }
    };


    public void assertAllFiles(File tmpDir, EdraakK12ContentScraper scraper) throws IOException {

        File jsonFile = new File(tmpDir, CONTENT_JSON);
        Assert.assertTrue("Downloaded content info json exists", ContentScraperUtil.fileHasContent(jsonFile));
        String jsonStr = new String(Files.readAllBytes(jsonFile.toPath()), "UTF-8");
        ContentResponse gsonContent = new GsonBuilder().disableHtmlEscaping().create().fromJson(jsonStr,ContentResponse.class);
        Assert.assertNotNull("Created Gson POJO Object", gsonContent);

        Assert.assertTrue("Downloaded Questions json exist", ContentScraperUtil.fileHasContent(new File(tmpDir, QUESTIONS_JSON)));
        Assert.assertTrue("Downloaded zip exists", ContentScraperUtil.fileHasContent(new File(tmpDir.getParent(), gsonContent.id + ".zip")));

        List<ContentResponse> questionSetList = scraper.getQuestionSet(gsonContent);
        Assert.assertNotNull("Has Questions Set", questionSetList);
        Assert.assertTrue("Has more than 1 question", questionSetList.size() > 0);

        File video = new File(tmpDir, VIDEO_MP4);
        if (ComponentType.ONLINE.getType().equalsIgnoreCase(gsonContent.target_component.component_type)) {
            Assert.assertEquals("Has Video", true, ContentScraperUtil.fileHasContent(video));
        } else {
            Assert.assertEquals("Should not have video", false, ContentScraperUtil.fileHasContent(video));
        }

        Assert.assertTrue("tincan file exists", ContentScraperUtil.fileHasContent(new File(tmpDir, "tincan.xml")));
        Assert.assertTrue("index html file exists", ContentScraperUtil.fileHasContent(new File(tmpDir, INDEX_HTML)));
        Assert.assertTrue("jquery file exists", ContentScraperUtil.fileHasContent(new File(tmpDir, JQUERY_JS)));
        Assert.assertTrue("material js file exists", ContentScraperUtil.fileHasContent(new File(tmpDir, MATERIAL_JS)));
        Assert.assertTrue("material css file exists", ContentScraperUtil.fileHasContent(new File(tmpDir, MATERIAL_CSS)));
        Assert.assertTrue("arabic font regular file exists", ContentScraperUtil.fileHasContent(new File(tmpDir, ARABIC_FONT_REGULAR)));
        Assert.assertTrue("arabic font bold file exists", ContentScraperUtil.fileHasContent(new File(tmpDir, ARABIC_FONT_BOLD)));

    }


    @Test
    public void givenServerOnline_whenEdXContentScraped_thenShouldConvertAndDownload() throws IOException{

        File tmpDir = Files.createTempDirectory("testedxcontentscraper").toFile();
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);
        String url = EdraakK12ContentScraper.generateUrl(mockWebServer.url("/api/").toString(), DETAIL_JSON_CONTENT_FILE, 41);
        EdraakK12ContentScraper scraper = new EdraakK12ContentScraper(url, tmpDir);
        scraper.scrapContent();
        assertAllFiles(tmpDir, scraper);

    }

    @Test(expected = IllegalArgumentException.class)
    public void givenNotImportedContent_whenEdXContentScraped_thenShouldThrowIllegalArgumentException() throws IOException {

        File tmpDir = Files.createTempDirectory("testedxcontentscraper").toFile();
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);
        String url = EdraakK12ContentScraper.generateUrl(mockWebServer.url("/api/").toString(), MAIN_CONTENT_CONTENT_FILE, 41);
        EdraakK12ContentScraper scraper = new EdraakK12ContentScraper(url, tmpDir);
        scraper.scrapContent();
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenNullTargetComponent_whenEdXContentScraped_thenShouldThrowIllegalArgumentException() throws IOException {

        File tmpDir = Files.createTempDirectory("testedxcontentscraper").toFile();
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);


        String url = EdraakK12ContentScraper.generateUrl(mockWebServer.url("/api/").toString(), MAIN_DETAIL_WITHOUT_TARGET_FILE, 41);
        EdraakK12ContentScraper scraper = new EdraakK12ContentScraper(url, tmpDir);
        scraper.scrapContent();

    }

    @Test(expected = IllegalArgumentException.class)
    public void givenNullTargetComponentChildren_whenEdXContentScraped_thenShouldThrowIllegalArgumentException() throws IOException{

        File tmpDir = Files.createTempDirectory("testedxcontentscraper").toFile();
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        String url = EdraakK12ContentScraper.generateUrl(mockWebServer.url("/api/").toString(),MAIN_DETAIL_WITHOUT_CHILDREN_FILE, 41);
        EdraakK12ContentScraper scraper = new EdraakK12ContentScraper(url, tmpDir);
        scraper.scrapContent();

    }

    @Test(expected = IllegalArgumentException.class)
    public void givenEncodedVideoListIsEmpty_whenEdXContentScraped_thenShouldThrowIllegalArgumentException() throws IOException {

        File tmpDir = Files.createTempDirectory("testedxcontentscraper").toFile();
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        String url = EdraakK12ContentScraper.generateUrl(mockWebServer.url("/api/").toString(), MAIN_DETAIL_NO_VIDEO_FOUND, 41);
        EdraakK12ContentScraper scraper = new EdraakK12ContentScraper(url, tmpDir);
        scraper.scrapContent();

    }

    @Test(expected = IllegalArgumentException.class)
    public void givenEmptyQuestionSet_whenEdXContentScraped_thenShouldThrowIOException() throws IOException {

        File tmpDir = Files.createTempDirectory("testedxcontentscraper").toFile();
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        String url = EdraakK12ContentScraper.generateUrl(mockWebServer.url("/api/").toString(), MAIN_DETAIL_NO_QUESTIONS_FOUND, 41);
        EdraakK12ContentScraper scraper = new EdraakK12ContentScraper(url, tmpDir);
        scraper.scrapContent();


    }


    @Test(expected = IllegalArgumentException.class)
    public void givenMalformedContent_whenEdXContentScraped_thenShouldThrowIllegalArgumentException() throws IOException{


            File tmpDir = Files.createTempDirectory("testedxcontentscraper").toFile();

            MockWebServer mockWebServer = new MockWebServer();

            try{
                mockWebServer.enqueue(new MockResponse().setBody("{id"));

                mockWebServer.start();

                String url = EdraakK12ContentScraper.generateUrl(mockWebServer.url("/api/").toString(), MALFORMED_COMPONENT_ID, 41);
                EdraakK12ContentScraper scraper = new EdraakK12ContentScraper(url, tmpDir);
                scraper.scrapContent();

            } finally {
                mockWebServer.close();
            }
    }

    @Test
    public void givenContentNotModified_whenEdXContentScrapedAgain_thenShouldNotDownloadComponents() throws IOException {
        //run the initial scrapContent
        File tmpDir = Files.createTempDirectory("testmodifiededraak").toFile();
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        String url = EdraakK12ContentScraper.generateUrl(mockWebServer.url("/api/").toString(), DETAIL_JSON_CONTENT_FILE, 41);
        EdraakK12ContentScraper scraper = new EdraakK12ContentScraper(url, tmpDir);
        scraper.scrapContent();

        long firstDownloadTime = new File(tmpDir, CONTENT_JSON).lastModified();
        //now run scrapContent again...
        scraper.scrapContent();

        long lastModified = new File(tmpDir, CONTENT_JSON).lastModified();
        //Assert that last modified dates are lower than firstDownloadCompleteTime
        Assert.assertTrue(lastModified == firstDownloadTime);

    }

    @Test
    public void givenVideoModified_whenEdXContentScrapedAgain_thenShouldVideoOnlyAgain() throws IOException  {

        File tmpDir = Files.createTempDirectory("testmodifiededraak").toFile();
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        String url = EdraakK12ContentScraper.generateUrl(mockWebServer.url("/api/").toString(), DETAIL_JSON_CONTENT_FILE, 41);
        EdraakK12ContentScraper scraper = new EdraakK12ContentScraper(url, tmpDir);

        scraper.scrapContent();

        long firstDownloadTime = new File(tmpDir, VIDEO_MP4).lastModified();
        //now run scrapContent again...
        scraper.scrapContent();

        long lastModified = new File(tmpDir, VIDEO_MP4).lastModified();
        //Assert that last modified dates are lower than firstDownloadCompleteTime
        Assert.assertTrue(lastModified == firstDownloadTime);

    }

    @Test
    public void givenQuestionSetModified_whenEdXContentScrapedAgain_thenShouldDownloadImagesOnlyAgain() throws IOException {

        File tmpDir = Files.createTempDirectory("testmodifiededraak").toFile();
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        String url = EdraakK12ContentScraper.generateUrl(mockWebServer.url("/api/").toString(), DETAIL_JSON_CONTENT_FILE, 41);
        EdraakK12ContentScraper scraper = new EdraakK12ContentScraper(url, tmpDir);

        scraper.scrapContent();
        long firstDownloadTime = new File(tmpDir, QUESTIONS_JSON).lastModified();
        //now run scrapContent again...

        scraper.scrapContent();

        long lastModified = new File(tmpDir, QUESTIONS_JSON).lastModified();
        //Assert that last modified dates are lower than firstDownloadCompleteTime
        Assert.assertTrue(lastModified == firstDownloadTime);

    }


    @Test
    public void testCommand() throws IOException{
        if(System.getProperty("url") != null && System.getProperty("dir") != null){
            File destination = new File(System.getProperty("dir"));
            EdraakK12ContentScraper scraper = new EdraakK12ContentScraper(
                    System.getProperty("url"),destination );
            scraper.scrapContent();
            assertAllFiles(destination, scraper);
        }
    }

    @Test
    public void testIndexCommand() throws IOException{
        IndexEdraakK12Content content = new IndexEdraakK12Content();
        if(System.getProperty("findUrl") != null && System.getProperty("findDir") != null) {
            content.findContent(System.getProperty("findUrl"), new File(System.getProperty("findDir")));
        }
    }

}