package com.ustadmobile.port.android.netwokmanager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.annotation.RequiresApi;

import com.ustadmobile.core.impl.UMLog;
import com.ustadmobile.core.impl.UstadMobileSystemImpl;
import com.ustadmobile.lib.db.entities.NetworkNode;
import com.ustadmobile.port.sharedse.networkmanager.BleEntryStatusTask;
import com.ustadmobile.port.sharedse.networkmanager.NetworkManagerBle;
import com.ustadmobile.port.sharedse.networkmanager.WiFiDirectGroupBle;
import com.ustadmobile.port.sharedse.networkmanager.WiFiDirectGroupListenerBle;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static android.content.Context.BLUETOOTH_SERVICE;
import static android.os.Looper.getMainLooper;

/**
 * This class provides methods to perform android network related communications.
 * All Bluetooth Low Energy and WiFi direct communications will be handled here.
 * Also, this is maintained as a singleton by all activities binding to NetworkServiceAndroid,
 * which is responsible to call the init method of this class.
 *
 * <p>
 * Use {@link NetworkManagerAndroidBle#startAdvertising()} to start advertising
 * BLE services to the nearby peers.
 *<p>
 * Use {@link NetworkManagerAndroidBle#stopAdvertising()} to stop advertising
 * BLE services to the nearby peers.
 *<p>
 * Use {@link NetworkManagerAndroidBle#startScanning()} to start scanning for the BLE
 * services advertised from peer devices.
 *<p>
 * Use {@link NetworkManagerAndroidBle#stopScanning()} to stop scanning for the BLE
 * services advertised from peer devices.
 *<p>
 * Use {@link NetworkManagerAndroidBle#createWifiDirectGroup} to create WiFi direct
 * group for peer content downloading.
 *<p>
 * <b>Note:</b> Most of the scan / advertise methods here require
 * {@link android.Manifest.permission#BLUETOOTH_ADMIN} permission.
 *
 * @see NetworkManagerBle
 *
 *  @author kileha3
 */
public class NetworkManagerAndroidBle extends NetworkManagerBle{

    private WifiManager wifiManager;

    private BluetoothManager bluetoothManager;

    private BluetoothAdapter bluetoothAdapter;

    private BluetoothLeAdvertiser bleServiceAdvertiser;

    private Map<Context, ServiceConnection> serviceConnectionMap;

    private BleGattServerAndroid gattServerAndroid;

    private BluetoothLeScanner bleServiceScanner;

    private NetworkServiceAndroid networkService;

    private ParcelUuid parcelServiceUuid = new ParcelUuid(USTADMOBILE_BLE_SERVICE_UUID);

    private WifiP2pManager.Channel wifiP2pChannel;

    private WifiP2pManager wifiP2pManager;

    private WiFiDirectGroupListenerBle wiFiDirectGroupListener;

    private boolean groupCreationInitiated = false;

