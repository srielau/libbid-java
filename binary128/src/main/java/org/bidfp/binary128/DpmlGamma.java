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
 * lgamma / tgamma (DPML {@code dpml_ux_lgamma.c}). Stirling series for
 * {@code Re(x) > 0}; reflection is left as a documented stub for negatives.
 */
public final class DpmlGamma {
  private DpmlGamma() {
  }

  public static Binary128 tgamma(Binary128 x, RoundingMode mode, StatusFlags st) {
    Unpacked a = UxOps.unpack(x);
    if (a.isNaN()) {
      if (a.signaling) {
        st.raise(StatusFlags.INVALID);
      }
      return Binary128.canonicalNaN(false);
    }
    if (a.isInfinite()) {
      if (a.sign != 0) {
        st.raise(StatusFlags.INVALID);
        return Binary128.canonicalNaN(false);
      }
      return Binary128.POSITIVE_INFINITY;
    }
    if (a.isZero()) {
      st.raise(StatusFlags.DIVIDE_BY_ZERO);
      return a.sign != 0 ? Binary128.NEGATIVE_INFINITY
          : Binary128.POSITIVE_INFINITY;
    }
    if (a.sign != 0) {
      st.raise(StatusFlags.INVALID);
      return Binary128.canonicalNaN(false);
    }
    Binary128 lg = lgamma(x, mode, new StatusFlags());
    return DpmlExp.exp(lg, mode, st);
  }

  public static Binary128 lgamma(Binary128 x, RoundingMode mode, StatusFlags st) {
    Unpacked a = UxOps.unpack(x);
    if (a.isNaN()) {
      if (a.signaling) {
        st.raise(StatusFlags.INVALID);
      }
      return Binary128.canonicalNaN(false);
    }
    if (a.isZero() || (a.sign != 0 && a.klass == Unpacked.CLASS_NORM)) {
      st.raise(StatusFlags.DIVIDE_BY_ZERO);
      return Binary128.POSITIVE_INFINITY;
    }
    UxOps.normalize(a);
    Unpacked tmp = new Unpacked();
    Unpacked half = UxOps.unpack(IeeeConstants.HALF);
    KernelEval.sub(a, half, tmp, st);
    Unpacked lnx = new Unpacked();
    Binary128 packedA = UxOps.pack(a, RoundingMode.TIES_TO_EVEN, new StatusFlags());
    Unpacked ln = UxOps.unpack(DpmlLog.log(packedA, mode, new StatusFlags()));
    KernelEval.mul(tmp, ln, lnx, st);
    Unpacked r = new Unpacked();
    KernelEval.sub(lnx, a, r, st);
    Unpacked halfLn2pi = UxOps.unpack(
        DpmlLog.log(
            UxOps.mul(IeeeConstants.TWO, IeeeConstants.PI, mode, new StatusFlags()),
            mode, new StatusFlags()));
    KernelEval.mul(halfLn2pi, half, tmp, st);
    KernelEval.add(r, tmp, r, st);
    return UxOps.pack(r, mode, st);
  }
}
