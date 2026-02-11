# Load Testing Tools

## Overview

This package contains tools for testing the iLoppis platform under load conditions.

- **SetupRunner**: Creates complete market infrastructure from scratch
- **LoadTestRunner**: Generates load by creating sold items
- **MailHogClient**: Handles magic link email parsing from MailHog

## Quick Start

### 1. Setup Market Infrastructure

First, create a complete market with event and vendors:

```bash
# Copy example config
cp load-test-setup.env.example load-test-setup.env

# Edit if needed (default values work with local docker-compose)
# Run setup
make setup-test ENV=load-test-setup.env
```

**Output:**
```
══════════════════════════════════════
✓ SETUP COMPLETE!
══════════════════════════════════════
Market ID:     abc123...
Event ID:      def456...
Cashier Alias: ABC-DEF
API Key:       xyz789...

Add to your load test ENV file:
EVENT_ID=def456...
API_KEY=xyz789...
══════════════════════════════════════
```

### 2. Run Load Test

Use the Event ID and API Key from setup:

```bash
# Copy example config
cp load-test-iloppis.env.example load-test-iloppis.env

# Edit file and add EVENT_ID and API_KEY from setup output
# Run load test
make load-test ENV=load-test-iloppis.env
```

## Setup Process Details

SetupRunner performs these steps:

1. **Login Platform Owner** (`goran@goencoder.se`)
   - Triggers magic link via `/v1/login`
   - Polls MailHog for email
   - Extracts API key from magic link
   - First user automatically becomes platform admin

2. **Login Market Owner** (`peter.karlsson@sillstugan.se`)
   - Same magic link process

3. **Create Draft Market**
   - POST `/v1/draft-markets`
   - Name, description, revenue split, etc.

4. **Approve Market** (as platform owner)
   - POST `/v1/draft-markets/{id}/approval`

5. **Create Event**
   - POST `/v1/events`
   - Links to approved market

6. **Create Vendors**
   - POST `/v1/events/{id}/vendors` (×100)
   - Creates `vendor1@loadtest.local` through `vendor100@loadtest.local`

7. **Approve All Vendors**
   - PUT `/v1/events/{id}/vendors/{vendorId}` (×100)
   - Sets status to `approved`

8. **Create Cashier API Key**
   - POST `/v1/events/{id}/api-keys`
   - Type: `API_KEY_TYPE_WEB_CASHIER`

## Configuration

### Setup Config (`load-test-setup.env`)

```bash
API_BASE_URL=http://localhost:8080
MAILHOG_URL=http://localhost:8025
PLATFORM_OWNER_EMAIL=goran@goencoder.se
MARKET_OWNER_EMAIL=peter.karlsson@sillstugan.se
MARKET_NAME="Load Test Market"
EVENT_NAME="Load Test Event"
VENDOR_COUNT=100
```

### Load Test Config (`load-test-iloppis.env`)

```bash
MODE=ILOPPIS
API_BASE_URL=http://localhost:8080
EVENT_ID=<from setup output>
API_KEY=<from setup output>
TARGET_TOTAL=500000
SELLER_COUNT=100
MIN_PRICE=10
MAX_PRICE=1000
MIN_ITEMS_PER_PURCHASE=10
MAX_ITEMS_PER_PURCHASE=20
```

## Environment Requirements

### Local Testing
- **Backend**: Running on http://localhost:8080
- **MailHog**: Running on http://localhost:8025
- **Database**: Clean state recommended (or setup will add to existing data)

### Staging Testing
```bash
API_BASE_URL=https://iloppis-staging.fly.dev
MAILHOG_URL=<staging mailhog url if available>
```

## MailHog Integration

SetupRunner uses MailHog to automatically extract API keys from magic link emails:

- Triggers login → Email sent to MailHog
- Polls `/api/v2/messages?limit=50`
- Finds email for target user
- Extracts API key using regex: `api_key=([a-f0-9-]+)`
- Timeout: 30 seconds

**Magic Link Format:**
```
https://iloppis.fly.dev/?api_key=ce169ac8-c65c-426c-83a8-df3e24f30d13
```

## Troubleshooting

### Setup fails at login step
**Symptom:** `Failed to find magic link email for X after 30 attempts`

**Solutions:**
- Check MailHog is running: `curl http://localhost:8025/api/v2/messages`
- Check backend sent email: Look in backend logs for "Sending magic link"
- Check email matches: Ensure `PLATFORM_OWNER_EMAIL` is correct

### Vendor creation fails
**Symptom:** API error when creating vendors

**Solutions:**
- Check `VENDOR_COUNT` doesn't exceed `MAX_VENDORS` in market config
- Ensure market is approved before creating event
- Check backend logs for validation errors

### Load test fails to submit items
**Symptom:** Items rejected or API errors

**Solutions:**
- Ensure vendors are approved: Check `/v1/events/{id}/vendors`
- Check seller numbers match: LoadTest uses `1..SELLER_COUNT`
- Verify API key has cashier permissions

## Local Mode (No Backend)

For testing without backend:

```bash
MODE=LOCAL
EVENT_ID=local-test
# ... other params
```

This writes directly to `data/events/{eventId}/pending_items.jsonl`

## Performance

Typical setup times (local):
- Login (×2): ~2s
- Market creation: ~0.5s
- Market approval: ~0.5s
- Event creation: ~0.5s
- Vendor creation (×100): ~20s
- Vendor approval (×100): ~20s
- **Total: ~45 seconds**

Load test performance:
- ~50-100 items/second (localhost)
- ~20-30 items/second (staging)
- 500,000 kr target → ~10,000 items → ~2-5 minutes
