package com.unnynet.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.net.URISyntaxException;
import java.util.Locale;

class URLLoadingResponser {
    private final Context context;
    private final WebViewDialog dialog;
    private final Logger logger;
    private String url;

    private String getLowerUrl() {
        return url.toLowerCase(Locale.ROOT);
    }

    URLLoadingResponser(Context context, WebViewDialog dialog, String url) {
        this.context = context;
        this.dialog = dialog;
        this.url = url;
        this.logger = Logger.getInstance();
    }

    private Intent smsIntent() {
        if (!getLowerUrl().startsWith("sms:")) {
            return null;
        }
        logger.debug("Received sms url...");
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            return intent;
        } catch (Exception e) {
            logger.error("sms url intent open exception: " + e.getMessage());
            return null;
        }
    }

    private Intent telIntent() {
        if (!getLowerUrl().startsWith("tel:")) {
            return null;
        }
        logger.debug("Received tel url...");
        return new Intent(Intent.ACTION_DIAL, Uri.parse(url));
    }

    private Intent mailToIntent() {
        if (!getLowerUrl().startsWith("mailto:")) {
            return null;
        }
        logger.debug("Received mailto url...");
        return new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
    }

    private Intent explicitlyIntent() {
        if (!getLowerUrl().startsWith("intent:")) {
            return null;
        }
        logger.debug("Received intent url...");
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            ResolveInfo info = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (info != null) {
                return intent;
            } else {
                String intentId = intent.getPackage();
                if (intentId == null) {
                    return null;
                }
                Intent marketIntent = new Intent(Intent.ACTION_VIEW);
                marketIntent.setData(Uri.parse("market://details?id=" + intentId));
                return marketIntent;
            }
        } catch (Exception e) {
            logger.error("Parsing intent url error. " + e.getMessage());
            return null;
        }
    }

    private Intent marketIntent() {
        if (!getLowerUrl().startsWith("market:")) {
            return null;
        }
        logger.debug("Received market url...");
        try {
            return Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            logger.error("Parsing market url error. " + e.getMessage());
            return null;
        }
    }

    private boolean tryToRunIntent(Intent intent) {
        if (intent != null) {
            if (context != null) {
                try {
                    context.startActivity(intent);
                } catch (Exception e) {
                    logger.error("No Activity found to handle Intent: " + intent.getData());
                }
            }
            return true;
        }
        return false;
    }

    boolean handleWithIntent() {

        logger.verbose("Checking url could be handled with any intents: " + url);
        return  tryToRunIntent(mailToIntent()) ||
                tryToRunIntent(telIntent()) ||
                tryToRunIntent(smsIntent()) ||
                tryToRunIntent(explicitlyIntent()) ||
                tryToRunIntent(marketIntent());
    }

    boolean canResponseDefinedScheme() {
        logger.verbose("Checking url could match with a defined url scheme: " + url);
        for (String scheme: dialog.getSchemes()) {
            if (url.startsWith(scheme + "://")) {
                logger.verbose("Found url match scheme: " + url);
                return true;
            }
        }

        logger.verbose("Did not find a matched scheme for: " + url);
        return false;
    }
}
