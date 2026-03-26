# Node.js Recurring Payment Example

This example demonstrates recurring payment processing using Node.js, Express, and the Global Payments GP API SDK.

## Requirements

- Node.js 14.x or later
- npm (Node Package Manager)
- Global Payments account and GP API credentials

## Project Structure

- `server.js` - Main application file containing server setup and payment processing endpoints
- `paymentUtils.js` - Utility module with payment processing functions
- `index.html` - Client-side payment form with GP API integration
- `package.json` - Project dependencies and scripts
- `.env.sample` - Template for environment variables
- `run.sh` - Convenience script to run the application

## Setup

1. Clone this repository
2. Copy `.env.sample` to `.env`
3. Update `.env` with your Global Payments GP API credentials:
   ```
   APP_ID=your_gp_api_app_id_here
   APP_KEY=your_gp_api_app_key_here
   GP_API_ENVIRONMENT=sandbox
   ```
4. Install dependencies:
   ```bash
   npm install
   ```
5. Run the application:
   ```bash
   ./run.sh
   ```
   Or manually:
   ```bash
   node server.js
   ```

## Implementation Details

### GP API Configuration

The application uses Global Payments GP API for payment processing:
- Loads credentials from .env file (APP_ID and APP_KEY)
- Configures GP API with sandbox/production environment
- Sets up Card Not Present channel for online transactions
- Generates access tokens for client-side tokenization

### Payment Processing Flow

#### Recurring Payments
1. Client loads the page and requests GP API access token from `/config` endpoint
2. User enters card details in GP API secure form
3. Frontend creates single-use token using GP API JS SDK
4. User fills in customer information and recurring schedule details
5. Frontend sends payment data with `is_recurring: true` to `/process-payment`
6. Server creates initial charge with StoredCredential for recurring payments
7. Server stores payment method for future recurring charges
8. Returns recurring schedule details and transaction confirmation

#### One-Time Payments
1. Client loads the page and requests GP API access token from `/config` endpoint
2. User enters card details in GP API secure form
3. Frontend creates single-use token using GP API JS SDK
4. Frontend sends payment data with `is_recurring: false` to `/process-payment`
5. Server processes one-time charge
6. Returns transaction confirmation

### Server Endpoints

#### GET /config
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

#### POST /process-payment
Processes a payment using the provided token and customer information.

**Request Parameters (Recurring Payment):**
- `payment_token` (string, required) - Token from client-side SDK
- `amount` (number, required) - Payment amount
- `currency` (string, optional) - Currency code (default: 'USD')
- `is_recurring` (boolean, required) - Set to true for recurring payments
- `frequency` (string, required) - Payment frequency (weekly, monthly, etc.)
- `start_date` (string, required) - Recurring payment start date (YYYY-MM-DD)
- `first_name` (string, required) - Customer first name
- `last_name` (string, required) - Customer last name
- `email` (string, required) - Customer email
- `phone` (string, optional) - Customer phone number
- `street_address` (string, optional) - Billing street address
- `city` (string, optional) - Billing city
- `state` (string, optional) - Billing state/province
- `billing_zip` (string, optional) - Billing postal code

**Request Parameters (One-Time Payment):**
- `payment_token` (string, required) - Token from client-side SDK
- `amount` (number, required) - Payment amount
- `currency` (string, optional) - Currency code (default: 'USD')
- `is_recurring` (boolean, optional) - Set to false or omit for one-time payment
- `billing_zip` (string, optional) - Billing postal code

