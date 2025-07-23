package com.example.gomail;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
//displaying inbox view with search bar and emails list
public class InboxActivity extends AppCompatActivity {
    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private Toolbar toolbar;
    private RecyclerView recyclerViewMails;
    private EditText editTextSearch;
    private InboxAdapter adapter;
    //data
    private ArrayList<MailItem> mailList = new ArrayList<>();
    private String authToken, userId;
    private String currentFolder = "inbox";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);
        recyclerViewMails = findViewById(R.id.recyclerViewMails);
        editTextSearch = findViewById(R.id.editTextSearch);
        //setting the toolbar and the navigation drawer
        setSupportActionBar(toolbar);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        //setting up mails list recycleview
        recyclerViewMails.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InboxAdapter(mailList, mail -> {
            //opening mailDetailActivity on click
            Intent intent = new Intent(InboxActivity.this, MailDetailActivity.class);
            intent.putExtra("mail_id", mail.id);
            startActivity(intent);
        });
        recyclerViewMails.setAdapter(adapter);
        //retreving stored tokens and ids
        SharedPreferences prefs = getSharedPreferences("GoMailPrefs", MODE_PRIVATE);
        authToken = prefs.getString("auth_token", null);
        userId = prefs.getString("user_id", null);

        loadUserLabels();
        //handling folder selection from the navigation drawer
        navView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawers();
            int id = item.getItemId();
            if (id == R.id.nav_inbox) currentFolder = "inbox";
            else if (id == R.id.nav_sent) currentFolder = "sent";
            else if (id == R.id.nav_spam) currentFolder = "spam";
            else if (id == R.id.nav_important) currentFolder = "important";
            else if (id == R.id.nav_drafts) currentFolder = "draft";
            else if (id == R.id.nav_logout) {
                handleLogout();
                return true;
            } else currentFolder = item.getTitle().toString();
            loadMails(currentFolder, editTextSearch.getText().toString().trim());
            return true;
        });
        //searching after pressing enter
        editTextSearch.setOnEditorActionListener((v, actionId, event) -> {
            loadMails(currentFolder, v.getText().toString().trim());
            return true;
        });
        //composing a new mail
        FloatingActionButton fab = findViewById(R.id.fab_compose);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(InboxActivity.this, ComposeActivity.class);
            startActivity(intent);
        });
        //loaidng inbox on startup
        loadMails("inbox", "");
    }
//reload mails when coming back
    @Override
    protected void onResume() {
        super.onResume();
        loadMails(currentFolder, editTextSearch.getText().toString().trim());
    }
