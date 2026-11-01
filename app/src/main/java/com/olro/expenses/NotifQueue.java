package com.olro.expenses;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Notifications wait here until the app page reads them. */
public final class NotifQueue {
    private static final String PREFS = "notif_queue";
    private static final String KEY = "items";
    private static final int MAX = 200;

    private NotifQueue() {}

    public static synchronized void put(Context ctx, String pkg, String text, long when) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
            JSONObject o = new JSONObject();
            o.put("p", pkg);
            o.put("t", text);
            o.put("w", when);
            arr.put(o);
            if (arr.length() > MAX) {
                JSONArray cut = new JSONArray();
                for (int i = arr.length() - MAX; i < arr.length(); i++) cut.put(arr.get(i));
                arr = cut;
            }
            sp.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static synchronized String drain(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = sp.getString(KEY, "[]");
        sp.edit().putString(KEY, "[]").apply();
        return s;
    }
}
