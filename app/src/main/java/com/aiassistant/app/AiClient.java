package com.aiassistant.app;

import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class AiClient {
    private static final String ASM_UPLOAD = "https://api.assemblyai.com/v2/upload";
    private static final String ASM_TRANSCRIPT = "https://api.assemblyai.com/v2/transcript";
    private static final String GROQ_CHAT = "https://api.groq.com/openai/v1/chat/completions";

    private final OkHttpClient http;

    public AiClient() {
        http = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    public String transcribe(byte[] wav, String key) throws Exception {
        // 1. Upload
        RequestBody uploadBody = RequestBody.create(wav,
            MediaType.parse("application/octet-stream"));
        Request uploadReq = new Request.Builder()
            .url(ASM_UPLOAD)
            .addHeader("authorization", key)
            .post(uploadBody)
            .build();
        String audioUrl;
        try (Response r = http.newCall(uploadReq).execute()) {
            if (!r.isSuccessful()) throw new IOException("Upload " + r.code() + " " + r.body().string());
            audioUrl = new JSONObject(r.body().string()).getString("upload_url");
        }

        // 2. Request transcript
        JSONObject body = new JSONObject();
        body.put("audio_url", audioUrl);
        body.put("language_code", "ru");
        body.put("language_detection", false);
        JSONArray models = new JSONArray();
        models.put("universal-2");
        body.put("speech_model", "universal-2");
        Request transcriptReq = new Request.Builder()
            .url(ASM_TRANSCRIPT)
            .addHeader("authorization", key)
            .post(RequestBody.create(body.toString(),
                MediaType.parse("application/json")))
            .build();
        String id;
        try (Response r = http.newCall(transcriptReq).execute()) {
            if (!r.isSuccessful()) throw new IOException("Transcript " + r.code() + " " + r.body().string());
            id = new JSONObject(r.body().string()).getString("id");
        }

        // 3. Poll
        for (int i = 0; i < 60; i++) {
            Thread.sleep(2000);
            Request poll = new Request.Builder()
                .url(ASM_TRANSCRIPT + "/" + id)
                .addHeader("authorization", key)
                .get().build();
            try (Response r = http.newCall(poll).execute()) {
                if (!r.isSuccessful()) throw new IOException("Poll " + r.code());
                JSONObject res = new JSONObject(r.body().string());
                String status = res.getString("status");
                if ("completed".equals(status)) {
                    return res.optString("text", "");
                }
                if ("error".equals(status)) {
                    throw new IOException("ASM error: " + res.optString("error"));
                }
            }
        }
        throw new IOException("Timeout при распознавании");
    }

    public String chat(String key, String model, String system,
            JSONArray history) throws Exception {
        JSONArray messages = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", system);
        messages.put(sys);
        for (int i = 0; i < history.length(); i++) {
            JSONObject m = history.getJSONObject(i);
            String role = m.getString("role");
            if ("user".equals(role) || "assistant".equals(role)) {
                messages.put(m);
            }
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        Request req = new Request.Builder()
            .url(GROQ_CHAT)
            .addHeader("Authorization", "Bearer " + key)
            .post(RequestBody.create(body.toString(),
                MediaType.parse("application/json")))
            .build();
        try (Response r = http.newCall(req).execute()) {
            String rb = r.body().string();
            if (!r.isSuccessful()) throw new IOException("Groq " + r.code() + " " + rb);
            JSONObject res = new JSONObject(rb);
            return res.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
        }
    }
}
