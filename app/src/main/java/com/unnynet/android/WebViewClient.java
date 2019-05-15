package com.unnynet.android;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.text.method.PasswordTransformationMethod;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.net.MalformedURLException;
import java.net.URL;


class WebViewClient extends android.webkit.WebViewClient {

    private WebViewDialog dialog;
    private boolean loadingSuccess;
    private boolean userStopped;
    private boolean sslErrored;
    private int httpStatusCode = 200;

    WebViewClient(WebViewDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        Logger.getInstance().info("WebClient onPageStarted: " + url);

        dialog.setLoading(true);
        dialog.updateUrl();
        dialog.updateCanNavigate();

        loadingSuccess = true;
        userStopped = false;
        sslErrored = false;
        httpStatusCode = 200;

        if (dialog.isShowSpinnerWhileLoading() && dialog.isShowing()) {
            dialog.showSpinner();
        }
        dialog.hideSystemUI();
        dialog.listener.onPageStarted(dialog, url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);

        dialog.setLoading(false);
        dialog.updateUrl();
        dialog.updateCanNavigate();

        dialog.hideSpinner();
        dialog.hideSystemUI();

        if (loadingSuccess) {
            if (sslErrored) {
                Logger.getInstance().error("WebClient onPageFinished: onReceivedError for url: " + url + " Error Code: " + -1202 + " Error: ssl error");
                dialog.listener.onReceivedError(dialog, -1202, "ssl error", url);
            } else if (userStopped) {
                Logger.getInstance().error("WebClient onPageFinished: onReceivedError for url: " + url + " Error Code: " + 999 + " Error: Operation cancelled.");
                dialog.listener.onReceivedError(dialog, -999, "Operation cancelled.", url);
            } else {
                Logger.getInstance().info("WebClient onPageFinished: " + url + ". Status Code:" + httpStatusCode + " Loading success: " + loadingSuccess);
                dialog.listener.onPageFinished(dialog, httpStatusCode, url);
            }
        }
    }

    @TargetApi(android.os.Build.VERSION_CODES.M)
    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        Logger.getInstance().error("WebClient onReceivedHttpError: onReceivedError for url: " + request.getUrl() + ", request.isForMainFrame[" + request.isForMainFrame() + "]" + " Error Code: " + errorResponse.getStatusCode() + " => " + errorResponse.getReasonPhrase());
        if (request.isForMainFrame()) {
            httpStatusCode = errorResponse.getStatusCode();
        }
    }

    @TargetApi(android.os.Build.VERSION_CODES.M)
    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        Logger.getInstance().error("WebClient onReceivedError: for url: " + request.getUrl() + ", request.isForMainFrame[" + request.isForMainFrame() + "]" + " Error Code: " + error.getErrorCode() + " => " + error.getDescription());

        loadingSuccess = false;

        dialog.hideSpinner();
        dialog.hideSystemUI();
        dialog.updateUrl();
        dialog.updateCanNavigate();

        dialog.listener.onReceivedError(dialog, error.getErrorCode(), error.getDescription().toString(), request.getUrl().toString());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        Logger.getInstance().error("WebClient onReceivedError: for url: " + failingUrl + " Error Code: " + errorCode + " Error: " + description);

        loadingSuccess = false;

        dialog.hideSpinner();
        dialog.hideSystemUI();
        dialog.updateUrl();
        dialog.updateCanNavigate();

        dialog.listener.onReceivedError(dialog, errorCode, description, failingUrl);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        Logger.getInstance().info("WebClient shouldOverrideUrlLoading: " + url);
        if (!dialog.listener.shouldOverrideUrlLoading(dialog, url)) {

            // https://github.com/onevcat/UniWebView/issues/53
            if (url.startsWith("file://")) {
                Logger.getInstance().debug("Loading a local file. The local file loading will never be overridden.");
                return false;
            }

            Logger.getInstance().debug("Adding customized header to request. " + dialog.getHeaders().toString());
            view.loadUrl(url, dialog.getHeaders());
        }
        return true;
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        Logger.getInstance().error("WebClient onReceivedSslError. Error url: " + error.getUrl() + " Error type: " + error.getPrimaryError());
        dialog.updateUrl();
        dialog.updateCanNavigate();
        try {
            Logger.getInstance().verbose("Trying to process SSL error...");
            URL url = new URL(error.getUrl());
            String host = url.getHost();
            if (dialog.getSslExceptionDomains().contains(host)) {
                Logger.getInstance().verbose("Found domain '" + host  + "' in sslExceptionDomains, proceeding url...");
                handler.proceed();
            } else {
                Logger.getInstance().verbose("Domain '" + host  + "' is not in exception. Refuse proceeding url.");
                sslErrored = true;
                handler.cancel();
            }
        } catch (MalformedURLException e) {
            Logger.getInstance().verbose("Url '" + error.getUrl()  + "' is malformed. Refuse proceeding url." + " Exception: " + e);
            sslErrored = true;
            handler.cancel();
        }
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
        String username = null;
        String password = null;
        dialog.updateUrl();
        dialog.updateCanNavigate();

        if (handler.useHttpAuthUsernamePassword() && view != null) {
            String[] haup = view.getHttpAuthUsernamePassword(host, realm);
            if (haup != null && haup.length == 2) {
                username = haup[0];
                password = haup[1];
            }
        }

        if (username != null && password != null) {
            handler.proceed(username, password);
        }  else {
            showHttpAuthDialog(view, handler, host, realm);
        }
    }

    private void showHttpAuthDialog(final WebView view, final HttpAuthHandler handler, final String host, final String realm) {
        final Context context = dialog.getContext();
        LinearLayout layout = new LinearLayout(context);

        final EditText userText = new EditText(context);
        userText.setHint("User Name");

        final EditText passwordText = new EditText(context);
        passwordText.setHint("Password");
        passwordText.setTransformationMethod(PasswordTransformationMethod.getInstance());

        layout.setOrientation(LinearLayout.VERTICAL);

        layout.addView(userText);
        layout.addView(passwordText);

        final AlertDialog.Builder mHttpAuthDialog = new AlertDialog.Builder(context);
        mHttpAuthDialog.setTitle("Authorization Required")
                .setMessage(host)
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, whichButton) -> {
                    String username = userText.getText().toString();
                    String password = passwordText.getText().toString();

                    view.setHttpAuthUsernamePassword(host, realm, username, password);

                    handler.proceed(username, password);
                })
                .setNegativeButton("Cancel", (dialog, whichButton) -> handler.cancel())
                .create().show();
    }

    void setUserStopped(boolean userStopped) {
        this.userStopped = userStopped;
    }
}
