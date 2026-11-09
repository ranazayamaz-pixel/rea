package com.javaagent.plugin;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** Small local task list with bounded storage and no remote side effects. */
public final class TaskPlugin implements AgentPlugin {
    private static final String PREFS = "private_tasks";
    private static final String TASKS = "tasks";

    @Override public String getId() { return "tasks"; }
    @Override public String getName() { return "Task List"; }
    @Override public String getDescription() { return "Add a local task, list tasks, or mark a task complete by its number."; }

    @Override public JSONObject getParametersSchema() {
        try {
            JSONObject properties = new JSONObject()
                    .put("action", new JSONObject().put("type", "string")
                            .put("enum", new JSONArray().put("add").put("list").put("complete")))
                    .put("text", new JSONObject().put("type", "string"))
                    .put("number", new JSONObject().put("type", "integer").put("minimum", 1));
            return new JSONObject().put("type", "object").put("properties", properties)
                    .put("required", new JSONArray().put("action")).put("additionalProperties", false);
        } catch (JSONException error) {
            throw new AssertionError(error);
        }
    }

    @Override public synchronized String execute(Context context, JSONObject arguments) throws Exception {
        String action = arguments.getString("action");
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray tasks = new JSONArray(prefs.getString(TASKS, "[]"));
        if ("add".equals(action)) {
            String text = arguments.optString("text", "").trim();
            if (text.isEmpty()) return "Task text is required.";
            if (text.length() > 500) return "A task can contain at most 500 characters.";
            if (tasks.length() >= 100) return "Task limit reached (100).";
            tasks.put(new JSONObject().put("id", UUID.randomUUID().toString().substring(0, 8))
                    .put("text", text).put("done", false));
            if (!prefs.edit().putString(TASKS, tasks.toString()).commit()) return "Could not save the task.";
            return "Added task " + tasks.length() + ": " + text;
        }
        if ("complete".equals(action)) {
            int number = arguments.optInt("number", 0);
            if (number < 1 || number > tasks.length()) return "Task number is out of range.";
            JSONObject task = tasks.getJSONObject(number - 1);
            task.put("done", true);
            if (!prefs.edit().putString(TASKS, tasks.toString()).commit()) return "Could not update the task.";
            return "Completed task " + number + ": " + task.optString("text", "");
        }
        if ("list".equals(action)) {
            if (tasks.length() == 0) return "No tasks saved.";
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                result.append(i + 1).append(task.optBoolean("done") ? ". [done] " : ". [open] ")
                        .append(task.optString("text", "")).append('\n');
            }
            return result.toString().trim();
        }
        return "Unsupported task action: " + action;
    }
}
