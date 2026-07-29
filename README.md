# Smart Integration Plugin

مكتبة (plugin) بجافا بتضاف كـ dependency لأي مشروع، وبتوفر واجهة موحّدة للتعامل مع:

- **الذكاء الاصطناعي**: OpenAI, Google Gemini, Anthropic (Claude), Groq, أو موديل محلي عبر Ollama — بنفس الكود بغض النظر عن الـ provider.
- **واتساب**: إرسال رسائل نصية عبر UltraMsg, Meta Cloud API, WaSenderAPI, أو أي provider تاني بيستخدم JSON + Bearer token (Generic).

مبني بـ Java 21 و Maven.

## المتطلبات

- Java 21+
- Maven 3.6+

## التثبيت

المشروع مش منشور على Maven Central، فبتضيفه محليًا:

```bash
mvn install
```

وبعدين تضيفه كـ dependency في `pom.xml` بتاع مشروعك:

```xml
<dependency>
    <groupId>com.smart.integration</groupId>
    <artifactId>smart-integration-plugin</artifactId>
    <version>1.0.0</version>
</dependency>
```

> المكتبة بتستخدم `slf4j-api` فقط (بدون implementation)، فلو عايز تشوف الـ logs، ضيف backend من اختيارك (logback, slf4j-simple, ...) في مشروعك.

## الاستخدام السريع

```java
import com.smart.integration.SmartIntegrationClient;
import com.smart.integration.ai.AIProvider;
import com.smart.integration.whatsapp.WhatsAppProvider;

SmartIntegrationClient client = new SmartIntegrationClient.Builder()
        .withAI(AIProvider.OPENAI, System.getenv("OPENAI_API_KEY"))
        .withAIModel("gpt-4o-mini")               // اختياري، لكن موصى به (راجع ملاحظة الموديلات تحت)
        .withSystemPrompt("أنت مساعد ذكي. أجب باختصار.")
        .withWhatsApp(WhatsAppProvider.ULTRAMSG,
                "https://api.ultramsg.com/instanceXXXX/messages/chat",
                System.getenv("ULTRAMSG_TOKEN"))
        .build();

// إرسال رسالة واتساب
var result = client.sendWhatsAppMessage("+201234567890", "أهلاً بيك!");
if (!result.success()) {
    System.err.println("فشل الإرسال: " + result.errorMessage());
}

// سؤال الذكاء الاصطناعي (جلسة واحدة مشتركة - مناسب لتطبيق ذو مستخدم واحد)
String reply = client.askAI("ما هي عاصمة مصر؟");

// أو جلسة مستقلة لكل مستخدم (مناسب لبوت واتساب بيخدم أكتر من رقم في نفس الوقت)
String replyForUser = client.askAI(phoneNumber, "ابعتلي ملخص الطلب اللي فات");
client.clearAIChat(phoneNumber); // "ابدأ من جديد"
```

## مزودو الذكاء الاصطناعي (`AIProvider`)

| القيمة | الوصف | مفتاح مطلوب؟ |
|---|---|---|
| `OPENAI` | OpenAI Chat Completions | نعم |
| `GOOGLE_GEMINI` | Google Gemini | نعم |
| `ANTHROPIC` | Anthropic Claude | نعم |
| `GROQ` | Groq (نفس شكل OpenAI) | نعم |
| `LOCAL` | Ollama محلي (`localhost:11434`) | لأ |

**ملاحظة مهمة**: الموديلات الافتراضية في `AIProvider` (زي `gpt-4o-mini`) بتتقادم أو تتوقف بمرور الوقت من جهة المزوّد. يُفضّل دايمًا تحديد موديل صريح عن طريق `withAIModel(...)` بدل الاعتماد على القيمة الافتراضية على المدى الطويل.

كل `AIService` instance بيحتفظ بتاريخ محادثة لكل `sessionId` بشكل مستقل وthread-safe (مفيد لبوت واتساب بيخدم أكتر من رقم في نفس الوقت)، وبيقصّ الـ history تلقائيًا عشان ميتجاوزش حدود التوكنز.

## مزودو الواتساب (`WhatsAppProvider`)

| القيمة | الوصف |
|---|---|
| `ULTRAMSG` | UltraMsg (form-encoded) |
| `META_CLOUD` | Meta Cloud API (WhatsApp Business) |
| `WASENDER` | WaSenderAPI |
| `GENERIC` | أي provider بيستخدم JSON + `Authorization: Bearer <token>` بحقلين `phone`/`message` |

`sendWhatsAppMessage(phone, message)` بترجع `WhatsAppSender.SendResult` (`success`, `statusCode`, `errorMessage`) بدل ما ترمي استثناء، عشان تقدر تتعامل مع فشل الإرسال بسهولة.

## الاختبارات

```bash
mvn test
```

الاختبارات بتغطي الـ providers، بناء الـ payloads، حالات الفشل، وسلوكيات الـ concurrency الخاصة بالـ AI session history — كلها ضد سيرفر HTTP محلي وهمي (`com.sun.net.httpserver`)، من غير أي استدعاء شبكة حقيقي.

## البناء

```bash
mvn package
```

بينتج `target/smart-integration-plugin-1.0.0.jar`.
