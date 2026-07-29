package com.smart.integration.whatsapp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenericSenderTest {

    @Test
    void buildPayload_usesPhoneAndMessageFields() {
        ObjectNode payload = new GenericSender().buildPayload("+201234567890", "hello");

        assertEquals("+201234567890", payload.get("phone").asText());
        assertEquals("hello", payload.get("message").asText());
    }
}
