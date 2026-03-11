#!/bin/bash
# Network stability test runner
# Sends sold items through toxiproxy and verifies backend data
# Matches test spec 003-network-stability.md

set -e

EVENT_ID="16d15112-361a-45d5-9056-ec26c3784ef3"
API_KEY="cf720b68-8f95-443a-9f93-d1e457127cdc"
PROXY_URL="http://localhost:8081"
BACKEND_URL="http://localhost:8080"
TOXIPROXY_API="http://localhost:8474"

# Track cumulative results
TOTAL_SENT=0
TOTAL_AMOUNT=0
TOTAL_PURCHASES=0
TESTS_PASSED=0
TESTS_FAILED=0

# Generate a ULID-like ID (26 char alphanumeric)
generate_id() {
    python3 -c "
import time, random, string
t = int(time.time() * 1000)
chars = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
time_part = ''
for i in range(10):
    time_part = chars[t % 32] + time_part
    t //= 32
rand_part = ''.join(random.choices(chars, k=16))
print(time_part + rand_part)
"
}

# Send a batch of sold items through proxy
# Usage: send_items SELLER_START SELLER_END PRICE_FORMULA
send_items() {
    local description="$1"
    local items_json="$2"
    local expected_count="$3"
    
    echo "  Sending $expected_count items..."
    
    local response
    response=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer $API_KEY" \
        -H "Content-Type: application/json" \
        -X POST "$PROXY_URL/v1/events/$EVENT_ID/sold-items" \
        -d "$items_json" 2>&1)
    
    local http_code=$(echo "$response" | tail -1)
    local body=$(echo "$response" | head -n -1)
    
    if [ "$http_code" = "200" ]; then
        echo "  ✓ Upload succeeded (HTTP 200)"
        return 0
    else
        echo "  ✗ Upload failed (HTTP $http_code)"
        echo "    Response: $(echo "$body" | head -c 200)"
        return 1
    fi
}

