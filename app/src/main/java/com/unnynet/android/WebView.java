package com.unnynet.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.view.ContextMenu;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Locale;


@SuppressLint("ViewConstructor")
class WebView extends android.webkit.WebView {

    private WebViewClient client;
    private Activity activity;

    @SuppressLint("SetJavaScriptEnabled")
    WebView(Context context, JSInterface.OnJSMessage onMessage) {
        super(context);
        this.activity = (Activity) context;
        WebSettings webSettings = getSettings();

        webSettings.setJavaScriptEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setGeolocationEnabled(true);
        webSettings.setSavePassword(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            webSettings.setAllowFileAccessFromFileURLs(true);
            webSettings.setAllowUniversalAccessFromFileURLs(true);
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        setScrollBarStyle(SCROLLBARS_INSIDE_OVERLAY);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        setLayoutParams(params);

        addJavascriptInterface(new JSInterface(onMessage), "globalReactFunctions");
    }
    
    static void clearCookies() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
            Logger.getInstance().verbose("Cookie manager flushed.");
        }
    }

    static void setCookie(String url, String cookie) {
        Logger logger = Logger.getInstance();

        CookieManager cm = CookieManager.getInstance();
        logger.verbose("Cookie set for url: " + url + ". Content: " + cookie);

        cm.setCookie(url, cookie);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            cm.flush();
        logger.verbose("Cookie manager flushed.");
    }

    static String getCookie(String url, String key) {
        Logger logger = Logger.getInstance();

        String value = "";
        CookieManager cm = CookieManager.getInstance();
        String cookies = cm.getCookie(url);
        if (cookies == null) {
            Logger.getInstance().debug("The content for url is not found in cookie. Url: " + url);
            return "";
        }

        logger.verbose("Cookie string is found: " + cookies + ", for url: " + url);
        logger.verbose("Trying to parse cookie to find for key: " + key);

        String[] temp = cookies.split(";");
        for (String kvPair : temp) {
            if (kvPair.contains(key)) {
                String[] pair = kvPair.split("=", 2);
                if (pair.length >= 2) {
                    value = pair[1];
                    logger.verbose("Found cookie value: " + value + ", for key: " + key);
                }
            }
        }

        return value;
    }

    public WebViewClient getClient() {
        return client;
    }

    public void setClient(WebViewClient client) {
        this.client = client;
        this.setWebViewClient(client);
    }

    public HashMap<String, String> getCustomizeHeaders() {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        super.onCreateContextMenu(menu);

        final HitTestResult webViewHitTestResult = this.getHitTestResult();
        int hitType = webViewHitTestResult.getType();
        if (hitType == HitTestResult.IMAGE_TYPE || hitType == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            final String downloadUrl = webViewHitTestResult.getExtra();
            if(downloadUrl == null)
                return;

            if (!downloadUrl.toLowerCase(Locale.ROOT).startsWith("http://") && !downloadUrl.toLowerCase(Locale.ROOT).startsWith("https://")) {
                return;
            }

            menu.setHeaderTitle(downloadUrl);
            menu.add(0, 1, 0, "Save Image").setOnMenuItemClickListener(menuItem -> {
                if (Build.VERSION.SDK_INT >= 23 && WebView.this.activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    WebView.this.activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                    Toast.makeText(getContext(), "No external storage write permission.", Toast.LENGTH_LONG).show();
                    return false;
                }

                if (URLUtil.isValidUrl(downloadUrl)) {
                    String fileName = URLUtil.guessFileName(downloadUrl, null, MimeTypeMap.getFileExtensionFromUrl(downloadUrl));
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDescription(fileName);
                    request.setTitle(fileName);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

//                        HashMap<String, String> headers = getCustomizeHeaders();
//                        for (HashMap.Entry<String, String> entry : headers.entrySet()) {
//                            String key = entry.getKey();
//                            String value = entry.getValue();
//                            request.addRequestHeader(key, value);
//                        }

                    DownloadManager downloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                    if (downloadManager != null) {
                        downloadManager.enqueue(request);
                        Toast.makeText(getContext(), "Download Started.", Toast.LENGTH_LONG).show();
                    } else {
                        Logger.getInstance().error("Can't get DownloadManager");
                    }

                } else {
                    Toast.makeText(getContext(), "Invalid URL.", Toast.LENGTH_LONG).show();
                }
                return false;
            });
        }

    }
}
