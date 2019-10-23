package com.unnynet.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.webkit.WebView.HitTestResult.UNKNOWN_TYPE;

class WebViewDialog extends Dialog implements JSInterface.OnJSMessage {

    final DialogListener listener;

    private HashSet<String> schemes;
    private HashMap<String, String> headers;

    // The sites which we should trust for permission request.
    private HashSet<String> permissionTrustDomains;
    private HashSet<String> sslExceptionDomains;

    private WebView webView;
    private boolean openLinksInExternalBrowser;
    private boolean immersiveMode = true;
    private boolean showSpinnerWhileLoading;
    private boolean isLoading;
    private Activity activity;
    private ProgressDialog spinner;
    private ImageButton closeBtn;
    private String spinnerText = "Loading...";

    // Origin and Size
    private int x;
    private int y;
    private int width;
    private int height;

    private boolean loadingInterrupted;
    private WebViewChromeClient chromeClient;

    private boolean animating;
    private boolean backButtonEnabled = true;
    private boolean closeButtonVisible = true;

    private float webViewAlpha = 1.0f;

    // Whether the web view already in "showing" state. This is not related to visibility of web view.
    // By using this we could avoid call dismiss or hide on the Dialog, which could solve an issue when
    // cooperation with Vuforia.
    private boolean webViewShowing = false;

    // Avoid show or hide web view for multiple times or without needs.
    private boolean webViewVisible = false;

    FrameLayout webViewContainer;

    // Sync properties
    static HashMap<String, String> presetUserAgent = new HashMap<>();
    static String defaultUserAgent = "";

    static String getUserAgent(WebViewDialog dialog, String name) {
        if (dialog != null) {
            return dialog.getUserAgent();
        } else {
            String value = presetUserAgent.get(name);
            return (value != null) ? value : defaultUserAgent;
        }
    }

    static void setUserAgent(WebViewDialog dialog, String name, String ua) {
        if (dialog != null) {
            dialog.setUserAgent(ua);
            presetUserAgent.remove(name);
        } else {
            presetUserAgent.put(name, ua);
        }
    }

    private String userAgent = defaultUserAgent;

    private String getUserAgent() {
        return userAgent;
    }

