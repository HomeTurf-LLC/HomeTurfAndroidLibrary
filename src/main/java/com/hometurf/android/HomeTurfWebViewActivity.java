package com.hometurf.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
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
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;

import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.hometurf.android.interfaces.HomeTurfBaseAuth0Service;
import com.hometurf.android.receivers.HomeTurfBroadcastReceiver;
import com.hometurf.android.services.HomeTurfImageUploadService;
import com.hometurf.android.services.HomeTurfJavascriptService;
import com.hometurf.android.services.HomeTurfRecordAudioService;
import com.hometurf.android.utils.HomeTurfOrientationUtils;

import java.net.CookieHandler;

import static com.hometurf.android.constants.PermissionCodes.INPUT_FILE_REQUEST_CODE;
import static com.hometurf.android.constants.PermissionCodes.MY_PERMISSIONS_AV;
import static com.hometurf.android.constants.PermissionCodes.MY_PERMISSIONS_RECORD_AUDIO;
import static com.hometurf.android.constants.PermissionCodes.REQUEST_CAMERA_FOR_UPLOAD;
import static com.hometurf.android.constants.PermissionCodes.REQUEST_FINE_LOCATION;
import static com.hometurf.android.constants.PermissionCodes.REQUEST_SHARE;

public class HomeTurfWebViewActivity extends Activity {

    private WebView webView;
    private String geolocationOrigin;
    private GeolocationPermissions.Callback geolocationCallback;
    private static HomeTurfBaseAuth0Service auth0Service;
    private static String watchPartyId;
    private HomeTurfJavascriptService javascriptService;
    private HomeTurfImageUploadService imageUploadService;
    private HomeTurfRecordAudioService recordAudioService;
    private int nextNotificationId = 0;
    private final int MAX_NUMBER_NOTIFICATIONS = 5;
    private boolean isBackgrounded = false;
    private final int defaultBackgroundColor = Color.parseColor("#000000");

    public HomeTurfWebViewActivity() {
    }

    public static void setAuth0Service(HomeTurfBaseAuth0Service auth0Service) {
        HomeTurfWebViewActivity.auth0Service = auth0Service;
    }

    public static void setWatchPartyId(String watchPartyId) {
        HomeTurfWebViewActivity.watchPartyId = watchPartyId;
    }

    @Override
    public void onBackPressed() {
        // Let web handle back transition rather than popping view by default
        javascriptService.executeJavaScriptActionInWebView("HARDWARE_BACK_BUTTON_PRESSED");
    }

