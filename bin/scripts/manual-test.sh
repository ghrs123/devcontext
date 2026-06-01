#!/usr/bin/env bash
# =============================================================================
# FitVision — Manual cURL Test Script
# Tests all 6 scenarios for POST /api/widget/v1/size-recommendation
#
# Prerequisites:
#   1. FitVision backend running: mvn spring-boot:run (port 8080)
#   2. PostgreSQL running: docker start fitvision-db
#   3. A store record in the database with api_key_public = $API_KEY and status = 'ACTIVE'
#   4. A product record with external_product_id = $PRODUCT_WITH_CHART for that store,
#      with an active size chart and size entries.
#   5. A product record with external_product_id = $PRODUCT_NO_CHART with no active size chart.
#
# Adjust the variables below before running.
# =============================================================================

BASE_URL="http://localhost:8080"
API_KEY="your-api-key-here"           # Replace with a real api_key_public from the stores table
PRODUCT_WITH_CHART="shopify-12345"    # Replace with an externalProductId that has an active size chart
PRODUCT_NO_CHART="shopify-99999"      # Replace with an externalProductId that has NO active size chart

echo ""
echo "============================================================"
echo " FitVision Widget API — Manual Test"
echo " Base URL : $BASE_URL"
echo " API Key  : $API_KEY"
echo "============================================================"

# -----------------------------------------------------------------------------
# Scenario 1: Missing API key header → HTTP 401, INVALID_API_KEY
# -----------------------------------------------------------------------------
echo ""
echo "--- Scenario 1: Missing API key ---"
echo "Expected: HTTP 401, error.code = INVALID_API_KEY"
curl -s -w "\nHTTP status: %{http_code}\n" \
  -X POST "$BASE_URL/api/widget/v1/size-recommendation" \
  -H "Content-Type: application/json" \
  -d '{
    "externalProductId": "'"$PRODUCT_WITH_CHART"'",
    "heightCm": 175,
    "weightKg": 75,
    "gender": "MALE"
  }' | python3 -m json.tool 2>/dev/null || cat

# -----------------------------------------------------------------------------
# Scenario 2: Invalid API key → HTTP 401, INVALID_API_KEY
# -----------------------------------------------------------------------------
echo ""
echo "--- Scenario 2: Invalid API key ---"
echo "Expected: HTTP 401, error.code = INVALID_API_KEY"
curl -s -w "\nHTTP status: %{http_code}\n" \
  -X POST "$BASE_URL/api/widget/v1/size-recommendation" \
  -H "Content-Type: application/json" \
  -H "X-FitVision-Key: totally-invalid-key-00000" \
  -d '{
    "externalProductId": "'"$PRODUCT_WITH_CHART"'",
    "heightCm": 175,
    "weightKg": 75,
    "gender": "MALE"
  }' | python3 -m json.tool 2>/dev/null || cat

# -----------------------------------------------------------------------------
# Scenario 3: Valid API key + product with size chart → HTTP 200, match found
# -----------------------------------------------------------------------------
echo ""
echo "--- Scenario 3: Valid API key + product with size chart ---"
echo "Expected: HTTP 200, data.recommendedSize != null, data.confidenceScore > 0"
curl -s -w "\nHTTP status: %{http_code}\n" \
  -X POST "$BASE_URL/api/widget/v1/size-recommendation" \
  -H "Content-Type: application/json" \
  -H "X-FitVision-Key: $API_KEY" \
  -d '{
    "externalProductId": "'"$PRODUCT_WITH_CHART"'",
    "heightCm": 175,
    "weightKg": 75,
    "gender": "MALE",
    "age": 30,
    "storeBodyData": false
  }' | python3 -m json.tool 2>/dev/null || cat

# -----------------------------------------------------------------------------
# Scenario 4: Valid API key + product with NO size chart → HTTP 200, fallback
# -----------------------------------------------------------------------------
echo ""
echo "--- Scenario 4: Valid API key + product with no size chart ---"
echo "Expected: HTTP 200, data.hasSizeChart = false, data.recommendedSize = null"
curl -s -w "\nHTTP status: %{http_code}\n" \
  -X POST "$BASE_URL/api/widget/v1/size-recommendation" \
  -H "Content-Type: application/json" \
  -H "X-FitVision-Key: $API_KEY" \
  -d '{
    "externalProductId": "'"$PRODUCT_NO_CHART"'",
    "heightCm": 165,
    "weightKg": 60,
    "gender": "FEMALE"
  }' | python3 -m json.tool 2>/dev/null || cat

# -----------------------------------------------------------------------------
# Scenario 5: Valid API key + product not found → HTTP 404, PRODUCT_NOT_FOUND
# -----------------------------------------------------------------------------
echo ""
echo "--- Scenario 5: Valid API key + product not found ---"
echo "Expected: HTTP 404, error.code = PRODUCT_NOT_FOUND"
curl -s -w "\nHTTP status: %{http_code}\n" \
  -X POST "$BASE_URL/api/widget/v1/size-recommendation" \
  -H "Content-Type: application/json" \
  -H "X-FitVision-Key: $API_KEY" \
  -d '{
    "externalProductId": "shopify-product-that-does-not-exist-99999",
    "heightCm": 175,
    "weightKg": 75
  }' | python3 -m json.tool 2>/dev/null || cat

# -----------------------------------------------------------------------------
# Scenario 6: Valid API key + invalid body (heightCm = 0) → HTTP 400, VALIDATION_ERROR
# -----------------------------------------------------------------------------
echo ""
echo "--- Scenario 6: Valid API key + invalid body (heightCm = 0) ---"
echo "Expected: HTTP 400, error.code = VALIDATION_ERROR"
curl -s -w "\nHTTP status: %{http_code}\n" \
  -X POST "$BASE_URL/api/widget/v1/size-recommendation" \
  -H "Content-Type: application/json" \
  -H "X-FitVision-Key: $API_KEY" \
  -d '{
    "externalProductId": "'"$PRODUCT_WITH_CHART"'",
    "heightCm": 0,
    "weightKg": 75,
    "gender": "MALE"
  }' | python3 -m json.tool 2>/dev/null || cat

echo ""
echo "============================================================"
echo " Manual test complete."
echo "============================================================"
