package ru.novatv.launcher;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;

/** Notification contents stay in process memory and are cleared when access is disconnected. */
public class NotificationAccess extends NotificationListenerService {
  static final class Item {
    final String title, text, pkg;
    final boolean ongoing, groupSummary;
    final long postTime;
    final android.app.PendingIntent contentIntent;

    Item(String title, String text, String pkg, boolean ongoing, boolean groupSummary, long postTime, android.app.PendingIntent contentIntent) {
      this.title = title;
      this.text = text;
      this.pkg = pkg;
      this.ongoing = ongoing;
      this.groupSummary = groupSummary;
      this.postTime = postTime;
      this.contentIntent = contentIntent;
    }
  }

  private static final Map<String, Item> items = new LinkedHashMap<>();

  static synchronized List<Item> snapshot(boolean showOngoing) {
    Map<List<String>, Item> distinct = new LinkedHashMap<>();
    for (Item item : items.values()) {
      if (item.groupSummary || (!showOngoing && item.ongoing)) continue;
      distinct.put(Arrays.asList(item.pkg, item.title, item.text), item);
    }
    return new ArrayList<>(distinct.values());
  }

  @Override
  public void onListenerConnected() {
    synchronized (NotificationAccess.class) {
      items.clear();
    }
    try {
      for (StatusBarNotification item : getActiveNotifications()) onNotificationPosted(item);
    } catch (SecurityException ignored) {
    }
  }

  @Override
  public void onNotificationPosted(StatusBarNotification item) {
    Notification notification = item.getNotification();
    String title = field(notification, Notification.EXTRA_TITLE);
    String text = field(notification, Notification.EXTRA_TEXT);
    if (text.isEmpty()) text = field(notification, Notification.EXTRA_SUB_TEXT);
    if ("ContentTitle".equalsIgnoreCase(title)) title = "";
    synchronized (NotificationAccess.class) {
      items.put(item.getKey(), new Item(title, text, item.getPackageName(), item.isOngoing(),
          (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0, item.getPostTime(), notification.contentIntent));
    }
  }

  private static String field(Notification notification, String key) {
    CharSequence text = notification.extras == null ? null : notification.extras.getCharSequence(key);
    return text == null ? "" : text.toString().trim();
  }

  @Override
  public void onNotificationRemoved(StatusBarNotification item) {
    synchronized (NotificationAccess.class) {
      items.remove(item.getKey());
    }
  }

  @Override
  public void onDestroy() {
    synchronized (NotificationAccess.class) {
      items.clear();
    }
    super.onDestroy();
  }

  @Override
  public void onListenerDisconnected() {
    synchronized (NotificationAccess.class) {
      items.clear();
    }
  }
}
