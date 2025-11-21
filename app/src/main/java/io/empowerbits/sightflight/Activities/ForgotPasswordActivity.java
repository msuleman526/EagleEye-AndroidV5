package io.empowerbits.sightflight.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import io.empowerbits.sightflight.ApiResponse.ErrorResponse;
import io.empowerbits.sightflight.ApiResponse.ForgotPasswordResponse;
import io.empowerbits.sightflight.R;
import io.empowerbits.sightflight.Retrofit.ApiClient;

import retrofit2.Callback;
import retrofit2.Response;

public class ForgotPasswordActivity extends AppCompatActivity {

    EditText emailEdt;
    LinearLayout loader;
    Button submitBtn;
    TextView backToLoginBtn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        emailEdt = findViewById(R.id.emailAddressEdt);
        loader = findViewById(R.id.loader);
        submitBtn = findViewById(R.id.submitButton);
        backToLoginBtn = findViewById(R.id.backToLoginBtn);

        submitBtn.setOnClickListener(view -> submitButtonClick());

        backToLoginBtn.setOnClickListener(view -> {
            finish();
        });
    }

    private void submitButtonClick() {
        String email = emailEdt.getText().toString().trim();

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email address.", Toast.LENGTH_SHORT).show();
            return;
        }

        loader.setVisibility(View.VISIBLE);
        submitBtn.setVisibility(View.GONE);

        ApiClient.getApiService().forgotPassword(email).enqueue(new Callback<ForgotPasswordResponse>() {
            @Override
            public void onResponse(retrofit2.Call<ForgotPasswordResponse> call, Response<ForgotPasswordResponse> response) {
                loader.setVisibility(View.GONE);
                submitBtn.setVisibility(View.VISIBLE);

                if (response.isSuccessful()) {
                    ForgotPasswordResponse forgotPasswordResponse = response.body();
                    Toast.makeText(ForgotPasswordActivity.this, forgotPasswordResponse.getStatus(), Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    try {
                        Gson gson = new Gson();
                        ErrorResponse error = gson.fromJson(response.errorBody().charStream(), ErrorResponse.class);
                        Toast.makeText(ForgotPasswordActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e("ParseError", "Error parsing error body", e);
                        Toast.makeText(ForgotPasswordActivity.this, "An error occurred. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ForgotPasswordResponse> call, Throwable t) {
                Log.e("ForgotPasswordFailure", t.getMessage());
                Toast.makeText(ForgotPasswordActivity.this, "There is some issue.", Toast.LENGTH_SHORT).show();
                loader.setVisibility(View.GONE);
                submitBtn.setVisibility(View.VISIBLE);
            }
        });
    }
}
