package ru.novatv.launcher;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

final class StatusBarView extends LinearLayout {
  private final MainActivity host;
  private final SharedPreferences preferences;
  private final int color;

  StatusBarView(MainActivity host) {
    super(host);
    this.host = host;
    preferences = host.getPreferences(0);
    color = preferences.getInt("status_color", 0xfff4fdff);
    setGravity(Gravity.CENTER_VERTICAL);
    int pad = host.dp(preferences.getInt("status_padding", 8));
    setPadding(pad, 0, pad, 0);
  }

  static float radius(int width, int height, int percent) {
    return Math.min(width, height) * Appearance.clamp(percent, 0, 100) / 200f;
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    int tint = backgroundColor(preferences);
    GradientDrawable background = new GradientDrawable();
    background.setColor(tint);
    background.setCornerRadius(radius(width, height, preferences.getInt("status_radius", 100)));
    setBackground(background);
  }

  static int backgroundColor(SharedPreferences preferences) {
    int tint = preferences.getInt("status_background", 0xb2101a30);
    if (preferences.getBoolean("status_background_argb", false)) return tint;
    int transparency = Appearance.clamp(preferences.getInt("status_transparency", 30), 0, 100);
    return ((255 * (100 - transparency) / 100) << 24) | (tint & 0x00ffffff);
  }

  TextView item(String text, String description, int icon, String key) {
    int mode = preferences.getInt(key + "_mode", preferences.getInt("status_label_mode", 2));
    TextView view = host.label(mode == 0 ? "" : text, preferences.getInt("status_size", 15), color);
    view.setGravity(Gravity.CENTER);
    view.setSingleLine(true);
    int padding = host.dp(12);
    view.setPadding(padding, 0, padding, 0);
    view.setContentDescription(description);
    if (icon != 0 && mode != 1) {
      Drawable image = host.getDrawable(icon).mutate();
      int size = host.dp(preferences.getInt("status_icon_size", 20));
      image.setBounds(0, 0, size, size);
      view.setCompoundDrawablesRelative(image, null, null, null);
      view.setCompoundDrawablePadding(mode == 0 ? 0 : host.dp(5));
    }
    style(view, key, false);
    LayoutParams params = new LayoutParams(-2, -1);
    if (getChildCount() != 0) params.setMarginStart(host.dp(preferences.getInt("status_gap", 8)));
    addView(view, params);
    return view;
  }

  void style(TextView button, String key, boolean focused) {
    int tint = focused ? 0xff10182a : preferences.getInt(key + "_color", color);
    button.setTextColor(tint);
    button.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(tint));
    button.setBackground(host.shape(focused ? 0xffd9eaff : 0x99172131, 14));
  }
}
