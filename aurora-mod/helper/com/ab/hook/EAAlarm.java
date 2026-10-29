package com.ab.hook;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Weckruf alle 2 s, solange EAService laeuft: verhindert, dass HarmonyOS den Container einfriert. */
public class EAAlarm extends BroadcastReceiver {
    static void schedule(Context c, long delayMs) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent op = PendingIntent.getBroadcast(c, 51, new Intent(c, EAAlarm.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Intent l = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            PendingIntent show = PendingIntent.getActivity(c, 52, l != null ? l : new Intent(), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + delayMs, show), op);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (EAService.running) schedule(c, 2000);
    }
}
