package ru.novatv.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import java.util.*;

/** Reads only rows the TV provider grants to an ordinary installed launcher. */
final class TvFeeds {
  static final class Program {
    final String title, artwork, intent, pkg, facts, description;

    Program(String title, String artwork, String intent, String pkg, String facts, String description) {
      this.title = title;
      this.artwork = artwork;
      this.intent = intent;
      this.pkg = pkg;
      this.facts = facts;
      this.description = description;
    }
  }

  static final class Channel {
    final String id, title, pkg, source;
    final List<Program> programs = new ArrayList<>();

    Channel(String id, String title, String pkg, String source) {
      this.id = id;
      this.title = title;
      this.pkg = pkg;
      this.source = source;
    }
  }

  static final class Result {
    final List<Channel> channels = new ArrayList<>();
    final List<String> details = new ArrayList<>();
    String message = "";
    int discovered, displayedPrograms, skippedWithoutLink;

    String diagnostics() {
      return "Preview Channels: " + discovered + "\nПоказано карточек: " + displayedPrograms
          + "\nПропущено без ссылки: " + skippedWithoutLink + "\n" + message + "\n"
          + android.text.TextUtils.join("\n", details);
    }
  }

  static Result read(Context context, boolean readPreview) {
    Result result = new Result();
    if (readPreview) readPreviewChannels(context, result);
    else result.message = "Для каналов приложений требуется разрешение.";
    try {
      Channel next = new Channel("watch_next", "Смотреть далее", "", "Android TV");
      readPrograms(context, TvContract.WatchNextPrograms.CONTENT_URI, next, result);
      if (!next.programs.isEmpty()) result.channels.add(0, next);
      result.details.add("Смотреть далее: " + next.programs.size());
    } catch (RuntimeException e) {
      result.details.add("Смотреть далее: " + failure(e));
    }
    return result;
  }

  private static void readPreviewChannels(Context context, Result result) {
    try (Cursor c =
        context
            .getContentResolver()
            .query(
                TvContract.Channels.CONTENT_URI,
                new String[] {"_id", "display_name", "package_name", "type", "browsable"},
                null,
                null,
                null)) {
      if (c == null) {
        result.message = "TV Provider не вернул список каналов.";
        return;
      }
      while (c.moveToNext() && result.discovered < 40) {
        if (!TvContract.Channels.TYPE_PREVIEW.equals(c.getString(3))) continue;
        result.discovered++;
        String pkg = c.getString(2);
        String source = sourceName(context, pkg);
        String title = c.getString(1);
        Channel channel = new Channel(c.getString(0),
            title == null || title.trim().isEmpty() ? source : title, pkg, source);
        String label = channel.title + " [" + pkg + "]";
        if (c.getInt(4) == 0) {
          result.details.add(label + ": выключен в системе");
          continue;
        }
        try {
          readPrograms(context, TvContract.buildPreviewProgramsUriForChannel(c.getLong(0)),
              channel, result);
          result.details.add(label + ": " + channel.programs.size() + " карточек");
          if (!channel.programs.isEmpty()) result.channels.add(channel);
        } catch (RuntimeException e) {
          result.details.add(label + ": " + failure(e));
        }
      }
      result.channels.sort(Comparator.comparing(channel -> channel.source, String.CASE_INSENSITIVE_ORDER));
      if (result.channels.isEmpty()) result.message = "Нет доступных каналов приложений. Подробности в диагностике.";
    } catch (RuntimeException e) {
      result.message = failure(e);
    }
  }

  private static String sourceName(Context context, String pkg) {
    if (pkg == null || pkg.isEmpty()) return "Приложение";
    try {
      PackageManager pm = context.getPackageManager();
      return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
    } catch (PackageManager.NameNotFoundException ignored) {
      return "Приложение";
    }
  }

  private static String failure(RuntimeException e) {
    return e instanceof SecurityException ? "Прошивка ограничивает чтение TV Provider."
        : "TV Provider не поддерживает запрос.";
  }

  private static final String[] BASIC = {
      "title", "poster_art_uri", "intent_uri", "package_name", "thumbnail_uri"
  };

  private static final String[] METADATA = {
      TvContract.PreviewPrograms.COLUMN_RELEASE_DATE,
      TvContract.PreviewPrograms.COLUMN_REVIEW_RATING,
      TvContract.PreviewPrograms.COLUMN_REVIEW_RATING_STYLE,
      TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
      TvContract.PreviewPrograms.COLUMN_CANONICAL_GENRE,
      TvContract.PreviewPrograms.COLUMN_LONG_DESCRIPTION,
      TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION
  };

