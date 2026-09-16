package com.example.instaautomation;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.*;

public class TelegramBotBridge {

    private final String botToken;
    private final OkHttpClient client = new OkHttpClient();
    private long lastUpdateId = 0;

    public TelegramBotBridge(String botToken) {
        this.botToken = botToken;
    }

    public interface CommandListener {
        void onNewTargetReceived(String chatId, String instagramUrl, String commentText);
    }

    public void checkForUpdates(CommandListener listener) {
        if (botToken.isEmpty()) return;
        String url = "https://api.telegram.org/bot" + botToken + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=10";

        Request req = new Request.Builder().url(url).build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) return;

                try {
                    JSONObject root = new JSONObject(response.body().string());
                    JSONArray result = root.getJSONArray("result");

                    for (int i = 0; i < result.length(); i++) {
                        JSONObject update = result.getJSONObject(i);
                        lastUpdateId = update.getLong("update_id");

                        if (update.has("message")) {
                            JSONObject msg = update.getJSONObject("message");
                            String chatId = String.valueOf(msg.getJSONObject("chat").getLong("id"));
                            String text = msg.optString("text", "");

                            if (text.contains("instagram.com")) {
                                String[] parts = text.split("\\|");
                                String link = parts[0].trim();
                                String comment = parts.length > 1 ? parts[1].trim() : "";

                                listener.onNewTargetReceived(chatId, link, comment);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    public void sendStatusReport(String chatId, String statusText) {
        if (botToken.isEmpty()) return;
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        RequestBody body = new FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", statusText)
                .build();

        Request req = new Request.Builder().url(url).post(body).build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}
            @Override
            public void onResponse(Call call, Response response) throws IOException {}
        });
    }
}
