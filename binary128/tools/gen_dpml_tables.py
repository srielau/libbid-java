#!/usr/bin/env python3
# Copyright (c) 2007-2025, Intel Corp. Table data is regenerated from Intel
# RDFP float128 UX headers (BSD-3-Clause). This generator is project tooling.
"""Generate Java DPML QUAD UX table classes from Intel float128 headers.

Reads little-endian DATA_* macros, POS/NEG UX records, and #define offsets
from dpml_*_x.h / dpml_four_over_pi.c and emits:
  org.bidfp.binary128.tables.{ConsX,ExpX,LogX,...}

Usage:
  python3 binary128/tools/gen_dpml_tables.py \\
    [--src DIR] [--out DIR] [--verify-only]

Default --src is the documented external Intel RDFP float128 tree on this
host. Does not vendor C sources into the git tree.
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

DEFAULT_SRC = Path("/Users/serge.rielau/libbid-java-upstream/LIBRARY/float128")
DEFAULT_OUT = (
    Path(__file__).resolve().parents[1]
    / "src"
    / "main"
    / "java"
    / "org"
    / "bidfp"
    / "binary128"
    / "tables"
)

INTEL_HEADER = """\
/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in
 * LICENSE-INTEL are met.
 *
 * Generated from Intel RDFP float128 UX table headers. Do not edit by
 * hand; regenerate with binary128/tools/gen_dpml_tables.py.
 */
"""

UX_SIGN_BIT = 0x80000000

# Expected little-endian byte lengths (u32 count * 4) for verify.
EXPECTED_BYTES = {
    "ConsX": 160,
    "ExpX": 1352,
    "LogX": 496,
    "PowX": 1048,
    "CbrtX": 104,
    "TrigX": 1032,
    "InvTrigX": 1312,
    "InvHyperX": 112,
    "ErfX": 1368,
    "LgammaX": 968,
    "FourOverPi": 263 * 8,
}

# Known spot checks: (class, long_index, expected_long)
# LogX LN_2 MSD at byte 456 => long index 57.
SPOT_CHECKS = [
    ("LogX", 57, 0xB17217F7D1CF79AB),
    ("ConsX", 7, 0x4000921FB54442D1),  # PI high word (LE pair low@6 high@7)
    ("FourOverPi", 2, 0x0028BE60DB939105),
]


@dataclass
class ParsedTable:
    name: str
    source_file: str
    u32s: list[int] = field(default_factory=list)
    defines: dict[str, object] = field(default_factory=dict)
    comments: list[str] = field(default_factory=list)


def parse_ux_exponent(token: str) -> int:
    """Parse Intel MPHOC exponent tokens like 0001, 00-1, 0-66, -131072."""
    s = token.strip()
    m = re.match(r"^0*(-\d+)$", s)
    if m:
        return int(m.group(1))
    return int(s, 10)


def u32(v: int) -> int:
    return v & 0xFFFFFFFF


def pack_longs(u32s: list[int]) -> list[int]:
    """Pack little-endian u32 pairs into Java long bit patterns."""
    out: list[int] = []
    i = 0
    n = len(u32s)
    while i < n:
        lo = u32s[i]
        hi = u32s[i + 1] if i + 1 < n else 0
        out.append(((hi & 0xFFFFFFFF) << 32) | (lo & 0xFFFFFFFF))
        i += 2
    return out


# Normal hex, or Intel scale words like 0x000000-1 (signed decimal after '-').
WORD = r"(?:0x[0-9a-fA-F]*-\d+|0x[0-9a-fA-F]+)"


def parse_table_word(token: str) -> int:
    """Parse a DATA_* word; map 0x000000-N to two's-complement -N."""
    s = token.strip()
    m = re.fullmatch(r"0x[0-9a-fA-F]*-(\d+)", s, flags=re.I)
    if m:
        return u32(-int(m.group(1)))
    return u32(int(s, 16))


