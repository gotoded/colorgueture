package com.hook.colorgueture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class GestureReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.hook.colorgueture.SHOW_PANEL".equals(intent.getAction())) {
            Intent overlayIntent = new Intent(context, OverlayActivity.class);
            overlayIntent.putExtra("x", intent.getFloatExtra("x", 0));
            overlayIntent.putExtra("y", intent.getFloatExtra("y", 0));
            overlayIntent.putExtra("side", intent.getStringExtra("side"));
            overlayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(overlayIntent);
        }
    }
}