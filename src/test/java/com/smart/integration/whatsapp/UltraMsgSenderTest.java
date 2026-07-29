package com.smart.integration.whatsapp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UltraMsgSenderTest {

    @Test
    void send_encodesFormFieldsAndReturnsSuccess() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedContentType = new AtomicReference<>();

        try (MockHttpServer mock = MockHttpServer.start((exchange) -> {
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            capturedBody.set(MockHttpServer.readBody(exchange));
            MockHttpServer.reply(exchange, 200, "{\"sent\":true}");
        })) {
            WhatsAppSender sender = new UltraMsgSender();
            WhatsAppSender.SendResult result = sender.send(mock.baseUrl(), "my token", "+20 123", "hi there");

            assertTrue(result.success());
            assertEquals("application/x-www-form-urlencoded", capturedContentType.get());
            assertTrue(capturedBody.get().contains("token=my+token"));
            assertTrue(capturedBody.get().contains("to=%2B20+123"));
            assertTrue(capturedBody.get().contains("body=hi+there"));
        }
    }

    @Test
    void send_treatsHttp200WithErrorField_asFailure() throws Exception {
        try (MockHttpServer mock = MockHttpServer.start((exchange) ->
                MockHttpServer.reply(exchange, 200, "{\"error\":\"invalid instance id\"}"))) {
            WhatsAppSender sender = new UltraMsgSender();
            WhatsAppSender.SendResult result = sender.send(mock.baseUrl(), "token", "+201234567890", "hi");

            assertFalse(result.success());
            assertEquals(200, result.statusCode());
            assertTrue(result.errorMessage().contains("invalid instance id"));
        }
    }

    @Test
    void send_returnsFailure_onNon2xxHttpStatus() throws Exception {
        try (MockHttpServer mock = MockHttpServer.start((exchange) ->
                MockHttpServer.reply(exchange, 500, "internal error"))) {
            WhatsAppSender sender = new UltraMsgSender();
            WhatsAppSender.SendResult result = sender.send(mock.baseUrl(), "token", "+201234567890", "hi");

            assertFalse(result.success());
            assertEquals(500, result.statusCode());
        }
    }
}