    private void setUserAgent(String userAgent) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            getWebView().getSettings().setUserAgentString(userAgent);
        }
        this.userAgent = userAgent;
    }

    private String url;

    String getUrl() {
        return url;
    }

    void updateUrl() {
        this.url = getWebView().getUrl();
    }

    private boolean canGoBack;
    private boolean canGoForward;

    boolean isCanGoBack() {
        return canGoBack;
    }

    boolean isCanGoForward() {
        return canGoForward;
    }

    void updateCanNavigate() {
        this.canGoBack = getWebView().canGoBack();
        this.canGoForward = getWebView().canGoForward();
    }

    private String name;
    // End of sync properties

    WebViewDialog(Activity activity, String name, DialogListener listener) {
        super(activity, android.R.style.Theme_Holo_NoActionBar);

        this.name = name;

        Logger.getInstance().debug("Creating new UniWebView dialog.");

        this.activity = activity;
        this.listener = listener;

        schemes = new HashSet<>();
        schemes.add("uniwebview");

        permissionTrustDomains = new HashSet<>();
        sslExceptionDomains = new HashSet<>();
        headers = new HashMap<>();

        prepareWindow();
        hideSystemUI();
        addWebViewContent();

        activity.registerForContextMenu(this.getWebView());

        setBouncesEnabled(false);

        // AndroidBug5497Workaround.assistFrameLayout(this.webViewContainer);
    }

    boolean touchFromAnotherDialog = false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!touchFromAnotherDialog) {
            return WebViewManager.getInstance().handleTouchEvent(this, activity, event);
        } else {
            boolean visible = webViewContainer.getVisibility() == View.VISIBLE;
            return visible && this.isEventInside(event);
        }
    }

    private boolean isEventInside(MotionEvent event) {
        return isViewContains(webView, (int) event.getRawX(), (int) event.getRawY());
    }

    private boolean isViewContains(View view, int rx, int ry) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        int w = view.getWidth();
        int h = view.getHeight();

        if (rx < x || rx > x + w || ry < y || ry > y + h) {
            return false;
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Logger.getInstance().verbose("onKeyDown, key code: " + keyCode);
        if (keyCode != KeyEvent.KEYCODE_BACK) {
            Logger.getInstance().verbose("Not back key. Delegating to super...");
            return super.onKeyDown(keyCode, event);
        }

        if (!backButtonEnabled) {
            Logger.getInstance().verbose("Back button is not enabled. Ignore.");
            return true;
        }

        if (!goBack()) {
            Logger.getInstance().verbose("No back page for the web view. Trying to close current web view...");
            listener.onDialogClosedByBackButton(this);
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Logger.getInstance().verbose("dispatchKeyEvent: " + event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            Logger.getInstance().verbose("Key down event for: " + keyCode);
            listener.onDialogKeyDown(this, keyCode);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        listener.onDialogClose(this);
    }

    boolean getAnimating() {
        return animating;
    }

    HashSet<String> getSchemes() {
        return schemes;
    }

    void setFrame(int x, int y, int width, int height) {
        Logger.getInstance().verbose(
                String.format(Locale.US, "Setting web dialog frame to {%d, %d, %d, %d}", x, y, width, height)
        );

        setPosition(x, y);
        setSize(width, height);
    }

    void setPosition(int x, int y) {
        this.x = x;
        this.y = y;

        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        window.setAttributes(params);

        webViewContainer.setX(x);
        webViewContainer.setY(y);
    }

    void setSize(int width, int height) {

        this.width = Math.max(0, width);
        this.height = Math.max(0, height);

        Window window = getWindow();
        Point size = displaySize();

        window.setLayout(size.x, size.y);

        ViewGroup.LayoutParams p = webViewContainer.getLayoutParams();
        p.width = this.width;
        p.height = this.height;
        webViewContainer.setLayoutParams(p);
    }

    void updateFrame() {
        setPosition(x, y);
        setSize(width, height);
    }

    boolean setShow(final boolean show, final boolean fade, UnnyNet.UnnyNetWebViewTransitionEdge edge, float duration, final String identifier) {

        if (webViewVisible && show) {
            Logger.getInstance().error("Showing web view is ignored since it is already visible.");

            return false;
        }

        if (!webViewVisible && !show) {
            Logger.getInstance().error("Hiding web view is ignored since it is already invisible.");
            return false;
        }

        if (animating) {
            Logger.getInstance().error("Trying to animate but another transition animation is not finished yet. Ignore this one.");
            return false;
        }

        if (webViewShowing) {
            boolean alreadyVisible = webViewContainer.getVisibility() == View.VISIBLE;
            if (show == alreadyVisible) {
                return false;
            }
        }

        if (show) {
            show();
            webViewVisible = true;
            showDialog();
            WebViewManager.getInstance().addShowingDialog(this);

            if (showSpinnerWhileLoading && isLoading) {
                showSpinner();
            }

        } else {
            webViewVisible = false;
            // Hide keyboard when the web view gets dismissed.
            InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(webView.getWindowToken(), 0);

            hideSpinner();
        }

        if (!fade && edge == UnnyNet.UnnyNetWebViewTransitionEdge.None) {
            // No animation needed.

            // We can only do UI things in UI thread, however Unity cannot directly call native methods in UI thread.
            // So we need to "simulated" a delay and prevent sending method back to Unity directly.
            // Now, some count latch is used to wait for async method return value and return it back to Unity.
            // Without this handler, the return value be fired directly and cause an error in timing.
            Handler handler = new Handler();
            handler.postDelayed(() -> finishShowDialog(show, identifier), 1);

        } else {
            // Need animation
            animatedShow(show, fade, edge, duration, identifier);
        }
        return true;
    }

    private void animatedShow(final boolean show, boolean fade, UnnyNet.UnnyNetWebViewTransitionEdge edge, float duration, final String identifier) {
        animating = true;

        // Setup animations
        AnimationSet set = new AnimationSet(false);
        int durationMillisecond = (int) (duration * 1000);

        Animation fadeAnimation = fadeAnimation(show, fade, durationMillisecond);
        if (fadeAnimation != null) {
            set.addAnimation(fadeAnimation);
        }

        Animation moveAnimation = moveAnimation(show, edge, durationMillisecond);
        if (moveAnimation != null) {
            set.addAnimation(moveAnimation);
        }

        // Start animating
        webViewContainer.startAnimation(set);

        // Clean & Notify when animation finished
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            animating = false;
            webViewContainer.clearAnimation();
            finishShowDialog(show, identifier);
        }, durationMillisecond);
    }

    private void finishShowDialog(boolean show, String identifier) {
        if (show) {
            listener.onShowTransitionFinished(this, identifier);
        } else {
            webViewContainer.setVisibility(View.GONE);
            hide();
            listener.onHideTransitionFinished(this, identifier);
        }
    }

    boolean animateTo(final int x, final int y, final int width, final int height, float duration, float delay, final String identifier) {

        if (animating) {
            Logger.getInstance().error("Trying to animate but another transition animation is not finished yet. Ignore this one.");
            return false;
        }

        animating = true;

        int durationMillisecond = (int) (duration * 1000);
        int delayMillisecond = (int) (delay * 1000);

        AnimationSet set = new AnimationSet(false);
        set.addAnimation(moveToAnimation(x, y, durationMillisecond, delayMillisecond));
        set.addAnimation(sizeToAnimation(width, height, durationMillisecond, delayMillisecond));

        webViewContainer.startAnimation(set);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                animating = false;
                setFrame(x, y, width, height);
                webViewContainer.clearAnimation();
                listener.onAnimationFinished(WebViewDialog.this, identifier);
            }
        }, durationMillisecond + delayMillisecond);
        return true;
    }

    void load(String url) {
        Logger.getInstance().info("UniWebView will load url: " + url + ". With headers: " + headers.toString());
        boolean result = shouldOverride(url, false);
        if (!result) {
            webView.loadUrl(url, headers);
        }
    }

    void stop() {
        webView.stopLoading();
        webView.getClient().setUserStopped(true);
    }

    boolean shouldOverride(String url, boolean checkExternal) {
        Logger logger = Logger.getInstance();
        logger.info("shouldOverrideUrlLoading for: " + url);
        URLLoadingResponser responser = new URLLoadingResponser(activity, this, url);
        if (responser.handleWithIntent()) {
            logger.debug("Url handled by intent.");
            return true;
        }

        if (responser.canResponseDefinedScheme()) {
            logger.debug("Url redirected to Unity: " + url);
            listener.onSendMessageReceived(this, url);
            return true;
        }

        if (checkExternal && openUrlExternal(url)) {
            return true;
        }

        logger.debug("Url is opening without overridden: " + url);
        return false;
    }

    void loadHTMLString(String html, String baseUrl) {
        Logger.getInstance().info("UniWebView will load html string with base url: " + baseUrl);
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
    }

    void addJavaScript(String jsString, final String identifier) {
        if (jsString == null) {
            Logger.getInstance().error("Trying to add null as js string. Aborting...");
            return;
        }

        Logger.getInstance().debug("Adding javascript string to web view. Requesting string: " + jsString);
        webView.evaluateJavascript(jsString, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Logger.getInstance().info("Receive a call back of adding js interface: " + value);
                if (value.equalsIgnoreCase("null")) {
                    WebViewResult payload = new WebViewResult(identifier, "0", "");
                    listener.onAddJavaScriptFinished(WebViewDialog.this, payload);
                } else {
                    WebViewResult payload = new WebViewResult(identifier, "-1", value);
                    listener.onAddJavaScriptFinished(WebViewDialog.this, payload);
                }
            }
        });
    }

    void evaluateJavaScript(String jsString, final String identifier, UnnyNet.OnWebViewResultListener callBack) {
        if (jsString == null) {
            Logger.getInstance().error("Trying to evaluate null as js string. Aborting...");
            return;
        }
        Logger.getInstance().debug("Evaluating javascript string in web view. Requesting string: " + jsString);
        webView.evaluateJavascript(jsString, value -> {
            Logger.getInstance().info("Receive a call back of evaluating js interface: " + value);
            if (value.equalsIgnoreCase("null")) {
                WebViewResult payload = new WebViewResult(identifier, "0", "");
//                    listener.onJavaScriptFinished(WebViewDialog.this, payload);
                if (callBack != null)
                    callBack.onCompleted(payload);
            } else {
                // Remove beginning and end double quote.
                value = value.replaceAll("^\"|\"$", "");

                // Convert to non-utf version (work around for '<' in result is presented by \u003C)
                value = removeUTFCharacters(value).toString();

                WebViewResult payload = new WebViewResult(identifier, "0", value);
//                    listener.onJavaScriptFinished(WebViewDialog.this, payload);
                if (callBack != null)
                    callBack.onCompleted(payload);
            }
        });
    }

    boolean goBack() {
        if (webView.canGoBack()) {
            webView.goBack();
            return true;
        } else {
            return false;
        }
    }

    boolean goForward() {
        if (webView.canGoForward()) {
            webView.goForward();
            return true;
        } else {
            return false;
        }
    }

    void destroy() {
        webView.loadUrl("about:blank");
        WebViewManager.getInstance().removeShowingDialog(this);

        // Flush and persist current cookie state.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            CookieManager.getInstance().flush();

        dismiss();
    }

    void goBackground() {
        if (isLoading) {
            loadingInterrupted = true;
            webView.stopLoading();
        }

        if (this.webViewShowing) {
            AlertDialog alert = chromeClient.getAlertDialog();
            if (alert != null) {
                alert.hide();
            }
            hide();
            webView.onPause();
        }
    }

    void goForeground() {
        if (loadingInterrupted) {
            webView.reload();
            loadingInterrupted = false;
        }

        if (this.webViewShowing) {
            show();

            AlertDialog alert = chromeClient.getAlertDialog();
            if (alert != null) {
                alert.show();
            }

            webView.onResume();
        }
    }

    void setSpinnerText(String spinnerText) {
        if (spinnerText != null) {
            this.spinnerText = spinnerText;
        } else {
            this.spinnerText = "Loading...";
        }
        spinner.setMessage(spinnerText);
    }

    void showSpinner() {
        Logger.getInstance().verbose("Show spinner for loading.");
        if (immersiveMode) {
            immersiveShow(spinner, this.getWindow());
        } else {
            spinner.show();
        }
    }

    void hideSpinner() {
        Logger.getInstance().verbose("Hide spinner.");
        spinner.hide();
    }

    void setBackgroundColor(float r, float g, float b, float a) {
        int redInt = (int) (r * 255);
        int greenInt = (int) (g * 255);
        int blueInt = (int) (b * 255);
        int alphaInt = (int) (a * 255);

        int backgroundColor = Color.argb(alphaInt, redInt, greenInt, blueInt);
        webViewContainer.setBackground(new ColorDrawable(backgroundColor));
    }

    void setOpenLinksInExternalBrowser(boolean openLinksInExternalBrowser) {
        this.openLinksInExternalBrowser = openLinksInExternalBrowser;
    }

    private void prepareWindow() {
        Logger.getInstance().verbose("Preparing window layout for web view dialog.");
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        if (Build.VERSION.SDK_INT >= 28) {
            int mode = this.activity.getWindow().getAttributes().layoutInDisplayCutoutMode;
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.layoutInDisplayCutoutMode = mode;
            getWindow().setAttributes(params);
        }
    }

    private void addWebViewContent() {
        // Create layout for web view and video view
        webViewContainer = new FrameLayout(getContext());
        webViewContainer.setVisibility(View.VISIBLE);

        webView = new WebView(activity, this) {
            @Override
            public HashMap<String, String> getCustomizeHeaders() {
                Logger.getInstance().error("Get header!!!");
                return WebViewDialog.this.getHeaders();
            }
        };

        webView.setClient(new WebViewClient(this));

        chromeClient = new WebViewChromeClient(activity, webViewContainer, null, webView, this);
        webView.setWebChromeClient(chromeClient);

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                Logger.getInstance().info("UniWebView onDownloadStart for url: " + url);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                activity.startActivity(intent);
            }
        });

        webView.setVisibility(View.VISIBLE);
        webView.setBackgroundColor(0);

        // Create spinner
        spinner = new ProgressDialog(getContext());
        spinner.setCanceledOnTouchOutside(true);
        spinner.requestWindowFeature(Window.FEATURE_NO_TITLE);
        spinner.setMessage(spinnerText);

        // Add views and content to dialog
        ViewGroup.LayoutParams webParams = new ViewGroup.LayoutParams(width, height);
        webViewContainer.setX(x);
        webViewContainer.setY(y);
        webViewContainer.setBackgroundColor(Color.parseColor("#ff36393f"));
        addContentView(webViewContainer, webParams);
        webViewContainer.addView(webView);

        float density = getContext().getResources().getDisplayMetrics().density;

        LayoutInflater li = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        closeBtn = new ImageButton(getContext());
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onJSMessage(JSInterface.Type.CLOSE, "");
            }
        });
        closeBtn.setScaleType(ImageView.ScaleType.FIT_END);
        closeBtn.setVisibility(this.closeButtonVisible ? View.VISIBLE : View.GONE);
        closeBtn.setImageResource(R.drawable.closebtn);
        closeBtn.setBackgroundResource(0);
        closeBtn.setPadding(0, 0, 0, 0);
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams((int) (25 * density), (int) (25 * density));
        btnParams.gravity = Gravity.END | Gravity.TOP;
        btnParams.setMargins(0, (int) (10 * density), (int) (10 * density), 0);
        closeBtn.setLayoutParams(btnParams);
        webViewContainer.addView(closeBtn);

        setBackgroundColor(1.0f, 1.0f, 1.0f, 1.0f);
        subOnLayoutChange();
    }

    private void subOnLayoutChange() {
        final View root = activity.getWindow().getDecorView().getRootView();
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect rect = new Rect();
            root.getWindowVisibleDisplayFrame(rect);

            Display display = activity.getWindowManager().getDefaultDisplay();
            Point point = new Point();
            display.getSize(point);

            int screenHeight = point.y;
            int heightDiff = root.getRootView().getHeight() - rect.bottom - rect.top;
            if (heightDiff > screenHeight / 3) { // assume that this means that the keyboard is on
//                    hideSystemUI();
                changeWebViewHeightOnKeyboardShow(heightDiff);
            } else {
                changeWebViewHeightOnKeyboardShow(0);
            }
        });
    }

    private void changeWebViewHeightOnKeyboardShow(int height) {
        if (webView != null) {
            FrameLayout.LayoutParams pms = (FrameLayout.LayoutParams) webView.getLayoutParams();
            pms.bottomMargin = height;
            webView.setLayoutParams(pms);
        }
    }

    @Override
    public void onJSMessage(JSInterface.Type type, String data) {
        if (listener != null)
            listener.onJSMessage(type, data);
    }

    /**
     * Try to open an url in external browser. This will consider web view clicking type and the external open settings.
     *
     * @return true if opening successfully. Otherwise, false.
     */
    boolean openUrlExternal(String url) {
        if (webView == null || webView.getHitTestResult() == null) {
            Logger.getInstance().error("Failed to open url due to dialog or webview being null. Url: " + url);
            return false;
        }

        if (!openLinksInExternalBrowser) {
            Logger.getInstance().verbose("UniWebView should open links in current web view.");
            return false;
        }

        if (webView.getHitTestResult().getType() == UNKNOWN_TYPE) {
            Logger.getInstance().debug("UniWebView getHitTestResult unknown. Do not open url externally.");
            return false;
        }

        Logger.getInstance().verbose("UniWebView is opening links in external browser.");

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        getContext().startActivity(intent);

        return true;
    }

    void hideSystemUI() {
        final View decorView = getWindow().getDecorView();

        // Fix input method showing causes ui show issue when slide up for navigation bar.
        int updatedUIOptions;
        if (immersiveMode) {
            updatedUIOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        } else {
            updatedUIOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        }
        decorView.setSystemUiVisibility(updatedUIOptions);

        final int finalOptions = updatedUIOptions;
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                decorView.setSystemUiVisibility(finalOptions);
            }
        });

    }

    void setLoading(boolean loading) {
        isLoading = loading;
    }

    boolean isShowSpinnerWhileLoading() {
        return showSpinnerWhileLoading;
    }

    void setCloseButtonVisible(final boolean visible) {
        this.closeButtonVisible = visible;
        if(this.closeBtn != null)
            closeBtn.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    void setShowSpinnerWhileLoading(boolean showSpinnerWhileLoading) {
        this.showSpinnerWhileLoading = showSpinnerWhileLoading;
    }

    void print() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            PrintManager printManager = (PrintManager) this.getContext().getSystemService(Context.PRINT_SERVICE);
            PrintDocumentAdapter printAdapter = this.getWebView().createPrintDocumentAdapter(this.webView.getUrl());
            printManager.print("UnnynetWebView Printing", printAdapter, new PrintAttributes.Builder().build());
        }
    }

    WebView getWebView() {
        return webView;
    }

    HashSet<String> getSslExceptionDomains() {
        return sslExceptionDomains;
    }

    HashSet<String> getPermissionTrustDomains() {
        return permissionTrustDomains;
    }

    boolean getImmersiveMode() {
        return immersiveMode;
    }

    void setImmersiveMode(boolean immersiveMode) {
        this.immersiveMode = immersiveMode;
        hideSystemUI();
    }

    void setBouncesEnabled(boolean enable) {
        if (enable) {
            webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        } else {
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
    }

    void setBackButtonEnabled(boolean backButtonEnabled) {
        this.backButtonEnabled = backButtonEnabled;
    }

    void setZoomEnabled(boolean enable) {
        webView.getSettings().setBuiltInZoomControls(enable);
    }

    float getWebViewAlpha() {
        return webViewAlpha;
    }

    void setWebViewAlpha(float alpha, boolean actually) {
        float a = (alpha > 1.0f) ? 1.0f : ((alpha < 0.0f) ? 0.0f : alpha);
        webViewAlpha = a;
        if (actually) {
            webViewContainer.setAlpha(a);
        }
    }

    void setHeaderField(String key, String value) {
        if (key == null || key.length() == 0) {
            Logger.getInstance().error("Trying to set null or empty key for header field. Please check you have set correct key.");
            return;
        }

        if (value == null) {
            headers.remove(key);
        } else {
            headers.put(key, value);
        }
    }

    HashMap<String, String> getHeaders() {
        return headers;
    }

    void clearHttpAuthUsernamePassword(String host, String realm) {
        getWebView().setHttpAuthUsernamePassword(host, realm, null, null);
    }

    // Workaround for a showing dialog not responsive under immersive mode.
    private void immersiveShow(Dialog dialog, Window superWindow) {
        Window window = dialog.getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        dialog.show();

        View decorView = window.getDecorView();
        if (superWindow != null) {
            decorView.setSystemUiVisibility(superWindow.getDecorView().getWindowSystemUiVisibility());
        } else {
            decorView.setSystemUiVisibility(decorView.getWindowSystemUiVisibility());
        }

        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    private void showDialog() {
        if (webViewShowing) {
            webViewContainer.setVisibility(View.VISIBLE);
        } else {
            webViewShowing = true;
            if (immersiveMode) {
                immersiveShow(this, activity.getWindow());
            } else {
                show();
            }
        }
    }

    private Point displaySize() {
        Display display = getWindow().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        if (immersiveMode) {
            display.getRealSize(size);
            return size;
        } else {
            display.getSize(size);
            return size;
        }
    }

    private Animation fadeAnimation(boolean show, boolean fade, int durationMillisecond) {
        if (!fade) {
            return null;
        }

        float startAlpha = show ? 0.0f : getWebViewAlpha();
        float endAlpha = show ? getWebViewAlpha() : 0.0f;
        Animation animation = new AlphaAnimation(startAlpha, endAlpha);
        animation.setFillAfter(true);
        animation.setDuration(durationMillisecond);
        return animation;
    }

    private Animation moveAnimation(boolean show, UnnyNet.UnnyNetWebViewTransitionEdge edge, int durationMillisecond) {
        if (edge == UnnyNet.UnnyNetWebViewTransitionEdge.None) {
            return null;
        }

        int xValue = 0, yValue = 0;
        Point size = displaySize();
        switch (edge) {
            case Top:
                xValue = 0;
                yValue = -size.y;
                break;
            case Left:
                xValue = -size.x;
                yValue = 0;
                break;
            case Bottom:
                xValue = 0;
                yValue = size.y;
                break;
            case Right:
                xValue = size.x;
                yValue = 0;
                break;
        }

        Animation animation = new TranslateAnimation(show ? xValue : 0, show ? 0 : xValue, show ? yValue : 0, show ? 0 : yValue);
        animation.setFillAfter(true);
        animation.setDuration(durationMillisecond);
        return animation;
    }

    private Animation moveToAnimation(int x, int y, int durationMillisecond, int delayMillisecond) {
        Animation animation = new TranslateAnimation(0, x - this.x, 0, y - this.y);
        animation.setFillAfter(true);
        animation.setDuration(durationMillisecond);
        animation.setStartOffset(delayMillisecond);
        return animation;
    }

    private Animation sizeToAnimation(int width, int height, int durationMillisecond, int delayMillisecond) {
        Animation animation = new ResizeAnimation(webViewContainer, this.width, width, this.height, height);
        animation.setFillAfter(true);
        animation.setDuration(durationMillisecond);
        animation.setStartOffset(delayMillisecond);
        return animation;
    }

    private StringBuffer removeUTFCharacters(String data) {
        Pattern p = Pattern.compile("\\\\u(\\p{XDigit}{4})");
        Matcher m = p.matcher(data);
        StringBuffer buf = new StringBuffer(data.length());
        while (m.find()) {
            String ch = String.valueOf((char) Integer.parseInt(m.group(1), 16));
            m.appendReplacement(buf, Matcher.quoteReplacement(ch));
        }
        m.appendTail(buf);
        return buf;
    }

    interface DialogListener {
        void onPageFinished(WebViewDialog dialog, int statusCode, String url);

        void onJSMessage(JSInterface.Type type, String data);

        void onPageStarted(WebViewDialog dialog, String url);

        void onReceivedError(WebViewDialog dialog, int errorCode, String description, String failingUrl);

        boolean shouldOverrideUrlLoading(WebViewDialog dialog, String url);

        void onSendMessageReceived(WebViewDialog dialog, String url);

        void onDialogClosedByBackButton(WebViewDialog dialog);

        void onDialogKeyDown(WebViewDialog dialog, int keyCode);

        void onDialogClose(WebViewDialog dialog);

        void onAddJavaScriptFinished(WebViewDialog uniWebViewDialog, WebViewResult value);

//        void onJavaScriptFinished(WebViewDialog dialog, WebViewResult result);

        void onAnimationFinished(WebViewDialog dialog, String identifier);

        void onShowTransitionFinished(WebViewDialog dialog, String identifier);

        void onHideTransitionFinished(WebViewDialog dialog, String identifier);
    }
}