package com.unnynet.android;

import android.support.annotation.NonNull;

import org.json.JSONObject;

import java.util.HashMap;


class WebViewResult {
    String id;
    String resultCode;
    String error;
    String data;

//    WebViewResult(JSONObject obj) {
//        try {
//            id = obj.getString("id");
//            resultCode = obj.getString("code");
//            data = obj.getString("data");
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//    }

    WebViewResult() {
    }

    public static WebViewResult error(String code, String msg) {
        WebViewResult res = new WebViewResult();
        res.error = msg;
        res.resultCode = code;

        return res;
    }

    WebViewResult(String id, String resultCode, String data) {
        this.id = id;
        this.resultCode = resultCode;
        this.data = data;
    }

    WebViewResult(String resultCode, String data) {
        this.resultCode = resultCode;
        this.data = data;
    }

    WebViewResult(String data) {
        this.data = data;
    }

    @NonNull
    public String toString() {
        HashMap<String, String> dic = new HashMap<>();

        if (id != null && !id.equals(""))
            dic.put("identifier", id);
        else
            dic.put("identifier", "");

        if (resultCode != null && !resultCode.equals(""))
            dic.put("resultCode", resultCode);

        if (data != null)
            dic.put("data", data);

        JSONObject obj = new JSONObject(dic);
        return obj.toString().replace("\\\\", "");
    }
}
