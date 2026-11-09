package com.javaagent.plugin;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Private, append-only notes. There is deliberately no model-triggered clear operation. */
public final class NotesPlugin implements AgentPlugin {
    private static final String PREFS = "private_notes";
    private static final String NOTES = "notes";

    @Override public String getId() { return "notes"; }
    @Override public String getName() { return "Private Notes"; }
    @Override public String getDescription() { return "Save a short private note on this device or list saved notes."; }

    @Override public JSONObject getParametersSchema() {
        try {
            JSONObject properties = new JSONObject()
                    .put("action", new JSONObject().put("type", "string")
                            .put("enum", new JSONArray().put("save").put("list")))
                    .put("text", new JSONObject().put("type", "string")
                            .put("description", "Note text; required when action is save"));
            return new JSONObject().put("type", "object").put("properties", properties)
                    .put("required", new JSONArray().put("action")).put("additionalProperties", false);
        } catch (JSONException error) {
            throw new AssertionError(error);
        }
    }

    @Override public synchronized String execute(Context context, JSONObject arguments) throws Exception {
        String action = arguments.getString("action");
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray notes = read(prefs);
        if ("save".equals(action)) {
            String text = arguments.optString("text", "").trim();
            if (text.isEmpty()) return "Note text is required.";
            if (text.length() > 4000) return "A note can contain at most 4,000 characters.";
            if (notes.length() >= 50) return "Note limit reached (50). Export or remove notes from app storage before adding more.";
            String id = UUID.randomUUID().toString().substring(0, 8);
            String created = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
            notes.put(new JSONObject().put("id", id).put("text", text).put("created", created));
            if (!prefs.edit().putString(NOTES, notes.toString()).commit()) return "Could not save the note.";
            return "Saved note " + id + ": " + text;
        }
        if ("list".equals(action)) {
            if (notes.length() == 0) return "No notes saved.";
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < notes.length(); i++) {
                JSONObject note = notes.getJSONObject(i);
                result.append(i + 1).append(". [").append(note.optString("created", ""))
                        .append("] ").append(note.optString("text", "")).append('\n');
            }
            return result.toString().trim();
        }
        return "Unsupported notes action: " + action;
    }

    private static JSONArray read(SharedPreferences prefs) throws JSONException {
        String raw = prefs.getString(NOTES, "[]");
        JSONArray value = new JSONArray(raw);
        return value == null ? new JSONArray() : value;
    }
}
