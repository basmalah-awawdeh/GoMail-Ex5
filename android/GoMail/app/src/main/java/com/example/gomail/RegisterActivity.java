package com.example.gomail;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.InputStream;

import okhttp3.*;

public class RegisterActivity extends AppCompatActivity {
    EditText fullNameEdit, usernameEdit, passwordEdit, confirmPasswordEdit;
    Button registerBtn, pickImageBtn;
    ImageView imagePreview;
    TextView loginLink;
    //code request for image picking
    private static final int PICK_IMAGE_REQUEST = 123;
    //the uri of the imaeg
    private Uri imageUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        //initialzing the views
        fullNameEdit = findViewById(R.id.editTextFullName);
        usernameEdit = findViewById(R.id.editTextRegisterUsername);
        passwordEdit = findViewById(R.id.editTextRegisterPassword);
        confirmPasswordEdit = findViewById(R.id.editTextRegisterConfirmPassword);
        pickImageBtn = findViewById(R.id.buttonPickImage);
        imagePreview = findViewById(R.id.imagePreview);
        registerBtn = findViewById(R.id.buttonRegister);
        loginLink = findViewById(R.id.textLoginLink);
        //picking an image button
        pickImageBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
        });
        //link for going back to login
        loginLink.setOnClickListener(v -> finish());
        //register button click
        registerBtn.setOnClickListener(v -> doRegister());
    }
    //handle results of image picking intent
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            imagePreview.setImageURI(imageUri);
        }
    }
    //registeration handling
    private void doRegister() {
        String fullName = fullNameEdit.getText().toString().trim();
        String username = usernameEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString().trim();
        String confirmPassword = confirmPasswordEdit.getText().toString().trim();
        // pssword have to be with at least 1 letter, 1 digit, 1 special character, and min 8 chars
        String passwordPattern = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$";
        //input validation
        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.matches(passwordPattern)) {
            Toast.makeText(this, "Password must be strong!", Toast.LENGTH_LONG).show();
            return;
        }
        if (imageUri == null) {
            Toast.makeText(this, "Please pick an image", Toast.LENGTH_SHORT).show();
            return;
        }
        //background thread for registirayion
        new Thread(() -> {
            try {
                //reading the image into byte array
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                byte[] imageBytes = new byte[inputStream.available()];
                inputStream.read(imageBytes);
                inputStream.close();

                OkHttpClient client = new OkHttpClient();
                //image request body
                RequestBody imageBody = RequestBody.create(imageBytes, MediaType.parse("image/*"));

                MultipartBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("username", username)
                        .addFormDataPart("password", password)
                        .addFormDataPart("name", fullName)
                        .addFormDataPart("image", "profile.jpg", imageBody)
                        .build();
                //building the post request to the registeration
                Request request = new Request.Builder()
                        .url("http://10.0.2.2:3003/api/users")
                        .post(requestBody)
                        .build();
                //excuting the requset
                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Registered!", Toast.LENGTH_SHORT).show();
                        //back to login
                        finish();
                    });
                } else {
                    String errorMsg = response.body() != null ? response.body().string() : "Unknown";
                    runOnUiThread(() -> Toast.makeText(this, "Register failed: " + errorMsg, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                //exception handling
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
