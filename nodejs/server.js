/**
 * Recurring Payment Processing Server - GP API
 *
 * This Express application demonstrates recurring payment schedule creation using the
 * Global Payments GP API SDK. It handles tokenized card data and creates recurring
 * payment schedules with customer information.
 *
 * @module server
 */

import express from 'express';
import * as dotenv from 'dotenv';
import {
    ServicesContainer,
    GpApiConfig,
    Environment,
    Channel,
    GpApiService
} from 'globalpayments-api';
import {
    configureSdk,
    createRecurringSchedule,
    processPaymentWithToken,
    sendSuccessResponse,
    sendErrorResponse,
    handleCORS
} from './paymentUtils.js';

// Load environment variables from .env file
dotenv.config();

// Initialize Express application with necessary middleware
const app = express();
const port = process.env.PORT || 8000;

// Middleware setup
app.use(express.static('.')); // Serve static files
app.use(express.urlencoded({ extended: true })); // Parse form data
app.use(express.json()); // Parse JSON requests
app.use(handleCORS); // Handle CORS

// Disable display errors
process.env.NODE_ENV = process.env.NODE_ENV || 'production';

/**
 * Configuration endpoint - provides GP API access token for client-side use
 *
 * This endpoint generates a session token specifically for client-side single-use tokenization.
 * The token is scoped with PMT_POST_Create_Single permission.
 *
 * @route GET /config
 */
app.get('/config', async (req, res) => {
    try {
        // Configure GP API to generate access token for client-side use
        const config = new GpApiConfig();
        config.appId = process.env.APP_ID || '';
        config.appKey = process.env.APP_KEY || '';
        config.environment = Environment.TEST;
        config.channel = Channel.CardNotPresent;
        config.country = 'US';

        // Set permissions specifically for client-side single-use tokenization
        config.permissions = ['PMT_POST_Create_Single'];

        // Configure service to establish connection
        ServicesContainer.configureService(config);

        // Generate session token for client-side tokenization
        const sessionToken = await GpApiService.generateTransactionKey(config);

        if (sessionToken && sessionToken.accessToken) {
            const accessToken = sessionToken.accessToken;
            console.log('Session token generated successfully:', accessToken.substring(0, 8) + '...');

            sendSuccessResponse(res, {
                accessToken: accessToken
            }, 'Configuration retrieved successfully');
        } else {
            throw new Error('Invalid session token response format');
        }
    } catch (error) {
        console.error('Configuration error:', error.message);
        sendErrorResponse(res, 500, `Error loading configuration: ${error.message}`, 'CONFIG_ERROR');
    }
});

/**
 * Payment processing endpoint
 *
 * Handles both one-time payments and recurring payment schedule creation.
 * The endpoint determines the payment type based on the is_recurring flag.
 *
 * @route POST /process-payment
 */
app.post('/process-payment', async (req, res) => {
    try {
        // Initialize SDK configuration
        configureSdk();

        const inputData = req.body;

        // Determine if this is a one-time payment or recurring schedule creation
        const isRecurring = inputData.is_recurring === true || inputData.is_recurring === 'true';

        // Validate payment token
        if (!inputData.payment_token || !inputData.payment_token.trim()) {
            return sendErrorResponse(res, 400, 'Missing required field: payment_token', 'VALIDATION_ERROR');
        }

        // Parse and validate amount
        if (!inputData.amount) {
            return sendErrorResponse(res, 400, 'Missing required field: amount', 'VALIDATION_ERROR');
        }

        const amount = parseFloat(inputData.amount);
        if (isNaN(amount) || amount <= 0) {
            return sendErrorResponse(res, 400, 'Invalid amount', 'VALIDATION_ERROR');
        }

        if (isRecurring) {
            // Validate recurring-specific fields
            const recurringFields = ['frequency', 'start_date'];
            for (const field of recurringFields) {
                if (!inputData[field] || !inputData[field].trim()) {
                    return sendErrorResponse(res, 400, `Missing required recurring field: ${field}`, 'VALIDATION_ERROR');
                }
            }

            // Validate customer data
            const customerFields = ['first_name', 'last_name', 'email'];
            for (const field of customerFields) {
                if (!inputData[field] || !inputData[field].trim()) {
                    return sendErrorResponse(res, 400, `Missing required customer field: ${field}`, 'VALIDATION_ERROR');
                }
            }

            // Prepare schedule data
            const scheduleData = {
                frequency: inputData.frequency,
                start_date: inputData.start_date,
                end_date: inputData.end_date || null,
                number_of_payments: inputData.number_of_payments || null,
                schedule_name: inputData.schedule_name || 'Recurring Payment'
            };

            // Prepare customer data
            const customerData = {
                first_name: inputData.first_name,
                last_name: inputData.last_name,
                email: inputData.email,
                phone: inputData.phone || '',
                street_address: inputData.street_address || '',
                city: inputData.city || '',
                state: inputData.state || '',
                billing_zip: inputData.billing_zip || ''
            };

            // Create recurring schedule
            const result = await createRecurringSchedule(
                inputData.payment_token,
                amount,
                inputData.currency || 'USD',
                scheduleData,
                customerData
            );

            sendSuccessResponse(res, result, 'Recurring payment schedule created successfully');
        } else {
            // Process one-time payment
            const result = await processPaymentWithToken(
                inputData.payment_token,
                amount,
                inputData.currency || 'USD',
                inputData
            );

            sendSuccessResponse(res, result, 'Payment processed successfully');
        }

    } catch (error) {
        console.error('Payment processing error:', error.message);

        // Determine if this is an API error or general error
        if (error.name === 'ApiError' || error.message.includes('API')) {
            sendErrorResponse(res, 400, `Payment processing failed: ${error.message}`, 'API_ERROR');
        } else {
            sendErrorResponse(res, 500, `Server error: ${error.message}`, 'SERVER_ERROR');
        }
    }
});

/**
 * Health check endpoint
 *
 * @route GET /health
 */
app.get('/health', (req, res) => {
    sendSuccessResponse(res, {
        status: 'healthy',
        service: 'recurring-payments-api',
        timestamp: new Date().toISOString()
    }, 'Service is healthy');
});

// Start the server
app.listen(port, '0.0.0.0', () => {
    console.log(`Recurring Payments Server running at http://localhost:${port}`);
    console.log(`Environment: ${process.env.GP_API_ENVIRONMENT || 'sandbox'}`);
    console.log('Ready to process recurring and one-time payments via GP API');
});
