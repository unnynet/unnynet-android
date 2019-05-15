package com.unnynet.android;

import android.support.annotation.NonNull;

import org.json.JSONObject;
import java.util.HashMap;

public class Message {
    public enum Method {
        PAGE_FINISHED(1),
        PAGE_STARTED(2),
        PAGE_ERROR_RECEIVED(3),
        MESSAGE_RECEIVED_LEGACY(4),
        WEB_VIEW_DONE(5),
        WEB_VIEW_KEY_DOWN(6),
        ADD_JS_FINISHED(7),
        EVAL_JS_FINISHED(8),
        ANIMATE_TO_FINISHED(9),
        SHOW_TRANSITION_FINISHED(10),
        HIDE_TRANSITION_FINISHED(11),
        MESSAGE_RECEIVED(12);

        private final int fId;

        private Method(int id) {
            this.fId = id;
        }

        public int value() {
            return fId;
        }

        public static Method forInt(int id) {
            for (Method day : values()) {
                if (day.fId == id) {
                    return day;
                }
            }
            throw new IllegalArgumentException("Invalid Method id: " + id);
        }
    }

    private WebViewResult result;
    private int method;
    private String name;

    Message(String name, Method method, WebViewResult result) {
        this.name = name;
        this.method = method.value();
        this.result = result;
    }

    @NonNull
    public String toString() {
        HashMap<String, String> dic = new HashMap<>();
        dic.put("method", String.valueOf(method));
        dic.put("name", name);
        dic.put("data", result.toString());
        JSONObject obj = new JSONObject(dic);
        return obj.toString();
    }
}