def tokenize_table_body(body: str) -> list[tuple[str, tuple]]:
    """Return list of (kind, args) from a TABLE_UNION initializer body."""
    # Strip C comments but keep newlines for sanity.
    body = re.sub(r"/\*.*?\*/", " ", body, flags=re.S)
    body = re.sub(r"//.*?$", " ", body, flags=re.M)
    tokens: list[tuple[str, tuple]] = []
    pos = 0
    patterns = [
        (
            "DATA_1x2",
            re.compile(rf"DATA_1x2\s*\(\s*({WORD})\s*,\s*({WORD})\s*\)"),
        ),
        (
            "DATA_2x2",
            re.compile(
                rf"DATA_2x2\s*\(\s*({WORD})\s*,\s*({WORD})\s*,\s*"
                rf"({WORD})\s*,\s*({WORD})\s*\)"
            ),
        ),
        (
            "DATA_4R",
            re.compile(
                rf"DATA_4R\s*\(\s*({WORD})\s*,\s*({WORD})\s*,\s*"
                rf"({WORD})\s*,\s*({WORD})\s*\)"
            ),
        ),
        (
            "DATA_4",
            re.compile(
                rf"DATA_4\s*\(\s*({WORD})\s*,\s*({WORD})\s*,\s*"
                rf"({WORD})\s*,\s*({WORD})\s*\)"
            ),
        ),
        ("POS", re.compile(r"\bPOS\b")),
        ("NEG", re.compile(r"\bNEG\b")),
        # Exponent after POS/NEG: 0001, 00-1, 0-66, -131072
        ("EXP", re.compile(r"(?<![0-9a-fxA-F])(0*-?\d+)(?![0-9a-fxA-F])")),
        ("COMMA", re.compile(r",")),
        ("WS", re.compile(r"\s+")),
    ]
    expect_exp = False
    while pos < len(body):
        matched = False
        for kind, cre in patterns:
            m = cre.match(body, pos)
            if not m:
                continue
            matched = True
            pos = m.end()
            if kind == "WS" or kind == "COMMA":
                break
            if kind == "POS":
                tokens.append(("SIGN", (0,)))
                expect_exp = True
            elif kind == "NEG":
                tokens.append(("SIGN", (UX_SIGN_BIT,)))
                expect_exp = True
            elif kind == "EXP":
                if expect_exp:
                    tokens.append(("EXP", (parse_ux_exponent(m.group(1)),)))
                    expect_exp = False
            elif kind.startswith("DATA_"):
                vals = tuple(parse_table_word(g) for g in m.groups())
                tokens.append((kind, vals))
                expect_exp = False
            break
        if not matched:
            # skip one char (e.g. stray punctuation)
            pos += 1
    return tokens


def expand_little_endian(tokens: list[tuple[str, tuple]]) -> list[int]:
    """Expand tokens to u32 stream under little-endian DATA_* rules."""
    out: list[int] = []
    for kind, args in tokens:
        if kind == "SIGN":
            out.append(u32(args[0]))
        elif kind == "EXP":
            out.append(u32(args[0]))
        elif kind == "DATA_1x2":
            # little_endian: a, b
            out.extend([u32(args[0]), u32(args[1])])
        elif kind in ("DATA_2x2", "DATA_4", "DATA_4R"):
            # little_endian: a,b,c,d (DATA_4R same as DATA_4)
            out.extend([u32(a) for a in args])
        else:
            raise ValueError(f"unknown token {kind}")
    return out


DEFINE_RE = re.compile(
    r"#\s*define\s+(\w+)\s+"
    r"(?:"
    r"\(\(U_WORD const \*\) \(\(char \*\) \w+ \+ (\d+)\)\)|"
    r"\(\(UX_FLOAT \*\) \(\(char \*\) \w+ \+ (\d+)\)\)|"
    r"\(\(FIXED_128 \*\) \(\(char \*\) \w+ \+ (\d+)\)\)|"
    r"\(\(UX_FRACTION_DIGIT_TYPE \*\) \(\(char \*\) \w+ \+ (\d+)\)\)|"
    r"\(\(double \*\) \(\(char \*\) \w+ \+ (\d+)\)\)|"
    r"\(\(long double \*\) \(\(char \*\) \w+ \+ (\d+)\)\)|"
    r"\*\(\(UX_FRACTION_DIGIT_TYPE \*\) \(\(char \*\) \w+ \+ (\d+)\)\)|"
    r"\*\(\(double \*\) \(\(char \*\) \w+ \+ (\d+)\)\)|"
    r"\(\s*signed\s+(?:__int64|long long)\s*\)\s*0x([0-9a-fA-F]+)\s*|"
    r"(\d+)\s*"
    r")"
)


