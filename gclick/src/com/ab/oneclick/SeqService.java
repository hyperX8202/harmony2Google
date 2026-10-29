package com.ab.oneclick;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Vordergrunddienst. Modus "apps": installiert Pakete nacheinander ueber EasyAbroad.
 * Modus "aurora": installiert ein mitgeliefertes APK ueber eine eigene Installationssitzung.
 * Das Ueberlagerungsfenster haelt die App im Hintergrund am Laufen, moveTaskToFront holt sie danach zurueck.
 */
public class SeqService extends Service {
    private static final String CH = "oneclick";
    private static final String CH_UP = "oneclick_up";
    private PowerManager.WakeLock wl;
    private volatile boolean running;
    private View overlay;

    static void start(Context c, String[] pkgs, String mode) { start(c, pkgs, mode, false); }

    static void start(Context c, String[] pkgs, String mode, boolean alarm) {
        Intent i = new Intent(c, SeqService.class);
        i.putExtra("alarm", alarm);
        i.putExtra("pkgs", pkgs);
        i.putExtra("mode", mode);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Throwable t) { Seq.log("Dienst-Start-Fehler " + t); }
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "Gclick", NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(new NotificationChannel(CH_UP, "Gclick Rueckkehr", NotificationManager.IMPORTANCE_HIGH));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        startForeground(2, b.setContentTitle("Gclick").setContentText(Tr.t("notif_running")).setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true).build());
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "oneclick:seq");
            wl.acquire(30 * 60 * 1000L);
        } catch (Throwable ignored) {}
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        final String[] pkgs = intent != null ? intent.getStringArrayExtra("pkgs") : null;
        final String mode = intent != null ? intent.getStringExtra("mode") : null;
        final boolean alarm = intent != null && intent.getBooleanExtra("alarm", false);
        if (pkgs == null || running) return START_NOT_STICKY;
        running = true;
        new Thread(new Runnable() { @Override public void run() {
            Seq.t0 = System.currentTimeMillis();
            Seq.running = true;
            if (alarm) AlarmReceiver.schedule(SeqService.this, 2000);
            try { work(pkgs, mode == null ? "apps" : mode); } catch (Throwable t) { Seq.log("Dienst-Fehler " + t); }
            Seq.running = false;
            running = false;
            stopSelf();
        } }).start();
        return START_NOT_STICKY;
    }

    private boolean has(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Throwable t) { return false; }
    }

    private String ts(long t0) { return String.format("%3ds", (System.currentTimeMillis() - t0) / 1000); }

    private boolean waitFg(int ms) {
        for (int w = 0; w < ms / 250; w++) { SystemClock.sleep(250); if (Seq.fg) return true; }
        return Seq.fg;
    }

    private void addOverlay() {
        try {
            boolean can = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
            Seq.log("Overlay-Recht: " + can);
            if (!can) return;
            final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            final View v = new View(this);
            final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
            new android.os.Handler(getMainLooper()).post(new Runnable() { @Override public void run() {
                try { wm.addView(v, lp); overlay = v; } catch (Throwable t) { Seq.log("Overlay-Fehler " + t); }
            } });
        } catch (Throwable t) { Seq.log("Overlay-Fehler " + t); }
    }

    private void removeOverlay() {
        final View v = overlay;
        if (v == null) return;
        new android.os.Handler(getMainLooper()).post(new Runnable() { @Override public void run() {
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(v); } catch (Throwable ignored) {}
        } });
        overlay = null;
    }

    private void bringBack(long t0, String label) {
        SystemClock.sleep(500);
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            am.moveTaskToFront(Seq.taskId, ActivityManager.MOVE_TASK_WITH_HOME);
            Seq.log(ts(t0) + " Rueckkehr (Weg1) fuer " + label);
        } catch (Throwable t) { Seq.log(ts(t0) + " Weg1 Fehler " + t); }
        if (waitFg(3000)) return;
        try {
            Intent back = new Intent(this, MainActivity.class);
            back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(back);
        } catch (Throwable t) { Seq.log(ts(t0) + " Weg2 Fehler " + t); }
        if (waitFg(3000)) return;
        try {
            Intent back = new Intent(this, MainActivity.class);
            back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            PendingIntent pi = PendingIntent.getActivity(this, 7, back, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_UP) : new Notification.Builder(this);
            Notification n = nb.setContentTitle("Gclick").setContentText(Tr.t("notif_back"))
                .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentIntent(pi).setFullScreenIntent(pi, true).setAutoCancel(true).build();
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(3, n);
            Seq.log(ts(t0) + " Weg3: Benachrichtigung gesendet");
        } catch (Throwable t) { Seq.log(ts(t0) + " Weg3 Fehler " + t); }
        waitFg(20000);
    }

    private void work(String[] pkgs, String mode) {
        long t0 = System.currentTimeMillis();
        Seq.log("--- " + mode + " gestartet ---");
        addOverlay();
        try {
            boolean firstApp = true;
            for (String pkg : pkgs) {
                if (has(pkg)) { Seq.status(pkg, "skipped"); continue; }
                Seq.status(pkg, "running");
                boolean ok;
                if (mode.equals("aurora")) ok = installAsset("astore.apk", pkg, t0);
                else ok = installViaEasyAbroad(pkg, t0, firstApp);
                Seq.status(pkg, ok ? "done" : "failed");
                if (ok) firstApp = false;
                Seq.log(ts(t0) + " " + pkg + (ok ? " fertig" : " FEHLGESCHLAGEN"));
                bringBack(t0, pkg);
                if (ok && mode.equals("aurora")) askInstallPermission(pkg, t0);
            }
        } finally {
            removeOverlay();
        }
        Seq.log(ts(t0) + " --- Ende ---");
    }

    /** Fuehrt direkt auf die Seite "Installationen erlauben", prueft live (Alarm haelt den Dienst wach) und holt die App zurueck. */
    private void askInstallPermission(String pkg, long t0) {
        Boolean allowed = Perm.installAllowed(this, pkg);
        Seq.log(ts(t0) + " Installationsrecht von " + pkg + (allowed == null ? ": nicht lesbar" : allowed ? ": schon erteilt" : ": fehlt"));
        if (Boolean.TRUE.equals(allowed)) { Seq.status("aurora_perm", "done"); return; }
        Seq.status("aurora_perm", "waiting");
        Perm.openInstallPermission(this, pkg);
        if (allowed == null) { Seq.log(ts(t0) + " Bitte den Schalter einschalten und im Wizard auf 'Erledigt' tippen"); return; }
        for (int w = 0; w < 600; w++) {
            SystemClock.sleep(500);
            if (Boolean.TRUE.equals(Perm.installAllowed(this, pkg))) {
                Seq.status("aurora_perm", "done");
                Seq.log(ts(t0) + " Installationsrecht erteilt");
                bringBack(t0, "Freigabe");
                return;
            }
        }
        Seq.log(ts(t0) + " Installationsrecht nach 5 min nicht erteilt");
    }

    private boolean installViaEasyAbroad(String pkg, long t0, boolean first) {
        Intent i = new Intent();
        i.setComponent(new ComponentName("com.easy.abroad", "com.easy.abroad.activities.MainActivity"));
        i.putExtra("openAppEntrance", "2");
        i.putExtra("type", "activity_app_detail");
        i.putExtra("package_name", pkg);
        i.putExtra("operation", "install");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); Seq.log(ts(t0) + " EasyAbroad gestartet fuer " + pkg); }
        catch (Throwable t) { Seq.log(ts(t0) + " Start-Fehler " + t); return false; }
        int maxPolls = first ? 1800 : 600;   // erste Installation (Google-Einrichtung im Hintergrund): 15 min, sonst 5 min
        for (int w = 0; w < maxPolls; w++) { SystemClock.sleep(500); if (has(pkg)) return true; }
        return false;
    }

    private boolean installAsset(String asset, String pkg, long t0) {
        try {
            long len = -1;
            try { android.content.res.AssetFileDescriptor fd = getAssets().openFd(asset); len = fd.getLength(); fd.close(); } catch (Throwable ignored) {}
            PackageInstaller pi = getPackageManager().getPackageInstaller();
            int id = pi.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
            PackageInstaller.Session ss = pi.openSession(id);
            OutputStream os = ss.openWrite("base.apk", 0, len);
            InputStream in = getAssets().open(asset);
            byte[] buf = new byte[262144];
            long tot = 0;
            int n;
            while ((n = in.read(buf)) > 0) { os.write(buf, 0, n); tot += n; }
            in.close();
            os.flush();
            os.close();
            Seq.log(ts(t0) + " Sitzung " + id + " geschrieben (" + tot / 1024 + " KB)");
            PendingIntent pend = PendingIntent.getBroadcast(this, id,
                new Intent("com.ab.oneclick.INSTALL_DONE").setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE);
            ss.commit(pend.getIntentSender());
            SystemClock.sleep(5000);
            Intent c = new Intent("android.content.pm.action.CONFIRM_INSTALL");
            c.putExtra("android.content.pm.extra.SESSION_ID", id);
            c.setComponent(new ComponentName("com.android.packageinstaller", "com.android.packageinstaller.InstallStart"));
            c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(c);
            Seq.log(ts(t0) + " Bestaetigungsdialog gestartet");
            for (int w = 0; w < 360; w++) { SystemClock.sleep(500); if (has(pkg)) return true; }
        } catch (Throwable t) { Seq.log(ts(t0) + " Install-Fehler " + t); }
        return false;
    }

    @Override public void onDestroy() {
        running = false;
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
