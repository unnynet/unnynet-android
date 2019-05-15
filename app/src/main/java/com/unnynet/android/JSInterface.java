package com.unnynet.android;


import android.webkit.JavascriptInterface;

public class JSInterface {

    public interface OnJSMessage {
        void onJSMessage(JSInterface.Type type, String data);
    }

    public enum Type {
        NONE(0),
        CLOSE(1);

        private final int fId;

        Type(int id) {
            this.fId = id;
        }

        public int value() {
            return fId;
        }

//        public static Type forInt(int id) {
//            for (Type type : values()) {
//                if (type.fId == id) {
//                    return type;
//                }
//            }
//            throw new IllegalArgumentException("Invalid Method id: " + id);
//        }
    }

    private final OnJSMessage listener;

    JSInterface(OnJSMessage listener) {
        this.listener = listener;
    }

    @JavascriptInterface
    public void sendUnityMessage(String data) {
        if (listener == null) {
            Logger.getInstance().error("On message: no listener");

            return;
        }

        listener.onJSMessage(Type.NONE, data);
    }
}
