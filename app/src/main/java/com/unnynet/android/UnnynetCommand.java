package com.unnynet.android;

import android.util.SparseArray;

import java.util.HashMap;
import java.util.Map;

class UnnynetCommand {
    enum Command {
        None(0),
        OpenLeaderBoards(1),
        OpenAchievements(2),
        OpenFriends(3),
        OpenChannel(4),
        OpenGuilds(5),
        OpenMyGuild(6),
        SendMessage(20),
        ReportLeaderboardScores(40),
        ReportAchievementProgress(41),
        AddGuildExperience(60),
        RequestFailed(70),
        RequestSucceeded(71),
        SetKeyboardOffset(80),
        SetConfig(81),
        SetDefaultChannel(82),
        AuthorizeWithCredentials(100),
        AuthorizeAsGuest(101),
        ForceLogout(110),
        GetGuildInfo(120);

        private int value;
        private static SparseArray<Command> map = new SparseArray<>();

        Command(int value) {
            this.value = value;
        }

        static {
            for (Command pageType : Command.values()) {
                map.put(pageType.value, pageType);
            }
        }

        public static Command valueOf(int pageType) {
            return map.get(pageType);
        }

        public int getValue() {
            return value;
        }
    }

    private static final Map<Integer, String> commands = new HashMap<Integer, String>() {{
        put(Command.OpenLeaderBoards.value, "window.globalReactFunctions.apiOpenLeaderboards()");
        put(Command.OpenAchievements.value, "window.globalReactFunctions.apiOpenAchievements()");
        put(Command.OpenFriends.value, "window.globalReactFunctions.apiOpenFriends()");
        put(Command.OpenChannel.value, "window.globalReactFunctions.apiOpenChannel('%s')");
        put(Command.OpenGuilds.value, "window.globalReactFunctions.apiOpenGuilds()");
        put(Command.OpenMyGuild.value, "window.globalReactFunctions.apiOpenMyGuild()");
        put(Command.SendMessage.value, "window.globalReactFunctions.apiSendMessage('%s', '%s')");
        put(Command.ReportLeaderboardScores.value, "window.globalReactFunctions.apiReportLeaderboardScores('%s', '%s')");
        put(Command.ReportAchievementProgress.value, "window.globalReactFunctions.apiReportAchievementProgress(%s, %s)");
        put(Command.AddGuildExperience.value, "window.globalReactFunctions.apiAddGuildExperience('%s')");
        put(Command.RequestFailed.value, "window.globalReactFunctions.requestFailed('%s', \"%s\")");
        put(Command.RequestSucceeded.value, "window.globalReactFunctions.requestSucceeded('%s')");
        put(Command.SetKeyboardOffset.value, "window.globalReactFunctions.apiSetKeyboardOffset('%s')");
        put(Command.SetConfig.value, "window.globalReactFunctions.apiSetConfig('%s')");
        put(Command.SetDefaultChannel.value, "window.globalReactFunctions.apiSetDefaultChannel('%s')");
        put(Command.AuthorizeWithCredentials.value, "window.globalReactFunctions.apiAuthWithCredentials('%s', '%s', '%s')");
        put(Command.AuthorizeAsGuest.value, "window.globalReactFunctions.apiAuthAsGuest('%s')");
        put(Command.ForceLogout.value, "window.globalReactFunctions.apiForceLogout()");
        put(Command.GetGuildInfo.value, "window.globalReactFunctions.apiGetGuildInfo(<*id*>, %s)");
    }};

    static String getCommand(Command cmd) {
        String val = commands.get(cmd.value);

        if (val == null)
            throw new IllegalArgumentException();

        return val;
    }
}
