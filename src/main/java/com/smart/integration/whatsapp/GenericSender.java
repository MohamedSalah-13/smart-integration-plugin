package com.smart.integration.whatsapp;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class GenericSender extends AbstractJsonWhatsAppSender {

    @Override
    protected ObjectNode buildPayload(String phone, String message) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("phone", phone);
        payload.put("message", message);
        return payload;
    }
}
