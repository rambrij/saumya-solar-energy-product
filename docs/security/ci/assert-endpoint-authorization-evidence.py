#!/usr/bin/env python3
"""Fail-closed reconciliation of every Spring MVC runtime mapping to authorization evidence."""
from __future__ import annotations
import csv
import re
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else "backend/target/phase2-endpoint-evidence")
repo_root = Path(__file__).resolve().parents[2]
matrix_path = repo_root / "PHASE_2_ENDPOINT_MATRIX.md"
FILES = {
    "authentication": "authentication-and-public-exceptions.csv",
    "positive": "positive-applicable-account-gates.csv",
    "negative_get": "negative-vendor-get.csv",
    "negative_mutations": "negative-vendor-mutations.csv",
}
EXPECTED_COLUMNS = {"http_method", "route_pattern", "handler_class", "handler_method", "actor", "expected", "actual", "result"}
failures: list[str] = []
warnings: list[str] = []

def key(row: dict[str, str]) -> tuple[str, str]:
    return row["http_method"].strip().upper(), row["route_pattern"].strip()

def read_rows(filename: str) -> list[dict[str, str]]:
    path = root / filename
    if not path.is_file():
        failures.append(f"{filename}: missing evidence file")
        return []
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        if set(reader.fieldnames or ()) != EXPECTED_COLUMNS:
            failures.append(f"{filename}: invalid CSV columns {reader.fieldnames}")
            return []
        rows = list(reader)
    seen: set[tuple[str, str]] = set()
    duplicates: set[tuple[str, str]] = set()
    for row in rows:
        identity = key(row)
        if identity in seen:
            duplicates.add(identity)
        seen.add(identity)
    if duplicates:
        failures.append(f"{filename}: duplicate mapping identities: {sorted(duplicates)[:12]}")
    invalid = [row for row in rows if row["result"] not in {"PASS", "EXEMPT", "DELEGATED", "FAIL"}]
    if invalid:
        failures.append(f"{filename}: {len(invalid)} row(s) have an unknown result")
    failed = [row for row in rows if row["result"] == "FAIL"]
    if failed:
        failures.append(f"{filename}: failed probes: " + "; ".join(
            f'{row["http_method"]} {row["route_pattern"]}={row["actual"]}' for row in failed[:12]
        ))
    return rows

def split_markdown_row(line: str) -> list[str]:
    body = line.strip()
    if body.startswith("|"):
        body = body[1:]
    if body.endswith("|"):
        body = body[:-1]
    cells: list[str] = []
    current: list[str] = []
    escaped = False
    for char in body:
        if char == "|" and not escaped:
            cells.append("".join(current).strip())
            current = []
            continue
        current.append(char)
        if char == "\\" and not escaped:
            escaped = True
        else:
            escaped = False
    cells.append("".join(current).strip())
    return cells

def route_patterns(cell: str) -> list[str]:
    values = re.split(r"<br\s*/?>", cell, flags=re.IGNORECASE)
    routes: list[str] = []
    for value in values:
        route = value.strip().strip(chr(96)).replace(r"\|", "|")
        route = re.sub(r"\s+", "", route)
        if route.startswith("/"):
            routes.append(route)
    return routes

def read_static_matrix() -> tuple[dict[tuple[str, str], str], set[tuple[str, str]]]:
    policies: dict[tuple[str, str], dict[str, str]] = {}
    declarations: set[tuple[str, str]] = set()
    if not matrix_path.is_file():
        warnings.append(f"static endpoint matrix not found at {matrix_path}")
        return policies, declarations
    in_main = False
    in_exports = False
    for line in matrix_path.read_text(encoding="utf-8").splitlines():
        if not line.startswith("|"):
            if in_main:
                in_main = False
            if in_exports:
                in_exports = False
            continue
        cells = split_markdown_row(line)
        if len(cells) >= 9 and cells[0] == "#" and cells[1] == "HTTP" and cells[2].startswith("Route pattern"):
            in_main, in_exports = True, False
            continue
        if len(cells) >= 4 and cells[0] == "HTTP" and cells[1] == "Route":
            in_exports, in_main = True, False
            continue
        if in_main and len(cells) >= 9 and cells[0].isdigit():
            method, policy = cells[1].upper(), cells[7]
            for route in route_patterns(cells[2]):
                identity = (method, route)
                declarations.add(identity)
                policies[identity] = {"policy": policy, "evidence": cells[8]}
        elif in_exports and len(cells) >= 4 and cells[0].upper() in {"GET", "POST", "PUT", "PATCH", "DELETE"}:
            method, route = cells[0].upper(), cells[1].strip().strip(chr(96))
            if route.startswith("/"):
                identity = (method, route)
                declarations.add(identity)
                policies[identity] = {"policy": cells[2], "evidence": cells[3]}
    return policies, declarations

