#include <stdint.h>
#include <stdio.h>
#include <string.h>

typedef struct { uint64_t w[2]; } f128; /* LE: w[0]=lo, w[1]=hi */

void bid_f128_add(f128 *r, f128 *a, f128 *b);
void bid_f128_sub(f128 *r, f128 *a, f128 *b);
void bid_f128_mul(f128 *r, f128 *a, f128 *b);
void bid_f128_div(f128 *r, f128 *a, f128 *b);
void bid_f128_sqrt(f128 *r, f128 *a);
void bid_f128_exp(f128 *r, f128 *a);
void bid_f128_expm1(f128 *r, f128 *a);
void bid_f128_exp2(f128 *r, f128 *a);
void bid_f128_exp10(f128 *r, f128 *a);
void bid_f128_log(f128 *r, f128 *a);
void bid_f128_log2(f128 *r, f128 *a);
void bid_f128_log10(f128 *r, f128 *a);
void bid_f128_log1p(f128 *r, f128 *a);
void bid_f128_pow(f128 *r, f128 *a, f128 *b);
void bid_f128_cbrt(f128 *r, f128 *a);
void bid_f128_sin(f128 *r, f128 *a);
void bid_f128_cos(f128 *r, f128 *a);
void bid_f128_tan(f128 *r, f128 *a);
void bid_f128_asin(f128 *r, f128 *a);
void bid_f128_acos(f128 *r, f128 *a);
void bid_f128_atan(f128 *r, f128 *a);
void bid_f128_sinh(f128 *r, f128 *a);
void bid_f128_cosh(f128 *r, f128 *a);
void bid_f128_tanh(f128 *r, f128 *a);
void bid_f128_asinh(f128 *r, f128 *a);
void bid_f128_acosh(f128 *r, f128 *a);
void bid_f128_atanh(f128 *r, f128 *a);
void bid_f128_erf(f128 *r, f128 *a);
void bid_f128_erfc(f128 *r, f128 *a);
void bid_f128_lgamma(f128 *r, f128 *a);
void bid_f128_tgamma(f128 *r, f128 *a);

static f128 mk(uint64_t hi, uint64_t lo) {
  f128 x;
  x.w[0] = lo;
  x.w[1] = hi;
  return x;
}

static void emit_un(const char *op, f128 x, f128 r) {
  printf("%s 0 %016llx %016llx %016llx %016llx\n", op,
         (unsigned long long)x.w[1], (unsigned long long)x.w[0],
         (unsigned long long)r.w[1], (unsigned long long)r.w[0]);
}

static void emit_bin(const char *op, f128 x, f128 y, f128 r) {
  printf("%s 0 %016llx %016llx %016llx %016llx %016llx %016llx\n", op,
         (unsigned long long)x.w[1], (unsigned long long)x.w[0],
         (unsigned long long)y.w[1], (unsigned long long)y.w[0],
         (unsigned long long)r.w[1], (unsigned long long)r.w[0]);
}

