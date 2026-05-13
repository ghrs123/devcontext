#!/bin/bash
# Creates the first admin account
# Usage: ./scripts/create-admin.sh <email> <password>
# Requires backend running at localhost:8080
curl -X POST http://localhost:8080/api/admin/seed \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$1\", \"password\": \"$2\", \"name\": \"FitVision Admin\"}"
