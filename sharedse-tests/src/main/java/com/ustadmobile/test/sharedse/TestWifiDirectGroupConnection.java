package com.ustadmobile.test.sharedse;

import com.ustadmobile.core.impl.HTTPResult;
import com.ustadmobile.core.impl.UMLog;
import com.ustadmobile.core.impl.UstadMobileSystemImpl;
import com.ustadmobile.port.sharedse.impl.UstadMobileSystemImplSE;
import com.ustadmobile.port.sharedse.networkmanager.NetworkManager;
import com.ustadmobile.core.networkmanager.NetworkManagerListener;
import com.ustadmobile.core.networkmanager.NetworkNode;
import com.ustadmobile.core.networkmanager.NetworkTask;
import com.ustadmobile.test.core.impl.PlatformTestUtil;
import com.ustadmobile.test.sharedse.http.RemoteTestServerHttpd;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

/**
 *
 * Runs a basic test to see if we can connect to a Wifi Direct group
 *
 * Created by mike on 6/2/17.
 */
public class TestWifiDirectGroupConnection {

    public static final int CONNECTION_TIMEOUT = 60 * 1000;

    @Test
    public void testWifiDirectGroupConnection() throws IOException{
        NetworkManager manager = UstadMobileSystemImplSE.getInstanceSE().getNetworkManager();
        Assume.assumeTrue("WiFi is available and enabled", manager.isWiFiEnabled());
        UstadMobileSystemImpl.l(UMLog.INFO, 324, "TestWifiDirectGroupConnection: start");
        String createGroupUrl = PlatformTestUtil.getRemoteTestEndpoint() + "?cmd="
                + RemoteTestServerHttpd.CMD_CREATEGROUP;
        HTTPResult result = UstadMobileSystemImpl.getInstance().makeRequest(createGroupUrl, null, null);
        Assert.assertEquals("Group created", 200, result.getStatus());
        String resultStr = new String(result.getResponse());
        JSONObject object = new JSONObject(resultStr);

        String ssid = object.getString("ssid");
        String passphrase = object.getString("passphrase");
        Assert.assertNotNull("Got ssid", ssid);
        Assert.assertNotNull("Got passphrase", passphrase);


        final String[] connectedSsid = new String[1];
        final Object connectionLock = new Object();
        NetworkManagerListener listener = new NetworkManagerListener() {
            @Override
            public void fileStatusCheckInformationAvailable(List<String> fileIds) {

            }

            @Override
            public void networkTaskCompleted(NetworkTask task) {

            }

            @Override
            public void networkNodeDiscovered(NetworkNode node) {

            }

            @Override
            public void networkNodeUpdated(NetworkNode node) {

            }

            @Override
            public void fileAcquisitionInformationAvailable(String entryId, long downloadId, int downloadSource) {

            }

            @Override
            public void wifiConnectionChanged(String ssid, boolean connected, boolean connectedOrConnecting) {
                if(connected) {
                    connectedSsid[0] = ssid;
                    synchronized (connectionLock) {
                        connectionLock.notify();
                    }
                }
            }
        };
        manager.addNetworkManagerListener(listener);
        manager.connectToWifiDirectGroup(ssid, passphrase);
        synchronized (connectionLock) {
            try {connectionLock.wait(CONNECTION_TIMEOUT);}
            catch(InterruptedException e) {}
        }

        Assert.assertEquals("Connected to created group ssid", ssid, connectedSsid[0]);

        try { Thread.sleep(2000); }
        catch(InterruptedException e) {}

        manager.restoreWifi();
        synchronized (connectionLock) {
            try { connectionLock.wait(CONNECTION_TIMEOUT); }
            catch(InterruptedException e) {}
        }

        Assert.assertNotEquals("Connected back to 'normal' wifi", ssid,connectedSsid[0]);
    }

}