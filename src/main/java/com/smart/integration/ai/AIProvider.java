package com.smart.integration.ai;

public enum AIProvider {

    // ملاحظة: هذه هي القيم الافتراضية فقط. مزودو الـ AI يقومون بإيقاف الموديلات القديمة
    // بشكل متكرر، لذا يُفضّل دائمًا تمرير موديل مخصص عبر withAIModel(...) بدل الاعتماد
    // على القيمة الافتراضية على المدى الطويل.
    OPENAI("https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
    GOOGLE_GEMINI("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent", "gemini-3.6-flash"),
    ANTHROPIC("https://api.anthropic.com/v1/messages", "claude-haiku-4-5-20251001"),
    GROQ("https://api.groq.com/openai/v1/chat/completions", "openai/gpt-oss-20b"),
    LOCAL("http://localhost:11434/api/chat", "llama3.1");

    private final String apiUrlTemplate;
    private final String defaultModel;

    AIProvider(String apiUrlTemplate, String defaultModel) {
        this.apiUrlTemplate = apiUrlTemplate;
        this.defaultModel = defaultModel;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * يبني رابط الـ API الفعلي. بعض المزودين (مثل Gemini) يضعون اسم الموديل
     * داخل الرابط نفسه، لذلك لازم يتبنى ديناميكيًا بدل ما يكون ثابت جوه الـ enum.
     */
    public String buildApiUrl(String model) {
        return apiUrlTemplate.contains("%s") ? String.format(apiUrlTemplate, model) : apiUrlTemplate;
    }
}
