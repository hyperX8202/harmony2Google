package com.ab.oneclick;

import android.app.Activity;

/** Gemeinsamer Zustand: Protokoll, Status je Schritt, Vordergrund-Status der Activity. */
final class Seq {
    static final StringBuilder LOG = new StringBuilder();
    static final java.util.concurrent.ConcurrentHashMap<String, String> STATUS = new java.util.concurrent.ConcurrentHashMap<String, String>();
    static volatile boolean fg;
    static volatile long lastResume;
    static volatile int taskId = -1;
    static volatile boolean running;
    static volatile boolean verbose;
    static volatile long t0;
    static volatile Activity cur;
    static volatile Runnable refresher;

    static synchronized void log(String m) {
        LOG.insert(0, m + "\n");
        if (LOG.length() > 8000) LOG.setLength(8000);
        notifyUi();
    }

    static void status(String key, String value) {
        if (value == null) STATUS.remove(key); else STATUS.put(key, value);
        notifyUi();
    }

    static void notifyUi() {
        final Activity a = cur;
        final Runnable r = refresher;
        if (a != null && r != null) a.runOnUiThread(r);
    }

    static synchronized String text() { return LOG.toString(); }
}