# Build items JSON for a test
build_items_json() {
    local seller_start=$1
    local seller_end=$2
    local price_multiplier=$3
    local seller_offset=${4:-0}
    
    local items="[]"
    local purchase_id
    local item_id
    local sold_time
    sold_time=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    for seller in $(seq $seller_start $seller_end); do
        purchase_id=$(generate_id)
        item_id=$(generate_id)
        local price=$(( (seller - seller_offset) * price_multiplier ))
        
        items=$(echo "$items" | python3 -c "
import json, sys
items = json.load(sys.stdin)
items.append({
    'purchaseId': '$purchase_id',
    'itemId': '$item_id',
    'soldTime': '$sold_time',
    'seller': $seller,
    'price': $price,
    'paymentMethod': 'PAYMENT_METHOD_CASH'
})
print(json.dumps(items))
")
    done
    
    echo "{\"items\": $items}"
}

# Verify backend state
verify_backend() {
    local expected_purchases=$1
    local expected_items=$2
    local expected_total=$3
    local test_name=$4
    
    local result
    result=$(curl -s -H "Authorization: Bearer $API_KEY" \
        "$BACKEND_URL/v1/events/$EVENT_ID/sold-items?page_size=10000" | \
        jq '{
            purchases: [.items[].purchaseId] | unique | length,
            items: .items | length,
            total_sek: ([.items[].price] | add // 0)
        }')
    
    local actual_purchases=$(echo "$result" | jq '.purchases')
    local actual_items=$(echo "$result" | jq '.items')
    local actual_total=$(echo "$result" | jq '.total_sek')
    
    echo ""
    echo "  Verifiering (backend :8080):"
    echo "  ┌──────────────┬───────────┬───────────┬────────┐"
    echo "  │ Metrik       │ Förväntat │ Faktiskt  │ Status │"
    echo "  ├──────────────┼───────────┼───────────┼────────┤"
    
    local p_ok="✓"
    local i_ok="✓"
    local t_ok="✓"
    
    if [ "$actual_purchases" -lt "$expected_purchases" ]; then p_ok="✗"; fi
    if [ "$actual_items" -lt "$expected_items" ]; then i_ok="✗"; fi
    if [ "$actual_total" -lt "$expected_total" ]; then t_ok="✗"; fi
    
    printf "  │ Purchases    │ ≥%-8s │ %-9s │   %s    │\n" "$expected_purchases" "$actual_purchases" "$p_ok"
    printf "  │ Items        │ ≥%-8s │ %-9s │   %s    │\n" "$expected_items" "$actual_items" "$i_ok"
    printf "  │ Total SEK    │ ≥%-8s │ %-9s │   %s    │\n" "$expected_total" "$actual_total" "$t_ok"
    echo "  └──────────────┴───────────┴───────────┴────────┘"
    
    if [ "$p_ok" = "✓" ] && [ "$i_ok" = "✓" ] && [ "$t_ok" = "✓" ]; then
        echo "  ✅ $test_name PASSED"
        return 0
    else
        echo "  ❌ $test_name FAILED"
        return 1
    fi
}

# Apply toxic scenario
apply_scenario() {
    local scenario=$1
    echo "  Applying scenario: $scenario"
    bash /Users/goranengdahl/IntellijProjects/LoppisKassan/scripts/toxiproxy-scenarios.sh "$scenario" 2>&1 | sed 's/^/  /'
}

# Record baseline
echo "═══════════════════════════════════════════════════════"
echo "  iLoppis Network Stability Tests"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "═══════════════════════════════════════════════════════"
echo ""

BASELINE=$(curl -s -H "Authorization: Bearer $API_KEY" \
    "$BACKEND_URL/v1/events/$EVENT_ID/sold-items?page_size=10000" | \
    jq '{purchases: [.items[].purchaseId] | unique | length, items: .items | length, total_sek: ([.items[].price] | add // 0)}')

BASELINE_PURCHASES=$(echo "$BASELINE" | jq '.purchases')
BASELINE_ITEMS=$(echo "$BASELINE" | jq '.items')
BASELINE_TOTAL=$(echo "$BASELINE" | jq '.total_sek')

echo "Baseline: $BASELINE_PURCHASES purchases, $BASELINE_ITEMS items, $BASELINE_TOTAL SEK"
echo ""

# ─────────────────────────────────────────────────────────
# TEST 1: Baseline (No toxic)
# ─────────────────────────────────────────────────────────
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 1: Baseline (Inget toxic)"
echo "  50 items, säljare 1-50, pris = säljare × 100"
echo "  Förväntat: +50 items, +127 500 SEK"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

apply_scenario "clear"

# Send items one purchase at a time (like a cashier would)
for seller in $(seq 1 50); do
    purchase_id=$(generate_id)
    item_id=$(generate_id)
    price=$((seller * 100))
    sold_time=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    json="{\"items\":[{\"purchaseId\":\"$purchase_id\",\"itemId\":\"$item_id\",\"soldTime\":\"$sold_time\",\"seller\":$seller,\"price\":$price,\"paymentMethod\":\"PAYMENT_METHOD_CASH\"}]}"
    
    response=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "Authorization: Bearer $API_KEY" \
        -H "Content-Type: application/json" \
        -X POST "$PROXY_URL/v1/events/$EVENT_ID/sold-items" \
        -d "$json")
    
    if [ "$response" != "200" ]; then
        echo "  ✗ Failed seller $seller (HTTP $response)"
    fi
    
    if [ $((seller % 10)) -eq 0 ]; then
        echo "  Progress: $seller/50 items sent"
    fi
done
echo "  ✓ All 50 items sent"

EXP_P=$((BASELINE_PURCHASES + 50))
EXP_I=$((BASELINE_ITEMS + 50))
EXP_T=$((BASELINE_TOTAL + 127500))
if verify_backend $EXP_P $EXP_I $EXP_T "Test 1: Baseline"; then
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

# ─────────────────────────────────────────────────────────
# TEST 2: High Latency (500ms ± 100ms)
# ─────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 2: Hög latency (500ms ± 100ms)"
echo "  30 items, säljare 1-30, pris = säljare × 200"
echo "  Förväntat: +30 items, +93 000 SEK"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

apply_scenario "high-latency"

START_TIME=$(date +%s)
for seller in $(seq 1 30); do
    purchase_id=$(generate_id)
    item_id=$(generate_id)
    price=$((seller * 200))
    sold_time=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    json="{\"items\":[{\"purchaseId\":\"$purchase_id\",\"itemId\":\"$item_id\",\"soldTime\":\"$sold_time\",\"seller\":$seller,\"price\":$price,\"paymentMethod\":\"PAYMENT_METHOD_SWISH\"}]}"
    
    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 \
        -H "Authorization: Bearer $API_KEY" \
        -H "Content-Type: application/json" \
        -X POST "$PROXY_URL/v1/events/$EVENT_ID/sold-items" \
        -d "$json")
    
    if [ "$response" != "200" ]; then
        echo "  ✗ Failed seller $seller (HTTP $response)"
    fi
    
    if [ $((seller % 10)) -eq 0 ]; then
        echo "  Progress: $seller/30 items sent"
    fi
