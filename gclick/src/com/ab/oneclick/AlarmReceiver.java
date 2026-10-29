package com.ab.oneclick;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Weckruf-Test: schreibt alle 2 s eine Zeile ins Protokoll, solange der Dienst laeuft. */
public class AlarmReceiver extends BroadcastReceiver {
    static void schedule(Context c, long delayMs) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent op = PendingIntent.getBroadcast(c, 31, new Intent(c, AlarmReceiver.class).setAction("com.ab.oneclick.WAKE"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent show = PendingIntent.getActivity(c, 32, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + delayMs, show), op);
        } catch (Throwable t) { Seq.log("Alarm-Fehler " + t); }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (!Seq.running) return;
        if (Seq.verbose) Seq.log(String.format("%3ds  Alarm (vorn: %s)", (System.currentTimeMillis() - Seq.t0) / 1000, Seq.fg));
        schedule(c, 2000);
    }
}
