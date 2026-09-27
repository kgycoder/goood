package com.lmarena.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * 로그인 화면.
 *
 * 사용자가 이미 로그인되어 있으면 즉시 ChatActivity로 이동한다.
 * 아니면 "Google로 로그인" 버튼을 보여주고, 클릭 시 WebView에서
 * LM Arena 페이지를 열어 로그인을 처리한다.
 * 로그인이 완료되면(DOM 감지) 자동으로 ChatActivity로 이동한다.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "lmarena_prefs";
    private static final String KEY_LOGGED_IN = "is_logged_in";
    private static final String ARENA_URL = "https://arena.ai/text/direct";

    private WebView loginWebView;
    private View loginWebContainer;
    private LinearLayout loadingLayout;
    private MaterialButton loginButton;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loginWebView = findViewById(R.id.loginWebView);
        loginWebContainer = findViewById(R.id.loginWebContainer);
        loadingLayout = findViewById(R.id.loadingLayout);
        loginButton = findViewById(R.id.loginButton);

        // 이미 로그인된 경우 바로 ChatActivity로
        if (prefs.getBoolean(KEY_LOGGED_IN, false)) {
            goToChat();
            return;
        }

        loginButton.setOnClickListener(v -> openLoginWebView());
        findViewById(R.id.loginBackBtn).setOnClickListener(v -> {
            loginWebContainer.setVisibility(View.GONE);
            loadingLayout.setVisibility(View.GONE);
        });

        setupWebView();
    }

    private void setupWebView() {
        WebSettings settings = loginWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Mobile UA를 유지 (LM Arena 모바일 DOM 사용)
        settings.setUserAgentString(settings.getUserAgentString()
            .replace("; wv", ""));

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(loginWebView, true);

        // JavaScript Bridge
        loginWebView.addJavascriptInterface(new LMArenaWebBridge(new LMArenaWebBridge.BridgeListener() {
            @Override
            public void onLoginDetected() {
                runOnUiThread(() -> {
                    prefs.edit().putBoolean(KEY_LOGGED_IN, true).apply();
                    goToChat();
                });
            }

            @Override
            public void onLogoutDetected() {
                // 로그인 화면 유지
            }

            @Override
            public void onResponseChunk(String chunk, boolean isDone) {}

            @Override
            public void onGeneratingStateChanged(boolean isGenerating) {}

            @Override
            public void onError(String message) {}

            @Override
            public void onPageReady(String url) {
                runOnUiThread(() -> loginWebView.evaluateJavascript(
                    DomInjector.getInitScript(), null));
            }
        }), "Android");

        loginWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // 페이지 로드 완료 후 초기화 스크립트 inject
                view.evaluateJavascript(DomInjector.getInitScript(), null);
                loadingLayout.setVisibility(View.GONE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // LM Arena 도메인 내에서만 이동 허용
                String url = request.getUrl().toString();
                if (url.startsWith("https://arena.ai") || url.startsWith("https://accounts.google.com")
                    || url.startsWith("https://lmarena.ai")) {
                    return false; // WebView에서 처리
                }
                return false;
            }
        });

        loginWebView.setWebChromeClient(new WebChromeClient());
    }

    private void openLoginWebView() {
        loginWebContainer.setVisibility(View.VISIBLE);
        loadingLayout.setVisibility(View.VISIBLE);
        loginWebView.loadUrl(ARENA_URL);
    }

    private void goToChat() {
        Intent intent = new Intent(this, ChatActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (loginWebContainer.getVisibility() == View.VISIBLE) {
            if (loginWebView.canGoBack()) {
                loginWebView.goBack();
            } else {
                loginWebContainer.setVisibility(View.GONE);
            }
        } else {
            super.onBackPressed();
        }
    }
}