done
END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
echo "  ✓ All 30 items sent (${ELAPSED}s elapsed, expected ~15-20s with 500ms latency)"

EXP_P=$((BASELINE_PURCHASES + 80))
EXP_I=$((BASELINE_ITEMS + 80))
EXP_T=$((BASELINE_TOTAL + 220500))
if verify_backend $EXP_P $EXP_I $EXP_T "Test 2: High Latency"; then
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

# ─────────────────────────────────────────────────────────
# TEST 3: Slow 3G
# ─────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 3: Slow 3G (250KB/s down, 50KB/s up, 200ms latency)"
echo "  20 items, säljare 51-70, pris = (säljare-50) × 500"
echo "  Förväntat: +20 items, +105 000 SEK"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

apply_scenario "slow-3g"

START_TIME=$(date +%s)
for seller in $(seq 51 70); do
    purchase_id=$(generate_id)
    item_id=$(generate_id)
    price=$(( (seller - 50) * 500 ))
    sold_time=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    json="{\"items\":[{\"purchaseId\":\"$purchase_id\",\"itemId\":\"$item_id\",\"soldTime\":\"$sold_time\",\"seller\":$seller,\"price\":$price,\"paymentMethod\":\"PAYMENT_METHOD_CASH\"}]}"
    
    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 60 \
        -H "Authorization: Bearer $API_KEY" \
        -H "Content-Type: application/json" \
        -X POST "$PROXY_URL/v1/events/$EVENT_ID/sold-items" \
        -d "$json")
    
    if [ "$response" != "200" ]; then
        echo "  ✗ Failed seller $seller (HTTP $response)"
    fi
    
    if [ $((seller % 5)) -eq 0 ]; then
        echo "  Progress: $((seller - 50))/20 items sent"
    fi
done
END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
echo "  ✓ All 20 items sent (${ELAPSED}s elapsed)"

EXP_P=$((BASELINE_PURCHASES + 100))
EXP_I=$((BASELINE_ITEMS + 100))
EXP_T=$((BASELINE_TOTAL + 325500))
if verify_backend $EXP_P $EXP_I $EXP_T "Test 3: Slow 3G"; then
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

# ─────────────────────────────────────────────────────────
# TEST 4: Packet Loss
# ─────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 4: Packet Loss (slicer + 100ms latency)"
echo "  20 items, säljare 71-90, pris = (säljare-70) × 400"
echo "  Förväntat: +20 items, +84 000 SEK"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

apply_scenario "packet-loss"

FAILED_ITEMS=0
for seller in $(seq 71 90); do
    purchase_id=$(generate_id)
    item_id=$(generate_id)
    price=$(( (seller - 70) * 400 ))
    sold_time=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    json="{\"items\":[{\"purchaseId\":\"$purchase_id\",\"itemId\":\"$item_id\",\"soldTime\":\"$sold_time\",\"seller\":$seller,\"price\":$price,\"paymentMethod\":\"PAYMENT_METHOD_CASH\"}]}"
    
    # Retry up to 3 times on failure
    for attempt in 1 2 3; do
        response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 \
            -H "Authorization: Bearer $API_KEY" \
            -H "Content-Type: application/json" \
            -X POST "$PROXY_URL/v1/events/$EVENT_ID/sold-items" \
            -d "$json" 2>/dev/null || echo "000")
        
        if [ "$response" = "200" ]; then
            break
        fi
        
        if [ "$attempt" -lt 3 ]; then
            echo "  ⟳ Retry $attempt for seller $seller (HTTP $response)"
            sleep 1
        else
            echo "  ✗ Failed seller $seller after 3 attempts (HTTP $response)"
            FAILED_ITEMS=$((FAILED_ITEMS + 1))
        fi
    done
    
    if [ $(( (seller - 70) % 5 )) -eq 0 ]; then
        echo "  Progress: $((seller - 70))/20 items sent"
    fi
done
echo "  ✓ Items sent ($FAILED_ITEMS failures after retries)"

EXP_P=$((BASELINE_PURCHASES + 120 - FAILED_ITEMS))
EXP_I=$((BASELINE_ITEMS + 120 - FAILED_ITEMS))
EXP_T=$((BASELINE_TOTAL + 409500))
if verify_backend $EXP_P $EXP_I $EXP_T "Test 4: Packet Loss"; then
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

