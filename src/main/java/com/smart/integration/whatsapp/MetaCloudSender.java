package com.smart.integration.whatsapp;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class MetaCloudSender extends AbstractJsonWhatsAppSender {

    @Override
    protected ObjectNode buildPayload(String phone, String message) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", phone);
        payload.put("type", "text");
        ObjectNode text = payload.putObject("text");
        text.put("preview_url", false);
        text.put("body", message);
        return payload;
    }
}
