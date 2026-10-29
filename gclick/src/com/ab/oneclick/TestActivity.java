package com.ab.oneclick;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.util.Rational;
import android.content.ComponentName;
import android.content.pm.PackageInstaller;
import java.io.InputStream;
import java.io.OutputStream;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Oneclick Testseite 2: oeffnet AppGallery-Links aus dem Container und zeigt installierte Pakete. */
public class TestActivity extends Activity {
    private static final String[][] LINKS = {
        {"1  store:// EasyAbroad (geht)", "store://appgallery.huawei.com/app/detail?id=com.easy.hmos.abroad", null, null},
        {"A  easy_abroad://details Photos", "easy_abroad://details?id=com.google.android.apps.photos", null, null},
        {"B  A + Paket com.easy.hmos.abroad", "easy_abroad://details?id=com.google.android.apps.photos", "com.easy.hmos.abroad", null},
        {"C  A + Aktion ohos.want.action.AG", "easy_abroad://details?id=com.google.android.apps.photos", "com.easy.hmos.abroad", "ohos.want.action.AG"},
        {"D  abroad://easy.hmos.abroad/open", "abroad://easy.hmos.abroad/open", null, null},
        {"E  abroad://.../open?id=Photos", "abroad://easy.hmos.abroad/open?id=com.google.android.apps.photos", null, null},
        {"F  abroad://com.easy.hmos.abroad/open", "abroad://com.easy.hmos.abroad/open", null, null},
    };
    private static final String[] PKGS = {
        "com.easy.abroad", "com.easy.hmos.abroad", "com.zhuoyi.appstore.lite", "com.huawei.appmarket",
        "com.google.android.gms", "com.google.android.gsf", "com.android.vending",
        "com.google.android.apps.maps", "com.google.android.apps.photos", "com.google.android.youtube",
        "com.google.android.gm", "com.ubercab", "com.ab.astore", "com.aurora.store", "com.ab.oneclick", "com.google.android.gm", "com.google.android.calculator", "com.google.android.feedback", "com.google.android.apps.docs",
    };

