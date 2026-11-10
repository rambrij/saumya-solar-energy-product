#!/usr/bin/env python3
"""Assert the disposable MySQL schema-audit artifact has the expected tenant isolation shape."""
from __future__ import annotations

import csv
import sys
from pathlib import Path

artifact = Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/tenant-schema-audit.txt")
if not artifact.is_file():
    raise SystemExit(f"FAIL: schema audit artifact not found: {artifact}")

lines = artifact.read_text(encoding="utf-8").splitlines()
base_header = next((i for i, line in enumerate(lines)
                    if line.strip().split("\t")[:5] ==
                    ["TABLE_NAME", "TOTAL_ROWS", "TENANT_ID_COLUMN", "NULL_TENANT_ROWS", "IS_NULLABLE"]), None)
exact_header = next((i for i, line in enumerate(lines)
                     if line.strip().split("\t") ==
                     ["table_name", "total_rows", "null_tenant_rows", "is_nullable"]), None)
if base_header is None or exact_header is None:
    raise SystemExit("FAIL: schema audit output is missing the expected base-table or exact tenant count section")

base_rows = []
for line in lines[base_header + 1:]:
    if not line.strip():
        break
    base_rows.append(line.split("\t"))

exact_lines = [lines[exact_header]]
for line in lines[exact_header + 1:]:
    if not line.strip() or line.startswith("child_table\t"):
        break
    exact_lines.append(line)
exact_rows = list(csv.DictReader(exact_lines, delimiter="\t"))

if len(base_rows) != 74:
    raise SystemExit(f"FAIL: expected 74 base tables, found {len(base_rows)}")
tenant_tables = [row for row in base_rows if len(row) >= 3 and row[2].upper() != "NO"]
if len(tenant_tables) != 64 or len(exact_rows) != 64:
    raise SystemExit(f"FAIL: expected 64 tenant_id-bearing tables, found inventory={len(tenant_tables)}, exact_counts={len(exact_rows)}")

nullable = {row["table_name"]: int(row["null_tenant_rows"]) for row in exact_rows
            if row["is_nullable"].upper() == "YES"}
not_null = [row for row in exact_rows if row["is_nullable"].upper() == "NO"]
expected_nullable = {"app_user", "role", "audit_event", "notification_delivery"}
if set(nullable) != expected_nullable:
    raise SystemExit(f"FAIL: nullable tenant_id columns differ from approved mixed-scope set: {sorted(nullable)}")
if len(not_null) != 60:
    raise SystemExit(f"FAIL: expected 60 NOT NULL tenant_id columns, found {len(not_null)}")

nonzero_not_null = {row["table_name"]: int(row["null_tenant_rows"]) for row in not_null
                    if int(row["null_tenant_rows"]) != 0}
if nonzero_not_null:
    raise SystemExit(f"FAIL: tenant-owned NOT NULL tables contain NULL tenant IDs: {nonzero_not_null}")

expected_nullable_nulls = {"app_user": 0, "role": 3, "audit_event": 0, "notification_delivery": 0}
if nullable != expected_nullable_nulls:
    raise SystemExit(f"FAIL: mixed-scope NULL row counts differ from the reviewed CI fixture: {nullable}")

print("PASS: 74 base tables; 64 tenant_id tables; 60 NOT NULL with zero NULL rows.")
print("PASS: four mixed-scope nullable tables; exact disposable-CI NULL counts:", nullable)
print("NOTE: these assertions apply only to disposable CI data; production requires the protected preflight and owner/DBA approval.")
