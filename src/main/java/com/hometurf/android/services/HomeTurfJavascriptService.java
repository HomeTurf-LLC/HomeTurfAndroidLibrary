package com.hometurf.android.services;

import android.webkit.WebView;

public class HomeTurfJavascriptService {
    private WebView webView;
    
    public HomeTurfJavascriptService(WebView webView) {
        this.webView = webView;
    }

    public void executeJavaScriptArgumentInWebView(final String executeArgument) {
        webView.post(() -> webView.evaluateJavascript(String.format("homeTurfCallbackFromNative.execute(%s)", executeArgument), null));
    }

    public void executeJavaScriptActionInWebView(final String action) {
        executeJavaScriptArgumentInWebView(String.format("{action: '%s'}", action));
    }

    public void executeJavaScriptActionAndStringDataInWebView(final String action, final String data) {
        executeJavaScriptArgumentInWebView(String.format("{action: '%s', data: '%s'}", action, data));
    }

    public void executeJavaScriptActionAndRawDataInWebView(final String action, final String data) {
        executeJavaScriptArgumentInWebView(String.format("{action: '%s', data: %s}", action, data));
    }
}
