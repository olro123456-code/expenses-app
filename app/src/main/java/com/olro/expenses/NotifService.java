package com.olro.expenses;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotifService extends NotificationListenerService {

    private static boolean blocked(String pkg) {
        return pkg.equals("android")
                || pkg.startsWith("com.android.systemui")
                || pkg.startsWith("com.whatsapp")
                || pkg.startsWith("org.telegram")
                || pkg.startsWith("com.facebook")
                || pkg.startsWith("com.instagram")
                || pkg.equals("com.google.android.youtube")
                || pkg.equals("com.google.android.gm")
                || pkg.equals("com.google.android.apps.maps");
    }

    private static String str(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            if (pkg == null || pkg.equals(getPackageName()) || blocked(pkg)) return;
            Notification n = sbn.getNotification();
            if (n == null) return;
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle e = n.extras;
            if (e == null) return;
            String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
            String text = str(e.getCharSequence(Notification.EXTRA_TEXT));
            String big = str(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
            StringBuilder sb = new StringBuilder();
            if (!title.isEmpty()) sb.append(title).append('\n');
            if (!text.isEmpty()) sb.append(text);
            if (!big.isEmpty() && !big.equals(text)) sb.append('\n').append(big);
            String all = sb.toString().trim();
            if (all.length() < 5) return;
            NotifQueue.put(this, pkg, all, sbn.getPostTime());
            MainActivity.ping();
        } catch (Exception ignored) {
        }
    }
}
