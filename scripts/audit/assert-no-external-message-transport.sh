#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$PWD}"
if [[ ! -d "$ROOT" ]]; then
  echo "ERROR: repository root not found: $ROOT" >&2
  exit 2
fi

# Encoded signatures keep the prohibited transport identifiers out of the
# checked-in repository while still allowing CI to enforce a repository-wide scan.
signatures=(
  "$(printf '\\x6b\\x61\\x66\\x6b\\x61')"
  "$(printf '\\x72\\x61\\x62\\x62\\x69\\x74\\x6d\\x71')"
  "$(printf '\\x72\\x61\\x62\\x62\\x69\\x74')"
  "$(printf '\\x61\\x6d\\x71\\x70')"
  "$(printf '\\x6a\\x6d\\x73')"
  "$(printf '\\x73\\x71\\x73')"
  "$(printf '\\x73\\x6e\\x73')"
  "$(printf '\\x65\\x76\\x65\\x6e\\x74\\x62\\x72\\x69\\x64\\x67\\x65')"
  "$(printf '\\x61\\x63\\x74\\x69\\x76\\x65\\x6d\\x71')"
  "$(printf '\\x61\\x72\\x74\\x65\\x6d\\x69\\x73')"
  "$(printf '\\x70\\x75\\x6c\\x73\\x61\\x72')"
  "$(printf '\\x6d\\x71\\x74\\x74')"
  "$(printf '\\x7a\\x65\\x72\\x6f\\x6d\\x71')"
  "$(printf '\\x6e\\x61\\x74\\x73')"
  "$(printf '\\x70\\x75\\x62\\x73\\x75\\x62')"
  "$(printf '\\x73\\x65\\x72\\x76\\x69\\x63\\x65\\x62\\x75\\x73')"
  "$(printf '\\x62\\x72\\x6f\\x6b\\x65\\x72')"
)

failed=0
for signature in "${signatures[@]}"; do
  if grep -RInFi --binary-files=without-match \
      --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=target \
      --exclude-dir=dist --exclude-dir=build --exclude-dir=coverage \
      --exclude-dir=.next --exclude-dir=.venv --exclude-dir=venv \
      "$signature" "$ROOT"; then
    failed=1
  fi
done

if [[ "$failed" -ne 0 ]]; then
  echo "FAIL: prohibited external messaging transport references were found."
  exit 1
fi

echo "PASS: no prohibited external messaging transport references exist in the repository tree."
