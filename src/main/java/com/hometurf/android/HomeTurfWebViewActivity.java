package com.hometurf.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.hometurf.android.interfaces.HomeTurfBaseAuth0Service;
import com.hometurf.android.services.HomeTurfImageUploadService;
import com.hometurf.android.services.HomeTurfJavascriptService;
import com.hometurf.android.services.HomeTurfRecordAudioService;
import com.hometurf.android.utils.HomeTurfOrientationUtils;

import java.net.CookieHandler;

import static com.hometurf.android.constants.PermissionCodes.INPUT_FILE_REQUEST_CODE;
import static com.hometurf.android.constants.PermissionCodes.MY_PERMISSIONS_RECORD_AUDIO;
import static com.hometurf.android.constants.PermissionCodes.REQUEST_CAMERA_FOR_UPLOAD;
import static com.hometurf.android.constants.PermissionCodes.REQUEST_FINE_LOCATION;

public class HomeTurfWebViewActivity extends Activity {

    private WebView webView;
    private String geolocationOrigin;
    private GeolocationPermissions.Callback geolocationCallback;
    private static HomeTurfBaseAuth0Service auth0Service;
    private HomeTurfJavascriptService javascriptService;
    private HomeTurfImageUploadService imageUploadService;
    private HomeTurfRecordAudioService recordAudioService;
    private int nextNotificationId = 0;
    private final int MAX_NUMBER_NOTIFICATIONS = 5;
    private boolean isBackgrounded = false;

    public HomeTurfWebViewActivity() {
    }

    public static void setAuth0Service(HomeTurfBaseAuth0Service auth0Service) {
        HomeTurfWebViewActivity.auth0Service = auth0Service;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_turf_web_view);

