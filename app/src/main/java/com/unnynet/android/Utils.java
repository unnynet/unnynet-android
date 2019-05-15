package com.unnynet.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.provider.Settings;

import java.io.IOException;
import java.io.InputStream;

final class Utils {
    @SuppressLint("HardwareIds")
    static String GetUniqId(Context ctx) {
        return Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private static InputStream loadInputStreamFromAssetFile(Context context, String fileName) {
        AssetManager am = context.getAssets();
        try {
            return am.open(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    static String loadContentFromFile(Context context, String path) {
        String content;
        try {
            InputStream is = loadInputStreamFromAssetFile(context, path);
            if(is == null)
                return null;

            int size = is.available();
            byte[] buffer = new byte[size];
            //noinspection ResultOfMethodCallIgnored
            is.read(buffer);
            is.close();
            content = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return content;
    }

}
