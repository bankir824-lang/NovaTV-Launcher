package ru.novatv.launcher;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** A remote-friendly settings stack over the launcher home screen. */
final class TvSettingsPanel extends Dialog {
  static final float WIDTH_FRACTION = 1f / 3f;
  private static final int BOTTOM_MARGIN_DP = 16;

  interface Page {
    void build(TvSettingsPanel panel);
  }

  private final MainActivity host;
  private static final class PageState {
    final Page page;
    Object focusKey;
    int scrollY;
    int focusIndex;

    PageState(Page page) {
      this.page = page;
    }
  }

  private final ArrayDeque<PageState> pages = new ArrayDeque<>();
  private ScrollView pageScroll;
  private LinearLayout body;
  private LinearLayout heading;
  private View first;
  private String currentTitle = "";
  private boolean parked;
  private final ArrayList<View> reorderableRows = new ArrayList<>();
  private View movingRow;
  private int originalRowIndex;
  private Consumer<List<String>> saveOrder;

  boolean isPage(String title) {
    return title.equals(currentTitle);
  }

  private int accent;

  TvSettingsPanel(MainActivity host, int accent, Page root) {
    super(host);
    this.host = host;
    this.accent = accent;
    pages.push(new PageState(root));
    setOnDismissListener(d -> host.settingsVisibilityChanged(false));
  }

  @Override
  public void show() {
    Window window = getWindow();
    int keepScreenOn = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
    window.setFlags(host.getWindow().getAttributes().flags & keepScreenOn, keepScreenOn);
    updateWindowBounds();
    if (parked) {
      parked = false;
      if (accent != host.accent()) drawPage();
      host.settingsVisibilityChanged(true);
      super.show();
      Object key = pages.peek().focusKey;
      View target = key == null ? first : body.findViewWithTag(key);
      if (target != null) target.requestFocus();
      return;
    }
    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    window.setWindowAnimations(R.style.NovaSettingsWindowAnimation);
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    drawPage();
    host.settingsVisibilityChanged(true);
    super.show();
    updateWindowBounds();
  }

  void updateWindowBounds() {
    View decor = host.getWindow().getDecorView();
    android.util.DisplayMetrics metrics = host.getResources().getDisplayMetrics();
    int width = decor.getWidth() > 0 ? decor.getWidth() : metrics.widthPixels;
    int height = decor.getHeight() > 0 ? decor.getHeight() : metrics.heightPixels;
    Window window = getWindow();
    WindowManager.LayoutParams bounds = window.getAttributes();
    bounds.gravity = Gravity.TOP | Gravity.END;
    bounds.y = host.settingsPanelTop();
    bounds.width = Math.round(width * WIDTH_FRACTION);
    bounds.height = Math.max(1, height - bounds.y - host.dp(BOTTOM_MARGIN_DP));
    window.setAttributes(bounds);
  }

  boolean canResume() {
    return parked;
  }

  @Override
  public boolean isShowing() {
    return !parked && super.isShowing();
  }

  @Override
  public void dismiss() {
    finishReordering(false);
    parked = false;
    super.dismiss();
  }

