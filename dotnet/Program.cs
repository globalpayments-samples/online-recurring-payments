using GlobalPayments.Api;
using GlobalPayments.Api.Entities;
using GlobalPayments.Api.Entities.Enums;
using GlobalPayments.Api.PaymentMethods;
using GlobalPayments.Api.Services;
using dotenv.net;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace RecurringPayments;

public class Program
{
    public static void Main(string[] args)
    {
        DotEnv.Load();

        var builder = WebApplication.CreateBuilder(args);

        var app = builder.Build();

        app.UseDefaultFiles();
        app.UseStaticFiles();

        ConfigureEndpoints(app);

        var port = System.Environment.GetEnvironmentVariable("PORT") ?? "8000";
        app.Urls.Add($"http://0.0.0.0:{port}");

        Console.WriteLine($"✅ Server running at http://localhost:{port}");
        Console.WriteLine($"Environment: {System.Environment.GetEnvironmentVariable("GP_API_ENVIRONMENT") ?? "sandbox"}");

        app.Run();
    }

    private static string SanitizePostalCode(string? postalCode)
    {
        if (string.IsNullOrEmpty(postalCode)) return string.Empty;

        var sanitized = Regex.Replace(postalCode, "[^a-zA-Z0-9-]", "");
        return sanitized.Length > 10 ? sanitized[..10] : sanitized;
    }

