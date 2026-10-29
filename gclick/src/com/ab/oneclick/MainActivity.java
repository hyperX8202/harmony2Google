package com.ab.oneclick;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/** Oneclick: Einrichtungs-Assistent fuer ein frisches HarmonyOS-Geraet (Mate XT 2) mit Android-Container. */
public class MainActivity extends Activity {
    // gBART: im AppGallery freigegeben
    static final boolean GBART_RELEASED = true;
    static final String GBART_PKG = "com.ab.keyboard";

    static final String[][] GAPPS = {
        {"Chrome", "com.android.chrome"},
        {"Google Maps", "com.google.android.apps.maps"},
        {"YouTube", "com.google.android.youtube"},
        {"Google", "com.google.android.googlequicksearchbox"},
        {"Gmail", "com.google.android.gm"},
        {"Google Drive", "com.google.android.apps.docs"},
        {"Google Fotos", "com.google.android.apps.photos"},
    };
    static final String AURORA_PKG = "com.ab.astore";

    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean resumed;

    private TextView tvNext, stRights, stGbart, stEasy, stApps, stAurora, stLogin, stContacts, stCal;
    private Button bOverlay, bNotif, bBatt, bGbart1, bGbart2, bEasy1, bEasy2, bApps, bAurora, bAuroraFile, bLogin, bContacts, bCal1, bCal2, bCal3;
    private final CheckBox[] cbs = new CheckBox[GAPPS.length];
    private final TextView[] appSt = new TextView[GAPPS.length];
    private volatile boolean contactsRunning;
    private final LinearLayout[] cards = new LinearLayout[9];
    private final LinearLayout[] bodies = new LinearLayout[9];
    private final TextView[] titles = new TextView[9];
    private final String[] titleKeys = new String[9];
    private final boolean[] expanded = new boolean[9];
    private TextView stPermNow;
    private Button bPermGo;
    private int autoPerm = -2, notifTaps, contactTaps, calTaps;
    private boolean leftForPerm, returnedUngranted, popupDlg, backFromPopup;
    private int lastPerm = -1;
    private int cardN = 0;
    private android.widget.ProgressBar pbContacts, pbAurora, pbApps;
    private int hxTaps;
    private TextView stAurPerm;
    private Button bAurP1, bAurP2, bAurAck;
    private TextView stMg;
    private Button bMg, bMgCheck, bMgAck;
    private int mgState;
    private boolean mgProbing, leftForMg;
    private long mgProbeAt;
    private Button bInstall, bFiles, bPopup, bPopupAck, bContactsSkip, bCalSkip, bEasy3;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            if (resumed) h.postDelayed(this, 2000);
        }
    };

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private boolean has(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Throwable t) { return false; }
    }

    private boolean overlayOk() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }

    private boolean notifOk() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    private boolean battOk() {
        try { return ((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()); } catch (Throwable t) { return false; }
    }

    private boolean installOk() {
        try { return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls(); } catch (Throwable t) { return false; }
    }

    private boolean filesOk() {
        try { return Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager(); } catch (Throwable t) { return false; }
    }

    /** "Popups im Hintergrund" ist ein Schalter der HarmonyOS-Einstellungen und aus dem Container nicht lesbar -> manuelle Bestaetigung. */
    private boolean popupOk() { return sp.getBoolean("popup_ack", false); }

    /** Darf Aurora Apps installieren? Per AppOps lesbar, sonst manuelle Bestaetigung. */
    private boolean aurInstallOk() {
        return "done".equals(Seq.STATUS.get("aurora_perm")) || Boolean.TRUE.equals(Perm.installAllowed(this, AURORA_PKG));
    }

    private boolean contactsOk() { return checkSelfPermission("android.permission.READ_CONTACTS") == PackageManager.PERMISSION_GRANTED; }

    private boolean calendarOk() { return checkSelfPermission("android.permission.READ_CALENDAR") == PackageManager.PERMISSION_GRANTED; }

    private boolean easyOk() { return (has("com.google.android.gms") && has("com.android.vending")) || sp.getBoolean("easy_ack", false); }

    private boolean loginOk() {
        try { return android.accounts.AccountManager.get(this).getAccountsByType("com.google").length > 0; } catch (Throwable t) { return false; }
    }

    private LinearLayout card(LinearLayout root, String titleKey) {
        final int idx = cardN++;
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(40, 128, 128, 128));
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        root.addView(c, lp);
        TextView t = new TextView(this);
        t.setText(Tr.t(titleKey));
        t.setTextSize(18);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        c.addView(t);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        c.addView(body);
        if (idx < cards.length) {
            cards[idx] = c;
            bodies[idx] = body;
            titles[idx] = t;
            titleKeys[idx] = titleKey;
        }
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (idx < expanded.length) { expanded[idx] = !expanded[idx]; refresh(); } }
        });
        return body;
    }

    private TextView text(LinearLayout parent, String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setPadding(0, dp(6), 0, 0);
        parent.addView(t);
        return t;
    }

    private GradientDrawable pill(int argb) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(argb);
        g.setCornerRadius(dp(12));
        return g;
    }

    private Button btn(LinearLayout parent, String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(15);
        b.setOnClickListener(l);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, pill(Color.argb(130, 128, 128, 128)));
        sl.addState(new int[]{-android.R.attr.state_enabled}, pill(Color.argb(25, 128, 128, 128)));
        sl.addState(new int[]{}, pill(Color.argb(70, 128, 128, 128)));
        b.setBackground(sl);
        try { b.setStateListAnimator(null); } catch (Throwable ignored) {}
        b.setMinHeight(dp(48));
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, 0);
        parent.addView(b, lp);
        return b;
    }

    /** Statusleiste und Navigationsleiste in der Hintergrundfarbe der App (kein weisser Balken unten). */
    private void styleBars() {
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.colorBackground, tv, true);
            int bg = tv.data;
            getWindow().setNavigationBarColor(bg);
            getWindow().setStatusBarColor(bg);
            if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
            boolean light = (Color.red(bg) + Color.green(bg) + Color.blue(bg)) / 3 > 128;
            if (light) {
                int f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
                getWindow().getDecorView().setSystemUiVisibility(f);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        sp = getSharedPreferences("oneclick", MODE_PRIVATE);
        styleBars();
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(32), dp(16), dp(24));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("Gclick");
        title.setTextSize(26);
        title.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { startActivity(new Intent(MainActivity.this, TestActivity.class)); return true; }
        });
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText(Tr.t("app_sub"));
        sub.setPadding(0, dp(4), 0, dp(8));
        root.addView(sub);
        tvNext = new TextView(this);
        tvNext.setTextSize(16);
        tvNext.setTypeface(null, android.graphics.Typeface.BOLD);
        tvNext.setPadding(0, 0, 0, dp(12));
        root.addView(tvNext);

        // 1 Rechte
        LinearLayout c1 = card(root, "s_rights");
        stRights = text(c1, "");
        stPermNow = text(c1, "");
        stPermNow.setTypeface(null, android.graphics.Typeface.BOLD);
        bPermGo = btn(c1, Tr.t("perm_open"), new View.OnClickListener() {
            @Override public void onClick(View v) { int cur = curPerm(); if (cur >= 0) openPerm(cur); }
        });
        bPopup = btn(c1, Tr.t("r_popup_open"), new View.OnClickListener() {
            @Override public void onClick(View v) { openPerm(7); }
        });
        bPopupAck = btn(c1, Tr.t("r_popup_ack"), new View.OnClickListener() {
            @Override public void onClick(View v) { sp.edit().putBoolean("popup_ack", true).commit(); Seq.log("Popups im Hintergrund bestaetigt"); refresh(); }
        });
        bBatt = btn(c1, Tr.t("r_batt") + " – " + Tr.t("btn_allow"), new View.OnClickListener() {
            @Override public void onClick(View v) { try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); } catch (Throwable t) { Seq.log("Akku-Seite: " + t); } }
        });

        // 2 gBART
        LinearLayout c2 = card(root, "s_gbart");
        stGbart = text(c2, "");
        bGbart1 = btn(c2, Tr.t("gbart_open"), new View.OnClickListener() {
            @Override public void onClick(View v) { openUri("store://appgallery.huawei.com/app/detail?id=" + GBART_PKG); }
        });
        bGbart2 = btn(c2, Tr.t("gbart_done"), new View.OnClickListener() {
            @Override public void onClick(View v) { sp.edit().putBoolean("gbart_done", true).apply(); refresh(); }
        });

        // 3 EasyAbroad
        LinearLayout c3 = card(root, "s_easy");
        stEasy = text(c3, "");
        bEasy1 = btn(c3, Tr.t("easy_open_ag"), new View.OnClickListener() {
            @Override public void onClick(View v) { openUri("store://appgallery.huawei.com/app/detail?id=com.easy.hmos.abroad"); }
        });
        bEasy2 = btn(c3, Tr.t("easy_start"), new View.OnClickListener() {
            @Override public void onClick(View v) { openUri("abroad://easy.hmos.abroad/open"); }
        });
        bEasy3 = btn(c3, Tr.t("easy_ack"), new View.OnClickListener() {
            @Override public void onClick(View v) { sp.edit().putBoolean("easy_ack", true).apply(); refresh(); }
        });

        // 4 Google-Apps
        LinearLayout c4 = card(root, "s_apps");
        stApps = text(c4, "");
        pbApps = new android.widget.ProgressBar(this);
        pbApps.setIndeterminate(true);
        pbApps.setVisibility(View.GONE);
        c4.addView(pbApps);
        for (int i = 0; i < GAPPS.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox cb = new CheckBox(this);
            cb.setText(GAPPS[i][0]);
            cb.setChecked(sp.getBoolean("sel_" + GAPPS[i][1], true));
            final String key = "sel_" + GAPPS[i][1];
            cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) { sp.edit().putBoolean(key, on).apply(); refresh(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            row.addView(cb, lp);
            TextView st = new TextView(this);
            st.setTextSize(13);
            row.addView(st);
            cbs[i] = cb;
            appSt[i] = st;
            c4.addView(row);
        }
        bApps = btn(c4, "", new View.OnClickListener() {
            @Override public void onClick(View v) { startApps(); }
        });

        // 5 Aurora
        LinearLayout c5 = card(root, "s_aurora");
        stMg = text(c5, "");
        bMg = btn(c5, Tr.t("mg_open"), new View.OnClickListener() {
            @Override public void onClick(View v) { openMicrog(); }
        });
        bMgCheck = btn(c5, Tr.t("mg_check"), new View.OnClickListener() {
            @Override public void onClick(View v) { probeMicrog(); }
        });
        bMgAck = btn(c5, Tr.t("mg_ack"), new View.OnClickListener() {
            @Override public void onClick(View v) { sp.edit().putBoolean("mg_ack", true).apply(); refresh(); }
        });
        stAurora = text(c5, "");
        pbAurora = new android.widget.ProgressBar(this);
        pbAurora.setIndeterminate(true);
        pbAurora.setVisibility(View.GONE);
        c5.addView(pbAurora);
        bAurora = btn(c5, Tr.t("aurora_install"), new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!overlayOk()) { Seq.log(Tr.t("apps_need")); return; }
                Seq.status(AURORA_PKG, "running");
                Seq.verbose = false;
                SeqService.start(MainActivity.this, new String[]{AURORA_PKG}, "aurora", true);
            }
        });
        bAuroraFile = btn(c5, Tr.t("aurora_file"), new View.OnClickListener() {
            @Override public void onClick(View v) { saveAuroraFile(); }
        });
        stAurPerm = text(c5, Tr.t("aurora_perm_hint"));
        bAurP1 = btn(c5, Tr.t("aurora_p_install"), new View.OnClickListener() {
            @Override public void onClick(View v) { Perm.openInstallPermission(MainActivity.this, AURORA_PKG); }
        });
        bAurP2 = btn(c5, Tr.t("aurora_p_notif"), new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, AURORA_PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Throwable t) { Seq.log("Aurora-Benachrichtigungen: " + t); }
            }
        });
        bAurAck = btn(c5, Tr.t("aurora_ack"), new View.OnClickListener() {
            @Override public void onClick(View v) { sp.edit().putBoolean("aurora_ack", true).apply(); refresh(); }
        });

        // 6 Google-Anmeldung
        LinearLayout c6 = card(root, "s_login");
        stLogin = text(c6, "");
        bLogin = btn(c6, Tr.t("login_btn"), new View.OnClickListener() {
            @Override public void onClick(View v) { addGoogleAccount(); }
        });

        // 7 Kontakte
        LinearLayout c7 = card(root, "s_contacts");
        stContacts = text(c7, "");
        pbContacts = new android.widget.ProgressBar(this);
        pbContacts.setIndeterminate(true);
        pbContacts.setVisibility(View.GONE);
        c7.addView(pbContacts);
        bContacts = btn(c7, Tr.t("contacts_btn"), new View.OnClickListener() {
            @Override public void onClick(View v) { contactsFlow(); }
        });
        bContactsSkip = btn(c7, Tr.t("btn_skip"), new View.OnClickListener() {
            @Override public void onClick(View v) { sp.edit().putBoolean("contacts_skip", true).apply(); refresh(); }
        });

        // 8 Kalender
        LinearLayout c8 = card(root, "s_cal");
        stCal = text(c8, Tr.t("cal_hint"));
        bCal1 = btn(c8, Tr.t("cal_1"), new View.OnClickListener() { @Override public void onClick(View v) { calExport(); } });
        bCal2 = btn(c8, Tr.t("cal_2"), new View.OnClickListener() { @Override public void onClick(View v) { calFindZip(); } });
        bCal3 = btn(c8, Tr.t("cal_3"), new View.OnClickListener() { @Override public void onClick(View v) { calOpenNext(); } });
        bCalSkip = btn(c8, Tr.t("btn_skip"), new View.OnClickListener() {
            @Override public void onClick(View v) { sp.edit().putBoolean("cal_skip", true).apply(); refresh(); }
        });

        TextView foot = new TextView(this);
        foot.setText(Tr.t("footer"));
        foot.setTextSize(12);
        foot.setGravity(Gravity.CENTER);
        foot.setAlpha(0.6f);
        foot.setPadding(0, dp(8), 0, dp(16));
        foot.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (++hxTaps >= 11) { hxTaps = 0; android.widget.Toast.makeText(MainActivity.this, Tr.t("hx_done"), android.widget.Toast.LENGTH_LONG).show(); }
            }
        });
        root.addView(foot);

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Seq.fg = true;
        Seq.lastResume = System.currentTimeMillis();
        Seq.taskId = getTaskId();
        Seq.cur = this;
        Seq.refresher = new Runnable() { @Override public void run() { refresh(); } };
        if (leftForPerm) { leftForPerm = false; returnedUngranted = true; if (lastPerm == 7) backFromPopup = true; }
        if (backFromPopup && !popupOk() && !popupDlg) {
            backFromPopup = false;
            popupDlg = true;
            h.postDelayed(new Runnable() { @Override public void run() { askPopupDone(); } }, 400);
        }
        if (leftForMg) { leftForMg = false; h.postDelayed(new Runnable() { @Override public void run() { probeMicrog(); } }, 500); }
        h.removeCallbacks(tick);
        h.post(tick);
    }

    // ---------- microG: Installation erlauben ----------
    private boolean mgOk() { return mgState == 1 || sp.getBoolean("mg_ok", false) || sp.getBoolean("mg_ack", false); }

    private void openMicrog() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName("com.android.vending", "org.microg.vending.ui.MainActivity");
            leftForMg = true;
            startActivity(i);
        } catch (Throwable t) {
            leftForMg = false;
            Seq.log("microG-Einstellungen: " + t);
        }
    }

    /** Fragt vending ohne Dateien an: "Install is disabled" = Schalter aus, jede andere Antwort = Schalter an. */
    private void probeMicrog() {
        if (mgProbing) return;
        mgProbing = true;
        mgProbeAt = System.currentTimeMillis();
        try {
            Intent i = new Intent("org.microg.vending.action.INSTALL_PACKAGE");
            i.setPackage("com.android.vending");
            i.setType("application/vnd.android.package-archive");
            startActivityForResult(i, 4712);
            final long t0 = mgProbeAt;
            h.postDelayed(new Runnable() { @Override public void run() { if (mgProbing && mgProbeAt == t0) { mgProbing = false; mgState = 0; refresh(); } } }, 15000);
        } catch (Throwable t) {
            mgProbing = false;
            mgState = 0;
            Seq.log("microG-Pruefung: " + t);
        }
        refresh();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 4712) return;
        mgProbing = false;
        String err = null;
        try { if (data != null) err = data.getStringExtra("error"); } catch (Throwable ignored) {}
        if (err == null) mgState = 0;
        else if (err.toLowerCase().contains("disabled")) mgState = 2;
        else { mgState = 1; sp.edit().putBoolean("mg_ok", true).apply(); }
        Seq.log("microG-Pruefung: " + (mgState == 1 ? "Installation erlaubt" : mgState == 2 ? "Installation ausgeschaltet" : "keine Antwort") + (err != null ? " (" + err + ")" : ""));
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        Seq.fg = false;
        h.removeCallbacks(tick);
    }

    private void openUri(String uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) { Seq.log("Link " + uri + ": " + t); }
    }

    private String mark(boolean ok) { return ok ? "✔ " : "✘ "; }

    /** Erste fehlende Pflicht-Berechtigung: 0 Ueberlagerung, 1 Installationen, 2 alle Dateien, 3 Benachrichtigungen; -1 = alle erteilt. */
    private int curPerm() {
        if (!overlayOk()) return 0;
        if (!installOk()) return 1;
        if (!filesOk()) return 2;
        if (!notifOk()) return 3;
        if (!contactsOk()) return 4;
        if (!calendarOk()) return 5;
        if (!battOk()) return 6;
        if (!popupOk()) return 7;
        return -1;
    }

    private String permLabel(int i) {
        return Tr.t(new String[]{"r_overlay", "r_install", "r_files", "r_notif", "r_contacts", "r_calendar", "r_batt", "r_popup"}[i]);
    }

    /** Oeffnet die App-Info-Seite von Gclick (Container-Einstellungen). Aufrufe an HarmonyOS-Schemas fuehren nur zu einem allgemeinen "Oeffnen mit"-Dialog. */
    /** "Popups im Hintergrund" ist nicht lesbar: nach der Rueckkehr von der Einstellungsseite direkt nachfragen. */
    private void askPopupDone() {
        try {
            new android.app.AlertDialog.Builder(this)
                .setMessage(Tr.t("popup_q"))
                .setPositiveButton(Tr.t("yes"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        sp.edit().putBoolean("popup_ack", true).commit();
                        Seq.log("Popups im Hintergrund bestaetigt");
                        refresh();
                    }
                })
                .setNegativeButton(Tr.t("no"), null)
                .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(android.content.DialogInterface d) { popupDlg = false; }
                })
                .show();
        } catch (Throwable t) { popupDlg = false; Seq.log("Rueckfrage: " + t); }
    }

    private void openHostSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) { Seq.log("App-Info nicht startbar: " + t); }
    }

    private void openPerm(int i) {
        leftForPerm = true;
        lastPerm = i;
        try {
            switch (i) {
                case 0: startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); break;
                case 1: startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); break;
                case 2: startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); break;
                case 3:
                    if (notifTaps++ == 0) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                    else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                    break;
                case 4:
                    if (contactTaps++ == 0) requestPermissions(new String[]{"android.permission.READ_CONTACTS", "android.permission.GET_ACCOUNTS"}, 2);
                    else openHostSettings();
                    break;
                case 5:
                    if (calTaps++ == 0) requestPermissions(new String[]{"android.permission.READ_CALENDAR"}, 3);
                    else openHostSettings();
                    break;
                case 6: startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); break;
                default: openHostSettings(); break;
            }
        } catch (Throwable t) { Seq.log("Berechtigung " + i + ": " + t); }
    }

    private void refresh() {
        if (stRights == null) return;
        boolean ov = overlayOk(), nt = notifOk(), bt = battOk(), ins = installOk(), fl = filesOk(), po = popupOk();
        stRights.setText(mark(ov) + Tr.t("r_overlay") + "\n" + mark(ins) + Tr.t("r_install") + "\n" + mark(fl) + Tr.t("r_files")
            + "\n" + mark(nt) + Tr.t("r_notif") + "\n" + mark(contactsOk()) + Tr.t("r_contacts") + "\n" + mark(calendarOk()) + Tr.t("r_calendar")
            + "\n" + mark(bt) + Tr.t("r_batt") + "\n" + mark(po) + Tr.t("r_popup"));
        int cur = curPerm();
        boolean rightsDone = cur < 0;   // alle sechs Freigaben muessen erteilt sein
        if (cur >= 0) {
            if (cur != autoPerm && resumed) {          // naechste Berechtigung: Seite sofort selbst oeffnen
                autoPerm = cur;
                returnedUngranted = false;
                final int go = cur;
                h.postDelayed(new Runnable() { @Override public void run() { if (resumed) openPerm(go); } }, 500);
            }
            stPermNow.setText(Tr.f("perm_now", permLabel(cur)) + (returnedUngranted && cur == autoPerm ? "\n" + Tr.t("perm_again") : ""));
        }
        stPermNow.setVisibility(cur >= 0 ? View.VISIBLE : View.GONE);
        bPermGo.setVisibility(cur >= 0 && cur != 7 ? View.VISIBLE : View.GONE);
        // "Popups im Hintergrund" ist in den HarmonyOS-Einstellungen und nicht lesbar: eigene Bestaetigung
        bPopup.setVisibility(cur == 7 ? View.VISIBLE : View.GONE);
        bPopupAck.setVisibility(cur == 7 ? View.VISIBLE : View.GONE);
        if (cur == 7) stPermNow.setText(Tr.f("perm_now", permLabel(7)) + "\n" + Tr.t("r_popup_hint"));
        if (cur == 4 || cur == 5) stPermNow.setText(stPermNow.getText() + "\n" + Tr.t("r_cc_hint"));
        bBatt.setVisibility(View.GONE);

        boolean gbDone = !GBART_RELEASED || sp.getBoolean("gbart_done", false);
        if (!GBART_RELEASED) {
            stGbart.setText(Tr.t("gbart_soon"));
            bGbart1.setVisibility(View.GONE);
            bGbart2.setVisibility(View.GONE);
        } else {
            stGbart.setText(gbDone ? "✔" : "");
            bGbart1.setVisibility(gbDone ? View.GONE : View.VISIBLE);
            bGbart2.setVisibility(gbDone ? View.GONE : View.VISIBLE);
        }

        boolean eo = easyOk();
        stEasy.setText(eo ? "✔ EasyAbroad" : Tr.t("easy_hint"));
        bEasy1.setVisibility(eo ? View.GONE : View.VISIBLE);
        bEasy2.setVisibility(eo ? View.GONE : View.VISIBLE);
        bEasy3.setVisibility(eo ? View.GONE : View.VISIBLE);

        int todo = 0;
        for (int i = 0; i < GAPPS.length; i++) {
            String pkg = GAPPS[i][1];
            boolean inst = has(pkg);
            String s = Seq.STATUS.get(pkg);
            String label;
            if (inst) label = Tr.t(s != null && s.equals("done") ? "st_done" : "st_skipped");
            else if ("running".equals(s)) label = Tr.t("st_running");
            else if ("failed".equals(s)) label = Tr.t("st_failed");
            else label = Tr.t("st_open");
            appSt[i].setText(label);
            cbs[i].setEnabled(!inst);
            if (!inst && cbs[i].isChecked()) todo++;
        }
        boolean appsDone = todo == 0;
        stApps.setText(Tr.t("apps_first"));
        bApps.setText(todo > 0 ? Tr.f("apps_install", todo) : Tr.t("apps_none"));
        bApps.setEnabled(todo > 0 && eo && ov);

        boolean au = has(AURORA_PKG);
        boolean mgOk = mgOk();
        if (!mgOk && appsDone && !mgProbing && mgProbeAt == 0 && has("com.android.vending")) probeMicrog();
        stMg.setVisibility(mgOk ? View.GONE : View.VISIBLE);
        stMg.setText(mgState == 2 ? Tr.t("mg_off") + "\n" + Tr.t("mg_hint") : (mgState == 0 && mgProbeAt > 0 && !mgProbing ? Tr.t("mg_unknown") + "\n" + Tr.t("mg_hint") : Tr.t("mg_hint")));
        bMg.setVisibility(mgOk ? View.GONE : View.VISIBLE);
        bMgCheck.setVisibility(mgOk ? View.GONE : View.VISIBLE);
        bMgCheck.setEnabled(!mgProbing);
        bMgAck.setVisibility(!mgOk && mgState == 0 && mgProbeAt > 0 && !mgProbing ? View.VISIBLE : View.GONE);
        String as = Seq.STATUS.get(AURORA_PKG);
        stAurora.setVisibility(mgOk ? View.VISIBLE : View.GONE);
        stAurora.setText(au ? Tr.t("st_done") : ("running".equals(as) ? Tr.t("st_running") : ("failed".equals(as) ? Tr.t("st_failed") + "\n" + Tr.t("aurora_hint") : Tr.t("aurora_hint"))));
        bAurora.setVisibility(au || !mgOk ? View.GONE : View.VISIBLE);
        bAurora.setEnabled(ov);
        bAuroraFile.setVisibility(au || !mgOk ? View.GONE : View.VISIBLE);
        boolean aurPermOk = mgOk && au && (aurInstallOk() || sp.getBoolean("aurora_ack", false));
        boolean aurPermShow = mgOk && au && !aurPermOk;
        stAurPerm.setVisibility(aurPermShow ? View.VISIBLE : View.GONE);
        bAurP1.setVisibility(aurPermShow ? View.VISIBLE : View.GONE);
        bAurP2.setVisibility(aurPermShow ? View.VISIBLE : View.GONE);
        bAurAck.setVisibility(aurPermShow ? View.VISIBLE : View.GONE);

        pbAurora.setVisibility("running".equals(as) && !au ? View.VISIBLE : View.GONE);
        boolean anyRunning = false;
        for (int i = 0; i < GAPPS.length; i++) if ("running".equals(Seq.STATUS.get(GAPPS[i][1])) && !has(GAPPS[i][1])) anyRunning = true;
        pbApps.setVisibility(anyRunning ? View.VISIBLE : View.GONE);

        boolean lo = loginOk();
        stLogin.setText(lo ? Tr.t("login_ok") : Tr.t("login_hint"));
        bLogin.setVisibility(lo ? View.GONE : View.VISIBLE);

        boolean contactsDone = sp.getBoolean("contacts_done", false) || sp.getBoolean("contacts_skip", false);
        String cs = Seq.STATUS.get("contacts");
        int cn = sp.getInt("contacts_n", 0);
        if (cs != null) stContacts.setText(cs);
        else if (sp.getBoolean("contacts_done", false)) stContacts.setText(Tr.f("contacts_done", cn));
        else if (sp.getBoolean("contacts_skip", false)) stContacts.setText(Tr.t("skipped"));
        else stContacts.setText(Tr.t("contacts_hint"));
        bContacts.setEnabled(lo && !contactsRunning);
        pbContacts.setVisibility(contactsRunning ? View.VISIBLE : View.GONE);
        bContactsSkip.setVisibility(contactsDone ? View.GONE : View.VISIBLE);
        boolean calDone = sp.getBoolean("cal_done", false) || sp.getBoolean("cal_skip", false);
        bCalSkip.setVisibility(calDone ? View.GONE : View.VISIBLE);

        // feste Reihenfolge: ein Schritt wird erst sichtbar, wenn alle davor erledigt sind
        boolean[] done = {rightsDone, gbDone, eo, appsDone, aurPermOk, lo, contactsDone, calDone};
        boolean show = true;
        String next = null;
        for (int i = 0; i < 8; i++) {
            boolean visible = show && !(i == 1 && !GBART_RELEASED);
            cards[i].setVisibility(visible ? View.VISIBLE : View.GONE);
            boolean collapsed = visible && done[i] && !expanded[i];
            bodies[i].setVisibility(collapsed ? View.GONE : View.VISIBLE);
            titles[i].setText((visible && done[i] ? "✔ " : "") + Tr.t(titleKeys[i]));
            if (show && !done[i] && next == null && !(i == 1 && !GBART_RELEASED)) {
                String[] keys = {"s_rights", "s_gbart", "s_easy", "s_apps", "s_aurora", "s_login", "s_contacts", "s_cal"};
                next = Tr.t(keys[i]);
            }
            show = show && done[i];
        }
        tvNext.setText(next == null ? Tr.t("all_done") : Tr.f("next", next));
    }

    // ---------- Google-Apps ----------
    private void startApps() {
        ArrayList<String> p = new ArrayList<String>();
        for (int i = 0; i < GAPPS.length; i++) if (cbs[i].isChecked() && !has(GAPPS[i][1])) { p.add(GAPPS[i][1]); Seq.status(GAPPS[i][1], null); }
        if (p.isEmpty()) return;
        Seq.verbose = false;
        SeqService.start(this, p.toArray(new String[0]), "apps", true);
    }

    // ---------- Aurora-Notloesung ----------
    private void saveAuroraFile() {
        new Thread(new Runnable() { @Override public void run() {
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "AStore.apk");
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                InputStream in = getAssets().open("astore.apk");
                OutputStream os = getContentResolver().openOutputStream(u);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                in.close();
                os.close();
                Seq.log(Tr.t("aurora_file_ok"));
            } catch (Throwable t) { Seq.log("Aurora-Datei: " + t); }
        } }).start();
    }

    // ---------- Google-Konto ----------
    private void addGoogleAccount() {
        try {
            Intent i = new Intent(Settings.ACTION_ADD_ACCOUNT);
            i.putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[]{"com.google"});
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Seq.log("Konto hinzufuegen: " + t);
            try { startActivity(new Intent(Settings.ACTION_SYNC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable t2) { Seq.log("Konten: " + t2); }
        }
    }

    // ---------- Kontakte ----------
    private int countContacts() {
        android.database.Cursor c = null;
        try {
            c = getContentResolver().query(android.provider.ContactsContract.Contacts.CONTENT_URI, new String[]{"_id"}, null, null, null);
            return c == null ? -1 : c.getCount();
        } catch (Throwable t) { return -1; } finally { if (c != null) c.close(); }
    }

    private void contactsFlow() {
        if (contactsRunning) return;
        if (checkSelfPermission("android.permission.READ_CONTACTS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.READ_CONTACTS", "android.permission.GET_ACCOUNTS"}, 2);
            Seq.log(Tr.t("contacts_perm"));
            return;
        }
        contactsRunning = true;
        Seq.status("contacts", Tr.t("contacts_start"));
        refresh();
        new Thread(new Runnable() { @Override public void run() {
            try {
                android.accounts.Account[] acc = android.accounts.AccountManager.get(MainActivity.this).getAccountsByType("com.google");
                if (acc.length == 0) { Seq.status("contacts", "✘ " + Tr.t("contacts_need")); return; }
                long t0 = System.currentTimeMillis();
                int last = -1, stable = 0;
                long lastReq = 0;
                while (System.currentTimeMillis() - t0 < 180000) {
                    if (System.currentTimeMillis() - lastReq > 30000) {
                        lastReq = System.currentTimeMillis();
                        for (android.accounts.Account a : acc) {
                            try { android.content.ContentResolver.setIsSyncable(a, "com.android.contacts", 1); } catch (Throwable ignored) {}
                            try { android.content.ContentResolver.setSyncAutomatically(a, "com.android.contacts", true); } catch (Throwable ignored) {}
                            try {
                                android.os.Bundle b = new android.os.Bundle();
                                b.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true);
                                b.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                                android.content.ContentResolver.requestSync(a, "com.android.contacts", b);
                            } catch (Throwable ignored) {}
                        }
                    }
                    int c = countContacts();
                    long waited = (System.currentTimeMillis() - t0) / 1000;
                    String msg = Tr.f("contacts_sync", c, waited);
                    if (c <= 0 && waited >= 20) msg += "\n" + Tr.t("contacts_wait_perm");
                    Seq.status("contacts", msg);
                    if (c > 0 && c == last) { stable++; if (stable >= 3) break; } else stable = 0;
                    last = c;
                    Thread.sleep(4000);
                }
                if (last <= 0) { Seq.status("contacts", "✘ " + Tr.t("contacts_none")); return; }
                Seq.status("contacts", Tr.f("contacts_export", last));
                ArrayList<String> keys = new ArrayList<String>();
                android.database.Cursor c = getContentResolver().query(android.provider.ContactsContract.Contacts.CONTENT_URI, new String[]{"lookup"}, null, null, null);
                if (c != null) { while (c.moveToNext()) keys.add(c.getString(0)); c.close(); }
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "oneclick_contacts.vcf");
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/x-vcard");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                final Uri out = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                OutputStream os = getContentResolver().openOutputStream(out);
                for (int from = 0; from < keys.size(); from += 100) {
                    StringBuilder ks = new StringBuilder();
                    for (int k = from; k < Math.min(keys.size(), from + 100); k++) { if (ks.length() > 0) ks.append(':'); ks.append(keys.get(k)); }
                    Uri vu = Uri.withAppendedPath(android.provider.ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode(ks.toString()));
                    InputStream in = getContentResolver().openInputStream(vu);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    in.close();
                }
                os.close();
                sp.edit().putBoolean("contacts_done", true).putInt("contacts_n", keys.size()).apply();
                Seq.status("contacts", Tr.f("contacts_open", keys.size()));
                Seq.log("Kontakte: " + keys.size() + " exportiert");
                runOnUiThread(new Runnable() { @Override public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(out, "text/x-vcard");
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Throwable t) { Seq.log("Kontakte oeffnen: " + t); }
                } });
            } catch (Throwable t) {
                Seq.status("contacts", "✘ " + t);
            } finally {
                contactsRunning = false;
            }
        } }).start();
    }

    // ---------- Kalender (optional) ----------
    private final ArrayList<Uri> icsUris = new ArrayList<Uri>();
    private int icsNext = 0;

    private void calExport() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://calendar.google.com/calendar/u/0/r/settings/export"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setPackage("com.android.chrome");
        try { startActivity(i); }
        catch (Throwable t) { i.setPackage(null); try { startActivity(i); } catch (Throwable t2) { Seq.log("Chrome: " + t2); } }
    }

    private void calFindZip() {
        if (Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            Seq.log(Tr.t("cal_perm"));
            try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); } catch (Throwable t) { Seq.log("Dateien-Recht: " + t); }
            return;
        }
        new Thread(new Runnable() { @Override public void run() {
            try {
                File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                File[] fs = dir.listFiles();
                if (fs == null) { Seq.log("Download-Ordner nicht lesbar"); return; }
                ArrayList<File> zips = new ArrayList<File>();
                for (File f : fs) if (f.isFile() && f.getName().toLowerCase().endsWith(".zip") && f.length() < 300L * 1024 * 1024) zips.add(f);
                java.util.Collections.sort(zips, new java.util.Comparator<File>() {
                    @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
                });
                for (int k = 0; k < zips.size() && k < 15; k++) {
                    File z = zips.get(k);
                    boolean ics = false;
                    try {
                        java.util.zip.ZipFile zf = new java.util.zip.ZipFile(z);
                        java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
                        while (en.hasMoreElements()) if (en.nextElement().getName().toLowerCase().endsWith(".ics")) { ics = true; break; }
                        zf.close();
                    } catch (Throwable t) { continue; }
                    if (ics) { unzipCalendar(new java.io.FileInputStream(z)); return; }
                }
                Seq.log(Tr.t("cal_nozip"));
            } catch (Throwable t) { Seq.log("ZIP-Suche: " + t); }
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
                if (ev == 0) continue;
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "oneclick_kalender_" + total + ".ics");
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/calendar");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                Uri out = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                OutputStream os = getContentResolver().openOutputStream(out);
                os.write(data);
                os.close();
                icsUris.add(out);
                Seq.log(Tr.f("cal_events", total, ev));
            }
            zin.close();
            if (icsUris.isEmpty()) { Seq.log(Tr.t("cal_noevents")); return; }
            runOnUiThread(new Runnable() { @Override public void run() { calOpenNext(); } });
        } catch (Throwable t) { Seq.log("Entpacken: " + t); }
    }

    private void calOpenNext() {
        if (icsNext >= icsUris.size()) { Seq.log(Tr.t("cal_nomore")); return; }
        Uri u = icsUris.get(icsNext++);
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, "text/calendar");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            sp.edit().putBoolean("cal_done", true).apply();
            Seq.log(Tr.f("cal_file", icsNext, icsUris.size()));
        } catch (Throwable t) { Seq.log("Kalender oeffnen: " + t); }
    }
}
