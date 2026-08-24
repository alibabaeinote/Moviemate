# MovieMate — معماری نوتیفیکیشن (FCM) — طراحی کامل

**وضعیت**: 🟡 طراحی‌شده — پر کردن شکاف شناسایی‌شده در چک‌لیست dev  
**مرجع**: MovieMate-Backend-Schema.md، ALI-74 (Retention)، MovieMate-Dev-Checklist.md بخش ۶

---

## ۱. چرا این سند لازم بود

در پروتوتایپ‌های قبلی، نوتیفیکیشن فقط **بصری** طراحی شده بود (یک mockup صفحه‌ی قفل با یک کارت). هیچ‌جا مشخص نشده بود که این نوتیف واقعاً از کجا ارسال می‌شود، چطور به گوشی درست می‌رسد، و با لمس کردنش کاربر کجا فرود می‌آید. این سند این شکاف را می‌بندد.

---

## ۲. [UPDATE] افزودن فیلد به Schema — `users`

باید به schema قبلی (`MovieMate-Backend-Schema.md`) این فیلد اضافه شود:

```typescript
users/{userId}
{
  // ...فیلدهای قبلی...
  fcmTokens: string[],   // ممکن است کاربر چند دستگاه داشته باشد
  fcmTokenUpdatedAt: Timestamp
}
```

**نکته‌ی فنی**: FCM token می‌تواند تغییر کند (نصب مجدد اپ، پاک کردن داده). اپ باید هر بار در startup چک کند و در صورت تغییر، آرایه را به‌روزرسانی کند (نه فقط یک‌بار در سایز‌آپ).

---

## ۳. انواع نوتیفیکیشن — هرکدام با منبع، محتوا، و مقصد مشخص

