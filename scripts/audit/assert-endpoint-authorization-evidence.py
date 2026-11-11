#!/usr/bin/env python3
"""Validate machine-readable runtime authorization evidence emitted by EndpointAuthenticationMatrixTest."""
from __future__ import annotations

import csv
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else "backend/target/phase2-endpoint-evidence")
required = {
    "authentication-and-public-exceptions.csv": 222,
    "positive-applicable-account-gates.csv": 207,
    "negative-vendor-get.csv": 83,
    "negative-vendor-mutations.csv": 115,
}
failures: list[str] = []
summary: list[tuple[str, int, int, int]] = []

for filename, minimum in required.items():
    path = root / filename
    if not path.is_file():
        failures.append(f"{filename}: missing evidence file")
        continue
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        expected_columns = {
            "http_method", "route_pattern", "handler_class", "handler_method",
            "actor", "expected", "actual", "result",
        }
        if set(reader.fieldnames or ()) != expected_columns:
            failures.append(f"{filename}: invalid CSV columns {reader.fieldnames}")
            continue
        rows = list(reader)
    passed = sum(row["result"] == "PASS" for row in rows)
    exempt = sum(row["result"] == "EXEMPT" for row in rows)
    delegated = sum(row["result"] == "DELEGATED" for row in rows)
    bad = [row for row in rows if row["result"] == "FAIL"]
    if len(rows) < minimum:
        failures.append(f"{filename}: expected at least {minimum} rows, found {len(rows)}")
    if bad:
        failures.append(
            f"{filename}: {len(bad)} failed probe(s): "
            + "; ".join(f'{row["http_method"]} {row["route_pattern"]}={row["actual"]}' for row in bad[:12])
        )
    unexpected = [row for row in rows if row["result"] not in {"PASS", "EXEMPT", "DELEGATED"}]
    if unexpected:
        failures.append(f"{filename}: {len(unexpected)} unknown result value(s)")
    if filename == "authentication-and-public-exceptions.csv":
        protected = [row for row in rows if row["actor"] == "ANONYMOUS"]
        if len(protected) < 207:
            failures.append(f"{filename}: fewer than 207 protected route probes ({len(protected)})")
        if sum(row["actor"] == "PUBLIC_ALLOWLIST" for row in rows) < 1:
            failures.append(f"{filename}: no public exceptions were classified")
    if filename == "positive-applicable-account-gates.csv":
        actors = {row["actor"] for row in rows}
        if not {"VENDOR_ALL_CATALOGUED_GRANTS", "PLATFORM_ALL_CATALOGUED_GRANTS",
                "CUSTOMER_ALL_CATALOGUED_GRANTS"}.issubset(actors):
            failures.append(f"{filename}: missing one or more applicable account types: {sorted(actors)}")
    summary.append((filename, len(rows), passed, exempt + delegated))

for filename, count, passed, exceptions in summary:
    print(f"{filename}: rows={count}, passed={passed}, exempt/delegated={exceptions}")

if failures:
    print("FAIL: endpoint authorization evidence is incomplete:")
    for failure in failures:
        print(f" - {failure}")
    raise SystemExit(1)

print("PASS: all runtime endpoint authorization probes are accounted for; no failing evidence rows.")
print("NOTE: all-grants probes test applicable account-type permission gates; named business-role and resource ownership tests remain complementary evidence.")
