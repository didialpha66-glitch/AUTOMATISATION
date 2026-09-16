package com.example.instaautomation;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import okhttp3.*;

public class InstagramEngine {

    private final OkHttpClient client;
    private final SharedPreferences prefs;
    private String csrfToken = "";
    private String sessionId = "";
    
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36";
    private final Map<String, List<Cookie>> cookieStore = new HashMap<>();

    public InstagramEngine(Context context) {
        this.prefs = context.getSharedPreferences("InstaSettings", Context.MODE_PRIVATE);
        this.sessionId = prefs.getString("sessionid", "");
        this.csrfToken = prefs.getString("csrftoken", "");

        this.client = new OkHttpClient.Builder()
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url.host(), cookies);
                        for (Cookie cookie : cookies) {
                            if (cookie.name().equals("csrftoken")) csrfToken = cookie.value();
                            if (cookie.name().equals("sessionid")) sessionId = cookie.value();
                        }
                        if (!sessionId.isEmpty()) {
                            prefs.edit().putString("sessionid", sessionId)
                                        .putString("csrftoken", csrfToken)
                                        .apply();
                        }
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url.host());
                        return cookies != null ? cookies : new ArrayList<>();
                    }
                })
                .build();
    }

    public boolean isLoggedIn() {
        return !sessionId.isEmpty();
    }

    public interface ActionCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public void login(String username, String password, ActionCallback callback) {
        Request initialReq = new Request.Builder()
                .url("https://www.instagram.com/accounts/login/")
                .addHeader("User-Agent", USER_AGENT)
                .build();

        client.newCall(initialReq).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Erreur GET Initiale: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (csrfToken.isEmpty()) {
                    callback.onError("CSRFToken initial introuvable.");
                    return;
                }

                long timestamp = System.currentTimeMillis() / 1000;
                String encPassword = "#PWD_INSTAGRAM:0:" + timestamp + ":" + password;

                RequestBody body = new FormBody.Builder()
                        .add("username", username)
                        .add("enc_password", encPassword)
                        .add("queryParams", "{}")
                        .add("optIntoOneTap", "false")
                        .build();

                Request loginReq = new Request.Builder()
                        .url("https://www.instagram.com/api/v1/web/accounts/login/ajax/")
                        .post(body)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("X-CSRFToken", csrfToken)
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .addHeader("X-Instagram-AJAX", "1")
                        .addHeader("Referer", "https://www.instagram.com/accounts/login/")
                        .build();

                client.newCall(loginReq).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        callback.onError("Erreur Réseau Connexion: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String res = response.body().string();
                        try {
                            JSONObject json = new JSONObject(res);
                            if (json.optBoolean("authenticated", false)) {
                                callback.onSuccess("Connexion Réussie!");
                            } else if (json.toString().contains("checkpoint_required")) {
                                callback.onError("SÉCURITÉ DÉTECTÉE: Vérification 2FA requise.");
                            } else {
                                callback.onError("Échec de connexion: " + json.optString("message"));
                            }
                        } catch (Exception e) {
                            callback.onError("Erreur Analyse JSON: " + e.getMessage());
                        }
                    }
                });
            }
        });
    }

    public String extractShortcode(String url) {
        if (url.contains("/p/")) {
            return url.split("/p/")[1].split("/")[0];
        } else if (url.contains("/reel/")) {
            return url.split("/reel/")[1].split("/")[0];
        }
        return "";
    }

    public String shortcodeToMediaId(String shortcode) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        long mediaId = 0;
        for (int i = 0; i < shortcode.length(); i++) {
            char letter = shortcode.charAt(i);
            mediaId = (mediaId * 64) + alphabet.indexOf(letter);
        }
        return String.valueOf(mediaId);
    }

    public void applyHumanSafetyDelay(int minSec, int maxSec) {
        try {
            int randomDelayMs = ThreadLocalRandom.current().nextInt(minSec * 1000, maxSec * 1000);
            Thread.sleep(randomDelayMs);
        } catch (InterruptedException ignored) {}
    }

    public boolean executeLike(String mediaId, int minDelay, int maxDelay) {
        applyHumanSafetyDelay(minDelay, maxDelay);

        String url = "https://www.instagram.com/api/v1/web/likes/" + mediaId + "/like/";
        RequestBody body = RequestBody.create("", MediaType.parse("application/x-www-form-urlencoded"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-CSRFToken", csrfToken)
                .addHeader("X-Instagram-AJAX", "1")
                .addHeader("Cookie", "sessionid=" + sessionId + "; csrftoken=" + csrfToken + ";")
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    public boolean executeComment(String mediaId, String commentText, int minDelay, int maxDelay) {
        applyHumanSafetyDelay(minDelay, maxDelay);

        String url = "https://www.instagram.com/api/v1/web/comments/" + mediaId + "/add/";
        RequestBody body = new FormBody.Builder()
                .add("comment_text", commentText)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-CSRFToken", csrfToken)
                .addHeader("X-Instagram-AJAX", "1")
                .addHeader("Cookie", "sessionid=" + sessionId + "; csrftoken=" + csrfToken + ";")
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }
}
