#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-backend/src/main/java}"
if [[ ! -d "$ROOT" ]]; then
  echo "ERROR: source root not found: $ROOT" >&2
  exit 2
fi

echo "# Legacy authorization and role-name guard inventory"
echo "# Root: $ROOT"
echo "# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo
echo "## Coarse legacy permission code references"
grep -RInE --include='*.java' 'CATALOG_MANAGE|CATALOG_VIEW|CUSTOMER_MANAGE|LEAD_MANAGE|SURVEY_MANAGE|QUOTATION_MANAGE|DISCOUNT_APPROVE([^_A-Z]|$)|INVENTORY_MANAGE|SERVICE_MANAGE|FINANCE_MANAGE|USER_MANAGE' "$ROOT" || true
echo
echo "## Role-name checks in authorization or ownership code (manual classification required)"
grep -RInE --include='*.java' 'roles\(\).*contains|roles\(\).*anyMatch|getRoles\(\).*anyMatch|\.getName\(\).*VENDOR_ADMIN|\.getName\(\).*SERVICE_MANAGER|\.getName\(\).*OPERATIONS_MANAGER|equals\("(VENDOR_ADMIN|SERVICE_MANAGER|OPERATIONS_MANAGER|SALES_MANAGER|SERVICE_ENGINEER|TECHNICIAN|INSTALLER)"\)' "$ROOT" || true
echo
echo "## Permission guard call sites"
grep -RInE --include='*.java' 'requirePermission\(|permission\(p,|authorize\(actor,|permissions\(\).*contains' "$ROOT" || true
