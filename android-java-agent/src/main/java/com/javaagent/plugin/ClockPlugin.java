package com.javaagent.plugin;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class ClockPlugin implements AgentPlugin {
    @Override public String getId() { return "clock"; }
    @Override public String getName() { return "Date & Time"; }
    @Override public String getDescription() { return "Get the current date and time, optionally in an IANA timezone such as Asia/Karachi."; }

    @Override public JSONObject getParametersSchema() {
        try {
            return new JSONObject().put("type", "object")
                    .put("properties", new JSONObject().put("timezone",
                            new JSONObject().put("type", "string").put("description", "Optional IANA timezone ID")))
                    .put("additionalProperties", false);
        } catch (JSONException error) {
            throw new AssertionError(error);
        }
    }

    @Override public String execute(Context context, JSONObject arguments) {
        String id = arguments.optString("timezone", "").trim();
        TimeZone zone = id.isEmpty() ? TimeZone.getDefault() : TimeZone.getTimeZone(id);
        if (!id.isEmpty() && "GMT".equals(zone.getID()) && !"GMT".equalsIgnoreCase(id)) {
            return "Unknown timezone: " + id;
        }
        SimpleDateFormat format = new SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm:ss z", Locale.US);
        format.setTimeZone(zone);
        return format.format(new Date()) + " (" + zone.getID() + ")";
    }
}
