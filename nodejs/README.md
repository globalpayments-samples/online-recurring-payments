# Online Recurring Payments — Node.js

Node.js/Express implementation of recurring payment processing using the Global Payments GP API SDK. Handles both one-time charges and full recurring schedule setup with customer data and `StoredCredential`.

Part of the [online-recurring-payments](../) multi-language project.

---

## Requirements

- Node.js 18+
- npm
- Global Payments GP API credentials (`APP_ID` + `APP_KEY`)

---

## Project Structure

```
nodejs/
├── .env.sample       # Environment variable template
├── package.json      # Dependencies (globalpayments-api)
├── Dockerfile
├── run.sh            # Install + start shortcut
├── server.js         # Express app: /config, /process-payment
├── paymentUtils.js   # GpApiConfig setup + recurring helpers
└── index.html        # Shared frontend (symlinked from root)
```

---

## Setup

```bash
cp .env.sample .env
```

Edit `.env`:

```env
APP_ID=your_gp_api_app_id_here
APP_KEY=your_gp_api_app_key_here
GP_API_ENVIRONMENT=sandbox
```

Install dependencies:

```bash
npm install
```

Start the server:

```bash
npm start
# or
./run.sh
```

Open: http://localhost:8000

---

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `APP_ID` | Your GP API application ID | `UJqPrAhrDkGzzNoFInpzKqoI8vfZtGRV` |
| `APP_KEY` | Your GP API application key | `zCFrbrn0NKly9sB4` |
| `GP_API_ENVIRONMENT` | `sandbox` for testing, `production` for live | `sandbox` |

Credentials available in the [GP Developer Portal](https://developer.globalpayments.com).

---

## SDK Configuration

`paymentUtils.js` configures `GpApiConfig` from environment variables:

```js
const config = new GpApiConfig();
config.appId = process.env.APP_ID;
config.appKey = process.env.APP_KEY;
config.environment = Environment.TEST; // or PRODUCTION
config.channel = Channel.CardNotPresent;
config.country = 'US';

ServicesContainer.configureService(config);
```

For `GET /config`, the token is scoped with `PMT_POST_Create_Single` permission so the frontend can tokenize cards without server-side write access.

---

## Endpoints

### `GET /config` — `server.js`

Generates a scoped access token for client-side hosted fields.

**Response:**
```json
{
  "success": true,
  "data": {
    "accessToken": "uua7...."
  },
  "message": "Configuration retrieved successfully",
  "timestamp": "2025-01-15T10:00:00.000Z"
}
```

---

### `POST /process-payment` — `server.js`

#### One-time payment

**Request body:**
```json
{
  "payment_token": "PMT_abc123...",
  "amount": 25.00,
  "currency": "USD",
  "is_recurring": false
}
```

**Success response:**
```json
{
  "success": true,
  "data": {
    "transaction_id": "TRN_abc123xyz",
    "amount": 25.00,
    "currency": "USD",
    "status": "captured",
    "message": "Payment processed successfully"
  }
}
```

#### Recurring payment setup

**Request body:**
```json
{
  "payment_token": "PMT_abc123...",
  "amount": 25.00,
  "currency": "USD",
  "is_recurring": true,
  "frequency": "Monthly",
  "start_date": "2025-02-01",
  "first_name": "Jane",
  "last_name": "Smith",
  "email": "jane.smith@example.com",
  "phone": "555-0100",
  "street_address": "123 Main St",
  "city": "Atlanta",
  "state": "GA",
  "billing_zip": "30301"
}
```

**Success response:**
```json
{
  "success": true,
  "data": {
    "transaction_id": "TRN_abc123xyz",
    "payment_method_id": "PMT_stored456",
    "customer_id": "CUS_789xyz",
    "amount": 25.00,
    "currency": "USD",
    "frequency": "Monthly",
    "start_date": "2025-02-01",
    "status": "active",
    "message": "Initial payment successful. Recurring payment method stored."
  }
}
```

Supported `frequency` values: `Weekly`, `Bi-Weekly`, `Monthly`, `Quarterly`, `Annually`

**Error response (`422`):**
```json
{
  "success": false,
  "error": "Payment declined: Insufficient funds",
  "timestamp": "2025-01-15T10:00:00.000Z"
}
```

---

## StoredCredential Flow

The initial charge uses `StoredCredentialType.INSTALLMENT` and `StoredCredentialSequence.FIRST`. Future charges reference the returned `paymentMethodId` with `StoredCredentialSequence.SUBSEQUENT`. This meets card network requirements for recurring billing and avoids false fraud declines.

---

## Test Cards

Use these in sandbox (`GP_API_ENVIRONMENT=sandbox`). CVV: `123`. Expiry: any future date.

| Brand | Card Number | Expected Result |
|-------|-------------|-----------------|
| Visa | 4263 9826 4026 9299 | Approved |
| Mastercard | 5425 2334 2424 1200 | Approved |
| Discover | 6011 0000 0000 0012 | Approved |
| Declined | 4000 1200 0000 1154 | Declined |

---

## Running with Docker

```bash
# From project root
docker-compose up nodejs
```

Runs on http://localhost:8001 (mapped from container port 8000).

---

## Troubleshooting

**`401` on `/config`**
Check `APP_ID` and `APP_KEY` in `.env`. Confirm `GP_API_ENVIRONMENT=sandbox` matches your credential set.

**`422` — "Missing required customer field"**
Recurring mode requires `first_name`, `last_name`, and `email` in the request body. `phone`, `street_address`, `city`, `state`, `billing_zip` are optional but improve authorization rates.

**"Invalid payment method" on subsequent charges**
The `payment_method_id` from the initial charge must be stored by your application. Sandbox stored credentials expire when the session ends — run a new initial charge to get a fresh ID.

**`Cannot find module 'globalpayments-api'`**
Run `npm install` before starting the server.

**`422` — "Missing required recurring field"**
Recurring mode requires `frequency` and `start_date` in addition to customer fields.

**ES module import errors**
Package uses ESM (`import`/`export`). Requires Node.js 18+. Check with `node -v`.

**Port 8000 already in use**
Check with `lsof -i :8000` and stop the conflicting process, or change the port in `run.sh`.
