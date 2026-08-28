/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in LICENSE-INTEL
 * are met.
 */
package org.bidfp.binary128;

/**
 * Packed IEEE 754 binary128 (two {@code long}s, high then low).
 *
 * <p>This type is the public seam for the DPML kernels. Arithmetic and
 * libm functions land in this module; BID convert wrappers stay in
 * {@code org.bidfp}.
 */
public final class Binary128 {
  public static final int BIAS = 16383;
  public static final int SIGNIFICAND_BITS = 112;
  public static final long MASK_SIGN = 0x8000_0000_0000_0000L;
  public static final long MASK_EXPONENT = 0x7fff_0000_0000_0000L;
  public static final long MASK_FRACTION_HIGH = 0x0000_ffff_ffff_ffffL;

  private final long high;
  private final long low;

  private Binary128(long high, long low) {
    this.high = high;
    this.low = low;
  }

  public static Binary128 fromRawBits(long high, long low) {
    return new Binary128(high, low);
  }

  public long highBits() {
    return high;
  }

  public long lowBits() {
    return low;
  }

  public boolean isSigned() {
    return (high & MASK_SIGN) != 0L;
  }

  public int biasedExponent() {
    return (int) ((high & MASK_EXPONENT) >>> 48);
  }

  public boolean isNaN() {
    return biasedExponent() == 0x7fff
        && ((high & MASK_FRACTION_HIGH) != 0L || low != 0L);
  }

  public boolean isInfinite() {
    return biasedExponent() == 0x7fff
        && (high & MASK_FRACTION_HIGH) == 0L
        && low == 0L;
  }

  public boolean isFinite() {
    return biasedExponent() != 0x7fff;
  }

  public boolean isZero() {
    return (high & ~MASK_SIGN) == 0L && low == 0L;
  }

  public void store(long[] out) {
    out[0] = high;
    out[1] = low;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Binary128 value)) {
      return false;
    }
    return high == value.high && low == value.low;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(high) * 31 + Long.hashCode(low);
  }

  @Override
  public String toString() {
    return String.format("0x%016x%016x", high, low);
  }
}