  private static void readPrograms(Context context, Uri uri, Channel channel, Result result) {
    String[] projection = Arrays.copyOf(BASIC, BASIC.length + METADATA.length);
    System.arraycopy(METADATA, 0, projection, BASIC.length, METADATA.length);
    int skipped = result.skippedWithoutLink;
    int displayed = result.displayedPrograms;
    try {
      readPrograms(context, uri, projection, channel, result);
    } catch (RuntimeException unsupported) {
      if (unsupported instanceof SecurityException) throw unsupported;
      channel.programs.clear();
      result.skippedWithoutLink = skipped;
      result.displayedPrograms = displayed;
      readPrograms(context, uri, BASIC, channel, result);
    }
  }

  private static void readPrograms(Context context, Uri uri, String[] projection, Channel channel,
      Result result) {
    try (Cursor c = context.getContentResolver().query(uri, projection, null, null, null)) {
      if (c == null) return;
      while (c.moveToNext() && channel.programs.size() < 12) {
        String intent = field(c, "intent_uri");
        if (intent.isEmpty()) {
          result.skippedWithoutLink++;
          continue;
        }
        String artwork = field(c, "poster_art_uri");
        if (artwork.isEmpty()) artwork = field(c, "thumbnail_uri");
        String pkg = field(c, "package_name");
        String description = field(c, METADATA[5]);
        if (description.isEmpty()) description = field(c, METADATA[6]);
        String title = field(c, "title");
        channel.programs.add(new Program(title.isEmpty() ? null : title, artwork, intent,
            pkg.isEmpty() ? channel.pkg : pkg, facts(c), description));
        result.displayedPrograms++;
      }
    }
  }

  private static String field(Cursor c, String name) {
    int index = c.getColumnIndex(name);
    if (index < 0 || c.isNull(index)) return "";
    return c.getString(index).trim();
  }

  private static String facts(Cursor c) {
    List<String> values = new ArrayList<>();
    String date = field(c, METADATA[0]);
    if (date.matches("[0-9]{4}(-[0-9]{2}-[0-9]{2}.*)?")) values.add(date.substring(0, 4));
    String rating = rating(field(c, METADATA[1]), field(c, METADATA[2]));
    if (!rating.isEmpty()) values.add(rating);
    String season = field(c, METADATA[3]);
    if (!season.isEmpty()) values.add("Сезон " + season);
    List<String> genres = new ArrayList<>();
    for (String genre : TvContract.Programs.Genres.decode(field(c, METADATA[4]))) {
      switch (genre) {
        case "DRAMA": genres.add("Драма"); break;
        case "COMEDY": genres.add("Комедия"); break;
        case "ENTERTAINMENT": genres.add("Развлекательное"); break;
        case "MOVIES": genres.add("Кино"); break;
        case "FAMILY_KIDS": genres.add("Семейное"); break;
        case "SPORTS": genres.add("Спорт"); break;
        case "NEWS": genres.add("Новости"); break;
        case "MUSIC": genres.add("Музыка"); break;
        case "ARTS": genres.add("Искусство"); break;
        case "DOCUMENTARY": genres.add("Документальное"); break;
        case "EDUCATION": genres.add("Образование"); break;
        case "GAMING": genres.add("Игры"); break;
        case "LIFE_STYLE": genres.add("Образ жизни"); break;
        case "SHOPPING": genres.add("Покупки"); break;
        case "TRAVEL": genres.add("Путешествия"); break;
        case "TECH_SCIENCE": genres.add("Наука и технологии"); break;
        case "ANIMAL_WILDLIFE": genres.add("Животные и природа"); break;
        default: if (!genre.trim().isEmpty()) genres.add(genre);
      }
    }
    if (!genres.isEmpty()) values.add(android.text.TextUtils.join(", ", genres));
    return android.text.TextUtils.join(" · ", values);
  }

  private static String rating(String value, String style) {
    if (value.isEmpty()) return "";
    try {
      switch (Integer.parseInt(style)) {
        case TvContract.PreviewPrograms.REVIEW_RATING_STYLE_STARS:
          double stars = Double.parseDouble(value);
          return stars >= 0 && stars <= 5 ? "★ " + value + "/5" : "";
        case TvContract.PreviewPrograms.REVIEW_RATING_STYLE_PERCENTAGE:
          double percent = Double.parseDouble(value);
          return percent >= 0 && percent <= 100 ? value + "%" : "";
        case TvContract.PreviewPrograms.REVIEW_RATING_STYLE_THUMBS_UP_DOWN:
          String[] votes = value.split(",");
          return votes.length == 2 && Long.parseLong(votes[0].trim()) >= 0
              && Long.parseLong(votes[1].trim()) >= 0 ? "↑ " + votes[0].trim() + " / ↓ " + votes[1].trim() : "";
        default: return "";
      }
    } catch (NumberFormatException invalid) {
      return "";
    }
  }
}
