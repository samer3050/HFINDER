package com.helpfinder.inovation.helpfinder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

//import java.util.UUID;


/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */

public class HelpFinder extends Activity {

    //private Button btnSignUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_finder);
        // btnSignUp = (Button) findViewById(R.id.btnSignUp);
    }

    public void onClickSignUp(View view) {

        final Intent intent = new Intent(this, SignUp.class);
//        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
//        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        startActivity(intent);
    }

    public void onClickLogIn(View view) {
        final Intent intent = new Intent(this, LogIn.class);
        startActivity(intent);
    }

}