//claring sessions and returning to login
    private void handleLogout() {
        getSharedPreferences("GoMailPrefs", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(InboxActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
//featching all mails from server
    private void loadMails(String folder, String searchQuery) {
        new Thread(() -> {
            try {
                URL url = new URL("http://10.0.2.2:3003/api/mails");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                if (authToken != null)
                    conn.setRequestProperty("Authorization", "Bearer " + authToken);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONArray arr = new JSONArray(sb.toString());
                    ArrayList<MailItem> mails = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        //mail fields
                        String id = obj.optString("_id", "");
                        String subject = obj.optString("subject", "(no subject)");
                        String body = obj.optString("body", "");
                        String date = obj.optString("timestamp", "");
                        String fromId = obj.optString("from", "");
                        String fromName = obj.optString("fromName", "");
                        String toId = obj.optString("to", "");
                        String toName = obj.optString("toName", "");
                        boolean sent = obj.optBoolean("sent", false);
                        JSONObject labelsObj = obj.optJSONObject("labels");
                        JSONObject customLabelsObj = obj.optJSONObject("customLabels");
                        boolean isSpam = false, isImportant = false;
                        //checking standard system labes
                        if (labelsObj != null && userId != null && labelsObj.has(userId)) {
                            JSONArray labelsArr = labelsObj.optJSONArray(userId);
                            for (int j = 0; labelsArr != null && j < labelsArr.length(); j++) {
                                String l = labelsArr.getString(j);
                                if (l.equals("spam")) isSpam = true;
                                if (l.equals("important")) isImportant = true;
                            }
                        }

                        boolean show = false;
                        //determine visibility based on the folder
                        switch (folder) {
                            case "inbox":
                                show = (toId.equals(userId) && sent && !isSpam);
                                break;
                            case "sent":
                                show = (fromId.equals(userId) && sent);
                                break;
                            case "spam":
                                show = (toId.equals(userId) && isSpam);
                                break;
                            case "important":
                                show = isImportant && (toId.equals(userId) || fromId.equals(userId));
                                break;
                            case "draft":
                                show = (fromId.equals(userId) && !sent);
                                break;
                            default:
                                show = false;
                                //checking system labels
                                if (labelsObj != null && labelsObj.has(userId)) {
                                    JSONArray userLabels = labelsObj.optJSONArray(userId);
                                    if (userLabels != null) {
                                        for (int j = 0; j < userLabels.length(); j++) {
                                            String label = userLabels.getString(j);
                                            if (label.equalsIgnoreCase(folder)) {
                                                show = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                //checking customed labels
                                if (!show && customLabelsObj != null && customLabelsObj.has(userId)) {
                                    JSONArray userCustomLabels = customLabelsObj.optJSONArray(userId);
                                    if (userCustomLabels != null) {
                                        for (int j = 0; j < userCustomLabels.length(); j++) {
                                            String label = userCustomLabels.getString(j);
                                            if (label.equalsIgnoreCase(folder)) {
                                                show = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                break;
                        }
                        //applying search filter
                        if (!searchQuery.isEmpty()) {
                            if (!(subject.toLowerCase().contains(searchQuery.toLowerCase()) ||
                                    body.toLowerCase().contains(searchQuery.toLowerCase()))) continue;
                        }
                        //if visible ==> adding to lis
                        if (show) {
                            mails.add(new MailItem(id, subject, fromName.isEmpty() ? fromId : fromName, body, date, folder));
                        }
                    }
                    //updating ui with filtered mail list
                    runOnUiThread(() -> {
                        mailList.clear();
                        mailList.addAll(mails);
                        adapter.notifyDataSetChanged();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Failed: " + responseCode, Toast.LENGTH_SHORT).show());
                }
                conn.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
//fetching all available customed labels
    private void loadUserLabels() {
        new Thread(() -> {
            try {
                URL url = new URL("http://10.0.2.2:3003/api/labels");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + authToken);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONArray arr = new JSONArray(sb.toString());

                    runOnUiThread(() -> {
                        Menu menu = navView.getMenu();
                        menu.removeGroup(1001);
                        menu.addSubMenu("Labels");

                        try {
                            for (int i = 0; i < arr.length(); i++) {
                                String label = arr.getJSONObject(i).getString("name");
                                //adding each label to the drwaer
                                menu.add(1001, Menu.NONE, Menu.NONE, label)
                                        .setOnMenuItemClickListener(item -> {
                                            currentFolder = label;
                                            loadMails(label, editTextSearch.getText().toString().trim());
                                            drawerLayout.closeDrawers();
                                            return true;
                                        });
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(this, "Failed to load labels", Toast.LENGTH_SHORT).show();
                        }
                        //option for creating a new label
                        menu.add(1001, Menu.NONE, Menu.NONE, "➕ Create New Label")
                                .setOnMenuItemClickListener(item -> {
                                    showCreateLabelDialog();
                                    return true;
                                });
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error loading labels: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
//openning dialog foe the user in oredr to create a label
    private void showCreateLabelDialog() {
        EditText input = new EditText(this);
        input.setHint("Label name");

        new AlertDialog.Builder(this)
                .setTitle("New Label")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String labelName = input.getText().toString().trim();
                    if (!labelName.isEmpty()) {
                        createLabel(labelName);
                    } else {
                        Toast.makeText(this, "Label name cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
//sending post request to create a label in backend
    private void createLabel(String name) {
        new Thread(() -> {
            try {
                URL url = new URL("http://10.0.2.2:3003/api/labels");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                //send label mail in json
                JSONObject json = new JSONObject();
                json.put("name", name);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes());
                os.flush();

                int code = conn.getResponseCode();
                conn.disconnect();

                if (code == 201) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Label created", Toast.LENGTH_SHORT).show();
                        //refresh label drawer
                        loadUserLabels();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to create label", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}