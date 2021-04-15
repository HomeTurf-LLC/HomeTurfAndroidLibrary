package com.hometurf.android.services;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static android.content.ContentValues.TAG;
import static com.hometurf.android.constants.PermissionCodes.INPUT_FILE_REQUEST_CODE;
import static com.hometurf.android.constants.PermissionCodes.REQUEST_CAMERA_FOR_UPLOAD;

public class HomeTurfImageUploadService {

    public ValueCallback<Uri[]> mFilePathCallback;
    public String mCameraPhotoPath;
    public boolean handlingUpload = false;
    private Intent takePictureIntent;
    private final Activity webViewActivity;

    public HomeTurfImageUploadService(Activity activity) {
        this.webViewActivity = activity;
    }

    public void setHandlingUpload(boolean handlingUpload) {
        this.handlingUpload = handlingUpload;
    }

    public boolean onShowFileChooser(
            WebView webView, ValueCallback<Uri[]> filePathCallback,
            WebChromeClient.FileChooserParams fileChooserParams) {
        handlingUpload = true;
        if (mFilePathCallback != null) {
            mFilePathCallback.onReceiveValue(null);
        }
        mFilePathCallback = filePathCallback;
        String[] permissions = {Manifest.permission.CAMERA};
        takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(webViewActivity.getPackageManager()) != null) {
            ActivityCompat.requestPermissions(webViewActivity, permissions, REQUEST_CAMERA_FOR_UPLOAD);
        } else {
            showFileUpload(false);
        }
        return true;
    }

    public void showFileUpload(boolean allowCamera) {
        Activity mActivity = null;
        if (allowCamera && takePictureIntent != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.e(TAG, "Unable to create Image File", ex);
            }

            // Continue only if the File was successfully created
            if (photoFile != null) {
                mCameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                Uri imageUri;
                // N is for Nougat Api 24 Android 7
                if (Build.VERSION_CODES.N <= android.os.Build.VERSION.SDK_INT) {
                    // FileProvider required for Android 7.  Sending a file URI throws exception.
                    imageUri = FileProvider.getUriForFile(webViewActivity, webViewActivity.getPackageName() + ".fileprovider", photoFile);
                } else {
                    // For older devices:
                    // Samsung Galaxy Tab 7" 2 (Samsung GT-P3113 Android 4.2.2, API 17)
                    // Samsung S3
                    imageUri = Uri.fromFile(photoFile);
                }
                takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                        imageUri);
            } else {
                takePictureIntent = null;
            }
        }
        Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentSelectionIntent.setType("image/*");

        Intent[] intentArray;
        if(takePictureIntent != null) {
            intentArray = new Intent[]{takePictureIntent};
        } else {
            intentArray = new Intent[0];
        }

        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
        chooserIntent.putExtra(Intent.EXTRA_TITLE, "Upload File");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

        webViewActivity.startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE);
    }

    /**
     * More info this method can be found at
     * http://developer.android.com/training/camera/photobasics.html
     *
     */
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }
}
