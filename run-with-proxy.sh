#!/bin/bash
# Start LoppisKassan with custom API endpoint (e.g., toxiproxy)
# Usage: ./run-with-proxy.sh [API_URL]
#
# Examples:
#   ./run-with-proxy.sh                              # Use toxiproxy at localhost:8081
#   ./run-with-proxy.sh http://localhost:8081        # Explicit URL
#   ./run-with-proxy.sh http://127.0.0.1:8080        # Direct backend

set -e

API_URL="${1:-http://localhost:8081}"

echo "═══════════════════════════════════════════════════════════"
echo "Starting LoppisKassan with API endpoint:"
echo "  $API_URL"
echo "═══════════════════════════════════════════════════════════"
echo

# Check if toxiproxy is running if using port 8081
if [[ "$API_URL" == *":8081"* ]]; then
    if ! curl -s http://localhost:8474/version > /dev/null 2>&1; then
        echo "⚠️  WARNING: Toxiproxy does not appear to be running!"
        echo "   Start it with: make toxiproxy-up"
        echo
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    else
        echo "✓ Toxiproxy is running"
        # Show current toxics
        echo
        echo "Active toxics:"
        curl -s http://localhost:8474/proxies/iloppis-backend/toxics | python3 -c "import sys, json; toxics = json.load(sys.stdin); print('  None (normal network)' if not toxics else '\n'.join(['  - ' + t['name'] + ' (' + t['type'] + ')' for t in toxics]))"
        echo
    fi
fi

# Export environment variable for LoppisKassan
export ILOPPIS_API_URL="$API_URL"

# Build if needed
if [ ! -f target/LoppisKassan-v2.0.0-jar-with-dependencies.jar ]; then
    echo "Building LoppisKassan..."
    mvn -B -U -DskipTests package
    echo
fi

# Run the application
echo "Launching LoppisKassan..."
echo
java -jar target/LoppisKassan-v2.0.0-jar-with-dependencies.jar
