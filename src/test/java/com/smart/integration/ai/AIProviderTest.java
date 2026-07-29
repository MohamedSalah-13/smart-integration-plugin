package com.smart.integration.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AIProviderTest {

    @Test
    void buildApiUrl_returnsTemplateAsIs_whenNoPlaceholder() {
        assertEquals("https://api.openai.com/v1/chat/completions", AIProvider.OPENAI.buildApiUrl("gpt-4o-mini"));
    }

    @Test
    void buildApiUrl_substitutesModel_whenTemplateHasPlaceholder() {
        String url = AIProvider.GOOGLE_GEMINI.buildApiUrl("gemini-3.6-flash");
        assertEquals(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent",
                url);
    }

    @Test
    void getDefaultModel_returnsProviderSpecificDefault() {
        assertEquals("llama3.1", AIProvider.LOCAL.getDefaultModel());
        assertEquals("claude-haiku-4-5-20251001", AIProvider.ANTHROPIC.getDefaultModel());
    }
}
