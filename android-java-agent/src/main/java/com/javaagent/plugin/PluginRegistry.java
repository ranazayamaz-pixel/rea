package com.javaagent.plugin;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns plugin registration, opt-in state, schemas and dispatch. */
public final class PluginRegistry {
    private static final String PREFS = "plugin_state";
    private final Map<String, AgentPlugin> plugins = new LinkedHashMap<>();
    private final SharedPreferences preferences;

    public PluginRegistry(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        register(new CalculatorPlugin());
        register(new ClockPlugin());
        register(new NotesPlugin());
        register(new TaskPlugin());
        register(new TextToolsPlugin());
        register(new DevicePlugin());
    }

    public void register(AgentPlugin plugin) {
        String id = plugin.getId();
        if (id == null || !id.matches("[a-z][a-z0-9_]{2,39}")) {
            throw new IllegalArgumentException("Invalid plugin id");
        }
        if (plugins.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate plugin: " + id);
        }
        plugins.put(id, plugin);
    }

    public List<AgentPlugin> all() {
        return Collections.unmodifiableList(new ArrayList<>(plugins.values()));
    }

    public AgentPlugin get(String id) {
        return plugins.get(id);
    }

    public boolean isEnabled(String id) {
        return preferences.getBoolean("enabled_" + id, true);
    }

    public void setEnabled(String id, boolean enabled) {
        if (!plugins.containsKey(id)) throw new IllegalArgumentException("Unknown plugin: " + id);
        preferences.edit().putBoolean("enabled_" + id, enabled).apply();
    }

    public JSONArray enabledToolSchemas() throws JSONException {
        JSONArray tools = new JSONArray();
        for (AgentPlugin plugin : plugins.values()) {
            if (!isEnabled(plugin.getId())) continue;
            JSONObject function = new JSONObject()
                    .put("name", plugin.getId())
                    .put("description", plugin.getDescription())
                    .put("parameters", plugin.getParametersSchema());
            tools.put(new JSONObject().put("type", "function").put("function", function));
        }
        return tools;
    }

    public String execute(Context context, String id, JSONObject arguments) throws Exception {
        AgentPlugin plugin = plugins.get(id);
        if (plugin == null) throw new IllegalArgumentException("Unknown plugin: " + id);
        if (!isEnabled(id)) throw new IllegalStateException(plugin.getName() + " is disabled");
        return plugin.execute(context, arguments == null ? new JSONObject() : arguments);
    }
}
