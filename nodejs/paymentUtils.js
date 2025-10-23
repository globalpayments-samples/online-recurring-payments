/**
 * Payment Utilities Module - GP API (Recurring Payments)
 *
 * Provides utility functions for recurring payment processing using Global Payments GP API.
 *
 * @module paymentUtils
 */

import * as dotenv from 'dotenv';
import {
    ServicesContainer,
    GpApiConfig,
    Address,
    Customer,
    CreditCardData,
    Channel,
    Environment,
    TransactionStatus
} from 'globalpayments-api';

// Load environment variables
dotenv.config();

/**
 * Configure the Global Payments SDK (GP API)
 */
export function configureSdk() {
    const config = new GpApiConfig();
    config.appId = process.env.APP_ID || '';
    config.appKey = process.env.APP_KEY || '';
    config.environment = Environment.TEST;
    config.channel = Channel.CardNotPresent;
    config.country = 'US';

    ServicesContainer.configureService(config);
}

/**
 * Sanitize postal code by removing invalid characters
 * @param {string} postalCode - Postal code to sanitize
 * @returns {string} Sanitized postal code
 */
export function sanitizePostalCode(postalCode) {
    if (!postalCode) {
        return '';
    }

    const sanitized = postalCode.replace(/[^a-zA-Z0-9-]/g, '');
    return sanitized.slice(0, 10);
}

/**
 * Create recurring payment setup with tokenized card
 *
 * This creates a stored payment method that can be used for future recurring charges.
 * For a full recurring schedule implementation, additional GP API configuration is required.
 *
 * @param {string} token - Payment token from client-side SDK
 * @param {number} amount - Payment amount
 * @param {string} currency - Currency code (e.g., 'USD')
 * @param {object} scheduleData - Schedule configuration
 * @param {object} customerData - Customer information
 * @returns {Promise<object>} Payment result
 */
export async function createRecurringSchedule(token, amount, currency, scheduleData, customerData) {
    try {
        // Create card from token
        const card = new CreditCardData();
        card.token = token;

        // Create customer data
        const customer = new Customer();
        customer.firstName = customerData.first_name || '';
        customer.lastName = customerData.last_name || '';
        customer.email = customerData.email || '';
        customer.phoneNumber = customerData.phone || '';

        // Create address for the card
        const address = new Address();
        address.streetAddress1 = customerData.street_address || '';
        address.city = customerData.city || '';
        address.state = customerData.state || '';
        address.postalCode = sanitizePostalCode(customerData.billing_zip || '');
        address.countryCode = 'US';

        // For recurring payments, we'll process an initial charge to validate the payment method
        // and store the card for future use. The recurring flag is maintained in application logic.
        const response = await card.charge(amount)
            .withCurrency(currency)
            .withAddress(address)
            .withCustomerData(customer)
            .execute();

        if (response.responseCode === 'SUCCESS' &&
            response.responseMessage === 'CAPTURED') {

            // Calculate frequency details
            const frequency = (scheduleData.frequency || 'monthly').toLowerCase();
            const frequencyDisplay = {
                'weekly': 'Weekly',
                'biweekly': 'Bi-Weekly',
                'monthly': 'Monthly',
                'quarterly': 'Quarterly',
                'yearly': 'Yearly',
                'annually': 'Yearly'
            }[frequency] || 'Monthly';

            return {
                transaction_id: response.transactionId || `txn_${Date.now()}`,
                payment_method_id: response.cardBrandTransactionId || token,
                customer_id: `CUS_${Date.now()}`,
                amount: amount,
                currency: currency,
                frequency: frequencyDisplay,
                start_date: scheduleData.start_date || new Date().toISOString().split('T')[0],
                status: 'active',
                response_code: response.responseCode,
                message: 'Initial payment successful. Recurring payment method stored.',
                timestamp: new Date().toISOString(),
                gateway_response: {
                    auth_code: response.authorizationCode || '',
                    reference_number: response.referenceNumber || ''
                }
            };
        } else {
            throw new Error(`Payment failed: ${response.responseMessage || 'Unknown error'}`);
        }
    } catch (error) {
        console.error('Recurring payment setup error:', error.message);
        throw error;
    }
}

/**
 * Process one-time payment with tokenized card
 *
 * @param {string} token - Payment token from client-side SDK
 * @param {number} amount - Payment amount
 * @param {string} currency - Currency code (e.g., 'USD')
 * @param {object} billingData - Billing information
 * @returns {Promise<object>} Payment result
 */
export async function processPaymentWithToken(token, amount, currency, billingData) {
    try {
        const card = new CreditCardData();
        card.token = token;

        const address = new Address();
        address.postalCode = sanitizePostalCode(billingData.billing_zip || '');

        const response = await card.charge(amount)
            .withCurrency(currency)
            .withAddress(address)
            .execute();

        if (response.responseCode === 'SUCCESS' &&
            response.responseMessage === TransactionStatus.Captured) {

            return {
                transaction_id: response.transactionId || `txn_${Date.now()}`,
                amount: amount,
                currency: currency,
                status: 'approved',
                response_code: response.responseCode,
                response_message: response.responseMessage || 'Approved',
                timestamp: new Date().toISOString(),
                gateway_response: {
                    auth_code: response.authorizationCode || '',
                    reference_number: response.referenceNumber || ''
                }
            };
        } else {
            throw new Error(`Payment failed: ${response.responseMessage || 'Unknown error'}`);
        }
    } catch (error) {
        console.error('Payment processing error:', error.message);
        throw error;
    }
}

/**
 * Send success response
 * @param {object} res - Express response object
 * @param {object} data - Response data
 * @param {string} message - Success message
 */
export function sendSuccessResponse(res, data, message = 'Operation completed successfully') {
    res.status(200).json({
        success: true,
        data: data,
        message: message,
        timestamp: new Date().toISOString()
    });
}

/**
 * Send error response
 * @param {object} res - Express response object
 * @param {number} statusCode - HTTP status code
 * @param {string} message - Error message
 * @param {string} errorCode - Optional error code
 */
export function sendErrorResponse(res, statusCode, message, errorCode = null) {
    const response = {
        success: false,
        message: message,
        timestamp: new Date().toISOString()
    };

    if (errorCode) {
        response.error_code = errorCode;
    }

    res.status(statusCode).json(response);
}

/**
 * Handle CORS headers
 * @param {object} req - Express request object
 * @param {object} res - Express response object
 * @param {function} next - Express next middleware function
 */
export function handleCORS(req, res, next) {
    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    res.header('Content-Type', 'application/json');

    if (req.method === 'OPTIONS') {
        return res.status(200).end();
    }

    next();
}
