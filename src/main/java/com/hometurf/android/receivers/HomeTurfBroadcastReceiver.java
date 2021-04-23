package com.hometurf.android.receivers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import static android.content.Intent.EXTRA_CHOSEN_COMPONENT;

public class HomeTurfBroadcastReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        System.out.println("On receive");
        ComponentName clickedComponent = intent.getParcelableExtra(EXTRA_CHOSEN_COMPONENT);
        System.out.println(clickedComponent.toShortString());
    }
}
