package com.javaagent.storage;

import android.content.Context;

import com.javaagent.model.ChatMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bounded, app-private and atomically replaced chat history. */
public final class ChatStore {
    private static final int MAX_MESSAGES = 80;
    private static final int MAX_FILE_BYTES = 512 * 1024;
    private final File file;

    public ChatStore(Context context) {
        file = new File(context.getFilesDir(), "chat-history.json");
    }

    public synchronized List<ChatMessage> load() {
        List<ChatMessage> messages = new ArrayList<>();
        if (!file.isFile() || file.length() > MAX_FILE_BYTES) return messages;
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) break;
                offset += count;
            }
            if (offset != data.length) return messages;
            JSONArray array = new JSONArray(new String(data, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length() && messages.size() < MAX_MESSAGES; i++) {
                JSONObject value = array.getJSONObject(i);
                messages.add(ChatMessage.fromJson(value));
            }
        } catch (Exception ignored) {
            // A malformed local cache must not prevent the agent from opening.
            return new ArrayList<>();
        } finally {
            if (input != null) try { input.close(); } catch (Exception ignored) { }
        }
        return messages;
    }

    public synchronized void append(ChatMessage message) {
        List<ChatMessage> messages = load();
        messages.add(message);
        while (messages.size() > MAX_MESSAGES) messages.remove(0);
        write(messages);
    }

    public synchronized void clear() {
        if (file.exists() && !file.delete()) {
            write(new ArrayList<ChatMessage>());
        }
    }

    private void write(List<ChatMessage> messages) {
        JSONArray array = new JSONArray();
        try {
            for (ChatMessage message : messages) array.put(message.toJson());
            byte[] bytes = array.toString().getBytes(StandardCharsets.UTF_8);
            while (bytes.length > MAX_FILE_BYTES && !messages.isEmpty()) {
                messages.remove(0);
                array = new JSONArray();
                for (ChatMessage message : messages) array.put(message.toJson());
                bytes = array.toString().getBytes(StandardCharsets.UTF_8);
            }
            File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
            FileOutputStream output = new FileOutputStream(temporary);
            try {
                output.write(bytes);
                output.getFD().sync();
            } finally {
                output.close();
            }
            if (!temporary.renameTo(file)) {
                FileOutputStream fallback = new FileOutputStream(file);
                try { fallback.write(bytes); fallback.getFD().sync(); }
                finally { fallback.close(); }
                if (!temporary.delete()) temporary.deleteOnExit();
            }
        } catch (Exception ignored) {
            // Chat delivery is more important than persistence; settings/errors never expose file contents.
        }
    }
}
