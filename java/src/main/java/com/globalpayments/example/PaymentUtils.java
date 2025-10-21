package com.globalpayments.example;

import com.global.api.ServicesContainer;
import com.global.api.entities.Address;
import com.global.api.entities.Customer;
import com.global.api.entities.Transaction;
import com.global.api.entities.enums.Channel;
import com.global.api.entities.enums.Environment;
import com.global.api.entities.enums.StoredCredentialInitiator;
import com.global.api.entities.enums.StoredCredentialSequence;
import com.global.api.entities.enums.StoredCredentialType;
import com.global.api.entities.enums.TransactionStatus;
import com.global.api.entities.StoredCredential;
import com.global.api.paymentMethods.CreditCardData;
import com.global.api.serviceConfigs.GpApiConfig;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public class PaymentUtils {

    private static final Dotenv dotenv = Dotenv.load();

    public static void configureSdk() throws Exception {
        GpApiConfig config = new GpApiConfig();
        config.setAppId(dotenv.get("APP_ID"));
        config.setAppKey(dotenv.get("APP_KEY"));
        config.setEnvironment(Environment.TEST);
        config.setChannel(Channel.CardNotPresent);
        config.setCountry("US");

        ServicesContainer.configureService(config);
    }

    public static String sanitizePostalCode(String postalCode) {
        if (postalCode == null || postalCode.isEmpty()) {
            return "";
        }
        String sanitized = postalCode.replaceAll("[^a-zA-Z0-9-]", "");
        return sanitized.length() > 10 ? sanitized.substring(0, 10) : sanitized;
    }

    public static JSONObject createRecurringSchedule(
            String token,
            BigDecimal amount,
            String currency,
            Map<String, String> scheduleData,
            Map<String, String> customerData
    ) throws Exception {

        CreditCardData card = new CreditCardData();
        card.setToken(token);

        Customer customer = new Customer();
        customer.setFirstName(customerData.getOrDefault("first_name", ""));
        customer.setLastName(customerData.getOrDefault("last_name", ""));
        customer.setEmail(customerData.getOrDefault("email", ""));
        // Note: SDK v14.2.20 does not support setPhoneNumber on Customer object
        // customer.setPhoneNumber(customerData.getOrDefault("phone", ""));

        Address address = new Address();
        address.setStreetAddress1(customerData.getOrDefault("street_address", ""));
        address.setCity(customerData.getOrDefault("city", ""));
        address.setState(customerData.getOrDefault("state", ""));
        address.setPostalCode(sanitizePostalCode(customerData.getOrDefault("billing_zip", "")));
        address.setCountryCode("US");

        StoredCredential storedCredential = new StoredCredential();
        storedCredential.setInitiator(StoredCredentialInitiator.CardHolder);
        storedCredential.setType(StoredCredentialType.Recurring);
        storedCredential.setSequence(StoredCredentialSequence.First);

        Transaction response = card.charge(amount)
                .withCurrency(currency)
                .withAddress(address)
                .withCustomerData(customer)
                .withStoredCredential(storedCredential)
                .execute();

        if ("SUCCESS".equals(response.getResponseCode()) &&
            TransactionStatus.Captured.getValue().equals(response.getResponseMessage())) {

            String frequency = scheduleData.getOrDefault("frequency", "monthly").toLowerCase();
            String frequencyDisplay = switch(frequency) {
                case "weekly" -> "Weekly";
                case "biweekly" -> "Bi-Weekly";
                case "monthly" -> "Monthly";
                case "quarterly" -> "Quarterly";
                case "yearly", "annually" -> "Yearly";
                default -> "Monthly";
            };

            JSONObject data = new JSONObject();
            data.put("transaction_id", response.getTransactionId() != null ?
                    response.getTransactionId() : "txn_" + UUID.randomUUID().toString().replace("-", ""));
            data.put("payment_method_id", response.getCardBrandTransactionId() != null ?
                    response.getCardBrandTransactionId() : token);
            data.put("customer_id", "CUS_" + UUID.randomUUID().toString().replace("-", ""));
            data.put("amount", amount.toString());
            data.put("currency", currency);
            data.put("frequency", frequencyDisplay);
            data.put("start_date", scheduleData.getOrDefault("start_date",
                    java.time.LocalDate.now().toString()));
            data.put("status", "active");
            data.put("response_code", response.getResponseCode());
            data.put("message", "Initial payment successful. Recurring payment method stored.");
            data.put("timestamp", java.time.Instant.now().toString());

            JSONObject gatewayResponse = new JSONObject();
            gatewayResponse.put("auth_code", response.getAuthorizationCode() != null ?
                    response.getAuthorizationCode() : "");
            gatewayResponse.put("reference_number", response.getReferenceNumber() != null ?
                    response.getReferenceNumber() : "");
            data.put("gateway_response", gatewayResponse);

            return data;
        } else {
            throw new Exception("Payment failed: " + (response.getResponseMessage() != null ?
                    response.getResponseMessage() : "Unknown error"));
        }
    }

    public static JSONObject processPaymentWithToken(
            String token,
            BigDecimal amount,
            String currency,
            Map<String, String> billingData
    ) throws Exception {

        CreditCardData card = new CreditCardData();
        card.setToken(token);

        Address address = new Address();
        address.setPostalCode(sanitizePostalCode(billingData.getOrDefault("billing_zip", "")));

        Transaction response = card.charge(amount)
                .withCurrency(currency)
                .withAddress(address)
                .execute();

        if ("SUCCESS".equals(response.getResponseCode()) &&
            TransactionStatus.Captured.getValue().equals(response.getResponseMessage())) {

            JSONObject data = new JSONObject();
            data.put("transaction_id", response.getTransactionId() != null ?
                    response.getTransactionId() : "txn_" + UUID.randomUUID().toString().replace("-", ""));
            data.put("amount", amount.toString());
            data.put("currency", currency);
            data.put("status", "approved");
            data.put("response_code", response.getResponseCode());
            data.put("response_message", response.getResponseMessage() != null ?
                    response.getResponseMessage() : "Approved");
            data.put("timestamp", java.time.Instant.now().toString());

            JSONObject gatewayResponse = new JSONObject();
            gatewayResponse.put("auth_code", response.getAuthorizationCode() != null ?
                    response.getAuthorizationCode() : "");
            gatewayResponse.put("reference_number", response.getReferenceNumber() != null ?
                    response.getReferenceNumber() : "");
            data.put("gateway_response", gatewayResponse);

            return data;
        } else {
            throw new Exception("Payment failed: " + (response.getResponseMessage() != null ?
                    response.getResponseMessage() : "Unknown error"));
        }
    }

    public static void sendSuccessResponse(HttpServletResponse response, JSONObject data, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");

        JSONObject successResponse = new JSONObject();
        successResponse.put("success", true);
        successResponse.put("data", data);
        successResponse.put("message", message);
        successResponse.put("timestamp", java.time.Instant.now().toString());

        response.getWriter().write(successResponse.toString());
    }

    public static void sendErrorResponse(HttpServletResponse response, int statusCode,
            String message, String errorCode) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");

        JSONObject errorResponse = new JSONObject();
        errorResponse.put("success", false);
        errorResponse.put("message", message);
        errorResponse.put("timestamp", java.time.Instant.now().toString());

        if (errorCode != null) {
            errorResponse.put("error_code", errorCode);
        }

        response.getWriter().write(errorResponse.toString());
    }
}
