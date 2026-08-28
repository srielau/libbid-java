# Performance benchmarks

The JMH suite measures BID64 and BID128 object APIs, the raw APIs used by JVM
integrations, and equivalent `BigDecimal` operations where Java provides one.
Inputs are deterministic and are prepared outside the measured region.

## Benchmark matrix

`BidJmhBenchmark` contains:

- add, subtract, multiply, and divide
- square root
- fused multiply-add
- truncating remainder (`fmod`)
- positive integral powers
- round-to-integral
- scale-by-power-of-ten
- ordered and cohort-equal comparisons

Add, multiply, and divide cover three operand distributions:

- `sameQuantum`: similar precision and equal exponents
- `mixedQuantum`: varied precision and different exponents
- `fullPrecision`: maximum format precision and different exponents

The extended operations use positive, full-precision values in `[1, 10)`,
signed full-precision addends, integral powers from 2 through 5, and decimal
scale changes from -12 through 12. Integral power uses exactly representable
three-digit BID64 and six-digit BID128 bases because general full-precision
`pow` can follow a different last-bit path than `BigDecimal.pow`. These bounds
keep results finite and within both BID formats.

Each arithmetic operation has BID64 object, BID64 raw, `BigDecimal` DECIMAL64,
BID128 object, BID128 raw, and `BigDecimal` DECIMAL128 methods. The extended
operations (subtract through scale-by-power-of-ten) validate their operands at
trial setup, checking object/raw bit equality and numerical equality with
`BigDecimal`. The comparison state validates its operands against
`BigDecimal.compareTo`. The base add, multiply, and divide state prepares
operands without a setup-time equivalence check.

The measured BID methods share one status-flag accumulator per state and do not
clear it between invocations. IEEE flags are sticky by design, so this is an
intentional contract: each BID measurement includes the cost of merging into
already-set flags. The `BigDecimal` methods do not touch flags.

The semantic comparisons have deliberate limits:

- BID fused multiply-add is compared with an exact `BigDecimal` multiply and
  add followed by one DECIMAL64 or DECIMAL128 rounding. It is not compared with
  separately rounded multiply and add operations.
- BID `fmod` uses a quotient truncated toward zero and corresponds to
  `BigDecimal.remainder`. IEEE `remainder`, which uses a nearest-integer
  quotient, is a different operation and is not part of this comparison.
- The power comparison uses only positive integral exponents accepted by
  `BigDecimal.pow(int, MathContext)`. It does not represent general BID `pow`.
- Round-to-integral compares numerical results. `BigDecimal` does not preserve
  BID cohorts, signed zero, fixed exponent bounds, or IEEE status flags.

`BidTranscendentalJmhBenchmark` covers:

- `exp`, `expm1`, `exp2`, `exp10`
- `log`, `log10`, `log2`, `log1p`, `cbrt`
- `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2`
- `sinh`, `cosh`, `tanh`, `asinh`, `acosh`, `atanh`
- `erf`, `erfc`, `tgamma`, `lgamma`, `pow`, `hypot`

It exposes four measured methods: `bid64Object`, `bid64Raw`, `bid128Object`, and
`bid128Raw`. The operation is a JMH parameter dispatched through a `switch`,
which adds a small constant overhead shared by every method and parameter value.
Always filter to a single operation and compare methods for that operation
(`bid64Object` against `bid64Raw`, or the BID128 pair); the dispatch cost is
negligible next to the expensive libm-style kernels. Java `BigDecimal` has no
standard transcendental functions, so this suite compares object and raw BID
paths only. Every measured method clears the shared status flags before the
call, so it reports one operation rather than an accumulation of sticky flags.

Transcendental inputs use full format precision and operation-specific domains:

- positive log-uniform values for `log`, `log10`, and `log2`
- signed values strictly inside `(-1, 1)`, spread across adjusted exponents
  from -1 down to -5 (down to near zero), for `log1p`, `asin`, `acos`, and
  `atanh`
- `[1, 10)` for `acosh`, `tgamma`, and `lgamma`
- a bounded, non-saturated domain (adjusted exponents -2 through 0) for `tanh`,
  while `asinh` is left intentionally broad to exercise large arguments
- bounded signed arguments for the exponentials, other trigonometric,
  hyperbolic, error, and root functions
- positive bases with bounded signed exponents for `pow`
- nonzero signed pairs for `atan2` and `hypot`

For `atan2` the receiver and first raw argument are `y` and the second argument
is `x`; `pow` and `hypot` keep their natural argument order. Trial setup rejects
non-finite results and exceptional flags, then verifies object/raw result bits
and flags for every operand.

The suite currently has 72 JMH methods. Accounting for workload and operation
parameters, a complete run produces 216 benchmark cases.

## Select benchmarks

Do not start with `full` on the whole suite. A complete full run is about 200
cases and takes on the order of an hour. Iterate with a filter, then capture a
full baseline only for the methods you still care about.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Same JVM, 200ms slices, reuse the JAR. Seconds, not hours.
benchmarks/run.sh iter BidJmhBenchmark.bid64Add
benchmarks/run.sh iter BidJmhBenchmark -p workload=fullPrecision
benchmarks/run.sh iter BidTranscendentalJmhBenchmark -p operation=log

# Confirm a slice with one fork before a baseline.
benchmarks/run.sh quick BidJmhBenchmark.bid64
```

`iter` skips Maven when `bid/target/benchmarks.jar` already exists. Pass
`--rebuild` after kernel changes. Pass `--gc` if you need allocation numbers
on an `iter` run; `quick` and `full` enable the GC profiler by default.

The include argument is a JMH regex matched against the fully qualified method
name, so `Bid64Add` is enough for one method and `BidTranscendental` is enough
for that class.

## Capture a baseline

Use the same otherwise-idle host, JDK, CPU governor, and JVM options for every
run. `iter` is for local loops, `quick` is a one-fork check, and `full` is the
optimization baseline.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
benchmarks/run.sh quick BidJmhBenchmark
benchmarks/run.sh full BidJmhBenchmark
```

Each run creates a timestamped directory under `benchmark-results/` containing:

- `jmh-result.json`: machine-readable measurements
- `jmh-output.txt`: complete JMH output
- `environment.txt`: commit, dirty state, OS, CPU, and Java version

The result directory is intentionally ignored by Git because measurements are
specific to a machine. Archive the full baseline with the build or performance
investigation that owns it.

## Compare an optimization

Run the same profile before and after the change, then compare the JSON files:

```bash
benchmarks/compare.py \
  benchmark-results/<baseline>/jmh-result.json \
  benchmark-results/<candidate>/jmh-result.json

benchmarks/compare.py --metric gc.alloc.rate.norm \
  benchmark-results/<baseline>/jmh-result.json \
  benchmark-results/<candidate>/jmh-result.json
```

The default 5 percent threshold highlights changes large enough to investigate.
It is not a statistical confidence test. Confirm any suspected regression or
improvement with repeated full runs and inspect JMH score-error intervals.

Avoid comparing results from different hosts or JDK builds. Do not optimize
solely for the `BigDecimal` ratio: it is a reference workload, while the raw BID
measurements are the primary Spark integration baseline.
