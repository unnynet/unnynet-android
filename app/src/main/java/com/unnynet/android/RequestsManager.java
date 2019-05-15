package com.unnynet.android;

import android.util.SparseArray;

import java.util.Map;

class RequestsManager {
    private int uniqueIds;
    private SparseArray<CommandInfo> allRequests = new SparseArray<>();

    private int requestId() {
        return uniqueIds++;
    }

    void addRequest(CommandInfo cmd) {
        int id = requestId();
        allRequests.put(id, cmd);

        String code = cmd.getCode().replace("<*id*>", "%s");
        cmd.code = String.format(code, id);
    }

    void replyReceived(Map<String, String> reply) {
        if (!reply.containsKey("id"))
            return;

        String idString = reply.get("id");
        if (idString == null)
            return;

        int id = Integer.valueOf(idString);
        CommandInfo info = allRequests.get(id, null);
        if (info == null)
            return;
        allRequests.delete(id);
        info.delayedRequestCallback.onCompleted(reply.get("data"));
    }
}
