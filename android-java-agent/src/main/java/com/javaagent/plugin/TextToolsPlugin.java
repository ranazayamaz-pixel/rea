package com.javaagent.plugin;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public final class TextToolsPlugin implements AgentPlugin {
    @Override public String getId() { return "text_tools"; }
    @Override public String getName() { return "Text Tools"; }
    @Override public String getDescription() { return "Count words and characters, trim, or change the case of supplied text."; }

    @Override public JSONObject getParametersSchema() {
        try {
            JSONObject properties = new JSONObject()
                    .put("operation", new JSONObject().put("type", "string").put("enum",
                            new JSONArray().put("word_count").put("character_count")
                                    .put("uppercase").put("lowercase").put("trim")))
                    .put("text", new JSONObject().put("type", "string"));
            return new JSONObject().put("type", "object").put("properties", properties)
                    .put("required", new JSONArray().put("operation").put("text"))
                    .put("additionalProperties", false);
        } catch (JSONException error) {
            throw new AssertionError(error);
        }
    }

    @Override public String execute(Context context, JSONObject arguments) throws Exception {
        String operation = arguments.getString("operation");
        String text = arguments.getString("text");
        if (text.length() > 50000) return "Text exceeds the 50,000 character limit.";
        if ("word_count".equals(operation)) {
            String trimmed = text.trim();
            return "Words: " + (trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length);
        }
        if ("character_count".equals(operation)) {
            return "Characters: " + text.length() + " (including spaces)";
        }
        if ("uppercase".equals(operation)) return text.toUpperCase(Locale.ROOT);
        if ("lowercase".equals(operation)) return text.toLowerCase(Locale.ROOT);
        if ("trim".equals(operation)) return text.trim();
        return "Unsupported text operation: " + operation;
    }
}
