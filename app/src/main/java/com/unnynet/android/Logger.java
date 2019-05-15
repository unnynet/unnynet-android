package com.unnynet.android;

import android.util.Log;

final class Logger {
    private static final int OFF =  0x00000000;
    private static final int VERBOSE = 0x00000002;
    private static final int DEBUG = 0x00000003;
    private static final int INFO = 0x00000004;
    private static final int WARN = 0x00000005;
    private static final int ERROR = 0x00000006;

    private static Logger instance = null;

    private String tag;
    private int level;

    Logger(String tag, int level) {
        this.tag = tag;
        this.level = level;
    }

    static Logger getInstance() {
        if (instance == null) {
            instance = new Logger("UnnyWebView", VERBOSE);
        }
        return instance;
    }

    void verbose(String message) {
        log(VERBOSE, message);
    }

    void debug(String message) {
        log(DEBUG, message);
    }

    void info(String message) {
        log(INFO, message);
    }

    void error(String message) {
        log(ERROR, message);
    }

    private void log(int level, String message) {
        if (level >= this.getLevel() && this.getLevel() != OFF) {
            switch (level) {
                case ERROR:
                    Log.e(tag, "<UnnyWebView-Android> " + message);
                    break;
                case DEBUG:
                    Log.d(tag, "<UnnyWebView-Android> " + message);
                    break;
                case INFO:
                    Log.i(tag, "<UnnyWebView-Android> " + message);
                    break;
                case VERBOSE:
                    Log.v(tag, "<UnnyWebView-Android> " + message);
                    break;
                case WARN:
                    Log.w(tag, "<UnnyWebView-Android> " + message);
                    break;
            }
        }
    }

    private int getLevel() {
        return level;
    }

    void setLevel(int level) {
        this.level = level;
        log(level, "Setting logging level to " + level);
    }

    public void disable() {
        this.level = OFF;
        log(DEBUG, "Setting logging level to " + OFF);
    }
}
