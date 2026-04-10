# Online Recurring Payments — .NET

ASP.NET Core implementation of recurring payment processing using the Global Payments GP API SDK. Handles both one-time charges and full recurring schedule setup with customer data and `StoredCredential`.

Part of the [online-recurring-payments](../) multi-language project.

---

## Requirements

- .NET 8.0 SDK
- Global Payments GP API credentials (`APP_ID` + `APP_KEY`)

---

## Project Structure

```
dotnet/
├── .env.sample        # Environment variable template
├── dotnet.csproj      # Dependencies (GlobalPayments.Api, dotenv.net)
├── appsettings.json   # ASP.NET Core config
├── Program.cs         # App entry point + all endpoints
├── Dockerfile
├── run.sh             # Restore + run shortcut
└── wwwroot/           # Static frontend files
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
dotnet restore
```

Start the server:

```bash
dotnet run
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

`Program.cs` configures `GpApiConfig` from environment variables using `dotenv.net`:

```csharp
DotEnv.Load();

var config = new GpApiConfig
{
    AppId = Environment.GetEnvironmentVariable("APP_ID"),
    AppKey = Environment.GetEnvironmentVariable("APP_KEY"),
    Environment = GpEnvironment.TEST,
    Channel = Channel.CardNotPresent,
    Country = "US"
};

ServicesContainer.ConfigureService(config);
```

For `GET /config`, the token is scoped with `PMT_POST_Create_Single` permission so the frontend can tokenize cards without full SDK access.

---

## Endpoints

### `GET /config` — `Program.cs`

Generates a scoped access token for client-side hosted fields initialization.

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

### `POST /process-payment` — `Program.cs`

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

The initial charge attaches `StoredCredential` with `StoredCredentialType.Installment` and `StoredCredentialSequence.First`. Future charges reference the returned `paymentMethodId` with `StoredCredentialSequence.Subsequent`. This meets card network requirements for recurring billing and avoids false fraud declines.

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

---

## Running with Docker

```bash
# From project root
docker-compose up dotnet
```

Runs on http://localhost:8006 (mapped from container port 8000 via `ASPNETCORE_URLS`).

---

## Troubleshooting

**`401` on `/config`**
Check `APP_ID` and `APP_KEY` in `.env`. Confirm `GP_API_ENVIRONMENT=sandbox` matches your credential set.

**`dotenv.net` package missing**
Run `dotnet restore` to install NuGet packages before running.

**`422` — "Missing required recurring field"**
Recurring mode requires `frequency` and `start_date`. Customer fields `first_name`, `last_name`, and `email` are also required.

**"Invalid payment method" on subsequent charges**
The `payment_method_id` from the initial charge must be stored by your application. Sandbox stored credentials expire when the session ends — run a new initial charge to get a fresh ID.

**Port conflict on 8000**
Override the port: `dotnet run --urls http://localhost:9000`. Update the Docker port mapping accordingly.

**.NET version mismatch**
Requires .NET 8.0 SDK. Check with `dotnet --version`. Use the Docker container if local SDK is older.
