package com.smart.integration.whatsapp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MetaCloudSenderTest {

    @Test
    void buildPayload_matchesMetaCloudApiShape() {
        ObjectNode payload = new MetaCloudSender().buildPayload("+201234567890", "hello");

        assertEquals("whatsapp", payload.get("messaging_product").asText());
        assertEquals("+201234567890", payload.get("to").asText());
        assertEquals("text", payload.get("type").asText());
        assertEquals("hello", payload.get("text").get("body").asText());
        assertFalse(payload.get("text").get("preview_url").asBoolean());
    }
}
