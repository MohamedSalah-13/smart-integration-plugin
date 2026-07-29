package com.smart.integration.whatsapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WhatsAppSenderFactoryTest {

    @Test
    void create_returnsMatchingSenderImplementation_perProvider() {
        assertInstanceOf(UltraMsgSender.class, WhatsAppSenderFactory.create(WhatsAppProvider.ULTRAMSG));
        assertInstanceOf(MetaCloudSender.class, WhatsAppSenderFactory.create(WhatsAppProvider.META_CLOUD));
        assertInstanceOf(WaSenderSender.class, WhatsAppSenderFactory.create(WhatsAppProvider.WASENDER));
        assertInstanceOf(GenericSender.class, WhatsAppSenderFactory.create(WhatsAppProvider.GENERIC));
    }

    @Test
    void create_fallsBackToGenericSender_whenProviderIsNull() {
        assertInstanceOf(GenericSender.class, WhatsAppSenderFactory.create(null));
    }
}
