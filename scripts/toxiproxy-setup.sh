#!/bin/bash
# Setup toxiproxy to proxy requests from :8081 to backend :8080
# Assumes backend is running at host.docker.internal:8080

set -e

TOXIPROXY_API="http://localhost:8474"
PROXY_NAME="iloppis-backend"

# Wait for toxiproxy to be ready
echo "Waiting for toxiproxy..."
until curl -s "$TOXIPROXY_API/version" > /dev/null 2>&1; do
    sleep 1
done
echo "✓ Toxiproxy ready"

# Delete existing proxy if it exists
curl -s -X DELETE "$TOXIPROXY_API/proxies/$PROXY_NAME" 2>/dev/null || true

# Create proxy: external :8081 -> backend :8080
echo "Creating proxy: 0.0.0.0:8081 -> host.docker.internal:8080"
curl -X POST "$TOXIPROXY_API/proxies" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"$PROXY_NAME\",
    \"listen\": \"0.0.0.0:8081\",
    \"upstream\": \"host.docker.internal:8080\",
    \"enabled\": true
  }"

echo
echo "✓ Proxy created successfully"
echo
echo "LoppisKassan should now use: http://localhost:8081"
echo "Control toxics via: http://localhost:8474"
