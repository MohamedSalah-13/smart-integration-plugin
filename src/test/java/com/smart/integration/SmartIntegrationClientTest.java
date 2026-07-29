package com.smart.integration;

import com.smart.integration.ai.AIProvider;
import com.smart.integration.whatsapp.WhatsAppProvider;
import com.smart.integration.whatsapp.WhatsAppSender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartIntegrationClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void withWhatsApp_rejectsBlankCredentials_whenProviderGiven() {
        SmartIntegrationClient.Builder builder = new SmartIntegrationClient.Builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder.withWhatsApp(WhatsAppProvider.GENERIC, "", "token"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.withWhatsApp(WhatsAppProvider.GENERIC, "https://example.com", null));
    }

    @Test
    void sendWhatsAppMessage_throwsIllegalState_whenWhatsAppNotConfigured() {
        SmartIntegrationClient client = new SmartIntegrationClient.Builder().build();
        assertThrows(IllegalStateException.class, () -> client.sendWhatsAppMessage("+201234567890", "hi"));
    }

    @Test
    void askAI_throwsIllegalState_whenAiNotConfigured() {
        SmartIntegrationClient client = new SmartIntegrationClient.Builder().build();
        assertThrows(IllegalStateException.class, () -> client.askAI("hello"));
    }

    @Test
    void sendWhatsAppMessage_roundTripsThroughConfiguredProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";

        SmartIntegrationClient client = new SmartIntegrationClient.Builder()
                .withWhatsApp(WhatsAppProvider.GENERIC, url, "token")
                .build();

        WhatsAppSender.SendResult result = client.sendWhatsAppMessage("+201234567890", "hi");

        assertTrue(result.success());
        assertEquals(200, result.statusCode());
    }

    @Test
    void withAI_doesNotRequireApiKey_forLocalProviderAtBuildTime() {
        // AIProvider.LOCAL (Ollama) بيشتغل من غير API key؛ التحقق بيحصل وقت الاستخدام مش وقت الـ build.
        SmartIntegrationClient client = new SmartIntegrationClient.Builder()
                .withAI(AIProvider.LOCAL, null)
                .build();
        assertDoesNotThrow(() -> client.clearAIChat());
    }
}
