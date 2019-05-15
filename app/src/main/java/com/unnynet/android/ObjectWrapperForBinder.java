package com.unnynet.android;

import android.os.Binder;

public class ObjectWrapperForBinder extends Binder {
    private final Object mData;

    ObjectWrapperForBinder(Object data) {
        mData = data;
    }

    public Object getData() {
        return mData;
    }
}
