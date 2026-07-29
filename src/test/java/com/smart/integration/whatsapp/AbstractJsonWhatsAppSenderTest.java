package com.smart.integration.whatsapp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** يغطي السلوك المشترك بين GenericSender/MetaCloudSender/WaSenderSender عن طريق GenericSender. */
class AbstractJsonWhatsAppSenderTest {

    @Test
    void send_sendsBearerTokenAndReturnsSuccess_onHttp200() throws Exception {
        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();

        try (MockHttpServer mock = MockHttpServer.start((exchange) -> {
            capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            MockHttpServer.reply(exchange, 200, "{\"status\":\"ok\"}");
        })) {
            WhatsAppSender sender = new GenericSender();
            WhatsAppSender.SendResult result = sender.send(mock.baseUrl(), "secret-token", "+201234567890", "hi");

            assertTrue(result.success());
            assertEquals(200, result.statusCode());
            assertEquals("Bearer secret-token", capturedAuthHeader.get());
        }
    }

    @Test
    void send_returnsFailure_onNon2xxResponse() throws Exception {
        try (MockHttpServer mock = MockHttpServer.start((exchange) ->
                MockHttpServer.reply(exchange, 401, "{\"error\":\"unauthorized\"}"))) {
            WhatsAppSender sender = new GenericSender();
            WhatsAppSender.SendResult result = sender.send(mock.baseUrl(), "bad-token", "+201234567890", "hi");

            assertFalse(result.success());
            assertEquals(401, result.statusCode());
        }
    }

    @Test
    void send_returnsErrorResult_whenServerUnreachable() {
        WhatsAppSender sender = new GenericSender();
        WhatsAppSender.SendResult result = sender.send("http://127.0.0.1:1", "token", "+201234567890", "hi");

        assertFalse(result.success());
        assertEquals(-1, result.statusCode());
    }
}
