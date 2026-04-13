# Online Recurring Payments — GP API

A complete recurring payment implementation using the Global Payments GP API. Developers can set up subscription billing by capturing an initial payment with customer data, tokenizing the card server-side with `StoredCredential`, and scheduling future charges against the stored payment method — without handling raw card numbers at any point. All implementations use the official Global Payments SDK (`GpApiConfig`).

Available in four languages: PHP, Node.js, .NET, and Java.

---

## Available Implementations

| Language | Framework | SDK Version | Port |
|----------|-----------|-------------|------|
| [**PHP**](./php/) | Built-in Server | globalpayments/php-sdk ^13.1 | 8000 |
| [**Node.js**](./nodejs/) | Express.js | globalpayments-api ^3.10.6 | 8000 |
| [**.NET**](./dotnet/) | ASP.NET Core | GlobalPayments.Api 9.0.16 | 8000 |
| [**Java**](./java/) | Jakarta Servlet | globalpayments-sdk 14.2.20 | 8000 |

Preview links (runs in browser via CodeSandbox):
- [PHP Preview](https://githubbox.com/globalpayments-samples/online-recurring-payments/tree/main/php)
- [Node.js Preview](https://githubbox.com/globalpayments-samples/online-recurring-payments/tree/main/nodejs)
- [.NET Preview](https://githubbox.com/globalpayments-samples/online-recurring-payments/tree/main/dotnet)
- [Java Preview](https://githubbox.com/globalpayments-samples/online-recurring-payments/tree/main/java)

---

## How It Works

This project demonstrates the full recurring payment lifecycle: an initial charge captures the customer's card and stores it as a reusable payment method. All future charges use the stored method without prompting the customer for card details again.

```
Browser
  │
  ├─ GET /config ──────────────────► Server
  │                                    └─ GP API: generate scoped access token
  │  ◄── { accessToken } ──────────────┘
  │
  ├─ Hosted fields tokenize card (client-side, PCI-compliant)
  │
  ├─ POST /process-payment ────────► Server
  │   {                               ├─ is_recurring: false → charge().execute()
  │     payment_token,                └─ is_recurring: true  → charge()
  │     amount,                                                 .withStoredCredential()
  │     is_recurring,                                           .withCustomer()
  │     frequency,                                              .withAddress()
  │     start_date,                                             .execute()
  │     first_name, last_name,
  │     email, phone, address         Returns transaction_id + payment_method_id
  │   }
  │  ◄── { transactionId, paymentMethodId, customerId, schedule } ──┘
  │
  └─ Future charges use paymentMethodId (no card re-entry)
```

### StoredCredential Flow

The initial charge uses `StoredCredentialType.INSTALLMENT` and `StoredCredentialSequence.FIRST`. All subsequent charges reference the same `paymentMethodId` with `StoredCredentialSequence.SUBSEQUENT`. This meets card network requirements for recurring billing and avoids false fraud declines.

---

## Prerequisites

- Global Payments developer account — [Sign up at developer.globalpayments.com](https://developer.globalpayments.com)
- GP API credentials: `APP_ID` and `APP_KEY` (sandbox available after sign-up)
- A local runtime for your chosen language:
  - PHP 8.0+ with Composer
  - Node.js 18+ with npm
  - .NET 8.0 SDK
  - Java 17+ with Maven

---

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/globalpayments-samples/online-recurring-payments.git
cd online-recurring-payments
```

### 2. Choose a language and configure credentials

```bash
cd php       # or nodejs, dotnet, java
cp .env.sample .env
```

Edit `.env`:

```env
APP_ID=your_gp_api_app_id_here
APP_KEY=your_gp_api_app_key_here
GP_API_ENVIRONMENT=sandbox
```

### 3. Install and run

**PHP:**
```bash
composer install
php -S localhost:8000
```
Open: http://localhost:8000

**Node.js:**
```bash
npm install
npm start
```
Open: http://localhost:8000

**.NET:**
```bash
dotnet restore
dotnet run
```
Open: http://localhost:8000

**Java:**
```bash
mvn clean package
mvn cargo:run
```
Open: http://localhost:8000

### 4. Test a recurring payment

1. Open the app in your browser
2. Enter amount (e.g. `25.00`) and select **Recurring**
3. Fill in customer details (name, email, address)
4. Choose frequency (e.g. **Monthly**) and a start date
5. Use a test card from [Test Cards](#test-cards) below
6. Click **Submit** — note the `paymentMethodId` in the response
7. That ID can be used for all future recurring charges

---

## API Endpoints

### `GET /config`

Returns a scoped GP API access token for client-side hosted field initialization. Token has `PMT_POST_Create_Single` permission — it can only tokenize cards, not process transactions.

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

### `POST /process-payment`

Processes either a one-time payment or an initial recurring charge with customer data.

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
  },
  "message": "Recurring payment schedule created successfully"
}
```

**Supported frequency values:** `Weekly`, `Bi-Weekly`, `Monthly`, `Quarterly`, `Annually`

**Error response (`422`):**
```json
{
  "success": false,
  "error": "Payment declined: Insufficient funds",
  "timestamp": "2025-01-15T10:00:00.000Z"
}
```

---

## Test Cards

Use these in sandbox (`GP_API_ENVIRONMENT=sandbox`). CVV: `123`. Expiry: any future date.

| Brand | Card Number | Expected Result |
|-------|-------------|-----------------|
| Visa | 4263 9826 4026 9299 | Approved |
| Visa | 4263 9700 0000 5262 | Approved |
| Mastercard | 5425 2334 2424 1200 | Approved |
| Discover | 6011 0000 0000 0012 | Approved |
| Declined | 4000 1200 0000 1154 | Declined |

> Sandbox transactions do not move real money.

---

## Project Structure

```
online-recurring-payments/
├── index.html                 # Shared frontend (served by all backends)
├── LICENSE
├── README.md
│
├── php/                       # Port 8000
│   ├── .env.sample
│   ├── composer.json
│   ├── Dockerfile
│   ├── PaymentUtils.php       # SDK config + shared helpers
│   ├── config.php             # GET /config endpoint
│   ├── process-payment.php    # POST /process-payment endpoint
│   └── run.sh
│
├── nodejs/                    # Port 8000
│   ├── .env.sample
│   ├── package.json
│   ├── Dockerfile
│   ├── server.js              # Express app: /config, /process-payment
│   ├── paymentUtils.js        # SDK config + recurring helpers
│   └── run.sh
│
├── dotnet/                    # Port 8000
│   ├── .env.sample
│   ├── *.csproj
│   ├── Program.cs             # ASP.NET Core app: all endpoints
│   ├── Dockerfile
│   └── wwwroot/               # Static frontend files
│
└── java/                      # Port 8000
    ├── .env.sample
    ├── pom.xml
    ├── Dockerfile
    └── src/
        └── main/java/com/globalpayments/example/
            ├── ConfigServlet.java
            └── ProcessPaymentServlet.java
```

---

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `APP_ID` | Your GP API application ID | `UJqPrAhrDkGzzNoFInpzKqoI8vfZtGRV` |
| `APP_KEY` | Your GP API application key | `zCFrbrn0NKly9sB4` |
| `GP_API_ENVIRONMENT` | `sandbox` for testing, `production` for live | `sandbox` |

Credentials are available in the [GP Developer Portal](https://developer.globalpayments.com) after creating an account.

---

## Troubleshooting

**`401 Unauthorized` on `/config`**
Credentials are invalid or for the wrong environment. Verify `APP_ID` and `APP_KEY` in `.env`. Confirm `GP_API_ENVIRONMENT` matches the credential set (sandbox vs production).

**`422` — "Payment declined" on initial charge**
The test card was declined. Try a different card from the [Test Cards](#test-cards) table. Confirm `GP_API_ENVIRONMENT=sandbox` when using test cards.

**Recurring charge returns "Invalid payment method"**
The `payment_method_id` from the initial charge must be stored and reused in subsequent charges. The stored credential expires if the sandbox session ends — run a new initial charge to get a fresh ID.

**Missing customer fields validation error**
Recurring payments require `first_name`, `last_name`, and `email`. `phone`, `street_address`, `city`, `state`, and `billing_zip` are optional but improve auth rates.

**Port 8000 already in use**
Another process is binding the port. Check with `lsof -i :8000` and stop the conflicting process, or update the port in `run.sh` and the server file.

**Node.js — `Cannot find module 'globalpayments-api'`**
Run `npm install` before `npm start`. Confirm Node.js 18+ is installed: `node -v`.

**Java build fails**
Requires Java 17+ and Maven 3.8+. Verify with `java -version` and `mvn -version`. Run `mvn clean package` before `mvn cargo:run`.

---

## License

MIT — see [LICENSE](./LICENSE).
