package com.javaagent.agent;

import android.content.Context;

import com.javaagent.model.ChatMessage;
import com.javaagent.plugin.PluginRegistry;
import com.javaagent.storage.SecureSettings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded model/tool loop plus a useful no-network local router. */
public final class AgentEngine {
    private static final int MAX_TOOL_ROUNDS = 6;
    private static final Pattern PERCENT_OF = Pattern.compile("(?i)([0-9]+(?:\\.[0-9]+)?)\\s*%\\s*of\\s*([0-9]+(?:\\.[0-9]+)?)");
    private final Context context;
    private final SecureSettings settings;
    private final PluginRegistry plugins;

    public interface Listener {
        void onStatus(String status);
    }

    public AgentEngine(Context context, SecureSettings settings, PluginRegistry plugins) {
        this.context = context.getApplicationContext();
        this.settings = settings;
        this.plugins = plugins;
    }

    public String send(String prompt, List<ChatMessage> history, Listener listener) throws Exception {
        if (settings.isOffline()) return offline(prompt, listener);
        if (settings.getBaseUrl().equals(SecureSettings.DEFAULT_BASE_URL) && !settings.hasApiKey()) {
            return offline(prompt, listener) + "\n\nOffline mode is active because no API key is configured. Add your provider in Settings for open-ended AI chat.";
        }

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content",
                "You are Java Agent, a concise and helpful Android assistant. Use available local tools when they improve accuracy. "
                        + "Validate tool results before reporting them. Never claim an action happened unless its tool result confirms it. "
                        + "Do not request passwords or expose secrets. Respond in the user's language."));
        int start = Math.max(0, history.size() - 30);
        for (int i = start; i < history.size(); i++) {
            ChatMessage message = history.get(i);
            messages.put(new JSONObject().put("role", message.role).put("content", message.content));
        }
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        JSONArray tools = plugins.enabledToolSchemas();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            listener.onStatus(round == 0 ? "Thinking…" : "Reviewing tool results…");
            OpenAiClient.Reply reply = OpenAiClient.complete(settings, messages, tools);
            messages.put(reply.assistantMessage);
            if (reply.toolCalls == null || reply.toolCalls.length() == 0) {
                String content = reply.content.trim();
                if (content.isEmpty()) throw new IllegalStateException("The provider returned an empty response");
                return content;
            }
            for (int i = 0; i < reply.toolCalls.length(); i++) {
                JSONObject call = reply.toolCalls.getJSONObject(i);
                JSONObject function = call.getJSONObject("function");
                String name = function.getString("name");
                String callId = call.optString("id", "call_" + round + "_" + i);
                String result;
                try {
                    String rawArguments = function.optString("arguments", "{}");
                    JSONObject arguments = new JSONObject(rawArguments);
                    if (plugins.get(name) != null) listener.onStatus("Running " + plugins.get(name).getName() + "…");
                    result = plugins.execute(context, name, arguments);
                } catch (Exception error) {
                    result = "Tool error: " + safeMessage(error);
                }
                if (result.length() > 16000) result = result.substring(0, 16000) + "\n[truncated]";
                messages.put(new JSONObject().put("role", "tool").put("tool_call_id", callId)
                        .put("name", name).put("content", result));
            }
        }
        throw new IllegalStateException("Tool step limit reached. Narrow the request and try again.");
    }

    private String offline(String prompt, Listener listener) throws Exception {
        String text = prompt.trim();
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        JSONObject arguments = new JSONObject();

        if (lower.matches("^(what('s| is) )?(the )?(date|time|date and time)( now)?[ ?.!]*$")
                || lower.contains("current time") || lower.contains("current date")) {
            listener.onStatus("Running Date & Time…");
            return plugins.execute(context, "clock", arguments);
        }
        if (lower.contains("device status") || lower.contains("device info") || lower.contains("battery level")) {
            listener.onStatus("Running Device Status…");
            return plugins.execute(context, "device_info", arguments);
        }
        if (lower.matches("^(show|list|read)( my)? notes[ .!]*$") || lower.equals("notes")) {
            listener.onStatus("Running Private Notes…");
            return plugins.execute(context, "notes", arguments.put("action", "list"));
        }
        Matcher note = Pattern.compile("(?i)^(?:save|remember)(?: a| this)? note(?: saying| that)?\\s*[: -]?\\s+(.+)$").matcher(text);
        if (note.matches()) {
            listener.onStatus("Running Private Notes…");
            return plugins.execute(context, "notes", arguments.put("action", "save").put("text", note.group(1).trim()));
        }
        if (lower.matches("^(show|list)( my)? tasks[ .!]*$") || lower.equals("tasks") || lower.equals("to do list")) {
            listener.onStatus("Running Task List…");
            return plugins.execute(context, "tasks", arguments.put("action", "list"));
        }
        Matcher task = Pattern.compile("(?i)^(?:add|create)(?: a| new)? task\\s*[: -]?\\s+(.+)$").matcher(text);
        if (task.matches()) {
            listener.onStatus("Running Task List…");
            return plugins.execute(context, "tasks", arguments.put("action", "add").put("text", task.group(1).trim()));
        }
        if (lower.startsWith("word count ") || lower.startsWith("count words ")) {
            String value = text.substring(lower.startsWith("word count ") ? 11 : 12).trim();
            listener.onStatus("Running Text Tools…");
            return plugins.execute(context, "text_tools", arguments.put("operation", "word_count").put("text", value));
        }

        String expression = text.replaceAll("(?i)^(?:calculate|compute|evaluate|what is|what's)\\s+", "")
                .replaceAll("[=?\\s]+$", "").trim();
        Matcher percent = PERCENT_OF.matcher(expression);
        if (percent.find()) expression = percent.replaceAll("($1/100)*$2");
        if (expression.matches("[0-9a-zA-Z+\\-*/%^().,\\s]+") && expression.matches(".*[0-9].*")
                && expression.matches(".*[+\\-*/%^].*")) {
            listener.onStatus("Running Calculator…");
            try {
                return expression + " = " + plugins.execute(context, "calculator",
                        arguments.put("expression", expression));
            } catch (Exception error) {
                return "I could not evaluate that expression: " + safeMessage(error);
            }
        }
        return "I’m ready offline. Try:\n"
                + "• Calculate 18% of 250\n"
                + "• Save a note: call Sam tomorrow\n"
                + "• Show my notes\n"
                + "• Add task: finish the project\n"
                + "• Show my tasks\n"
                + "• What is the time?\n"
                + "• Device status\n\n"
                + "Connect an OpenAI-compatible API in Settings for open-ended conversation and automatic tool use.";
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