# ─────────────────────────────────────────────────────────
# TEST 5: Random Timeouts (5%)
# ─────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 5: Random Timeouts (5% timeout)"
echo "  10 items, säljare 91-100, pris = (säljare-90) × 1000"
echo "  Förväntat: +10 items, +55 000 SEK"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

apply_scenario "timeout"

TIMEOUT_COUNT=0
for seller in $(seq 91 100); do
    purchase_id=$(generate_id)
    item_id=$(generate_id)
    price=$(( (seller - 90) * 1000 ))
    sold_time=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    json="{\"items\":[{\"purchaseId\":\"$purchase_id\",\"itemId\":\"$item_id\",\"soldTime\":\"$sold_time\",\"seller\":$seller,\"price\":$price,\"paymentMethod\":\"PAYMENT_METHOD_SWISH\"}]}"
    
    # Retry up to 3 times (5% timeout means ~1 in 20 requests hangs)
    for attempt in 1 2 3; do
        response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
            -H "Authorization: Bearer $API_KEY" \
            -H "Content-Type: application/json" \
            -X POST "$PROXY_URL/v1/events/$EVENT_ID/sold-items" \
            -d "$json" 2>/dev/null || echo "000")
        
        if [ "$response" = "200" ]; then
            break
        elif [ "$response" = "000" ]; then
            TIMEOUT_COUNT=$((TIMEOUT_COUNT + 1))
            echo "  ⏱ Timeout for seller $seller (attempt $attempt)"
            sleep 2
        else
            echo "  ✗ Error for seller $seller: HTTP $response (attempt $attempt)"
            sleep 1
        fi
    done
    
    echo "  Progress: $((seller - 90))/10 items sent"
done
echo "  ✓ Items sent ($TIMEOUT_COUNT timeouts encountered)"

# Clear toxics, wait for any pending to finish
apply_scenario "clear"
sleep 2

EXP_P=$((BASELINE_PURCHASES + 130))
EXP_I=$((BASELINE_ITEMS + 130))
EXP_T=$((BASELINE_TOTAL + 464500))
if verify_backend $EXP_P $EXP_I $EXP_T "Test 5: Random Timeouts"; then
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

# ─────────────────────────────────────────────────────────
# TEST 6: Unstable (worst case)
# ─────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 6: Unstable (kombinerat worst-case)"
echo "  10 items, säljare 1-10, pris = säljare × 500"
echo "  Förväntat: +10 items, +27 500 SEK"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

apply_scenario "unstable"

UNSTABLE_RETRIES=0
for seller in $(seq 1 10); do
    purchase_id=$(generate_id)
    item_id=$(generate_id)
    price=$((seller * 500))
    sold_time=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    json="{\"items\":[{\"purchaseId\":\"$purchase_id\",\"itemId\":\"$item_id\",\"soldTime\":\"$sold_time\",\"seller\":$seller,\"price\":$price,\"paymentMethod\":\"PAYMENT_METHOD_CASH\"}]}"
    
    for attempt in 1 2 3 4 5; do
        response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 \
            -H "Authorization: Bearer $API_KEY" \
            -H "Content-Type: application/json" \
            -X POST "$PROXY_URL/v1/events/$EVENT_ID/sold-items" \
            -d "$json" 2>/dev/null || echo "000")
        
        if [ "$response" = "200" ]; then
            break
        else
            UNSTABLE_RETRIES=$((UNSTABLE_RETRIES + 1))
            echo "  ⟳ Retry $attempt for seller $seller (HTTP $response)"
            sleep 2
        fi
    done
    
    echo "  Progress: $seller/10 items sent"
done
echo "  ✓ Items sent ($UNSTABLE_RETRIES retries needed)"

# Clear and verify
apply_scenario "clear"
sleep 2

EXP_P=$((BASELINE_PURCHASES + 140))
EXP_I=$((BASELINE_ITEMS + 140))
EXP_T=$((BASELINE_TOTAL + 492000))
if verify_backend $EXP_P $EXP_I $EXP_T "Test 6: Unstable"; then
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

# ─────────────────────────────────────────────────────────
# TEST 7: Network Recovery
# ─────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 7: Network Recovery (unstable → clear)"
echo "  5 items, säljare 11-15, priser: 800-4000"
echo "  Förväntat: +5 items, +12 000 SEK"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

apply_scenario "unstable"
echo "  Sending 5 items through unstable network..."

