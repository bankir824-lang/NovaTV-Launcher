package ru.novatv.launcher;

import static org.junit.Assert.*;

import org.junit.Test;

public class AppearanceTest {
  @Test
  public void wideCardsReserveTitleSpaceOnlyWhenEnabled() {
    assertEquals(98, Appearance.cardHeight(160, 0, false));
    assertEquals(122, Appearance.cardHeight(160, 0, true));
  }

  @Test
  public void ratiosProduceExpectedGeometry() {
    assertEquals(168, Appearance.cardHeight(160, 1, false));
    assertEquals(128, Appearance.cardHeight(160, 2, false));
  }

  @Test
  public void storedOutOfRangeWidthsCannotCreateHugeCards() {
    assertEquals(Appearance.cardHeight(240, 0, true), Appearance.cardHeight(10000, 0, true));
    assertEquals(Appearance.cardHeight(60, 0, true), Appearance.cardHeight(-10, 0, true));
  }

  @Test
  public void gridArrowsRespectRowsAtPageEdges() {
    assertEquals(18, Appearance.neighbor(5, 30, 6, 3, 1, 0));
    assertEquals(5, Appearance.neighbor(18, 30, 6, 3, -1, 0));
    assertEquals(0, Appearance.neighbor(6, 30, 6, 3, 0, -1));
  }

  @Test
  public void gridArrowsCannotWrapVerticallyOrMoveIntoMissingCells() {
    assertEquals(-1, Appearance.neighbor(0, 30, 6, 3, 0, -1));
    assertEquals(-1, Appearance.neighbor(12, 30, 6, 3, 0, 1));
    assertEquals(-1, Appearance.neighbor(13, 14, 6, 3, 1, 0));
    assertEquals(-1, Appearance.neighbor(0, 30, 6, 3, -1, 0));
  }
}
