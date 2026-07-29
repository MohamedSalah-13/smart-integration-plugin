package com.smart.integration.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIServiceTest {

    @Test
    void sendMessage_throwsWithoutHittingNetwork_whenApiKeyMissing() {
        AIService service = new AIService(AIProvider.OPENAI, null, "system prompt");
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage("hi"));
    }

    @Test
    void sendMessage_openAiFormat_returnsAssistantContent() throws Exception {
        try (MockHttpServer mock = MockHttpServer.start((exchange) -> {
            MockHttpServer.readBody(exchange);
            MockHttpServer.reply(exchange, 200,
                    "{\"choices\":[{\"message\":{\"content\":\"hello reply\"}}]}");
        })) {
            AIService service = new AIService(AIProvider.OPENAI, "key", "sys", "gpt-x", mock.baseUrl());
            assertEquals("hello reply", service.sendMessage("hi"));
        }
    }

    @Test
    void sendMessage_geminiFormat_sendsSystemInstructionSeparately() throws Exception {
        StringBuilder capturedBody = new StringBuilder();
        try (MockHttpServer mock = MockHttpServer.start((exchange) -> {
            capturedBody.append(MockHttpServer.readBody(exchange));
            MockHttpServer.reply(exchange, 200,
                    "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"gemini reply\"}]}}]}");
        })) {
            AIService service = new AIService(AIProvider.GOOGLE_GEMINI, "key", "you are helpful", "gemini-x", mock.baseUrl());
            assertEquals("gemini reply", service.sendMessage("hi"));
        }

        JsonObject request = JsonParser.parseString(capturedBody.toString()).getAsJsonObject();
        assertTrue(request.has("systemInstruction"), "system prompt must be sent as systemInstruction for Gemini");
        // "contents" لازم يحتوي رسالة المستخدم بس، مش الـ system
        assertEquals(1, request.getAsJsonArray("contents").size());
    }

    @Test
    void sendMessage_ollamaFormat_worksWithoutApiKey() throws Exception {
        try (MockHttpServer mock = MockHttpServer.start((exchange) -> {
            MockHttpServer.readBody(exchange);
            MockHttpServer.reply(exchange, 200, "{\"message\":{\"content\":\"local reply\"}}");
        })) {
            AIService service = new AIService(AIProvider.LOCAL, null, "sys", null, mock.baseUrl());
            assertEquals("local reply", service.sendMessage("hi"));
        }
    }

    @Test
    void sendMessage_wrapsHttpErrorInAIServiceException() throws Exception {
        try (MockHttpServer mock = MockHttpServer.start((exchange) ->
                MockHttpServer.reply(exchange, 500, "{\"error\":\"boom\"}"))) {
            AIService service = new AIService(AIProvider.OPENAI, "key", "sys", "gpt-x", mock.baseUrl());
            AIServiceException ex = assertThrows(AIServiceException.class, () -> service.sendMessage("hi"));
            assertTrue(ex.getMessage().contains("500"));
        }
    }

    /**
     * تغطية لباگ كان موجود: لو النداء فشل، رسالة الـ user كانت تفضل في الـ history من غير رد،
     * فالنداء اللي بعده كان بيبعت رسالتين "user" متتاليتين - Anthropic بيرفض ده (لازم alternation صارم).
     * هنا أول نداء بيفشل عمدًا، وتاني نداء بيتحقق إن الـ messages array بيبدأ بـ user وبيتبادل الأدوار صح.
     */
    @Test
    void sendMessage_removesOrphanedUserMessage_afterFailedAnthropicCall() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        try (MockHttpServer mock = MockHttpServer.start((exchange) -> {
            String body = MockHttpServer.readBody(exchange);
            if (callCount.incrementAndGet() == 1) {
                MockHttpServer.reply(exchange, 500, "{\"error\":\"simulated failure\"}");
                return;
            }

            JsonArray messages = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("messages");
            String previousRole = null;
            for (JsonElement el : messages) {
                String role = el.getAsJsonObject().get("role").getAsString();
                if (role.equals(previousRole)) {
                    MockHttpServer.reply(exchange, 400, "{\"error\":\"role alternation broken\"}");
                    return;
                }
                previousRole = role;
            }
            if (messages.size() == 0 || !"user".equals(messages.get(0).getAsJsonObject().get("role").getAsString())) {
                MockHttpServer.reply(exchange, 400, "{\"error\":\"must start with user\"}");
                return;
            }
            MockHttpServer.reply(exchange, 200, "{\"content\":[{\"text\":\"second reply\"}]}");
        })) {
            AIService service = new AIService(AIProvider.ANTHROPIC, "key", "sys", "claude-x", mock.baseUrl());

            assertThrows(AIServiceException.class, () -> service.sendMessage("session-1", "first message"));
            String reply = service.sendMessage("session-1", "second message");

            assertEquals("second reply", reply);
        }
    }

    /** تغطية للحد الأقصى لطول الـ history: بعد عدد كبير من الرسائل لازم مايكبرش من غير حدود. */
    @Test
    void sendMessage_trimsHistory_soRequestSizeStaysBounded() throws Exception {
        AtomicInteger lastMessageCount = new AtomicInteger();
        try (MockHttpServer mock = MockHttpServer.start((exchange) -> {
            String body = MockHttpServer.readBody(exchange);
            JsonArray messages = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("messages");
            lastMessageCount.set(messages.size());
            MockHttpServer.reply(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        })) {
            AIService service = new AIService(AIProvider.OPENAI, "key", "sys", "gpt-x", mock.baseUrl());
            for (int i = 0; i < 15; i++) {
                service.sendMessage("session-trim", "message " + i);
            }
        }

        // من غير trimming كان هيبقى 1 (system) + 15*2 = 31 رسالة في آخر طلب.
        assertTrue(lastMessageCount.get() < 31, "history should have been trimmed, got " + lastMessageCount.get());
    }

    /**
     * تغطية لإصلاح الـ race condition: رسالتين من نفس الجلسة في نفس اللحظة لازم يتعالجوا
     * بالتتابع (بسبب القفل الخاص بكل sessionId)، مش بالتوازي.
     */
    @Test
    void sendMessage_serializesConcurrentCallsForSameSession() throws Exception {
        AtomicInteger active = new AtomicInteger(0);
        AtomicInteger maxActive = new AtomicInteger(0);

        try (MockHttpServer mock = MockHttpServer.start((exchange) -> {
            int current = active.incrementAndGet();
            maxActive.updateAndGet(prev -> Math.max(prev, current));
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            MockHttpServer.readBody(exchange);
            active.decrementAndGet();
            try {
                MockHttpServer.reply(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        })) {
            AIService service = new AIService(AIProvider.OPENAI, "key", "sys", "gpt-x", mock.baseUrl());
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(2);

            Future<?> f1 = pool.submit(() -> {
                startLatch.countDown();
                awaitQuietly(startLatch);
                return runQuietly(() -> service.sendMessage("shared-session", "from thread 1"));
            });
            Future<?> f2 = pool.submit(() -> {
                startLatch.countDown();
                awaitQuietly(startLatch);
                return runQuietly(() -> service.sendMessage("shared-session", "from thread 2"));
            });

            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);
            pool.shutdown();
        }

        assertEquals(1, maxActive.get(), "requests for the same session must not run concurrently");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private interface ThrowingSupplier {
        String get() throws AIServiceException;
    }

    private static String runQuietly(ThrowingSupplier supplier) {
        try {
            return supplier.get();
        } catch (AIServiceException e) {
            throw new RuntimeException(e);
        }
    }
}
