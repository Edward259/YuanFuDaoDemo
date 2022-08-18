package com.android.onyx.utils;

import android.content.Context;
import android.graphics.Rect;

import com.onyx.android.sdk.api.device.epd.EpdController;

public class TouchUtils {

    public static void disableFingerTouch(Context context) {
        int width = context.getResources().getDisplayMetrics().widthPixels;
        int height = context.getResources().getDisplayMetrics().heightPixels;
        Rect rect = new Rect(0, 0, width, height);
        Rect[] arrayRect =new Rect[]{rect};
        EpdController.setAppCTPDisableRegion(context, arrayRect);
    }

    public static void enableFingerTouch(Context context) {
        EpdController.appResetCTPDisableRegion(context);
    }
}
