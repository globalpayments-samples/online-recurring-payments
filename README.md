# Online Recurring Payments - GP API

This project demonstrates recurring payment implementation using Global Payments GP API across multiple programming languages. Each implementation showcases how to set up subscription billing, manage recurring payment schedules, and process both one-time and recurring payments.

## Available Implementations

- [.NET Core](./dotnet/) - ASP.NET Core web application
- [Java](./java/) - Jakarta EE servlet-based web application
- [Node.js](./nodejs/) - Express.js web application
- [PHP](./php/) - PHP web application

## Features

### Recurring Payment Management
- **StoredCredential Implementation** - Secure storage of payment methods for future recurring charges
- **Customer Data Collection** - Comprehensive customer information capture and association
- **Flexible Scheduling** - Support for various payment frequencies:
  - Weekly
  - Bi-Weekly
  - Monthly
  - Quarterly
  - Yearly/Annually
- **Initial Payment Validation** - Processes an initial charge to validate the payment method
- **Payment Method Storage** - Stores tokenized payment methods for future use

### Payment Processing
- **One-Time Payments** - Standard single payment processing
- **Recurring Payments** - Subscription-based recurring charges
- **GP API Integration** - Full Global Payments GP API support
- **Client-Side Tokenization** - Secure card data handling with GP API JS SDK
- **Access Token Generation** - Backend generates scoped tokens for frontend use

### Security & Compliance
- **PCI Compliance** - Card data never touches your server
- **Tokenization** - Client-side card tokenization using GP API
- **Scoped Access Tokens** - Frontend tokens limited to PMT_POST_Create_Single permission
- **Environment Variables** - Secure credential management
- **Input Validation** - Comprehensive validation of all payment data

## Project Structure

```
online-recurring-payments/
├── dotnet/              # .NET Core implementation
│   ├── Program.cs       # Main application with payment endpoints
│   ├── wwwroot/         # Static files
│   └── README.md        # .NET-specific documentation
├── java/                # Java implementation
│   ├── src/             # Java source files
│   │   └── main/
│   │       ├── java/    # Java payment processing code
│   │       └── webapp/  # Web resources
│   └── README.md        # Java-specific documentation
├── nodejs/              # Node.js implementation
│   ├── server.js        # Express server with payment endpoints
│   ├── paymentUtils.js  # Payment utility functions
│   └── README.md        # Node.js-specific documentation
├── php/                 # PHP implementation
│   ├── config.php       # Configuration and access token endpoint
│   ├── process-payment.php  # Payment processing endpoint
│   ├── PaymentUtils.php     # Payment utility class
│   └── README.md        # PHP-specific documentation
├── index.html           # Shared frontend example
├── LICENSE              # Project license
└── README.md            # This file
```

## Quick Start

### Prerequisites

- Global Payments account with GP API credentials (APP_ID and APP_KEY)
- Development environment for your chosen language:
  - .NET Core 6.0+
  - Java 11+
  - Node.js 14.x+
  - PHP 7.4+

### General Setup

1. **Choose your language** - Navigate to any implementation directory (dotnet, java, nodejs, php)

2. **Set up credentials** - Copy `.env.sample` to `.env` and add your GP API credentials:
   ```
   APP_ID=your_gp_api_app_id_here
   APP_KEY=your_gp_api_app_key_here
   GP_API_ENVIRONMENT=sandbox
   ```

3. **Install dependencies** - Each directory has its own dependency installation:
   - .NET: `dotnet restore`
   - Java: `mvn clean install`
   - Node.js: `npm install`
   - PHP: `composer install`

4. **Run the server** - Execute `./run.sh` or use language-specific commands

5. **Test the application** - Open `http://localhost:8000` in your browser

### Language-Specific Instructions

See individual README files in each language directory for detailed setup and implementation information.

## Recurring Payment Flow

### 1. Frontend Initialization
- Client loads the page and requests GP API access token from `/config` endpoint
- Frontend initializes GP API JS SDK with the access token

### 2. Customer Input
- User enters card details in GP API secure form
- User provides customer information (name, email, phone, address)
- User selects payment frequency and start date

### 3. Tokenization
- Frontend creates single-use token using GP API JS SDK
- Card data is tokenized client-side and never reaches your server

### 4. Backend Processing
- Frontend sends payment data with `is_recurring: true` to `/process-payment`
- Server creates initial charge with customer data and address
- Payment method is stored for future recurring charges
- Server returns recurring schedule details and transaction confirmation

### 5. Future Recurring Charges
- Use the stored payment method ID for subsequent charges
- Process recurring payments according to the defined schedule
- Customer is billed automatically on the scheduled frequency

## API Endpoints

All implementations provide these standardized endpoints:

### GET /config
Returns GP API access token for client-side SDK initialization.

**Response:**
```json
{
    "success": true,
    "data": {
        "accessToken": "xxx"
    },
    "message": "Configuration retrieved successfully",
    "timestamp": "2024-10-21T12:00:00.000Z"
}
```

### POST /process-payment
Processes both one-time and recurring payments.

**Request Parameters (Recurring):**
- `payment_token` (string, required) - Token from client-side SDK
- `amount` (number, required) - Payment amount
- `currency` (string, optional) - Currency code (default: 'USD')
- `is_recurring` (boolean, required) - Set to true for recurring
- `frequency` (string, required) - Payment frequency
- `start_date` (string, required) - Start date (YYYY-MM-DD)
- `first_name`, `last_name`, `email` (string, required) - Customer info
- `phone`, `street_address`, `city`, `state`, `billing_zip` (string, optional)

**Response (Success):**
```json
{
    "success": true,
    "data": {
        "transaction_id": "TRN_xxx",
        "payment_method_id": "PMT_xxx",
        "customer_id": "CUS_xxx",
        "amount": 25.00,
        "currency": "USD",
        "frequency": "Monthly",
        "start_date": "2024-11-01",
        "status": "active",
        "message": "Initial payment successful. Recurring payment method stored."
    },
    "message": "Recurring payment schedule created successfully"
}
```

## Test Cards (GP API Sandbox)

Use these test cards in the sandbox environment:

**Successful Payment:**
- Card Number: 4263970000005262
- CVV: 123
- Expiry: Any future date

**Declined Payment:**
- Card Number: 4000120000001154
- CVV: 123
- Expiry: Any future date

## Security Considerations

This example demonstrates core implementation. For production use, implement:

- **Input Validation** - Comprehensive validation of all user inputs
- **Rate Limiting** - Prevent abuse with request rate limiting
- **Security Headers** - HSTS, CSP, X-Frame-Options, etc.
- **Logging** - Secure logging with PII protection
- **Fraud Prevention** - Implement fraud detection measures
- **HTTPS** - Always use HTTPS in production
- **Error Handling** - Don't expose sensitive information in error messages
- **Monitoring** - Implement application performance monitoring
- **Compliance** - Ensure PCI DSS, GDPR, and other compliance requirements

## Additional Resources

- [Global Payments Developer Hub](https://developer.globalpay.com/)
- [GP API Documentation](https://developer.globalpay.com/api)
- [GP API JS SDK](https://github.com/globalpayments/globalpayments-js)

## Support

For issues or questions:
- Check the [Global Payments Developer Hub](https://developer.globalpay.com/)
- Review language-specific README files in each directory
- Contact Global Payments support

## License

MIT License - see LICENSE file for details
