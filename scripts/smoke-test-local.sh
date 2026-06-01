#!/bin/bash
# Local smoke test — verifies dev environment is running correctly
./scripts/smoke-test.sh \
  http://localhost:8080 \
  http://localhost:3000 \
  http://localhost:5173/fitvision-widget.min.js
