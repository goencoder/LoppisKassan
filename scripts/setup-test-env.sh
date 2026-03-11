#!/bin/bash
set -e

# ── Config ───────────────────────────────────────────────────────
TIMESTAMP=$(date +%H%M)
MARKET_NAME="Test Market $TIMESTAMP"
EVENT_NAME="NetworkStabilityTest $TIMESTAMP"
VENDOR_COUNT=100

GORAN_EMAIL="goran@goencoder.se"
PETER_EMAIL="peter.karlsson@sillstugan.se"
API_BASE="http://localhost:8080"
MAILHOG_API="http://localhost:8025"

echo "🏗️  Setting up test environment [$TIMESTAMP]"
echo ""

# ── Helper: login via magic link ─────────────────────────────────
# Prints only the API key to stdout; all status goes to stderr.
login_magic_link() {
  local email="$1"
  local label="$2"

  # Clear MailHog inbox
  curl -s -X DELETE "$MAILHOG_API/api/v1/messages" > /dev/null

  # Trigger magic link
  curl -s -X POST "$API_BASE/v1/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\": \"$email\"}" > /dev/null

  # Poll for the email (max 30 s)
  for i in $(seq 1 30); do
    sleep 1
    KEY=$(curl -s "$MAILHOG_API/api/v2/messages?limit=1" \
      | jq -r --arg e "$email" \
          '.items[]
           | select(.Raw.To[] | contains($e))
           | .Content.Body' \
      | grep -oE 'api_key=[a-f0-9-]+' \
      | head -1 \
      | cut -d= -f2)

    if [ -n "$KEY" ]; then
      echo "$KEY"          # only this goes to stdout → captured by $()
      return 0
    fi
  done

  echo "❌ Timeout waiting for magic link ($label)" >&2
  return 1
}

# ── Step 1: Login Göran (platform owner) ─────────────────────────
echo -n "🔑 Logging in Göran (platform owner)... " >&2
GORAN_KEY=$(login_magic_link "$GORAN_EMAIL" "Göran")
echo "✅" >&2

# ── Step 2: Login Peter (market owner) ───────────────────────────
echo -n "🔑 Logging in Peter (market owner)...   " >&2
PETER_KEY=$(login_magic_link "$PETER_EMAIL" "Peter")
echo "✅" >&2
echo ""

# ── Step 3: Create draft market ──────────────────────────────────
echo -n "📦 Creating draft market... "
MARKET_RESP=$(curl -s -X POST "$API_BASE/v1/draft-markets" \
  -H "Authorization: Bearer $PETER_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"market\": {
      \"name\": \"$MARKET_NAME\",
      \"description\": \"Network stability test market\",
      \"ownerEmail\": \"$PETER_EMAIL\",
      \"revenueSplit\": {
        \"vendorPercentage\": 85,
        \"marketOwnerPercentage\": 10,
        \"platformProviderPercentage\": 5,
        \"charityPercentage\": 0
      }
    }
  }")
MARKET_ID=$(echo "$MARKET_RESP" | jq -r '.market.id')
echo "✅  $MARKET_ID"

# ── Step 4: Approve market (platform owner) ──────────────────────
echo -n "✔️  Approving market... "
curl -s -X POST "$API_BASE/v1/draft-markets/$MARKET_ID/approval" \
  -H "Authorization: Bearer $GORAN_KEY" \
  -H "Content-Type: application/json" \
  -d '{}' > /dev/null
echo "✅"

# ── Step 5: Create event ─────────────────────────────────────────
START_TIME=$(date -u -v+1d +"%Y-%m-%dT08:00:00Z" 2>/dev/null \
  || date -u -d '+1 day' +"%Y-%m-%dT08:00:00Z")
END_TIME=$(date -u -v+1d +"%Y-%m-%dT17:00:00Z" 2>/dev/null \
  || date -u -d '+1 day' +"%Y-%m-%dT17:00:00Z")

echo -n "📅 Creating event... "
EVENT_RESP=$(curl -s -X POST "$API_BASE/v1/events" \
  -H "Authorization: Bearer $PETER_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"event\": {
      \"marketId\": \"$MARKET_ID\",
      \"name\": \"$EVENT_NAME\",
      \"description\": \"555-item network stability test\",
      \"startTime\": \"$START_TIME\",
      \"endTime\": \"$END_TIME\",
      \"addressStreet\": \"Testgatan 1\",
      \"addressCity\": \"Stockholm\",
      \"addressZip\": \"11122\",
      \"maxVendors\": 150
    }
  }")
EVENT_ID=$(echo "$EVENT_RESP" | jq -r '.event.id')
echo "✅  $EVENT_ID"

# ── Step 6: Create & approve 100 vendors ─────────────────────────
echo -n "👥 Creating $VENDOR_COUNT vendors... "
for i in $(seq 1 $VENDOR_COUNT); do
  # Create vendor
  V_RESP=$(curl -s -X POST "$API_BASE/v1/events/$EVENT_ID/vendors" \
    -H "Authorization: Bearer $PETER_KEY" \
    -H "Content-Type: application/json" \
    -d "{
      \"sellerNumber\": $i,
      \"applicant\": {
        \"email\": \"vendor${i}@loadtest.local\",
        \"firstName\": \"Vendor\",
        \"lastName\": \"Nr$i\"
      }
    }")
  VID=$(echo "$V_RESP" | jq -r '.vendor.vendorId')

  # Approve vendor
  curl -s -X PUT "$API_BASE/v1/events/$EVENT_ID/vendors/$VID" \
    -H "Authorization: Bearer $PETER_KEY" \
    -H "Content-Type: application/json" \
    -d "{
      \"vendor\": {
        \"vendorId\": \"$VID\",
        \"sellerNumber\": $i,
        \"status\": \"approved\"
      }
    }" > /dev/null

  if [ $((i % 25)) -eq 0 ]; then echo -n "$i "; fi
done
echo "✅"

# ── Step 7: Create cashier API key ───────────────────────────────
echo -n "💰 Creating cashier API key... "
KEY_RESP=$(curl -s -X POST "$API_BASE/v1/events/$EVENT_ID/api-keys" \
  -H "Authorization: Bearer $PETER_KEY" \
  -H "Content-Type: application/json" \
  -d '{"type": "API_KEY_TYPE_WEB_CASHIER"}')
CASHIER_KEY=$(echo "$KEY_RESP" | jq -r '.apiKey')
CASHIER_ALIAS=$(echo "$KEY_RESP" | jq -r '.alias')
echo "✅  $CASHIER_ALIAS"

# ── Step 8: Write network-test.env ───────────────────────────────
cat > network-test.env <<EOF
ILOPPIS_API_URL=http://localhost:8081
ILOPPIS_EVENT_ID=$EVENT_ID
ILOPPIS_API_KEY=$CASHIER_KEY
ILOPPIS_DIRECT_URL=http://localhost:8080
TOXIPROXY_API=http://localhost:8474
TOXIPROXY_PROXY=iloppis-backend
EOF

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║             SETUP COMPLETE [$TIMESTAMP]             ║"
echo "╠══════════════════════════════════════════════════════╣"
echo "║ Market ID:    $MARKET_ID"
echo "║ Event ID:     $EVENT_ID"
echo "║ Cashier Code: $CASHIER_ALIAS"
echo "║ API Key:      $CASHIER_KEY"
echo "║ Vendors:      $VENDOR_COUNT (all approved)"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
echo "🚀 Ready: make network-test ENV=./network-test.env"
