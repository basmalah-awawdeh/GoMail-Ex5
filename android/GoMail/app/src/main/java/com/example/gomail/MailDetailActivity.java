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

/**
 * This activity displays the details of a selected mail.
 * It allows users to mark the mail as spam or important, delete it, or move it to a custom label.
 */
public class MailDetailActivity extends AppCompatActivity {
    private TextView fromText, subjectText, bodyText, dateText, textTo;
    private String mailId;
    private String token;

    // Label states
    private boolean isSpam = false;
    private boolean isImportant = false;

    private Button spamBtn, importantBtn, deleteBtn, moveLabelBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mail_detail);

        // Setup toolbar with back navigation
        Toolbar toolbar = findViewById(R.id.toolbar_detail);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Initialize views
        fromText = findViewById(R.id.textFrom);
        textTo = findViewById(R.id.textTo);
        subjectText = findViewById(R.id.textSubject);
        dateText = findViewById(R.id.textDate);
        bodyText = findViewById(R.id.textBody);

        // Setup action buttons (initially disabled until data loads)
        spamBtn = findViewById(R.id.buttonSpam);
        importantBtn = findViewById(R.id.buttonImportant);
        deleteBtn = findViewById(R.id.buttonDelete);
        moveLabelBtn = findViewById(R.id.buttonMoveLabel);

        spamBtn.setEnabled(false);
        importantBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        moveLabelBtn.setEnabled(false);

        // Move to custom label handler
        moveLabelBtn.setOnClickListener(v -> showLabelPickerDialog());

        // Get user token and mail ID
        token = getSharedPreferences("GoMailPrefs", MODE_PRIVATE).getString("auth_token", null);
        mailId = getIntent().getStringExtra("mail_id");

        if (mailId != null) {
            loadMail(mailId); // Fetch mail details from backend
        } else {
            Toast.makeText(this, "No mail ID provided", Toast.LENGTH_SHORT).show();
        }

        // Set click listeners for spam, important, and delete buttons
        spamBtn.setOnClickListener(v -> markLabel("spam"));
        importantBtn.setOnClickListener(v -> markLabel("important"));
        deleteBtn.setOnClickListener(v -> deleteMail());
    }

    // Handle back button in toolbar
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /**
     * Fetch mail details from backend and populate UI
     */
    private void loadMail(String mailId) {
        new Thread(() -> {
            try {
                URL url = new URL("http://10.0.2.2:3003/api/mails/" + mailId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);

                int code = conn.getResponseCode();
                System.out.println("PATCH response code: " + code);

                if (code == 200) {
                    // Read response JSON
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject obj = new JSONObject(sb.toString());

                    // Update UI on main thread
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

                        // Check if the mail is labeled as spam or important
                        String userId = getSharedPreferences("GoMailPrefs", MODE_PRIVATE)
                                .getString("user_id", null);
                        JSONObject labelsObj = obj.optJSONObject("labels");
                        if (labelsObj != null && userId != null && labelsObj.has(userId)) {
                            JSONArray userLabels = labelsObj.optJSONArray(userId);
                            if (userLabels != null) {
                                for (int i = 0; i < userLabels.length(); i++) {
                                    String label = userLabels.optString(i);
                                    if (label.equals("spam")) isSpam = true;
                                    if (label.equals("important")) isImportant = true;
                                }
                            }
                        }

                        // Update button text based on current label state
                        spamBtn.setText(isSpam ? "Unspam" : "Spam");
                        importantBtn.setText(isImportant ? "Unimportant" : "Important");

                        // Enable actions
                        spamBtn.setEnabled(true);
                        importantBtn.setEnabled(true);
                        deleteBtn.setEnabled(true);
                        moveLabelBtn.setEnabled(true);
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

    /**
     * Toggle label ("spam" or "important") for the mail using PATCH
     */
    private void markLabel(String label) {
        new Thread(() -> {
            try {
                URL url = new URL("http://10.0.2.2:3003/api/mails/" + mailId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);

                // Add label to JSON body
                JSONObject json = new JSONObject();
                json.put("label", label);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes());
                os.flush();

                int code = conn.getResponseCode();
                conn.disconnect();

                // Show result to user
                runOnUiThread(() -> {
                    if (code == 204) {
                        Toast.makeText(this, label + " label updated", Toast.LENGTH_SHORT).show();
                        finish(); // Go back to inbox
                    } else {
                        Toast.makeText(this, "Failed to mark: " + code, Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * Delete the mail from server
     */
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

    /**
     * Convert ISO date format into a more readable form
     */
    private String formatDate(String isoDate) {
        try {
            java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

            java.util.Date date = isoFormat.parse(isoDate);

            java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("MMM d, yyyy HH:mm");
            outputFormat.setTimeZone(java.util.TimeZone.getDefault());

            return outputFormat.format(date);
        } catch (Exception e) {
            return isoDate;
        }
    }

    /**
     * Fetch available labels from the server and show a picker dialog
     */
    private void showLabelPickerDialog() {
        new Thread(() -> {
            try {
                URL url = new URL("http://10.0.2.2:3003/api/labels");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    JSONArray arr = new JSONArray(sb.toString());

                    // Build a list of label names
                    List<String> labels = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        labels.add(arr.getJSONObject(i).getString("name"));
                    }

                    // Show selection dialog on main thread
                    runOnUiThread(() -> {
                        String[] labelArray = labels.toArray(new String[0]);
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("Move to Label")
                                .setItems(labelArray, (dialog, which) -> {
                                    String selected = labelArray[which];
                                    moveToCustomLabel(selected);
                                })
                                .show();
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error loading labels: "
                        + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * Move this mail to a custom label
     */
    private void moveToCustomLabel(String labelName) {
        new Thread(() -> {
            try {
                URL url = new URL("http://10.0.2.2:3003/api/mails/" + mailId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);

                // Build JSON array of custom labels
                JSONObject json = new JSONObject();
                JSONArray arr = new JSONArray();
                arr.put(labelName);
                json.put("customLabels", arr);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes());
                os.flush();

                int code = conn.getResponseCode();
                conn.disconnect();

                runOnUiThread(() -> {
                    if (code == 204) {
                        Toast.makeText(this, "Moved to " + labelName, Toast.LENGTH_SHORT).show();
                        finish(); // Go back to inbox
                    } else {
                        Toast.makeText(this, "Failed to move: " + code, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
