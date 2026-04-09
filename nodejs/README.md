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

Calls `GpApiService.generateTransactionKey()` with scoped permissions. Returns `accessToken` for client-side hosted fields.

### `POST /process-payment` — `server.js`

One-time payment (`is_recurring: false`):
- Calls `processPaymentWithToken()` from `paymentUtils.js`
- Uses `CreditCardData` + `charge().execute()`
- Returns `transactionId`

Recurring setup (`is_recurring: true`):
- Validates `frequency`, `start_date`, `first_name`, `last_name`, `email`
- Calls `createRecurringSchedule()` from `paymentUtils.js`
- Uses `StoredCredentialType.INSTALLMENT` + `StoredCredentialSequence.FIRST`
- Returns `transactionId`, `paymentMethodId`, `customerId`

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

**`Cannot find module 'globalpayments-api'`**
Run `npm install` before starting the server.

**`422` — "Missing required recurring field"`**
Recurring mode requires `frequency` and `start_date`. Customer fields `first_name`, `last_name`, `email` are also required.

**ES module import errors**
Package uses ESM (`import`/`export`). Requires Node.js 18+. Check with `node -v`.
