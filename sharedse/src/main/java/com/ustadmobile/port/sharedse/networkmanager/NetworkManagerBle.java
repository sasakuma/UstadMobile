package com.ustadmobile.port.sharedse.networkmanager;

import com.ustadmobile.core.db.UmAppDatabase;
import com.ustadmobile.core.db.dao.EntryStatusResponseDao;
import com.ustadmobile.core.db.dao.NetworkNodeDao;
import com.ustadmobile.core.impl.UMLog;
import com.ustadmobile.core.impl.UmCallback;
import com.ustadmobile.core.impl.UstadMobileSystemImpl;
import com.ustadmobile.lib.db.entities.NetworkNode;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This is an abstract class which is used to implement platform specific NetworkManager
 *
 * @author kileha3
 *
 */

public abstract class NetworkManagerBle {

    /**
     * Flag to indicate entry status request
     */
    public static final byte ENTRY_STATUS_REQUEST = (byte) 111;

    /**
     * Flag to indicate entry status response
     */
    public static final byte ENTRY_STATUS_RESPONSE = (byte) 112;

    /**
     * Flag to indicate WiFi direct group creation request
     */
    public static final byte WIFI_GROUP_CREATION_REQUEST = (byte) 113;

    /**
     * Flag to indicate WiFi direct group creation response
     */
    public static final byte WIFI_GROUP_CREATION_RESPONSE = (byte) 114;

    /**
     * Commonly used MTU for android devices
     */
    public static final int DEFAULT_MTU_SIZE = 20;

    public static final int MAXIMUM_MTU_SIZE = 512;

    /**
     * Bluetooth Low Energy service UUID for our app
     */
    public static final UUID USTADMOBILE_BLE_SERVICE_UUID = UUID.fromString("7d2ea28a-f7bd-485a-bd9d-92ad6ecfe93e");


    private final Object knownNodesLock = new Object();

    private Object mContext;

    private boolean isStopMonitoring = false;

    private ExecutorService entryStatusTaskExecutorService = Executors.newCachedThreadPool();

    private Map<Object, List<Long>> availabilityMonitoringRequests = new HashMap<>();

    /**
     * Holds all created entry status tasks
     */
    protected Vector<BleEntryStatusTask> entryStatusTasks = new Vector<>();

    /**
     * Do the main initialization of the NetworkManager : set the mContext
     *
     * @param context The mContext to use for the network manager
     */
    public synchronized void init(Object context){
        if(this.mContext == null){
            this.mContext = context;
        }
    }


    /**
     * Check if WiFi is enabled / disabled on the device
     * @return boolean true, if enabled otherwise false.
     */
    public abstract boolean isWiFiEnabled();


    /**
     * Check if the device is Bluetooth Low Energy capable
     * @return True is capable otherwise false
     */
    public abstract boolean isBleCapable();


    /**
     * Check if bluetooth is enabled on the device
     * @return True if enabled otherwise false
     */
    public abstract boolean isBluetoothEnabled();

    /**
     * Check if the device can create BLE service and advertise it to the peer devices
     * @return true if can advertise its service else false
     */
    public abstract boolean canDeviceAdvertise();

    /**
     * Start advertising BLE service to the peer devices
     * <b>Use case</b>
     * When this method called, it will create BLE service and start advertising it.
     */
    public abstract void startAdvertising();

    /**
     * Stop advertising the service which was created and advertised by {@link NetworkManagerBle#startAdvertising()}
     */
    public abstract void stopAdvertising();

    /**
     * Start scanning for the peer devices whose services are being advertised
     */
    public abstract void startScanning();

    /**
     * Stop scanning task which was started by {@link NetworkManagerBle#startScanning()}
     */
    public abstract void stopScanning();


    /**
     * This should be called by the platform implementation when BLE discovers a nearby device
     *
     * @param node The nearby device discovered
     */
    protected void handleNodeDiscovered(NetworkNode node) {

        synchronized (knownNodesLock){
            long updateTime = Calendar.getInstance().getTimeInMillis();
            NetworkNodeDao networkNodeDao = UmAppDatabase.getInstance(mContext).getNetworkNodeDao();
            networkNodeDao.updateLastSeen(node.getBluetoothMacAddress(),updateTime,
                    new UmCallback<Integer>() {
                        @Override
                        public void onSuccess(Integer result) {
                            if(result == 0){
                                node.setNetworkNodeLastUpdated(updateTime);
                                networkNodeDao.insert(node);
                                List<Long> entryUidsToMonitor = new ArrayList<>(getAllUidsToBeMonitored());
                                if(!isStopMonitoring){
                                    BleEntryStatusTask entryStatusTask =
                                            makeEntryStatusTask(mContext,entryUidsToMonitor,node);
                                    entryStatusTasks.add(entryStatusTask);
                                    entryStatusTaskExecutorService.execute(entryStatusTask);
                                    UstadMobileSystemImpl.l(UMLog.DEBUG,694,
                                            "Node added to the db and task created",null);
                                }else{
                                    UstadMobileSystemImpl.l(UMLog.DEBUG,694,
                                            "Task couldn't be created, monitoring stopped",
                                            null);
                                }
                            }else{
                                UstadMobileSystemImpl.l(UMLog.DEBUG,694,
                                        "Node exists: was updated successfully",null);
                            }
                        }

                        @Override
                        public void onFailure(Throwable exception) {
                            UstadMobileSystemImpl.l(UMLog.DEBUG,694,
                                    "NetworkNode updated failed",new Exception(exception));
                        }
                    });
        }
    }

