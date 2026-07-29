package com.smart.integration.whatsapp;

public final class WhatsAppSenderFactory {

    private WhatsAppSenderFactory() {}

    public static WhatsAppSender create(WhatsAppProvider provider) {
        if (provider == null) return new GenericSender();
        return switch (provider) {
            case ULTRAMSG  -> new UltraMsgSender();
            case META_CLOUD -> new MetaCloudSender();
            case WASENDER  -> new WaSenderSender();
            case GENERIC   -> new GenericSender();
        };
    }
}