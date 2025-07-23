package com.example.gomail;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    EditText usernameEdit, passwordEdit;
    Button loginBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //getting the references from layout
        usernameEdit = findViewById(R.id.editTextUsername);
        passwordEdit = findViewById(R.id.editTextPassword);
        loginBtn = findViewById(R.id.buttonLogin);
        //listener for the login button
        loginBtn.setOnClickListener(v -> doLogin());
        //listener to the register link
        TextView registerLink = findViewById(R.id.textRegisterLink);
        registerLink.setOnClickListener(v -> {
            //navigating to registerActivity
            Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
    //login requedt
    private void doLogin() {
        String username = usernameEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString().trim();

        new Thread(() -> {
            try {
                //http post request for login
                URL url = new URL("http://10.0.2.2:3003/api/users/tokens");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset= UTF-8");
                conn.setDoOutput(true);
                //creating json body
                JSONObject jsonParam = new JSONObject();
                jsonParam.put("username", username);
                jsonParam.put("password", password);
                //sending json to server
                OutputStream os = conn.getOutputStream();
                os.write(jsonParam.toString().getBytes());
                os.flush();
                //getting server response code
                int responseCode = conn.getResponseCode();
                //login succeeded
                if (responseCode == 200) {
                    InputStream is = conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int b;
                    while ((b = is.read()) != -1) {
                        sb.append((char) b);
                    }
                    //parsing the json responde
                    JSONObject respJson = new JSONObject(sb.toString());
                    String token = respJson.getString("token");
                    JSONObject userObj = respJson.getJSONObject("user");
                    String userId = userObj.getString("id");
                    //saving tokens
                    runOnUiThread(() -> {
                        getSharedPreferences("GoMailPrefs", MODE_PRIVATE)
                                .edit()
                                .putString("auth_token", token)
                                .putString("user_id", userId)
                                .apply();
                        //showing success and going to inbox
                        Toast.makeText(MainActivity.this, "Login success!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(MainActivity.this, InboxActivity.class);
                        startActivity(intent);
                        //preventing going back to login
                        finish();
                    });
                    //login failed
                } else {
                    String errorMsg = "Username or password is incorrect";
                    try {
                        InputStream es = conn.getErrorStream();
                        if (es != null) {
                            StringBuilder sb = new StringBuilder();
                            int b;
                            while ((b = es.read()) != -1) {
                                sb.append((char) b);
                            }
                            String errorBody = sb.toString();
                            if (!errorBody.isEmpty()) {
                                JSONObject errJson = new JSONObject(errorBody);
                                if (errJson.has("error")) {
                                    errorMsg = errJson.getString("error");
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    String finalErrorMsg = errorMsg;
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, finalErrorMsg, Toast.LENGTH_SHORT).show());
                }
                //close connection
                conn.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
