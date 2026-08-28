#!/usr/bin/env python3
import argparse
import json
import sys


def load(path):
    with open(path, encoding="utf-8") as handle:
        rows = json.load(handle)
    return {key(row): row for row in rows}


def key(row):
    parameters = tuple(sorted(row.get("params", {}).items()))
    return row["benchmark"], parameters


def label(item):
    benchmark, parameters = item
    name = benchmark.rsplit(".", 1)[-1]
    if not parameters:
        return name
    values = ", ".join(f"{name}={value}" for name, value in parameters)
    return f"{name} [{values}]"


def main():
    parser = argparse.ArgumentParser(
        description="Compare two JMH JSON result files.")
    parser.add_argument("baseline")
    parser.add_argument("candidate")
    parser.add_argument(
        "--threshold",
        type=float,
        default=5.0,
        help="percentage change highlighted as a regression or improvement")
    parser.add_argument(
        "--metric",
        default="score",
        choices=("score", "gc.alloc.rate.norm"),
        help="metric to compare")
    args = parser.parse_args()

    baseline = load(args.baseline)
    candidate = load(args.candidate)
    common = sorted(set(baseline) & set(candidate), key=label)
    if not common:
        raise SystemExit("no matching benchmark cases")

    print(f"{'Benchmark':60} {'Baseline':>12} {'Candidate':>12} {'Change':>10}")
    print("-" * 98)
    regressions = 0
    for item in common:
        old_metric = metric(baseline[item], args.metric)
        new_metric = metric(candidate[item], args.metric)
        if old_metric["scoreUnit"] != new_metric["scoreUnit"]:
            raise SystemExit(f"unit mismatch for {label(item)}")
        old = old_metric["score"]
        new = new_metric["score"]
        if old == 0.0:
            change = 0.0 if new == 0.0 else float("inf")
        else:
            change = (new / old - 1.0) * 100.0
        marker = ""
        if change >= args.threshold:
            marker = " REGRESSION"
            regressions += 1
        elif change <= -args.threshold:
            marker = " IMPROVEMENT"
        print(
            f"{label(item):60} {old:12.3f} {new:12.3f} "
            f"{change:+9.2f}%{marker}")

    missing = len(set(baseline) - set(candidate))
    added = len(set(candidate) - set(baseline))
    print()
    print(
        f"Compared {len(common)} cases; {missing} missing; {added} added; "
        f"{regressions} regressions at {args.threshold:.1f}% threshold.")
    return 1 if regressions else 0


def metric(row, name):
    if name == "score":
        return row["primaryMetric"]
    try:
        return row["secondaryMetrics"][name]
    except KeyError as error:
        raise SystemExit(
            f"metric {name} is missing for {row['benchmark']}") from error


if __name__ == "__main__":
    sys.exit(main())
