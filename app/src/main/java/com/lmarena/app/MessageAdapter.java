package com.lmarena.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final LayoutInflater inflater;

    public MessageAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void addMessage(ChatMessage msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateLastAssistant(String text, List<ChatMessage.CodeBlock> codeBlocks, boolean done) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role == ChatMessage.Role.ASSISTANT) {
                ChatMessage m = messages.get(i);
                m.text = text;
                m.codeBlocks = codeBlocks;
                m.isStreaming = !done;
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).role == ChatMessage.Role.USER ? TYPE_USER : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_USER) {
            View v = inflater.inflate(R.layout.item_message_user, parent, false);
            return new UserViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_message_assistant, parent, false);
            return new AssistantViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(msg);
        } else {
            ((AssistantViewHolder) holder).bind(msg);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ── User ViewHolder ──────────────────────────────────────────────

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final TextView messageText;

        UserViewHolder(View v) {
            super(v);
            messageText = v.findViewById(R.id.messageText);
        }

        void bind(ChatMessage msg) {
            messageText.setText(msg.text);
        }
    }

    // ── Assistant ViewHolder ─────────────────────────────────────────

    class AssistantViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout thinkingLayout;
        final LinearLayout contentContainer;

        AssistantViewHolder(View v) {
            super(v);
            thinkingLayout = v.findViewById(R.id.thinkingLayout);
            contentContainer = v.findViewById(R.id.contentContainer);
        }

        void bind(ChatMessage msg) {
            contentContainer.removeAllViews();

            if (msg.isStreaming && msg.text.isEmpty()) {
                thinkingLayout.setVisibility(View.VISIBLE);
            } else {
                thinkingLayout.setVisibility(View.GONE);
            }

            if (!msg.text.isEmpty()) {
                // Extract plain text (remove code block content from inline text)
                String plainText = extractPlainText(msg.text, msg.codeBlocks);
                if (!plainText.trim().isEmpty()) {
                    TextView tv = new TextView(itemView.getContext());
                    tv.setTextColor(0xFFEDEDF0);
                    tv.setTextSize(15f);
                    tv.setLineSpacing(0, 1.5f);
                    tv.setText(plainText.trim());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.bottomMargin = dpToPx(6);
                    contentContainer.addView(tv, lp);
                }

                // Add code blocks
                for (ChatMessage.CodeBlock cb : msg.codeBlocks) {
                    View codeView = inflater.inflate(R.layout.item_code_block, contentContainer, false);
                    TextView langLabel = codeView.findViewById(R.id.langLabel);
                    TextView codeText = codeView.findViewById(R.id.codeText);
                    ImageButton copyBtn = codeView.findViewById(R.id.copyBtn);

                    langLabel.setText(cb.language.isEmpty() ? "code" : cb.language);
                    codeText.setText(cb.code);

                    copyBtn.setOnClickListener(v -> {
                        ClipboardManager cm = (ClipboardManager) itemView.getContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("code", cb.code));
                        Toast.makeText(itemView.getContext(), "복사됨", Toast.LENGTH_SHORT).show();
                    });

                    contentContainer.addView(codeView);
                }
            }
        }

        private String extractPlainText(String fullText, List<ChatMessage.CodeBlock> codeBlocks) {
            // Remove code block content from plain text (rough approximation)
            String text = fullText;
            for (ChatMessage.CodeBlock cb : codeBlocks) {
                if (cb.code.length() > 20) {
                    // Remove code from displayed text to avoid duplication
                    String snippet = cb.code.substring(0, Math.min(cb.code.length(), 50));
                    int idx = text.indexOf(snippet);
                    if (idx >= 0) {
                        text = text.substring(0, idx).trim();
                    }
                }
            }
            return text;
        }

        private int dpToPx(int dp) {
            return (int) (dp * itemView.getResources().getDisplayMetrics().density);
        }
    }
}
