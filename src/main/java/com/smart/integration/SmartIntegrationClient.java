package com.smart.integration;

import com.smart.integration.ai.AIProvider;
import com.smart.integration.ai.AIService;
import com.smart.integration.ai.AIServiceException;
import com.smart.integration.whatsapp.WhatsAppProvider;
import com.smart.integration.whatsapp.WhatsAppSender;
import com.smart.integration.whatsapp.WhatsAppSenderFactory;

public class SmartIntegrationClient {

    private final String waApiUrl;
    private final String waApiToken;
    private final WhatsAppSender waSender;

    private final AIService aiService;

    private SmartIntegrationClient(Builder builder) {
        this.waApiUrl = builder.waApiUrl;
        this.waApiToken = builder.waApiToken;
        this.waSender = builder.waProvider != null ? WhatsAppSenderFactory.create(builder.waProvider) : null;

        this.aiService = builder.aiProvider != null
                ? new AIService(builder.aiProvider, builder.aiApiKey, builder.aiSystemPrompt, builder.aiModel)
                : null;
    }

    // ==========================================
    // واجهة الواتساب
    // ==========================================
    public WhatsAppSender.SendResult sendWhatsAppMessage(String phone, String message) {
        if (waSender == null || waApiUrl == null || waApiToken == null) {
            throw new IllegalStateException("WhatsApp service is not configured properly.");
        }
        return waSender.send(waApiUrl, waApiToken, phone, message);
    }

    // ==========================================
    // واجهة الذكاء الاصطناعي
    // ==========================================

    /** يستخدم جلسة محادثة واحدة مشتركة - مناسب لتطبيق ذو مستخدم واحد فقط. */
    public String askAI(String message) throws AIServiceException {
        requireAiConfigured();
        return aiService.sendMessage(message);
    }

    /**
     * يستخدم جلسة محادثة مستقلة لكل sessionId (مثلاً رقم هاتف واتساب)، بحيث
     * لو الـ plugin ده بيخدم أكتر من مستخدم في نفس الوقت (بوت واتساب مثلاً)،
     * كل مستخدم يكون ليه تاريخ محادثة منفصل تمامًا عن الباقي.
     */
    public String askAI(String sessionId, String message) throws AIServiceException {
        requireAiConfigured();
        return aiService.sendMessage(sessionId, message);
    }

    public void clearAIChat() {
        if (aiService != null) {
            aiService.clearHistory();
        }
    }

    public void clearAIChat(String sessionId) {
        if (aiService != null) {
            aiService.clearHistory(sessionId);
        }
    }

    private void requireAiConfigured() {
        if (aiService == null) {
            throw new IllegalStateException("AI service is not configured.");
        }
    }

    // ==========================================
    // نمط البناء (Builder)
    // ==========================================
    public static class Builder {
        private String waApiUrl;
        private String waApiToken;
        private WhatsAppProvider waProvider;

        private AIProvider aiProvider;
        private String aiApiKey;
        private String aiModel;
        private String aiSystemPrompt = "أنت مساعد ذكي. أجب باختصار."; // قيمة افتراضية

        public Builder withWhatsApp(WhatsAppProvider provider, String apiUrl, String apiToken) {
            if (provider != null && (apiUrl == null || apiUrl.isBlank() || apiToken == null || apiToken.isBlank())) {
                throw new IllegalArgumentException(
                        "apiUrl and apiToken are required when a WhatsApp provider is configured.");
            }
            this.waProvider = provider;
            this.waApiUrl = apiUrl;
            this.waApiToken = apiToken;
            return this;
        }

        public Builder withAI(AIProvider provider, String apiKey) {
            this.aiProvider = provider;
            this.aiApiKey = apiKey;
            return this;
        }

        /** اختياري: يحدد موديل مختلف عن الافتراضي الخاص بالـ provider (موصى به). */
        public Builder withAIModel(String model) {
            this.aiModel = model;
            return this;
        }

        public Builder withSystemPrompt(String prompt) {
            this.aiSystemPrompt = prompt;
            return this;
        }

        public SmartIntegrationClient build() {
            return new SmartIntegrationClient(this);
        }
    }
}
