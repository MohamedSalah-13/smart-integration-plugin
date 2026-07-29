package com.smart.integration.whatsapp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaSenderSenderTest {

    @Test
    void buildPayload_usesToAndTextFields() {
        ObjectNode payload = new WaSenderSender().buildPayload("+201234567890", "hello");

        assertEquals("+201234567890", payload.get("to").asText());
        assertEquals("hello", payload.get("text").asText());
    }
}
