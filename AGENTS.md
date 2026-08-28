# Apache Spark-style notes for this repo

This repository publishes two JARs from one git tree:

| Module | Maven artifact | Package | Role |
|---|---|---|---|
| `binary128/` | `org.bidfp:binary128` | `org.bidfp.binary128` | IEEE binary128 + DPML libm |
| `bid/` | `org.bidfp:libbid-java` | `org.bidfp` | BID64/BID128; depends on `binary128` |

`upstream/TESTS/readtest.in` is the Intel oracle (repo root). Intel C is not
vendored; see `upstream/README`.

Do not implement DPML kernels in `bid/`. Follow `binary128/AGENTS.md` for that
port. Do not implement BID packing in `binary128/`.

Keep source lines within 100 characters. ASCII punctuation in comments.
