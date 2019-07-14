package com.unnynet.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.view.Display;
import android.webkit.WebSettings;

import com.unnynet.android.helper.AndroidPermissions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class UnnyNetDefault implements UnnyNet {
    private String JSON_GAME_ID = "gameId";
    private String JSON_GUESTS_ALLOWED = "guests_allowed";
    private String JSON_GAME_LOGIN = "game_login";
    private String JSON_DEFAULT_CHANNEL = "default_channel";
    private String JSON_OPEN_WITH_FADE = "open_fade";
    private String JSON_OPEN_WITH_ANIMATION = "open_animation";
    private String JSON_CLOSE_WITH_FADE = "close_fade";
    private String JSON_CLOSE_WITH_ANIMATION = "close_animation";
    private String JSON_PUBLIC_KEY = "public_key";
    private int SEND_REQUEST_RETRY_DELAY = 1000;
    private int TASK_PERIOD = 100;
    private String ExtStoragePermissionStatus = "ExtStoragePermissionStatus";
    private String CommandQueue = "CommandQueue";
    private String WebViewPrefs = "UNWebViewPrefs";
    private String urlOnStart = "https://unnynet.com/#/plugin/8ff16d3c-ebcc-4582-a734-77ca6c14af29";//Default UnnyNet group for developers

    private final int UnnyNetPluginVersion = 2;

    private int DEF_PERMISSION_STATUS = AndroidPermissions.PERMISSION_DENIED;
    private UnnyNetWebViewTransitionEdge DEFAULT_ANIMATION = UnnyNetWebViewTransitionEdge.Left;

    private static final String DEFAULT_DIALOG_NAME = "un_dialog";

    private Activity activity;

    private String gameId;
    private String defaultChannel = "general";
    private String publicKey;
    boolean allowGuestMode = true;
    private boolean loginWithCredentials = false;
    private boolean openWithFade = false;
    private UnnyNetWebViewTransitionEdge openAnimationDirection = DEFAULT_ANIMATION;
    private boolean closeWithFade = false;
    private UnnyNetWebViewTransitionEdge closeAnimationDirection = DEFAULT_ANIMATION;
    private String dialogName = DEFAULT_DIALOG_NAME;

    private OnPlayerAuthorizedListener onPlayerAuthorized;
    private OnPlayerNameChangedListener onPlayerNameChanged;
    private OnRankChangedListener onRankChanged;
    private OnNewGuildListener onNewGuild;
    private OnGameLoginRequestListener onGameLoginRequest;
    private OnNewGuildRequestListener onNewGuildRequest;
    private OnAchievementCompletedListener onAchievementCompleted;
    private ScheduledThreadPoolExecutor exec;

    private ScheduledFuture<?> queueCheckerTask;
    private List<CommandInfo> queue = new ArrayList<>();
    private RequestsManager requestsManager;

    void applyStartURL() {
        loadGameId();
        String addPath = getAdditionalPath();
        urlOnStart = "https://unnynet.com" + addPath;
    }


    private void loadGameId() {
        String json = Utils.loadContentFromFile(activity, "unnynet.data.json");
        if (json != null) {
            try {
                JSONObject jsonObj = new JSONObject(json);

                if (json.contains(JSON_GAME_ID))
                    gameId = jsonObj.getString(JSON_GAME_ID);
                else
                    gameId = "8ff16d3c-ebcc-4582-a734-77ca6c14af29";//Default UnnyNet group for developers

                if (json.contains(JSON_GUESTS_ALLOWED))
                    allowGuestMode = jsonObj.getBoolean(JSON_GUESTS_ALLOWED);

                if (json.contains(JSON_PUBLIC_KEY))
                    publicKey = jsonObj.getString(JSON_PUBLIC_KEY);

                if (json.contains(JSON_GAME_LOGIN))
                    loginWithCredentials = jsonObj.getBoolean(JSON_GAME_LOGIN);

                if (json.contains(JSON_DEFAULT_CHANNEL))
                    defaultChannel = jsonObj.getString(JSON_DEFAULT_CHANNEL);

                if (json.contains(JSON_OPEN_WITH_FADE))
                    openWithFade = jsonObj.getBoolean(JSON_OPEN_WITH_FADE);

                if (json.contains(JSON_OPEN_WITH_ANIMATION))
                    openAnimationDirection = UnnyNetWebViewTransitionEdge.valueOf(jsonObj.getInt(JSON_OPEN_WITH_ANIMATION));

                if (json.contains(JSON_CLOSE_WITH_FADE))
                    closeWithFade = jsonObj.getBoolean(JSON_CLOSE_WITH_FADE);

                if (json.contains(JSON_CLOSE_WITH_ANIMATION))
                    closeAnimationDirection = UnnyNetWebViewTransitionEdge.valueOf(jsonObj.getInt(JSON_CLOSE_WITH_ANIMATION));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private String getAdditionalPath() {
        String deviceId = allowGuestMode ? Utils.GetUniqId(activity) : null;

        List<String> prms = new ArrayList<>();
        if (deviceId != null)
            prms.add("device_id=" + deviceId);
        prms.add("version=" + UnnyNetPluginVersion);
        if (loginWithCredentials)
            prms.add("game_login=1");
        if(publicKey != null && !publicKey.isEmpty())
            prms.add("public_key=" + publicKey);

        String addPath = "/#/plugin/" + gameId + "?";

        for (int i = 0; i < prms.size() - 1; i++)
            addPath += prms.get(i) + "&";
        addPath += prms.get(prms.size() - 1);

        return addPath;
    }

    public UnnyNetDefault(final Activity activity) {
        this.activity = activity;
        requestsManager = new RequestsManager();
        applyStartURL();
        prepare();
        initScheduler();
        showWebViewDialog(true);
        loadQueue();
        Logger.getInstance().setLevel(2);
    }

    private void initScheduler() {
        exec = new ScheduledThreadPoolExecutor(1);
        queueCheckerTask = exec.scheduleAtFixedRate(new QueueChecker(), 0, TASK_PERIOD, TimeUnit.MILLISECONDS);
    }

    class QueueChecker implements Runnable {

        @Override
        public void run() {
            checkQueue();
        }
    }

    private Activity getActivity() {
        return activity;
    }

    void prepare() {
        final CountDownLatch latch = new CountDownLatch(1);

        activity.runOnUiThread(() -> {
            WebViewDialog.defaultUserAgent = WebSettings.getDefaultUserAgent(activity);
            latch.countDown();
            init();
            load(urlOnStart);
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void init() {
        final String name = dialogName;
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            public void run() {
                WebViewDialog.DialogListener listener = new WebViewDialog.DialogListener() {
                    @Override
                    public void onPageFinished(WebViewDialog dialog, int statusCode, String url) {
                        Logger.getInstance().info("onPageFinished: " + url);
                        WebViewResult res = new WebViewResult(Integer.toString(statusCode), url);
                        sendMessage(new Message(name, Message.Method.PAGE_FINISHED, res));
                    }

                    @Override
                    public void onJSMessage(JSInterface.Type type, String data) {
                        switch (type) {
                            case CLOSE:
                                hide();
                                break;
                            default:
                                onMessageReceived(data);
                        }
                    }

                    @Override
                    public void onPageStarted(WebViewDialog dialog, String url) {
                        Logger.getInstance().info("onPageStarted: " + url);
                        sendMessage(new Message(name, Message.Method.PAGE_STARTED, new WebViewResult(url)));
                    }

                    @Override
                    public void onReceivedError(WebViewDialog dialog, int errorCode, String description, String failingUrl) {
                        Logger.getInstance().error("onPageErrorReceived: " + errorCode + ", " + description + ": " + failingUrl);
                        onPageErrorReceived(failingUrl, errorCode, description);
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebViewDialog dialog, String url) {
                        return dialog.shouldOverride(url, true);
                    }

                    @Override
                    public void onSendMessageReceived(WebViewDialog dialog, String data) {
                        onMessageReceived(data);
//                        UnnyWebViewMessage tt = new UnnyWebViewMessage(data);
//                        Logger.getInstance().debug("hehe: " + tt.Path + " => " + tt.Scheme + ": " +  String.valueOf(tt.Args.containsKey("retry")));
//                        sendMessage(new Message(name, Message.Method.MESSAGE_RECEIVED_LEGACY, new WebViewResult(data)));
                    }

                    @Override
                    public void onDialogClosedByBackButton(WebViewDialog dialog) {
                        Logger.getInstance().info("onDialogClosedByBackButton");
                        sendMessage(new Message(name, Message.Method.WEB_VIEW_DONE, new WebViewResult()));
                    }

                    @Override
                    public void onDialogKeyDown(WebViewDialog dialog, int keyCode) {
                        Logger.getInstance().info("onDialogKeyDown, key: " + keyCode);
                        sendMessage(new Message(name, Message.Method.WEB_VIEW_KEY_DOWN, new WebViewResult(Integer.toString(keyCode))));
                    }

                    @Override
                    public void onDialogClose(WebViewDialog dialog) {
                        Logger.getInstance().info("onDialogClose");
                        WebViewManager.getInstance().removeWebView(name);
                    }

                    @Override
                    public void onAddJavaScriptFinished(WebViewDialog WebViewDialog, WebViewResult value) {
                        sendMessage(new Message(name, Message.Method.ADD_JS_FINISHED, value));
                    }

//                    @Override
//                    public void onJavaScriptFinished(WebViewDialog dialog, WebViewResult result) {
//                        sendMessage(new Message(name, Message.Method.EVAL_JS_FINISHED, result));
//                    }

                    @Override
                    public void onAnimationFinished(WebViewDialog dialog, String identifier) {
                        Logger.getInstance().info("onAnimationFinished, animation id: " + identifier);
                        sendMessage(new Message(name, Message.Method.ANIMATE_TO_FINISHED, new WebViewResult(identifier)));
                    }

                    @Override
                    public void onShowTransitionFinished(WebViewDialog dialog, String identifier) {
                        Logger.getInstance().info("onShowTransitionFinished");
                        sendMessage(new Message(name, Message.Method.SHOW_TRANSITION_FINISHED, new WebViewResult(identifier)));
                    }

                    @Override
                    public void onHideTransitionFinished(WebViewDialog dialog, String identifier) {
                        Logger.getInstance().info("onHideTransitionFinished");
                        sendMessage(new Message(name, Message.Method.HIDE_TRANSITION_FINISHED, new WebViewResult(identifier)));
                    }
                };

                Display display = activity.getWindowManager().getDefaultDisplay();
                Point point = new Point();
                display.getSize(point);

                int screenHeight = point.y;
                int screenWidth = point.x;

                Logger.getInstance().info("Interface init: " + name);
                WebViewDialog dialog = new WebViewDialog(getActivity(), name, listener);
                dialog.setFrame(0, 0, screenWidth, screenHeight);

                WebViewManager.getInstance().setWebView(name, dialog);
            }
        });
    }

//    private void onLegacySendMessageReceived(String name, WebViewResult result) {
//        switch (name) {
//
//        }
//    }

    private String loadErrPage() {
        return Utils.loadContentFromFile(activity, "unnynet.com.error.html");
    }

    private void onPageErrorReceived(String failingUrl, int errorCode, String description) {
        String errPage = loadErrPage();
        if (errPage == null || errPage.isEmpty()) {
            Logger.getInstance().error("No error page");
            return;
        }

        loadHTMLString(errPage, "error");
    }

    private void onExtStoragePermissionResult(int res) {
        SharedPreferences sharedPref = activity.getSharedPreferences(WebViewPrefs, Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(ExtStoragePermissionStatus, res);
        editor.apply();
        setAttachmentsPermissionStatus(res);
    }

    private void onAttachmentPermissionAsked(boolean force) {
        SharedPreferences sharedPref = activity.getSharedPreferences(WebViewPrefs, Activity.MODE_PRIVATE);
        int status = sharedPref.getInt(ExtStoragePermissionStatus, DEF_PERMISSION_STATUS);

        switch (status) {
            case AndroidPermissions.PERMISSION_GRANTED:
                setAttachmentsPermissionStatus(status);
                break;
            case AndroidPermissions.PERMISSION_DENIED:
                AndroidPermissions.requestPermissionAsync(activity, AndroidPermissions.WRITE_EXTERNAL_STORAGE, ((permission, res) -> onExtStoragePermissionResult(res)));
                break;
            case AndroidPermissions.PERMISSION_DENIED_AND_NEVER_AGAIN:
                if (force)
                    AndroidPermissions.requestPermissionAsync(activity, AndroidPermissions.WRITE_EXTERNAL_STORAGE, ((permission, res) -> onExtStoragePermissionResult(res)));
                else
                    setAttachmentsPermissionStatus(status);
                break;
        }
    }

    private void hide() {
        hide(closeWithFade, closeAnimationDirection, 0.4f);
    }

    private void onAction(UnnyWebViewMessage message) {
        if (message.Args.containsKey("exit")) {
            hide();
        }
        if (message.Args.containsKey("askpermission")) {
            onAttachmentPermissionAsked(message.Args.containsKey("force"));
        }
        if (message.Args.containsKey("retry")) {
            load(urlOnStart);
        }
    }

    private void applyRequest(Map<String, String> args, UnnyRequest request) {
        String error = null;
        if (request != null)
            error = request.onRequest();

        String sysId = args.get("sys_id");
        if (error != null)
            sendRequestFailed(sysId, error);
        else
            sendRequestSucceeded(sysId);
    }

    private void sendRequestFailed(String sys_id, String err) {
        String code = String.format(UnnynetCommand.getCommand(UnnynetCommand.Command.RequestFailed), sys_id, err);
        evaluateCodeInJavaScript(new CommandInfo(UnnynetCommand.Command.RequestFailed, code, false), false);
    }

    private void sendRequestSucceeded(String sys_id) {
        String code = String.format(UnnynetCommand.getCommand(UnnynetCommand.Command.RequestSucceeded), sys_id);
        evaluateCodeInJavaScript(new CommandInfo(UnnynetCommand.Command.RequestSucceeded, code, false), false);
    }

    private void onMessageReceived(String rawMessage) {
        Logger.getInstance().debug("onMessageReceived: " + rawMessage);

        UnnyWebViewMessage msg = new UnnyWebViewMessage(rawMessage);

        Logger.getInstance().debug(msg.Path + " => " + msg.Scheme + ", " + msg.Args.size());

        switch (msg.Path) {
            case "action":
                onAction(msg);
                break;
            case "authorised":
                setWebView(true);
                SharedPreferences sharedPref = activity.getSharedPreferences(WebViewPrefs, Activity.MODE_PRIVATE);
                int status = sharedPref.getInt(ExtStoragePermissionStatus, DEF_PERMISSION_STATUS);
                setAttachmentsPermissionStatus(status);
                if (onPlayerAuthorized != null)
                    onPlayerAuthorized.onPlayerAuthorized(msg.Args.get("unny_id"), msg.Args.get("name"));
                setDefaultChannel(defaultChannel);
                break;

            case "renamed":
                if (onPlayerNameChanged != null)
                    onPlayerNameChanged.onPlayerNameChanged(msg.Args.get("name"));
                break;
            case "rank_changed":
                if (onRankChanged != null) {
                    int prevI;
                    int i;

                    try {
                        prevI = Integer.valueOf(msg.Args.get("prev_index"));
                        i = Integer.valueOf(msg.Args.get("curr_index"));
                    } catch (NumberFormatException | NullPointerException ex) {
                        prevI = 0;
                        i = 0;
                    }
                    onRankChanged.onRankChanged(prevI, msg.Args.get("prev_rank"), i, msg.Args.get("curr_rank"));
                }
                break;
            case "new_guild":
                if (onNewGuild != null)
                    onNewGuild.onNewGuild(msg.Args.get("name"), msg.Args.get("description"), msg.Args.get("type"));
                break;
            case "ask_new_guild": {
                applyRequest(msg.Args, () -> onNewGuildRequest.onNewGuildRequest(msg.Args.get("name"), msg.Args.get("description"), msg.Args.get("type")));
                break;
            }
            case "ach_completed":
                if (onAchievementCompleted != null)
                    onAchievementCompleted.onAchievementCompleted(msg.Args.get("ach_id"));
                break;
            case "game_login_request":
                if (onGameLoginRequest != null)
                    onGameLoginRequest.onGameLoginRequest();
                break;
            case "request_reply":
                requestsManager.replyReceived(msg.Args);
                break;
        }
    }

    private static void sendMessage(final Message message) {
        Logger.getInstance().debug("sendMessage:" + message.toString());
//        UnityPlayer.UnitySendMessage("WebViewAndroidStaticListener", "OnMessage", message.toString());
    }

    private void runSafelyOnUiThread(final DialogRunnable r) {
        Activity activity = getActivity();
        if (activity == null)
            return;

        activity.runOnUiThread(() -> {
            try {
                r.run();
            } catch (Exception e) {
                Logger.getInstance().error("WebView should run on UI thread: " + e.getMessage());
            }
        });
    }

    public void onPause() {
        showWebViewDialog(false);

        if (queueCheckerTask != null) {
            queueCheckerTask.cancel(false);
            queueCheckerTask = null;
        }

        if (queue == null || queue.size() == 0)
            return;

        saveQueue();
    }

    private void saveQueue() {
        if (queue == null || queue.size() == 0)
            return;

        JSONArray commands = new JSONArray();
        for (int i = 0; i < queue.size(); ++i) {
            CommandInfo info = queue.get(i);
            if (info.needToSave()) {
                commands.put(info.toJson());
            }
        }

        SharedPreferences sharedPref = activity.getSharedPreferences(WebViewPrefs, Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(CommandQueue, commands.toString());
        editor.apply();

        Logger.getInstance().debug("saveQueue: " + commands.toString());
    }

    private void loadQueue() {
        SharedPreferences sharedPref = activity.getSharedPreferences(WebViewPrefs, Activity.MODE_PRIVATE);
        String commandsStr = sharedPref.getString(CommandQueue, null);
        if (commandsStr == null || commandsStr.isEmpty())
            return;

        Logger.getInstance().debug("loadQueue: " + commandsStr);

        try {
            JSONArray commands = new JSONArray(commandsStr);
            for (int i = 0; i < commands.length(); ++i) {
                JSONObject infoObj = commands.getJSONObject(i);
                CommandInfo info = CommandInfo.fromJson(infoObj);
                if (info != null)
                    queue.add(info);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(CommandQueue);
        editor.apply();
    }

    public void onResume() {
        showWebViewDialog(true);

        if (queueCheckerTask == null) {
            initScheduler();
        }

        loadQueue();
    }

    private void showWebViewDialog(final boolean show) {
        Logger.getInstance().info("Interface showWebViewDialog");
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            void runWith(WebViewDialog dialog) {
                if (show) {
                    dialog.goForeground();
                    dialog.hideSystemUI();
                } else {
                    dialog.goBackground();
                }
            }
        });
    }

    private void hide(final boolean fade, final UnnyNetWebViewTransitionEdge edge, final float duration) {
        Logger.getInstance().info("Interface hide");

        final String identifier = UUID.randomUUID().toString();
        final CountDownLatch latch = new CountDownLatch(1);
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            void runWith(WebViewDialog dialog) {
                boolean res = dialog.setShow(false, fade, edge, duration, identifier);
                latch.countDown();

//                if (callBack != null)
//                    callBack.OnHideStarted(res);
            }
        });
    }

    @Override
    public void show() {
        Logger.getInstance().info("Interface show");

        final String identifier = UUID.randomUUID().toString();
        final CountDownLatch latch = new CountDownLatch(1);
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            public void runWith(WebViewDialog dialog) {
                boolean res = dialog.setShow(true, openWithFade, openAnimationDirection, 0.4f, identifier);
                latch.countDown();
            }
        });
    }

    private void print() {
        Logger.getInstance().info("Interface print");
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            void runWith(WebViewDialog dialog) {
                dialog.print();
            }
        });
    }

    void setDebug(final boolean enabled) {
        Logger.getInstance().info("Interface setDebug: " + enabled);
        runSafelyOnUiThread(new DialogRunnable(null) {
            @Override
            public void run() {
                WebView.setWebContentsDebuggingEnabled(enabled);
            }
        });
    }

    void setImmersiveMode(final boolean enabled) {
        Logger.getInstance().info("Interface setImmersiveModeEnabled");
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            void runWith(WebViewDialog dialog) {
                dialog.setImmersiveMode(enabled);
            }
        });
    }

    void setAlpha(final float alpha) {
        Logger.getInstance().info("Interface setWebViewAlpha: " + alpha);
        WebViewDialog dialog = WebViewManager.getInstance().getWebViewDialog(dialogName);
        if (dialog != null) {
            dialog.setWebViewAlpha(alpha, false);
        }
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            void runWith(WebViewDialog dialog) {
                dialog.setWebViewAlpha(alpha, true);
            }
        });
    }

    float getAlpha() {
        Logger.getInstance().info("Interface getWebViewAlpha");
        WebViewDialog dialog = WebViewManager.getInstance().getWebViewDialog(dialogName);
        return (dialog != null) ? dialog.getWebViewAlpha() : 1.0f;
    }

    void setBackgroundColor(final float r, final float g, final float b, final float a) {
        Logger.getInstance().info(String.format(Locale.US, "setBackgroundColor: {%f, %f, %f, %f}", r, g, b, a));
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            void runWith(WebViewDialog dialog) {
                dialog.setBackgroundColor(r, g, b, a);
            }
        });
    }

    @Override
    public void onDestroy() {
        Logger.getInstance().info("Destroy web view" + dialogName);
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            void runWith(WebViewDialog dialog) {
                dialog.destroy();
                if (queueCheckerTask != null)
                    queueCheckerTask.cancel(false);
            }
        });
    }


    private void load(String originalUrl) {
        if (!originalUrl.contains("unnynet.com") && !originalUrl.contains("http://localhost")) {
            originalUrl = "https://unnynet.com";
        }

        final String url = originalUrl;

        Logger.getInstance().info("Interface load: " + url);
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            public void runWith(WebViewDialog dialog) {
                dialog.load(url);
            }
        });
    }

    private void loadHTMLString(final String html, final String baseUrl) {
        Logger.getInstance().info("Interface loadHTMLString: " + baseUrl);
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            public void runWith(WebViewDialog dialog) {
                dialog.loadHTMLString(html, baseUrl);
            }
        });
    }

    public void reload() {
        Logger.getInstance().info("Interface reload");
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            public void runWith(WebViewDialog dialog) {
                dialog.getWebView().reload();
            }
        });
    }

    public boolean animateTo(final int x, final int y, final int width, final int height,
                             final float duration, final float delay, final String identifier) {
        Logger.getInstance().info(String.format(Locale.US, "Interface animateTo: {%d, %d, %d, %d}", x, y, width, height));

        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = new boolean[1];
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            void runWith(WebViewDialog dialog) {
                result[0] = dialog.animateTo(x, y, width, height, duration, delay, identifier);
            }
        });

        try {
            latch.await(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result[0];
    }

    public void openLeaderboards(OnCompleteListener doneCallback) {
        evaluateCommand(UnnynetCommand.Command.OpenLeaderBoards, true, doneCallback);
    }

    public void openAchievements(OnCompleteListener doneCallback) {
        evaluateCommand(UnnynetCommand.Command.OpenAchievements, true, doneCallback);
    }

    public void openFriends(OnCompleteListener doneCallback) {
        evaluateCommand(UnnynetCommand.Command.OpenFriends, true, doneCallback);
    }

    public void openChannel(String channelName, OnCompleteListener doneCallback) {
        evaluateCodeInJavaScript(new CommandInfo(UnnynetCommand.Command.OpenChannel, String.format(UnnynetCommand.getCommand(UnnynetCommand.Command.OpenChannel), channelName), true, doneCallback), false);
    }

    public void openGuilds(OnCompleteListener doneCallback) {
        evaluateCommand(UnnynetCommand.Command.OpenGuilds, true, doneCallback);
    }

    public void openMyGuild(OnCompleteListener doneCallback) {
        evaluateCommand(UnnynetCommand.Command.OpenMyGuild, true, doneCallback);
    }

    public void sendMessageToChannel(String channelName, String message, OnCompleteListener doneCallback) {
        if (message != null && !message.isEmpty()) {
            String code = String.format(UnnynetCommand.getCommand(UnnynetCommand.Command.SendMessage), channelName, message);
            evaluateCodeInJavaScript(new CommandInfo(UnnynetCommand.Command.SendMessage, code, false, doneCallback), false);
        }
    }

    public void authorizeWithCredentials(String login, String password, String displayName, OnCompleteListener doneCallback) {
        String code = String.format(UnnynetCommand.getCommand(UnnynetCommand.Command.AuthorizeWithCredentials), login, password, displayName);
        evaluateCodeInJavaScript(new CommandInfo(UnnynetCommand.Command.AuthorizeWithCredentials, code, false, doneCallback), true);
    }

    public void authorizeAsGuest(String displayName, OnCompleteListener doneCallback) {
        String code = String.format(UnnynetCommand.getCommand(UnnynetCommand.Command.AuthorizeAsGuest), displayName);
        evaluateCodeInJavaScript(new CommandInfo(UnnynetCommand.Command.AuthorizeWithCredentials, code, false, doneCallback), true);
    }

    public void forceLogout(OnCompleteListener doneCallback) {
        evaluateCommand(UnnynetCommand.Command.ForceLogout, false, doneCallback);
    }

    public void getGuildInfo(boolean fullInfo, OnRequestCompleteListener doneCallback) {
        String code = String.format(UnnynetCommand.getCommand(UnnynetCommand.Command.GetGuildInfo), fullInfo ? 1 : 0);
        CommandInfo cmd = new CommandInfo(UnnynetCommand.Command.GetGuildInfo, code, doneCallback);
        requestsManager.addRequest(cmd);

        evaluateCodeInJavaScript(cmd, false);
    }

    public void reportLeaderboards(String leaderboardsName, int newScore, OnCompleteListener doneCallback) {
        String code = String.format(UnnynetCommand.getCommand(UnnynetCommand.Command.ReportLeaderboardScores), leaderboardsName, newScore);
        evaluateCodeInJavaScript(new CommandInfo(UnnynetCommand.Command.ReportLeaderboardScores, code, false, doneCallback), false);
    }

    public void reportAchievements(int achId, int progress, OnCompleteListener doneCallback) {
        String code = String.format(UnnynetCommand.getCommand(UnnynetCommand.Command.ReportAchievementProgress), achId, progress);
        evaluateCodeInJavaScript(new CommandInfo(UnnynetCommand.Command.ReportAchievementProgress, code, false, doneCallback), false);
    }

    private void setDefaultChannel(String channelName) {
        String code = String.format(UnnynetCommand.getCommand(UnnynetCommand.Command.SetDefaultChannel), channelName);
        evaluateCodeInJavaScript(new CommandInfo(UnnynetCommand.Command.SetDefaultChannel, code, false), false);
    }

    private void setAttachmentsPermissionStatus(int status) {
        setConfig("{\"attachmentsPermissionStatus\": " + status + "}");
    }

    public void addGuildExperience(int experience, OnCompleteListener doneCallback) {
        String code = String.format(UnnynetCommand.getCommand(UnnynetCommand.Command.AddGuildExperience), experience);
        evaluateCodeInJavaScript(new CommandInfo(UnnynetCommand.Command.AddGuildExperience, code, false, doneCallback), false);
    }

    private void evaluateCommand(UnnynetCommand.Command command, boolean openWindow, OnCompleteListener doneCallback) {
        evaluateCodeInJavaScript(new CommandInfo(command, UnnynetCommand.getCommand(command), openWindow, doneCallback), false);
    }

    private void setWebView(boolean webView) {
        setConfig("{\"webView\": " + (webView ? "true" : "false") + "}");
    }

    private void setConfig(String config) {
        String code = String.format(UnnynetCommand.getCommand(UnnynetCommand.Command.SetConfig), config);
        evaluateCodeInJavaScript(new CommandInfo(UnnynetCommand.Command.SetConfig, code, false), false);
    }

    private void evaluateCodeInJavaScript(CommandInfo info, boolean highPriority) {
        int indexToReplace = -1;
        for (int i = 1; i < queue.size(); i++) {
            if (queue.get(i).couldBeReplaced(info.getCommand())) {
                indexToReplace = i;
                break;
            }
        }

        if (indexToReplace == -1) {
            if (highPriority)
                queue.add(0, info);
            else
                queue.add(info);
        } else
            queue.set(indexToReplace, info);
    }

    private static ResponseData sendCallback(WebViewResult payload, UnnyNet.OnCompleteListener doneCallback) {
        if (!payload.resultCode.equals("0")) {
            if (doneCallback != null)
                doneCallback.onCompleted(new ResponseData(false, new Error(Error.Errors.Unknown, "Error occurred: " + payload.resultCode)));

            return new ResponseData(false, Error.getUnnyNetNotReadyError());
        } else {
            if (!payload.data.equals("0")) {
                try {
                    ResponseData data = new ResponseData(payload.data);

                    if (!data.isSuccess()) {
                        if (doneCallback != null)
                            doneCallback.onCompleted(data);
//                        switch (ResponseData.Errors.valueOf(data.getError().getCode())) {
//                            case UnnynetNotReady:
//                                return data;
//                        }
                        return data;
                    } else {
                        if (doneCallback != null)
                            doneCallback.onCompleted(null);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();

                    if (doneCallback != null)
                        doneCallback.onCompleted(new ResponseData(false, new Error(Error.Errors.Unknown, payload.data)));
                }
            } else {
                if (doneCallback != null)
                    doneCallback.onCompleted(null);
            }
        }

        return null;
    }

    private void checkQueue() {
//        Logger.getInstance().debug("checkQueue: " + queue.size());
        if (queue.size() == 0)
            return;

        CommandInfo info = queue.get(0);

        long curTime = System.currentTimeMillis();
        if (info.getStartedTime() == 0) {
            info.setStartedTime(curTime);
        } else {
            double secs = curTime - info.getStartedTime();
            if (secs > SEND_REQUEST_RETRY_DELAY) {
                info.setStartedTime(curTime);
            } else {
                return;
            }
        }
        long original = info.getStartedTime();

        evaluateJavaScript(info.getCode(), UUID.randomUUID().toString(), (payload) -> {
            if (info.getCallbackNative() != null) {
                info.getCallbackNative().onCompleted(payload);
            }
            if (original == info.getStartedTime()) {
                ResponseData resp = sendCallback(payload, info.getCallback());
                if (resp == null || resp.isSuccess()) {
                    queue.remove(0);
                    if (info.isOpenWindow())
                        show();
                } else {
                    switch (resp.getError().getCode()) {
                        case UnnynetNotReady:
                            Logger.getInstance().debug("Not ready, w8");
                            break;
                        case NotAuthorized:
                            Logger.getInstance().debug("Not authorized, w8");
                            break;
                        default:
                            queue.remove(0);
//                            if (info.isOpenWindow())
//                                show();
                            break;
                    }
                }
            }
        });
    }

    private void evaluateJavaScript(final String jsString, final String identifier, UnnyNet.OnWebViewResultListener callBack) {
        Logger.getInstance().info("Interface evaluateJavaScript");
        runSafelyOnUiThread(new DialogRunnable(dialogName) {
            @Override
            void runWith(WebViewDialog dialog) {
                dialog.evaluateJavaScript(jsString, identifier, callBack);
            }
        });
    }

    private static class DialogRunnable implements Runnable {
        private String name;

        DialogRunnable(String name) {
            this.name = name;
        }

        void runWith(WebViewDialog dialog) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void run() {
            WebViewDialog dialog = WebViewManager.getInstance().getWebViewDialog(name);
            if (dialog != null) {
                runWith(dialog);
            } else {
                Logger.getInstance().error("Did not find the correct web view dialog for name: " + name);
            }
        }
    }

    @Override
    public UnnyNet setOnPlayerAuthorizedListener(OnPlayerAuthorizedListener callBack) {
        onPlayerAuthorized = callBack;

        return this;
    }


    @Override
    public UnnyNet setOnNewGuildRequestListener(OnNewGuildRequestListener callBack) {
        onNewGuildRequest = callBack;

        return this;
    }

    @Override
    public UnnyNet setOnNewGuildListener(OnNewGuildListener callBack) {
        onNewGuild = callBack;

        return this;
    }

    @Override
    public UnnyNet setOnGameLoginRequestListener(OnGameLoginRequestListener callBack) {
        onGameLoginRequest = callBack;

        return this;
    }

    @Override
    public UnnyNet setOnRankChangedListener(OnRankChangedListener callBack) {
        onRankChanged = callBack;

        return this;
    }

    @Override
    public UnnyNet setOnAchievementCompletedListener(OnAchievementCompletedListener callBack) {
        onAchievementCompleted = callBack;

        return this;
    }

    @Override
    public UnnyNet setOnPlayerNameChangedListener(OnPlayerNameChangedListener callBack) {
        onPlayerNameChanged = callBack;

        return this;
    }

//    public interface OnShowStartedListener {
//        void OnShowStarted(boolean started);
//    }

    public interface OnHideStartedListener {
        void OnHideStarted(boolean started);
    }
}