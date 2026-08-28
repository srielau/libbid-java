# Performance benchmarks

The JMH suite measures BID64 and BID128 add, multiply, divide, and comparison
paths. Arithmetic covers three deterministic operand distributions:

- `sameQuantum`: similar precision and equal exponents
- `mixedQuantum`: varied precision and different exponents
- `fullPrecision`: maximum format precision and different exponents

Each arithmetic case includes the object API, the raw API used by JVM
integrations, and an equivalent `BigDecimal` operation. Comparison cases include
ordered values and numerically equal values with different cohorts.

## Capture a baseline

Use the same otherwise-idle host, JDK, CPU governor, and JVM options for every
run. The quick profile is for iteration; the full profile is the optimization
baseline.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
benchmarks/run.sh quick
benchmarks/run.sh full
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
