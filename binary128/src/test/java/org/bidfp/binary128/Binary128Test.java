package org.bidfp.binary128;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class Binary128Test {
  private static final RoundingMode RN = RoundingMode.TIES_TO_EVEN;
  private static final Binary128 TWO =
      Binary128.fromRawBits(0x4000_0000_0000_0000L, 0L);

  @Test
  void classifiesZeroInfAndNan() {
    Binary128Check.main(new String[0]);
  }

  @Test
  void packedLayoutMatchesIntelHighLow() {
    Binary128 one = Binary128.ONE;
    assertEquals(0x3fff_0000_0000_0000L, one.highBits());
    assertEquals(0L, one.lowBits());
    assertEquals(0x3fff, one.biasedExponent());
    assertEquals(0L, one.fractionHigh());
    assertEquals(1L << 48, one.significandHigh());
    assertFalse(one.isSigned());
    assertTrue(one.isNormal());
  }

  @Test
  void binary64RoundTripsExactly() {
    long[] samples = {
        0x0000_0000_0000_0000L,
        0x8000_0000_0000_0000L,
        0x0000_0000_0000_0001L,
        0x000f_ffff_ffff_ffffL,
        0x0010_0000_0000_0000L,
        0x3ff8_0000_0000_0000L,
        0x7fef_ffff_ffff_ffffL,
        0xffef_ffff_ffff_ffffL
    };
    for (long bits : samples) {
      double input = Double.longBitsToDouble(bits);
      StatusFlags status = new StatusFlags();
      double output = Binary128.fromBinary64(input).toBinary64(RN, status);
      assertEquals(bits, Double.doubleToRawLongBits(output));
      assertEquals(0, status.bits());
    }
  }

  @Test
  void fromBinary64ConvertsSmallestSubnormalExactly() {
    assertEquals(
        Binary128.fromRawBits(0x3bcd_0000_0000_0000L, 0L),
        Binary128.fromBinary64(Double.MIN_VALUE));
  }

  @Test
  void binary64TieHonorsAllFiveModes() {
    Binary128 halfway = Binary128.fromRawBits(
        0x3fff_0000_0000_0000L, 1L << 59);
    assertDoubleBits(0x3ff0_0000_0000_0000L, halfway, RoundingMode.TIES_TO_EVEN);
    assertDoubleBits(0x3ff0_0000_0000_0000L, halfway, RoundingMode.TOWARD_NEGATIVE);
    assertDoubleBits(0x3ff0_0000_0000_0001L, halfway, RoundingMode.TOWARD_POSITIVE);
    assertDoubleBits(0x3ff0_0000_0000_0000L, halfway, RoundingMode.TOWARD_ZERO);
    assertDoubleBits(0x3ff0_0000_0000_0001L, halfway, RoundingMode.TIES_AWAY);

    Binary128 negativeHalfway = halfway.negate();
    assertDoubleBits(
        0xbff0_0000_0000_0000L, negativeHalfway, RoundingMode.TIES_TO_EVEN);
    assertDoubleBits(
        0xbff0_0000_0000_0001L, negativeHalfway, RoundingMode.TOWARD_NEGATIVE);
    assertDoubleBits(
        0xbff0_0000_0000_0000L, negativeHalfway, RoundingMode.TOWARD_POSITIVE);
    assertDoubleBits(
        0xbff0_0000_0000_0000L, negativeHalfway, RoundingMode.TOWARD_ZERO);
    assertDoubleBits(
        0xbff0_0000_0000_0001L, negativeHalfway, RoundingMode.TIES_AWAY);
  }

  @Test
  void binary64GradualUnderflowAndPromotionAreRounded() {
    Binary128 minimumNormal = Binary128.fromBinary64(Double.MIN_NORMAL);
    Binary128 halfMinimumSubnormal =
        Binary128.fromRawBits(0x3bcc_0000_0000_0000L, 0L);
    Binary128 midpoint = minimumNormal.subtract(
        halfMinimumSubnormal, RN, new StatusFlags());

    StatusFlags nearestStatus = new StatusFlags();
    double nearest = midpoint.toBinary64(RN, nearestStatus);
    assertEquals(0x0010_0000_0000_0000L, Double.doubleToRawLongBits(nearest));
    assertEquals(StatusFlags.INEXACT, nearestStatus.bits());

    StatusFlags zeroStatus = new StatusFlags();
    double towardZero = midpoint.toBinary64(RoundingMode.TOWARD_ZERO, zeroStatus);
    assertEquals(0x000f_ffff_ffff_ffffL, Double.doubleToRawLongBits(towardZero));
    assertEquals(
        StatusFlags.UNDERFLOW | StatusFlags.INEXACT,
        zeroStatus.bits());
  }

  @Test
  void binary64OverflowHonorsDirectedModes() {
    Binary128 twoTo1024 =
        Binary128.fromRawBits(0x43ff_0000_0000_0000L, 0L);
    StatusFlags nearestStatus = new StatusFlags();
    assertEquals(
        Double.POSITIVE_INFINITY,
        twoTo1024.toBinary64(RN, nearestStatus));
    assertEquals(
        StatusFlags.OVERFLOW | StatusFlags.INEXACT,
        nearestStatus.bits());

    StatusFlags zeroStatus = new StatusFlags();
    assertEquals(
        Double.MAX_VALUE,
        twoTo1024.toBinary64(RoundingMode.TOWARD_ZERO, zeroStatus));
    assertEquals(
        StatusFlags.OVERFLOW | StatusFlags.INEXACT,
        zeroStatus.bits());
  }

  @Test
  void binary128SubnormalRoundsUpToMinimumNormal() {
    Binary128 largestSubnormal =
        Binary128.fromRawBits(0x0000_ffff_ffff_ffffL, -1L);
    Binary128 minimumNormal =
        Binary128.fromRawBits(0x0001_0000_0000_0000L, 0L);
    Binary128 sum = largestSubnormal.add(minimumNormal, RN, new StatusFlags());

    assertEquals(
        minimumNormal,
        sum.divide(TWO, RN, new StatusFlags()));
    assertEquals(
        largestSubnormal,
        sum.divide(TWO, RoundingMode.TOWARD_ZERO, new StatusFlags()));
  }

  @Test
  void arithmeticRoundsExactResultsAndTies() {
    Binary128 halfUlp =
        Binary128.fromFields(false, 0x3fff - 113, 0L, 0L);
    Binary128 next = Binary128.fromRawBits(
        Binary128.ONE.highBits(), Binary128.ONE.lowBits() + 1);
    assertEquals(
        Binary128.ONE,
        Binary128.ONE.add(halfUlp, RN, new StatusFlags()));
    assertEquals(
        next,
        Binary128.ONE.add(
            halfUlp, RoundingMode.TOWARD_POSITIVE, new StatusFlags()));

    Binary128 oneThird = Binary128.fromRawBits(
        0x3ffd_5555_5555_5555L, 0x5555_5555_5555_5555L);
    assertEquals(
        oneThird,
        Binary128.ONE.divide(
            Binary128.fromBinary64(3.0), RN, new StatusFlags()));
    assertEquals(
        TWO,
        TWO.multiply(Binary128.ONE, RN, new StatusFlags()));
  }

  @Test
  void squareRootUsesRemainderForDirectedRounding() {
    Binary128 lower = Binary128.fromRawBits(
        0x3fff_6a09_e667_f3bcL, 0xc908_b2fb_1366_ea95L);
    Binary128 upper = Binary128.fromRawBits(
        0x3fff_6a09_e667_f3bcL, 0xc908_b2fb_1366_ea96L);
    StatusFlags downStatus = new StatusFlags();
    StatusFlags upStatus = new StatusFlags();
    assertEquals(lower, TWO.sqrt(RoundingMode.TOWARD_ZERO, downStatus));
    assertEquals(upper, TWO.sqrt(RoundingMode.TOWARD_POSITIVE, upStatus));
    assertEquals(StatusFlags.INEXACT, downStatus.bits());
    assertEquals(StatusFlags.INEXACT, upStatus.bits());
  }

  @Test
  void cancellationZeroSignFollowsRoundingMode() {
    assertEquals(
        Binary128.NEGATIVE_ZERO,
        Binary128.ONE.add(
            Binary128.ONE.negate(),
            RoundingMode.TOWARD_NEGATIVE,
            new StatusFlags()));
    assertEquals(
        Binary128.ZERO,
        Binary128.ONE.add(
            Binary128.ONE.negate(),
            RoundingMode.TOWARD_POSITIVE,
            new StatusFlags()));
    assertEquals(
        Binary128.NEGATIVE_ZERO,
        Binary128.NEGATIVE_ZERO.add(
            Binary128.NEGATIVE_ZERO, RN, new StatusFlags()));
  }

  @Test
  void nanPayloadIsQuietedAndPreserved() {
    Binary128 signaling = Binary128.fromRawBits(
        0xffff_1234_5678_9abcL, 0xdef0_1234_5678_9abcL);
    StatusFlags status = new StatusFlags();
    Binary128 result = signaling.add(Binary128.ONE, RN, status);
    assertEquals(
        Binary128.fromRawBits(
            0xffff_9234_5678_9abcL, 0xdef0_1234_5678_9abcL),
        result);
    assertEquals(StatusFlags.INVALID, status.bits());

    StatusFlags packStatus = new StatusFlags();
    assertEquals(result, UxOps.pack(UxOps.unpack(signaling), RN, packStatus));
    assertEquals(StatusFlags.INVALID, packStatus.bits());
  }

  @Test
  void specialArithmeticRaisesExactFlags() {
    StatusFlags divideStatus = new StatusFlags();
    assertEquals(
        Binary128.POSITIVE_INFINITY,
        Binary128.ONE.divide(Binary128.ZERO, RN, divideStatus));
    assertEquals(StatusFlags.DIVIDE_BY_ZERO, divideStatus.bits());

    StatusFlags invalidStatus = new StatusFlags();
    assertTrue(
        Binary128.ZERO.multiply(
            Binary128.POSITIVE_INFINITY, RN, invalidStatus).isNaN());
    assertEquals(StatusFlags.INVALID, invalidStatus.bits());

    StatusFlags denormalStatus = new StatusFlags();
    Binary128 minimumSubnormal = Binary128.fromRawBits(0L, 1L);
    minimumSubnormal.add(Binary128.ZERO, RN, denormalStatus);
    assertTrue(denormalStatus.contains(StatusFlags.DENORMAL));
  }

  @Test
  void binary128OverflowHonorsAllModes() {
    assertOverflow(Binary128.POSITIVE_INFINITY, RoundingMode.TIES_TO_EVEN);
    assertOverflow(Binary128.POSITIVE_INFINITY, RoundingMode.TIES_AWAY);
    assertOverflow(Binary128.POSITIVE_INFINITY, RoundingMode.TOWARD_POSITIVE);
    assertOverflow(Binary128.POSITIVE_MAX, RoundingMode.TOWARD_NEGATIVE);
    assertOverflow(Binary128.POSITIVE_MAX, RoundingMode.TOWARD_ZERO);
  }

  private static void assertDoubleBits(
      long expected, Binary128 value, RoundingMode mode) {
    StatusFlags status = new StatusFlags();
    assertEquals(
        expected,
        Double.doubleToRawLongBits(value.toBinary64(mode, status)));
    assertEquals(StatusFlags.INEXACT, status.bits());
  }

  private static void assertOverflow(Binary128 expected, RoundingMode mode) {
    StatusFlags status = new StatusFlags();
    assertEquals(expected, Binary128.POSITIVE_MAX.multiply(TWO, mode, status));
    assertEquals(
        StatusFlags.OVERFLOW | StatusFlags.INEXACT,
        status.bits());
  }
}