  void open(Page page) {
    rememberPage();
    pages.push(new PageState(page));
    drawPage();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
      if (isShowing() && event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) park();
      return true;
    }
    int key = event.getKeyCode();
    if (movingRow != null && (key == KeyEvent.KEYCODE_DPAD_UP
        || key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_LEFT
        || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        int index = reorderableRows.indexOf(movingRow);
        if (key == KeyEvent.KEYCODE_DPAD_UP) moveRowTo(index - 1);
        if (key == KeyEvent.KEYCODE_DPAD_DOWN) moveRowTo(index + 1);
      }
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

  @Override
  public void onBackPressed() {
    if (movingRow != null) {
      finishReordering(false);
    } else if (pages.size() > 1) {
      pages.pop();
      drawPage();
    } else {
      park();
    }
  }

  private void park() {
    finishReordering(false);
    rememberPage();
    parked = true;
    super.hide();
    host.settingsVisibilityChanged(false);
  }

  private void rememberPage() {
    if (pageScroll == null) return;
    PageState state = pages.peek();
    View focused = pageScroll.findFocus();
    state.focusKey = focused == null ? null : focused.getTag();
    if (focused != null) {
      java.util.ArrayList<View> targets = body.getFocusables(View.FOCUS_FORWARD);
      state.focusIndex = Math.max(0, targets.indexOf(focused));
    }
    state.scrollY = pageScroll.getScrollY();
  }

  private void drawPage() {
    finishReordering(false);
    reorderableRows.clear();
    saveOrder = null;
    accent = host.accent();
    if (pageScroll == null) createPanel();
    body.animate().cancel();
    body.setTranslationX(0);
    heading.removeAllViews();
    body.removeAllViews();
    currentTitle = "";
    pageScroll.scrollTo(0, 0);
    first = null;
    PageState state = pages.peek();
    state.page.build(this);
    View target = state.focusKey == null ? null : body.findViewWithTag(state.focusKey);
    boolean restoreScroll = target != null && target.isFocusable() && target.isEnabled();
    if (!restoreScroll) {
      java.util.ArrayList<View> targets = body.getFocusables(View.FOCUS_FORWARD);
      target = targets.isEmpty() ? first : targets.get(Math.min(state.focusIndex, targets.size() - 1));
    }
    if (target == null) return;
    target.requestFocus();
    if (restoreScroll) {
      final View focused = target;
      body.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
          v.removeOnLayoutChangeListener(this);
          if (pages.peek() == state && focused.isFocused()) pageScroll.scrollTo(0, state.scrollY);
        }
      });
    }
  }

  private void createPanel() {
    LinearLayout panel = host.vertical();
    panel.setBackground(NovaUi.surface(host));
    heading = host.vertical();
    heading.setPadding(host.dp(20), host.dp(20), host.dp(20), host.dp(10));
    panel.addView(heading, host.lp(-1, -2));
    pageScroll = new ScrollView(host);
    pageScroll.setFillViewport(true);
    pageScroll.setVerticalScrollBarEnabled(false);
    pageScroll.setSmoothScrollingEnabled(false);
    body = host.vertical();
    body.setPadding(host.dp(14), 0, host.dp(14), host.dp(18));
    pageScroll.addView(body);
    panel.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(panel);
  }

  void title(String icon, String title, String subtitle) {
    title(null, icon, title, subtitle);
  }

  void title(android.graphics.drawable.Drawable artwork, String icon, String title, String subtitle) {
    currentTitle = title;
    if (pages.size() > 1) heading.addView(host.label("Настройки", 13, accent));
    LinearLayout titleRow = host.horizontal();
    titleRow.setGravity(Gravity.CENTER_VERTICAL);
    if (artwork == null) titleRow.addView(badge(icon), host.lp(32, 32));
    else {
      ImageView image = new ImageView(host);
      image.setImageDrawable(artwork);
      titleRow.addView(image, host.lp(32, 32));
    }
    TextView caption = host.label(title, 24, NovaUi.TEXT);
    caption.setTypeface(null, android.graphics.Typeface.BOLD);
    caption.setPadding(host.dp(8), 0, 0, 0);
    titleRow.addView(caption);
    heading.addView(titleRow);
    if (!subtitle.isEmpty()) {
      TextView text = host.label(subtitle, 13, NovaUi.SECONDARY);
      text.setPadding(0, host.dp(6), 0, 0);
      heading.addView(text);
    }
    host.gap(body, 12);
  }

  void section(String label) {
    host.gap(body, 10);
    body.addView(host.label(label, 13, accent));
    host.gap(body, 6);
  }

  void countdown(long deadline) {
    TextView remaining = new TextView(host) {
      private final Runnable update = new Runnable() {
        @Override public void run() {
          long millis = Math.max(0, deadline - android.os.SystemClock.elapsedRealtime());
          long seconds = (millis + 999) / 1000;
          setText(String.format(java.util.Locale.ROOT, "Осталось %d:%02d",
              seconds / 60, seconds % 60));
          if (seconds > 0 && getWindowVisibility() == VISIBLE) postDelayed(this, 1000);
        }
      };
      @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        removeCallbacks(update);
        if (visibility == VISIBLE) update.run();
      }
      @Override protected void onDetachedFromWindow() {
        removeCallbacks(update);
        super.onDetachedFromWindow();
      }
    };
    remaining.setTextSize(15);
    remaining.setTextColor(NovaUi.TEXT);
    body.addView(remaining);
  }

  private LinearLayout row(String icon, String title, String subtitle) {
    LinearLayout row = host.horizontal();
    row.setContentDescription(title);
    row.setTag("setting:" + title);
    row.setPadding(host.dp(12), host.dp(9), host.dp(12), host.dp(9));
    row.setMinimumHeight(host.dp(52));
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setFocusable(true);
    row.setFocusableInTouchMode(true);
    row.setBackground(NovaUi.interaction(host, accent));
    row.addView(badge(icon), host.lp(32, 32));
    LinearLayout labels = host.vertical();
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
    params.leftMargin = host.dp(10);
    row.addView(labels, params);
    TextView name = host.label(title, 17, 0xfff5f7ff);
    labels.addView(name);
    if (!subtitle.isEmpty()) {
      TextView hint = host.label(subtitle, 12, 0xffb9c2d7);
      labels.addView(hint);
    }
    LinearLayout.LayoutParams rp = host.lp(-1, -2);
    rp.bottomMargin = host.dp(4);
    body.addView(row, rp);
    if (first == null) first = row;
    return row;
  }

  private ImageView badge(String icon) {
    ImageView badge = new ImageView(host);
    badge.setImageResource(iconFor(icon));
    badge.setColorFilter(accent);
    badge.setPadding(host.dp(6), host.dp(6), host.dp(6), host.dp(6));
    badge.setBackground(host.shape((accent & 0x00ffffff) | 0x22000000, 9));
    return badge;
  }

  private int iconFor(String icon) {
    switch (icon) {
      case "♧": return R.drawable.nova_bell;
      case "ᛒ": return R.drawable.nova_bluetooth;
      case "◷": return R.drawable.nova_clock;
      case "☾": return R.drawable.nova_ui_moon;
      case "display": return R.drawable.nova_ui_tv;
      case "screensaver": return R.drawable.nova_ui_screensaver;
      case "sleep_timer": return R.drawable.nova_ui_sleep_timer;
      case "☁": return R.drawable.nova_weather;
      case "▦":
      case "☷": return R.drawable.nova_apps;
      case "▣":
      case "▭": return R.drawable.nova_ui_card;
      case "◉": return R.drawable.nova_wifi;
      case "⚙": return R.drawable.nova_ui_settings;
      case "⌂": return R.drawable.nova_ui_home;
      case "≡": return R.drawable.nova_ui_rows;
      case "━": return R.drawable.nova_ui_panel;
      case "▧": return R.drawable.nova_ui_photo;
      case "◐": return R.drawable.nova_ui_palette;
      case "◯": return R.drawable.nova_ui_drop;
      case "▤": return R.drawable.nova_ui_tv;
      case "◎": return R.drawable.nova_ui_pulse;
      case "▯": return R.drawable.nova_ui_phone;
      case "eye": return R.drawable.nova_ui_eye;
      case "eye_off": return R.drawable.nova_ui_eye_off;
      case "folder": return R.drawable.nova_ui_folder;
      case "layers": return R.drawable.nova_ui_layers;
      case "⇥": return R.drawable.nova_ui_input;
      case "⌁": return R.drawable.nova_ui_remote;
      case "shield": return R.drawable.nova_ui_shield;
      case "♿": return R.drawable.nova_ui_access;
      case "↺":
      case "↻": return R.drawable.nova_ui_reset;
      case "▷": return R.drawable.nova_ui_play;
      case "calendar": return R.drawable.nova_ui_calendar;
      case "search": return R.drawable.nova_ui_search;
      case "ruler": return R.drawable.nova_ui_ruler;
      case "T": return R.drawable.nova_ui_type;
      case "☀": return R.drawable.nova_ui_sun;
      case "!": return R.drawable.nova_ui_info;
      case "⌖": return R.drawable.nova_ui_pin;
      case "☆":
      case "✦": return R.drawable.nova_ui_star;
      case "+": return R.drawable.nova_ui_plus;
      case "↔": return R.drawable.nova_ui_horizontal;
      case "↕": return R.drawable.nova_ui_vertical;
      case "←": return R.drawable.nova_ui_left;
      case "→": return R.drawable.nova_ui_right;
      case "↑": return R.drawable.nova_ui_up;
      case "⏻": return R.drawable.nova_ui_power;
      default: return R.drawable.nova_ui_more;
    }
  }

  void action(String icon, String title, String subtitle, Runnable action) {
    action("setting:" + title, icon, title, subtitle, action);
  }

  void action(String key, String icon, String title, String subtitle, Runnable action) {
    addAction(key, icon, title, subtitle, action, false);
  }

  void reorderableAction(String key, String icon, String title, String subtitle,
      Runnable action, Consumer<List<String>> save) {
    LinearLayout row = addAction(key, icon, title, subtitle, () -> {
      if (movingRow != null) finishReordering(true);
      else action.run();
    }, true);
    reorderableRows.add(row);
    saveOrder = save;
    row.setOnLongClickListener(v -> {
      if (movingRow == null) {
        movingRow = row;
        originalRowIndex = reorderableRows.indexOf(row);
        row.setBackground(NovaUi.interaction(host, 0xff8de7d3));
      }
      return true;
    });
    RemoteOkHandler.bind(row);
  }

  private LinearLayout addAction(String key, String icon, String title, String subtitle,
      Runnable action, boolean reorderable) {
    LinearLayout row = row(icon, title, subtitle);
    row.setTag(key);
    if (reorderable) {
      ImageView handle = new ImageView(host);
      handle.setImageResource(R.drawable.nova_ui_reorder);
      handle.setColorFilter(accent);
      row.addView(handle, host.lp(22, 22));
      row.setContentDescription(title + ". Удерживайте OK для перемещения");
    }
    ImageView arrow = new ImageView(host);
    arrow.setImageResource(R.drawable.nova_ui_chevron);
    arrow.setColorFilter(accent);
    row.addView(arrow, host.lp(20, 20));
    row.setOnClickListener(v -> action.run());
    return row;
  }

  private void moveRowTo(int target) {
    if (target < 0 || target >= reorderableRows.size()) return;
    int from = reorderableRows.indexOf(movingRow);
    if (target == from) return;
    int bodyIndex = body.indexOfChild(reorderableRows.get(target));
    // Reattach the focused row at its new index and keep focus on it.
    body.removeView(movingRow);
    body.addView(movingRow, bodyIndex);
    reorderableRows.remove(from);
    reorderableRows.add(target, movingRow);
    movingRow.requestFocus();
    View moved = movingRow;
    moved.post(() -> {
      if (moved == movingRow && moved.isFocused()) {
        moved.requestRectangleOnScreen(new android.graphics.Rect(0, 0, moved.getWidth(),
            moved.getHeight()), true);
      }
    });
  }

  private void finishReordering(boolean save) {
    if (movingRow == null) return;
    if (!save) moveRowTo(originalRowIndex);
    movingRow.setBackground(NovaUi.interaction(host, accent));
    movingRow = null;
    if (save) {
      ArrayList<String> order = new ArrayList<>();
      for (View row : reorderableRows) order.add((String) row.getTag());
      saveOrder.accept(order);
    }
  }

  void unavailable(String icon, String title, String reason) {
    LinearLayout row = row(icon, title, reason);
    row.setEnabled(false);
    row.setFocusable(false);
    if (first == row) first = null;
  }

  void refresh() {
    rememberPage();
    drawPage();
  }

  void app(
      String key,
      String title,
      String subtitle,
      android.graphics.drawable.Drawable icon,
      Runnable click,
      Runnable longClick) {
    LinearLayout row = row("", title, subtitle);
    row.setTag(key);
    replaceBadge(row, icon);
    row.setOnClickListener(v -> click.run());
    if (longClick != null) {
      row.setOnLongClickListener(v -> {
        longClick.run();
        return true;
      });
    }
    RemoteOkHandler.bind(row);
  }

  void notification(String title, String subtitle, android.graphics.drawable.Drawable icon,
      Runnable click) {
    LinearLayout row = row("", title, subtitle);
    replaceBadge(row, icon);
    row.setOnClickListener(v -> click.run());
  }

  private void replaceBadge(LinearLayout row, android.graphics.drawable.Drawable icon) {
    if (icon != null) {
      View old = row.getChildAt(0);
      android.view.ViewGroup.LayoutParams params = old.getLayoutParams();
      row.removeViewAt(0);
      ImageView image = new ImageView(host);
      image.setImageDrawable(icon);
      image.setScaleType(ImageView.ScaleType.FIT_CENTER);
      row.addView(image, 0, params);
    }
  }

  void toggle(
      String icon, String title, String subtitle, boolean value, Consumer<Boolean> changed) {
    toggle("setting:" + title, icon, title, subtitle, value, changed);
  }

  void toggle(String key, String icon, String title, String subtitle, boolean value,
      Consumer<Boolean> changed) {
    LinearLayout row = row(icon, title, subtitle);
    row.setTag(key);
    Switch toggle = new Switch(host);
    toggle.setChecked(value);
    int[][] states = {new int[] {android.R.attr.state_checked}, new int[] {}};
    int[] colors = {accent, 0xff9aacc5};
    toggle.setThumbTintList(new android.content.res.ColorStateList(states, colors));
    toggle.setTrackTintList(
        new android.content.res.ColorStateList(
            states, new int[] {(accent & 0x00ffffff) | 0x99000000, 0xff4a556c}));
    toggle.setClickable(false);
    toggle.setFocusable(false);
    row.addView(toggle);
    row.setOnClickListener(
        v -> {
          toggle.setChecked(!toggle.isChecked());
          changed.accept(toggle.isChecked());
        });
  }

  void slider(
      String icon, String title, int min, int max, int value, String suffix, IntConsumer changed) {
    slider(icon, title, min, max, value, 1, suffix, changed);
  }

  void slider(String icon, String title, int min, int max, int value, int step, String suffix,
      IntConsumer changed) {
    int initial = min + Math.max(0, Math.min((max - min) / step,
        Math.round((float) (value - min) / step))) * step;
    LinearLayout container = host.vertical();
    container.setPadding(host.dp(12), host.dp(8), host.dp(12), host.dp(8));
    LinearLayout labelRow = host.horizontal();
    labelRow.setGravity(Gravity.CENTER_VERTICAL);
    labelRow.addView(badge(icon), host.lp(28, 28));
    TextView text = host.label(title + " · " + initial + suffix, 15, NovaUi.TEXT);
    text.setPadding(host.dp(8), 0, 0, 0);
    labelRow.addView(text);
    container.addView(labelRow);
    SeekBar seek = new SeekBar(host);
    seek.setTag("slider:" + title);
    seek.setFocusable(true);
    seek.setFocusableInTouchMode(true);
    seek.setMax((max - min) / step);
    seek.setProgress((initial - min) / step);
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
    container.addView(seek, host.lp(-1, 34));
    container.setAddStatesFromChildren(true);
    container.setBackground(NovaUi.interaction(host, accent));
    seek.setContentDescription(title);
    seek.setOnKeyListener(
        (v, key, event) -> {
          if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
          if (event.getAction() == KeyEvent.ACTION_DOWN)
            seek.setProgress(
                Math.max(
                    0,
                    Math.min(
                        seek.getMax(),
                        seek.getProgress() + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1))));
          return true;
        });
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            int actual = progress * step + min;
            text.setText(title + " · " + actual + suffix);
            changed.accept(actual);
          }

          public void onStartTrackingTouch(SeekBar bar) {}

          public void onStopTrackingTouch(SeekBar bar) {}
        });
    body.addView(container, host.lp(-1, -2));
    if (first == null) first = seek;
  }

  void palette(String title, int current, int fallback, IntConsumer save) {
    title("◐", title, "");
    int[] base = {
        0xffed493e, 0xffff961c, 0xffffd22c, 0xff88c940, 0xff20c299,
        0xff28a8e0, 0xff4d67e3, 0xff854bd7, 0xffde53ac, 0xffaaaaaa
    };
    float[] hsv = new float[3];
    android.graphics.Color.colorToHSV(current, hsv);
    float[] selected = hsv.clone();
    int[] opacity = {android.graphics.Color.alpha(current)};
    int[] brightness = {50};
    int[] result = {current};
    View sample = new View(host);
    Runnable preview = () -> {
      float[] color = selected.clone();
      float adjustment = (brightness[0] - 50) / 50f;
      color[2] = adjustment >= 0 ? color[2] + (1f - color[2]) * adjustment
          : color[2] * (1f + adjustment);
      result[0] = android.graphics.Color.HSVToColor(opacity[0], color);
      sample.setBackground(host.shape(result[0], 8));
    };
    for (int shade = 0; shade < 6; shade++) {
      LinearLayout line = host.horizontal();
      for (int column = 0; column < base.length; column++) {
        float[] chip = new float[3];
        android.graphics.Color.colorToHSV(base[column], chip);
        chip[1] *= shade < 3 ? .28f + shade * .24f : 1f;
        chip[2] *= shade < 3 ? 1f : 1f - (shade - 3) * .22f;
        int color = android.graphics.Color.HSVToColor(chip);
        View swatch = new View(host);
        swatch.setFocusable(true);
        swatch.setTag("palette:" + shade + ":" + column);
        swatch.setContentDescription(String.format(java.util.Locale.ROOT, "Цвет #%06X", color & 0xffffff));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, host.dp(30), 1f);
        size.setMargins(host.dp(2), host.dp(2), host.dp(2), host.dp(2));
        line.addView(swatch, size);
        swatch.setBackground(host.shape(color, 7));
        swatch.setOnFocusChangeListener((view, focused) -> {
          android.graphics.drawable.GradientDrawable border = host.shape(color, 7);
          if (focused) border.setStroke(host.dp(3), android.graphics.Color.WHITE);
          view.setBackground(border);
        });
        swatch.setOnClickListener(v -> {
          System.arraycopy(chip, 0, selected, 0, 3);
          preview.run();
        });
        if (first == null) first = swatch;
      }
      body.addView(line, host.lp(-1, 34));
    }
    body.addView(sample, host.lp(-1, 34));
    preview.run();
    slider("◐", "Яркость", 0, 100, brightness[0], " %", value -> {
      brightness[0] = value;
      preview.run();
    });
    slider("◐", "Непрозрачность", 0, 100, Math.round(opacity[0] * 100f / 255), " %", value -> {
      opacity[0] = Math.round(255f * value / 100);
      preview.run();
    });
    action("palette.default", "↻", "По умолчанию", "", () -> {
      save.accept(fallback);
      onBackPressed();
    });
    action("palette.cancel", "×", "Отмена", "", this::onBackPressed);
    action("palette.ok", "✓", "OK", "", () -> {
      save.accept(result[0]);
      onBackPressed();
    });
    body.setTranslationX(body.getWidth() > 0 ? body.getWidth() : host.dp(180));
    body.animate().translationX(0).setDuration(160).start();
  }

}
