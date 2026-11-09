package com.javaagent.model;

import org.json.JSONException;
import org.json.JSONObject;

/** A durable user-visible chat message. Internal tool messages are intentionally not persisted. */
public final class ChatMessage {
    public final String role;
    public final String content;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("role", role).put("content", content);
    }

    public static ChatMessage fromJson(JSONObject value) throws JSONException {
        String role = value.getString("role");
        if (!"user".equals(role) && !"assistant".equals(role)) {
            throw new JSONException("Unsupported chat role");
        }
        return new ChatMessage(role, value.getString("content"));
    }
}
