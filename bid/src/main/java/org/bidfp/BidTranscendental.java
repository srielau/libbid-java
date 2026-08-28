/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the conditions in LICENSE-INTEL are met.
 */
package org.bidfp;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * Transcendental BID64/BID128 operations via binary64 evaluation.
 *
 * <p>Provisional. Intel-identical results need the {@code binary128} module
 * DPML kernels plus BID convert in {@link BidConvert}. Do not grow this
 * {@code double} path; replace it when DPML lands.
 */
final class BidTranscendental {
  private BidTranscendental() {
  }

  static long unary64(long x, RoundingMode mode, StatusFlags flags, DoubleUnaryOperator op) {
    double in = BidConvert.toBinary64From64(x, RoundingMode.TIES_TO_EVEN, new StatusFlags());
    double out = op.applyAsDouble(in);
    return BidConvert.fromBinary64To64(out, mode, flags);
  }

  static long binary64(
      long x, long y, RoundingMode mode, StatusFlags flags, DoubleBinaryOperator op) {
    double a = BidConvert.toBinary64From64(x, RoundingMode.TIES_TO_EVEN, new StatusFlags());
    double b = BidConvert.toBinary64From64(y, RoundingMode.TIES_TO_EVEN, new StatusFlags());
    return BidConvert.fromBinary64To64(op.applyAsDouble(a, b), mode, flags);
  }

  static void unary128(
      long high, long low, RoundingMode mode, StatusFlags flags, DoubleUnaryOperator op,
      long[] out) {
    double in = BidConvert.toBinary64From128(
        high, low, RoundingMode.TIES_TO_EVEN, new StatusFlags());
    BidConvert.fromBinary64To128(op.applyAsDouble(in), mode, flags, out);
  }

  static void binary128(
      long xh, long xl, long yh, long yl,
      RoundingMode mode, StatusFlags flags, DoubleBinaryOperator op, long[] out) {
    double a = BidConvert.toBinary64From128(
        xh, xl, RoundingMode.TIES_TO_EVEN, new StatusFlags());
    double b = BidConvert.toBinary64From128(
        yh, yl, RoundingMode.TIES_TO_EVEN, new StatusFlags());
    BidConvert.fromBinary64To128(op.applyAsDouble(a, b), mode, flags, out);
  }

  static long cbrt64(long x, RoundingMode mode, StatusFlags flags) {
    return unary64(x, mode, flags, Math::cbrt);
  }

  static double asinh(double x) {
    return Math.log(x + Math.sqrt(x * x + 1.0));
  }

  static double acosh(double x) {
    return Math.log(x + Math.sqrt(x * x - 1.0));
  }

  static double atanh(double x) {
    return 0.5 * Math.log((1.0 + x) / (1.0 - x));
  }

  static double erf(double x) {
    double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
    double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741)
        * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
    return x < 0 ? -y : y;
  }

  static double tgamma(double x) {
    return Math.exp(lgamma(x));
  }

  static double lgamma(double x) {
    double[] c = {
      76.18009172947146, -86.50532032941677, 24.01409824083091,
      -1.231739572450155, 0.001208650973866179, -0.000005395239384953
    };
    double y = x;
    double tmp = x + 5.5;
    tmp -= (x + 0.5) * Math.log(tmp);
    double ser = 1.000000000190015;
    for (int j = 0; j < 6; j++) {
      ser += c[j] / ++y;
    }
    return -tmp + Math.log(2.5066282746310005 * ser / x);
  }
}
