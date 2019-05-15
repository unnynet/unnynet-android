package com.unnynet.android;

import android.app.Activity;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

class WebViewManager {
    private HashMap<String, WebViewDialog> webViewDialog;
    private ArrayList<WebViewDialog> showingDialogs;

    private static WebViewManager instance = null;

    private WebViewManager() {
        webViewDialog = new HashMap<>();
        showingDialogs = new ArrayList<>();
    }

    static WebViewManager getInstance() {
        if (instance == null) {
            instance = new WebViewManager();
        }
        return instance;
    }

    WebViewDialog getWebViewDialog(String name) {
        if (name != null && name.length() != 0 && webViewDialog.containsKey(name)) {
            return webViewDialog.get(name);
        }
        return null;
    }

    void removeWebView(String name) {
        if (webViewDialog.containsKey(name)) {
            Logger.getInstance().debug("Removing web view dialog from manager: " + name);
            webViewDialog.remove(name);
        }
    }

    void setWebView(String name, WebViewDialog webViewDialog) {
        Logger.getInstance().debug("Adding web view dialog to manager: " + name);
        this.webViewDialog.put(name, webViewDialog);
    }

    private Collection<WebViewDialog> allDialogs() {
        return webViewDialog.values();
    }

    void addShowingDialog(WebViewDialog webViewDialog) {
        if (!showingDialogs.contains(webViewDialog)) {
            showingDialogs.add(webViewDialog);
        }
    }

    void removeShowingDialog(WebViewDialog webViewDialog) {
        showingDialogs.remove(webViewDialog);
    }

    boolean handleTouchEvent(WebViewDialog dialog, Activity activity, MotionEvent event) {
        boolean touchHandledByAnotherDialog = false;
        for (WebViewDialog d : this.allDialogs()) {
            if (d != dialog) {
                // Handle touch events for multiple web dialogs. Proxy events to all other dialogs.
                d.getWebView().requestFocus();
                d.touchFromAnotherDialog = true;
                touchHandledByAnotherDialog = d.dispatchTouchEvent(event) || touchHandledByAnotherDialog;
                d.touchFromAnotherDialog = false;
            }
        }

        // Also proxy to activity if no other dialog handled the event.
        if (!touchHandledByAnotherDialog) {
            activity.dispatchTouchEvent(event);
        }
        return false;
    }

//    ArrayList<WebViewDialog> getShowingDialogs() {
//        return showingDialogs;
//    }
}
