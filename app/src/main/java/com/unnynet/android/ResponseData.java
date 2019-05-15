package com.unnynet.android;

import org.json.JSONException;
import org.json.JSONObject;


public class ResponseData {
    private boolean success;
    private Error error;

    ResponseData(String data) {
        if (data == null || data.isEmpty()) {
            success = false;
            error = new Error(Error.Errors.UnnynetNotReady);
        } else {
            try {
                JSONObject jsonObject = new JSONObject(data.replace("\\",""));
                success = jsonObject.getBoolean("success");
                if(jsonObject.has("error")) {
                    JSONObject err = jsonObject.getJSONObject("error");
                    int code = err.getInt("code");
                    String msg = err.getString("message");
                    error = new Error(Error.Errors.valueOf(code), msg);
                }
            } catch (JSONException e) {
                e.printStackTrace();

                success = false;
                error = new Error(Error.Errors.Unknown);
            }
        }
    }

    ResponseData(boolean success, Error err) {
        this.success = success;
        this.error = err;
    }

    public Error getError() {
        return error;
    }

    void setError(Error err) {
        this.error = err;
    }

    public boolean isSuccess() {
        return success;
    }

//    void setSuccess(boolean s) {
//        this.success = s;
//    }
}