    public void destroyWebView() {
        // From: https://stackoverflow.com/questions/17418503/destroy-webview-in-android
        webView.clearHistory();

        // NOTE: clears RAM cache, if you pass true, it will also clear the disk cache.
        // Probably not a great idea to pass true if you have other WebViews still alive.
        webView.clearCache(true);

        // Loading a blank page is optional, but will ensure that the WebView isn't doing anything when you destroy it.
        webView.loadUrl("about:blank");

        webView.onPause();
        webView.removeAllViews();
        webView.destroyDrawingCache();

        // NOTE: This pauses JavaScript execution for ALL WebViews,
        // do not use if you have other WebViews still alive.
        // If you create another WebView after calling this,
        // make sure to call mWebView.resumeTimers().
        webView.pauseTimers();

        // NOTE: This can occasionally cause a segfault below API 17 (4.2)
        webView.destroy();

        // Null out the reference so that you don't end up re-using it.
        webView = null;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.setBackgroundColor(defaultBackgroundColor);
//        webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
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
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }

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
        String useWatchPartyId = TextUtils.isEmpty(watchPartyId) ? "" : watchPartyId;
        webView.clearCache(true);
        webView.clearHistory();
        webView.loadUrl(String.format("%s?activeTeamId=%s&useNativeAuth0=%s&watchPartyId=%s", homeTurfUrl, homeTurfTeamId, useNativeAuth0, useWatchPartyId));
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
        Log.d("nav", "HERE!!!");
        javascriptService.executeJavaScriptActionInWebView("NAVIGATE_BACK_TO_TEAM_APP_REQUEST_RECEIVED");
        this.finishAfterTransition();
        destroyWebView();
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES
                .O) {
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
    public void share(@NonNull String title, @NonNull String message, @Nullable String subject) {
        javascriptService.executeJavaScriptActionInWebView("SHARE_REQUEST_RECEIVED");
        try {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, message);
            if (subject != null) {
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            }
            sendIntent.setType("text/plain");
            Intent shareIntent = Intent.createChooser(sendIntent, title);
            startActivity(shareIntent);
            Context myContext = getApplicationContext();
            PendingIntent pi = PendingIntent.getBroadcast(myContext, REQUEST_SHARE,
                    new Intent(myContext, HomeTurfBroadcastReceiver.class),
                    PendingIntent.FLAG_UPDATE_CURRENT);
            Intent.createChooser(shareIntent, null, pi.getIntentSender());
            javascriptService.executeJavaScriptActionInWebView("SHARE_SUCCESS"); // TODO: Should this be returned after intent is closed?
        } catch (Exception e) {
            javascriptService.executeJavaScriptActionAndStringDataInWebView("SHARE_ERROR", e.getMessage());
        }
    }

    @JavascriptInterface
    public void changeBackgroundColor(String color) {
        javascriptService.executeJavaScriptActionInWebView("NATIVE_BACKGROUND_COLOR_CHANGE_REQUEST_RECEIVED");
        try {
            String colorWithHash = "#" + color;
            webView.setBackgroundColor(Color.parseColor(colorWithHash));
            javascriptService.executeJavaScriptActionInWebView("NATIVE_BACKGROUND_COLOR_CHANGE_SUCCESS");
        } catch (IllegalArgumentException e) {
            javascriptService.executeJavaScriptActionAndStringDataInWebView("NATIVE_BACKGROUND_COLOR_CHANGE_ERROR", "Invalid color provided");
        }
    }

    @JavascriptInterface
    public void recordAudio(long timeOfRequestFromWebMillis) {
        javascriptService.executeJavaScriptActionInWebView("RECORD_AUDIO_REQUEST_RECEIVED");
        recordAudioService.startRecording(timeOfRequestFromWebMillis);
    }

    @JavascriptInterface
    public void recordTeamScream() {
        javascriptService.executeJavaScriptActionInWebView("RECORD_TEAM_SCREAM_REQUEST_RECEIVED");
        recordAudioService.startRecordingTeamScream();
    }

    @JavascriptInterface
    public void requestAVPermission() {
        javascriptService.executeJavaScriptActionInWebView("REQUEST_AV_PERMISSION_RECEIVED");
        System.out.println("Requesting audio + video permissions");
        boolean audioPermissionAlreadyGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        boolean videoPermissionAlreadyGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        if (!audioPermissionAlreadyGranted || !videoPermissionAlreadyGranted) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "HomeTurf needs access to your microphone for your Watch Party", Toast.LENGTH_LONG).show();
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                Toast.makeText(this, "HomeTurf needs access to your camera for your Watch Party", Toast.LENGTH_LONG).show();
            }

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA},
                    MY_PERMISSIONS_AV);

        }
        else {
            System.out.println("Permission already granted");
            javascriptService.executeJavaScriptActionInWebView("REQUEST_AV_PERMISSION_SUCCESS");
        }
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

    @JavascriptInterface
    public void requestCameraPermission() {
        javascriptService.executeJavaScriptActionInWebView("REQUEST_CAMERA_PERMISSION_RECEIVED");
        System.out.println("Requesting camera permissions");
        boolean permissionAlreadyGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        if (!permissionAlreadyGranted) {
            // When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                Toast.makeText(this, "HomeTurf needs your permission to use you camera for your Watch Party,", Toast.LENGTH_LONG).show();
            }
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_FOR_UPLOAD);
        }
        else {
            System.out.println("Permission already granted");
            javascriptService.executeJavaScriptActionInWebView("REQUEST_CAMERA_PERMISSION_SUCCESS");
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
            case MY_PERMISSIONS_AV: {
                boolean allowCamera = false;
                boolean allowAudio = false;

                System.out.println("grand results length " + grantResults.length);

                if (grantResults.length > 1) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        System.out.println("Permission for audio granted");
                        allowCamera = true;
                    }
                    if (grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                        System.out.println("Permission for camera granted");
                        allowAudio = true;
                    }
                }
                if (!allowCamera && !allowAudio) {
                    System.out.println("Permission denied");
                    System.out.println("Camera permission " + allowCamera);
                    System.out.println("Audio permission " + allowAudio);
                    Toast.makeText(this, "Permission denied to camera and/or microphone", Toast.LENGTH_LONG).show();
                    // Send back perm denied message
                    javascriptService.executeJavaScriptActionInWebView("REQUEST_AV_PERMISSION_ERROR");
                } else {
                    System.out.println("Permission for camera and microphone granted");
                    javascriptService.executeJavaScriptActionInWebView("REQUEST_AV_PERMISSION_SUCCESS");
                }
            }
        }
    }
}
