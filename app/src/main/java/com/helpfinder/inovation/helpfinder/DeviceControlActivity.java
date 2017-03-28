/*
 Author: Samer Ismail
 App Name: HelpFinder
 Description: HelpFinder is an app designed to work with ana accessory
 used as aa panic alarm. The app subscribes for notification to be
  notified when a button is pressed on the accessory.
 */

package com.helpfinder.inovation.helpfinder;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity implements OnClickListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();
    private static final int REQUEST_SMS = 0;
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private TextView mConnectionState;
    private String mDeviceName;
    private String mDeviceAddress;
    //private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private boolean protect=false;
    private Button btn_addDevice;
    private Button btn_reset;
    private Button btn_protect;
    private TextView tv_deviceBond;
    private EditText etPhoneNo;
    private EditText etSMSmsg;
    private static final int MY_PERMISSION_LOCATION = 1;
    private LocationManager locationManager;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
          //  requestBLEPerm();
          //  sms_enable();
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            mBluetoothLeService.getGPSCoord();
            String phone_number= etPhoneNo.getText().toString();
            String sms_message=etSMSmsg.getText().toString();
           // mBluetoothLeService.updateGPS();
            mBluetoothLeService.updateContact(phone_number,sms_message);
            if(mDeviceName!=null) mBluetoothLeService.startScan(mDeviceName,mDeviceAddress);
            // Automatically connects to the device upon successful start-up initialization

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService.disconnect();
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

        }

    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);
        // Sets up UI references.
        mConnectionState = (TextView) findViewById(R.id.connection_state);

        final Handler mhandler = new Handler();
        btn_addDevice= (Button) findViewById(R.id.btn_addDevice);
        btn_addDevice.setOnClickListener(this);

        btn_protect= (Button) findViewById(R.id.btn_protect);
        btn_protect.setOnClickListener(this);
        btn_reset= (Button) findViewById(R.id.btn_reset);
        btn_reset.setOnClickListener(this);

        tv_deviceBond = (TextView) findViewById(R.id.tv_deviceBond);
        etPhoneNo = (EditText) findViewById(R.id.etPhoneNo);
        etSMSmsg = (EditText) findViewById(R.id.etSMSmsg);
          requestBLEPerm();
      //  checkGPSEnabled();
      //  checkGPSPermission();

          sms_enable();

    }

    protected void onResume() {
        super.onResume();

        if (mBluetoothLeService != null) {
            //SIS    final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            //SIS    Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //SIS unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
       //SIS unbindService(mServiceConnection);
       //SIS mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress); //Connect to the selected device                     
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {

            case R.id.btn_addDevice: {
                Intent intent = new Intent(this, ScanDevice.class);
                startActivityForResult(intent,1);
                return;
            }

            case R.id.btn_protect: {
                if(protect) {
                    protect=false;
                    btn_protect.setText("Start");
                    //Kill service
                    if(mBluetoothLeService!=null) mBluetoothLeService.disconnect();
                    unbindService(mServiceConnection);
                    Intent intent = new Intent(this,BluetoothLeService.class);
                    mBluetoothLeService = null;
                    stopService(intent);
                } else {
                    protect=true;
                    btn_protect.setText("Stop");
                    Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
                    //unbindService(mServiceConnection);
                    //stopService(gattServiceIntent);
                    bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                }

                return;
            }

            case R.id.btn_reset: {
                Log.i(TAG, "button pressed: reset");
                mDeviceAddress="";
                mDeviceName="";
                tv_deviceBond.setText("Device bonded: none");
                return;
            }

            default: return;
        }

    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_WRITTEN);
        return intentFilter;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
       if (requestCode == 1) {

            if(resultCode == Activity.RESULT_OK){
                mDeviceName=data.getStringExtra("EXTRAS_DEVICE_NAME");
                mDeviceAddress=data.getStringExtra("EXTRAS_DEVICE_ADDRESS");
                tv_deviceBond.setText("Device bonded: "+mDeviceName);
            }

            if (resultCode == RESULT_CANCELED) {
                //Write your code if there's no result
            }
       }

    } //onActivityResult


    private void requestBLEPerm() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_ADMIN)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.BLUETOOTH_ADMIN)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_ADMIN},
                        1);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
    }

    public void sms_enable() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            int hasSMSPermission = checkSelfPermission(Manifest.permission.SEND_SMS);
            if (hasSMSPermission != PackageManager.PERMISSION_GRANTED) {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)) {
                    showMessageOKCancel("You need to allow access to Send SMS",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        requestPermissions(new String[] {Manifest.permission.SEND_SMS},
                                                REQUEST_SMS);
                                    }
                                }
                            });
                    return;
                }
                requestPermissions(new String[] {Manifest.permission.SEND_SMS},
                        REQUEST_SMS);
                return;
            }

        }
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(DeviceControlActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private void checkGPSPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && this.checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && this.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSION_LOCATION);
            Log.d("Abe", "GPS permission granted");
        } else {

            Log.d("Abe", "GPS permission NOT granted");
            //   gps functions.
        }
    }
    private void checkGPSEnabled() {
        LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean enabled = service
                .isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!enabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == MY_PERMISSION_LOCATION
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            Log.d("Abe", "GPS permission granted or sure");

        } else {
            Log.d("Abe", "GPS permission not granted at all!");
        }
    }

}