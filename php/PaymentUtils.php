<?php

declare(strict_types=1);

/**
 * Payment Utilities Class - GP API (Recurring Payments)
 *
 * Provides utility functions for recurring payment processing using Global Payments GP API.
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

use Dotenv\Dotenv;
use GlobalPayments\Api\Entities\Address;
use GlobalPayments\Api\Entities\Customer;
use GlobalPayments\Api\Entities\Enums\Channel;
use GlobalPayments\Api\Entities\Enums\Environment;
use GlobalPayments\Api\Entities\Enums\RecurringSequence;
use GlobalPayments\Api\Entities\Enums\RecurringType;
use GlobalPayments\Api\Entities\Enums\StoredCredentialInitiator;
use GlobalPayments\Api\Entities\Enums\StoredCredentialSequence;
use GlobalPayments\Api\Entities\Enums\StoredCredentialType;
use GlobalPayments\Api\Entities\Enums\TransactionStatus;
use GlobalPayments\Api\Entities\Schedule;
use GlobalPayments\Api\Entities\StoredCredential;
use GlobalPayments\Api\PaymentMethods\CreditCardData;
use GlobalPayments\Api\ServiceConfigs\Gateways\GpApiConfig;
use GlobalPayments\Api\ServicesContainer;

class PaymentUtils
{
    /**
     * Configure the Global Payments SDK (GP API)
     */
    public static function configureSdk(): void
    {
        $dotenv = Dotenv::createImmutable(__DIR__);
        $dotenv->load();

        $config = new GpApiConfig();
        $config->appId = $_ENV['APP_ID'] ?? '';
        $config->appKey = $_ENV['APP_KEY'] ?? '';
        $config->environment = Environment::TEST;
        $config->channel = Channel::CardNotPresent;
        $config->country = 'US';

        ServicesContainer::configureService($config);
    }

    /**
     * Sanitize postal code by removing invalid characters
     */
    public static function sanitizePostalCode(?string $postalCode): string
    {
        if ($postalCode === null) {
            return '';
        }

        $sanitized = preg_replace('/[^a-zA-Z0-9-]/', '', $postalCode);
        return substr($sanitized, 0, 10);
    }

    /**
     * Create recurring payment setup with tokenized card
     *
     * This creates a stored payment method that can be used for future recurring charges.
     * For a full recurring schedule implementation, additional GP API configuration is required.
     */
    public static function createRecurringSchedule(
        string $token,
        float $amount,
        string $currency,
        array $scheduleData,
        array $customerData
    ): array {
        try {
            // Create card from token
            $card = new CreditCardData();
            $card->token = $token;

            // Create customer data
            $customer = new Customer();
            $customer->firstName = $customerData['first_name'] ?? '';
            $customer->lastName = $customerData['last_name'] ?? '';
            $customer->email = $customerData['email'] ?? '';
            $customer->phoneNumber = $customerData['phone'] ?? '';

            // Create address for the card
            $address = new Address();
            $address->streetAddress1 = $customerData['street_address'] ?? '';
            $address->city = $customerData['city'] ?? '';
            $address->state = $customerData['state'] ?? '';
            $address->postalCode = self::sanitizePostalCode($customerData['billing_zip'] ?? '');
            $address->countryCode = 'US';

            // Create stored credential for recurring payment
            $storedCredential = new StoredCredential();
            $storedCredential->initiator = StoredCredentialInitiator::CARDHOLDER;
            $storedCredential->type = StoredCredentialType::RECURRING;
            $storedCredential->sequence = StoredCredentialSequence::FIRST;

            // For recurring payments, we'll process an initial charge to validate the payment method
            // and store the card for future use
            $response = $card->charge($amount)
                ->withCurrency($currency)
                ->withAddress($address)
                ->withCustomerData($customer)
                ->withStoredCredential($storedCredential)
                ->execute();

            if ($response->responseCode === 'SUCCESS' &&
                $response->responseMessage === TransactionStatus::CAPTURED) {

                // Calculate frequency details
                $frequency = strtolower($scheduleData['frequency'] ?? 'monthly');
                $frequencyDisplay = match($frequency) {
                    'weekly' => 'Weekly',
                    'biweekly' => 'Bi-Weekly',
                    'monthly' => 'Monthly',
                    'quarterly' => 'Quarterly',
                    'yearly' => 'Yearly',
                    'annually' => 'Yearly',
                    default => 'Monthly'
                };

                return [
                    'transaction_id' => $response->transactionId ?? 'txn_' . uniqid(),
                    'payment_method_id' => $response->cardBrandTransactionId ?? $token,
                    'customer_id' => 'CUS_' . uniqid(),
                    'amount' => $amount,
                    'currency' => $currency,
                    'frequency' => $frequencyDisplay,
                    'start_date' => $scheduleData['start_date'] ?? date('Y-m-d'),
                    'status' => 'active',
                    'response_code' => $response->responseCode,
                    'message' => 'Initial payment successful. Recurring payment method stored.',
                    'timestamp' => date('c'),
                    'gateway_response' => [
                        'auth_code' => $response->authorizationCode ?? '',
                        'reference_number' => $response->referenceNumber ?? ''
                    ]
                ];
            } else {
                throw new \Exception('Payment failed: ' . ($response->responseMessage ?? 'Unknown error'));
            }
        } catch (\Exception $e) {
            error_log('Recurring payment setup error: ' . $e->getMessage());
            throw $e;
        }
    }

    /**
     * Process one-time payment with tokenized card
     */
    public static function processPaymentWithToken(
        string $token,
        float $amount,
        string $currency,
        array $billingData
    ): array {
        try {
            $card = new CreditCardData();
            $card->token = $token;

            $address = new Address();
            $address->postalCode = self::sanitizePostalCode($billingData['billing_zip'] ?? '');

            $response = $card->charge($amount)
                ->withCurrency($currency)
                ->withAddress($address)
                ->execute();

            if ($response->responseCode === 'SUCCESS' &&
                $response->responseMessage === TransactionStatus::CAPTURED) {

                return [
                    'transaction_id' => $response->transactionId ?? 'txn_' . uniqid(),
                    'amount' => $amount,
                    'currency' => $currency,
                    'status' => 'approved',
                    'response_code' => $response->responseCode,
                    'response_message' => $response->responseMessage ?? 'Approved',
                    'timestamp' => date('c'),
                    'gateway_response' => [
                        'auth_code' => $response->authorizationCode ?? '',
                        'reference_number' => $response->referenceNumber ?? ''
                    ]
                ];
            } else {
                throw new \Exception('Payment failed: ' . ($response->responseMessage ?? 'Unknown error'));
            }
        } catch (\Exception $e) {
            error_log('Payment processing error: ' . $e->getMessage());
            throw $e;
        }
    }

    /**
     * Send success response
     */
    public static function sendSuccessResponse($data, string $message = 'Operation completed successfully'): void
    {
        http_response_code(200);

        $response = [
            'success' => true,
            'data' => $data,
            'message' => $message,
            'timestamp' => date('c')
        ];

        echo json_encode($response);
        exit();
    }

    /**
     * Send error response
     */
    public static function sendErrorResponse(int $statusCode, string $message, string $errorCode = null): void
    {
        http_response_code($statusCode);

        $response = [
            'success' => false,
            'message' => $message,
            'timestamp' => date('c')
        ];

        if ($errorCode) {
            $response['error_code'] = $errorCode;
        }

        echo json_encode($response);
        exit();
    }

    /**
     * Handle CORS headers
     */
    public static function handleCORS(): void
    {
        header('Access-Control-Allow-Origin: *');
        header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
        header('Access-Control-Allow-Headers: Content-Type, Authorization');
        header('Content-Type: application/json');

        if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
            http_response_code(200);
            exit();
        }
    }

    /**
     * Parse JSON input for POST requests
     */
    public static function parseJsonInput(): array
    {
        $inputData = [];
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            $rawInput = file_get_contents('php://input');
            if ($rawInput) {
                $inputData = json_decode($rawInput, true) ?? [];
            }
            $inputData = array_merge($_POST, $inputData);
        }
        return $inputData;
    }
}
