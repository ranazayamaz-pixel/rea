package com.javaagent.plugin;

import android.content.Context;

import org.json.JSONObject;

/**
 * Stable extension contract for local agent capabilities.
 * Implementations must validate all model-supplied arguments and bound their work.
 */
public interface AgentPlugin {
    String getId();

    String getName();

    String getDescription();

    /** JSON Schema object describing the execute arguments. */
    JSONObject getParametersSchema();

    /** Execute synchronously on the agent worker, never on Android's UI thread. */
    String execute(Context context, JSONObject arguments) throws Exception;
}
