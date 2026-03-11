# Network Chaos Testing with Toxiproxy

## Quick Start

```bash
# 1. Start toxiproxy
make toxiproxy-up

# 2. Run load test through proxy (normal network)
make load-test ENV=load-test-proxied.env

# 3. Apply network chaos scenario
make toxiproxy-scenario SCENARIO=slow-3g

# 4. Run load test again to see degraded performance
make load-test ENV=load-test-proxied.env

# 5. Clear toxics (back to normal)
make toxiproxy-scenario SCENARIO=clear

# 6. Stop proxy
make toxiproxy-down
```

## Available Scenarios

| Scenario | Description | Use Case |
|----------|-------------|----------|
| `clear` | Normal network (no toxics) | Baseline test |
| `high-latency` | 500ms ± 100ms delay | Test UI responsiveness |
| `slow-3g` | 250 KB/s down, 50 KB/s up, 200ms | Mobile network simulation |
| `packet-loss` | Packet fragmentation + 200ms latency | Flaky WiFi |
| `timeout` | 5% of requests timeout after 30s | Test retry logic |
| `slow-close` | 5s delay on connection close | Connection pool exhaustion |
| `unstable` | Mix of all problems | Realistic poor network |

## Scenario Examples

### Test Mobile Network Performance
```bash
make toxiproxy-scenario SCENARIO=slow-3g
make load-test ENV=load-test-proxied.env
```

### Test Timeout Handling
```bash
make toxiproxy-scenario SCENARIO=timeout
# Watch LoppisKassan fallback to offline mode
```

### Test Under Worst Conditions
```bash
make toxiproxy-scenario SCENARIO=unstable
# High jitter, throttling, packet loss, rare timeouts
```

## Manual Toxic Control

### REST API
```bash
# List active toxics
curl http://localhost:8474/proxies/iloppis-backend/toxics | jq

# Add custom latency
curl -X POST http://localhost:8474/proxies/iloppis-backend/toxics \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my_latency",
    "type": "latency",
    "attributes": {
      "latency": 1000,
      "jitter": 200
    }
  }'

# Remove specific toxic
curl -X DELETE http://localhost:8474/toxics/my_latency
```

### Toxic Types

**latency** - Add delay to connection
```json
{
  "latency": 500,    // milliseconds
  "jitter": 100      // variation in ms
}
```

**bandwidth** - Limit throughput
```json
{
  "rate": 100        // KB/s
}
```

**slicer** - Fragment packets (simulates loss)
```json
{
  "average_size": 100,
  "size_variation": 50,
  "delay": 10
}
```

**timeout** - Close connections after delay
```json
{
  "timeout": 5000    // milliseconds
}
```

**slow_close** - Delay connection close
```json
{
  "delay": 5000      // milliseconds
}
```

**toxicity** - Probability (0.0-1.0) that toxic applies
```json
{
  "type": "timeout",
  "toxicity": 0.1,   // 10% of requests
  "attributes": { "timeout": 5000 }
}
```

## Integration with LoppisKassan

The proxy runs on `localhost:8081` and forwards to backend `localhost:8080`.

### Configuration

Update any ENV file to use proxy:
```bash
# Before (direct backend)
API_BASE_URL=http://127.0.0.1:8080

# After (through toxiproxy)
API_BASE_URL=http://localhost:8081
```

### Expected Behavior

With network chaos active, LoppisKassan should:
- ✓ Show loading indicators during high latency
- ✓ Fallback to offline mode on timeout
- ✓ Queue sales locally and retry when network recovers
- ✓ Display connection status to user

## Troubleshooting

### Proxy not starting
```bash
# Check if port 8081 is in use
lsof -ti:8081

# Restart proxy
make toxiproxy-down && make toxiproxy-up
```

### Backend not reachable through proxy
```bash
# Check proxy status
curl http://localhost:8474/proxies

# Verify upstream connection
curl http://localhost:8081/v1/events
```

### Toxics not applying
```bash
# List active toxics
make toxiproxy-scenario SCENARIO=list

# Clear and reapply
make toxiproxy-scenario SCENARIO=clear
make toxiproxy-scenario SCENARIO=slow-3g
```

## Architecture

```
┌──────────────┐   localhost:8081   ┌───────────┐   localhost:8080   ┌─────────┐
│ LoppisKassan │ ──────────────────> │ toxiproxy │ ──────────────────> │ Backend │
└──────────────┘  (with toxics)     └───────────┘   (clean)           └─────────┘
```

Toxiproxy sits between the application and backend, allowing dynamic injection of network issues without modifying code.

## Performance Notes

- **No toxics**: ~0.5ms overhead
- **Latency toxic**: Exactly as configured
- **Bandwidth toxic**: CPU impact ~5% per 1000 req/s
- **Multiple toxics**: Additive effect (latency + bandwidth + packet loss)

## Advanced: Custom Scenarios

Edit `scripts/toxiproxy-scenarios.sh` to add custom scenarios:

```bash
function scenario_my_custom() {
    clear_toxics
    echo "My custom network scenario"
    
    # Your toxics here
    curl -X POST "$TOXIPROXY_API/proxies/$PROXY_NAME/toxics" \
      -H "Content-Type: application/json" \
      -d '{...}'
}
```

Then call:
```bash
make toxiproxy-scenario SCENARIO=my-custom
```
