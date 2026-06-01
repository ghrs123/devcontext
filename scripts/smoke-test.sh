#!/bin/bash
# FitVision Production Smoke Test
# Usage: ./scripts/smoke-test.sh https://api.fitvision.io
# Exit 0: all checks passed
# Exit 1: one or more checks failed

BASE_URL=${1:-https://api.fitvision.io}
DASHBOARD_URL=${2:-https://app.fitvision.io}
WIDGET_CDN=${3:-https://cdn.fitvision.io/widget/fitvision-widget.min.js}

PASS=0
FAIL=0

check() {
  local name=$1
  local result=$2
  local expected=$3
  if echo "$result" | grep -q "$expected"; then
    echo "✅ $name"
    PASS=$((PASS + 1))
  else
    echo "❌ $name (expected: $expected, got: $result)"
    FAIL=$((FAIL + 1))
  fi
}

echo "=== FitVision Smoke Test ==="
echo "Backend: $BASE_URL"
echo "Dashboard: $DASHBOARD_URL"
echo ""

# 1. Backend health
HEALTH=$(curl -sf "$BASE_URL/actuator/health" | jq -r '.status' 2>/dev/null)
check "Backend health" "$HEALTH" "UP"

# 2. Widget CDN
WIDGET_STATUS=$(curl -o /dev/null -sw "%{http_code}" "$WIDGET_CDN")
check "Widget CDN accessible" "$WIDGET_STATUS" "200"

# 3. Auth endpoint responds
AUTH_STATUS=$(curl -o /dev/null -sw "%{http_code}" -X POST "$BASE_URL/api/dashboard/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@test.com","password":"wrong"}')
check "Auth endpoint responds" "$AUTH_STATUS" "401"

# 4. Widget API CORS headers present
CORS=$(curl -sf -I -X OPTIONS "$BASE_URL/api/widget/v1/size-recommendation" \
  -H "Origin: https://test.myshopify.com" | grep -i "access-control-allow-origin")
check "Widget CORS headers" "$CORS" "access-control"

# 5. Swagger accessible
SWAGGER_STATUS=$(curl -o /dev/null -sw "%{http_code}" "$BASE_URL/swagger-ui.html")
check "Swagger accessible" "$SWAGGER_STATUS" "200"

# 6. Admin seed endpoint responds (should be 409 — admin exists)
SEED_STATUS=$(curl -o /dev/null -sw "%{http_code}" -X POST "$BASE_URL/api/admin/seed" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@test.com","password":"test","name":"Smoke"}')
check "Admin seed returns 409 (admin exists)" "$SEED_STATUS" "409"

# 7. Dashboard loads (HTML response)
DASHBOARD_STATUS=$(curl -o /dev/null -sw "%{http_code}" "$DASHBOARD_URL/login")
check "Dashboard login page" "$DASHBOARD_STATUS" "200"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="

if [ $FAIL -gt 0 ]; then
  exit 1
fi
exit 0
