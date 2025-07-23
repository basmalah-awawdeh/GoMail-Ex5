package com.example.gomail;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

//displaying the details of the mails and alloxing marking to labels or deleting
public class MailDetailActivity extends AppCompatActivity {
    private TextView fromText, subjectText, bodyText, dateText, textTo;
    private String mailId;
    private String token;

    private Button spamBtn, importantBtn, deleteBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mail_detail);

        //toolbar with back button
        Toolbar toolbar = findViewById(R.id.toolbar_detail);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        //initializing the text fields
        fromText = findViewById(R.id.textFrom);
        textTo = findViewById(R.id.textTo);
        subjectText = findViewById(R.id.textSubject);
        dateText = findViewById(R.id.textDate);
        bodyText = findViewById(R.id.textBody);

        // initializing buttons and disable them until the data is loaded
        spamBtn = findViewById(R.id.buttonSpam);
        importantBtn = findViewById(R.id.buttonImportant);
        deleteBtn = findViewById(R.id.buttonDelete);
        spamBtn.setEnabled(false);
        importantBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        //getting token and mailId from intent
        token = getSharedPreferences("GoMailPrefs", MODE_PRIVATE).getString("auth_token", null);
        mailId = getIntent().getStringExtra("mail_id");
        System.out.println("mailId being used: " + mailId);

        if (mailId != null) {
            //fetching mail details from backend
            loadMail(mailId);
        } else {
            Toast.makeText(this, "No mail ID provided", Toast.LENGTH_SHORT).show();
        }
        //buton listeners
        spamBtn.setOnClickListener(v -> markLabel("spam"));
        importantBtn.setOnClickListener(v -> markLabel("important"));
        deleteBtn.setOnClickListener(v -> deleteMail());
    }
//back button enabling
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
//fetching mails details by id
    private void loadMail(String mailId) {
        new Thread(() -> {
            try {
                URL url = new URL("http://10.0.2.2:3003/api/mails/" + mailId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);

                int code = conn.getResponseCode();
                System.out.println("PATCH response code: " + code);

                if (code == 200) {
                    //reading the backend response
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    JSONObject obj = new JSONObject(sb.toString());
                    //setting all fields from json
                    runOnUiThread(() -> {
                        String to = obj.optString("toName", obj.optString("to", ""));
                        textTo.setText("To: " + to);
                        String from = obj.optString("fromName", obj.optString("from", ""));
                        String subject = obj.optString("subject", "(no subject)");
                        String timestamp = obj.optString("timestamp", "");
                        String body = obj.optString("body", "");

                        fromText.setText("From: " + from);
                        subjectText.setText(subject);
                        dateText.setText(formatDate(timestamp));
                        bodyText.setText(body);

                        //enabling the buttons only after loading
                        spamBtn.setEnabled(true);
                        importantBtn.setEnabled(true);
                        deleteBtn.setEnabled(true);
                    });
                } else {
                    runOnUiThread(() -> subjectText.setText("Failed to load mail"));
                }
                conn.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> subjectText.setText("Error: " + e.getMessage()));
            }
        }).start();
    }
//patching mark mail as spam or important request
    private void markLabel(String label) {
        new Thread(() -> {
            try {
                URL url = new URL("http://10.0.2.2:3003/api/mails/" + mailId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PATCH"); // <-- Use PATCH directly
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);
                //sending the label in request body
                JSONObject json = new JSONObject();
                json.put("label", label);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes());
                os.flush();

                int code = conn.getResponseCode();
                conn.disconnect();

                runOnUiThread(() -> {
                    if (code == 204) {
                        Toast.makeText(this, label + " label updated", Toast.LENGTH_SHORT).show();
                        //going back to inbox after update
                        finish();
                    } else {
                        Toast.makeText(this, "Failed to mark: " + code, Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

//handling delete request
    private void deleteMail() {
        new Thread(() -> {
            try {
                URL url = new URL("http://10.0.2.2:3003/api/mails/" + mailId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("Authorization", "Bearer " + token);

                int code = conn.getResponseCode();
                conn.disconnect();

                runOnUiThread(() -> {
                    if (code == 204) {
                        Toast.makeText(this, "Mail deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Failed to delete mail", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
//converting ISO 8601 date string to readable format
    private String formatDate(String isoDate) {
        try {
            java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

            java.util.Date date = isoFormat.parse(isoDate);

            java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("MMM d, yyyy HH:mm");
            outputFormat.setTimeZone(java.util.TimeZone.getDefault());

            return outputFormat.format(date);
            //parsing fails ==> return raw string
        } catch (Exception e) {
            return isoDate;
        }
    }
}
