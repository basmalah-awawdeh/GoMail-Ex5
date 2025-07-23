package com.example.gomail;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
//activity for composing and sending new mails
public class ComposeActivity extends AppCompatActivity {
    EditText toEdit, subjectEdit, bodyEdit;
    Button sendBtn;
    String authToken;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);
        //initializing form fields and buttons
        toEdit = findViewById(R.id.editTextTo);
        subjectEdit = findViewById(R.id.editTextSubject);
        bodyEdit = findViewById(R.id.editTextBody);
        sendBtn = findViewById(R.id.buttonSend);
        Button cancelBtn = findViewById(R.id.buttonCancel);
        //retrieving auth token from sharedPreferences
        authToken = getSharedPreferences("GoMailPrefs", MODE_PRIVATE).getString("auth_token", null);
        //sending email and cancel activity button listener
        sendBtn.setOnClickListener(v -> sendMail());
        cancelBtn.setOnClickListener(v -> finish());

    }
    //extracting hyperlinks from the body of the email
    private JSONArray extractLinks(String text) {
        JSONArray links = new JSONArray();
        //regex for URLs (matches http(s):// and www.)
        Pattern urlPattern = Pattern.compile(
                "(https?://[\\w\\-.~:/?#\\[\\]@!$&'()*+,;=%]+)|(www\\.[\\w\\-.~:/?#\\[\\]@!$&'()*+,;=%]+)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = urlPattern.matcher(text);
        while (matcher.find()) {
            links.put(matcher.group());
        }
        return links;
    }
    //sending the composed mail to backend via http post request
    private void sendMail() {
        String to = toEdit.getText().toString().trim();
        String subject = subjectEdit.getText().toString().trim();
        String body = bodyEdit.getText().toString().trim();
        //validating required fields
        if (to.isEmpty() || subject.isEmpty() || body.isEmpty()) {
            Toast.makeText(this, "All fields required", Toast.LENGTH_SHORT).show();
            return;
        }
        //performingg network request in a background thread
        new Thread(() -> {
            try {
                //connection to backend api
                URL url = new URL("http://10.0.2.2:3003/api/mails");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
                conn.setDoOutput(true);
                //construct mail json
                JSONObject obj = new JSONObject();
                obj.put("to", to);
                obj.put("subject", subject);
                obj.put("body", body);
                //include the extracted links
                obj.put("links", extractLinks(body));
                //mark it as sent
                obj.put("sent", true);
                //send request
                OutputStream os = conn.getOutputStream();
                os.write(obj.toString().getBytes());
                os.flush();
                //handle response
                int code = conn.getResponseCode();
                if (code == 201) {
                    //sending succeeded
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Mail sent!", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    //if failed ==> show detailed error from backend
                    InputStream es = conn.getErrorStream();
                    StringBuilder sb = new StringBuilder();
                    if (es != null) {
                        int b;
                        while ((b = es.read()) != -1) sb.append((char) b);
                    }
                    String error = sb.toString();
                    runOnUiThread(() -> Toast.makeText(this, "Failed: " + code + "\n" + error, Toast.LENGTH_LONG).show());
                }
                conn.disconnect();
            } catch (Exception e) {
                //exception showing on ui
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}