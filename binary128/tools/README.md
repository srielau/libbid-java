# DPML QUAD UX table generator

Deterministic converter from Intel RDFP `LIBRARY/float128` UX headers
(`dpml_*_x.h`, `dpml_four_over_pi.c`) to Java `long[]` tables under
`org.bidfp.binary128.tables`.

## Usage

```bash
# Default source path (this host):
python3 binary128/tools/gen_dpml_tables.py

# Explicit Intel float128 tree:
python3 binary128/tools/gen_dpml_tables.py \
  --src /path/to/LIBRARY/float128

# Parse + spot-check only (no write):
python3 binary128/tools/gen_dpml_tables.py --verify-only
```

Does not copy C sources into the repo. Re-run after updating the external
Intel tree, then `mvn -pl binary128 test`.
