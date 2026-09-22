#!/usr/bin/env bash
set -euo pipefail

database_url="${DATABASE_URL:-postgresql://rescue_lab:rescue_lab@localhost:5432/rescue_lab}"
base_url="${BASE_URL:-http://localhost:8080}"

mkdir -p build/evidence/inc-001

psql "$database_url" -f performance/sql/inc-001-seed.sql
psql "$database_url" -f performance/sql/inc-001-explain.sql \
  > build/evidence/inc-001/explain-plan.txt

BASE_URL="$base_url" \
  API_USERNAME="${API_USERNAME:-alice}" \
  API_PASSWORD="${API_PASSWORD:-alice-change-me}" \
  PAGE_SIZE="${PAGE_SIZE:-100}" \
  VUS="${VUS:-10}" \
  DURATION="${DURATION:-30s}" \
  k6 run performance/k6/inc-001-order-search.js

echo "Evidence written to build/evidence/inc-001"
