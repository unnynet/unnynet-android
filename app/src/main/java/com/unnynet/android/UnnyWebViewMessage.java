package com.unnynet.android;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

class UnnyWebViewMessage {

//    String RawMessage;
    String Scheme;
    String Path;
    Map<String, String> Args;

    UnnyWebViewMessage(String rawMessage) {
        String[] schemeSplit = rawMessage.split("://");
        int index;
        if (schemeSplit.length == 1) {
            index = 0;
            this.Scheme = "unnynet";
        } else {
            this.Scheme = schemeSplit[0];
            index = 1;
        }

        String pathAndArgsString = "";

        while (index < schemeSplit.length) {
            pathAndArgsString = pathAndArgsString.concat(schemeSplit[index]);
            index++;
        }

        String[] split = pathAndArgsString.split("\\?");

        try {
            this.Path = URLDecoder.decode(split[0], "UTF-8");

            this.Args = new HashMap<>();
            if (split.length > 1) {
                for (String pair : split[1].split("&")) {
                    String[] elems = pair.split("=");
                    if (elems.length > 1) {
                        String key = URLDecoder.decode(elems[0], "UTF-8");
                        if (Args.containsKey(key)) {
                            String existingValue = Args.get(key);
                            Args.put(key, existingValue + "," + URLDecoder.decode(elems[1], "UTF-8"));
                        } else {
                            Args.put(key, URLDecoder.decode(elems[1], "UTF-8"));
                        }
                    }
                }
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}