auth_rows = read_rows(FILES["authentication"])
positive_rows = read_rows(FILES["positive"])
get_rows = read_rows(FILES["negative_get"])
mutation_rows = read_rows(FILES["negative_mutations"])
auth_by_key = {key(row): row for row in auth_rows}
positive_by_key = {key(row): row for row in positive_rows}
get_by_key = {key(row): row for row in get_rows}
mutation_by_key = {key(row): row for row in mutation_rows}
runtime_keys = set(auth_by_key)
protected_rows = [row for row in auth_rows if row["actor"] == "ANONYMOUS"]
public_rows = [row for row in auth_rows if row["actor"] == "PUBLIC_ALLOWLIST"]
protected_keys = {key(row) for row in protected_rows}
public_keys = {key(row) for row in public_rows}
if not runtime_keys:
    failures.append("runtime authentication inventory is empty")
for row in protected_rows:
    if row["result"] != "PASS" or row["actual"] not in {"401", "403"}:
        failures.append(f'authentication gate not proven: {row["http_method"]} {row["route_pattern"]} -> {row["actual"]}')
for row in public_rows:
    if row["result"] != "EXEMPT":
        failures.append(f'public mapping is not explicitly classified: {row["http_method"]} {row["route_pattern"]}')

def compare_exact(label: str, actual: set[tuple[str, str]], expected: set[tuple[str, str]]) -> None:
    missing = expected - actual
    unexpected = actual - expected
    if missing:
        failures.append(f"{label}: missing {len(missing)} runtime mapping(s): {sorted(missing)[:15]}")
    if unexpected:
        failures.append(f"{label}: unexpected {len(unexpected)} mapping(s): {sorted(unexpected)[:15]}")

positive_keys = set(positive_by_key)
compare_exact("positive applicable-account evidence", positive_keys, protected_keys)
for row in positive_rows:
    if row["result"] not in {"PASS", "DELEGATED"}:
        failures.append(f'positive gate not proven: {row["http_method"]} {row["route_pattern"]} -> {row["actual"]}')

protected_gets = {key(row) for row in protected_rows if row["http_method"] == "GET"}
compare_exact("zero-permission GET evidence", set(get_by_key), protected_gets)
for row in get_rows:
    if row["result"] not in {"PASS", "EXEMPT"}:
        failures.append(f'GET denial/exemption not proven: {row["http_method"]} {row["route_pattern"]} -> {row["actual"]}')
    if row["result"] == "EXEMPT" and row["expected"] != "self-service exception":
        failures.append(f'GET exemption lacks a declared self-service reason: {row["route_pattern"]}')

protected_mutations = {key(row) for row in protected_rows if row["http_method"] not in {"GET", "HEAD", "OPTIONS"}}
compare_exact("zero-permission mutation evidence", set(mutation_by_key), protected_mutations)
for row in mutation_rows:
    if row["result"] not in {"PASS", "DELEGATED"}:
        failures.append(f'mutation denial/delegation not proven: {row["http_method"]} {row["route_pattern"]} -> {row["actual"]}')
    if row["result"] == "DELEGATED" and "Test" not in row["expected"] and "test" not in row["expected"]:
        failures.append(f'delegated mutation lacks a focused test reference: {row["http_method"]} {row["route_pattern"]}')

policies, matrix_keys = read_static_matrix()
missing_from_runtime = matrix_keys - runtime_keys
runtime_not_in_matrix = runtime_keys - matrix_keys
if missing_from_runtime:
    failures.append(f"static matrix declares {len(missing_from_runtime)} route(s) absent at runtime: {sorted(missing_from_runtime)[:15]}")
if runtime_not_in_matrix:
    warnings.append(f"{len(runtime_not_in_matrix)} runtime mapping(s) are not declared in PHASE_2_ENDPOINT_MATRIX.md: {sorted(runtime_not_in_matrix)[:15]}")

