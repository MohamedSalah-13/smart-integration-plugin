package com.smart.integration.whatsapp;

import java.net.http.HttpResponse;

public interface WhatsAppSender {

    record SendResult(boolean success, int statusCode, String errorMessage) {
        public static SendResult ok(int code) { return new SendResult(true, code, null); }
        public static SendResult fail(int code, String msg) { return new SendResult(false, code, msg); }
        public static SendResult error(String msg) { return new SendResult(false, -1, msg); }
    }

    SendResult send(String apiUrl, String apiToken, String phone, String message);

    default SendResult mapResponse(HttpResponse<String> response) {
        int code = response.statusCode();
        if (code >= 200 && code < 300) return SendResult.ok(code);
        return SendResult.fail(code, "HTTP " + code + ": " + response.body());
    }
}