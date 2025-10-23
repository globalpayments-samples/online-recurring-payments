package com.globalpayments.example;

import com.global.api.entities.enums.Channel;
import com.global.api.entities.enums.Environment;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.serviceConfigs.GpApiConfig;
import com.global.api.services.GpApiService;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@WebServlet(urlPatterns = {"/config", "/process-payment"})
public class ProcessPaymentServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Dotenv dotenv = Dotenv.load();

    private String generateNonce() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String hashSecret(String nonce, String appKey) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[] hash = digest.digest((nonce + appKey).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (request.getServletPath().equals("/config")) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            try {
                String nonce = generateNonce();
                String secret = hashSecret(nonce, dotenv.get("APP_KEY"));

                JSONObject tokenRequest = new JSONObject();
                tokenRequest.put("app_id", dotenv.get("APP_ID"));
                tokenRequest.put("nonce", nonce);
                tokenRequest.put("secret", secret);
                tokenRequest.put("grant_type", "client_credentials");
                tokenRequest.put("seconds_to_expire", 600);
                tokenRequest.put("permissions", new String[]{"PMT_POST_Create_Single"});

                String apiEndpoint = "production".equals(dotenv.get("GP_API_ENVIRONMENT"))
                    ? "https://apis.globalpay.com/ucp/accesstoken"
                    : "https://apis.sandbox.globalpay.com/ucp/accesstoken";

                URL url = new URL(apiEndpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-GP-Version", "2021-03-22");
                conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = tokenRequest.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                java.io.InputStream inputStream = responseCode == 200 ?
                        conn.getInputStream() : conn.getErrorStream();

                String encoding = conn.getContentEncoding();
                if ("gzip".equalsIgnoreCase(encoding)) {
                    inputStream = new java.util.zip.GZIPInputStream(inputStream);
                }

                BufferedReader br = new BufferedReader(
                    new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8)
                );
                String responseBody = br.lines().collect(Collectors.joining());
                br.close();

                if (responseCode != 200) {
                    throw new Exception("Failed to generate access token: " + responseBody);
                }

                JSONObject tokenResponse = new JSONObject(responseBody);
                String token = tokenResponse.getString("token");
                int expiresIn = tokenResponse.optInt("seconds_to_expire", 600);

                JSONObject successResponse = new JSONObject();
                successResponse.put("success", true);

                JSONObject data = new JSONObject();
                data.put("accessToken", token);
                successResponse.put("data", data);

                successResponse.put("message", "Configuration retrieved successfully");
                successResponse.put("timestamp", java.time.Instant.now().toString());

                response.getWriter().write(successResponse.toString());

            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("success", false);
                errorResponse.put("message", "Error loading configuration: " + e.getMessage());
                errorResponse.put("error_code", "CONFIG_ERROR");
                errorResponse.put("timestamp", java.time.Instant.now().toString());
                response.getWriter().write(errorResponse.toString());
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            BufferedReader reader = request.getReader();
            String requestBody = reader.lines().collect(Collectors.joining());
            JSONObject jsonRequest = new JSONObject(requestBody);

            if (!jsonRequest.has("payment_token") ||
                jsonRequest.getString("payment_token").trim().isEmpty()) {
                throw new ApiException("Missing required field: payment_token");
            }

            if (!jsonRequest.has("amount")) {
                throw new ApiException("Missing required field: amount");
            }

            String paymentToken = jsonRequest.getString("payment_token");
            BigDecimal amount = jsonRequest.getBigDecimal("amount");
            boolean isRecurring = jsonRequest.optBoolean("is_recurring", false);

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException("Invalid amount");
            }

            if (isRecurring) {
                String[] requiredFields = {"frequency", "start_date", "first_name", "last_name", "email"};
                for (String field : requiredFields) {
                    if (!jsonRequest.has(field) || jsonRequest.getString(field).trim().isEmpty()) {
                        throw new ApiException("Missing required recurring field: " + field);
                    }
                }

                PaymentUtils.configureSdk();

                Map<String, String> scheduleData = new HashMap<>();
                scheduleData.put("frequency", jsonRequest.getString("frequency"));
                scheduleData.put("start_date", jsonRequest.getString("start_date"));
                scheduleData.put("end_date", jsonRequest.optString("end_date", null));
                scheduleData.put("schedule_name", jsonRequest.optString("schedule_name", "Recurring Payment"));

                Map<String, String> customerData = new HashMap<>();
                customerData.put("first_name", jsonRequest.getString("first_name"));
                customerData.put("last_name", jsonRequest.getString("last_name"));
                customerData.put("email", jsonRequest.getString("email"));
                customerData.put("phone", jsonRequest.optString("phone", ""));
                customerData.put("street_address", jsonRequest.optString("street_address", ""));
                customerData.put("city", jsonRequest.optString("city", ""));
                customerData.put("state", jsonRequest.optString("state", ""));
                customerData.put("billing_zip", jsonRequest.optString("billing_zip", ""));

                String currency = jsonRequest.optString("currency", "USD");

                JSONObject result = PaymentUtils.createRecurringSchedule(
                    paymentToken,
                    amount,
                    currency,
                    scheduleData,
                    customerData
                );

                PaymentUtils.sendSuccessResponse(response, result,
                    "Recurring payment schedule created successfully");

            } else {
                PaymentUtils.configureSdk();

                Map<String, String> billingData = new HashMap<>();
                billingData.put("billing_zip", jsonRequest.optString("billing_zip", ""));

                String currency = jsonRequest.optString("currency", "USD");

                JSONObject result = PaymentUtils.processPaymentWithToken(
                    paymentToken,
                    amount,
                    currency,
                    billingData
                );

                PaymentUtils.sendSuccessResponse(response, result, "Payment processed successfully");
            }

        } catch (ApiException e) {
            PaymentUtils.sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                "Payment processing failed: " + e.getMessage(), "API_ERROR");
        } catch (Exception e) {
            PaymentUtils.sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Server error: " + e.getMessage(), "SERVER_ERROR");
        }
    }
}
