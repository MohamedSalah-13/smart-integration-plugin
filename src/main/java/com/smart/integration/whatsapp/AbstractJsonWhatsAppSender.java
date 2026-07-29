package com.smart.integration.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * أساس مشترك للمزودين اللي بيبعتوا JSON عن طريق POST مع Bearer token
 * (UltraMsg مختلف عن دول لأنه form-encoded بدون Bearer، فبيفضل مستقل).
 * كل الفرق بين GenericSender/MetaCloudSender/WaSenderSender هو شكل الـ payload بس،
 * فده بيشيل التكرار اللي كان موجود في التلات كلاسات دول.
 */
public abstract class AbstractJsonWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(AbstractJsonWhatsAppSender.class);

    protected static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    protected abstract ObjectNode buildPayload(String phone, String message);

    @Override
    public SendResult send(String apiUrl, String apiToken, String phone, String message) {
        ObjectNode payload = buildPayload(phone, message);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        log.debug("Sending WhatsApp message via {}", getClass().getSimpleName());
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            SendResult result = mapResponse(response);
            if (!result.success()) {
                log.warn("WhatsApp send via {} failed: {}", getClass().getSimpleName(), result.errorMessage());
            }
            return result;
        } catch (Exception e) {
            log.warn("WhatsApp send via {} failed: {}", getClass().getSimpleName(), e.toString());
            return SendResult.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
