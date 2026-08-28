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
 * pow kernel (DPML {@code dpml_ux_pow.c} family): exp(y * log(x)) plus IEEE
 * special cases.
 */
public final class DpmlPow {
  private DpmlPow() {
  }

  public static Binary128 pow(
      Binary128 x, Binary128 y, RoundingMode mode, StatusFlags st) {
    if (x.isNaN() || y.isNaN()) {
      if (x.isSignalingNaN() || y.isSignalingNaN()) {
        st.raise(StatusFlags.INVALID);
      }
      return Binary128.canonicalNaN(false);
    }
    if (y.isZero()) {
      return Binary128.ONE;
    }
    if (x.equals(Binary128.ONE)) {
      return Binary128.ONE;
    }
    StatusFlags local = new StatusFlags();
    Binary128 ln = DpmlLog.log(x.abs(), mode, local);
    if (local.contains(StatusFlags.INVALID)) {
      st.raise(StatusFlags.INVALID);
      return Binary128.canonicalNaN(false);
    }
    Binary128 prod = UxOps.mul(ln, y, mode, local);
    Binary128 r = DpmlExp.exp(prod, mode, st);
    if (x.isSigned() && !x.isZero()) {
      st.raise(StatusFlags.INVALID);
      return Binary128.canonicalNaN(false);
    }
    return r;
  }
}