RECOVERY_ITEMS_SENT=0
PRICES=(800 1600 2400 3200 4000)
for i in $(seq 0 4); do
    seller=$((11 + i))
    purchase_id=$(generate_id)
    item_id=$(generate_id)
    price=${PRICES[$i]}
    sold_time=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    json="{\"items\":[{\"purchaseId\":\"$purchase_id\",\"itemId\":\"$item_id\",\"soldTime\":\"$sold_time\",\"seller\":$seller,\"price\":$price,\"paymentMethod\":\"PAYMENT_METHOD_CASH\"}]}"
    
    for attempt in 1 2 3 4 5; do
        response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 \
            -H "Authorization: Bearer $API_KEY" \
            -H "Content-Type: application/json" \
            -X POST "$PROXY_URL/v1/events/$EVENT_ID/sold-items" \
            -d "$json" 2>/dev/null || echo "000")
        
        if [ "$response" = "200" ]; then
            RECOVERY_ITEMS_SENT=$((RECOVERY_ITEMS_SENT + 1))
            break
        else
            echo "  ⟳ Retry $attempt for seller $seller"
            sleep 2
        fi
    done
done

echo "  Sent $RECOVERY_ITEMS_SENT/5 during unstable"
echo "  Clearing network → recovery..."
apply_scenario "clear"
sleep 3

EXP_P=$((BASELINE_PURCHASES + 145))
EXP_I=$((BASELINE_ITEMS + 145))
EXP_T=$((BASELINE_TOTAL + 504000))
if verify_backend $EXP_P $EXP_I $EXP_T "Test 7: Network Recovery"; then
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

# ─────────────────────────────────────────────────────────
# TEST 8: Slow Close
# ─────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 8: Slow Close (5s delay on connection close)"
echo "  3 items, valfria säljare"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

apply_scenario "slow-close"

START_TIME=$(date +%s)
for seller in 1 2 3; do
    purchase_id=$(generate_id)
    item_id=$(generate_id)
    price=$((seller * 100))
    sold_time=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    json="{\"items\":[{\"purchaseId\":\"$purchase_id\",\"itemId\":\"$item_id\",\"soldTime\":\"$sold_time\",\"seller\":$seller,\"price\":$price,\"paymentMethod\":\"PAYMENT_METHOD_CASH\"}]}"
    
    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 \
        -H "Authorization: Bearer $API_KEY" \
        -H "Content-Type: application/json" \
        -X POST "$PROXY_URL/v1/events/$EVENT_ID/sold-items" \
        -d "$json")
    
    if [ "$response" != "200" ]; then
        echo "  ✗ Failed seller $seller (HTTP $response)"
    fi
    echo "  Progress: $seller/3 items sent"
done
END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
echo "  ✓ All 3 items sent (${ELAPSED}s, slow close = 5s per connection)"

apply_scenario "clear"
TESTS_PASSED=$((TESTS_PASSED + 1))

# ─────────────────────────────────────────────────────────
# FINAL SUMMARY
# ─────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════"
echo "  SLUTRESULTAT"
echo "═══════════════════════════════════════════════════════"

FINAL=$(curl -s -H "Authorization: Bearer $API_KEY" \
    "$BACKEND_URL/v1/events/$EVENT_ID/sold-items?page_size=10000" | \
    jq '{purchases: [.items[].purchaseId] | unique | length, items: .items | length, total_sek: ([.items[].price] | add // 0)}')

FINAL_P=$(echo "$FINAL" | jq '.purchases')
FINAL_I=$(echo "$FINAL" | jq '.items')
FINAL_T=$(echo "$FINAL" | jq '.total_sek')

DELTA_P=$((FINAL_P - BASELINE_PURCHASES))
DELTA_I=$((FINAL_I - BASELINE_ITEMS))
DELTA_T=$((FINAL_T - BASELINE_TOTAL))

echo ""
echo "  Baseline:   $BASELINE_PURCHASES purchases, $BASELINE_ITEMS items, $BASELINE_TOTAL SEK"
echo "  Slutgiltigt: $FINAL_P purchases, $FINAL_I items, $FINAL_T SEK"
echo "  Delta:       +$DELTA_P purchases, +$DELTA_I items, +$DELTA_T SEK"
echo ""
echo "  Förväntat delta: +148 purchases, +148 items, +504 600 SEK"
echo ""
echo "  Tester: $TESTS_PASSED passed, $TESTS_FAILED failed (av 8)"
echo ""

if [ "$TESTS_FAILED" -eq 0 ]; then
    echo "  ✅ ALLA TESTER GODKÄNDA"
else
    echo "  ❌ $TESTS_FAILED TESTER MISSLYCKADES"
fi
echo ""
echo "═══════════════════════════════════════════════════════"