    private static void ConfigureEndpoints(WebApplication app)
    {
        app.MapGet("/config", (HttpContext context) =>
        {
            try
            {
                var config = new GpApiConfig
                {
                    AppId = System.Environment.GetEnvironmentVariable("APP_ID"),
                    AppKey = System.Environment.GetEnvironmentVariable("APP_KEY"),
                    Environment = GlobalPayments.Api.Entities.Environment.TEST,
                    Channel = Channel.CardNotPresent,
                    Country = "US",
                    Permissions = new[] { "PMT_POST_Create_Single" }
                };

                var sessionToken = GpApiService.GenerateTransactionKey(config);

                if (sessionToken == null || string.IsNullOrEmpty(sessionToken.Token))
                {
                    throw new Exception("Failed to generate session token");
                }

                return Results.Ok(new
                {
                    success = true,
                    data = new
                    {
                        accessToken = sessionToken.Token
                    },
                    message = "Configuration retrieved successfully",
                    timestamp = DateTime.UtcNow.ToString("o")
                });
            }
            catch (Exception ex)
            {
                return Results.Json(new
                {
                    success = false,
                    message = "Error loading configuration: " + ex.Message,
                    error_code = "CONFIG_ERROR",
                    timestamp = DateTime.UtcNow.ToString("o")
                }, statusCode: 500);
            }
        });

        app.MapPost("/process-payment", async (HttpContext context) =>
        {
            try
            {
                var jsonBody = await JsonSerializer.DeserializeAsync<JsonElement>(context.Request.Body);

                if (!jsonBody.TryGetProperty("payment_token", out var tokenElement) ||
                    string.IsNullOrWhiteSpace(tokenElement.GetString()))
                {
                    throw new Exception("Missing required field: payment_token");
                }

                if (!jsonBody.TryGetProperty("amount", out var amountElement))
                {
                    throw new Exception("Missing required field: amount");
                }

                var paymentToken = tokenElement.GetString()!;
                var amount = amountElement.GetDecimal();
                var isRecurring = jsonBody.TryGetProperty("is_recurring", out var recurringEl) &&
                                 recurringEl.GetBoolean();

                if (amount <= 0)
                {
                    throw new Exception("Invalid amount");
                }

                if (isRecurring)
                {
                    var requiredFields = new[] { "frequency", "start_date", "first_name", "last_name", "email" };
                    foreach (var field in requiredFields)
                    {
                        if (!jsonBody.TryGetProperty(field, out var fieldEl) ||
                            string.IsNullOrWhiteSpace(fieldEl.GetString()))
                        {
                            throw new Exception($"Missing required field: {field}");
                        }
                    }

                    var config = new GpApiConfig
                    {
                        AppId = System.Environment.GetEnvironmentVariable("APP_ID"),
                        AppKey = System.Environment.GetEnvironmentVariable("APP_KEY"),
                        Environment = GlobalPayments.Api.Entities.Environment.TEST,
                        Channel = Channel.CardNotPresent,
                        Country = "US"
                    };

                    ServicesContainer.ConfigureService(config);

                    var card = new CreditCardData
                    {
                        Token = paymentToken
                    };

                    var customer = new Customer
                    {
                        FirstName = jsonBody.GetProperty("first_name").GetString(),
                        LastName = jsonBody.GetProperty("last_name").GetString(),
                        Email = jsonBody.GetProperty("email").GetString(),
                        MobilePhone = jsonBody.TryGetProperty("phone", out var phoneEl) ?
                            phoneEl.GetString() : ""
                    };

                    var address = new Address
                    {
                        StreetAddress1 = jsonBody.TryGetProperty("street_address", out var streetEl) ?
                            streetEl.GetString() : "",
                        City = jsonBody.TryGetProperty("city", out var cityEl) ?
                            cityEl.GetString() : "",
                        State = jsonBody.TryGetProperty("state", out var stateEl) ?
                            stateEl.GetString() : "",
                        PostalCode = SanitizePostalCode(
                            jsonBody.TryGetProperty("billing_zip", out var zipEl) ?
                            zipEl.GetString() : ""),
                        CountryCode = "US"
                    };

                    var storedCredential = new StoredCredential
                    {
                        Initiator = StoredCredentialInitiator.CardHolder,
                        Type = StoredCredentialType.Recurring,
                        Sequence = StoredCredentialSequence.First
                    };

                    var currency = jsonBody.TryGetProperty("currency", out var currEl) ?
                        currEl.GetString() : "USD";

                    var response = card.Charge(amount)
                        .WithCurrency(currency)
                        .WithAddress(address)
                        .WithCustomerData(customer)
                        .WithStoredCredential(storedCredential)
                        .Execute();

                    if (response.ResponseCode == "SUCCESS" &&
                        response.ResponseMessage == "CAPTURED")
                    {
                        var frequency = jsonBody.GetProperty("frequency").GetString()?.ToLower() ?? "monthly";
                        var frequencyDisplay = frequency switch
                        {
                            "weekly" => "Weekly",
                            "biweekly" => "Bi-Weekly",
                            "monthly" => "Monthly",
                            "quarterly" => "Quarterly",
                            "yearly" or "annually" => "Yearly",
                            _ => "Monthly"
                        };

                        return Results.Ok(new
                        {
                            success = true,
                            data = new
                            {
                                transaction_id = response.TransactionId ?? $"txn_{Guid.NewGuid():N}",
                                payment_method_id = response.CardBrandTransactionId ?? paymentToken,
                                customer_id = $"CUS_{Guid.NewGuid():N}",
                                amount = amount.ToString("F2"),
                                currency = currency,
                                frequency = frequencyDisplay,
                                start_date = jsonBody.GetProperty("start_date").GetString(),
                                status = "active",
                                response_code = response.ResponseCode,
                                message = "Initial payment successful. Recurring payment method stored.",
                                timestamp = DateTime.UtcNow.ToString("o"),
                                gateway_response = new
                                {
                                    auth_code = response.AuthorizationCode ?? "",
                                    reference_number = response.ReferenceNumber ?? ""
                                }
                            },
                            message = "Recurring payment schedule created successfully",
                            timestamp = DateTime.UtcNow.ToString("o")
                        });
                    }
                    else
                    {
                        throw new Exception($"Payment failed: {response.ResponseMessage ?? "Unknown error"}");
                    }
                }
                else
                {
                    var config = new GpApiConfig
                    {
                        AppId = System.Environment.GetEnvironmentVariable("APP_ID"),
                        AppKey = System.Environment.GetEnvironmentVariable("APP_KEY"),
                        Environment = GlobalPayments.Api.Entities.Environment.TEST,
                        Channel = Channel.CardNotPresent,
                        Country = "US"
                    };

                    ServicesContainer.ConfigureService(config);

                    var card = new CreditCardData
                    {
                        Token = paymentToken
                    };

                    var address = new Address
                    {
                        PostalCode = SanitizePostalCode(
                            jsonBody.TryGetProperty("billing_zip", out var zipEl) ?
                            zipEl.GetString() : "")
                    };

                    var currency = jsonBody.TryGetProperty("currency", out var currEl) ?
                        currEl.GetString() : "USD";

                    var response = card.Charge(amount)
                        .WithCurrency(currency)
                        .WithAddress(address)
                        .Execute();

                    if (response.ResponseCode == "SUCCESS" &&
                        response.ResponseMessage == TransactionStatus.Captured.ToString())
                    {
                        return Results.Ok(new
                        {
                            success = true,
                            data = new
                            {
                                transaction_id = response.TransactionId ?? $"txn_{Guid.NewGuid():N}",
                                amount = amount.ToString("F2"),
                                currency = currency,
                                status = "approved",
                                response_code = response.ResponseCode,
                                response_message = response.ResponseMessage ?? "Approved",
                                timestamp = DateTime.UtcNow.ToString("o"),
                                gateway_response = new
                                {
                                    auth_code = response.AuthorizationCode ?? "",
                                    reference_number = response.ReferenceNumber ?? ""
                                }
                            },
                            message = "Payment processed successfully",
                            timestamp = DateTime.UtcNow.ToString("o")
                        });
                    }
                    else
                    {
                        throw new Exception($"Payment failed: {response.ResponseMessage ?? "Unknown error"}");
                    }
                }
            }
            catch (ApiException ex)
            {
                return Results.Json(new
                {
                    success = false,
                    message = "Payment processing failed: " + ex.Message,
                    error_code = "API_ERROR",
                    timestamp = DateTime.UtcNow.ToString("o")
                }, statusCode: 400);
            }
            catch (Exception ex)
            {
                return Results.Json(new
                {
                    success = false,
                    message = "Server error: " + ex.Message,
                    error_code = "SERVER_ERROR",
                    timestamp = DateTime.UtcNow.ToString("o")
                }, statusCode: 500);
            }
        });
    }
}
