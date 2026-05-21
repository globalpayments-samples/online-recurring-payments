# Online Recurring Payments

> Process an initial card-not-present charge and capture reusable token metadata for recurring billing using GP API SDKs in PHP, Node.js, .NET, and Java.

## Critical Patterns

1. **Recurring mode is an enrollment step, not a scheduler.** All four implementations accept recurring fields, run an initial charge, and return token metadata, but none of them persist a billing schedule or trigger future charges on their own. If you need real subscription renewals, store `payment_method_id` in your own system and build the follow-up billing job yourself.
2. **Stored-credential handling is not identical across languages.** PHP (`PaymentUtils::createRecurringSchedule()`), Java (`PaymentUtils.createRecurringSchedule()`), and .NET (the recurring branch inside `ConfigureEndpoints()`) attach `StoredCredential` with `Recurring` + `First`; Node.js `createRecurringSchedule()` in `nodejs/paymentUtils.js` does not call `withStoredCredential()` at all. Do not assume a fix in one language automatically preserves card-network recurring semantics in the others.
3. **`GP_API_ENVIRONMENT` does not fully switch the charge path to production.** Node.js, PHP, Java, and .NET all hard-code the SDK config to `Environment.TEST` / `GpEnvironment.TEST` in their payment flows. Java only uses `GP_API_ENVIRONMENT` to pick the `/ucp/accesstoken` URL in `ProcessPaymentServlet.doGet()`, and Node/.NET only log it.
4. **The frontend and route shape vary by language.** PHP serves `config.php` and `process-payment.php`; Node.js and .NET use `/config` and `/process-payment`; Java uses `/config` and `/process-payment` through `ProcessPaymentServlet`; Node.js alone adds `/health`. Update the matching frontend file in each language directory when changing endpoint names.

## Repository Structure

### PHP (plain PHP)
- [`php/PaymentUtils.php`](php/PaymentUtils.php) — reference helper; `configureSdk()`, `sanitizePostalCode()`, `createRecurringSchedule()`, `processPaymentWithToken()`, `sendSuccessResponse()`, `sendErrorResponse()`, `handleCORS()`, `parseJsonInput()`.
- [`php/config.php`](php/config.php) — `GET /config.php`; creates a scoped transaction key with `GpApiService::generateTransactionKey()`.
- [`php/process-payment.php`](php/process-payment.php) — `POST /process-payment.php`; validates request data and delegates to `PaymentUtils::createRecurringSchedule()` or `PaymentUtils::processPaymentWithToken()`.
- [`php/index.html`](php/index.html) — hosted-fields frontend; fetches `config.php`, tokenizes via `cardForm.tokenize()`, then posts to `process-payment.php`.
- [`php/run.sh`](php/run.sh) — installs Composer deps and starts PHP's built-in server on `${PORT:-8000}`.
- [`php/.env.sample`](php/.env.sample) — sample GP API credentials.

### Node.js (Express)
- [`nodejs/server.js`](nodejs/server.js) — reference HTTP surface; `app.get('/config')`, `app.post('/process-payment')`, and `app.get('/health')`.
- [`nodejs/paymentUtils.js`](nodejs/paymentUtils.js) — `configureSdk()`, `sanitizePostalCode()`, `createRecurringSchedule()`, `processPaymentWithToken()`, `sendSuccessResponse()`, `sendErrorResponse()`, `handleCORS()`.
- [`nodejs/index.html`](nodejs/index.html) — hosted-fields frontend; fetches `/config`, tokenizes with `GlobalPayments.creditCard.form()`, then posts to `/process-payment`.
- [`nodejs/run.sh`](nodejs/run.sh) — installs npm deps and starts `server.js`.
- [`nodejs/.env.sample`](nodejs/.env.sample) — sample GP API credentials.
- [`nodejs/package.json`](nodejs/package.json) — Node runtime deps, including `globalpayments-api`.

### .NET (ASP.NET Core minimal API)
- [`dotnet/Program.cs`](dotnet/Program.cs) — app entry point; `Main()`, `SanitizePostalCode()`, `ConfigureEndpoints()`, and anonymous handlers for `app.MapGet("/config", ...)` and `app.MapPost("/process-payment", ...)`.
- [`dotnet/wwwroot/index.html`](dotnet/wwwroot/index.html) — hosted-fields frontend; fetches `/config`, tokenizes in the browser, then posts to `/process-payment`.
- [`dotnet/run.sh`](dotnet/run.sh) — runs `dotnet restore` and `dotnet run`.
- [`dotnet/.env.sample`](dotnet/.env.sample) — sample GP API credentials.
- [`dotnet/dotnet.csproj`](dotnet/dotnet.csproj) — targets `net9.0` and references `GlobalPayments.Api`.
- [`dotnet/appsettings.json`](dotnet/appsettings.json) — ASP.NET config stub; payment configuration is still read from environment variables.

