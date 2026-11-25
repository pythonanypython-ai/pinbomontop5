# دایرکت بلادرنگ PinBom Android 5.10.0

در این نسخه، Polling دوره‌ای inbox و صفحهٔ گفت‌وگو حذف شده است. اپ از اتصال WebSocket احراز هویت‌شده با همان توکن نشست استفاده می‌کند.

## مسیر اتصال

```text
wss://<host>/v1/chats/socket
Authorization: Bearer <access_token>
Upgrade: websocket
Connection: Upgrade
```

قبل از ساخت اتصال، کلاینت یک GET احراز هویت‌شده به `GET /v1/chats/` می‌زند تا در صورت نیاز refresh token اجرا شود؛ سپس access token به هدر WebSocket اضافه می‌شود. توکن داخل URL یا Query String قرار نمی‌گیرد.

## رویدادها

رویدادهای `conversation.created`, `message.created`, `message.updated`, `message.deleted`, `reaction.updated`, `receipt.read` و `typing` از سوکت دریافت می‌شوند. برای `typing`، کلاینت پیام زیر را می‌فرستد:

```json
{"type":"typing","conversation_id":"<conversation-id>"}
```

سوکت صرفاً سیگنال تغییر را پخش می‌کند؛ پیام، فایل ویس و دادهٔ کامل واکنش‌ها از همان REST APIهای قبلی خوانده می‌شوند. بنابراین قابلیت تاریخچه، آپلود فایل و بازیابی پس از قطعی حفظ شده است.

## پایداری اتصال

- Ping هر ۲۵ ثانیه در کلاینت و سرور
- تلاش مجدد با backoff از ۱ ثانیه تا حداکثر ۳۰ ثانیه
- پس از اتصال مجدد، دریافت دوبارهٔ تاریخچه/لیست از REST
- تایپ با محدودیت ارسال در کلاینت و سرور
- درخواست WebSocket از Polling دوره‌ای استفاده نمی‌کند

اتصال WebSocket هنگام نمایش صفحهٔ Inbox یا گفت‌وگو فعال می‌شود و با خروج از صفحه بسته می‌شود. اعلان در زمانی که اپ بسته است همچنان نیازمند FCM و اطلاعات Firebase معتبر است.
