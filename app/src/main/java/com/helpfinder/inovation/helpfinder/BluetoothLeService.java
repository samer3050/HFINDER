/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.helpfinder.inovation.helpfinder;

import android.*;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service implements LocationListener {
    public final static String ACTION_GATT_CONNECTED =
            "com.delta.amrut.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.delta.amrut.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.delta.amrut.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.delta.amrut.ACTION_DATA_AVAILABLE";
    public final static String ACTION_GATT_WRITTEN =
            "com.delta.amrut.ACTION_GATT_WRITTEN";
    public final static String EXTRA_DATA =
            "com.delta.amrut.EXTRA_DATA";
    private final static String TAG = BluetoothLeService.class.getSimpleName();
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int SERVICE_TYPE_PRIMARY = 0;
    private static final int SERVICE_TYPE_SECONDARY = 1;
    private static final int FORMAT_UINT16 = 18;
    private static final int GATT_TIMEOUT = 100; // milliseconds;
    private static BluetoothLeService mThis = null;
    private final IBinder mBinder = new LocalBinder();
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private String mBluetoothDeviceName;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattService mBluetoothGattService;
    private BluetoothLeScanner mBluetoothLeScanner;
    private int mConnectionState = STATE_DISCONNECTED;
    private boolean status;
    private String phone_number;
    private String sms_message;
    private byte[] passwd_device={0x12,0x14};
    private Integer count = 0;
    private volatile boolean mBusy = false; // Write/read pending response
    private LocationManager locationManager;
    private String provider;
    private Location location;
    private float latitude;
    private float longitude;
    private static final int MY_PERMISSION_LOCATION = 1;

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                startScan(mBluetoothDeviceName,mBluetoothDeviceAddress);
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                //Connect to service to read the necessary code.
                UUID uuidService= UUID.fromString("00431c4a-a7a4-428b-a96d-d92d43c8c7cf");
                connectService(uuidService); //Hard coded service.
                UUID uuidCharWrite = UUID.fromString("f1b41cde-dbf5-4acf-8679-ecb8b4dca6ff");
                passwd_device[0]=0x12;
                writeCharacteristic(uuidCharWrite, passwd_device);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.w(TAG, "getProperties: " + String.valueOf(characteristic.getProperties()));
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT successfully read");
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                byte[] temp=characteristic.getValue();
                Log.i(TAG, "Received data");
                if(temp[0]==0x12) send_sms(phone_number,sms_message);
                disconnect(); //Transaction finished. Disconnect from HelpFinder device.
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT succefully written " + status);
                broadcastUpdate(ACTION_GATT_WRITTEN, characteristic);
                //Password written to device. Know read the response.
                UUID readChar = UUID.fromString("f1b41cde-dbf5-4acf-8679-ecb8b4dca6fe");
                readCharacteristic(readChar);
                Log.i(TAG, "Message send");
            } else Log.w(TAG, "GATT failed writing: " + status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

            if (status == 0) Log.w(TAG, "Descriptor succesfully written ");
            else Log.w(TAG, "Descriptor unsuccesfully written code" + String.valueOf(status));
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
        mBusy = false;
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, stringBuilder.toString());
        }
        sendBroadcast(intent);
        mBusy = false;
    }

    private boolean checkGatt() {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        if (mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized");
            return false;
        }

        if (mBusy) {
            Log.w(TAG, "LeService busy");
            return false;
        }
        return true;

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        stopSelf();
        return super.onUnbind(intent);
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        mThis = this;
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothLeScanner == null) {
            Log.e(TAG, "Unable to obtain a BluetoothLeScanner.");
            return false;
        }
        return true;
    }

     /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        Log.d(TAG, "Connect entered");
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    public boolean connectService(UUID uuid) {
        mBluetoothGattService = mBluetoothGatt.getService(uuid);
        if (mBluetoothGattService == null) {
            Log.i(TAG, "mBluetoothGattService is null!");
            return false;
        } else {
            Log.i(TAG, "mBluetoothGatt is OK");
            return true;
        }
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */

    public boolean readCharacteristic(UUID uuid) {
        mBusy = true;
        if (mBluetoothGattService == null) Log.w(TAG, "GattService is broken!");
        BluetoothGattCharacteristic characteristic = mBluetoothGattService.getCharacteristic(uuid);
        if (characteristic == null) Log.w(TAG, "charactertisitc is bad!");
        else Log.w(TAG, "charactertisitc is OK!");
        return mBluetoothGatt.readCharacteristic(characteristic);
    }

    public boolean writeCharacteristic(UUID uuid, byte[] b) {
        //if (!checkGatt())
        //	return false;
        BluetoothGattCharacteristic characteristic = mBluetoothGattService.getCharacteristic(uuid);
        if (characteristic == null) {
            Toast.makeText(this, "Your characteristic is null. BLE112 firmware error.", Toast.LENGTH_LONG).show();
            return false;
        }
        byte[] val = new byte[2];
        val[0] = b[0];
        val[1] = b[1];
        characteristic.setValue(val);
        mBusy = true;
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */

    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable) {
        if (!checkGatt()) {
            Log.i(TAG, "checkGatt problem");
            return false;
        }
        //if(characteristic== null) Log.i(TAG,"characteristic is null"); else Log.i(TAG,"characteristic is OK");

        if (!mBluetoothGatt.setCharacteristicNotification(characteristic, enable)) {
            Log.i(TAG, "setChar problem");
            return false;
        }
        BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        if (clientConfig == null) {
            Log.i(TAG, "clientConfig is null! problem");
            return false;
        }

        if (enable == true) {
            Log.i(TAG, "enable notification");
            if (!clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))
                Log.i(TAG, "setValue problem true");
        } else {
            Log.i(TAG, "disable notification");
            if (clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE))
                Log.i(TAG, "setvalue probelm false");
            ;
        }

        //mBusy = true;
        return mBluetoothGatt.writeDescriptor(clientConfig);
    }

    public void enableNotifications(UUID uuid, boolean enable) {

        BluetoothGattCharacteristic charac = mBluetoothGattService.getCharacteristic(uuid);
        setCharacteristicNotification(charac, enable);
        waitIdle(GATT_TIMEOUT);

    }

    public boolean waitIdle(int i) {
        i /= 10;
        while (--i > 0) {
            if (mBusy)
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            else
                break;
        }

        return i > 0;
    }

     public void startScan(String deviceName, String deviceAddress) {

         mBluetoothDeviceAddress=deviceAddress;
         mBluetoothDeviceName=deviceName;
        //Scan for devices advertising the thermometer service
        ScanFilter helpFinderFilter = new ScanFilter.Builder()
                .setDeviceName(deviceName)
                .build();
        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(helpFinderFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
    }

    private void stopScan() {
        mBluetoothLeScanner.stopScan(mScanCallback);
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onScanResult");
            //Check that the device you want to connect to is the correct one. Compare result and mBluetoothDeviceAddress.
            String actualDeviceAddress = result.getDevice().getAddress().toString();
            String actualDeviceName = result.getDevice().getName().toString();
            if((mBluetoothDeviceAddress.contentEquals(actualDeviceAddress)) & (mBluetoothDeviceName.contentEquals(actualDeviceName)) )  {
                stopScan(); // Stop scanning while processing the scan result.
                connect(mBluetoothDeviceAddress);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d(TAG, "onBatchScanResults: " + results.size() + " results");
            for (ScanResult result : results) {
                //  processResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "LE Scan Failed: " + errorCode);
        }

        private void processResult(ScanResult result) {
            Log.i(TAG, "New LE Device: " + result.getDevice().getName() + " @ " + result.getRssi());
        }
    }; // End mScanCallback

    public void updateContact(String number, String message) {
        phone_number=number;
        sms_message=message;
    } // End updateContatct

    private void send_sms(String phoneNumber, String message) {
        message=message+" Klik her for GPS lokation: "+"http://maps.google.com/?q="+String.valueOf(latitude)+","+String.valueOf(longitude);
        SmsManager sms = SmsManager.getDefault();
        // if message length is too long messages are divided
        List<String> messages = sms.divideMessage(message);
        for (String msg : messages) {
            PendingIntent sentIntent = PendingIntent.getBroadcast(this, 0, new Intent("SMS_SENT"), 0);
            PendingIntent deliveredIntent = PendingIntent.getBroadcast(this, 0, new Intent("SMS_DELIVERED"), 0);
            sms.sendTextMessage(phoneNumber, null, msg, sentIntent, deliveredIntent);
        }
    }

        public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    } //End GPS coord.

    public void getGPSCoord() {
        locationManager=(LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, false);

        try{
            locationManager.requestLocationUpdates("gps",5000,0,this);
            location = locationManager.getLastKnownLocation(provider);
        } catch(Exception e) {
            Log.d("Abe","Something went wroing in getLastKnowLocation");
        }
        if (location != null) {
            System.out.println("Provider " + provider + " has been selected.");
            onLocationChanged(location);
        } else {

            //Do something if location is null
        }
    } //End getGPSCoord

    @Override
    public void onLocationChanged(Location location) {
         latitude = (float) (location.getLatitude());
         longitude = (float) (location.getLongitude());
        Log.d("location","Location changed. Lat:"+latitude+" Long.: "+ longitude);
    } //End onLocationChanged

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }//End onStatusChanged

    @Override
    public void onProviderEnabled(String provider) {
        Toast.makeText(this, "Enabled new provider " + provider,
                Toast.LENGTH_SHORT).show();
    } //End onProviderEnabled

    @Override
    public void onProviderDisabled(String provider) {
        Toast.makeText(this, "Disabled provider " + provider,
                Toast.LENGTH_SHORT).show();
    }//End onProviderDisabled

}