        webView = findViewById(R.id.homeTurfWebView);
        javascriptService = new HomeTurfJavascriptService(webView);
        imageUploadService = new HomeTurfImageUploadService(this);
        if (auth0Service != null) {
            auth0Service.setJavascriptService(javascriptService);
            auth0Service.setWebViewActivity(this);
        }
        recordAudioService = new HomeTurfRecordAudioService(javascriptService);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false; // Prevent crash - see https://stackoverflow.com/questions/47592026/my-application-keeps-on-crashing-using-webview
            }

            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.getSettings().setAppCacheEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setGeolocationEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.setBackgroundColor(Color.parseColor("#0a1129"));
        CookieHandler.setDefault(new java.net.CookieManager());
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().acceptCookie();
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient() {
            //            @Override // Possibly implement in future
//            public void onProgressChanged(WebView view, int newProgress) {
//                if (newProgress == 100) {
//                }
//            }
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                           GeolocationPermissions.Callback callback) {
                String perm = Manifest.permission.ACCESS_FINE_LOCATION;
                geolocationOrigin = origin;
                geolocationCallback = callback;
                ActivityCompat.requestPermissions(HomeTurfWebViewActivity.this, new String[]{perm}, REQUEST_FINE_LOCATION);
            }

            //From https://github.com/googlearchive/chromium-webview-samples/blob/master/input-file-example/app/src/main/java/inputfilesample/android/chrome/google/com/inputfilesample/MainFragment.java
            public boolean onShowFileChooser(
                    WebView webView, ValueCallback<Uri[]> filePathCallback,
                    WebChromeClient.FileChooserParams fileChooserParams) {
                return imageUploadService.onShowFileChooser(webView, filePathCallback, fileChooserParams);
            }
        });
        webView.addJavascriptInterface(this, "homeTurfAndroidJsInterface");
        if (0 != (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        Resources applicationContextResources = getApplicationContext().getResources();
        String homeTurfUrl = applicationContextResources.getString(R.string.home_turf_url);
        String homeTurfTeamId = applicationContextResources.getString(R.string.home_turf_team_id);
        String useNativeAuth0 = applicationContextResources.getString(R.string.home_turf_use_auth0); // Defaults to false in lib R.string file
        webView.loadUrl(String.format("%s?activeTeamId=%s&useNativeAuth0=%s", homeTurfUrl, homeTurfTeamId, useNativeAuth0));
        createNotificationChannel();
    }

    @Override
    public void onPause() {
        super.onPause();
        isBackgrounded = true;
        javascriptService.executeJavaScriptActionInWebView("APP_DID_ENTER_BACKGROUND");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isBackgrounded) { // Avoid running on first load
            isBackgrounded = false;
            javascriptService.executeJavaScriptActionInWebView("APP_WILL_ENTER_FOREGROUND");
        }
    }

    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent intent) {
        if (requestCode != INPUT_FILE_REQUEST_CODE || imageUploadService.mFilePathCallback == null) {
            super.onActivityResult(requestCode, resultCode, intent);
            return;
        }

        Uri[] results = null;

        // Check that the response is a good one
        if (resultCode == Activity.RESULT_OK) {
            String filePath = intent == null ? null : intent.getDataString();
            if (filePath == null) {
                // If there is not data, then we may have taken a photo
                if (imageUploadService.mCameraPhotoPath != null) {
                    results = new Uri[]{Uri.parse(imageUploadService.mCameraPhotoPath)};
                }
            } else {
                results = new Uri[]{Uri.parse(filePath)};
            }
        }

        imageUploadService.mFilePathCallback.onReceiveValue(results);
        imageUploadService.mFilePathCallback = null;
        webView.clearCache(true); // Refresh image preview
        imageUploadService.setHandlingUpload(false);
    }

    @JavascriptInterface
    public void navigateBackToTeamApp() {
        javascriptService.executeJavaScriptActionInWebView("NAVIGATE_BACK_TO_TEAM_APP_REQUEST_RECEIVED");
        new Handler(Looper.getMainLooper()).post(this::onBackPressed);
    }

    @JavascriptInterface
    public void lockToOrientation(String orientation) {
        javascriptService.executeJavaScriptActionInWebView("LOCK_TO_ORIENTATION_REQUEST_RECEIVED");
        switch(orientation) {
            case "portrait":
                HomeTurfOrientationUtils.lockOrientationPortrait(this);
                break;
            case "landscape":
                HomeTurfOrientationUtils.lockOrientationLandscape(this);
                break;
            case "none":
                HomeTurfOrientationUtils.unlockOrientation(this);
                break;
            default:
                Log.d("lockToOrientation", String.format("Unknown orientation '%s' passed to lockToOrientation", orientation));
        }
    }

    @JavascriptInterface
    public void loginAuth0() {
        javascriptService.executeJavaScriptActionInWebView("LOGIN_AUTH0_REQUEST_RECEIVED");
        if (auth0Service == null) {
            javascriptService.executeJavaScriptActionInWebView("LOGIN_AUTH0_ERROR");
            return;
        }
        auth0Service.login();
    }

    @JavascriptInterface
    public void logoutAuth0() {
        javascriptService.executeJavaScriptActionInWebView("LOGOUT_AUTH0_REQUEST_RECEIVED");
        if (auth0Service == null) {
            javascriptService.executeJavaScriptActionInWebView("LOGOUT_AUTH0_ERROR");
            return;
        }
        auth0Service.logout();
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.home_turf_channel_name);
            String description = getString(R.string.home_turf_channel_description);
            String channelId = getString(R.string.home_turf_channel_id);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @JavascriptInterface
    public void clearLocalNotifications() {
        String channelId = getString(R.string.home_turf_channel_id);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        for (int i = 0; i <= MAX_NUMBER_NOTIFICATIONS; i++) {
            notificationManager.cancel(i);
        }
    }

    @JavascriptInterface
    public void triggerLocalNotification(String title, String message) {
        // Create an explicit intent for an Activity in your app
        Intent intent = new Intent(this, this.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        String channelId = getString(R.string.home_turf_channel_id);
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(nextNotificationId, notification);
        nextNotificationId = (nextNotificationId % MAX_NUMBER_NOTIFICATIONS) + 1;
    }

    @JavascriptInterface
    public void recordAudio(long timeOfRequestFromWebMillis) {
        javascriptService.executeJavaScriptActionInWebView("RECORD_AUDIO_REQUEST_RECEIVED");
        recordAudioService.startRecording(timeOfRequestFromWebMillis);
    }

    @JavascriptInterface
    public void requestRecordAudioPermission() {
        javascriptService.executeJavaScriptActionInWebView("REQUEST_RECORD_AUDIO_PERMISSION_RECEIVED");
        System.out.println("Requesting audio + time sync permissions");
        boolean permissionAlreadyGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        if (!permissionAlreadyGranted) {
            // When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "HomeTurf needs your permission to record audio to sync your live channel", Toast.LENGTH_LONG).show();
            }
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_RECORD_AUDIO);
        }
        else {
            System.out.println("Permission already granted");
            javascriptService.executeJavaScriptActionInWebView("REQUEST_RECORD_AUDIO_PERMISSION_SUCCESS");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("Permission just granted");
                    javascriptService.executeJavaScriptActionInWebView("REQUEST_RECORD_AUDIO_PERMISSION_SUCCESS");
                } else {
                    System.out.println("Permission denied");
                    Toast.makeText(this, "Permission denied to record audio", Toast.LENGTH_LONG).show();
                    // Send back perm denied message
                    javascriptService.executeJavaScriptActionInWebView("REQUEST_RECORD_AUDIO_PERMISSION_ERROR");
                }
                return;
            }
            case REQUEST_FINE_LOCATION: {
                boolean allow = false;
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // user has allowed this permission
                    System.out.println("Permission for location granted");
                    allow = true;
                } else {
                    System.out.println("Permission for location denied");
                    Toast.makeText(this, "Permission denied for geolocation, country will default to US unless already set", Toast.LENGTH_LONG).show();
                }
                if (geolocationCallback != null) {
                    // call back to web chrome client
                    geolocationCallback.invoke(geolocationOrigin, allow, false);
                }
                return;
            }
            case REQUEST_CAMERA_FOR_UPLOAD: {
                boolean allowCamera = false;
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // user has allowed this permission
                    System.out.println("Permission for camera granted");
                    allowCamera = true;
                } else {
                    System.out.println("Permission for camera denied");
//                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show();
                }
                if (imageUploadService.handlingUpload) {
                    imageUploadService.showFileUpload(allowCamera);
                }
            }
        }
    }
}
