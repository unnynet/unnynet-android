package com.unnynet.android;


import android.util.SparseArray;

public interface UnnyNet {
    void onDestroy();

    /***
     * Sets UnnyNet visible on screen.
     */
    void show();

    void onPause();

    void onResume();

    UnnyNet setOnPlayerAuthorizedListener(OnPlayerAuthorizedListener callBack);

    UnnyNet setOnGameLoginRequestListener(OnGameLoginRequestListener callBack);

    UnnyNet setOnNewGuildRequestListener(OnNewGuildRequestListener callBack);

    UnnyNet setOnNewGuildListener(OnNewGuildListener callBack);

    UnnyNet setOnRankChangedListener(OnRankChangedListener callBack);

    UnnyNet setOnAchievementCompletedListener(OnAchievementCompletedListener callBack);

    UnnyNet setOnPlayerNameChangedListener(OnPlayerNameChangedListener callBack);

    void openLeaderboards(OnCompleteListener doneCallback);

    void setCloseButtonVisible(final boolean visible);

    void openAchievements(OnCompleteListener doneCallback);

    void openFriends(OnCompleteListener doneCallback);

    void openChannel(String channelName, OnCompleteListener doneCallback);

    void openGuilds(OnCompleteListener doneCallback);

    void openMyGuild(OnCompleteListener doneCallback);

    void sendMessageToChannel(String channelName, String message, OnCompleteListener doneCallback);

    void reportLeaderboards(String leaderboardsName, int newScore, OnCompleteListener doneCallback);

    void reportAchievements(int achId, int progress, OnCompleteListener doneCallback);

    void addGuildExperience(int experience, OnCompleteListener doneCallback);

    void authorizeWithCredentials(String login, String password, String displayName, OnCompleteListener doneCallback);

    void authorizeAsGuest(String displayName, OnCompleteListener doneCallback);

    void forceLogout(OnCompleteListener doneCallback);

    void getGuildInfo(boolean fullInfo, OnRequestCompleteListener doneCallback);

    interface OnAchievementCompletedListener {
        void onAchievementCompleted(String achId);
    }

    interface OnPlayerNameChangedListener {
        void onPlayerNameChanged(String newName);
    }

    interface OnRankChangedListener {
        void onRankChanged(int index, String rank, int prevIndex, String prevRank);
    }

    interface UnnyRequest {
        String onRequest();
    }

    interface OnNewGuildRequestListener {
        String onNewGuildRequest(String name, String description, String guildType);
    }

    interface OnNewGuildListener {
        void onNewGuild(String name, String description, String guildId);
    }

    interface OnPlayerAuthorizedListener {
        void onPlayerAuthorized(String unnyId, String name);
    }

    interface OnGameLoginRequestListener {
        void onGameLoginRequest();
    }

    interface OnCompleteListener {
        void onCompleted(ResponseData error);
    }

    interface OnRequestCompleteListener {
        void onCompleted(String data);
    }

    interface OnWebViewResultListener {
        void onCompleted(WebViewResult result);
    }

    /***
     * An enum to identify transition edge from or to when the UniWebView
     * transition happens. You can specify an edge in Show() or Hide() methods of web view.
     */
    enum UnnyNetWebViewTransitionEdge {
        /***
         * No transition when showing or hiding.
         */
        None(0),
        /***
         * Transit the web view from/to top.
         */
        Top(1),
        /***
         * Transit the web view from/to left.
         */
        Left(2),
        /***
         * Transit the web view from/to bottom.
         */
        Bottom(3),
        /***
         * Transit the web view from/to right.
         */
        Right(4);

        private int value;
        private static SparseArray<UnnyNetWebViewTransitionEdge> map = new SparseArray<>();

        UnnyNetWebViewTransitionEdge(int value) {
            this.value = value;
        }

        static {
            for (UnnyNetWebViewTransitionEdge pageType : UnnyNetWebViewTransitionEdge.values()) {
                map.put(pageType.value, pageType);
            }
        }

        public static UnnyNetWebViewTransitionEdge valueOf(int err) {
            return map.get(err, None);
        }

        public int getValue() {
            return value;
        }
    }
}