    /**
     * Open bluetooth setting section from setting panel
     */
    public abstract void openBluetoothSettings();

    /**
     * Enable or disable WiFi on the device
     *
     * @param enabled Enable when true otherwise disable
     * @return true if the operation is successful, false otherwise
     */
    public abstract boolean setWifiEnabled(boolean enabled);

    /**
     * Create a new WiFi direct group on this device. A WiFi direct group
     * will create a new SSID and passphrase other devices can use to connect in "legacy" mode.
     *
     * The process is asynchronous and the {@link WiFiDirectGroupListenerBle} should be used to listen for
     * group creation.
     *
     * If a WiFi direct group is already under creation this method has no effect.
     *
     * @param wiFiDirectGroupListener Listener for group creation task
     */
    public abstract void createWifiDirectGroup(WiFiDirectGroupListenerBle wiFiDirectGroupListener);

    /**
     * Remove a WiFi group from the device.The process is asynchronous and the
     * {@link WiFiDirectGroupListenerBle} should be used to listen for
     * group removal.
     * @param wiFiDirectGroupListener Listener for group removal
     */
    public abstract void removeWifiDirectGroup(WiFiDirectGroupListenerBle wiFiDirectGroupListener);

    /**
     * Start monitoring availability of specific entries from peer devices
     * @param monitor Object to monitor e.g Presenter
     * @param entryUidsToMonitor List of entries to be monitored
     */
    public void startMonitoringAvailability(Object monitor, List<Long> entryUidsToMonitor) {
        availabilityMonitoringRequests.put(monitor, entryUidsToMonitor);

        NetworkNodeDao networkNodeDao = UmAppDatabase.getInstance(mContext).getNetworkNodeDao();
        EntryStatusResponseDao responseDao =
                UmAppDatabase.getInstance(mContext).getEntryStatusResponseDao();

        List<Long> uniqueEntryUidsToMonitor = new ArrayList<>(getAllUidsToBeMonitored());
        List<Integer> knownNetworkNodes =
                getAllKnownNetworkNodeIds(networkNodeDao.findAllActiveNodes());

        List<EntryStatusResponseDao.EntryWithoutRecentResponse> entryWithoutRecentResponses =
                responseDao.findEntriesWithoutRecentResponse(uniqueEntryUidsToMonitor,knownNetworkNodes);

        //Group entryUUid by node where their status will be checked from
        HashMap<Integer,List<Long>> nodeToCheckEntryList = new HashMap<>();
        for(EntryStatusResponseDao.EntryWithoutRecentResponse entryResponse: entryWithoutRecentResponses){
            int nodeIdToCheckFrom = entryResponse.getNodeId();
            if(nodeToCheckEntryList.containsKey(nodeIdToCheckFrom)){
                nodeToCheckEntryList.get(nodeIdToCheckFrom).add(entryResponse.getContentEntryUid());
            }else{
                List<Long> entryUuids = new ArrayList<>();
                entryUuids.add(entryResponse.getContentEntryUid());
                nodeToCheckEntryList.put(nodeIdToCheckFrom, entryUuids);
            }
        }

        //Make entryStatusTask as per node list and entryUuids found
        for(int nodeId : nodeToCheckEntryList.keySet()){
            NetworkNode networkNode = networkNodeDao.findNodeById(nodeId);
            BleEntryStatusTask entryStatusTask = makeEntryStatusTask(mContext,
                    nodeToCheckEntryList.get(nodeId),networkNode);
            entryStatusTasks.add(entryStatusTask);
            entryStatusTaskExecutorService.execute(entryStatusTask);
        }
    }

    /**
     * Stop monitoring the availability of entries from peer devices
     * @param monitor Monitor object which created a monitor (e.g Presenter)
     */
    public void stopMonitoringAvailability(Object monitor) {
        availabilityMonitoringRequests.remove(monitor);
        isStopMonitoring = availabilityMonitoringRequests.size() == 0;
    }


    /**
     * Get all unique entry UUID's to be monitored
     * @return Set of all unique UUID's
     */
    protected Set<Long> getAllUidsToBeMonitored() {
        Set uidsToBeMonitoredSet = new HashSet();
        for(List<Long> uidList : availabilityMonitoringRequests.values()) {
            uidsToBeMonitoredSet.addAll(uidList);
        }
        return uidsToBeMonitoredSet;
    }


    protected List<Integer> getAllKnownNetworkNodeIds(List<NetworkNode> networkNodes){
        List<Integer> nodeIdList = new ArrayList<>();
        for(NetworkNode networkNode: networkNodes){
            nodeIdList.add(networkNode.getNodeId());
        }
        return nodeIdList;
    }

    /**
     * Connecting a client to a group network for content acquisition
     * @param ssid Group network SSID
     * @param passphrase Group network passphrase
     */
    public abstract void connectToWiFi(String ssid, String passphrase);

    /**
     * Create entry status task for a specific peer device,
     * it will request status of the provided entries from the provided peer device
     * @param context Platform specific mContext
     * @param entryUidsToCheck List of entries to be checked from the peer device
     * @param peerToCheck Peer device to request from
     * @return Created BleEntryStatusTask
     *
     * @see BleEntryStatusTask
     */
    public abstract BleEntryStatusTask makeEntryStatusTask(Object context,List<Long> entryUidsToCheck, NetworkNode peerToCheck);


    /**
     * Clean up the network manager for shutdown
     */
    public void onDestroy(){
        entryStatusTaskExecutorService.shutdown();
        if(entryStatusTaskExecutorService.isShutdown()){
            mContext = null;
        }
    }
}