    private TextView out;
    private TextView seqView;
    private volatile long lastResume;
    private volatile boolean fg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.colorBackground, tv, true);
            getWindow().setNavigationBarColor(tv.data);
            getWindow().setStatusBarColor(tv.data);
        } catch (Throwable ignored) {}
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        col.setPadding(p, p * 2, p, p);
        sv.addView(col);

        TextView title = new TextView(this);
        title.setText("Oneclick Testseite 2");
        title.setTextSize(22);
        col.addView(title);

        seqView = new TextView(this);
        seqView.setTextSize(12);
        seqView.setTypeface(android.graphics.Typeface.MONOSPACE);
        seqView.setText("(Protokoll: erscheint hier nach Tipp auf S)");
        col.addView(seqView);

        TextView hint = new TextView(this);
        hint.setText("Tippe die Links nacheinander an. Es zaehlt: oeffnet sich die HarmonyOS-AppGallery auf der richtigen App-Seite? Unten steht die Antwort des Systems.");
        hint.setPadding(0, p / 2, 0, p / 2);
        col.addView(hint);

        for (final String[] l : LINKS) {
            Button bt = new Button(this);
            bt.setAllCaps(false);
            bt.setText(l[0]);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { open(l[0], l[1], l[2], l[3]); }
            });
            col.addView(bt);
        }

        final String[][] MP = {
            {"M1  EasyAbroad: Gmail-Seite", "com.google.android.gm"},
            {"M2  EasyAbroad: Drive-Seite", "com.google.android.apps.docs"},
            {"M3  EasyAbroad: Chrome-Seite", "com.android.chrome"},
        };
        for (final String[] mp : MP) {
            Button mb = new Button(this);
            mb.setAllCaps(false);
            mb.setText(mp[0]);
            mb.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openDetail(mp[0], mp[1]); }
            });
            col.addView(mb);
        }

        Button seq = new Button(this);
        seq.setAllCaps(false);
        seq.setText("S  Ablauf: Drive + Gmail nacheinander, danach zurueck");
        seq.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { SeqService.start(TestActivity.this, new String[]{"com.google.android.apps.docs", "com.google.android.gm"}, "apps"); }
        });
        col.addView(seq);

        Button seq2 = new Button(this);
        seq2.setAllCaps(false);
        seq2.setText("S2  wie S, aber mit Bild-in-Bild");
        seq2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Seq.log("PiP-Feature: " + getPackageManager().hasSystemFeature("android.software.picture_in_picture"));
                try {
                    enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(9, 16)).build());
                    Seq.log("PiP: enterPictureInPictureMode ok");
                } catch (Throwable t) { Seq.log("PiP-Fehler " + t); }
                new android.os.Handler().postDelayed(new Runnable() { @Override public void run() {
                    SeqService.start(TestActivity.this, new String[]{"com.google.android.apps.docs", "com.google.android.gm"}, "apps");
                } }, 1500);
            }
        });
        col.addView(seq2);

        String[][] XB = {
            {"X1  Keep + Sheets: OHNE Tricks (Dienst + Overlay)", "1"},
            {"X2  Translate + Slides: mit Bild-in-Bild", "2"},
            {"X3  Authenticator + Meet: mit Alarm-Weckruf", "3"},
        };
        for (final String[] xb : XB) {
            Button xbt = new Button(this);
            xbt.setAllCaps(false);
            xbt.setText(xb[0]);
            xbt.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { runExperiment(Integer.parseInt(xb[1])); }
            });
            col.addView(xbt);
        }

        final String[][] IP = {
            {"I1  Drive DIREKT installieren", "com.google.android.apps.docs"},
            {"I2  Chrome DIREKT installieren", "com.android.chrome"},
        };
        for (final String[] ip : IP) {
            Button ib = new Button(this);
            ib.setAllCaps(false);
            ib.setText(ip[0]);
            ib.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openInstall(ip[0], ip[1]); }
            });
            col.addView(ib);
        }

        Button inst2 = new Button(this);
        inst2.setAllCaps(false);
        inst2.setText("R  Google Rechner installieren (echte App, 3,2 MB)");
        inst2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { installAsset("calc.apk", "com.google.android.calculator"); }
        });
        col.addView(inst2);

        Button ov = new Button(this);
        ov.setAllCaps(false);
        ov.setText("Rechte: ueber anderen Apps einblenden / Benachrichtigungen");
        ov.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
                catch (Throwable t) { log("Overlay-Seite: " + t); }
                try { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1); } catch (Throwable ignored) {}
            }
        });
        col.addView(ov);

        Button bat = new Button(this);
        bat.setAllCaps(false);
        bat.setText("Rechte: Akku-Optimierung ausschalten");
        bat.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); }
                catch (Throwable t) { log("Akku-Seite: " + t); }
            }
        });
        col.addView(bat);

        String[][] KB = {
            {"K1  Konten und Kontakte im Container zaehlen", "1"},
            {"K2  Test-VCF in Download schreiben", "2"},
            {"K3  Test-VCF per Oeffnen mit an HarmonyOS", "3"},
            {"K4  ALLE Container-Kontakte als VCF exportieren", "4"},
            {"K5  Sync-Zustand und Kontakte-Bausteine zeigen", "5"},
            {"K6  Kontakte-Sync einschalten und anstossen", "6"},
            {"K7  Kalender: Bausteine, Konten, Zahlen zeigen", "7"},
            {"K8  Kalender-Sync einschalten und anstossen", "8"},
            {"C0  Chrome: Google-Kalender-Export oeffnen", "10"},
            {"C2  ZIP automatisch suchen, entpacken, in Kalender oeffnen", "12"},
            {"C3  naechste Kalenderdatei oeffnen", "13"},
            {"C1  (alt) iCal-Link aus Zwischenablage laden", "11"},
        };
        for (final String[] kb : KB) {
            Button kbtn = new Button(this);
            kbtn.setAllCaps(false);
            kbtn.setText(kb[0]);
            kbtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { contactsTest(Integer.parseInt(kb[1])); }
            });
            col.addView(kbtn);
        }

        Button chk = new Button(this);
        chk.setAllCaps(false);
        chk.setText("Installierte Pakete pruefen");
        chk.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { check(); }
        });
        col.addView(chk);

        Button mg = new Button(this);
        mg.setAllCaps(false);
        mg.setText("V1  microG-Uebergabe wie Aurora nachstellen (Rechner)");
        mg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { microgTest(); }
        });
        col.addView(mg);

        Button ms = new Button(this);
        ms.setAllCaps(false);
        ms.setText("V3  microG-Einstellungen oeffnen");
        ms.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent mi = new Intent(Intent.ACTION_MAIN);
                    mi.setClassName("com.android.vending", "org.microg.vending.ui.MainActivity");
                    startActivity(mi);
                    Seq.log("V3: microG-Einstellungen gestartet");
                } catch (Throwable t) { Seq.log("V3: FEHLER " + t); }
            }
        });
        col.addView(ms);

        out = new TextView(this);
        out.setTextSize(13);
        out.setTypeface(android.graphics.Typeface.MONOSPACE);
        out.setPadding(0, p, 0, 0);
        col.addView(out);
        setContentView(sv);
        check();
    }

    @Override
    protected void onPause() {
        super.onPause();
        fg = false;
        Seq.fg = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastResume = System.currentTimeMillis();
        fg = true;
        Seq.fg = true;
        Seq.lastResume = lastResume;
        Seq.taskId = getTaskId();
        Seq.cur = this;
        Seq.refresher = new Runnable() { @Override public void run() { refreshSeq(); } };
        refreshSeq();
        if (out != null) check();
    }

    private void open(String name, String url, String pkg, String action) {
        StringBuilder sb = new StringBuilder();
        sb.append("> ").append(name).append('\n');
        Intent i = new Intent(action != null ? action : Intent.ACTION_VIEW, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (pkg != null) i.setPackage(pkg);
        try {
            ResolveInfo ri = getPackageManager().resolveActivity(i, 0);
            sb.append("  resolve: ").append(ri == null || ri.activityInfo == null ? "NULL (kein Container-Handler)"
                : ri.activityInfo.packageName + "/" + ri.activityInfo.name).append('\n');
        } catch (Throwable t) {
            sb.append("  resolve-Fehler: ").append(t).append('\n');
        }
        try {
            startActivity(i);
            sb.append("  startActivity: OK\n");
        } catch (Throwable t) {
            sb.append("  startActivity: ").append(t).append('\n');
        }
        out.setText(sb.append('\n').append(out.getText()));
    }

    /** Startet die EasyAbroad-Detailseite im Container (MarketPageActivity ist exportiert). */
    private void openDetail(String name, String pkg) {
        StringBuilder sb = new StringBuilder("> " + name + "\n");
        Intent i = new Intent();
        i.setComponent(new ComponentName("com.easy.abroad", "com.easy.abroad.activities.MarketPageActivity"));
        i.putExtra("packageName", pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            sb.append("  startActivity: OK\n");
        } catch (Throwable t) {
            sb.append("  startActivity: ").append(t).append('\n');
        }
        out.setText(sb.append('\n').append(out.getText()));
    }

    /** EasyAbroad-TestActivity (exportiert): Detailseite mit operation=install -> autoDownload. */
    private void openInstall(String name, String pkg) {
        StringBuilder sb = new StringBuilder("> " + name + "\n");
        Intent i = new Intent();
        i.setComponent(new ComponentName("com.easy.abroad", "com.easy.abroad.activities.TestActivity"));
        i.putExtra("openAppEntrance", "2");
        i.putExtra("type", "activity_app_detail");
        i.putExtra("package_name", pkg);
        i.putExtra("operation", "install");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            sb.append("  startActivity: OK\n");
        } catch (Throwable t) {
            sb.append("  startActivity: ").append(t).append('\n');
        }
        out.setText(sb.append('\n').append(out.getText()));
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, android.content.res.Configuration c) {
        super.onPictureInPictureModeChanged(inPip, c);
        Seq.log("PiP-Modus: " + inPip);
    }

    private android.net.Uri lastVcf;

    private boolean needContactsPerm() {
        if (checkSelfPermission("android.permission.READ_CONTACTS") == PackageManager.PERMISSION_GRANTED) return false;
        requestPermissions(new String[]{"android.permission.READ_CONTACTS", "android.permission.GET_ACCOUNTS"}, 2);
        Seq.log("Kontakte-Recht angefragt - bitte erlauben und den Knopf nochmal tippen");
        return true;
    }

    private android.net.Uri newDownload(String name) {
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/x-vcard");
        cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
        return getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
    }

    private void contactsTest(int which) {
        try {
            if (which == 1) {
                if (needContactsPerm()) return;
                StringBuilder sb = new StringBuilder("K1:");
                try {
                    android.accounts.Account[] acc = android.accounts.AccountManager.get(this).getAccounts();
                    sb.append(" Konten=").append(acc.length);
                    for (android.accounts.Account a : acc) sb.append(" [").append(a.type).append("]");
                } catch (Throwable t) { sb.append(" Konten-Fehler ").append(t); }
                Seq.log(sb.toString());
                java.util.HashMap<String, Integer> byType = new java.util.HashMap<String, Integer>();
                android.database.Cursor c = getContentResolver().query(android.provider.ContactsContract.RawContacts.CONTENT_URI,
                    new String[]{"account_type"}, "deleted=0", null, null);
                int n = 0;
                if (c != null) { while (c.moveToNext()) { String t = c.getString(0); if (t == null) t = "(lokal)"; Integer o = byType.get(t); byType.put(t, o == null ? 1 : o + 1); n++; } c.close(); }
                Seq.log("K1: Rohkontakte=" + n + " nach Konto-Typ: " + byType);
                android.database.Cursor c2 = getContentResolver().query(android.provider.ContactsContract.Contacts.CONTENT_URI, new String[]{"_id"}, null, null, null);
                Seq.log("K1: Kontakte gesamt=" + (c2 == null ? -1 : c2.getCount()));
                if (c2 != null) c2.close();
            } else if (which == 2) {
                android.net.Uri u = newDownload("oneclick_test.vcf");
                String v = "BEGIN:VCARD\nVERSION:3.0\nFN:Oneclick Test Eins\nN:Eins;Oneclick Test;;;\nTEL;TYPE=CELL:+49 170 0000001\nEND:VCARD\n"
                         + "BEGIN:VCARD\nVERSION:3.0\nFN:Oneclick Test Zwei\nN:Zwei;Oneclick Test;;;\nTEL;TYPE=CELL:+49 170 0000002\nEND:VCARD\n";
                OutputStream os = getContentResolver().openOutputStream(u);
                os.write(v.getBytes("UTF-8"));
                os.close();
                lastVcf = u;
                Seq.log("K2: geschrieben -> " + u);
            } else if (which == 3) {
                if (lastVcf == null) { Seq.log("K3: erst K2 oder K4 tippen"); return; }
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(lastVcf, "text/x-vcard");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    ResolveInfo ri = getPackageManager().resolveActivity(i, 0);
                    Seq.log("K3: resolve=" + (ri == null || ri.activityInfo == null ? "NULL" : ri.activityInfo.packageName + "/" + ri.activityInfo.name));
                } catch (Throwable t) { Seq.log("K3: resolve-Fehler " + t); }
                startActivity(i);
                Seq.log("K3: startActivity ok");
            } else if (which == 4) {
                if (needContactsPerm()) return;
                java.util.ArrayList<String> keys = new java.util.ArrayList<String>();
                android.database.Cursor c = getContentResolver().query(android.provider.ContactsContract.Contacts.CONTENT_URI, new String[]{"lookup"}, null, null, null);
                if (c != null) { while (c.moveToNext()) keys.add(c.getString(0)); c.close(); }
                Seq.log("K4: " + keys.size() + " Kontakte gefunden");
                if (keys.isEmpty()) return;
                android.net.Uri u = newDownload("oneclick_contacts.vcf");
                OutputStream os = getContentResolver().openOutputStream(u);
                long bytes = 0;
                for (int from = 0; from < keys.size(); from += 100) {
                    StringBuilder ks = new StringBuilder();
                    for (int k = from; k < Math.min(keys.size(), from + 100); k++) { if (ks.length() > 0) ks.append(':'); ks.append(keys.get(k)); }
                    android.net.Uri vu = android.net.Uri.withAppendedPath(android.provider.ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI, android.net.Uri.encode(ks.toString()));
                    InputStream in = getContentResolver().openInputStream(vu);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) { os.write(buf, 0, n); bytes += n; }
                    in.close();
                }
                os.close();
                lastVcf = u;
                Seq.log("K4: " + bytes + " Bytes -> " + u);
            } else if (which == 10) {
                Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://calendar.google.com/calendar/u/0/r/settings/export"));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.setPackage("com.android.chrome");
                try {
                    startActivity(i);
                    Seq.log("C0: Chrome gestartet. Dort Menue -> 'Desktop-Version', dann 'Exportieren' tippen und die ZIP-Datei herunterladen. Danach zurueck zu Oneclick: C2.");
                } catch (Throwable t) {
                    Seq.log("C0: Chrome nicht startbar: " + t);
                    i.setPackage(null);
                    startActivity(i);
                    Seq.log("C0: Standardbrowser gestartet");
                }
            } else if (which == 12) {
                findAndOpenCalendarZip();
            } else if (which == 13) {
                openNextIcs();
            } else if (which == 11) {
                icalImport();
            } else if (which == 5 || which == 6) {
                android.accounts.Account[] acc = android.accounts.AccountManager.get(this).getAccountsByType("com.google");
                Seq.log("K" + which + ": google-Konten=" + acc.length);
                StringBuilder ty = new StringBuilder();
                for (android.content.SyncAdapterType t : android.content.ContentResolver.getSyncAdapterTypes()) {
                    if (t.accountType.equals("com.google") || t.authority.contains("contacts")) ty.append(t.accountType).append(":").append(t.authority).append(t.isUserVisible() ? "" : "(unsichtbar)").append(" ");
                }
                Seq.log("K" + which + ": SyncAdapter " + (ty.length() == 0 ? "KEINE" : ty.toString()));
                for (android.accounts.Account a : acc) {
                    if (which == 6) {
                        try { android.content.ContentResolver.setIsSyncable(a, "com.android.contacts", 1); } catch (Throwable t) { Seq.log("K6: setIsSyncable " + t); }
                        try { android.content.ContentResolver.setSyncAutomatically(a, "com.android.contacts", true); } catch (Throwable t) { Seq.log("K6: setSyncAutomatically " + t); }
                        try {
                            android.os.Bundle b2 = new android.os.Bundle();
                            b2.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true);
                            b2.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                            android.content.ContentResolver.requestSync(a, "com.android.contacts", b2);
                            Seq.log("K6: requestSync gesendet - nach 30 s K1 tippen");
                        } catch (Throwable t) { Seq.log("K6: requestSync " + t); }
                    }
                    Seq.log("K" + which + ": syncable=" + android.content.ContentResolver.getIsSyncable(a, "com.android.contacts")
                        + " auto=" + android.content.ContentResolver.getSyncAutomatically(a, "com.android.contacts")
                        + " master=" + android.content.ContentResolver.getMasterSyncAutomatically());
                }
                String[] ps = {"com.google.android.syncadapters.contacts", "com.google.android.contacts", "com.android.providers.contacts", "com.android.contacts", "com.google.android.apps.contacts"};
                StringBuilder pk = new StringBuilder();
                for (String q : ps) pk.append(q.replace("com.google.android.", "g.").replace("com.android.", "a.")).append("=").append(has(q) ? "JA" : "nein").append(" ");
                Seq.log("K" + which + ": " + pk);
            } else if (which == 7 || which == 8) {
                if (checkSelfPermission("android.permission.READ_CALENDAR") != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.READ_CALENDAR", "android.permission.GET_ACCOUNTS"}, 3);
                    Seq.log("Kalender-Recht angefragt - bitte erlauben und den Knopf nochmal tippen");
                    return;
                }
                String[] ps = {"com.android.providers.calendar", "com.google.android.calendar", "com.android.calendar", "com.google.android.syncadapters.calendar", "com.google.android.gms"};
                StringBuilder pk = new StringBuilder();
                for (String q : ps) pk.append(q.replace("com.google.android.", "g.").replace("com.android.", "a.")).append("=").append(has(q) ? "JA" : "nein").append(" ");
                Seq.log("K" + which + ": Pakete " + pk);
                try {
                    android.content.ContentProviderClient pc = getContentResolver().acquireContentProviderClient("com.android.calendar");
                    Seq.log("K" + which + ": Kalender-Provider " + (pc == null ? "FEHLT" : "da"));
                    if (pc != null) pc.release();
                } catch (Throwable t) { Seq.log("K" + which + ": Provider-Fehler " + t); }
                android.accounts.Account[] acc = android.accounts.AccountManager.get(this).getAccountsByType("com.google");
                String[] auth = {"com.android.calendar", "com.google.android.calendar", "com.google.android.calendar.tasks"};
                for (android.accounts.Account a : acc) {
                    for (String au : auth) {
                        if (which == 8) {
                            try { android.content.ContentResolver.setIsSyncable(a, au, 1); } catch (Throwable t) { Seq.log("K8: setIsSyncable " + au + " " + t); }
                            try { android.content.ContentResolver.setSyncAutomatically(a, au, true); } catch (Throwable t) { Seq.log("K8: auto " + au + " " + t); }
                            try {
                                android.os.Bundle b2 = new android.os.Bundle();
                                b2.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true);
                                b2.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                                android.content.ContentResolver.requestSync(a, au, b2);
                            } catch (Throwable t) { Seq.log("K8: requestSync " + au + " " + t); }
                        }
                        String st = "K" + which + ": " + au + " syncable=" + android.content.ContentResolver.getIsSyncable(a, au)
                            + " auto=" + android.content.ContentResolver.getSyncAutomatically(a, au);
                        try { st += " aktiv=" + android.content.ContentResolver.isSyncActive(a, au) + " wartend=" + android.content.ContentResolver.isSyncPending(a, au); }
                        catch (Throwable t) { st += " (Status nicht lesbar)"; }
                        Seq.log(st);
                    }
                }
                try {
                    android.database.Cursor c = getContentResolver().query(android.provider.CalendarContract.Calendars.CONTENT_URI,
                        new String[]{"account_type", "sync_events", "visible"}, null, null, null);
                    java.util.HashMap<String, Integer> m = new java.util.HashMap<String, Integer>();
                    int n = 0, syncOn = 0;
                    if (c != null) { while (c.moveToNext()) { n++; String t = c.getString(0); Integer o = m.get(t); m.put(t, o == null ? 1 : o + 1); if (c.getInt(1) == 1) syncOn++; } c.close(); }
                    Seq.log("K" + which + ": Kalender=" + n + " (sync_events an: " + syncOn + ") " + m);
                    android.database.Cursor e = getContentResolver().query(android.provider.CalendarContract.Events.CONTENT_URI, new String[]{"_id"}, null, null, null);
                    Seq.log("K" + which + ": Termine=" + (e == null ? -1 : e.getCount()));
                    if (e != null) e.close();
                } catch (Throwable t) { Seq.log("K" + which + ": Kalender-Abfrage " + t); }
                if (which == 8) Seq.log("K8: gesendet - nach 30 s K7 tippen");
            }
        } catch (Throwable t) {
            Seq.log("K" + which + ": FEHLER " + t);
        }
    }

    private final java.util.ArrayList<android.net.Uri> icsUris = new java.util.ArrayList<android.net.Uri>();
    private int icsNext = 0;

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 4711) {
            StringBuilder sb = new StringBuilder("V1: Ergebnis von vending nach " + (System.currentTimeMillis() - microgT0) + " ms: resultCode=" + res);
            if (data != null) {
                sb.append(" data=").append(data.getData()).append(" action=").append(data.getAction());
                if (data.getExtras() != null) for (String k : data.getExtras().keySet()) sb.append(" [").append(k).append('=').append(String.valueOf(data.getExtras().get(k))).append(']');
            } else sb.append(" data=null");
            Seq.log(sb.toString());
            return;
        }
        if (req != 77) return;
        if (res != RESULT_OK || data == null || data.getData() == null) { Seq.log("C2: keine Datei gewaehlt"); return; }
        final android.net.Uri zip = data.getData();
        new Thread(new Runnable() { @Override public void run() {
            try { unzipCalendar(getContentResolver().openInputStream(zip)); } catch (Throwable t) { Seq.log("C2: FEHLER " + t); }
        } }).start();
    }

    private void unzipCalendar(InputStream src) {
        try {
            icsUris.clear();
            icsNext = 0;
            java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(src);
            java.util.zip.ZipEntry e;
            int total = 0;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory() || !name.toLowerCase().endsWith(".ics")) continue;
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                int n;
                while ((n = zin.read(buf)) > 0) bo.write(buf, 0, n);
                byte[] data = bo.toByteArray();
                String txt = new String(data, "UTF-8");
                int ev = 0, idx = 0;
                while ((idx = txt.indexOf("BEGIN:VEVENT", idx)) >= 0) { ev++; idx += 12; }
                total++;
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "oneclick_kalender_" + total + ".ics");
                cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/calendar");
                cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri out = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                OutputStream os = getContentResolver().openOutputStream(out);
                os.write(data);
                os.close();
                if (ev > 0) icsUris.add(out);
                Seq.log("C2: Kalender " + total + ": " + data.length + " Bytes, " + ev + " Termine" + (ev == 0 ? " (leer, wird uebersprungen)" : ""));
            }
            zin.close();
            if (icsUris.isEmpty()) { Seq.log("C2: keine .ics mit Terminen in der ZIP gefunden (" + total + " Dateien)"); return; }
            Seq.log("C2: " + icsUris.size() + " Kalender mit Terminen. Oeffne den ersten, danach C3 fuer die naechsten.");
            runOnUiThread(new Runnable() { @Override public void run() { openNextIcs(); } });
        } catch (Throwable t) { Seq.log("C2: FEHLER " + t); }
    }

    /** Sucht die neueste ZIP im Download-Ordner, die .ics-Dateien enthaelt, und entpackt sie. */
    private void findAndOpenCalendarZip() {
        if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            Seq.log("C2: Recht 'Zugriff auf alle Dateien' fehlt - Einstellungsseite wird geoeffnet. Erlauben, zurueck zu Oneclick, C2 nochmal tippen.");
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) { Seq.log("C2: Einstellungsseite: " + t); }
            return;
        }
        new Thread(new Runnable() { @Override public void run() {
            try {
                java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                java.io.File[] fs = dir.listFiles();
                Seq.log("C2: Download-Ordner: " + (fs == null ? "NICHT LESBAR" : fs.length + " Dateien"));
                if (fs == null) return;
                java.util.ArrayList<java.io.File> zips = new java.util.ArrayList<java.io.File>();
                for (java.io.File f : fs) if (f.isFile() && f.getName().toLowerCase().endsWith(".zip") && f.length() < 300L * 1024 * 1024) zips.add(f);
                java.util.Collections.sort(zips, new java.util.Comparator<java.io.File>() {
                    @Override public int compare(java.io.File a, java.io.File b) { return Long.compare(b.lastModified(), a.lastModified()); }
                });
                Seq.log("C2: " + zips.size() + " ZIP-Dateien gefunden, pruefe die neuesten");
                for (int k = 0; k < zips.size() && k < 15; k++) {
                    java.io.File z = zips.get(k);
                    boolean hasIcs = false;
                    try {
                        java.util.zip.ZipFile zf = new java.util.zip.ZipFile(z);
                        java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
                        while (en.hasMoreElements()) if (en.nextElement().getName().toLowerCase().endsWith(".ics")) { hasIcs = true; break; }
                        zf.close();
                    } catch (Throwable t) { continue; }
                    if (hasIcs) {
                        long ageMin = (System.currentTimeMillis() - z.lastModified()) / 60000;
                        Seq.log("C2: Kalender-ZIP gefunden (" + z.length() / 1024 + " KB, vor " + ageMin + " min)");
                        unzipCalendar(new java.io.FileInputStream(z));
                        return;
                    }
                }
                Seq.log("C2: keine ZIP mit .ics-Dateien gefunden. Erst C0 und den Export in Chrome durchfuehren.");
            } catch (Throwable t) { Seq.log("C2: FEHLER " + t); }
        } }).start();
    }

    private void openNextIcs() {
        if (icsNext >= icsUris.size()) { Seq.log("C3: keine weitere Kalenderdatei (" + icsUris.size() + " insgesamt)"); return; }
        android.net.Uri u = icsUris.get(icsNext++);
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, "text/calendar");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Seq.log("C3: Datei " + icsNext + " von " + icsUris.size() + " geoeffnet - in der Auswahl die Kalender-App waehlen");
        } catch (Throwable t) { Seq.log("C3: Oeffnen-Fehler " + t); }
    }

    private android.net.Uri lastIcs;


    private void icalImport() {
        String url = null;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData cd = cm.getPrimaryClip();
            if (cd != null && cd.getItemCount() > 0 && cd.getItemAt(0).getText() != null) url = cd.getItemAt(0).getText().toString().trim();
        } catch (Throwable t) { Seq.log("C1: Zwischenablage-Fehler " + t); return; }
        if (url == null || !url.startsWith("https://") || !url.contains("ical")) {
            Seq.log("C1: Zwischenablage enthaelt keinen https-iCal-Link (Laenge " + (url == null ? 0 : url.length()) + ")");
            return;
        }
        final String u = url;
        try { Seq.log("C1: lade von " + new java.net.URL(u).getHost() + " (Link " + u.length() + " Zeichen)"); } catch (Throwable ignored) {}
        new Thread(new Runnable() { @Override public void run() {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                int code = c.getResponseCode();
                if (code != 200) { Seq.log("C1: HTTP " + code); return; }
                InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                byte[] data = bo.toByteArray();
                String txt = new String(data, "UTF-8");
                if (!txt.startsWith("BEGIN:VCALENDAR")) { Seq.log("C1: Antwort ist kein Kalender (" + data.length + " B)"); return; }
                int ev = 0, idx = 0;
                while ((idx = txt.indexOf("BEGIN:VEVENT", idx)) >= 0) { ev++; idx += 12; }
                Seq.log("C1: " + data.length + " Bytes, " + ev + " Termine");
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "oneclick_calendar.ics");
                cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/calendar");
                cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                final android.net.Uri out = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                OutputStream os = getContentResolver().openOutputStream(out);
                os.write(data);
                os.close();
                lastIcs = out;
                Seq.log("C1: geschrieben -> " + out);
                runOnUiThread(new Runnable() { @Override public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(out, "text/calendar");
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        Seq.log("C1: Oeffnen-mit gestartet");
                    } catch (Throwable t) { Seq.log("C1: Oeffnen-Fehler " + t); }
                } });
            } catch (Throwable t) { Seq.log("C1: FEHLER " + t); }
        } }).start();
    }

    private void runExperiment(final int n) {
        final String[] pk = n == 1 ? new String[]{"com.google.android.keep", "com.google.android.apps.docs.editors.sheets"}
            : n == 2 ? new String[]{"com.google.android.apps.translate", "com.google.android.apps.docs.editors.slides"}
            : new String[]{"com.google.android.apps.authenticator2", "com.google.android.apps.tachyon"};
        Seq.verbose = (n == 3);
        Seq.log("=== Experiment X" + n + " " + (n == 1 ? "ohne Tricks" : n == 2 ? "Bild-in-Bild" : "Alarm") + " ===");
        if (n == 2) {
            try {
                enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(9, 16)).build());
                Seq.log("PiP: aufgerufen");
            } catch (Throwable t) { Seq.log("PiP-Fehler " + t); }
        }
        new android.os.Handler().postDelayed(new Runnable() { @Override public void run() {
            SeqService.start(TestActivity.this, pk, "apps", n == 3);
        } }, n == 2 ? 1500 : 200);
    }

    void refreshSeq() { if (seqView != null) seqView.setText(Seq.text()); }

    private void log(final String m) {
        runOnUiThread(new Runnable() { @Override public void run() { out.setText(m + "\n" + out.getText()); } });
    }

    private long microgT0;

    private void microgTest() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final String pkg = "com.google.android.calculator";
                    Seq.log("V1: Rechner vorher installiert: " + has(pkg));
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "gclick_v1_" + System.currentTimeMillis() + ".apk");
                    cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive");
                    cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                    final android.net.Uri u = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    OutputStream os = getContentResolver().openOutputStream(u);
                    InputStream in = getAssets().open("calc.apk");
                    byte[] buf = new byte[262144];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    in.close();
                    os.close();
                    Seq.log("V1: APK geschrieben " + u);
                    try { grantUriPermission("com.android.vending", u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Throwable t) { Seq.log("V1: grant " + t); }
                    final java.util.ArrayList<android.net.Uri> l = new java.util.ArrayList<android.net.Uri>();
                    l.add(u);
                    final Intent i = new Intent("org.microg.vending.action.INSTALL_PACKAGE");
                    i.setPackage("com.android.vending");
                    i.setType("application/vnd.android.package-archive");
                    i.putExtra("android.intent.extra.STREAM", (java.io.Serializable) l);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    runOnUiThread(new Runnable() { @Override public void run() {
                        try { microgT0 = System.currentTimeMillis(); startActivityForResult(i, 4711); Seq.log("V1: an vending gesendet"); }
                        catch (Throwable t) { Seq.log("V1: Start-Fehler " + t); }
                    }});
                    for (int k = 0; k < 60; k++) {
                        Thread.sleep(2000);
                        if (has(pkg)) { Seq.log("V1: FERTIG - Rechner installiert nach " + (System.currentTimeMillis() - microgT0) + " ms"); return; }
                    }
                    Seq.log("V1: nach 120 s nicht installiert");
                } catch (Throwable t) { Seq.log("V1: FEHLER " + t); }
            }
        }).start();
    }

    private boolean has(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Throwable t) { return false; }
    }

    /** Installiert ein APK aus den Assets ueber eine eigene PackageInstaller-Sitzung + Bestaetigungsdialog. */
    private void installAsset(final String asset, final String pkg) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    log("T: vorher installiert: " + has(pkg));
                    long len = -1;
                    try { android.content.res.AssetFileDescriptor fd = getAssets().openFd(asset); len = fd.getLength(); fd.close(); } catch (Throwable ignored) {}
                    PackageInstaller pi = getPackageManager().getPackageInstaller();
                    PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    int id = pi.createSession(sp);
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
                    log("T: Sitzung " + id + " geschrieben (" + tot + " B)");
                    PendingIntent pend = PendingIntent.getBroadcast(TestActivity.this, id,
                        new Intent("com.ab.oneclick.INSTALL_DONE").setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE);
                    ss.commit(pend.getIntentSender());
                    log("T: commit ok, warte 5 s auf Bestaetigung ...");
                    Thread.sleep(5000);
                    final Intent c = new Intent("android.content.pm.action.CONFIRM_INSTALL");
                    c.putExtra("android.content.pm.extra.SESSION_ID", id);
                    c.setComponent(new ComponentName("com.android.packageinstaller", "com.android.packageinstaller.InstallStart"));
                    c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    runOnUiThread(new Runnable() { @Override public void run() {
                        try { startActivity(c); log("T: Bestaetigungsdialog gestartet - bitte Installieren tippen"); }
                        catch (Throwable t) { log("T: CONFIRM-Fehler " + t); }
                    }});
                    for (int i = 0; i < 60; i++) {
                        Thread.sleep(2000);
                        if (has(pkg)) {
                            String inst = "?";
                            try { inst = getPackageManager().getInstallerPackageName(pkg); } catch (Throwable ignored) {}
                            log("T: FERTIG - " + pkg + " ist installiert, Installer=" + inst);
                            return;
                        }
                    }
                    log("T: nach 120 s nicht installiert");
                } catch (Throwable t) {
                    log("T: FEHLER " + t);
                }
            }
        }).start();
    }

    private void check() {
        PackageManager pm = getPackageManager();
        StringBuilder sb = new StringBuilder("Pakete im Container:\n");
        try {
            sb.append("  gesamt sichtbar: ").append(pm.getInstalledPackages(0).size()).append('\n');
        } catch (Throwable t) {
            sb.append("  Liste: ").append(t).append('\n');
        }
        for (String s : PKGS) {
            String line;
            try {
                PackageInfo pi = pm.getPackageInfo(s, 0);
                String inst = "?";
                try { inst = pm.getInstallerPackageName(s); } catch (Throwable ignored) {}
                line = "JA   v" + pi.versionName + "  [" + inst + "]";
            } catch (PackageManager.NameNotFoundException e) {
                line = "nein";
            } catch (Throwable t) {
                line = "Fehler " + t;
            }
            sb.append("  ").append(s).append(": ").append(line).append('\n');
        }
        try {
            PackageInfo vi = pm.getPackageInfo("com.android.vending", 0);
            sb.append("  vending: v").append(vi.versionName).append(" (").append(vi.versionCode).append(") ")
              .append(vi.applicationInfo.sourceDir).append(" enabled=").append(vi.applicationInfo.enabled).append('\n');
            android.content.Intent mi = new android.content.Intent("org.microg.vending.action.INSTALL_PACKAGE");
            mi.setPackage("com.android.vending");
            mi.setType("application/vnd.android.package-archive");
            java.util.List<android.content.pm.ResolveInfo> ri = pm.queryIntentActivities(mi, 0);
            sb.append("  microG-Action Handler: ").append(ri.size());
            for (android.content.pm.ResolveInfo r : ri) sb.append(" ").append(r.activityInfo.name);
            sb.append('\n');
            android.content.pm.PackageInfo ai = pm.getPackageInfo("com.google.android.gms", 0);
            sb.append("  gms: v").append(ai.versionName).append('\n');
        } catch (Throwable t) {
            sb.append("  vending-Pruefung: ").append(t).append('\n');
        }
        String old = out.getText().toString();
        int k = old.indexOf("Pakete im Container:");
        if (k >= 0) {
            int e = old.indexOf("\n\n", k);
            old = e >= 0 ? old.substring(0, k) + old.substring(e + 2) : old.substring(0, k);
        }
        out.setText(sb.append('\n').append(old));
    }
}
