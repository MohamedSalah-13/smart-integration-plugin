package com.smart.integration.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * UltraMsg بيستخدم form-encoded body بدون Authorization header (التوكن بيتبعت
 * كـ form field)، فمختلف كفاية عن باقي الـ senders إنه يفضل مستقل بدل الوراثة
 * من AbstractJsonWhatsAppSender.
 */
public class UltraMsgSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(UltraMsgSender.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public SendResult send(String apiUrl, String apiToken, String phone, String message) {
        String form = "token=" + URLEncoder.encode(apiToken, StandardCharsets.UTF_8)
                + "&to=" + URLEncoder.encode(phone, StandardCharsets.UTF_8)
                + "&body=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        log.debug("Sending WhatsApp message via UltraMsg");
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            String body = response.body() == null ? "" : response.body();

            if (code >= 200 && code < 300) {
                try {
                    JsonNode root = JSON.readTree(body);
                    if (root.has("error")) {
                        log.warn("UltraMsg send failed: {}", root.get("error").asText());
                        return SendResult.fail(code, "UltraMsg error: " + root.get("error").asText());
                    }
                    return SendResult.ok(code);
                } catch (Exception parseEx) {
                    log.warn("UltraMsg returned an unexpected response body");
                    return SendResult.fail(code, "Unexpected response: " + body);
                }
            }
            log.warn("UltraMsg send failed with HTTP {}", code);
            return SendResult.fail(code, "HTTP " + code + " → " + body);
        } catch (Exception e) {
            log.warn("UltraMsg send failed: {}", e.toString());
            return SendResult.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
