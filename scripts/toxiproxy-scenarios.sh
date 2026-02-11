#!/bin/bash
# Network chaos scenarios for testing LoppisKassan resilience

set -e

TOXIPROXY_API="http://localhost:8474"
PROXY_NAME="iloppis-backend"

function clear_toxics() {
    echo "Clearing all toxics..."
    curl -s -X DELETE "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" > /dev/null
    echo "✓ All toxics cleared (normal network)"
}

function scenario_high_latency() {
    clear_toxics
    echo "Scenario: High latency (500ms ± 100ms)"
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "high_latency",
        "type": "latency",
        "attributes": {
          "latency": 500,
          "jitter": 100
        }
      }'
    echo
    echo "✓ Added 500ms latency with 100ms jitter"
}

function scenario_slow_3g() {
    clear_toxics
    echo "Scenario: Slow 3G (250 KB/s down, 50 KB/s up, 200ms latency)"
    
    # Downstream bandwidth limit
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "slow_download",
        "type": "bandwidth",
        "stream": "downstream",
        "attributes": {
          "rate": 250
        }
      }'
    
    # Upstream bandwidth limit
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "slow_upload",
        "type": "bandwidth",
        "stream": "upstream",
        "attributes": {
          "rate": 50
        }
      }'
    
    # Latency
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "mobile_latency",
        "type": "latency",
        "attributes": {
          "latency": 200,
          "jitter": 50
        }
      }'
    
    echo
    echo "✓ Simulating slow 3G connection"
}

function scenario_packet_loss() {
    clear_toxics
    echo "Scenario: Packet loss (10% loss, 200ms latency)"
    
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "packet_loss",
        "type": "slicer",
        "attributes": {
          "average_size": 100,
          "size_variation": 50,
          "delay": 10
        }
      }'
    
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "latency",
        "type": "latency",
        "attributes": {
          "latency": 200,
          "jitter": 100
        }
      }'
    
    echo
    echo "✓ Added packet fragmentation and latency"
}

function scenario_timeout() {
    clear_toxics
    echo "Scenario: Random timeouts (5% of connections hang for 30s)"
    
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "timeout_toxic",
        "type": "timeout",
        "attributes": {
          "timeout": 30000
        },
        "toxicity": 0.05
      }'
    
    echo
    echo "✓ 5% of requests will timeout after 30s"
}

function scenario_slow_close() {
    clear_toxics
    echo "Scenario: Slow connection close (5s delay on close)"
    
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "slow_close",
        "type": "slow_close",
        "attributes": {
          "delay": 5000
        }
      }'
    
    echo
    echo "✓ Connections will take 5s to close"
}

function scenario_unstable() {
    clear_toxics
    echo "Scenario: Unstable connection (mix of all problems)"
    
    # High jitter latency
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "unstable_latency",
        "type": "latency",
        "attributes": {
          "latency": 300,
          "jitter": 200
        }
      }'
    
    # Bandwidth fluctuation
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "throttle",
        "type": "bandwidth",
        "attributes": {
          "rate": 100
        }
      }'
    
    # Occasional packet loss
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "packet_drops",
        "type": "slicer",
        "attributes": {
          "average_size": 50,
          "size_variation": 30,
          "delay": 20
        }
      }'
    
    # 2% chance of timeout
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "random_timeout",
        "type": "timeout",
        "attributes": {
          "timeout": 20000
        },
        "toxicity": 0.02
      }'
    
    echo
    echo "✓ Unstable network: high jitter, throttling, packet loss, rare timeouts"
}

function list_toxics() {
    echo "Current toxics:"
    curl -s "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" | python3 -m json.tool
}

function show_help() {
    cat << EOF
Network Chaos Scenarios for LoppisKassan Testing

Usage: $0 <scenario>

Scenarios:
  clear           - Remove all toxics (normal network)
  high-latency    - 500ms ± 100ms delay
  slow-3g         - Slow 3G: 250 KB/s down, 50 KB/s up, 200ms latency
  packet-loss     - 10% packet loss with 200ms latency
  timeout         - 5% of requests timeout after 30s
  slow-close      - Connections take 5s to close
  unstable        - Mix of all problems (realistic poor network)
  list            - Show current active toxics

Examples:
  $0 slow-3g        # Simulate mobile network
  $0 unstable       # Realistic poor network
  $0 clear          # Back to normal
  $0 list           # See what's active

API Reference:
  Toxiproxy UI:  http://localhost:8474
  Proxy endpoint: http://localhost:8081 (point LoppisKassan here)
EOF
}

case "${1:-}" in
    clear)
        clear_toxics
        ;;
    high-latency)
        scenario_high_latency
        ;;
    slow-3g)
        scenario_slow_3g
        ;;
    packet-loss)
        scenario_packet_loss
        ;;
    timeout)
        scenario_timeout
        ;;
    slow-close)
        scenario_slow_close
        ;;
    unstable)
        scenario_unstable
        ;;
    list)
        list_toxics
        ;;
    *)
        show_help
        exit 1
        ;;
esac