def parse_defines(text: str) -> dict[str, object]:
    """Extract byte-offset and integer defines from a generated header."""
    defs: dict[str, object] = {}
    for line in text.splitlines():
        if "#define" not in line:
            continue
        # Normalize tabs
        line = line.replace("\t", " ")
        m = re.match(
            r"#\s*define\s+(\w+)\s+(.+)$",
            line.strip(),
        )
        if not m:
            continue
        name, rhs = m.group(1), m.group(2).strip()
        # Skip function-like macros (poly selectors).
        if "(" in name:
            continue
        om = re.search(r"\(char \*\)\s*\w+\s*\+\s*(\d+)\)", rhs)
        if om:
            defs[name] = int(om.group(1))
            continue
        dm = re.search(
            r"\(\s*signed\s+(?:__int64|long long)\s*\)\s*0x([0-9a-fA-F]+)",
            rhs,
        )
        if dm:
            defs[name] = int(dm.group(1), 16)
            continue
        if re.fullmatch(r"\d+", rhs):
            defs[name] = int(rhs)
            continue
        # Index-style plain macros (EXP_DEGREE_INDEX etc.)
        im = re.fullmatch(r"(\d+)\s*", rhs)
        if im:
            defs[name] = int(im.group(1))
    return defs


def extract_table_body(text: str) -> str | None:
    """Find the primary TABLE_UNION / PACKED_CONSTANT_TABLE initializer."""
    # Prefer INSTANTIATE_TABLE section for cons.
    m = re.search(
        r"#if\s+INSTANTIATE_TABLE\s*(.*?)\s*#endif",
        text,
        re.S,
    )
    if m and "PACKED_CONSTANT_TABLE" in m.group(1):
        text_region = m.group(1)
    else:
        text_region = text
    m = re.search(
        r"(?:static\s+)?const\s+TABLE_UNION\s+\w+\s*\[\s*\]\s*=\s*\{(.*?)\};",
        text_region,
        re.S,
    )
    if m:
        return m.group(1)
    return None


def parse_header(path: Path, java_name: str) -> ParsedTable:
    text = path.read_text(encoding="utf-8", errors="replace")
    body = extract_table_body(text)
    if body is None:
        raise SystemExit(f"no TABLE_UNION in {path}")
    tokens = tokenize_table_body(body)
    u32s = expand_little_endian(tokens)
    defs = parse_defines(text)
    # Also capture INSTANTIATE_DEFINES index names for ConsX.
    if java_name == "ConsX":
        for m in re.finditer(
            r"#\s*define\s+(\w+)\s+(\d+)\s*$",
            text,
            re.M,
        ):
            defs[m.group(1)] = int(m.group(2))
    comments = re.findall(r"/\*\s*[^*\n][^\n]*\*/", body)
    return ParsedTable(
        name=java_name,
        source_file=path.name,
        u32s=u32s,
        defines=defs,
        comments=comments[:8],
    )


