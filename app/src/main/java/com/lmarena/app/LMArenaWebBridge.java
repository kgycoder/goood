package com.lmarena.app;

import android.webkit.JavascriptInterface;

/**
 * JavaScript Bridge: WebView -> Java
 *
 * LM Arena 페이지의 JavaScript가 이 인터페이스를 통해
 * 네이티브 앱에 이벤트를 전달한다.
 */
public class LMArenaWebBridge {

    public interface BridgeListener {
        void onLoginDetected();
        void onLogoutDetected();
        void onResponseChunk(String chunk, boolean isDone);
        void onGeneratingStateChanged(boolean isGenerating);
        void onError(String message);
        void onPageReady(String url);
    }

    private final BridgeListener listener;

    public LMArenaWebBridge(BridgeListener listener) {
        this.listener = listener;
    }

    @JavascriptInterface
    public void notifyLoginDetected() {
        listener.onLoginDetected();
    }

    @JavascriptInterface
    public void notifyLogoutDetected() {
        listener.onLogoutDetected();
    }

    @JavascriptInterface
    public void notifyResponseChunk(String chunk, boolean isDone) {
        listener.onResponseChunk(chunk, isDone);
    }

    @JavascriptInterface
    public void notifyGeneratingState(boolean isGenerating) {
        listener.onGeneratingStateChanged(isGenerating);
    }

    @JavascriptInterface
    public void notifyError(String message) {
        listener.onError(message);
    }

    @JavascriptInterface
    public void notifyPageReady(String url) {
        listener.onPageReady(url);
    }
}
