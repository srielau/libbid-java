package org.bidfp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class Bid128TgammaBoundaryTest {
  private static final long INPUT_HIGH = 0x3006_0a79_8f1d_7485L;
  private static final long INPUT_LOW = 0x227a_5d54_044f_2235L;
  private static final long RESULT_HIGH = 0x5fff_ed09_bead_87c0L;
  private static final long RESULT_LOW = 0x378d_8e63_ffff_edbbL;
  private static final long MAX_LOW = 0x378d_8e63_ffff_ffffL;

  @Test
  void nearestLastFiniteResultUsesExtendedLgamma() {
    StatusFlags flags = new StatusFlags();
    long[] result = gamma(INPUT_HIGH, INPUT_LOW, RoundingMode.TIES_TO_EVEN, flags);

    assertArrayEquals(new long[] {RESULT_HIGH, RESULT_LOW}, result);
    assertTrue(flags.contains(StatusFlags.INEXACT));
    assertFalse(flags.contains(StatusFlags.OVERFLOW));
  }

  @Test
  void nextInputCrossesOverflowBoundary() {
    long[] next = new long[2];
    Bid128Raw.nextUp(INPUT_HIGH, INPUT_LOW, new StatusFlags(), next);
    assertArrayEquals(
        new long[] {0x3004_68bf_9726_8d33L, 0x58c7_a548_2b17_5613L}, next);

    StatusFlags nearestFlags = new StatusFlags();
    long[] nearest = gamma(next[0], next[1], RoundingMode.TIES_TO_EVEN, nearestFlags);
    assertEquals(Bid128.MASK_INFINITY, nearest[0]);
    assertEquals(0L, nearest[1]);
    assertTrue(nearestFlags.contains(StatusFlags.OVERFLOW));
    assertTrue(nearestFlags.contains(StatusFlags.INEXACT));

    StatusFlags downFlags = new StatusFlags();
    long[] down = gamma(next[0], next[1], RoundingMode.TOWARD_NEGATIVE, downFlags);
    assertArrayEquals(new long[] {RESULT_HIGH, MAX_LOW}, down);
    assertTrue(downFlags.contains(StatusFlags.OVERFLOW));
    assertTrue(downFlags.contains(StatusFlags.INEXACT));
  }

  private static long[] gamma(
      long high, long low, RoundingMode mode, StatusFlags flags) {
    long[] result = new long[2];
    Bid128Raw.tgamma(high, low, mode, flags, result);
    return result;
  }
}