### Java (Jakarta Servlet)
- [`java/src/main/java/com/globalpayments/example/PaymentUtils.java`](java/src/main/java/com/globalpayments/example/PaymentUtils.java) — SDK helper; `configureSdk()`, `sanitizePostalCode()`, `createRecurringSchedule()`, `processPaymentWithToken()`, `sendSuccessResponse()`, `sendErrorResponse()`.
- [`java/src/main/java/com/globalpayments/example/ProcessPaymentServlet.java`](java/src/main/java/com/globalpayments/example/ProcessPaymentServlet.java) — request handler; `generateNonce()`, `hashSecret()`, `doGet()`, and `doPost()` for `/config` and `/process-payment`.
- [`java/src/main/webapp/index.html`](java/src/main/webapp/index.html) — hosted-fields frontend; fetches `/config`, tokenizes in the browser, then posts to `/process-payment`.
- [`java/run.sh`](java/run.sh) — runs `mvn clean package cargo:run`.
- [`java/.env.sample`](java/.env.sample) — sample GP API credentials.
- [`java/pom.xml`](java/pom.xml) — WAR build, embedded Tomcat via Cargo, and `globalpayments-sdk` dependency.

### Shared
- [`README.md`](README.md) — top-level overview for the four implementations.
- [`package.json`](package.json) — root convenience script that starts the Node.js server from the repo root.
- [`LICENSE`](LICENSE) — repository license.
- There is no root `index.html` and no `docker-compose.yml`; the frontend and run scripts live inside each language directory.

## API Surface

| Method | Path | Implementations | Purpose |
|--------|------|-----------------|---------|
| GET | `/config` | Node.js, .NET, Java | Return a hosted-fields access token scoped to `PMT_POST_Create_Single`. |
| GET | `/config.php` | PHP | PHP-specific config endpoint; same purpose as `/config`. |
| POST | `/process-payment` | Node.js, .NET, Java | Accept a tokenized card plus payment metadata, then run a one-time charge or recurring-enrollment charge. |
| POST | `/process-payment.php` | PHP | PHP-specific payment endpoint; same purpose as `/process-payment`. |
| GET | `/health` | Node.js only | Lightweight liveness check returning `status: healthy`. |

## Environment Variables

Required in every language-specific `.env.sample`:
- `APP_ID` — GP API application ID used for transaction-key generation and SDK auth.
- `APP_KEY` — GP API application key paired with `APP_ID`.
- `GP_API_ENVIRONMENT` — documented as `sandbox` or `production`; Java uses it for the access-token URL, but the charge flows still hard-code test mode.

Optional runtime variables used in code but not listed in any `.env.sample`:
- `PORT` — read by Node.js (`process.env.PORT || 8000`), .NET (`Environment.GetEnvironmentVariable("PORT") ?? "8000"`), and `php/run.sh` (`${PORT:-8000}`). Java does not read `PORT`; Cargo pins port `8000` in `pom.xml`.

Default behavior when config is absent:
- Node.js, PHP, and .NET fall back to empty `APP_ID` / `APP_KEY` strings, then fail when they try to mint a token or submit a charge.
- Java loads values from `.env` through `dotenv-java`; missing values surface as token-generation or payment errors.

## Sandbox Credentials

Use GP API sandbox credentials from <https://developer.globalpay.com>.

| Type | Value | Notes |
|------|-------|-------|
| Visa test card | `4263970000005262` | Included in every frontend test-card helper. |
| Mastercard test card | `5425230000004415` | Included in every frontend test-card helper. |
| CVV / expiry | `123` and any future date | AMEX in the frontend helper uses `1234` CVV. |

## API Request Shape

Java's `GET /config` path builds the access token request manually in `ProcessPaymentServlet.doGet()` instead of calling the SDK helper used by the other languages.
- URL — `https://apis.sandbox.globalpay.com/ucp/accesstoken` by default, or `https://apis.globalpay.com/ucp/accesstoken` when `GP_API_ENVIRONMENT=production`.
- Headers — `Content-Type: application/json`, `X-GP-Version: 2021-03-22`, and `Accept-Encoding: gzip, deflate`.
- `app_id` — copied from `APP_ID`.
- `nonce` — generated by `generateNonce()`.
- `secret` — SHA-512 hash of `nonce + APP_KEY`, produced by `hashSecret()`.
- `grant_type` — fixed to `client_credentials`.
- `seconds_to_expire` — fixed to `600`.
- `permissions` — fixed to `PMT_POST_Create_Single`.

