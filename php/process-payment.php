<?php

declare(strict_types=1);

/**
 * Recurring Payment Processing Script - GP API
 *
 * This script demonstrates recurring payment schedule creation using the Global Payments GP API SDK.
 * It handles tokenized card data and creates recurring payment schedules with customer information.
 *
 * PHP version 7.4 or higher
 *
 * @category  Payment_Processing
 * @package   GlobalPayments_Sample
 * @author    Global Payments
 * @license   MIT License
 * @link      https://github.com/globalpayments
 */

require_once 'vendor/autoload.php';
require_once 'PaymentUtils.php';

use GlobalPayments\Api\Entities\Exceptions\ApiException;

ini_set('display_errors', '0');

// Initialize SDK configuration
PaymentUtils::configureSdk();
PaymentUtils::handleCORS();

try {
    // Parse JSON input
    $inputData = PaymentUtils::parseJsonInput();

    // Determine if this is a one-time payment or recurring schedule creation
    $isRecurring = isset($inputData['is_recurring']) && $inputData['is_recurring'] === true;

    // Validate payment token
    if (!isset($inputData['payment_token']) || empty(trim($inputData['payment_token']))) {
        throw new ApiException("Missing required field: payment_token");
    }

    // Parse and validate amount
    if (!isset($inputData['amount'])) {
        throw new ApiException("Missing required field: amount");
    }

    $amount = floatval($inputData['amount']);
    if ($amount <= 0) {
        throw new ApiException('Invalid amount');
    }

    if ($isRecurring) {
        // Validate recurring-specific fields
        $recurringFields = ['frequency', 'start_date'];
        foreach ($recurringFields as $field) {
            if (!isset($inputData[$field]) || empty(trim($inputData[$field]))) {
                throw new ApiException("Missing required recurring field: $field");
            }
        }

        // Validate customer data
        $customerFields = ['first_name', 'last_name', 'email'];
        foreach ($customerFields as $field) {
            if (!isset($inputData[$field]) || empty(trim($inputData[$field]))) {
                throw new ApiException("Missing required customer field: $field");
            }
        }

        // Prepare schedule data
        $scheduleData = [
            'frequency' => $inputData['frequency'],
            'start_date' => $inputData['start_date'],
            'end_date' => $inputData['end_date'] ?? null,
            'number_of_payments' => $inputData['number_of_payments'] ?? null,
            'schedule_name' => $inputData['schedule_name'] ?? 'Recurring Payment'
        ];

        // Prepare customer data
        $customerData = [
            'first_name' => $inputData['first_name'],
            'last_name' => $inputData['last_name'],
            'email' => $inputData['email'],
            'phone' => $inputData['phone'] ?? '',
            'street_address' => $inputData['street_address'] ?? '',
            'city' => $inputData['city'] ?? '',
            'state' => $inputData['state'] ?? '',
            'billing_zip' => $inputData['billing_zip'] ?? ''
        ];

        // Create recurring schedule
        $result = PaymentUtils::createRecurringSchedule(
            $inputData['payment_token'],
            $amount,
            $inputData['currency'] ?? 'USD',
            $scheduleData,
            $customerData
        );

        PaymentUtils::sendSuccessResponse($result, 'Recurring payment schedule created successfully');
    } else {
        // Process one-time payment
        $result = PaymentUtils::processPaymentWithToken(
            $inputData['payment_token'],
            $amount,
            $inputData['currency'] ?? 'USD',
            $inputData
        );

        PaymentUtils::sendSuccessResponse($result, 'Payment processed successfully');
    }

} catch (ApiException $e) {
    // Handle payment processing errors
    PaymentUtils::sendErrorResponse(400, 'Payment processing failed: ' . $e->getMessage(), 'API_ERROR');
} catch (Exception $e) {
    // Handle general errors
    PaymentUtils::sendErrorResponse(500, 'Server error: ' . $e->getMessage(), 'SERVER_ERROR');
}
