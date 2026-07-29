package com.smart.integration.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * خدمة موحّدة للتحدث مع أكثر من مزوّد AI بنفس الواجهة.
 * <p>
 * تدعم أكثر من "جلسة محادثة" (session) في نفس الوقت عن طريق sendMessage(sessionId, message):
 * ده مهم لو الـ plugin ده هيُستخدم من بوت واتساب مثلاً، بحيث كل رقم هاتف له تاريخ محادثة
 * منفصل بدل ما يشتركوا كلهم في نفس الـ history (كان ده باگ في النسخة الأصلية).
 */
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private static final String DEFAULT_SESSION = "__default__";
    private static final int MAX_HISTORY_MESSAGES = 20; // لا يشمل الـ system prompt

    private final HttpClient httpClient;
    private final Gson gson;
    private final Map<String, List<Message>> sessions = new ConcurrentHashMap<>();
    // قفل مستقل لكل sessionId: بيمنع رسالتين من نفس الجلسة (نفس رقم واتساب مثلاً)
    // إنهم يقروا/يعدّلوا نفس الـ history في نفس الوقت (كان ده سبب تلخبط الترتيب).
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

    private final AIProvider provider;
    private final String apiKey;
    private final String model;
    private final String systemPrompt;
    private final String apiUrlOverride;

    public AIService(AIProvider provider, String apiKey, String systemPrompt) {
        this(provider, apiKey, systemPrompt, null);
    }

    /**
     * @param modelOverride موديل مخصص بدل الافتراضي (موصى به، لأن الموديلات الافتراضية بتتقدّم/تتوقف بمرور الوقت).
     */
    public AIService(AIProvider provider, String apiKey, String systemPrompt, String modelOverride) {
        this(provider, apiKey, systemPrompt, modelOverride, null);
    }

    /**
     * Package-private: بيسمح للاختبارات بتوجيه الطلبات لسيرفر HTTP محلي وهمي
     * بدل الـ endpoint الحقيقي للـ provider، بدون فتح الـ URL كـ API عام للمستهلكين.
     */
    AIService(AIProvider provider, String apiKey, String systemPrompt, String modelOverride, String apiUrlOverride) {
        if (provider == null) {
            throw new IllegalArgumentException("AIProvider must not be null");
        }
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.gson = new Gson();
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : provider.getDefaultModel();
        this.systemPrompt = systemPrompt;
        this.apiUrlOverride = apiUrlOverride;
    }

    private String resolveApiUrl() {
        return apiUrlOverride != null ? apiUrlOverride : provider.buildApiUrl(model);
    }

    /** يرسل رسالة ضمن الجلسة الافتراضية (مناسب لتطبيق ذو مستخدم واحد). */
    public String sendMessage(String userMessage) throws AIServiceException {
        return sendMessage(DEFAULT_SESSION, userMessage);
    }

    /**
     * يرسل رسالة ضمن جلسة محددة (مثلاً رقم هاتف واتساب)، بحيث كل جلسة عندها
     * تاريخ محادثة مستقل عن الباقي، ونفس الـ AIService instance ممكن يخدم عدة مستخدمين بأمان.
     */
    public String sendMessage(String sessionId, String userMessage) throws AIServiceException {
        validateApiKey();

        Object lock = sessionLocks.computeIfAbsent(sessionId, id -> new Object());
        synchronized (lock) {
            List<Message> history = sessions.computeIfAbsent(sessionId, id -> newHistory());
            history.add(new Message("user", userMessage));

            log.debug("Sending message to {} (session={}, model={})", provider, sessionId, model);

            String response;
            try {
                response = switch (provider) {
                    case OPENAI, GROQ -> sendOpenAIFormat(history);
                    case GOOGLE_GEMINI -> sendGeminiFormat(history);
                    case ANTHROPIC -> sendAnthropicFormat(history);
                    case LOCAL -> sendOllamaFormat(history);
                };
            } catch (AIServiceException e) {
                // نشيل رسالة الـ user اللي اتضافت قبل المحاولة، عشان الـ history متفضلش
                // فيها رسالتين "user" متتاليتين (Anthropic مثلاً بيرفض ده في الطلب التالي).
                history.remove(history.size() - 1);
                log.warn("AI request to {} failed (session={}): {}", provider, sessionId, e.getMessage());
                throw e;
            } catch (Exception e) {
                history.remove(history.size() - 1);
                log.warn("AI request to {} failed (session={}): {}", provider, sessionId, e.toString());
                throw new AIServiceException(
                        "Failed to get a response from " + provider.name() + ": " + e.getMessage(), e);
            }

            history.add(new Message("assistant", response));
            trimHistory(history);
            return response;
        }
    }

    private void validateApiKey() {
        // الباگ الأصلي: apiKey == null || apiKey.isBlank() && provider != LOCAL
        // بسبب أولوية && على || كان بيرمي استثناء حتى لو الـ provider هو LOCAL.
        boolean missing = apiKey == null || apiKey.isBlank();
        if (missing && provider != AIProvider.LOCAL) {
            throw new IllegalArgumentException("API Key is missing for provider: " + provider.name());
        }
    }

    private List<Message> newHistory() {
        List<Message> list = new CopyOnWriteArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            list.add(new Message("system", systemPrompt));
        }
        return list;
    }

    /** يمنع نمو الـ history بلا حدود (وبالتالي تجاوز حدود التوكنز في محادثة طويلة). */
    private void trimHistory(List<Message> history) {
        while (history.size() > MAX_HISTORY_MESSAGES) {
            for (Message m : history) {
                if (!"system".equals(m.role())) {
                    history.remove(m);
                    break;
                }
            }
        }
    }

    private String sendOpenAIFormat(List<Message> history) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("max_tokens", 1000);

        JsonArray messagesArray = new JsonArray();
        for (Message msg : history) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.role());
            msgObj.addProperty("content", msg.content());
            messagesArray.add(msgObj);
        }
        requestBody.add("messages", messagesArray);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveApiUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AIServiceException("Server error (" + response.statusCode() + "): " + response.body());
        }

        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
        return jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }

    private String sendGeminiFormat(List<Message> history) throws Exception {
        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();

        for (Message msg : history) {
            if ("system".equals(msg.role())) continue;
            JsonObject content = new JsonObject();
            content.addProperty("role", "user".equals(msg.role()) ? "user" : "model");
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", msg.content());
            parts.add(part);
            content.add("parts", parts);
            contents.add(content);
        }
        requestBody.add("contents", contents);

        // الباگ الأصلي: كان بيتجاهل رسالة الـ system تمامًا مع Gemini (لأنه كان بيعمل continue
        // وميحطّهاش في أي مكان). الصح إنها تتبعت كـ systemInstruction منفصل.
        history.stream()
                .filter(m -> "system".equals(m.role()))
                .findFirst()
                .ifPresent(sys -> {
                    JsonObject systemInstruction = new JsonObject();
                    JsonArray parts = new JsonArray();
                    JsonObject part = new JsonObject();
                    part.addProperty("text", sys.content());
                    parts.add(part);
                    systemInstruction.add("parts", parts);
                    requestBody.add("systemInstruction", systemInstruction);
                });

        String url = resolveApiUrl() + "?key=" + apiKey;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AIServiceException("Gemini error (" + response.statusCode() + "): " + response.body());
        }

        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
        return jsonResponse.getAsJsonArray("candidates").get(0).getAsJsonObject()
                .getAsJsonObject("content").getAsJsonArray("parts")
                .get(0).getAsJsonObject().get("text").getAsString();
    }

    private String sendAnthropicFormat(List<Message> history) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("max_tokens", 1000);

        JsonArray messagesArray = new JsonArray();
        for (Message msg : history) {
            if ("system".equals(msg.role())) {
                requestBody.addProperty("system", msg.content());
            } else {
                JsonObject msgObj = new JsonObject();
                msgObj.addProperty("role", msg.role());
                msgObj.addProperty("content", msg.content());
                messagesArray.add(msgObj);
            }
        }
        requestBody.add("messages", messagesArray);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveApiUrl()))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AIServiceException("Claude error (" + response.statusCode() + "): " + response.body());
        }

        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
        return jsonResponse.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
    }

    private String sendOllamaFormat(List<Message> history) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);

        JsonArray messagesArray = new JsonArray();
        for (Message msg : history) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.role());
            msgObj.addProperty("content", msg.content());
            messagesArray.add(msgObj);
        }
        requestBody.add("messages", messagesArray);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveApiUrl()))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AIServiceException("Ollama error. Make sure it is running locally.");
        }

        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
        return jsonResponse.getAsJsonObject("message").get("content").getAsString();
    }

    /** يمسح تاريخ الجلسة الافتراضية. */
    public void clearHistory() {
        clearHistory(DEFAULT_SESSION);
    }

    /** يمسح تاريخ جلسة محددة (مثلاً بعد ما مستخدم واتساب معين يطلب "ابدأ من جديد"). */
    public void clearHistory(String sessionId) {
        sessions.remove(sessionId);
        log.debug("Cleared AI history (session={})", sessionId);
    }

    private record Message(String role, String content) {}
}
