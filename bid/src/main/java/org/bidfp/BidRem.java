/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the conditions in LICENSE-INTEL are met.
 */
package org.bidfp;

final class BidRem {
  private BidRem() {
  }

  static long rem64(long x, long y, StatusFlags flags) {
    if (Bid64Raw.isNaN(x) || Bid64Raw.isNaN(y)) {
      if (Bid64Raw.isSignalingNaN(x) || Bid64Raw.isSignalingNaN(y)) {
        flags.raise(StatusFlags.INVALID);
      }
      return BidIntegral.canonicalizeNaN64(
          Bid64Raw.isNaN(x) ? x : y, new StatusFlags());
    }
    if (Bid64Raw.isInf(x) || Bid64Raw.isZero(y)) {
      flags.raise(StatusFlags.INVALID);
      return Bid64.MASK_NAN;
    }
    if (Bid64Raw.isZero(x)) {
      int exponent = Bid64.biasedExponentBits(x);
      if (Bid64Raw.isFinite(y)) {
        exponent = Math.min(exponent, Bid64.biasedExponentBits(y));
      }
      return Bid64.finiteRawBits(Bid64Raw.isSigned(x), exponent, 0L);
    }
    if (Bid64Raw.isInf(y)) {
      return x;
    }
    DecNum result = remainder(
        DecNum.ofCoefficient(false, Bid64.significandBits(x),
            Bid64.biasedExponentBits(x) - 398),
        DecNum.ofCoefficient(false, Bid64.significandBits(y),
            Bid64.biasedExponentBits(y) - 398),
        Bid64Raw.isSigned(x),
        true);
    return result.packBid64(RoundingMode.TIES_TO_EVEN, flags);
  }

  static long fmod64(long x, long y, StatusFlags flags) {
    if (Bid64Raw.isNaN(x) || Bid64Raw.isNaN(y)) {
      if (Bid64Raw.isSignalingNaN(x) || Bid64Raw.isSignalingNaN(y)) {
        flags.raise(StatusFlags.INVALID);
      }
      return BidIntegral.canonicalizeNaN64(
          Bid64Raw.isNaN(x) ? x : y, new StatusFlags());
    }
    if (Bid64Raw.isInf(x) || Bid64Raw.isZero(y)) {
      flags.raise(StatusFlags.INVALID);
      return Bid64.MASK_NAN;
    }
    if (Bid64Raw.isZero(x)) {
      int exponent = Bid64.biasedExponentBits(x);
      if (Bid64Raw.isFinite(y)) {
        exponent = Math.min(exponent, Bid64.biasedExponentBits(y));
      }
      return Bid64.finiteRawBits(Bid64Raw.isSigned(x), exponent, 0L);
    }
    if (Bid64Raw.isInf(y)) {
      return x;
    }
    DecNum result = remainder(
        DecNum.ofCoefficient(false, Bid64.significandBits(x),
            Bid64.biasedExponentBits(x) - 398),
        DecNum.ofCoefficient(false, Bid64.significandBits(y),
            Bid64.biasedExponentBits(y) - 398),
        Bid64Raw.isSigned(x),
        false);
    return result.packBid64(RoundingMode.TOWARD_ZERO, flags);
  }

  static void rem128(
      long xh, long xl, long yh, long yl, StatusFlags flags, long[] out) {
    Bid128 x = Bid128.fromRawBits(xh, xl);
    Bid128 y = Bid128.fromRawBits(yh, yl);
    if (x.isNaN() || y.isNaN()) {
      if (x.isSignalingNaN() || y.isSignalingNaN()) {
        flags.raise(StatusFlags.INVALID);
      }
      Bid128 nan = x.isNaN() ? x : y;
      BidIntegral.canonicalizeNaN128(
          nan.highBits(), nan.lowBits(), new StatusFlags(), out);
      return;
    }
    if (x.isInfinite() || y.isZero()) {
      flags.raise(StatusFlags.INVALID);
      DecNum.store128(Bid128.QUIET_NAN, out);
      return;
    }
    if (x.isZero()) {
      int exponent = y.isFinite()
          ? Math.min(x.biasedExponent(), y.biasedExponent())
          : x.biasedExponent();
      DecNum.store128(Bid128.finite(x.isSigned(), exponent, 0L, 0L), out);
      return;
    }
    if (y.isInfinite()) {
      out[0] = xh;
      out[1] = xl;
      return;
    }
    DecNum result = remainder(unpack128(x), unpack128(y), x.isSigned(), true);
    result.packBid128(RoundingMode.TIES_TO_EVEN, flags, out);
  }

