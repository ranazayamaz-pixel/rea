package com.javaagent.plugin;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

/** Returns coarse, non-sensitive device diagnostics; no identifiers or location are exposed. */
public final class DevicePlugin implements AgentPlugin {
    @Override public String getId() { return "device_info"; }
    @Override public String getName() { return "Device Status"; }
    @Override public String getDescription() { return "Read Android version, device model, battery level and available app memory."; }

    @Override public JSONObject getParametersSchema() {
        try {
            return new JSONObject().put("type", "object")
                    .put("properties", new JSONObject()).put("additionalProperties", false);
        } catch (JSONException error) {
            throw new AssertionError(error);
        }
    }

    @Override public String execute(Context context, JSONObject arguments) {
        JSONObject result = new JSONObject();
        try {
            result.put("manufacturer", Build.MANUFACTURER);
            result.put("model", Build.MODEL);
            result.put("android", Build.VERSION.RELEASE);
            result.put("sdk", Build.VERSION.SDK_INT);
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) result.put("battery_percent", Math.round(level * 100f / scale));
            }
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(memory);
                result.put("available_ram_mb", memory.availMem / (1024 * 1024));
                result.put("low_memory", memory.lowMemory);
            }
        } catch (JSONException error) {
            throw new AssertionError(error);
        }
        return result.toString();
    }
}
