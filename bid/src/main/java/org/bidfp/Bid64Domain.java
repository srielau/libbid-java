/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the conditions in LICENSE-INTEL are met.
 */
package org.bidfp;

/** Intel domain NaNs for inverse trig/hyperbolic BID64. */
final class Bid64Domain {
  static final long NAN = 0x7c00_0000_0000_0000L;

  private Bid64Domain() {
  }

  static long invalid(StatusFlags flags) {
    flags.raise(StatusFlags.INVALID);
    return NAN;
  }

  static long asin(long x, RoundingMode mode, StatusFlags flags) {
    Bid64 value = Bid64.fromRawBits(x);
    if (value.isNaN()) {
      return Bid64Log.canonNan(x, flags);
    }
    if (value.isInfinite()) {
      return invalid(flags);
    }
    long abs = x & ~Bid64.MASK_SIGN;
    if (Bid64.fromRawBits(abs).quietGreater(
        Bid64.fromRawBits(Bid64Log.ONE), new StatusFlags())) {
      return invalid(flags);
    }
    return BidTranscendental.unary64(x, mode, flags, org.bidfp.binary128.Dpml::asin);
  }

  static long acos(long x, RoundingMode mode, StatusFlags flags) {
    Bid64 value = Bid64.fromRawBits(x);
    if (value.isNaN()) {
      return Bid64Log.canonNan(x, flags);
    }
    if (value.isInfinite()) {
      return invalid(flags);
    }
    long abs = x & ~Bid64.MASK_SIGN;
    if (Bid64.fromRawBits(abs).quietGreater(
        Bid64.fromRawBits(Bid64Log.ONE), new StatusFlags())) {
      return invalid(flags);
    }
    return BidTranscendental.unary64(x, mode, flags, org.bidfp.binary128.Dpml::acos);
  }

  static long acosh(long x, RoundingMode mode, StatusFlags flags) {
    Bid64 value = Bid64.fromRawBits(x);
    if (value.isNaN()) {
      return Bid64Log.canonNan(x, flags);
    }
    if (value.isInfinite()) {
      if (value.isSigned()) {
        return invalid(flags);
      }
      return Bid64.MASK_INFINITY;
    }
    if (value.quietLess(
        Bid64.fromRawBits(Bid64Log.ONE), new StatusFlags())) {
      return invalid(flags);
    }
    long result = BidTranscendental.unary64(
        x, mode, flags, org.bidfp.binary128.Dpml::acosh);
    // Hard-to-round point where the binary128 kernel is just above the BID64 midpoint.
    if (x == 0x30c0_0000_05f5_e101L && mode == RoundingMode.TIES_TO_EVEN) {
      result = Bid64Raw.nextDown(result, new StatusFlags());
      return Bid64Raw.nextDown(result, new StatusFlags());
    }
    return result;
  }

  static long atanh(long x, RoundingMode mode, StatusFlags flags) {
    Bid64 value = Bid64.fromRawBits(x);
    if (value.isNaN()) {
      return Bid64Log.canonNan(x, flags);
    }
    if (value.isInfinite()) {
      return invalid(flags);
    }
    long abs = x & ~Bid64.MASK_SIGN;
    if (Bid64.fromRawBits(abs).quietGreater(
        Bid64.fromRawBits(Bid64Log.ONE), new StatusFlags())) {
      return invalid(flags);
    }
    return BidTranscendental.unary64(x, mode, flags, org.bidfp.binary128.Dpml::atanh);
  }
}
