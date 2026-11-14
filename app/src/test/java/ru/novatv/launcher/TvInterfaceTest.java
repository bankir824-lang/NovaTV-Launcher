package ru.novatv.launcher;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.*;

import android.app.Dialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TvInterfaceTest {
  @Test
  public void cardFocusOutlineIsASeparateForeground() {
    MainActivity activity = create();
    AppCardView card = new AppCardView(activity, 10, Color.BLUE);
    assertTrue(card.contents.getClipToOutline());
    card.showFocus(true, true, Color.WHITE);
    assertNotNull(card.getForeground());
    assertFalse(card.getClipToOutline());
    android.graphics.drawable.Drawable outline = card.getForeground();
    card.showFocus(false, true, Color.WHITE);
    assertSame(outline, card.getForeground());
    assertEquals(0, outline.getAlpha());
    activity.finish();
  }

  @Test
  public void statusRadiusReachesHalfTheHeightAtOneHundredPercent() {
    assertEquals(0f, StatusBarView.radius(200, 40, 0), 0f);
    assertEquals(20f, StatusBarView.radius(200, 40, 100), 0f);
  }

  @Test
  public void backReturnsFocusToRowsSetting() {
    MainActivity activity = create();
    activity.settings();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    View root = panel.findViewById(android.R.id.content);
    View home = root.findViewWithTag("root.home");
    assertTrue(home.performClick());
    View cards = panel.getWindow().getDecorView().findViewWithTag("home.rows");
    assertTrue(cards.requestFocus());
    assertTrue(cards.performClick());
    panel.onBackPressed();
    assertEquals("home.rows", panel.getCurrentFocus().getTag());
    panel.onBackPressed();
    assertEquals("root.home", panel.getCurrentFocus().getTag());
    assertSame(root, panel.findViewById(android.R.id.content));
    panel.dismiss();
    activity.finish();
  }

  @Test
  public void keepScreenOnAppliesToTheLauncherAndTheSettingsWindow() {
    MainActivity activity = create();
    int flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
    activity.setBool("keep_screen_on", true);
    activity.settings();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    View root = panel.findViewById(android.R.id.content);
    View power = root.findViewWithTag("root.power");
    assertTrue(power.requestFocus());
    assertTrue(power.performClick());
    View keepScreenOn = root.findViewWithTag("power.keep_screen_on");
    assertEquals(flag, activity.getWindow().getAttributes().flags & flag);
    assertEquals(flag, panel.getWindow().getAttributes().flags & flag);
    assertTrue(keepScreenOn.performClick());
    assertFalse(activity.getPreferences(0).getBoolean("keep_screen_on", true));
    assertEquals(0, activity.getWindow().getAttributes().flags & flag);
    assertEquals(0, panel.getWindow().getAttributes().flags & flag);
    panel.onBackPressed();
    assertEquals("root.power", panel.getCurrentFocus().getTag());
    assertSame(root, panel.findViewById(android.R.id.content));
    panel.dismiss();
    activity.finish();
  }

  @Test
  public void screensaverResetsOnInteractionAndConsumesTheWakePress() {
    ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup().visible();
    MainActivity activity = controller.get();
    View root = activity.findViewById(android.R.id.content);
    ShadowLooper main = shadowOf(android.os.Looper.getMainLooper());
    activity.getPreferences(0).edit().putBoolean(NovaScreensaver.ENABLED, true)
        .putInt(NovaScreensaver.DELAY, 1).apply();
    controller.windowFocusChanged(true);

    main.idleFor(java.time.Duration.ofSeconds(59));
    assertNull(root.findViewWithTag("nova.screensaver"));
    activity.onUserInteraction();
    main.idleFor(java.time.Duration.ofSeconds(59));
    assertNull(root.findViewWithTag("nova.screensaver"));
    main.idleFor(java.time.Duration.ofSeconds(1));
    assertNotNull(root.findViewWithTag("nova.screensaver"));

    assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU)));
    assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU)));
    assertNull(root.findViewWithTag("nova.screensaver"));
    assertNull(ShadowDialog.getLatestDialog());
    controller.pause();
    main.idleFor(java.time.Duration.ofMinutes(1));
    assertNull(root.findViewWithTag("nova.screensaver"));
    controller.stop().destroy();
  }

  @Test
  public void remoteMenuOpensSettingsAndBackOnlyClosesIt() {
    MainActivity activity = create();
    activity.onBackPressed();
    assertNull(ShadowDialog.getLatestDialog());
    activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
    assertNull(ShadowDialog.getLatestDialog());
    activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
    Dialog panel = ShadowDialog.getLatestDialog();
    assertTrue(panel.isShowing());
    panel.onBackPressed();
    activity.onBackPressed();
    assertFalse(panel.isShowing());
    activity.finish();
  }

  @Test
  public void reopeningSettingsReusesPanelAndRestoresFocus() {
    MainActivity activity = create();
    activity.settings();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    View content = panel.findViewById(android.R.id.content);
    View wallpaper = content.findViewWithTag("root.wallpaper");
    assertTrue(wallpaper.requestFocus());
    panel.onBackPressed();
    assertFalse(panel.isShowing());
    activity.settings();
    assertTrue(panel.isShowing());
    assertSame(content, panel.findViewById(android.R.id.content));
    assertSame(wallpaper, panel.getCurrentFocus());
    panel.dismiss();
    activity.finish();
  }

  @Test
  public void repeatedAppearanceChangesKeepTheSameHomeScreen() {
    MainActivity activity = create();
    ViewGroup windowContent = activity.findViewById(android.R.id.content);
    View home = windowContent.getChildAt(0);
    activity.settings();
    assertEquals(1f, home.getScaleX(), 0f);
    assertEquals(0f, home.getRotationY(), 0f);
    assertEquals(0f, home.getTranslationX(), 0f);
    for (int width = 128; width < 136; width++) activity.setInt("card_width", width);
    shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250));
    assertSame(home, windowContent.getChildAt(0));
    assertEquals(135, activity.getPreferences(0).getInt("card_width", 0));
    ((TvSettingsPanel) ShadowDialog.getLatestDialog()).dismiss();
    activity.finish();
  }

  @Test
  public void holdingOkOnHeaderGearOpensOnlyAndroidSettings() {
    MainActivity activity = create();
    layoutRemoteTarget(activity.getWindow().getDecorView(), 1920);
    View gear = activity.findViewById(android.R.id.content).findViewWithTag("header.settings");
    gear.setFocusableInTouchMode(true);
    assertTrue(gear.requestFocus());
    activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
    shadowOf(android.os.Looper.getMainLooper()).idleFor(
        java.time.Duration.ofMillis(ViewConfiguration.getLongPressTimeout()));
    activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
    assertEquals(android.provider.Settings.ACTION_SETTINGS,
        shadowOf(activity).getNextStartedActivity().getAction());
    assertNull(ShadowDialog.getLatestDialog());
    activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
    activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
    assertTrue(ShadowDialog.getLatestDialog().isShowing());
    assertNull(shadowOf(activity).getNextStartedActivity());
    activity.finish();
  }

  @Test
  public void returningFromCancelledWallpaperPickKeepsTheSameRows() {
    MainActivity activity = create();
    View root = activity.findViewById(android.R.id.content);
    View header = root.findViewWithTag("header.controls");
    View card = root.findViewWithTag("app_order:test.tv.app0");
    assertNotNull(header);
    assertNotNull(card);
    for (int request : new int[] {7, 8}) {
      activity.onPause();
      activity.onActivityResult(request, android.app.Activity.RESULT_CANCELED, null);
      activity.onResume();
      assertSame(header, root.findViewWithTag("header.controls"));
      assertSame(card, root.findViewWithTag("app_order:test.tv.app0"));
    }
    activity.finish();
  }

  @Test
  public void nightDimmingUpdatesOnlyTheWallpaperShade() {
    MainActivity activity = create();
    View root = activity.findViewById(android.R.id.content);
    View shade = root.findViewWithTag("wallpaper.night_shade");
    View card = root.findViewWithTag("app_order:test.tv.app0");
    assertNotNull(card);
    assertEquals(0f, shade.getAlpha(), 0f);
    java.util.Calendar now = java.util.Calendar.getInstance();
    int minute = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
    activity.setInt(NightDimming.START, (minute + 1430) % 1440);
    activity.setInt(NightDimming.END, (minute + 10) % 1440);
    activity.setInt(NightDimming.AMOUNT, 60);
    activity.setInt(NightDimming.STEP, 60);
    activity.setBool(NightDimming.ENABLED, true);
    shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
    assertEquals(0.6f, shade.getAlpha(), 0f);
    assertSame(card, root.findViewWithTag("app_order:test.tv.app0"));
    activity.setBool(NightDimming.ENABLED, false);
    shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
    assertEquals(0f, shade.getAlpha(), 0f);
    activity.finish();
  }

  @Test
  public void nightDimmingReachesTheMaximumInShortIntervals() {
    assertEquals(10, NightDimming.amountAt(22 * 60, 22 * 60, 0, 35, 10));
    assertEquals(20, NightDimming.amountAt(22 * 60 + 20, 22 * 60, 0, 35, 10));
    assertEquals(30, NightDimming.amountAt(22 * 60 + 40, 22 * 60, 0, 35, 10));
    assertEquals(35, NightDimming.amountAt(23 * 60, 22 * 60, 0, 35, 10));
    assertEquals(30, NightDimming.amountAt(23 * 60 + 10, 22 * 60, 0, 35, 10));
    assertEquals(10, NightDimming.amountAt(23 * 60 + 59, 22 * 60, 0, 35, 10));
    assertEquals(0, NightDimming.amountAt(0, 22 * 60, 0, 35, 10));
    for (int duration = 1; duration < 1440; duration++) {
      int start = 23 * 60;
      int end = (start + duration) % 1440;
      int previous = 0;
      for (int offset = 0; offset < duration; offset++) {
        int value = NightDimming.amountAt((start + offset) % 1440, start, end, 97, 13);
        assertTrue(value >= 0 && value <= 97);
        assertTrue(offset < duration / 2 ? value >= previous : value <= previous || value == 97);
        previous = value;
      }
      assertEquals(97, NightDimming.amountAt((start + duration / 2) % 1440, start, end, 97, 13));
    }
  }

  @Test
  public void nightDimmingSupportsMidnightAndExcludesTheEndTime() {
    assertTrue(NightDimming.includes(22 * 60, 22 * 60, 7 * 60));
    assertTrue(NightDimming.includes(0, 22 * 60, 7 * 60));
    assertFalse(NightDimming.includes(7 * 60, 22 * 60, 7 * 60));
    assertTrue(NightDimming.includes(12 * 60, 10 * 60, 14 * 60));
    assertFalse(NightDimming.includes(9 * 60, 10 * 60, 14 * 60));
    assertFalse(NightDimming.includes(0, 0, 0));
    assertEquals(10, NightDimming.amountAt(22 * 60, 22 * 60, 7 * 60, 35, 10));
    assertEquals(20, NightDimming.amountAt(23 * 60, 22 * 60, 7 * 60, 35, 10));
    assertEquals(35, NightDimming.amountAt(2 * 60, 22 * 60, 7 * 60, 35, 10));
    assertEquals(10, NightDimming.amountAt(6 * 60, 22 * 60, 7 * 60, 35, 10));
    assertEquals(0, NightDimming.amountAt(7 * 60, 22 * 60, 7 * 60, 35, 10));
  }

  @Test
  public void rowVisibilityAndHeadingSettingsKeepTheSameCards() {
    MainActivity activity = create();
    View root = activity.findViewById(android.R.id.content);
    View row = root.findViewWithTag("home.row:tv");
    View card = root.findViewWithTag("app_order:test.tv.app0");
    assertNotNull(card);
    activity.setBool("show_all", false);
    assertEquals(View.GONE, row.getVisibility());
    activity.setBool("show_all", true);
    assertEquals(View.VISIBLE, row.getVisibility());
    activity.setBool("show_titles", false);
    activity.setBool("show_category_icons", false);
    activity.setBool("remember_focus", false);
    activity.setBool("pin_row", true);
    assertSame(row, root.findViewWithTag("home.row:tv"));
    assertSame(card, root.findViewWithTag("app_order:test.tv.app0"));
    activity.finish();
  }

  @Test
  public void statusItemsHaveRequestedOrderAndRemoteFocus() {
    MainActivity activity = create();
    View root = activity.findViewById(android.R.id.content);
    View network = root.findViewWithTag("header.network");
    View notifications = root.findViewWithTag("header.notifications");
    ViewGroup controls = root.findViewWithTag("header.controls");
    ViewGroup information = root.findViewWithTag("header.information");
    String[] controlsOrder = {"header.apps", "header.search", "header.settings",
        "header.timer", "header.network", "header.notifications", "header.bluetooth"};
    for (int i = 0; i < controlsOrder.length; i++)
      assertEquals(controlsOrder[i], controls.getChildAt(i).getTag());
    String[] informationOrder = {"header.weather", "header.date", "header.clock"};
    for (int i = 0; i < informationOrder.length; i++)
      assertEquals(informationOrder[i], information.getChildAt(i).getTag());
    assertSame(controls, ((ViewGroup) controls.getParent()).getChildAt(0));
    assertTrue(network.isFocusable());
    assertTrue(notifications.isFocusable());
    assertTrue(notifications.performClick());
    assertTrue(ShadowDialog.getLatestDialog().isShowing());
    activity.finish();
  }

  @Test
  public void blockAndRowReorderingSaveOnOkAndRestoreOnBack() {
    MainActivity activity = create();
    activity.settings();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    View root = panel.findViewById(android.R.id.content);
    root.findViewWithTag("root.status").performClick();
    root.findViewWithTag("status.content").performClick();
    View controls = root.findViewWithTag("controls");
    controls.requestFocus();
    controls.performLongClick();
    panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
    assertSame(controls, panel.getCurrentFocus());
    controls.performClick();
    assertEquals("clock|controls", activity.getPreferences(0).getString("status_block_order", ""));
    View headerControls = activity.findViewById(android.R.id.content).findViewWithTag("header.controls");
    assertEquals(1, ((ViewGroup) headerControls.getParent()).indexOfChild(headerControls));
    panel.onBackPressed();
    panel.onBackPressed();
    root.findViewWithTag("root.home").performClick();
    root.findViewWithTag("home.rows").performClick();
    View favorites = root.findViewWithTag("favorites");
    ViewGroup rows = (ViewGroup) favorites.getParent();
    int first = rows.indexOfChild(favorites);
    favorites.requestFocus();
    favorites.performLongClick();
    panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
    assertEquals(first + 1, rows.indexOfChild(favorites));
    panel.onBackPressed();
    assertEquals(first, rows.indexOfChild(favorites));
    assertFalse(activity.getPreferences(0).contains("home_row_order"));
    panel.dismiss();
    activity.finish();
  }

  @Test
  public void sleepTimerDimsAfterTheDeadlineAndAKeyRestoresTheWindow() {
    ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup().visible();
    MainActivity activity = controller.get();
    controller.windowFocusChanged(true);
    View root = activity.findViewById(android.R.id.content);
    ShadowLooper main = shadowOf(android.os.Looper.getMainLooper());
    activity.setBool("keep_screen_on", true);
    activity.setSleepTimer(15);
    main.idleFor(java.time.Duration.ofMinutes(14));
    assertNull(root.findViewWithTag("nova.sleep"));
    main.idleFor(java.time.Duration.ofMinutes(1));
    assertNotNull(root.findViewWithTag("nova.sleep"));
    int flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
    assertEquals(0, activity.getWindow().getAttributes().flags & flag);
    assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)));
    assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)));
    assertNull(root.findViewWithTag("nova.sleep"));
    assertEquals(flag, activity.getWindow().getAttributes().flags & flag);
    activity.setSleepTimer(15);
    activity.setSleepTimer(0);
    main.idleFor(java.time.Duration.ofMinutes(15));
    assertNull(root.findViewWithTag("nova.sleep"));
    controller.pause().stop().destroy();
  }

  @Test
  public void novaTvHasSeparateEntryPointsForInstallerTvAndHome() {
    Context context = RuntimeEnvironment.getApplication();
    PackageManager packages = context.getPackageManager();
    for (String category : new String[] {
        Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_HOME
    }) {
      Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category)
          .setPackage(context.getPackageName());
      int flags = Intent.CATEGORY_HOME.equals(category) ? PackageManager.MATCH_DEFAULT_ONLY : 0;
      ResolveInfo resolved = packages.resolveActivity(intent, flags);
      assertNotNull(category, resolved);
      assertEquals(MainActivity.class.getName(), resolved.activityInfo.name);
    }
  }

  private MainActivity create() {
    return create(6);
  }

  private MainActivity create(int count) {
    android.app.Application app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("MainActivity", 0).edit().clear().apply();
    for (int n = 0; n < count; n++) {
      ResolveInfo info = new ResolveInfo();
      info.activityInfo = new ActivityInfo();
      info.activityInfo.packageName = "test.tv.app" + n;
      info.activityInfo.name = "test.tv.app" + n + ".MainActivity";
      info.activityInfo.applicationInfo = new ApplicationInfo();
      info.activityInfo.applicationInfo.packageName = info.activityInfo.packageName;
      info.nonLocalizedLabel = "Приложение " + (n + 1);
      info.activityInfo.enabled = true;
      info.activityInfo.applicationInfo.enabled = true;
      info.activityInfo.applicationInfo.flags = ApplicationInfo.FLAG_INSTALLED;
      PackageInfo fixture = new PackageInfo();
      fixture.packageName = info.activityInfo.packageName;
      fixture.applicationInfo = info.activityInfo.applicationInfo;
      fixture.activities = new ActivityInfo[] {info.activityInfo};
      shadowOf(app.getPackageManager()).installPackage(fixture);
      info.activityInfo.exported = true;
      Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
      shadowOf(app.getPackageManager()).addResolveInfoForIntent(launch, info);
    }
    MainActivity activity =
        Robolectric.buildActivity(MainActivity.class).create().start().resume().visible().get();
    shadowOf(android.os.Looper.getMainLooper()).idle();
    return activity;
  }

  @Test
  public void hiddenOnlyLibraryDoesNotContainVisibleApps() {
    MainActivity a = create();
    a.getPreferences(0)
        .edit()
        .putString("hidden", "test.tv.app0")
        .putString("favorite_order", "test.tv.app0")
        .apply();
    assertTrue(a.visibleApps(a.getFavorites(), false).isEmpty());
    assertEquals(1, a.visibleApps(a.getFavorites(), true).size());
    a.finish();
  }

  @Test
  public void resizingTvCardsKeepsFavoriteRowAndHeaderInstances() {
    MainActivity activity = create();
    activity.getPreferences(0).edit().putString("favorite_order", "test.tv.app0").apply();
    activity.home();
    View root = activity.findViewById(android.R.id.content);
    View favorites = root.findViewWithTag("home.row:favorites");
    View television = root.findViewWithTag("home.row:tv");
    View header = root.findViewWithTag("header.controls");
    assertNotNull(favorites);
    assertNotNull(television);
    activity.settings();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    panel.open(activity::rowsSettings);
    View settingsRoot = panel.findViewById(android.R.id.content);
    settingsRoot.findViewWithTag("tv").performClick();
    SeekBar size = settingsRoot.findViewWithTag("slider:Размер карточки");
    size.setProgress(size.getProgress() + 1);
    assertSame(favorites, root.findViewWithTag("home.row:favorites"));
    assertSame(header, root.findViewWithTag("header.controls"));
    assertNotSame(television, root.findViewWithTag("home.row:tv"));
    assertEquals(100, activity.getPreferences(0).getInt("row:tv:card_width", 0));
    panel.dismiss();
    activity.finish();
  }

  @Test
  public void wholeHeaderDisappearsAndControlOrderSurvivesToggle() {
    MainActivity activity = create();
    View root = activity.findViewById(android.R.id.content);
    activity.getPreferences(0).edit().putString("status_controls_order", "bluetooth|search").apply();
    activity.setBool("show_status", false);
    assertNull(root.findViewWithTag("header.apps"));
    assertNull(root.findViewWithTag("header.information"));
    activity.setBool("show_status", true);
    ViewGroup controls = root.findViewWithTag("header.controls");
    assertEquals("header.bluetooth", controls.getChildAt(0).getTag());
    assertEquals("header.search", controls.getChildAt(1).getTag());
    activity.finish();
  }

  @Test
  public void settingsOpenAndNestedPageBackKeepsPanelOpen() throws Exception {
    MainActivity a = create();
    a.settings();
    shadowOf(android.os.Looper.getMainLooper()).idle();
    Dialog panel = ShadowDialog.getLatestDialog();
    assertTrue(panel instanceof TvSettingsPanel);
    TvSettingsPanel settings = (TvSettingsPanel) panel;
    settings.open(a::rowsSettings);
    assertTrue(settings.isShowing());
    save(a, settings, "settings-cards.png");
    settings.onBackPressed();
    assertTrue(settings.isShowing());
    save(a, settings, "settings-root.png");
    settings.dismiss();
    assertFalse(settings.isShowing());
    shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(220));
    save(a, null, "home.png");
    a.finish();
  }

  @Test
  public void remoteSliderUpdatesPreferenceAndRetainsFocus() {
    MainActivity a = create();
    a.settings();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    panel.open(
        p ->
            p.slider(
                "▭",
                "Размер карточки",
                80,
                240,
                126,
                " dp",
                value -> a.setInt("card_width", value)));
    View focus = panel.getCurrentFocus();
    assertTrue(focus instanceof SeekBar);
    focus.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
    assertEquals(127, a.getPreferences(0).getInt("card_width", 0));
    shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
    assertSame(focus, panel.getCurrentFocus());
    panel.dismiss();
    a.finish();
  }

  @Test
  public void libraryEnterLaunchesExactlyOnce() {
    MainActivity a = create();
    a.openLibrary();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    View decor = panel.getWindow().getDecorView();
    decor.measure(
        View.MeasureSpec.makeMeasureSpec(826, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
    decor.layout(0, 0, 826, 1080);
    shadowOf(android.os.Looper.getMainLooper()).idle();
    View row = panel.getCurrentFocus();
    assertNotNull(row);
    assertEquals("Приложение 1", String.valueOf(row.getContentDescription()));
    assertTrue(row.isFocused());
    assertTrue("Строка должна быть присоединена к окну", row.isAttachedToWindow());
    panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
    panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    Intent launched = shadowOf(a).getNextStartedActivity();
    assertNotNull(
        "Последнее сообщение: " + org.robolectric.shadows.ShadowToast.getTextOfLatestToast(),
        launched);
    assertEquals("test.tv.app0", launched.getComponent().getPackageName());
    assertNull(shadowOf(a).getNextStartedActivity());
    a.finish();
  }

  @Test
  public void protectedLibraryWaitsForPinInsteadOfOpeningNullPanel() {
    MainActivity a = create();
    a.getPreferences(0)
        .edit()
        .putBoolean("pin_settings", true)
        .putString("pin_hash", "test-only")
        .apply();
    a.openLibrary();
    Dialog dialog = ShadowDialog.getLatestDialog();
    assertNotNull(dialog);
    assertTrue(dialog.isShowing());
    assertFalse(dialog instanceof TvSettingsPanel);
    dialog.dismiss();
    a.finish();
  }

  @Test
  public void horizontalKeysStopAtTheEdgesOfAnAppLine() {
    MainActivity activity = create(15);
    View decor = activity.getWindow().getDecorView();
    layoutRemoteTarget(decor, 1920);
    View first = decor.findViewWithTag("app_order:test.tv.app0");
    ViewGroup line = (ViewGroup) first.getParent();
    assertTrue(line.getChildCount() > 1);
    View last = line.getChildAt(line.getChildCount() - 1);
    for (int i = 0; i < line.getChildCount(); i++) line.getChildAt(i).setFocusableInTouchMode(true);
    assertTrue(first.requestFocus());
    assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)));
    assertSame(first, decor.findFocus());
    activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
    assertSame(line.getChildAt(1), decor.findFocus());
    assertTrue(last.requestFocus());
    assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)));
    assertSame(last, decor.findFocus());
    activity.finish();
  }

  @Test
  public void manyApplicationsUseMultipleRemoteNavigableRows() {
    MainActivity a = create(15);
    a.getPreferences(0).edit().putInt("row:tv:card_width", 240).apply();
    a.home();
    View first = a.getWindow().getDecorView().findViewWithTag("app_order:test.tv.app0"),
        last = a.getWindow().getDecorView().findViewWithTag("app_order:test.tv.app8");
    assertNotNull(first);
    assertNotNull(last);
    assertNotSame(first.getParent(), last.getParent());
    assertTrue(first.isFocusable());
    assertTrue(last.isFocusable());
    a.finish();
  }

  private void layoutRemoteTarget(View decor, int width) {
    decor.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
    decor.layout(0, 0, width, 1080);
    shadowOf(android.os.Looper.getMainLooper()).idle();
  }

  private android.app.AlertDialog contextMenu() {
    Dialog dialog = ShadowDialog.getLatestDialog();
    assertTrue(dialog instanceof android.app.AlertDialog);
    android.app.AlertDialog menu = (android.app.AlertDialog) dialog;
    assertTrue(menu.isShowing());
    android.widget.ListAdapter adapter = menu.getListView().getAdapter();
    boolean favorite = false, move = false;
    for (int n = 0; n < adapter.getCount(); n++) {
      String item = String.valueOf(adapter.getItem(n));
      assertNotEquals("Открыть", item);
      favorite |= item.equals("Добавить в избранное");
      move |= item.equals("Переместить приложение");
    }
    assertTrue(favorite);
    assertTrue(move);
    return menu;
  }

  @Test
  public void mainCardShortOkLaunchesExactlyOnce() {
    MainActivity a = create();
    layoutRemoteTarget(a.getWindow().getDecorView(), 1920);
    View card = a.getWindow().getDecorView().findViewWithTag("app_order:test.tv.app0");
    card.setFocusableInTouchMode(true);
    card.requestFocus();
    assertTrue("Карточка должна получить фокус пульта", card.hasFocus());
    assertTrue(card.isAttachedToWindow());
    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
    assertNotNull(shadowOf(a).getNextStartedActivity());
    assertNull(shadowOf(a).getNextStartedActivity());
    a.finish();
  }

  @Test
  public void mainCardHoldingOpensMenuAndStillAllowsFavorites() {
    MainActivity a = create();
    layoutRemoteTarget(a.getWindow().getDecorView(), 1920);
    View card = a.getWindow().getDecorView().findViewWithTag("app_order:test.tv.app0");
    card.setFocusableInTouchMode(true);
    card.requestFocus();
    assertTrue("Карточка должна получить фокус пульта", card.hasFocus());
    assertTrue(card.isAttachedToWindow());
    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
    shadowOf(android.os.Looper.getMainLooper())
        .idleFor(java.time.Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1));
    android.app.AlertDialog menu = contextMenu();
    card.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
    assertNull(shadowOf(a).getNextStartedActivity());
    menu.getListView().performItemClick(null, 0, 0);
    assertTrue(a.favoriteIds().contains("test.tv.app0"));
    menu.dismiss();
    a.finish();
  }

  @Test
  public void libraryLongEnterDoesNotLaunchAfterContextMenu() {
    MainActivity a = create();
    a.openLibrary();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    layoutRemoteTarget(panel.getWindow().getDecorView(), 826);
    View row = panel.getCurrentFocus();
    panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
    shadowOf(android.os.Looper.getMainLooper())
        .idleFor(java.time.Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1));
    android.app.AlertDialog menu = contextMenu();
    row.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    assertNull(shadowOf(a).getNextStartedActivity());
    menu.dismiss();
    panel.dismiss();
    a.finish();
  }

  private void save(MainActivity activity, Dialog panel, String name) throws Exception {
    View view = activity.getWindow().getDecorView();
    int width = 1920, height = 1080;
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, width, height);
    Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(image);
    view.draw(canvas);
    if (panel != null) {
      View overlay = panel.getWindow().getDecorView();
      int panelWidth = (int) (width * 0.43f);
      overlay.measure(
          View.MeasureSpec.makeMeasureSpec(panelWidth, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      overlay.layout(0, 0, panelWidth, height);
      canvas.save();
      canvas.translate(width - panelWidth, 0);
      overlay.draw(canvas);
      canvas.restore();
    }
    File folder = new File("build/reports/ui");
    folder.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(folder, name))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    image.recycle();
  }
}
