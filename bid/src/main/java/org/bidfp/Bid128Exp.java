/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the conditions in LICENSE-INTEL are met.
 */
package org.bidfp;

import org.bidfp.binary128.Binary128;
import org.bidfp.binary128.Dpml;

/** Intel {@code bid128_exp.c}: NaN/Inf/0, 15000 clamps, 2-part + 11000 shift. */
final class Bid128Exp {
  private static final Bid128 EXP_11000 =
      Bid128.fromRawBits(0x5550_558a_da28_5f8bL, 0xd43e_de77_5707_fd0aL);
  private static final Bid128 EXP_M11000 =
      Bid128.fromRawBits(0x0aab_1c2b_bc58_f8f5L, 0x995a_b678_1dd4_b6f5L);
  private static final Bid128 ZERO =
      Bid128.fromRawBits(0x3040_0000_0000_0000L, 0L);
  private static final Bid128 INF =
      Bid128.fromRawBits(Bid128.MASK_INFINITY, 0L);
  private static final Bid128 ONE =
      Bid128.fromRawBits(0x3040_0000_0000_0000L, 1L);
  private static final Bid128 C_15000 =
      Bid128.fromRawBits(0x3040_0000_0000_0000L, 0x3a98L);
  private static final Bid128 C_N15000 =
      Bid128.fromRawBits(0xb040_0000_0000_0000L, 0x3a98L);
  private static final Bid128 TEN_POW_N6000 =
      Bid128.fromRawBits(0x0160_0000_0000_0000L, 1L);
  private static final Binary128 F128_11000 =
      Binary128.fromRawBits(0x400c_57c0_0000_0000L, 0L);
  private static final Binary128 F128_NEG_11000 =
      Binary128.fromRawBits(0xc00c_57c0_0000_0000L, 0L);

  private Bid128Exp() {
  }

  static void exp(
      long hi, long lo, RoundingMode mode, StatusFlags flags, long[] out) {
    Bid128 x = Bid128.fromRawBits(hi, lo);
    if (x.isNaN()) {
      if (x.isSignalingNaN()) {
        flags.raise(StatusFlags.INVALID);
      }
      long quiet = hi & 0xfc00_3fff_ffff_ffffL;
      long payloadLow = lo;
      UInt128 payload = new UInt128(quiet & 0x0000_3fff_ffff_ffffL, payloadLow);
      if (payload.compareTo(PowersOfTen.MAX_33) > 0) {
        quiet &= ~0x0000_3fff_ffff_ffffL;
        payloadLow = 0L;
      }
      out[0] = quiet;
      out[1] = payloadLow;
      return;
    }
    if (x.isInfinite()) {
      DecNum.store128(x.isSigned() ? ZERO : INF, out);
      return;
    }
    if (x.isZero()) {
      DecNum.store128(ONE, out);
      return;
    }
    if (x.quietGreater(C_15000, new StatusFlags())) {
      Bid128Raw.mul(
          EXP_11000.highBits(), EXP_11000.lowBits(),
          EXP_11000.highBits(), EXP_11000.lowBits(),
          mode, flags, out);
      return;
    }
    if (x.quietLess(C_N15000, new StatusFlags())) {
      Bid128Raw.mul(
          TEN_POW_N6000.highBits(), TEN_POW_N6000.lowBits(),
          TEN_POW_N6000.highBits(), TEN_POW_N6000.lowBits(),
          mode, flags, out);
      return;
    }

    long[] nq = new long[2];
    long[] mq = new long[2];
    BidBinary128Convert.toBinary128TwoPart(hi, lo, nq, mq);
    Binary128 high = Binary128.fromRawBits(nq[0], nq[1]);
    Binary128 low = Binary128.fromRawBits(mq[0], mq[1]);
    org.bidfp.binary128.RoundingMode binaryMode = BidTranscendental.binaryMode(mode);
    org.bidfp.binary128.StatusFlags local = new org.bidfp.binary128.StatusFlags();
    if (greater(high, F128_11000, binaryMode, local)) {
      high = Dpml.sub(high, F128_11000, binaryMode, local);
      Binary128 exp = combineExp(high, low, binaryMode, local);
      flags.raise(local.bits());
      BidConvert.fromBinary128To128(
          exp.highBits(), exp.lowBits(), mode, flags, out);
      Bid128Raw.mul(
          out[0], out[1], EXP_11000.highBits(), EXP_11000.lowBits(), mode, flags, out);
    } else if (less(high, F128_NEG_11000, binaryMode, local)) {
      high = Dpml.add(high, F128_11000, binaryMode, local);
      Binary128 exp = combineExp(high, low, binaryMode, local);
      flags.raise(local.bits());
      BidConvert.fromBinary128To128(
          exp.highBits(), exp.lowBits(), mode, flags, out);
      Bid128Raw.mul(
          out[0], out[1], EXP_M11000.highBits(), EXP_M11000.lowBits(),
          mode, flags, out);
    } else {
      Binary128 exp = combineExp(high, low, binaryMode, local);
      flags.raise(local.bits());
      BidConvert.fromBinary128To128(
          exp.highBits(), exp.lowBits(), mode, flags, out);
    }
  }

  private static Binary128 combineExp(
      Binary128 nq,
      Binary128 mq,
      org.bidfp.binary128.RoundingMode mode,
      org.bidfp.binary128.StatusFlags status) {
    Binary128 rq = Dpml.exp(nq, mode, status);
    Binary128 rt = Dpml.mul(rq, mq, mode, status);
    return Dpml.add(rq, rt, mode, status);
  }

  private static boolean greater(
      Binary128 a, Binary128 b,
      org.bidfp.binary128.RoundingMode mode,
      org.bidfp.binary128.StatusFlags status) {
    Binary128 d = Dpml.sub(a, b, mode, new org.bidfp.binary128.StatusFlags());
    return !d.isNaN() && !d.isZero() && !d.isSigned();
  }

  private static boolean less(
      Binary128 a, Binary128 b,
      org.bidfp.binary128.RoundingMode mode,
      org.bidfp.binary128.StatusFlags status) {
    Binary128 d = Dpml.sub(a, b, mode, new org.bidfp.binary128.StatusFlags());
    return !d.isNaN() && !d.isZero() && d.isSigned();
  }
}