def parse_four_over_pi(path: Path) -> ParsedTable:
    text = path.read_text(encoding="utf-8", errors="replace")
    m = re.search(
        r"__four_over_pi\s*\[\s*\]\s*=\s*\{(.*?)\};",
        text,
        re.S,
    )
    if not m:
        raise SystemExit(f"no __four_over_pi array in {path}")
    body = re.sub(r"/\*.*?\*/", " ", m.group(1), flags=re.S)
    vals = [
        int(x[:-3], 16) if x.lower().endswith("ull") else int(x, 16)
        for x in re.findall(r"0x[0-9a-fA-F]+(?:ull)?", body, flags=re.I)
    ]
    # Already u64; store as longs directly via fake u32 pairs.
    u32s: list[int] = []
    for v in vals:
        u32s.append(u32(v))
        u32s.append(u32(v >> 32))
    defs = {
        "FOUR_OV_PI_ZERO_PAD_LEN": 138,
        "BITS_PER_DIGIT": 64,
        "LENGTH": len(vals),
    }
    # Pull defines from #else DESCRIBE section if present.
    for name, num in re.findall(
        r"#\s*define\s+(FOUR_OV_PI_ZERO_PAD_LEN|BITS_PER_DIGIT|"
        r"NUM_INDEX_BITS|NUM_OCTANT_BITS|MIN_OVERHANG)\s+(\d+)",
        text,
    ):
        defs[name] = int(num)
    return ParsedTable(
        name="FourOverPi",
        source_file=path.name,
        u32s=u32s,
        defines=defs,
    )


def java_ident(name: str) -> str:
    """Map C macro name to a Java-friendly constant identifier."""
    return name


def emit_long_array(longs: list[int], indent: str = "      ") -> str:
    lines: list[str] = []
    row: list[str] = []
    for i, v in enumerate(longs):
        # Always unsigned hex for bit patterns.
        row.append(f"0x{v & 0xFFFFFFFFFFFFFFFF:016X}L")
        if len(row) == 2 or i == len(longs) - 1:
            line = indent + ", ".join(row)
            if i != len(longs) - 1:
                line += ","
            # Keep <= 100 chars: 2 hex longs fit.
            lines.append(line)
            row = []
    return "\n".join(lines)


def emit_java(table: ParsedTable) -> str:
    longs = pack_longs(table.u32s)
    nbytes = len(table.u32s) * 4
    lines: list[str] = []
    lines.extend(INTEL_HEADER.rstrip().splitlines())
    lines.append("package org.bidfp.binary128.tables;")
    lines.append("")
    lines.append("/**")
    lines.append(
        " * QUAD UX table from Intel {@code " + table.source_file + "}."
    )
    lines.append(
        " * Little-endian memory image as {@code long[]} ("
        + str(nbytes)
        + " bytes)."
    )
    lines.append(" */")
    lines.append(f"public final class {table.name} {{")
    lines.append(f"  private {table.name}() {{")
    lines.append("  }")
    lines.append("")
    lines.append("  /** Total table size in bytes (Intel comment offsets). */")
    lines.append(f"  public static final int BYTE_LENGTH = {nbytes};")
    lines.append("")

    # Emit offset / degree constants (stable public API for kernels).
    offset_items = sorted(
        ((k, v) for k, v in table.defines.items() if isinstance(v, int)),
        key=lambda kv: (
            0
            if (
                "CLASS" in kv[0]
                or kv[0].startswith("UX_")
                or kv[0].endswith("_ADDRESS")
                or kv[0].endswith("_ARRAY")
            )
            else 1,
            kv[0],
        ),
    )
    for name, val in offset_items:
        jname = java_ident(name)
        if len(jname) > 60:
            continue
        if abs(val) <= 0x7FFFFFFF:
            line = f"  public static final int {jname} = {val};"
        else:
            line = (
                f"  public static final long {jname} = "
                f"0x{val & 0xFFFFFFFFFFFFFFFF:X}L;"
            )
        if len(line) > 100:
            raise SystemExit(f"define line too long: {line!r}")
        lines.append(line)
    if offset_items:
        lines.append("")

    lines.append("  /** Little-endian table words (two u32s per long). */")
    lines.append("  public static final long[] TABLE = {")
    lines.extend(emit_long_array(longs).splitlines())
    lines.append("  };")
    lines.append("}")
    lines.append("")
    for ln in lines:
        if len(ln) > 100:
            raise SystemExit(
                f"line exceeds 100 chars in {table.name}: {ln!r}"
            )
    return "\n".join(lines)


