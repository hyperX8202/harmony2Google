package com.ab.hook;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/** Weiche: Apps, die EasyAbroad kennt, werden ueber EasyAbroad installiert statt ueber Aurora. */
public final class EA {
    private static HashSet<String> set;
    static final LinkedList<String> queue = new LinkedList<String>();

    private static synchronized HashSet<String> load(Context ctx) {
        if (set != null) return set;
        HashSet<String> s = new HashSet<String>();
        try {
            InputStream in = ctx.getAssets().open("easyabroad.txt");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String l;
            while ((l = r.readLine()) != null) { l = l.trim(); if (l.length() > 0) s.add(l); }
            r.close();
        } catch (Throwable ignored) {}
        set = s;
        return s;
    }

    static void dbg(Context c, String m) { dbg(c, m, false); }

    static void dbg(Context c, String m, boolean show) {
        android.util.Log.d("AuroraEA", m);
    }

    static void toast(final Context ctx, final String de, final String en) {
        final boolean isDe = "de".equals(Locale.getDefault().getLanguage());
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                try { Toast.makeText(ctx, isDe ? de : en, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
            }
        });
    }

    static boolean launch(Context ctx, String pkg) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.easy.abroad", "com.easy.abroad.activities.MainActivity"));
            i.putExtra("openAppEntrance", "2");
            i.putExtra("type", "activity_app_detail");
            i.putExtra("package_name", pkg);
            i.putExtra("operation", "install");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static int ownTaskId(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.AppTask> l = am.getAppTasks();
            if (l != null && !l.isEmpty()) {
                ActivityManager.RecentTaskInfo ti = l.get(0).getTaskInfo();
                return Build.VERSION.SDK_INT >= 29 ? ti.taskId : ti.id;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /** true = EasyAbroad uebernimmt, Aurora soll selbst nichts laden. */
    public static boolean handle(Context ctx, String pkg) {
        try {
            if (ctx == null || pkg == null || !load(ctx).contains(pkg)) return false;
            Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
            synchronized (EA.class) {
                if (EAService.running) {
                    if (!queue.contains(pkg) && !pkg.equals(EAService.current)) queue.add(pkg);
                    toast(app, "In der Warteschlange für EasyAbroad", "Queued for EasyAbroad");
                    return true;
                }
            }
            int task = ownTaskId(app);
            dbg(app, "Weiche: " + pkg + " task=" + task + " sdk=" + Build.VERSION.SDK_INT);
            if (!launch(app, pkg)) { dbg(app, "EasyAbroad-Start fehlgeschlagen"); return false; }
            toast(app, "Installation läuft über EasyAbroad", "Installing via EasyAbroad");
            try {
                Intent s = new Intent(app, EAService.class);
                s.putExtra("pkg", pkg);
                s.putExtra("task", task);
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(s); else app.startService(s);
                dbg(app, "Dienst angefordert");
            } catch (Throwable t) { dbg(app, "Dienst-Start-Fehler " + t); }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