**Response (Success - Recurring):**
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
        "response_code": "SUCCESS",
        "message": "Initial payment successful. Recurring payment method stored.",
        "timestamp": "2024-10-21T12:00:00.000Z",
        "gateway_response": {
            "auth_code": "12345A",
            "reference_number": "xxx"
        }
    },
    "message": "Recurring payment schedule created successfully",
    "timestamp": "2024-10-21T12:00:00.000Z"
}
```

**Response (Success - One-Time):**
```json
{
    "success": true,
    "data": {
        "transaction_id": "TRN_xxx",
        "amount": 10.00,
        "currency": "USD",
        "status": "approved",
        "response_code": "SUCCESS",
        "response_message": "Approved",
        "timestamp": "2024-10-21T12:00:00.000Z",
        "gateway_response": {
            "auth_code": "12345A",
            "reference_number": "xxx"
        }
    },
    "message": "Payment processed successfully",
    "timestamp": "2024-10-21T12:00:00.000Z"
}
```

**Response (Error):**
```json
{
    "success": false,
    "message": "Payment processing failed: [error details]",
    "error_code": "API_ERROR",
    "timestamp": "2024-10-21T12:00:00.000Z"
}
```

#### GET /health
Health check endpoint to verify service status.

**Response:**
```json
{
    "success": true,
    "data": {
        "status": "healthy",
        "service": "recurring-payments-api",
        "timestamp": "2024-10-21T12:00:00.000Z"
    },
    "message": "Service is healthy",
    "timestamp": "2024-10-21T12:00:00.000Z"
}
```

### Key Features

#### Recurring Payment Setup
- **StoredCredential Implementation**: Uses GP API StoredCredential with `CardHolder` initiator, `Recurring` type, and `First` sequence
- **Initial Payment Validation**: Processes an initial charge to validate the payment method
- **Payment Method Storage**: Stores tokenized card for future recurring charges
- **Customer Data Association**: Links customer information with the payment method
- **Flexible Scheduling**: Supports various frequencies (weekly, monthly, quarterly, yearly)

#### Security Features
- **Client-Side Tokenization**: Card data never touches your server
- **Access Token Scoping**: Frontend tokens limited to PMT_POST_Create_Single permission
- **CORS Handling**: Proper cross-origin resource sharing configuration
- **Input Validation**: Comprehensive validation of all required fields
- **Error Handling**: Detailed error messages with appropriate HTTP status codes

#### Payment Utils Module
The `paymentUtils.js` module provides reusable functions:
- `configureSdk()` - Initialize GP API configuration
- `sanitizePostalCode()` - Clean and validate postal codes
- `createRecurringSchedule()` - Set up recurring payment with StoredCredential
- `processPaymentWithToken()` - Process one-time payment
- `sendSuccessResponse()` - Standardized success responses
- `sendErrorResponse()` - Standardized error responses
- `handleCORS()` - CORS middleware

### Error Handling

The application implements comprehensive error handling:
- **Validation Errors**: Returns 400 with specific field error messages
- **API Errors**: Returns 400 with GP API error details
- **Server Errors**: Returns 500 with generic error message
- **Configuration Errors**: Returns 500 with configuration error details

All errors include:
- `success: false` flag
- Descriptive error message
- Error code (VALIDATION_ERROR, API_ERROR, SERVER_ERROR, CONFIG_ERROR)
- Timestamp

## Testing

### Test Cards (GP API Sandbox)

Use these test cards in the sandbox environment:

**Successful Payment:**
- Card Number: 4263970000005262
- CVV: 123
- Expiry: Any future date

**Declined Payment:**
- Card Number: 4000120000001154
- CVV: 123
- Expiry: Any future date

### Testing Recurring Payments

1. Fill in all customer information fields
2. Select a payment frequency (e.g., Monthly)
3. Choose a start date
4. Enter a test card number
5. Set `is_recurring` to true
6. Submit the form
7. Verify the response includes recurring schedule details

### Testing One-Time Payments

1. Enter minimal information (token and amount)
2. Set `is_recurring` to false or omit the field
3. Submit the form
4. Verify the response includes transaction details

## Security Considerations

This example demonstrates basic implementation. For production use, consider:

- **Input Validation**: Implement additional validation for all user inputs
- **Rate Limiting**: Add request rate limiting to prevent abuse
- **Security Headers**: Include security headers (HSTS, CSP, etc.)
- **Logging**: Implement comprehensive logging with PII protection
- **Fraud Prevention**: Add fraud detection and prevention measures
- **HTTPS**: Always use HTTPS in production
- **Environment Variables**: Secure storage of credentials (never commit .env)
- **PCI Compliance**: Ensure all PCI DSS requirements are met
- **Error Messages**: Don't expose sensitive information in error messages
- **Session Management**: Implement proper session handling if needed

## Troubleshooting

### Common Issues

**"Configuration error: Invalid credentials"**
- Verify APP_ID and APP_KEY in .env file
- Ensure credentials are for the correct environment (sandbox/production)

**"Missing required field: payment_token"**
- Ensure frontend is successfully generating tokens
- Check browser console for GP API JS SDK errors
- Verify access token is being retrieved from /config endpoint

**"Payment failed: Insufficient funds"**
- Normal for test cards that simulate declined transactions
- Use test cards that simulate successful payments

**Module import errors**
- Ensure package.json has `"type": "module"`
- Use .js extension in all import statements
- Run `npm install` to install all dependencies

## Production Deployment

Before deploying to production:

1. Update GP_API_ENVIRONMENT to 'production' in .env
2. Use production GP API credentials
3. Implement comprehensive error handling and logging
4. Add monitoring and alerting
5. Configure proper HTTPS/SSL certificates
6. Implement rate limiting and DDoS protection
7. Review and implement all security considerations
8. Test thoroughly with production credentials in test mode
9. Set up backup and disaster recovery procedures
10. Implement proper CI/CD pipelines

## Additional Resources

- [Global Payments Developer Hub](https://developer.globalpayments.com/)
- [GP API Documentation](https://developer.globalpayments.com/api/references-overview)
- [Node.js SDK on GitHub](https://github.com/globalpayments/node-sdk)
- [GP API JS SDK](https://github.com/globalpayments/globalpayments-js)

## Support

For issues or questions:
- Check the [Global Payments Developer Hub](https://developer.globalpayments.com/)
- Review the [SDK documentation](https://github.com/globalpayments/node-sdk)
- Contact Global Payments support

## License

This example is provided as-is for demonstration purposes.