int main(void) {
  f128 samples[16];
  int n = 0;
  samples[n++] = mk(0x0000000000000000ULL, 0x0000000000000000ULL); /* +0 */
  samples[n++] = mk(0x8000000000000000ULL, 0x0000000000000000ULL); /* -0 */
  samples[n++] = mk(0x3fff000000000000ULL, 0x0000000000000000ULL); /* 1 */
  samples[n++] = mk(0xbfff000000000000ULL, 0x0000000000000000ULL); /* -1 */
  samples[n++] = mk(0x4000000000000000ULL, 0x0000000000000000ULL); /* 2 */
  samples[n++] = mk(0x3ffe000000000000ULL, 0x0000000000000000ULL); /* 0.5 */
  samples[n++] = mk(0x4000800000000000ULL, 0x0000000000000000ULL); /* 3 */
  samples[n++] = mk(0x3ffd555555555555ULL, 0x5555555555555555ULL); /* ~1/3 */
  samples[n++] = mk(0x3fff6a09e667f3bcULL, 0xc908b2fb1366ea95ULL); /* sqrt2 */
  samples[n++] = mk(0x4000921fb54442d1ULL, 0x8469898cc51701b8ULL); /* pi */
  samples[n++] = mk(0x0001000000000000ULL, 0x0000000000000000ULL); /* min n */
  samples[n++] = mk(0x0000000000000000ULL, 0x0000000000000001ULL); /* min s */
  samples[n++] = mk(0x7ffeffffffffffffULL, 0xffffffffffffffffULL); /* max */
  samples[n++] = mk(0x7fff000000000000ULL, 0x0000000000000000ULL); /* +inf */
  samples[n++] = mk(0xffff000000000000ULL, 0x0000000000000000ULL); /* -inf */
  samples[n++] = mk(0x7fff800000000000ULL, 0x0000000000000000ULL); /* qnan */

  f128 r;
  int i, j;

  puts("# Intel DPML bid_f128 oracle from libbid.a");
  puts("# CALL_BY_REF=0 GLOBAL_RND=0 GLOBAL_FLAGS=0 USE_COMPILER_F128_TYPE=0");
  puts("# Packed IEEE binary128 hex: high then low (Java Binary128 order).");
  puts("# rnd column is always 0: bid_f128_* do not take a rounding mode;");
  puts("# DPML pack uses the library default (ties-to-even).");
  puts("# unary: op rnd xhi xlo rhi rlo");
  puts("# binary: op rnd xhi xlo yhi ylo rhi rlo");

  for (i = 0; i < n; i++) {
    for (j = 0; j < n; j++) {
      bid_f128_add(&r, &samples[i], &samples[j]);
      emit_bin("add", samples[i], samples[j], r);
      bid_f128_sub(&r, &samples[i], &samples[j]);
      emit_bin("sub", samples[i], samples[j], r);
      bid_f128_mul(&r, &samples[i], &samples[j]);
      emit_bin("mul", samples[i], samples[j], r);
      bid_f128_div(&r, &samples[i], &samples[j]);
      emit_bin("div", samples[i], samples[j], r);
    }
  }

  /* extra exact arithmetic */
  {
    f128 one = mk(0x3fff000000000000ULL, 0);
    f128 four = mk(0x4001000000000000ULL, 0);
    bid_f128_add(&r, &one, &one);
    emit_bin("add", one, one, r);
    bid_f128_sqrt(&r, &four);
    emit_un("sqrt", four, r);
  }

  for (i = 0; i < n; i++) {
    bid_f128_sqrt(&r, &samples[i]); emit_un("sqrt", samples[i], r);
    bid_f128_cbrt(&r, &samples[i]); emit_un("cbrt", samples[i], r);
    bid_f128_exp(&r, &samples[i]); emit_un("exp", samples[i], r);
    bid_f128_expm1(&r, &samples[i]); emit_un("expm1", samples[i], r);
    bid_f128_exp2(&r, &samples[i]); emit_un("exp2", samples[i], r);
    bid_f128_exp10(&r, &samples[i]); emit_un("exp10", samples[i], r);
    bid_f128_log(&r, &samples[i]); emit_un("log", samples[i], r);
    bid_f128_log2(&r, &samples[i]); emit_un("log2", samples[i], r);
    bid_f128_log10(&r, &samples[i]); emit_un("log10", samples[i], r);
    bid_f128_log1p(&r, &samples[i]); emit_un("log1p", samples[i], r);
    bid_f128_sin(&r, &samples[i]); emit_un("sin", samples[i], r);
    bid_f128_cos(&r, &samples[i]); emit_un("cos", samples[i], r);
    bid_f128_tan(&r, &samples[i]); emit_un("tan", samples[i], r);
    bid_f128_asin(&r, &samples[i]); emit_un("asin", samples[i], r);
    bid_f128_acos(&r, &samples[i]); emit_un("acos", samples[i], r);
    bid_f128_atan(&r, &samples[i]); emit_un("atan", samples[i], r);
    bid_f128_sinh(&r, &samples[i]); emit_un("sinh", samples[i], r);
    bid_f128_cosh(&r, &samples[i]); emit_un("cosh", samples[i], r);
    bid_f128_tanh(&r, &samples[i]); emit_un("tanh", samples[i], r);
    bid_f128_asinh(&r, &samples[i]); emit_un("asinh", samples[i], r);
    bid_f128_acosh(&r, &samples[i]); emit_un("acosh", samples[i], r);
    bid_f128_atanh(&r, &samples[i]); emit_un("atanh", samples[i], r);
    bid_f128_erf(&r, &samples[i]); emit_un("erf", samples[i], r);
    bid_f128_erfc(&r, &samples[i]); emit_un("erfc", samples[i], r);
    bid_f128_lgamma(&r, &samples[i]); emit_un("lgamma", samples[i], r);
    bid_f128_tgamma(&r, &samples[i]); emit_un("tgamma", samples[i], r);
  }

  /* pow on a reduced grid to keep the file bounded */
  {
    int idx[] = {0, 2, 3, 4, 5, 6, 13, 14, 15};
    int ni = (int)(sizeof idx / sizeof idx[0]);
    for (i = 0; i < ni; i++) {
      for (j = 0; j < ni; j++) {
        bid_f128_pow(&r, &samples[idx[i]], &samples[idx[j]]);
        emit_bin("pow", samples[idx[i]], samples[idx[j]], r);
      }
    }
  }
  return 0;
}