TABLE_SPECS = [
    ("ConsX", "dpml_cons_x.h", parse_header),
    ("ExpX", "dpml_exp_x.h", parse_header),
    ("LogX", "dpml_log_x.h", parse_header),
    ("PowX", "dpml_pow_x.h", parse_header),
    ("CbrtX", "dpml_cbrt_x.h", parse_header),
    ("TrigX", "dpml_trig_x.h", parse_header),
    ("InvTrigX", "dpml_inv_trig_x.h", parse_header),
    ("InvHyperX", "dpml_inv_hyper_x.h", parse_header),
    ("ErfX", "dpml_erf_x.h", parse_header),
    ("LgammaX", "dpml_lgamma_x.h", parse_header),
]


def load_all(src: Path) -> list[ParsedTable]:
    tables: list[ParsedTable] = []
    for java_name, filename, parser in TABLE_SPECS:
        path = src / filename
        if not path.is_file():
            raise SystemExit(f"missing {path}")
        tables.append(parser(path, java_name))
    fop = src / "dpml_four_over_pi.c"
    if not fop.is_file():
        raise SystemExit(f"missing {fop}")
    tables.append(parse_four_over_pi(fop))
    return tables


def verify(tables: list[ParsedTable]) -> list[str]:
    errors: list[str] = []
    by_name = {t.name: t for t in tables}
    for name, expected in EXPECTED_BYTES.items():
        t = by_name.get(name)
        if t is None:
            errors.append(f"missing table {name}")
            continue
        got = len(t.u32s) * 4
        if got != expected:
            # Allow odd trailing padding to next long for verify of content
            # but report mismatch.
            errors.append(f"{name}: BYTE_LENGTH {got} != expected {expected}")
    for name, idx, want in SPOT_CHECKS:
        t = by_name[name]
        longs = pack_longs(t.u32s)
        if idx >= len(longs):
            errors.append(f"{name}: spot index {idx} out of range")
            continue
        got = longs[idx] & 0xFFFFFFFFFFFFFFFF
        if got != want:
            errors.append(
                f"{name}.TABLE[{idx}]=0x{got:016X} want 0x{want:016X}"
            )
    # LogX LN_2 offset define.
    logx = by_name["LogX"]
    if logx.defines.get("LN_2") != 448:
        errors.append(f"LogX.LN_2 offset {logx.defines.get('LN_2')} != 448")
    if logx.defines.get("LOG2_COEF_ARRAY_DEGREE") != 0x11:
        errors.append("LogX.LOG2_COEF_ARRAY_DEGREE != 17")
    return errors


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--src",
        type=Path,
        default=DEFAULT_SRC,
        help=f"Intel float128 directory (default: {DEFAULT_SRC})",
    )
    ap.add_argument(
        "--out",
        type=Path,
        default=DEFAULT_OUT,
        help=f"Java tables package dir (default: {DEFAULT_OUT})",
    )
    ap.add_argument(
        "--verify-only",
        action="store_true",
        help="Parse and verify without writing Java files",
    )
    args = ap.parse_args(argv)

    if not args.src.is_dir():
        print(f"error: source dir not found: {args.src}", file=sys.stderr)
        print(
            "Pass --src pointing at Intel RDFP LIBRARY/float128",
            file=sys.stderr,
        )
        return 2

    tables = load_all(args.src)
    errors = verify(tables)
    for t in tables:
        nbytes = len(t.u32s) * 4
        nlong = len(pack_longs(t.u32s))
        print(f"{t.name}: {nbytes} bytes, {nlong} longs, "
              f"{len(t.defines)} defines <- {t.source_file}")

    if errors:
        print("VERIFY FAILED:", file=sys.stderr)
        for e in errors:
            print(f"  {e}", file=sys.stderr)
        return 1
    print("VERIFY OK")

    if args.verify_only:
        return 0

    args.out.mkdir(parents=True, exist_ok=True)
    for t in tables:
        out_path = args.out / f"{t.name}.java"
        out_path.write_text(emit_java(t), encoding="utf-8")
        print(f"wrote {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
