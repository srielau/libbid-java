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
import org.bidfp.binary128.tables.FourOverPi;
import org.bidfp.binary128.tables.TrigX;

/**
 * QUAD UX Payne-Hanek radian reduction from {@code dpml_ux_radian_reduce.c}.
 *
 * <p>The Intel digit convolution is expressed as one arbitrary-precision
 * integer product. This preserves the same bit-indexed 4/pi reduction without
 * converting the (potentially enormous) quotient to a machine integer.
 */
final class UxRadianReduce {
  private static final int TABLE_BITS = FourOverPi.LENGTH * Long.SIZE;
  private static final int FOUR_OVER_PI_BINARY_POINT =
      TABLE_BITS - FourOverPi.FOUR_OV_PI_ZERO_PAD_LEN - 1;
  private static final BigInteger FOUR = BigInteger.valueOf(4);
  private static final BigInteger FOUR_OVER_PI = tableInteger();

  private UxRadianReduce() {
  }

  static int reduce(
      Unpacked argument, int octant, Unpacked reduced, StatusFlags status) {
    if (argument.exponent < 0) {
      return reduceSmall(argument, octant, reduced, status);
    }

    BigInteger fraction = unsigned128(argument.fracHi, argument.fracLo);
    BigInteger numerator = fraction.multiply(FOUR_OVER_PI);
    int denominatorShift =
        FOUR_OVER_PI_BINARY_POINT + 128 - argument.exponent;
    if (argument.sign != 0) {
      numerator = numerator.negate();
    }
    if (octant != 0) {
      numerator = numerator.add(
          BigInteger.valueOf(octant).shiftLeft(denominatorShift));
    }

    BigInteger quotient = nearestEvenPowerOfTwo(numerator, denominatorShift + 1);
    BigInteger remainder =
        numerator.subtract(quotient.shiftLeft(denominatorShift + 1));
    rationalToUnpacked(remainder, denominatorShift, reduced);

    Unpacked piOverFour =
        UxTable.readUxFloat(TrigX.TABLE, TrigX.UX_PI_OVER_FOUR);
    Unpacked radians = new Unpacked();
    UxOps.mulUnpacked(reduced, piOverFour, radians, status);
    reduced.copyFrom(radians);
    return quotient.mod(FOUR).intValue();
  }

  private static int reduceSmall(
      Unpacked argument, int octant, Unpacked reduced, StatusFlags status) {
    int effectiveOctant = octant + (argument.sign != 0 ? -1 : 0);
    effectiveOctant += effectiveOctant & 1;
    int quadrant = effectiveOctant >> 1;
    int adjustment = octant - effectiveOctant;
    if (adjustment == 0) {
      reduced.copyFrom(argument);
      return quadrant;
    }

    Unpacked piOverFour =
        UxTable.readUxFloat(TrigX.TABLE, TrigX.UX_PI_OVER_FOUR);
    if (adjustment < 0) {
      UxOps.negate(piOverFour);
    }
    UxOps.addsubUnpacked(argument, piOverFour, reduced, status);
    return quadrant;
  }

  private static BigInteger nearestEvenPowerOfTwo(BigInteger value, int shift) {
    boolean negative = value.signum() < 0;
    BigInteger magnitude = value.abs();
    BigInteger quotient = magnitude.shiftRight(shift);
    BigInteger remainder =
        magnitude.subtract(quotient.shiftLeft(shift));
    BigInteger half = BigInteger.ONE.shiftLeft(shift - 1);
    int compare = remainder.compareTo(half);
    if (compare > 0 || (compare == 0 && quotient.testBit(0))) {
      quotient = quotient.add(BigInteger.ONE);
    }
    return negative ? quotient.negate() : quotient;
  }

  private static void rationalToUnpacked(
      BigInteger numerator, int denominatorShift, Unpacked result) {
    if (numerator.signum() == 0) {
      result.setZero(0);
      return;
    }
    int sign = numerator.signum() < 0 ? Unpacked.UX_SIGN_BIT : 0;
    BigInteger magnitude = numerator.abs();
    int bits = magnitude.bitLength();
    BigInteger fraction;
    if (bits > 128) {
      int discarded = bits - 128;
      fraction = magnitude.shiftRight(discarded);
      if (magnitude.getLowestSetBit() < discarded) {
        fraction = fraction.setBit(0);
      }
    } else {
      fraction = magnitude.shiftLeft(128 - bits);
    }
    result.setNorm(
        sign,
        bits - denominatorShift,
        fraction.shiftRight(64).longValue(),
        fraction.longValue());
  }

  private static BigInteger tableInteger() {
    BigInteger value = BigInteger.ZERO;
    for (long word : FourOverPi.TABLE) {
      value = value.shiftLeft(Long.SIZE).or(unsigned(word));
    }
    return value;
  }

  private static BigInteger unsigned128(long high, long low) {
    return unsigned(high).shiftLeft(64).or(unsigned(low));
  }

  private static BigInteger unsigned(long value) {
    return new BigInteger(Long.toUnsignedString(value));
  }
}
