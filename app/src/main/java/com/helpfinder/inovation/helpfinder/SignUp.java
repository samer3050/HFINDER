package com.helpfinder.inovation.helpfinder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Created by SIS on 21-11-2015.
 */
public class SignUp extends Activity {

    private Button btnCreateAccount;
    private EditText etSignUpPassword, etSignUpEmail;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        btnCreateAccount = (Button) findViewById(R.id.btnCreateAccount);
        etSignUpPassword = (EditText) findViewById(R.id.etSignUpEmail);
        etSignUpEmail = (EditText) findViewById(R.id.etSignUpEmail);

    }

    public void onClickSignUp(View view) {
        Toast.makeText(this, "Sign Up", Toast.LENGTH_SHORT).show();
        Bundle dataBundle = new Bundle();
        dataBundle.putString("email", etSignUpEmail.getText().toString());
        dataBundle.putString("password", etSignUpPassword.getText().toString());
        Intent intent = new Intent(getApplicationContext(), DeviceControlActivity.class);
        intent.putExtras(dataBundle);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}