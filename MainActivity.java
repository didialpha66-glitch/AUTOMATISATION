package com.example.instaautomation;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private EditText etUser, etPass, etBotToken, etAdminChatId, etMinDelay, etMaxDelay, etDailyLimit;
    private Spinner spAccounts;
    private TextView tvSuccess, tvFailed, tvLogConsole;
    private Button btnAddAccount, btnToggle;

    private SharedPreferences prefs;
    private List<String> accountList = new ArrayList<>();
    private ArrayAdapter<String> accountAdapter;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("InstaSettings", MODE_PRIVATE);

        etUser = findViewById(R.id.etUser);
        etPass = findViewById(R.id.etPass);
        etBotToken = findViewById(R.id.etBotToken);
        etAdminChatId = findViewById(R.id.etAdminChatId);
        etMinDelay = findViewById(R.id.etMinDelay);
        etMaxDelay = findViewById(R.id.etMaxDelay);
        etDailyLimit = findViewById(R.id.etDailyLimit);
        spAccounts = findViewById(R.id.spAccounts);
        tvSuccess = findViewById(R.id.tvSuccess);
        tvFailed = findViewById(R.id.tvFailed);
        tvLogConsole = findViewById(R.id.tvLogConsole);
        btnAddAccount = findViewById(R.id.btnAddAccount);
        btnToggle = findViewById(R.id.btnToggle);

        tvLogConsole.setMovementMethod(new ScrollingMovementMethod());

        accountAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, accountList);
        spAccounts.setAdapter(accountAdapter);

        loadSavedSettings();

        btnAddAccount.setOnClickListener(v -> {
            String u = etUser.getText().toString().trim();
            String p = etPass.getText().toString().trim();

            if (!u.isEmpty() && !p.isEmpty()) {
                saveAccount(u, p);
                etUser.setText("");
                etPass.setText("");
                appendLog("Compte ajouté: " + u);
            } else {
                Toast.makeText(this, "Veuillez remplir le nom d'utilisateur et le mot de passe!", Toast.LENGTH_SHORT).show();
            }
        });

        btnToggle.setOnClickListener(v -> {
            if (!isServiceRunning) {
                saveAllSettings();
                startAutomation();
            } else {
                stopAutomation();
            }
        });
    }

    private void saveAccount(String user, String pass) {
        try {
            JSONArray arr = new JSONArray(prefs.getString("accounts_json", "[]"));
            JSONObject obj = new JSONObject();
            obj.put("user", user);
            obj.put("pass", pass);
            arr.put(obj);

            prefs.edit().putString("accounts_json", arr.toString()).apply();
            updateAccountSpinner();
        } catch (Exception ignored) {}
    }

    private void updateAccountSpinner() {
        accountList.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString("accounts_json", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                accountList.add(arr.getJSONObject(i).getString("user"));
            }
            accountAdapter.notifyDataSetChanged();
        } catch (Exception ignored) {}
    }

    private void saveAllSettings() {
        prefs.edit()
                .putString("bot_token", etBotToken.getText().toString().trim())
                .putString("admin_chat_id", etAdminChatId.getText().toString().trim())
                .putInt("min_delay", Integer.parseInt(etMinDelay.getText().toString().trim()))
                .putInt("max_delay", Integer.parseInt(etMaxDelay.getText().toString().trim()))
                .putInt("daily_limit", Integer.parseInt(etDailyLimit.getText().toString().trim()))
                .putString("selected_account", spAccounts.getSelectedItem() != null ? spAccounts.getSelectedItem().toString() : "")
                .apply();
        appendLog("Paramètres sauvegardés avec succès.");
    }

    private void loadSavedSettings() {
        etBotToken.setText(prefs.getString("bot_token", ""));
        etAdminChatId.setText(prefs.getString("admin_chat_id", ""));
        etMinDelay.setText(String.valueOf(prefs.getInt("min_delay", 15)));
        etMaxDelay.setText(String.valueOf(prefs.getInt("max_delay", 35)));
        etDailyLimit.setText(String.valueOf(prefs.getInt("daily_limit", 50)));

        tvSuccess.setText("✅ REÇU: " + prefs.getInt("stat_success", 0));
        tvFailed.setText("❌ NON REÇU: " + prefs.getInt("stat_failed", 0));

        updateAccountSpinner();
    }

    private void startAutomation() {
        Intent serviceIntent = new Intent(this, AutomationForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        isServiceRunning = true;
        btnToggle.setText("ARRÊTER LE BOT");
        btnToggle.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
        appendLog(">>> DÉMARRAGE DU SERVICE D'AUTOMATISATION...");
    }

    private void stopAutomation() {
        Intent serviceIntent = new Intent(this, AutomationForegroundService.class);
        stopService(serviceIntent);
        isServiceRunning = false;
        btnToggle.setText("DÉMARRER LE BOT");
        btnToggle.setBackgroundTintList(getColorStateList(android.R.color.holo_green_dark));
        appendLog(">>> SERVICE D'AUTOMATISATION ARRÊTÉ.");
    }

    private void appendLog(String logText) {
        tvLogConsole.append("\n> " + logText);
    }
}
