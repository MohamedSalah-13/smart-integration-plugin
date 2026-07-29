package com.smart.integration.ai;

/**
 * يُرمى عند فشل التواصل مع مزوّد الذكاء الاصطناعي (خطأ شبكة، رد غير متوقع، حالة HTTP غير 200...).
 * استبدال RuntimeException العامة بهذا النوع يسمح للمشاريع المستهلكة بالتعامل مع
 * أخطاء الـ AI بشكل مميز عن أي أخطاء runtime أخرى.
 */
public class AIServiceException extends Exception {

    public AIServiceException(String message) {
        super(message);
    }

    public AIServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