| نوع | Trigger | چه زمانی | متن نمونه | Deep Link مقصد |
|---|---|---|---|---|
| **Daily Match Ready** | Cloud Function `generateDailyMatch` | ۹ صبح، فقط اگر `aBothOnboarded=true` | "Today's match is ready 🎬" | صفحه‌ی Match (کارت پیشنهاد امروز) |
| **Partner Joined** | `onPairUpdate` (وقتی userB پر می‌شود) | لحظه‌ای که نفر دوم با کد join می‌کند | "Sara joined! Rate your films to get your first match" | صفحه‌ی وضعیت onboarding |
| **Partner Rated (Waiting گروه)** | `onRatingComplete` | وقتی یک نفر ۱۰ فیلم اولیه را تمام کرد ولی دیگری نه | "Ali finished rating — your turn!" | صفحه‌ی Rate |
| **Partner Committed** | `onCommitUpdate` | وقتی یک نفر "We're in" زد ولی دیگری هنوز نه | "Ali wants to watch Dune with you!" | صفحه‌ی Match (کارت با دکمه‌ی We're in) |
| **Both Confirmed / Reminder** | `onCommitUpdate` (وقتی هر دو true شد) | بلافاصله بعد از تعهد متقابل کامل | "You're both in! We'll remind you tonight" | صفحه‌ی Reminder/زمان‌بندی |
| **Scheduled Reminder** | Cloud Function زمان‌بندی‌شده (۱۵ دقیقه قبل از زمان پیشنهادی) | زمان تماشای پیشنهادی | "Ready to watch Dune? 🍿" | صفحه‌ی Match (دکمه‌ی We watched it) |
| **Watchlist Activity** | `onWatchlistUpdate` | وقتی یک نفر فیلمی به‌صورت دستی اضافه می‌کند | "Sara added Poor Things to your list" | تب Watchlist |

---

## ۴. معماری ارسال (Cloud Function سطح بالا)

```javascript
async function sendNotification(userId, type, payload) {
  const user = await getUser(userId);
  if (!user.fcmTokens || user.fcmTokens.length === 0) return;

  // چک تنظیمات کاربر (notificationSettings که در schema قبلی تعریف شده)
  if (!isNotificationTypeEnabled(user.notificationSettings, type)) return;

  const message = {
    tokens: user.fcmTokens,
    notification: {
      title: payload.title,
      body: payload.body
    },
    data: {
      type: type,              // برای deep linking سمت کلاینت
      deepLinkTarget: payload.target,
      pairId: payload.pairId,
      matchId: payload.matchId || ""
    }
  };

  await admin.messaging().sendEachForMulticast(message);
}
```

**نکته‌ی مهم**: هر نوتیفیکیشن باید `notificationSettings` کاربر (که در schema قبلی وجود دارد: `dailyMatch`, `partnerActivity`, `reminders`) را چک کند قبل از ارسال — این دقیقاً همان سه گزینه‌ای است که در صفحه‌ی Settings تب Us طراحی شده بود.

---

## ۵. Deep Linking — از نوتیفیکیشن به صفحه‌ی درست

### سمت Android (Jetpack Compose + Navigation)
```kotlin
// در MainActivity، هنگام دریافت intent از نوتیفیکیشن
val deepLinkTarget = intent.getStringExtra("deepLinkTarget")
val pairId = intent.getStringExtra("pairId")

when (deepLinkTarget) {
    "match" -> navController.navigate("match/$pairId")
    "watchlist" -> navController.navigate("watchlist/$pairId")
    "rate" -> navController.navigate("rate/$pairId/${matchId}")
    "reminder" -> navController.navigate("reminder/$pairId")
    else -> navController.navigate("home")
}
```

این باید هم برای حالتی که اپ کاملاً بسته است (cold start از نوتیف) و هم حالتی که اپ در background است، تست شود — این دو مسیر کد متفاوت در اندروید دارند.

---

## ۶. مجوز نوتیفیکیشن (Android 13+)

از Android 13 به بعد (API 33+)، اپ باید صریحاً از کاربر مجوز `POST_NOTIFICATIONS` بگیرد — این دیگر خودکار نیست.

**پیشنهاد UX**: این مجوز را در همان صفحه‌ی onboarding (بعد از "Invite partner"، قبل از "Waiting") درخواست کنیم، همراه با توضیح کوتاه چرا لازم است ("So we can tell you when your daily match is ready") — نه یک popup سیستمی بی‌زمینه.

---

## ۷. جلوگیری از Notification Fatigue

طبق اصول قبلی retention (ALI-74: "Frequency Caps")، این سند آن‌ها را در سطح فنی مشخص می‌کند:

```
- حداکثر ۱ نوتیفیکیشن در روز اگر چیز مهمی نیست
- اگر کاربر همین الان اپ را باز کرده (activeSession=true در ۵ دقیقه‌ی گذشته)، نوتیف ارسال نشود
- نوتیف‌های نوع "Partner Activity" اگر در ۱۰ دقیقه‌ی اخیر مشابهی ارسال شده، merge/skip شوند
```

این منطق باید در همان تابع `sendNotification` به‌عنوان یک چک قبل از ارسال اضافه شود.

---

## ۸. Definition of Done — این لایه

- [ ] فیلد `fcmTokens` به schema اضافه و در Firestore واقعی تست شده
- [ ] هر ۷ نوع نوتیفیکیشن بالا به‌صورت Cloud Function واقعی نوشته شده
- [ ] Deep linking برای هر دو حالت (cold start / background) تست شده
- [ ] درخواست مجوز Android 13+ در onboarding پیاده و تست شده
- [ ] منطق frequency cap تست شده (سناریو: چند رویداد هم‌زمان، فقط یک نوتیف باید برسد)
- [ ] تنظیمات کاربر (`notificationSettings`) واقعاً نوتیف‌ها را فیلتر می‌کند (تست دستی: خاموش کردن یک نوع، چک عدم دریافت)

---

## ۹. اتصال به چک‌لیست کلی

این سند بخش ۶ چک‌لیست (`MovieMate-Dev-Checklist.md`) را از "طراحی نشده" به "🟡 طراحی‌شده" تغییر می‌دهد. پیاده‌سازی واقعی هنوز 🔴 است.
