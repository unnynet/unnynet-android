package com.unnynet.android;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.webkit.WebChromeClient;

import java.io.File;
import java.io.IOException;

public class WebViewFileChooserActivity extends Activity {

    final static String CHROME_CLIENT_BINDER_KEY = "chromeClient";
    private final static int FILECHOOSER_RESULTCODE = 19238467;
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 19238468;
    static Activity mainActivity;

    private WebViewChromeClient chromeClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ObjectWrapperForBinder binder = (ObjectWrapperForBinder) getIntent().getExtras().getBinder(CHROME_CLIENT_BINDER_KEY);
        chromeClient = (WebViewChromeClient) binder.getData();

        if (Build.VERSION.SDK_INT >= 23 && this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            mainActivity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else {
            startFileChooserActivity();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startFileChooserActivity();
            } else {
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Logger.getInstance().verbose("File Chooser activity result: " + resultCode);
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (resultCode == Activity.RESULT_OK) {
                Logger.getInstance().info("File chooser got a file. : " + data);
                chromeClient.receivedFileValue(data, true);
            } else {
                Logger.getInstance().error("File chooser failed to get a file. Result code: " + resultCode);
                chromeClient.receivedFileValue(null, false);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
        finish();
    }

    private void startFileChooserActivity() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            Logger.getInstance().verbose("Found an activity for taking photo. Try to get image.");
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = chromeClient.createImageFile();
                Logger.getInstance().verbose("photoFile: " + photoFile.getAbsolutePath());
                takePictureIntent.putExtra("PhotoPath", chromeClient.getCameraPhotoPath());
            } catch (IOException ex) {
                // Error occurred while creating the File
                Logger.getInstance().error("Error while creating image file. Exception: " + ex);
            }

            // Continue only if the File was successfully created
            if (photoFile != null) {
                chromeClient.setCameraPhotoPath("file:" + photoFile.getAbsolutePath());
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
            } else {
                takePictureIntent = null;
            }
        }

        // Set up the intent to get an existing image
        Intent contentSelectionIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
        WebChromeClient.FileChooserParams params = chromeClient.getFileChooserParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && params != null && params.getAcceptTypes() != null && params.getAcceptTypes().length >= 1 && !params.getAcceptTypes()[0].equals("")) {
            String[] types = params.getAcceptTypes();
            contentSelectionIntent.setType("*/*");
            String[] mimeTypes = new String[types.length];
            System.arraycopy(types, 0, mimeTypes, 0, types.length);
            contentSelectionIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        } else {
            contentSelectionIntent.setType("*/*");
        }


        // Set up the intents for the Intent chooser
        Intent[] intentArray;
        if (takePictureIntent != null) {
            intentArray = new Intent[]{takePictureIntent};
        } else {
            intentArray = new Intent[0];
        }

        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

        startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);
    }
}
