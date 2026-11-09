package com.javaagent;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.javaagent.agent.AgentEngine;
import com.javaagent.model.ChatMessage;
import com.javaagent.plugin.AgentPlugin;
import com.javaagent.plugin.PluginRegistry;
import com.javaagent.storage.ChatStore;
import com.javaagent.storage.SecureSettings;
import com.javaagent.ui.Ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, Button> tabs = new LinkedHashMap<>();
    private SecureSettings settings;
    private ChatStore store;
    private PluginRegistry plugins;
    private AgentEngine engine;
    private List<ChatMessage> history;
    private FrameLayout content;
    private TextView providerState;
    private View agentView;
    private LinearLayout messages;
    private ScrollView chatScroll;
    private EditText prompt;
    private Button send;
    private TextView agentStatus;
    private boolean busy;
    private String selectedTab = "agent";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Ui.BACKGROUND);
        window.setNavigationBarColor(Ui.BACKGROUND);
        settings = new SecureSettings(this);
        store = new ChatStore(this);
        plugins = new PluginRegistry(this);
        engine = new AgentEngine(this, settings, plugins);
        history = store.load();

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BACKGROUND);
        root.addView(createHeader());
        root.addView(createTabs());
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        showTab("agent");
    }

    private View createHeader() {
        LinearLayout header = Ui.row(this);
        header.setPadding(Ui.dp(18), Ui.dp(14), Ui.dp(14), Ui.dp(10));
        TextView logo = Ui.title(this, "J", 23);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.gradient(Color.rgb(113, 91, 226), Color.rgb(80, 196, 200), 15));
        header.addView(logo, new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46)));

        LinearLayout names = Ui.column(this);
        names.setPadding(Ui.dp(12), 0, 0, 0);
        TextView title = Ui.title(this, "Java Agent", 20);
        names.addView(title);
        providerState = Ui.text(this, "", 11, Ui.MUTED);
        names.addView(providerState);
        header.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button clear = Ui.button(this, "↺", false);
        clear.setContentDescription("Clear chat history");
        clear.setTextSize(22);
        clear.setPadding(0, 0, 0, 0);
        clear.setOnClickListener(view -> confirmClear());
        header.addView(clear, new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46)));
        updateProviderState();
        return header;
    }

    private View createTabs() {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(14), Ui.dp(4), Ui.dp(14), Ui.dp(10));
        addTab(bar, "agent", "Agent");
        addTab(bar, "plugins", "Plugins");
        addTab(bar, "settings", "Settings");
        return bar;
    }

    private void addTab(LinearLayout bar, String id, String label) {
        Button tab = Ui.button(this, label, false);
        tab.setMinHeight(Ui.dp(42));
        tab.setMinimumHeight(Ui.dp(42));
        tab.setOnClickListener(view -> showTab(id));
        tabs.put(id, tab);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(44), 1);
        params.setMargins(Ui.dp(3), 0, Ui.dp(3), 0);
        bar.addView(tab, params);
    }

    private void showTab(String id) {
        selectedTab = id;
        for (Map.Entry<String, Button> entry : tabs.entrySet()) {
            boolean selected = entry.getKey().equals(id);
            entry.getValue().setBackground(Ui.round(selected ? Ui.ACCENT : Ui.SURFACE,
                    13, selected ? Ui.ACCENT : Ui.BORDER));
            entry.getValue().setTextColor(selected ? Color.rgb(15, 13, 30) : Ui.MUTED);
        }
        content.removeAllViews();
        if ("agent".equals(id)) {
            if (agentView == null) agentView = createAgentView();
            content.addView(agentView);
        } else if ("plugins".equals(id)) content.addView(createPluginsView());
        else content.addView(createSettingsView());
        updateProviderState();
    }

    private View createAgentView() {
        LinearLayout page = Ui.column(this);
        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);
        messages = Ui.column(this);
        messages.setPadding(Ui.dp(14), Ui.dp(6), Ui.dp(14), Ui.dp(18));
        chatScroll.addView(messages);
        page.addView(chatScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        renderHistory();

        LinearLayout composer = Ui.column(this);
        composer.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(12));
        composer.setBackground(Ui.round(Ui.BACKGROUND, 0, Ui.BACKGROUND));
        agentStatus = Ui.text(this, "Ready • local tools available", 11, Ui.MUTED);
        composer.addView(agentStatus);
        LinearLayout inputRow = Ui.row(this);
        prompt = Ui.input(this, "Describe your idea…", true);
        prompt.setMinLines(1);
        prompt.setMaxLines(5);
        prompt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        inputRow.addView(prompt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        send = Ui.button(this, "↑", true);
        send.setContentDescription("Send message");
        send.setTextSize(24);
        send.setPadding(0, 0, 0, 0);
        send.setOnClickListener(view -> sendPrompt());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(Ui.dp(52), Ui.dp(52));
        sendParams.setMargins(Ui.dp(10), 0, 0, 0);
        inputRow.addView(send, sendParams);
        Ui.margins(inputRow, 0, 7, 0, 0);
        composer.addView(inputRow);
        page.addView(composer);
        return page;
    }

    private void renderHistory() {
        messages.removeAllViews();
        if (history.isEmpty()) {
            messages.addView(createWelcome());
            return;
        }
        for (ChatMessage message : history) addMessageBubble(message.role, message.content, false);
        scrollToBottom();
    }

    private View createWelcome() {
        LinearLayout welcome = Ui.column(this);
        welcome.setGravity(Gravity.CENTER_HORIZONTAL);
        welcome.setPadding(Ui.dp(12), Ui.dp(30), Ui.dp(12), Ui.dp(16));
        TextView badge = Ui.badge(this, "AI + LOCAL PLUGINS");
        welcome.addView(badge);
        TextView title = Ui.title(this, "What will you build?", 29);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(20), 0, 0);
        welcome.addView(title);
        TextView subtitle = Ui.text(this,
                "Chat, calculate, organize tasks and use private tools — from one focused workspace.",
                15, Ui.MUTED);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(24));
        welcome.addView(subtitle);
        welcome.addView(suggestion("Calculate 18% of 250", "Accurate local calculator"));
        welcome.addView(suggestion("Save a note: call Sam tomorrow", "Private on-device notes"));
        welcome.addView(suggestion("Device status", "Battery and system diagnostics"));
        return welcome;
    }

    private View suggestion(String value, String caption) {
        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(16), Ui.dp(13), Ui.dp(16), Ui.dp(13));
        TextView title = Ui.text(this, value, 14, Ui.TEXT);
        title.setTypeface(Typeface.create("sans", Typeface.BOLD));
        card.addView(title);
        card.addView(Ui.text(this, caption, 11, Ui.MUTED));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> {
            prompt.setText(value);
            prompt.setSelection(value.length());
            prompt.requestFocus();
        });
        Ui.margins(card, 0, 5, 0, 5);
        return card;
    }

    private void addMessageBubble(String role, String value, boolean error) {
        boolean user = "user".equals(role);
        LinearLayout block = Ui.column(this);
        TextView label = Ui.text(this, user ? "YOU" : error ? "AGENT ERROR" : "JAVA AGENT",
                10, error ? Ui.ERROR : user ? Ui.ACCENT : Ui.CYAN);
        label.setTypeface(Typeface.create("sans", Typeface.BOLD));
        label.setGravity(user ? Gravity.END : Gravity.START);
        block.addView(label);
        TextView bubble = Ui.text(this, value, 15, error ? Ui.ERROR : Ui.TEXT);
        bubble.setTextIsSelectable(true);
        bubble.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
        int background = error ? Color.rgb(48, 25, 34) : user ? Color.rgb(39, 34, 69) : Ui.SURFACE;
        int border = error ? Color.rgb(118, 57, 69) : user ? Color.rgb(81, 67, 137) : Ui.BORDER;
        bubble.setBackground(Ui.round(background, 17, border));
        Ui.margins(bubble, user ? 38 : 0, 4, user ? 0 : 38, 0);
        block.addView(bubble);
        Ui.margins(block, 0, 6, 0, 6);
        messages.addView(block);
        scrollToBottom();
    }

    private void sendPrompt() {
        if (busy || prompt == null) return;
        String value = prompt.getText().toString().trim();
        if (value.isEmpty()) return;
        if (value.length() > 16000) {
            Toast.makeText(this, "Message is limited to 16,000 characters", Toast.LENGTH_LONG).show();
            return;
        }
        if (history.isEmpty()) messages.removeAllViews();
        List<ChatMessage> previous = new ArrayList<>(history);
        ChatMessage user = new ChatMessage("user", value);
        appendHistory(user);
        addMessageBubble("user", value, false);
        prompt.setText("");
        setBusy(true);
        worker.execute(() -> {
            try {
                String answer = boundedAnswer(engine.send(value, previous, status -> runOnUiThread(() -> {
                    if (!isDestroyed() && agentStatus != null) agentStatus.setText(status);
                })));
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    ChatMessage assistant = new ChatMessage("assistant", answer);
                    appendHistory(assistant);
                    addMessageBubble("assistant", answer, false);
                    setBusy(false);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    String message = error.getMessage();
                    if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
                    addMessageBubble("assistant", "Request failed\n" + message, true);
                    setBusy(false);
                });
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        send.setEnabled(!value);
        send.setAlpha(value ? 0.45f : 1f);
        send.setText(value ? "…" : "↑");
        agentStatus.setText(value ? "Starting agent…" : settings.isOffline() ? "Ready • offline" : "Ready");
    }

    private View createPluginsView() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = pageColumn();
        scroll.addView(page);
        page.addView(Ui.title(this, "Plugin system", 26));
        TextView intro = Ui.text(this,
                "Enable only the tools you trust. The model sees schemas for enabled plugins; execution stays on this device.",
                14, Ui.MUTED);
        Ui.margins(intro, 0, 6, 0, 16);
        page.addView(intro);
        TextView safety = Ui.badge(this, "NO SHELL ACCESS  •  BOUNDED EXECUTION");
        page.addView(safety);
        page.addView(Ui.space(this, 14));

        for (AgentPlugin plugin : plugins.all()) {
            LinearLayout card = Ui.card(this);
            LinearLayout heading = Ui.row(this);
            LinearLayout names = Ui.column(this);
            names.addView(Ui.title(this, plugin.getName(), 17));
            names.addView(Ui.text(this, plugin.getId(), 11, Ui.ACCENT));
            heading.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Switch toggle = new Switch(this);
            toggle.setContentDescription("Enable " + plugin.getName());
            toggle.setChecked(plugins.isEnabled(plugin.getId()));
            toggle.setOnCheckedChangeListener((button, checked) -> {
                plugins.setEnabled(plugin.getId(), checked);
                Toast.makeText(this, plugin.getName() + (checked ? " enabled" : " disabled"), Toast.LENGTH_SHORT).show();
            });
            heading.addView(toggle);
            card.addView(heading);
            TextView description = Ui.text(this, plugin.getDescription(), 13, Ui.MUTED);
            Ui.margins(description, 0, 8, 0, 0);
            card.addView(description);
            Ui.margins(card, 0, 6, 0, 6);
            page.addView(card);
        }
        TextView developer = Ui.text(this,
                "For developers: implement AgentPlugin, validate arguments in execute(), then register the plugin in PluginRegistry. No eval or arbitrary shell commands are used.",
                12, Ui.MUTED);
        Ui.margins(developer, 4, 16, 4, 24);
        page.addView(developer);
        return scroll;
    }

    private View createSettingsView() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = pageColumn();
        scroll.addView(page);
        page.addView(Ui.title(this, "Provider settings", 26));
        TextView intro = Ui.text(this,
                "Use any OpenAI-compatible Chat Completions endpoint. Nothing is sent until you send a message.",
                14, Ui.MUTED);
        Ui.margins(intro, 0, 6, 0, 18);
        page.addView(intro);

        EditText baseUrl = settingField(page, "API base URL", settings.getBaseUrl(), "https://api.openai.com/v1");
        baseUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText model = settingField(page, "Model", settings.getModel(), "gpt-4o-mini");
        EditText apiKey = settingField(page, "API key", settings.getApiKey(), "Your own key — never shared");
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        Switch offline = new Switch(this);
        offline.setText("Offline tools only");
        offline.setTextColor(Ui.TEXT);
        offline.setTextSize(15);
        offline.setChecked(settings.isOffline());
        offline.setPadding(Ui.dp(4), Ui.dp(14), Ui.dp(4), Ui.dp(14));
        page.addView(offline);

        Button save = Ui.button(this, "Save configuration", true);
        save.setOnClickListener(view -> {
            String endpoint = baseUrl.getText().toString().trim();
            if (!(endpoint.startsWith("https://") || endpoint.startsWith("http://10.0.2.2")
                    || endpoint.startsWith("http://localhost") || endpoint.startsWith("http://127.0.0.1"))) {
                baseUrl.setError("Use HTTPS, or HTTP only for a local model server");
                return;
            }
            try {
                settings.save(endpoint, model.getText().toString(), apiKey.getText().toString(), offline.isChecked());
                updateProviderState();
                Toast.makeText(this, "Configuration saved securely", Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(this, "Could not save: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        page.addView(save);
        Button clearKey = Ui.button(this, "Remove saved API key", false);
        clearKey.setOnClickListener(view -> {
            settings.clearApiKey();
            apiKey.setText("");
            updateProviderState();
            Toast.makeText(this, "API key removed", Toast.LENGTH_SHORT).show();
        });
        Ui.margins(clearKey, 0, 10, 0, 0);
        page.addView(clearKey);

        LinearLayout privacy = Ui.card(this);
        privacy.addView(Ui.title(this, "Privacy & network", 16));
        TextView details = Ui.text(this,
                "• API keys are AES-GCM encrypted with Android Keystore.\n"
                        + "• HTTPS is required except emulator/host loopback.\n"
                        + "• Chat history and plugin data are app-private.\n"
                        + "• No analytics, advertising or Emergent credentials.\n"
                        + "• Connection is tested only when you send a chat.",
                13, Ui.MUTED);
        Ui.margins(details, 0, 10, 0, 0);
        privacy.addView(details);
        Ui.margins(privacy, 0, 22, 0, 30);
        page.addView(privacy);
        return scroll;
    }

    private EditText settingField(LinearLayout page, String label, String value, String hint) {
        TextView labelView = Ui.text(this, label, 12, Ui.MUTED);
        labelView.setTypeface(Typeface.create("sans", Typeface.BOLD));
        Ui.margins(labelView, 2, 0, 0, 6);
        page.addView(labelView);
        EditText input = Ui.input(this, hint, false);
        input.setText(value);
        input.setSelection(input.length());
        Ui.margins(input, 0, 0, 0, 18);
        page.addView(input);
        return input;
    }

    private LinearLayout pageColumn() {
        LinearLayout page = Ui.column(this);
        page.setPadding(Ui.dp(18), Ui.dp(12), Ui.dp(18), Ui.dp(24));
        return page;
    }

    private void updateProviderState() {
        if (providerState == null) return;
        String state;
        int color;
        if (settings.isOffline()) {
            state = "OFFLINE  •  LOCAL TOOLS";
            color = Ui.CYAN;
        } else if (settings.getBaseUrl().equals(SecureSettings.DEFAULT_BASE_URL) && !settings.hasApiKey()) {
            state = "DEMO  •  ADD API IN SETTINGS";
            color = Ui.MUTED;
        } else {
            state = "READY  •  " + settings.getModel();
            color = Ui.ACCENT;
        }
        providerState.setText(state);
        providerState.setTextColor(color);
    }

    private void appendHistory(ChatMessage message) {
        history.add(message);
        while (history.size() > 80) history.remove(0);
        store.append(message);
    }

    private static String boundedAnswer(String answer) {
        if (answer.length() <= 100000) return answer;
        return answer.substring(0, 100000) + "\n\n[Response truncated at 100,000 characters]";
    }

    private void confirmClear() {
        if (busy) {
            Toast.makeText(this, "Wait for the current request before clearing history", Toast.LENGTH_SHORT).show();
            return;
        }
        if (history.isEmpty()) return;
        new AlertDialog.Builder(this).setTitle("Clear chat history?")
                .setMessage("This removes user-visible messages from this device. Notes and tasks are kept.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    history.clear();
                    store.clear();
                    if (messages != null) renderHistory();
                    if (!"agent".equals(selectedTab)) showTab("agent");
                }).show();
    }

    private void scrollToBottom() {
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