  static void fmod128(
      long xh, long xl, long yh, long yl, StatusFlags flags, long[] out) {
    Bid128 x = Bid128.fromRawBits(xh, xl);
    Bid128 y = Bid128.fromRawBits(yh, yl);
    if (x.isNaN() || y.isNaN()) {
      if (x.isSignalingNaN() || y.isSignalingNaN()) {
        flags.raise(StatusFlags.INVALID);
      }
      Bid128 nan = x.isNaN() ? x : y;
      BidIntegral.canonicalizeNaN128(
          nan.highBits(), nan.lowBits(), new StatusFlags(), out);
      return;
    }
    if (x.isInfinite() || y.isZero()) {
      flags.raise(StatusFlags.INVALID);
      DecNum.store128(Bid128.QUIET_NAN, out);
      return;
    }
    if (x.isZero()) {
      int exponent = y.isFinite()
          ? Math.min(x.biasedExponent(), y.biasedExponent())
          : x.biasedExponent();
      DecNum.store128(Bid128.finite(x.isSigned(), exponent, 0L, 0L), out);
      return;
    }
    if (y.isInfinite()) {
      out[0] = xh;
      out[1] = xl;
      return;
    }
    DecNum result = remainder(unpack128(x), unpack128(y), x.isSigned(), false);
    result.packBid128(RoundingMode.TOWARD_ZERO, flags, out);
  }

  private static DecNum unpack128(Bid128 value) {
    UInt128 coefficient = value.coefficient();
    DecNum result = DecNum.ofUnsigned(coefficient.high(), coefficient.low());
    result.shiftExp(value.biasedExponent() - 6176);
    return result;
  }

  private static DecNum remainder(
      DecNum numerator, DecNum denominator, boolean negative, boolean nearestEven) {
    int commonExponent = Math.min(numerator.exp(), denominator.exp());
    int numeratorZeros = numerator.exp() - commonExponent;
    int denominatorZeros = denominator.exp() - commonExponent;
    String numeratorDigits = numerator.toDigits();
    String denominatorDigits = denominator.toDigits();
    int numeratorLength = numeratorDigits.length() + numeratorZeros;
    int denominatorLength = denominatorDigits.length() + denominatorZeros;
    DecNum divisor = DecNum.ofLong(0L);
    for (int i = 0; i < denominatorDigits.length(); i++) {
      divisor.multiplySmall(10);
      divisor.addDigit(denominatorDigits.charAt(i) - '0');
    }
    if (denominatorLength > numeratorLength) {
      DecNum result = DecNum.ofLong(0L);
      for (int i = 0; i < numeratorDigits.length(); i++) {
        result.multiplySmall(10);
        result.addDigit(numeratorDigits.charAt(i) - '0');
      }
      result.multiplyPow10(numeratorZeros);
      result.shiftExp(commonExponent);
      if (negative) {
        result.setNegative();
      }
      return result;
    }
    divisor.multiplyPow10(denominatorZeros);
    DecNum result = DecNum.ofLong(0L);
    int quotientLastDigit = 0;
    for (int i = 0; i < numeratorLength; i++) {
      int digit = i < numeratorDigits.length() ? numeratorDigits.charAt(i) - '0' : 0;
      result.multiplySmall(10);
      result.addDigit(digit);
      quotientLastDigit = 0;
      while (result.compareAbsolute(divisor) >= 0) {
        result.subtractAbsolute(divisor);
        quotientLastDigit++;
      }
    }
    if (nearestEven && !result.isZero()) {
      DecNum twice = DecNum.ofLong(0L);
      twice.copyFrom(result);
      twice.multiplySmall(2);
      int halfComparison = twice.compareAbsolute(divisor);
      if (halfComparison > 0 || halfComparison == 0 && (quotientLastDigit & 1) != 0) {
        DecNum rounded = DecNum.ofLong(0L);
        rounded.copyFrom(divisor);
        rounded.subtractAbsolute(result);
        result = rounded;
        negative = !negative;
      }
    }
    result.shiftExp(commonExponent);
    if (negative) {
      result.setNegative();
    }
    return result;
  }
}