    /**
     * Listeners for the WiFi-Direct group connections / states,
     * invoked when WiFi Direct state/connection has changed
     */
    private BroadcastReceiver p2pBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null){
                switch (intent.getAction()){
                    case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                        if(groupCreationInitiated){
                            wifiP2pManager.requestGroupInfo(wifiP2pChannel, group -> {
                                WiFiDirectGroupBle groupBle =
                                        new WiFiDirectGroupBle(group.getNetworkName(),group.getPassphrase());
                                wiFiDirectGroupListener.groupCreated(groupBle,null);
                            });
                        }
                        break;
                }
            }
        }
    };


    /**
     * Callback for BLE service scans for devices with
     * Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP
     *
     * @see android.bluetooth.BluetoothAdapter.LeScanCallback
     */
    private BluetoothAdapter.LeScanCallback leScanCallback = (device, rssi, scanRecord) -> {
        NetworkNode networkNode = new NetworkNode();
        networkNode.setBluetoothMacAddress(device.getAddress());
        handleNodeDiscovered(networkNode);
    };

    /**
     * Callback for BLE service scans for devices with
     * Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
     *
     * @see ScanCallback
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            UstadMobileSystemImpl.l(UMLog.DEBUG,690,
                    "Device found "+result.getDevice().getAddress());
            super.onScanResult(callbackType, result);
            NetworkNode networkNode = new NetworkNode();
            networkNode.setBluetoothMacAddress(result.getDevice().getAddress());
            handleNodeDiscovered(networkNode);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            UstadMobileSystemImpl.l(UMLog.DEBUG,690,
                    "Failed to scan for BLE services with errorCode "+errorCode);
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(Object context) {
        networkService = ((NetworkServiceAndroid) context);
        wifiManager= (WifiManager) networkService.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        bluetoothManager = (BluetoothManager)networkService.getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if(requirePermission()){
            bleServiceScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        gattServerAndroid = new BleGattServerAndroid(((Context) context),this);
        wifiP2pManager = (WifiP2pManager) networkService.getSystemService(Context.WIFI_P2P_SERVICE);
        wifiP2pChannel = wifiP2pManager.initialize(networkService, getMainLooper(), null);

        //setting up WiFi Direct connection listener
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        networkService.registerReceiver(p2pBroadcastReceiver, intentFilter);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWiFiEnabled() {
        return wifiManager.isWifiEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBleCapable() {
        return networkService.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canDeviceAdvertise() {
        return requirePermission() && bluetoothAdapter.isMultipleAdvertisementSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startAdvertising() {
        if(requirePermission()){
            bleServiceAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

            BluetoothGattService service = new BluetoothGattService(parcelServiceUuid.getUuid(),
                    BluetoothGattService.SERVICE_TYPE_PRIMARY);

            BluetoothGattCharacteristic writeCharacteristic = new BluetoothGattCharacteristic(
                    parcelServiceUuid.getUuid(), BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);

            service.addCharacteristic(writeCharacteristic);

            gattServerAndroid.getGattServer().addService(service);

            if (bleServiceAdvertiser == null) {
                return;
            }

            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setConnectable(true)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                    .build();

            ParcelUuid parcelUuid = new ParcelUuid(parcelServiceUuid.getUuid());
            AdvertiseData data = new AdvertiseData.Builder()
                    .addServiceUuid(parcelUuid)
                    .build();

            bleServiceAdvertiser.startAdvertising(settings, data, new AdvertiseCallback() {
                @Override public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    super.onStartSuccess(settingsInEffect);
                    UstadMobileSystemImpl.l(UMLog.DEBUG,689,
                            "Service advertised successfully");
                }

                @Override public void onStartFailure(int errorCode) {
                    super.onStartFailure(errorCode);
                    UstadMobileSystemImpl.l(UMLog.ERROR,689,
                            "Service could'nt start, with error code "+errorCode);
                }
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopAdvertising() {
        if(bleServiceAdvertiser == null) return;
        if (gattServerAndroid.getGattServer() != null) {
            gattServerAndroid.getGattServer().clearServices();
            gattServerAndroid.getGattServer().close();
            gattServerAndroid = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startScanning() {

        if(requirePermission()){
            List<ScanFilter> filter = Collections.singletonList(new ScanFilter.Builder()
                    .setServiceUuid(parcelServiceUuid)
                    .build());
             ScanSettings scanSettings=new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    .build();
            bleServiceScanner.startScan(filter,scanSettings,bleScanCallback);
        }else{
            if(!bluetoothAdapter.isDiscovering()){
                bluetoothAdapter.startLeScan(new UUID[] { parcelServiceUuid.getUuid()},leScanCallback);
            }else{
                UstadMobileSystemImpl.l(UMLog.ERROR,689,
                        "Scanning already started, no need to start it again");
            }
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopScanning() {
        if(bluetoothAdapter == null) return;
        if(requirePermission()){
            bleServiceScanner.stopScan(bleScanCallback);
        }else{
            bluetoothAdapter.stopLeScan(leScanCallback);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void openBluetoothSettings() {
        networkService.startActivity(new Intent(
                android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean setWifiEnabled(boolean enabled) {
        return wifiManager.setWifiEnabled(enabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createWifiDirectGroup(WiFiDirectGroupListenerBle wiFiDirectGroupListener) {
        this.wiFiDirectGroupListener = wiFiDirectGroupListener;
        wifiP2pManager.createGroup(wifiP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                groupCreationInitiated = true;
                UstadMobileSystemImpl.l(UMLog.ERROR,692,
                        "Group created successfully");
            }

            @Override
            public void onFailure(int reason) {
                UstadMobileSystemImpl.l(UMLog.ERROR,692,
                        "Failed to create a group with error code "+reason);
            }
        });
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected BleEntryStatusTask makeEntryStatusTask(Object context, List<Long> entryUidsToCheck,
                                                     NetworkNode peerToCheck) {
        return new BleEntryStatusTaskAndroid((Context)context,entryUidsToCheck,peerToCheck);
    }

    /**
     * Responsible for setting up the right services connection
     * @param serviceConnectionMap Map of all services connection made within the app.
     */
    public void setServiceConnectionMap(Map<Context, ServiceConnection> serviceConnectionMap) {
        this.serviceConnectionMap = serviceConnectionMap;
    }

    /**
     * Check if the device needs runtime-permission
     * @return True if needed else False
     */
    private boolean requirePermission(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    /**
     * Get bluetooth manager instance
     * @return Instance of a BluetoothManager
     */
    public BluetoothManager getBluetoothManager(){
        return bluetoothManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        stopAdvertising();
        stopScanning();
        super.onDestroy();
    }
}
