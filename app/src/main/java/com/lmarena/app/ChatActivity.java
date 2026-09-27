package com.lmarena.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 메인 채팅 화면.
 *
 * 숨겨진 WebView가 LM Arena 페이지를 실제로 로드/유지하고,
 * DomInjector JS가 DOM을 폴링해 응답을 감지한다.
 * 네이티브 RecyclerView UI에 메시지를 표시한다.
 */
public class ChatActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "lmarena_prefs";
    private static final String KEY_LOGGED_IN = "is_logged_in";
    private static final String DIRECT_URL = "https://arena.ai/text/direct";
    private static final String AGENT_URL = "https://arena.ai/agent/";

    // UI
    private DrawerLayout drawerLayout;
    private RecyclerView chatRecycler;
    private EditText promptInput;
    private View sendBtn;
    private View emptyState;
    private TextView topBarTitle;
    private View statusDot;
    private TextView statusText;
    private View sidebarStatusDot;
    private TextView sidebarStatusText;
    private Chip chipDirect;
    private Chip chipAgent;
    private RadioGroup chatModeGroup;

    // Hidden WebView (1x1, invisible, runs LM Arena)
    private WebView arenaWebView;

    // State
    private MessageAdapter adapter;
    private String chatMode = "direct";
    private boolean isGenerating = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        setupHiddenWebView();
        setupListeners();

        // Start loading LM Arena in background
        arenaWebView.loadUrl(DIRECT_URL);
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        topBarTitle = findViewById(R.id.topBarTitle);
        statusDot = findViewById(R.id.statusDot);
        statusText = findViewById(R.id.statusText);
        sidebarStatusDot = findViewById(R.id.sidebarStatusDot);
        sidebarStatusText = findViewById(R.id.sidebarStatusText);
        promptInput = findViewById(R.id.promptInput);
        sendBtn = findViewById(R.id.sendBtn);
        emptyState = findViewById(R.id.emptyState);
        chipDirect = findViewById(R.id.chipDirect);
        chipAgent = findViewById(R.id.chipAgent);
        chatModeGroup = findViewById(R.id.chatModeGroup);

        // RecyclerView - add programmatically inside chatInner
        LinearLayout chatInner = findViewById(R.id.chatInner);
        chatRecycler = new RecyclerView(this);
        LinearLayout.LayoutParams rvParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        chatRecycler.setLayoutParams(rvParams);
        chatRecycler.setNestedScrollingEnabled(false);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        chatRecycler.setLayoutManager(llm);
        adapter = new MessageAdapter(this);
        chatRecycler.setAdapter(adapter);
        chatInner.addView(chatRecycler);
    }

    private void setupHiddenWebView() {
        // Attach hidden WebView to root FrameLayout (invisible, 1x1px)
        FrameLayout root = (FrameLayout) getWindow().getDecorView().getRootView();
        arenaWebView = new WebView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
        arenaWebView.setVisibility(View.INVISIBLE);
        root.addView(arenaWebView, lp);

        WebSettings settings = arenaWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(arenaWebView, true);

        arenaWebView.addJavascriptInterface(
            new LMArenaWebBridge(new LMArenaWebBridge.BridgeListener() {
                @Override
                public void onLoginDetected() {
                    runOnUiThread(() -> {
                        prefs.edit().putBoolean(KEY_LOGGED_IN, true).apply();
                        setStatus(getString(R.string.connected), true);
                    });
                }

                @Override
                public void onLogoutDetected() {
                    runOnUiThread(() -> {
                        prefs.edit().putBoolean(KEY_LOGGED_IN, false).apply();
                        setStatus(getString(R.string.logged_out), false);
                        goToLogin();
                    });
                }

                @Override
                public void onResponseChunk(String payloadJson, boolean isDone) {
                    runOnUiThread(() -> handleResponseChunk(payloadJson, isDone));
                }

                @Override
                public void onGeneratingStateChanged(boolean generating) {
                    runOnUiThread(() -> {
                        isGenerating = generating;
                        sendBtn.setAlpha(generating ? 0.5f : 1f);
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() ->
                        Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onPageReady(String url) {
                    runOnUiThread(() -> setStatus(getString(R.string.connecting), false));
                }
            }), "Android");

        arenaWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(DomInjector.getInitScript(), null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // Handle all URLs in WebView
            }
        });

        arenaWebView.setWebChromeClient(new WebChromeClient());
    }

    private void setupListeners() {
        findViewById(R.id.menuBtn).setOnClickListener(v ->
            drawerLayout.openDrawer(findViewById(R.id.sidebar)));
        findViewById(R.id.closeSidebarBtn).setOnClickListener(v ->
            drawerLayout.closeDrawers());
        findViewById(R.id.newChatBtn).setOnClickListener(v -> newChat());
        sendBtn.setOnClickListener(v -> sendMessage());
        findViewById(R.id.logoutBtn).setOnClickListener(v -> logout());

        chipDirect.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) switchMode("direct");
        });
        chipAgent.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) switchMode("agent");
        });

        chatModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioModeDirect) {
                chipDirect.setChecked(true);
            } else if (checkedId == R.id.radioModeAgent) {
                chipAgent.setChecked(true);
            }
        });
    }

    private void switchMode(String mode) {
        if (mode.equals(chatMode)) return;
        chatMode = mode;
        topBarTitle.setText(mode.equals("agent") ? "Agent 모드" : "Direct 모드");
        arenaWebView.loadUrl(mode.equals("agent") ? AGENT_URL : DIRECT_URL);
        newChat();
        drawerLayout.closeDrawers();
    }

    private void sendMessage() {
        String text = promptInput.getText().toString().trim();
        if (TextUtils.isEmpty(text) || isGenerating) return;

        promptInput.setText("");
        emptyState.setVisibility(View.GONE);

        adapter.addMessage(ChatMessage.user(text));
        adapter.addMessage(ChatMessage.assistantStreaming());
        scrollToBottom();

        String escaped = DomInjector.escapeForJson(text);
        arenaWebView.evaluateJavascript(DomInjector.getSendPromptScript(escaped), null);
    }

    private void handleResponseChunk(String payloadJson, boolean isDone) {
        try {
            JSONObject payload = new JSONObject(payloadJson);
            String text = payload.optString("text", "");
            boolean done = payload.optBoolean("isDone", isDone);

            List<ChatMessage.CodeBlock> codeBlocks = new ArrayList<>();
            JSONArray arr = payload.optJSONArray("codeBlocks");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject cb = arr.getJSONObject(i);
                    codeBlocks.add(new ChatMessage.CodeBlock(
                        cb.optString("language", ""),
                        cb.optString("code", "")));
                }
            }

            adapter.updateLastAssistant(text, codeBlocks, done);
            if (!done) scrollToBottom();
        } catch (Exception ignored) {}
    }

    private void newChat() {
        adapter.clear();
        emptyState.setVisibility(View.VISIBLE);
        String script = chatMode.equals("agent")
            ? DomInjector.getNewChatAgentScript()
            : DomInjector.getNewChatScript();
        arenaWebView.evaluateJavascript(script, null);
    }

    private void logout() {
        drawerLayout.closeDrawers();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        arenaWebView.clearCache(true);
        arenaWebView.clearHistory();
        prefs.edit().putBoolean(KEY_LOGGED_IN, false).apply();
        goToLogin();
    }

    private void goToLogin() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void setStatus(String text, boolean online) {
        statusText.setText(text);
        if (sidebarStatusText != null) sidebarStatusText.setText(text);
        int color = online ? Color.parseColor("#4CAF50") : Color.parseColor("#666680");
        android.content.res.ColorStateList csl =
            android.content.res.ColorStateList.valueOf(color);
        if (statusDot != null) statusDot.setBackgroundTintList(csl);
        if (sidebarStatusDot != null) sidebarStatusDot.setBackgroundTintList(csl);
    }

    private void scrollToBottom() {
        chatRecycler.post(() -> {
            int count = adapter.getItemCount();
            if (count > 0) chatRecycler.scrollToPosition(count - 1);
        });
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(findViewById(R.id.sidebar))) {
            drawerLayout.closeDrawers();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (arenaWebView != null) {
            arenaWebView.destroy();
        }
        super.onDestroy();
    }
}
