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
 * Public DPML facade: packed binary128 libm with explicit rounding and
 * status. Kernels live in {@code Dpml*} classes; this type is the stable
 * entry surface.
 */
public final class Dpml {
  private Dpml() {
  }

  public static Binary128 add(Binary128 x, Binary128 y, RoundingMode r, StatusFlags s) {
    return UxOps.add(x, y, r, s);
  }

  public static Binary128 sub(Binary128 x, Binary128 y, RoundingMode r, StatusFlags s) {
    return UxOps.sub(x, y, r, s);
  }

  public static Binary128 mul(Binary128 x, Binary128 y, RoundingMode r, StatusFlags s) {
    return UxOps.mul(x, y, r, s);
  }

  public static Binary128 div(Binary128 x, Binary128 y, RoundingMode r, StatusFlags s) {
    return UxOps.div(x, y, r, s);
  }

  public static Binary128 sqrt(Binary128 x, RoundingMode r, StatusFlags s) {
    return UxOps.sqrt(x, r, s);
  }

  public static Binary128 exp(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlExp.exp(x, r, s);
  }

  public static Binary128 expm1(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlExp.expm1(x, r, s);
  }

  public static Binary128 exp2(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlExp.exp2(x, r, s);
  }

  public static Binary128 exp10(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlExp.exp10(x, r, s);
  }

  public static Binary128 log(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlLog.log(x, r, s);
  }

  public static Binary128 log2(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlLog.log2(x, r, s);
  }

  public static Binary128 log10(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlLog.log10(x, r, s);
  }

  public static Binary128 log1p(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlLog.log1p(x, r, s);
  }

  public static Binary128 pow(Binary128 x, Binary128 y, RoundingMode r, StatusFlags s) {
    return DpmlPow.pow(x, y, r, s);
  }

  public static Binary128 cbrt(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlCbrt.cbrt(x, r, s);
  }

  public static Binary128 sin(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlTrig.sin(x, r, s);
  }

  public static Binary128 cos(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlTrig.cos(x, r, s);
  }

  public static Binary128 tan(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlTrig.tan(x, r, s);
  }

  public static Binary128 asin(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlInvTrig.asin(x, r, s);
  }

  public static Binary128 acos(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlInvTrig.acos(x, r, s);
  }

  public static Binary128 atan(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlInvTrig.atan(x, r, s);
  }

  public static Binary128 atan2(
      Binary128 y, Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlInvTrig.atan2(y, x, r, s);
  }

  public static Binary128 sinh(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlHyper.sinh(x, r, s);
  }

  public static Binary128 cosh(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlHyper.cosh(x, r, s);
  }

  public static Binary128 tanh(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlHyper.tanh(x, r, s);
  }

  public static Binary128 asinh(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlInvHyper.asinh(x, r, s);
  }

  public static Binary128 acosh(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlInvHyper.acosh(x, r, s);
  }

  public static Binary128 atanh(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlInvHyper.atanh(x, r, s);
  }

  public static Binary128 erf(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlErf.erf(x, r, s);
  }

  public static Binary128 erfc(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlErf.erfc(x, r, s);
  }

  public static Binary128 lgamma(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlGamma.lgamma(x, r, s);
  }

  public static Binary128 tgamma(Binary128 x, RoundingMode r, StatusFlags s) {
    return DpmlGamma.tgamma(x, r, s);
  }
}
