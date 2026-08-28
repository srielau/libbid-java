/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in LICENSE-INTEL
 * are met.
 */
package org.bidfp.binary128;

import java.math.BigInteger;

/** Family-private exact classification and exponent helpers for pow and cbrt. */
final class DpmlPowCbrtSupport {
  private static final BigInteger MASK_64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

  private DpmlPowCbrtSupport() {
  }

  static Binary128 quietNaN(Binary128 value, StatusFlags status) {
    if (value.isSignalingNaN()) {
      status.raise(StatusFlags.INVALID);
    }
    return Binary128.fromRawBits(
        value.highBits() | Binary128.QUIET_NAN_BIT, value.lowBits());
  }

  static int compareAbsToOne(Binary128 value) {
    int exponent = value.biasedExponent();
    if (exponent != Binary128.BIAS) {
      return Integer.compare(exponent, Binary128.BIAS);
    }
    return value.fractionHigh() == 0L && value.fractionLow() == 0L ? 0 : 1;
  }

  /**
   * Returns 0 for non-integral, 1 for even integral, and 2 for odd integral.
   * This examines the packed significand, so very large representable values
   * are classified without conversion through a Java primitive.
   */
  static int integerKind(Binary128 value) {
    if (!value.isFinite()) {
      return 0;
    }
    if (value.isZero()) {
      return 1;
    }
    int e = value.biasedExponent() - Binary128.BIAS;
    if (e < 0) {
      return 0;
    }
    if (e > Binary128.SIGNIFICAND_BITS) {
      return 1;
    }
    BigInteger sig = unsigned(value.significandHigh()).shiftLeft(64)
        .or(unsigned(value.significandLow()));
    int fractionalBits = Binary128.SIGNIFICAND_BITS - e;
    if (fractionalBits != 0
        && sig.and(BigInteger.ONE.shiftLeft(fractionalBits).subtract(BigInteger.ONE))
            .signum() != 0) {
      return 0;
    }
    return sig.testBit(fractionalBits) ? 2 : 1;
  }

  /** Round a finite UX value to the nearest integer, ties to even. */
  static int nearestInt(Unpacked value) {
    Unpacked u = value.copy();
    UxOps.normalize(u);
    if (u.exponent <= 0) {
      return 0;
    }
    if (u.exponent > 31) {
      return u.sign != 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
    BigInteger sig = unsigned(u.fracHi).shiftLeft(64).or(unsigned(u.fracLo));
    int shift = 128 - u.exponent;
    BigInteger integer = sig.shiftRight(shift);
    if (shift > 0) {
      BigInteger remainder = sig.and(BigInteger.ONE.shiftLeft(shift).subtract(BigInteger.ONE));
      BigInteger half = BigInteger.ONE.shiftLeft(shift - 1);
      int cmp = remainder.compareTo(half);
      if (cmp > 0 || (cmp == 0 && integer.testBit(0))) {
        integer = integer.add(BigInteger.ONE);
      }
    }
    int result = integer.intValue();
    return u.sign != 0 ? -result : result;
  }

  static double normalizedFractionAsDouble(Unpacked value) {
    long bits = 0x3ff0_0000_0000_0000L
        | ((value.fracHi >>> 11) & 0x000f_ffff_ffff_ffffL);
    return Double.longBitsToDouble(bits);
  }

  static int floorDiv3(int value) {
    int quotient = value / 3;
    return value < 0 && value % 3 != 0 ? quotient - 1 : quotient;
  }

  static int floorMod3(int value) {
    return value - 3 * floorDiv3(value);
  }

  private static BigInteger unsigned(long value) {
    BigInteger result = BigInteger.valueOf(value);
    return value < 0L ? result.add(MASK_64).add(BigInteger.ONE) : result;
  }
}
