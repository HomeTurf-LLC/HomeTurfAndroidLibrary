package com.hometurf.android.interfaces;

import android.app.Activity;

import com.hometurf.android.services.HomeTurfJavascriptService;

public interface HomeTurfBaseAuth0Service {
    boolean isAuthorizing = false;
    boolean isLoggingOut = false;
    void setJavascriptService(HomeTurfJavascriptService javascriptService);
    void setWebViewActivity(Activity activity);
    void login();
    void logout();
}
