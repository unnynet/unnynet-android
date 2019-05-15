package com.unnynet.android;

import android.util.SparseArray;

public class Error {
    public enum Errors {
        Unknown(1),
        NotAuthorized(2),
        NoMessage(3),
        NoChannel(4),
        UnnynetNotReady(5),
        NoGameId(6),
        NoSuchLeaderboard(7),
        NoLeaderboardsForTheGame(8),
        NoAchievementsForTheGame(9),
        NoGuildsForTheGame(10),
        NotInGuild(11),
        NoSuchAchievement(12),
        WrongAchievementType(13);

        int value;
        static SparseArray<Errors> map = new SparseArray<>();

        Errors(int value) {
            this.value = value;
        }

        static {
            for (Errors pageType : Errors.values()) {
                map.put(pageType.value, pageType);
            }
        }

        public static Errors valueOf(int err) {
            return map.get(err, Unknown);
        }

        public int getValue() {
            return value;
        }
    }

    private Errors code;
    private String message;

    Error(Errors code) {
        this.code = code;
    }

    Error(Errors code, String message) {
        this.code = code;
        this.message = message;
    }

    public Errors getCode() {
        return code;
    }

    public void setCode(Errors code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    static Error getUnnyNetNotReadyError() {
        return new Error(Errors.UnnynetNotReady);
    }
}
