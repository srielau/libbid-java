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
 * log / log2 / log10 / log1p kernels (DPML {@code dpml_ux_log.c} family).
 */
public final class DpmlLog {
  private DpmlLog() {
  }

  public static Binary128 log(Binary128 x, RoundingMode mode, StatusFlags st) {
    Unpacked a = UxOps.unpack(x);
    if (a.isNaN()) {
      if (a.signaling) {
        st.raise(StatusFlags.INVALID);
      }
      return Binary128.canonicalNaN(false);
    }
    if (a.sign != 0 && !a.isZero()) {
      st.raise(StatusFlags.INVALID);
      return Binary128.canonicalNaN(false);
    }
    if (a.isZero()) {
      st.raise(StatusFlags.DIVIDE_BY_ZERO);
      return Binary128.NEGATIVE_INFINITY;
    }
    if (a.isInfinite()) {
      return Binary128.POSITIVE_INFINITY;
    }
    UxOps.normalize(a);
    int k = a.exponent - 1;
    a.exponent = 1;
    Unpacked one = UxOps.unpack(Binary128.ONE);
    Unpacked f = new Unpacked();
    Unpacked tmp = new Unpacked();
    KernelEval.sub(a, one, f, st);
    Unpacked lp = new Unpacked();
    KernelEval.log1pSeries(f, lp, st);
    Unpacked kn = KernelEval.fromInt(k);
    Unpacked ln2 = UxOps.unpack(IeeeConstants.LN2);
    KernelEval.mul(kn, ln2, tmp, st);
    Unpacked r = new Unpacked();
    KernelEval.add(tmp, lp, r, st);
    return UxOps.pack(r, mode, st);
  }

  public static Binary128 log2(Binary128 x, RoundingMode mode, StatusFlags st) {
    Binary128 ln = log(x, mode, new StatusFlags());
    return UxOps.div(ln, IeeeConstants.LN2, mode, st);
  }

  public static Binary128 log10(Binary128 x, RoundingMode mode, StatusFlags st) {
    Binary128 ln = log(x, mode, new StatusFlags());
    return UxOps.div(ln, IeeeConstants.LN10, mode, st);
  }

  public static Binary128 log1p(Binary128 x, RoundingMode mode, StatusFlags st) {
    return log(UxOps.add(x, Binary128.ONE, mode, new StatusFlags()), mode, st);
  }
}
