package com.lmarena.app;

import java.util.ArrayList;
import java.util.List;

public class ChatMessage {
    public enum Role { USER, ASSISTANT }

    public static class CodeBlock {
        public final String language;
        public final String code;
        public CodeBlock(String language, String code) {
            this.language = language;
            this.code = code;
        }
    }

    public final Role role;
    public String text;
    public boolean isStreaming;
    public List<CodeBlock> codeBlocks;

    public ChatMessage(Role role, String text) {
        this.role = role;
        this.text = text;
        this.isStreaming = false;
        this.codeBlocks = new ArrayList<>();
    }

    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, text);
    }

    public static ChatMessage assistantStreaming() {
        ChatMessage m = new ChatMessage(Role.ASSISTANT, "");
        m.isStreaming = true;
        return m;
    }
}
