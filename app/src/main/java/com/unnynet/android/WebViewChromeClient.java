package com.unnynet.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.unnynet.android.WebViewFileChooserActivity.CHROME_CLIENT_BINDER_KEY;

public class WebViewChromeClient extends WebChromeClient {

    private Activity activity;
    private WebViewDialog dialog;
    private AlertDialog alertDialog;

    private ValueCallback<Uri[]> callback;
    private String cameraPhotoPath;
    private FileChooserParams params;

    AlertDialog getAlertDialog() {
        return alertDialog;
    }

    WebViewChromeClient(Activity activity, View activityNonVideoView,View loadingView, WebView webView, WebViewDialog dialog) {
        super();
        this.activity = activity;
        this.dialog = dialog;
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        Logger.getInstance().info("WebViewChromeClient onShowFileChooser.");
        if (callback != null) {
            Logger.getInstance().error("Trying to show another file chooser before previous one finished. Discard previous upload!");
            callback.onReceiveValue(null);
        }

        callback = filePathCallback;
        params = fileChooserParams;

        Logger.getInstance().verbose("Start file chooser activity.");

        final Bundle bundle = new Bundle();
        bundle.putBinder(CHROME_CLIENT_BINDER_KEY, new ObjectWrapperForBinder(this));

        WebViewFileChooserActivity.mainActivity = activity;
        Intent intent = new Intent(activity, WebViewFileChooserActivity.class);
        intent.putExtras(bundle);
        activity.startActivity(intent);

        return true;
    }

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Logger.getInstance().info("WebViewChromeClient onPermissionRequest, url: " + request.getOrigin().toString());

            // The grant/deny should be run in UI thread.
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        URL url = new URL(request.getOrigin().toString());
                        if (dialog.getPermissionTrustDomains().contains(url.getHost())) {
                            Logger.getInstance().info("Permission domain '" + url.getHost()  + "' contains in permission trusted domains. Granting...");
                            request.grant(request.getResources());
                        } else {
                            Logger.getInstance().error("Permission domain '" + url.getHost()  + "' is not allowed. Deny request.");
                            Logger.getInstance().error("If you want to allow permission access from this domain, add it through `UniWebView.AddPermissionTrustDomain` first");
                            request.deny();
                        }
                    } catch (MalformedURLException e) {
                        Logger.getInstance().error("onPermissionRequest failed due to malformed url exception. " + e.getMessage());
                        request.deny();
                    }
                }
            });
        }
    }

    FileChooserParams getFileChooserParams() {
        return params;
    }

    private void showAlert() {
        if (dialog.getImmersiveMode()) {
            alertDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            alertDialog.show();
            alertDialog.getWindow().getDecorView().setSystemUiVisibility(
                    dialog.getWindow().getDecorView().getSystemUiVisibility()
            );
            alertDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        } else {
            alertDialog.show();
        }
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(dialog.getContext());
        alertDialog = alertDialogBuilder
                .setTitle(url)
                .setMessage(message)
                .setCancelable(false)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        result.confirm();
                        alertDialog = null;
                    }
                }).create();
        showAlert();
        return true;
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(dialog.getContext());
        alertDialog = alertDialogBuilder
                .setTitle(url)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        result.confirm();
                        alertDialog = null;
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                        result.cancel();
                        alertDialog = null;
                    }
                }).create();
        showAlert();
        return true;
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(dialog.getContext());
        alertDialogBuilder
                .setTitle(url)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setCancelable(false);

        final EditText input = new EditText(dialog.getContext());
        input.setSingleLine();
        alertDialogBuilder.setView(input);

        alertDialogBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Editable editable = input.getText();
                String value = "";
                if (editable != null) {
                    value = editable.toString();
                }
                dialog.dismiss();
                result.confirm(value);
                alertDialog = null;
            }
        });

        alertDialogBuilder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
                result.cancel();
                alertDialog = null;
            }
        });

        alertDialog = alertDialogBuilder.create();
        showAlert();
        return true;
    }

    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        callback.invoke(origin, true, false);
    }

    void receivedFileValue(Intent intent, boolean hasImage) {
        Uri[] results = null;
        if (intent != null) {
            String uri = intent.getDataString();
            if (uri == null) { // Camera capture
                if (hasImage && cameraPhotoPath != null) {
                    results = new Uri[]{Uri.parse(cameraPhotoPath)};
                }
            } else { // Image picker
                Uri contentUri = Uri.parse(uri);
                String path = uriToFilename(contentUri);
                if (path != null) {
                    results = new Uri[]{Uri.fromFile(new File(path))};
                } else {
                    try {
                        InputStream input = activity.getContentResolver().openInputStream(contentUri);
                        File f = createTempFile(contentUri);
                        copyInputStreamToFile(input, f);
                        results = new Uri[]{Uri.fromFile(f)};
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Logger.getInstance().error("Can not get correct path on disk storage.");
                }
            }
        } else {
            if (hasImage && cameraPhotoPath != null) {
                results = new Uri[]{Uri.parse(cameraPhotoPath)};
            }
        }

        if (callback != null) {
            callback.onReceiveValue(results);
        }

        callback = null;
        params = null;
    }

    private void copyInputStreamToFile(InputStream in, File file ) {
        try {
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while((len=in.read(buf))>0){
                out.write(buf,0,len);
            }
            out.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String uriToFilename(Uri uri) {
        return ProviderPathConverter.getPath(activity, uri);
    }

    File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "IMAGE_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,   // prefix
                ".png",  // suffix
                storageDir      // directory
        );
    }

    File createTempFile(Uri uri) throws IOException {
        Cursor cursor = activity.getContentResolver()
                .query(uri, null, null, null, null, null);

        String displayName = "";
        if (cursor != null && cursor.moveToFirst()) {
            displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        }

        cursor.close();

        return File.createTempFile(
                displayName,   // prefix
                null,  // .tmp
                null      // tmp folder
        );
    }

    void setCameraPhotoPath(String cameraPhotoPath) {
        this.cameraPhotoPath = cameraPhotoPath;
    }

    String getCameraPhotoPath() {
        return cameraPhotoPath;
    }
}