output = root / "runtime-route-authorization-reconciliation.csv"
with output.open("w", newline="", encoding="utf-8") as handle:
    fields = ["http_method", "route_pattern", "handler_class", "handler_method", "classification",
              "anonymous_evidence", "positive_gate_evidence", "zero_permission_evidence",
              "matrix_policy", "matrix_evidence", "test_references", "test_reference_status",
              "matrix_match", "evidence_strength"]
    writer = csv.DictWriter(handle, fieldnames=fields)
    writer.writeheader()
    for identity in sorted(runtime_keys):
        method, route = identity
        auth = auth_by_key[identity]
        pos = positive_by_key.get(identity)
        neg = get_by_key.get(identity) if method == "GET" else mutation_by_key.get(identity)
        matrix_record = policies.get(identity, {})
        matrix_evidence = matrix_record.get("evidence", "")
        test_refs = re.findall(r"\b([A-Za-z0-9_]+Test)\.([A-Za-z0-9_]+)\b", matrix_evidence)
        verified_refs = []
        unresolved_refs = []
        for test_class, test_method in test_refs:
            candidates = list((repo_root / "backend" / "src" / "test" / "java").rglob(test_class + ".java"))
            if any(re.search(r"\b" + re.escape(test_method) + r"\s*\(", candidate.read_text(encoding="utf-8")) for candidate in candidates):
                verified_refs.append(test_class + "." + test_method)
            else:
                unresolved_refs.append(test_class + "." + test_method)
        if unresolved_refs:
            test_reference_status = "UNRESOLVED_REFERENCE"
            failures.append(f"{method} {route}: matrix references missing test method(s): {unresolved_refs}")
        elif verified_refs:
            test_reference_status = "VERIFIED_TEST_METHOD_REFERENCE"
        elif test_refs:
            test_reference_status = "TEST_REFERENCE_UNVERIFIED"
        elif matrix_evidence:
            test_reference_status = "GENERIC_OR_GATE_ONLY_EVIDENCE"
        else:
            test_reference_status = "MISSING_MATRIX_EVIDENCE"
        if auth["actor"] == "PUBLIC_ALLOWLIST":
            classification = "PUBLIC"
            negative_result = "PUBLIC_EXEMPT"
            strength = "PUBLIC_BOUNDARY"
        else:
            classification = "PROTECTED"
            negative_result = "MISSING" if neg is None else neg["result"] + ":" + neg["actual"]
            if pos and pos["result"] == "DELEGATED" or neg and neg["result"] == "DELEGATED":
                strength = "FOCUSED_TEST_DELEGATION"
            elif neg and neg["actual"] == "404":
                strength = "NON_DISCLOSING_NOT_FOUND_PROBE"
            elif neg and neg["result"] == "EXEMPT":
                strength = "SELF_SERVICE_EXCEPTION"
            else:
                strength = "ACCOUNT_PERMISSION_GATE_ONLY"
        writer.writerow({
            "http_method": method, "route_pattern": route,
            "handler_class": auth["handler_class"], "handler_method": auth["handler_method"],
            "classification": classification,
            "anonymous_evidence": auth["result"] + ":" + auth["actual"],
            "positive_gate_evidence": "MISSING" if pos is None else pos["result"] + ":" + pos["actual"],
            "zero_permission_evidence": negative_result,
            "matrix_policy": matrix_record.get("policy", ""),
            "matrix_evidence": matrix_evidence,
            "test_references": ";".join(verified_refs + unresolved_refs),
            "test_reference_status": test_reference_status,
            "matrix_match": "YES" if identity in matrix_keys else "NO",
            "evidence_strength": strength,
        })

print(f"Runtime mappings reconciled: {len(runtime_keys)} total; {len(protected_keys)} protected; {len(public_keys)} public.")
print(f"Positive applicable-account probes: {len(positive_keys)}.")
print(f"Zero-permission GET evidence: {len(get_by_key)}.")
print(f"Zero-permission mutation evidence: {len(mutation_by_key)}.")
print(f"Static matrix mappings: {len(matrix_keys)}; runtime-only mappings: {len(runtime_not_in_matrix)}.")
print(f"Per-mapping reconciliation written to {output}.")
for warning in warnings:
    print("WARN: " + warning)
if failures:
    print("FAIL: runtime endpoint authorization evidence is incomplete:")
    for failure in failures:
        print(" - " + failure)
    raise SystemExit(1)

not_found = sum(1 for row in get_rows + mutation_rows if row["actual"] == "404" and row["result"] == "PASS")
delegated = sum(1 for row in positive_rows + mutation_rows if row["result"] == "DELEGATED")
exempt = sum(1 for row in get_rows if row["result"] == "EXEMPT")
print("PASS: every runtime mapping has an explicit authentication/public classification.")
print("PASS: every protected mapping has a positive applicable-account gate and a verb-appropriate least-privilege result or explicit exception/delegation.")
print(f"Evidence quality notice: {not_found} denial probes returned 404; {delegated} mappings are delegated; {exempt} GET mappings are self-service exceptions.")
print("NOTE: account-level gates do not replace fixture-backed successful and denied CRUD tests for every named business role/resource.")
