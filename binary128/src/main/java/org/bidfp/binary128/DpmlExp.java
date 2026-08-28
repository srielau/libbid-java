/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in LICENSE-INTEL
 * are met.
 */
package org.bidfp.binary128;

import org.bidfp.binary128.tables.IeeeConstants;

/**
 * exp / expm1 / exp2 / exp10 kernels (DPML {@code dpml_ux_exp.c} family).
 * Range reduction uses ln2 / log2(e); the reduced exponential is a Taylor
 * series evaluated with unpacked add/mul/div, not {@code Math.exp}.
 */
public final class DpmlExp {
  private DpmlExp() {
  }

  public static Binary128 exp(Binary128 x, RoundingMode mode, StatusFlags st) {
    Unpacked a = UxOps.unpack(x);
    if (a.isNaN()) {
      if (a.signaling) {
        st.raise(StatusFlags.INVALID);
      }
      return Binary128.canonicalNaN(false);
    }
    if (a.isInfinite()) {
      return a.sign != 0 ? Binary128.ZERO : Binary128.POSITIVE_INFINITY;
    }
    if (a.isZero()) {
      return Binary128.ONE;
    }
    StatusFlags local = new StatusFlags();
    Unpacked nTimes = new Unpacked();
    Unpacked r = new Unpacked();
    Unpacked tmp = new Unpacked();
    Unpacked log2e = UxOps.unpack(IeeeConstants.LOG2E);
    Unpacked ln2 = UxOps.unpack(IeeeConstants.LN2);
    UxOps.mulUnpacked(a, log2e, tmp, local);
    int n = KernelEval.roundToInt(tmp);
    Unpacked ni = KernelEval.fromInt(n);
    UxOps.mulUnpacked(ni, ln2, nTimes, local);
    Unpacked na = a.copy();
    UxOps.negate(nTimes);
    UxOps.addsubUnpacked(na, nTimes, r, local);
    Unpacked series = new Unpacked();
    KernelEval.expSeries(r, series, local);
    series.exponent += n;
    return UxOps.pack(series, mode, st);
  }

  public static Binary128 exp2(Binary128 x, RoundingMode mode, StatusFlags st) {
    return exp(UxOps.mul(x, IeeeConstants.LN2, mode, new StatusFlags()), mode, st);
  }

  public static Binary128 exp10(Binary128 x, RoundingMode mode, StatusFlags st) {
    return exp(UxOps.mul(x, IeeeConstants.LN10, mode, new StatusFlags()), mode, st);
  }

  public static Binary128 expm1(Binary128 x, RoundingMode mode, StatusFlags st) {
    Binary128 e = exp(x, mode, new StatusFlags());
    return UxOps.sub(e, Binary128.ONE, mode, st);
  }
}
