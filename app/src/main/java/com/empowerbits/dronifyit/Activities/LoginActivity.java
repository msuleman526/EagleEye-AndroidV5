package com.empowerbits.dronifyit.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.empowerbits.dronifyit.ApiResponse.ErrorResponse;
import com.empowerbits.dronifyit.ApiResponse.LoginResponse;
import com.empowerbits.dronifyit.R;
import com.empowerbits.dronifyit.Retrofit.ApiClient;
import com.empowerbits.dronifyit.util.UserSessionManager;

import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    EditText emailEdt, passwordEdt;
    LinearLayout loader, checkboxView;
    Button loginBtn;
    CheckBox remCheckbox;

    UserSessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new UserSessionManager(getApplicationContext());
        emailEdt = findViewById(R.id.emailAddressEdt);
        passwordEdt = findViewById(R.id.passwordEdt);
        emailEdt.setText(sessionManager.getUserEmail());
        passwordEdt.setText(sessionManager.getFieldPassword());
        loader = findViewById(R.id.loader);
        loginBtn = findViewById(R.id.loginButton);
        checkboxView = findViewById(R.id.checkboxView);
        remCheckbox = findViewById(R.id.remmeberMeCheckbox);

        emailEdt.setText(sessionManager.getFieldEmail());
        passwordEdt.setText(sessionManager.getFieldPassword());

        checkboxView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                remCheckbox.setChecked(!remCheckbox.isChecked());
            }
        });

        loginBtn.setOnClickListener(view -> loginButtonClick());

    }

    private void loginButtonClick(){
        String email = emailEdt.getText().toString();
        String password = passwordEdt.getText().toString();

        Log.d("Email", email);
        Log.d("Password", password);

        if(remCheckbox.isChecked()){
            sessionManager.saveFieldEmail(email);
            sessionManager.saveFieldPassword(password);
        }else{
            sessionManager.saveFieldEmail("");
            sessionManager.saveFieldPassword("");
        }

        if(email.equals("") || password.equals("")){
            Toast.makeText(this, "Please fill the fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        loader.setVisibility(View.VISIBLE);
        loginBtn.setVisibility(View.GONE);

        ApiClient.getApiService().login(email,password).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(retrofit2.Call<LoginResponse> call, Response<LoginResponse> response) {
                loader.setVisibility(View.GONE);
                loginBtn.setVisibility(View.VISIBLE);

                if (response.isSuccessful()) {
                    LoginResponse loginResponse = response.body();
                    sessionManager.saveUser(loginResponse.getUser().id, loginResponse.getUser().first_name+" "+loginResponse.getUser().last_name, loginResponse.getUser().email, loginResponse.getToken());
                    Toast.makeText(LoginActivity.this, "Welcome "+loginResponse.getUser().first_name+" "+loginResponse.getUser().last_name, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                } else {
                    try {
                        Gson gson = new Gson();
                        ErrorResponse error = gson.fromJson(response.errorBody().charStream(), ErrorResponse.class);
                        Toast.makeText(LoginActivity.this, ""+error.getMessage(), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e("ParseError", "Error parsing error body", e);
                    }
                }
            }

            @Override
            public void onFailure(retrofit2.Call<LoginResponse> call, Throwable t) {
                Log.e("LoginFailure", t.getMessage());
                Toast.makeText(LoginActivity.this, "There is some issue.", Toast.LENGTH_SHORT).show();
                loader.setVisibility(View.GONE);
                loginBtn.setVisibility(View.VISIBLE);
            }
        });
    }
}
