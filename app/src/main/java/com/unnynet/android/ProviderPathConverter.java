package com.unnynet.android;

import android.content.Context;
import android.net.Uri;

import com.unnynet.android.helper.FileUtils;

class ProviderPathConverter {
    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     * <p>
     * https://stackoverflow.com/questions/20067508/get-real-path-from-uri-android-kitkat-new-storage-access-framework/20559175#20559175
     *
     * @param context The context.
     * @param uri     The Uri to query.
     * @author paulburke
     */
    static String getPath(final Context context, final Uri uri) {
        return FileUtils.getPath(context, uri);
    }
}
