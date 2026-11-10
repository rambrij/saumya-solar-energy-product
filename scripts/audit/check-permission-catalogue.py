#!/usr/bin/env python3
"""Compare Java permission guard codes with the checked-in permission catalogue."""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "backend")
java_root = root / "src/main/java"
migration_root = root / "src/main/resources/db/migration"
seeder = java_root / "com/saumyasolar/config/DataSeeder.java"

if not java_root.is_dir() or not seeder.is_file():
    raise SystemExit(f"ERROR: expected Java source and DataSeeder under {root}")

catalogue = set()
seed_text = seeder.read_text(encoding="utf-8")
m = re.search(r"PERMISSIONS\s*=\s*List\.of\((.*?)\);", seed_text, re.S)
if m:
    catalogue.update(re.findall(r'"([A-Z][A-Z0-9_]+)"', m.group(1)))

for migration in migration_root.glob("*.sql"):
    sql = migration.read_text(encoding="utf-8")
    for match in re.finditer(r"INSERT(?:\s+IGNORE)?\s+INTO\s+permission\b[\s\S]*?;", sql, re.I):
        catalogue.update(re.findall(r"'([A-Z][A-Z0-9_]+)'", match.group(0)))

guard_codes = set()
guard_pattern = re.compile(
    r'\b(?:requirePermission|permission|authorize|hasPermission)\s*\([^;\n]{0,240}?"([A-Z][A-Z0-9_]+)"',
    re.M,
)
direct_pattern = re.compile(
    r'\b(?:permissions|authorities)\(\)\s*\.\s*contains\s*\(\s*"([A-Z][A-Z0-9_]+)"',
    re.M,
)
for source in java_root.rglob("*.java"):
    text = source.read_text(encoding="utf-8")
    guard_codes.update(guard_pattern.findall(text))
    guard_codes.update(direct_pattern.findall(text))

missing = sorted(guard_codes - catalogue)
print("# Permission catalogue drift report")
print(f"Java permission guard codes: {len(guard_codes)}")
print(f"Permission catalogue codes (DataSeeder + Flyway): {len(catalogue)}")
print(f"Missing permission catalogue codes: {len(missing)}")
print()
print("## Referenced permission codes")
for code in sorted(guard_codes):
    print(f"- {code}")
print()
print("## Missing catalogue entries")
if missing:
    for code in missing:
        print(f"- {code}")
    raise SystemExit(1)
print("None")
