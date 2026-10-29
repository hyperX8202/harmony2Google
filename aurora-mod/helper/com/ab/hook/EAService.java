package com.ab.hook;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.List;

/** Haelt Aurora wach, bis EasyAbroad die App installiert hat, und holt Aurora dann zurueck. */
public class EAService extends Service {
    static volatile boolean running;
    static volatile String current;
    private PowerManager.WakeLock wl;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        if (running) return START_NOT_STICKY;
        running = true;
        EA.dbg(this, "Dienst laeuft (startForeground folgt)");
        final String pkg = in != null ? in.getStringExtra("pkg") : null;
        final int task = in != null ? in.getIntExtra("task", -1) : -1;
        current = pkg;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification.Builder nb;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel("ea_wait", "EasyAbroad", NotificationManager.IMPORTANCE_LOW));
                nb = new Notification.Builder(this, "ea_wait");
            } else nb = new Notification.Builder(this);
            Notification n = nb.setContentTitle("Aurora").setContentText("EasyAbroad: " + pkg).setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true).build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(41, n, 1); else startForeground(41, n);
            EA.dbg(this, "startForeground ok");
        } catch (Throwable t) { EA.dbg(this, "startForeground Fehler " + t); }
        try {
            wl = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aurora:ea");
            wl.acquire(30 * 60 * 1000L);
        } catch (Throwable ignored) {}
        EAAlarm.schedule(this, 2000);
        EA.dbg(this, "Alarm geplant, Wake-Lock=" + (wl != null && wl.isHeld()));
        new Thread(new Runnable() {
            @Override public void run() { work(pkg, task); }
        }).start();
        return START_NOT_STICKY;
    }

    private long stamp(String pkg) {
        try { return getPackageManager().getPackageInfo(pkg, 0).lastUpdateTime; } catch (PackageManager.NameNotFoundException e) { return 0; } catch (Throwable t) { return -1; }
    }

    private boolean fg() {
        try {
            List<ActivityManager.RunningAppProcessInfo> l = ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getRunningAppProcesses();
            if (l != null) for (ActivityManager.RunningAppProcessInfo p : l)
                if (p.pid == android.os.Process.myPid()) return p.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        } catch (Throwable ignored) {}
        return false;
    }

    private void back(int task) {
        SystemClock.sleep(500);
        try {
            ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).moveTaskToFront(task, ActivityManager.MOVE_TASK_WITH_HOME);
            EA.dbg(this, "Weg1 moveTaskToFront(" + task + ") ohne Fehler");
        } catch (Throwable t) { EA.dbg(this, "Weg1 Fehler " + t); }
        for (int i = 0; i < 6 && !fg(); i++) SystemClock.sleep(500);
        EA.dbg(this, "nach Weg1 vorn=" + fg());
        if (fg()) return;
        try {
            Intent l = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (l != null) {
                l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(l);
                EA.dbg(this, "Weg2 startActivity ohne Fehler");
            }
        } catch (Throwable t) { EA.dbg(this, "Weg2 Fehler " + t); }
        for (int i = 0; i < 6 && !fg(); i++) SystemClock.sleep(500);
        EA.dbg(this, "nach Weg2 vorn=" + fg());
    }

    private void work(String first, int task) {
        try {
            String pkg = first;
            while (pkg != null) {
                current = pkg;
                long base = stamp(pkg);
                if (!pkg.equals(first)) EA.launch(this, pkg);
                EA.dbg(this, "warte auf " + pkg + " (Stempel " + base + ")");
                for (int w = 0; w < 1200; w++) {          // bis 10 min
                    SystemClock.sleep(500);
                    long s = stamp(pkg);
                    if (w % 20 == 19) EA.dbg(this, "warte " + (w / 2 + 1) + " s, Stempel " + s + ", vorn=" + fg(), false);
                    if (s != base && s != -1) { EA.dbg(this, "installiert erkannt, Stempel " + s); back(task); break; }
                }
                first = "";
                synchronized (EA.class) { pkg = EA.queue.isEmpty() ? null : EA.queue.removeFirst(); }
            }
        } finally {
            running = false;
            current = null;
            try { if (wl != null && wl.isHeld()) wl.release(); } catch (Throwable ignored) {}
            try { stopForeground(true); } catch (Throwable ignored) {}
            stopSelf();
        }
    }
}
