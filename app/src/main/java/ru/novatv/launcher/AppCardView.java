package ru.novatv.launcher;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

final class AppCardView extends FrameLayout {
  final LinearLayout contents;
  private final MainActivity host;
  private final int radius;
  private GradientDrawable focusOutline;
  private int focusColor;

  AppCardView(MainActivity host, int radius, int color) {
    super(host);
    this.host = host;
    this.radius = radius;
    setFocusable(true);
    setOutlineProvider(null);
    focusColor = host.accent();
    focusOutline = host.shape(Color.TRANSPARENT, radius);
    focusOutline.setStroke(host.dp(2), focusColor);
    focusOutline.setAlpha(0);
    setForeground(focusOutline);
    contents = host.vertical();
    contents.setGravity(Gravity.CENTER);
    contents.setBackground(host.shape(color, radius));
    contents.setClipToOutline(true);
    addView(contents, new LayoutParams(-1, -1));
  }

  void setCardColor(int color) {
    contents.setBackground(host.shape(color, radius));
  }

  void showFocus(boolean focused, boolean zoom, int color) {
    showFocus(focused, zoom ? 1.06f : 1f, color);
  }

  void showFocus(boolean focused, float zoom, int color) {
    float scale = focused ? zoom : 1f;
    setScaleX(scale);
    setScaleY(scale);
    // Keep the focused card above its neighbours without drawing a dynamic elevation shadow.
    setTranslationZ(focused ? 1f : 0f);
    if (focused && (focusOutline == null || focusColor != color)) {
      focusColor = color;
      focusOutline = host.shape(Color.TRANSPARENT, radius);
      focusOutline.setStroke(host.dp(2), color);
    }
    if (focusOutline != null) {
      focusOutline.setAlpha(focused ? 255 : 0);
      if (getForeground() != focusOutline) setForeground(focusOutline);
    }
  }

  void addArtwork(Drawable artwork, int height, boolean banner) {
    FrameLayout box = new FrameLayout(host);
    ImageView image = new ImageView(host);
    image.setImageDrawable(artwork);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    int size = banner ? -1 : host.dp(Math.max(1, Math.min(height - 6, Math.round(height * .68f))));
    box.addView(image, new LayoutParams(size, size, Gravity.CENTER));
    contents.addView(box, host.lp(-1, height));
  }
}
