#!/bin/bash
# Usage: ./scripts/create-admin.sh <email> <password> <bootstrap-token> [base-url]
curl -X POST ${4:-http://localhost:8080}/api/admin/seed \
  -H "Content-Type: application/json" \
  -H "X-Bootstrap-Token: $3" \
  -d "{\"email\": \"$1\", \"password\": \"$2\", \"name\": \"FitVision Admin\"}"
