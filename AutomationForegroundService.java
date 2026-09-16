package com.example.instaautomation;

import android.app.*;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class AutomationForegroundService extends Service {

    private static final String CHANNEL_ID = "InstaAutoChannel";
    private InstagramEngine instaEngine;
    private TelegramBotBridge tgBridge;
    private SharedPreferences prefs;
    private boolean isRunning = true;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("InstaSettings", MODE_PRIVATE);
        instaEngine = new InstagramEngine(this);
        
        String botToken = prefs.getString("bot_token", "");
        tgBridge = new TelegramBotBridge(botToken);

        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Service InstaAuto Actif")
                .setContentText("Traitement automatique des requêtes Telegram en arrière-plan...")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();

        startForeground(1, notification);
        startListeningLoop();
    }

    private void startListeningLoop() {
        new Thread(() -> {
            while (isRunning) {
                if (instaEngine.isLoggedIn()) {
                    int dailyLimit = prefs.getInt("daily_limit", 50);
                    int currentSuccess = prefs.getInt("stat_success", 0);

                    if (currentSuccess >= dailyLimit) {
                        // Limite journalière atteinte
                        try { Thread.sleep(60000); } catch (InterruptedException ignored) {}
                        continue;
                    }

                    tgBridge.checkForUpdates((chatId, instagramUrl, commentText) -> {
                        String shortcode = instaEngine.extractShortcode(instagramUrl);
                        if (shortcode.isEmpty()) {
                            tgBridge.sendStatusReport(chatId, "❌ NON REÇU: Lien Instagram invalide.");
                            return;
                        }

                        String mediaId = instaEngine.shortcodeToMediaId(shortcode);
                        int minDelay = prefs.getInt("min_delay", 15);
                        int maxDelay = prefs.getInt("max_delay", 35);

                        boolean likeSuccess = instaEngine.executeLike(mediaId, minDelay, maxDelay);
                        boolean commentSuccess = true;

                        if (!commentText.isEmpty()) {
                            commentSuccess = instaEngine.executeComment(mediaId, commentText, minDelay, maxDelay);
                        }

                        if (likeSuccess && commentSuccess) {
                            int successCount = prefs.getInt("stat_success", 0) + 1;
                            prefs.edit().putInt("stat_success", successCount).apply();
                            tgBridge.sendStatusReport(chatId, "✅ REÇU: Action terminée avec succès!");
                        } else {
                            int failCount = prefs.getInt("stat_failed", 0) + 1;
                            prefs.edit().putInt("stat_failed", failCount).apply();
                            tgBridge.sendStatusReport(chatId, "❌ NON REÇU: Échec (Session expirée ou Action Bloquée).");
                        }
                    });
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "InstaAuto Channel", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