## Architecture Summary

**Hosted-fields tokenization:** browser `index.html` → `GET /config` or `GET /config.php` → access token → `GlobalPayments.creditCard.form()` → `cardForm.tokenize()` → `payment_token`.

**One-time payment:** browser posts `payment_token`, amount, currency, and optional billing ZIP → `processPaymentWithToken()` or the .NET inline one-time branch → `card.charge(...).execute()` → JSON response with transaction data.

**Recurring enrollment:** browser posts recurring fields plus customer details → `createRecurringSchedule()` or the .NET inline recurring branch → initial charge (with stored credential in PHP/Java/.NET only) → app returns `payment_method_id`, synthetic `customer_id`, and echoed schedule fields for the caller to persist.

## Security Notes

This is demo code, not a production subscription platform. Node.js and PHP explicitly allow permissive CORS, while the .NET and Java samples rely on same-origin serving and do not add auth around the payment endpoints. None of the implementations keep a durable record of customers or stored credentials. The per-language frontends also ship placeholder business-policy links and test-card helpers that must be replaced before any live deployment.

## How to Run

- PHP — `cd php && ./run.sh` → installs Composer packages and serves `http://localhost:8000` by default.
- Node.js — `cd nodejs && ./run.sh` → installs npm packages and serves `http://localhost:8000` by default.
- .NET — `cd dotnet && ./run.sh` → restores packages and runs on `http://localhost:8000` by default; the project targets `net9.0`, so use a .NET 9 SDK.
- Java — `cd java && ./run.sh` → runs `mvn clean package cargo:run` and serves `http://localhost:8000`; `pom.xml` sets `maven.compiler.release` to `23`, so you need a JDK that supports Java 23.
- Browser requirement — every implementation uses hosted fields in `index.html`, so you need a real browser to generate a `payment_token` before the payment endpoint can succeed.

## How to Verify

- Node.js / .NET / Java config endpoint — `curl http://localhost:8000/config` → expect `{"success":true,"data":{"accessToken":"..."}}` when valid sandbox credentials are configured.
- PHP config endpoint — `curl http://localhost:8000/config.php` → expect the same response shape.
- Node.js health endpoint — `curl http://localhost:8000/health` → expect `{"success":true,"data":{"status":"healthy",...}}`.
- Node.js / .NET / Java payment endpoint — `curl -X POST http://localhost:8000/process-payment -H 'Content-Type: application/json' -d '{"payment_token":"<browser-generated token>","amount":29.99,"currency":"USD","is_recurring":false}'` → expect `success: true` plus transaction data when the token is valid.
- PHP payment endpoint — `curl -X POST http://localhost:8000/process-payment.php -H 'Content-Type: application/json' -d '{"payment_token":"<browser-generated token>","amount":29.99,"currency":"USD","is_recurring":false}'` → expect the same success shape.
- Recurring verification — repeat the payment curl with `"is_recurring":true`, `"frequency":"Monthly"`, `"start_date":"2026-01-01"`, `"first_name":"Jane"`, `"last_name":"Doe"`, and `"email":"jane.doe@example.com"`; expect `payment_method_id`, `customer_id`, and `status: active` in the response.
- Token caveat — you cannot mint a real `payment_token` with curl alone because the token comes from the browser-hosted fields integration.

## Making Changes

The repo aims to expose the same user-facing recurring-payment flow in PHP, Node.js, .NET, and Java, but the implementations are not perfectly identical today. When you change validation, payload shape, stored-credential handling, or response fields in one language, inspect and update the others deliberately rather than assuming parity already exists.

Do not modify the per-language frontend files (`php/index.html`, `nodejs/index.html`, `dotnet/wwwroot/index.html`, `java/src/main/webapp/index.html`) or the four `.env.sample` files in isolation if the same integration change should apply everywhere. There is no shared root frontend file or multi-service Docker file to update.

## SDK Versions

- PHP — `globalpayments/php-sdk` `^13.1`
- Node.js — `globalpayments-api` `^3.10.7`
- .NET — `GlobalPayments.Api` `9.0.16`
- Java — `globalpayments-sdk` `14.2.20`
