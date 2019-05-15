package com.unnynet.android.helper;


import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;

public class PermissionFragment extends Fragment {

    public static final String PERMISSION_NAMES = "PermissionNames";
    private static final int PERMISSIONS_REQUEST_CODE = 15887;
    private AndroidPermissions.PermissionRequestResult resultCallbacks;


    public void setListener(AndroidPermissions.PermissionRequestResult listener) {
        resultCallbacks = listener;
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (resultCallbacks == null) {
            getFragmentManager().beginTransaction().remove(this).commit();
        } else {
            String[] permissionNames = getArguments().getStringArray(PERMISSION_NAMES);
            if (permissionNames != null)
                requestPermissions(permissionNames, PERMISSIONS_REQUEST_CODE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != PERMISSIONS_REQUEST_CODE)
            return;

        for (int i = 0; i < permissions.length && i < grantResults.length; ++i) {
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                resultCallbacks.onPermissionResult(permissions[i], AndroidPermissions.PERMISSION_GRANTED);
            else {
                boolean should = getActivity().shouldShowRequestPermissionRationale(permissions[i]);
                resultCallbacks.onPermissionResult(permissions[i], should ? AndroidPermissions.PERMISSION_DENIED : AndroidPermissions.PERMISSION_DENIED_AND_NEVER_AGAIN);
            }
        }

        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.remove(this);
        fragmentTransaction.commit();
    }
}
