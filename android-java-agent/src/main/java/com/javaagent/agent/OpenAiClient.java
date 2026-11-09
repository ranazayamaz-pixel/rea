package com.javaagent.agent;

import com.javaagent.storage.SecureSettings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Minimal OpenAI-compatible Chat Completions transport with no third-party runtime dependency. */
final class OpenAiClient {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_ERROR_BYTES = 256 * 1024;

    static final class Reply {
        final JSONObject assistantMessage;
        final String content;
        final JSONArray toolCalls;

        Reply(JSONObject assistantMessage) throws JSONException {
            this.assistantMessage = assistantMessage;
            this.content = assistantMessage.isNull("content") ? "" : assistantMessage.optString("content", "");
            this.toolCalls = assistantMessage.optJSONArray("tool_calls");
        }
    }

    private OpenAiClient() { }

    static Reply complete(SecureSettings settings, JSONArray messages, JSONArray tools) throws Exception {
        URL endpoint = endpoint(settings.getBaseUrl());
        JSONObject body = new JSONObject()
                .put("model", settings.getModel())
                .put("messages", messages)
                .put("stream", false);
        if (tools != null && tools.length() > 0) {
            body.put("tools", tools).put("tool_choice", "auto");
        }
        byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(90000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(request.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Java-Agent-Android/1.0");
            String apiKey = settings.getApiKey();
            if (!apiKey.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            OutputStream output = connection.getOutputStream();
            try { output.write(request); } finally { output.close(); }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readBounded(stream, status >= 200 && status < 300
                    ? MAX_RESPONSE_BYTES : MAX_ERROR_BYTES);
            if (status < 200 || status >= 300) {
                throw new IOException("Provider HTTP " + status + ": " + providerError(response));
            }
            JSONObject root = new JSONObject(response);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new JSONException("Provider returned no choices");
            }
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) throw new JSONException("Provider returned no assistant message");
            return new Reply(message);
        } finally {
            connection.disconnect();
        }
    }

    static URL endpoint(String configuredBase) throws Exception {
        String value = configuredBase == null ? "" : configuredBase.trim();
        if (value.isEmpty()) value = SecureSettings.DEFAULT_BASE_URL;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        URI uri = new URI(value);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Enter a valid API base URL without query parameters");
        }
        boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "10.0.2.2".equals(host);
        if (!"https".equalsIgnoreCase(scheme) && !("http".equalsIgnoreCase(scheme) && local)) {
            throw new IllegalArgumentException("HTTPS is required (HTTP is allowed only for local model servers)");
        }
        String result = value.endsWith("/chat/completions") ? value : value + "/chat/completions";
        return new URL(result);
    }

    private static String providerError(String response) {
        try {
            JSONObject root = new JSONObject(response);
            JSONObject error = root.optJSONObject("error");
            String message = error != null ? error.optString("message", "") : root.optString("message", "");
            if (!message.isEmpty()) return message.length() > 800 ? message.substring(0, 800) : message;
        } catch (Exception ignored) { }
        String compact = response.replaceAll("\\s+", " ").trim();
        if (compact.isEmpty()) return "No error details returned";
        return compact.substring(0, Math.min(compact.length(), 800));
    }

    private static String readBounded(InputStream stream, int maximum) throws IOException {
        if (stream == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[8192];
            while (true) {
                int count = stream.read(buffer);
                if (count < 0) break;
                if (output.size() + count > maximum) throw new IOException("Provider response is too large");
                output.write(buffer, 0, count);
            }
        } finally {
            stream.close();
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
