package com.unnynet.android.helper;


import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.PermissionChecker;

public class AndroidPermissions {
    public static final int PERMISSION_GRANTED = 0;
    public static final int PERMISSION_DENIED = -(1 << 1);
    public static final int PERMISSION_DENIED_AND_NEVER_AGAIN = -(1 << 2);
    public static final String WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";

    public interface PermissionRequestResult {
        void onPermissionResult(String permissionName, int result);
    }

    public static boolean isPermissionGranted(Activity activity, String permissionName) {
        if (activity == null)
            return false;

        // For Android < Android M, self permissions are always granted.
        boolean result = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                final PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                int targetSdkVersion = info.applicationInfo.targetSdkVersion;

                if (targetSdkVersion >= Build.VERSION_CODES.M) {
                    // targetSdkVersion >= Android M, we can use Context#checkSelfPermission
                    result = activity.checkSelfPermission(permissionName) == PackageManager.PERMISSION_GRANTED;
                } else {
                    // targetSdkVersion < Android M, we have to use PermissionChecker
                    result = PermissionChecker.checkSelfPermission(activity, permissionName) == PermissionChecker.PERMISSION_GRANTED;
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                result = false;
            }
        }

        return result;
    }

    public static void requestPermissionsAsync(Activity activity, final String[] permissionNames, final PermissionRequestResult resultCallbacks) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;
        if (activity == null || permissionNames == null || resultCallbacks == null)
            return;

        final PermissionFragment request = new PermissionFragment();
        request.setListener(resultCallbacks);
        Bundle bundle = new Bundle();
        bundle.putStringArray(PermissionFragment.PERMISSION_NAMES, permissionNames);
        request.setArguments(bundle);
        FragmentTransaction fragmentTransaction = activity.getFragmentManager().beginTransaction();
        fragmentTransaction.add(0, request);
        fragmentTransaction.commit();
    }

    public static void requestPermissionAsync(Activity activity, final String permissionName, final PermissionRequestResult resultCallbacks) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;
        if (activity == null || permissionName == null || permissionName.equals("") || resultCallbacks == null)
            return;

        final String[] permissionNames = new String[1];
        permissionNames[0] = permissionName;

        final PermissionFragment request = new PermissionFragment();
        request.setListener(resultCallbacks);
        Bundle bundle = new Bundle();
        bundle.putStringArray(PermissionFragment.PERMISSION_NAMES, permissionNames);
        request.setArguments(bundle);
        FragmentTransaction fragmentTransaction = activity.getFragmentManager().beginTransaction();
        fragmentTransaction.add(0, request);
        fragmentTransaction.commit();
    }
}