package ru.novatv.launcher;

/** Pixel-independent geometry shared by layout and tests. */
final class Appearance {
  static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  static int cardHeight(int width, int ratio, boolean title) {
    int safe = clamp(width, 60, 240);
    int image = ratio == 3 ? safe * 3 / 2 : ratio == 1 ? safe : ratio == 2 ? safe * 3 / 4 : safe * 9 / 16;
    return image + (title ? 24 : 0) + 8;
  }

  static int tileHeight(int width, int ratio) {
    return cardHeight(width, ratio, false) - 8;
  }

  static int neighbor(int index, int count, int columns, int rows, int dx, int dy) {
    if (index < 0 || index >= count || columns < 1 || rows < 1) return -1;
    int row = (index / columns) % rows,
        col = index % columns,
        page = index / (columns * rows),
        target = index;
    if (dy < 0) {
      if (row == 0) return -1;
      target -= columns;
    } else if (dy > 0) {
      if (row == rows - 1) return -1;
      target += columns;
    } else if (dx < 0) {
      if (col > 0) target--;
      else if (page > 0) target -= (rows - 1) * columns + 1;
      else return -1;
    } else if (dx > 0) {
      target += col < columns - 1 ? 1 : (rows - 1) * columns + 1;
    }
    return target >= 0 && target < count ? target : -1;
  }
}
