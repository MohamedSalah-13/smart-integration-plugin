package com.smart.integration.whatsapp;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class WaSenderSender extends AbstractJsonWhatsAppSender {

    @Override
    protected ObjectNode buildPayload(String phone, String message) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("to", phone);
        payload.put("text", message);
        return payload;
    }
}
