package com.ab.oneclick;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** Freigaben fuer andere Apps im Container: Seite oeffnen und Zustand lesen. */
final class Perm {
    /** Oeffnet "Apps aus unbekannten Quellen installieren" fuer pkg (feste Container-Seite, sonst allgemeiner Aufruf). */
    static void openInstallPermission(Context c, String pkg) {
        Intent i = new Intent();
        i.setClassName("com.android.settings", "com.android.settings.Settings$ManageAppExternalSourcesActivity");
        i.setData(Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            Seq.log("Seite 'Installationen erlauben' fuer " + pkg + " geoeffnet");
            return;
        } catch (Throwable t) { Seq.log("Feste Seite nicht startbar (" + t + "), versuche allgemeinen Aufruf"); }
        try {
            c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) { Seq.log("Installationsrecht-Seite: " + t); }
    }

    /** true/false = Zustand lesbar, null = nicht lesbar. */
    static Boolean installAllowed(Context c, String pkg) {
        try {
            int uid = c.getPackageManager().getApplicationInfo(pkg, 0).uid;
            android.app.AppOpsManager am = (android.app.AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            java.lang.reflect.Method m;
            try { m = am.getClass().getMethod("unsafeCheckOpNoThrow", String.class, int.class, String.class); }
            catch (NoSuchMethodException e) { m = am.getClass().getMethod("checkOpNoThrow", String.class, int.class, String.class); }
            int mode = ((Integer) m.invoke(am, "android:request_install_packages", uid, pkg)).intValue();
            return mode == 0;
        } catch (Throwable t) { return null; }
    }
}
