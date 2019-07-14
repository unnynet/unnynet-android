package com.unnynet.android;

import org.json.JSONException;
import org.json.JSONObject;

public class CommandInfo {

    private boolean openWindow;
    String code;
    private UnnynetCommand.Command command;
    private UnnyNet.OnCompleteListener callback;
    UnnyNet.OnRequestCompleteListener delayedRequestCallback;
    private UnnyNet.OnWebViewResultListener callbackNative;
    private long startedTime;
//    int retries = 0;

    boolean isOpenWindow() {
        return openWindow;
    }

    String getCode() {
        return code;
    }

    void setCode(String code) {
        this.code = code;
    }

    UnnynetCommand.Command getCommand() {
        return command;
    }

    UnnyNet.OnCompleteListener getCallback() {
        return callback;
    }

    UnnyNet.OnWebViewResultListener getCallbackNative() {
        return callbackNative;
    }

    long getStartedTime() {
        return startedTime;
    }

    void setStartedTime(long date) {
        startedTime = date;
    }

//    int getRetries() {
//        return retries;
//    }

    CommandInfo(UnnynetCommand.Command cmd, String code, boolean openWindow) {
        this.command = cmd;
        this.code = code;
        this.callback = null;
        this.openWindow = openWindow;
        this.startedTime = 0;
    }

    CommandInfo(UnnynetCommand.Command cmd, String code, boolean openWindow, UnnyNet.OnCompleteListener callback) {
        this(cmd, code, openWindow);

        this.callback = callback;
    }

    public CommandInfo(UnnynetCommand.Command cmd, String code, boolean openWindow, UnnyNet.OnWebViewResultListener callback) {
        this(cmd, code, openWindow);

        this.callbackNative = callback;
    }

    //Delayed replies
    CommandInfo(UnnynetCommand.Command cmd, String code, UnnyNet.OnRequestCompleteListener doneCallback) {
        this(cmd, code, false);

        this.delayedRequestCallback = doneCallback;
    }

    boolean needToSave() {
        switch (getCommand()) {
            case SendMessage:
            case ReportLeaderboardScores:
            case ReportAchievementProgress:
            case AddGuildExperience:
                return true;
            default:
                return false;
        }
    }

    boolean couldBeReplaced(UnnynetCommand.Command cmd) {
        switch (cmd) {
            case OpenLeaderBoards:
            case OpenAchievements:
            case OpenFriends:
            case OpenChannel:
            case OpenGuilds:
            case OpenMyGuild:
                return getCommand() == UnnynetCommand.Command.OpenLeaderBoards || getCommand() == UnnynetCommand.Command.OpenAchievements || getCommand() == UnnynetCommand.Command.OpenFriends || getCommand() == UnnynetCommand.Command.OpenChannel || getCommand() == UnnynetCommand.Command.OpenGuilds || getCommand() == UnnynetCommand.Command.OpenMyGuild;
            case AuthorizeWithCredentials:
            case ForceLogout:
            case GetGuildInfo:
            case AuthorizeAsGuest:
                return getCommand() == cmd;
            default:
                return false;
        }
    }

    JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("code", code);
            obj.put("openWindow", openWindow);
            obj.put("command", command.getValue());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return obj;
    }

    static CommandInfo fromJson(JSONObject obj) {
        try {
            String code = obj.getString("code");
            boolean openWindow = obj.getBoolean("openWindow");
            UnnynetCommand.Command command = UnnynetCommand.Command.valueOf(obj.getInt("command"));

            return new CommandInfo(command, code, openWindow);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

//    boolean sameType(UnnynetCommand.Command cmd) {
//        switch (cmd) {
//            case OpenLeaderBoards:
//            case OpenAchievements:
//            case OpenFriends:
//            case OpenChannel:
//            case OpenGuilds:
//            case OpenMyGuild:
//                return getCommand() == UnnynetCommand.Command.OpenLeaderBoards || getCommand() == UnnynetCommand.Command.OpenAchievements || getCommand() == UnnynetCommand.Command.OpenFriends || getCommand() == UnnynetCommand.Command.OpenChannel || getCommand() == UnnynetCommand.Command.OpenGuilds || getCommand() == UnnynetCommand.Command.OpenMyGuild;
//            default:
//                return getCommand() == cmd;
//        }
//    }
}
