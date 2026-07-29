package com.smart.integration.whatsapp;

public enum WhatsAppProvider {
    ULTRAMSG("UltraMsg"),
    META_CLOUD("Meta Cloud API"),
    WASENDER("WaSenderAPI"),
    GENERIC("Generic (JSON Bearer)");

    private final String displayName;

    WhatsAppProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}