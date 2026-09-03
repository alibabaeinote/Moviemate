# MovieMate — چک‌لیست توسعه فنی تا v1

**آخرین به‌روزرسانی**: بعد از اولین sprint کدنویسی (اسکلت بک‌اند + اپ)
**هدف**: نقشه‌ی دقیق «چی واقعاً کد شده» در مقابل «چی مانده»
**روش**: وضعیت‌ها بر اساس کد موجود در repo علامت خورده‌اند، نه بر اساس اسناد طراحی.

### راهنمای وضعیت‌ها
- 🔴 **شروع‌نشده** — هیچ کد واقعی وجود ندارد
- 🟡 **کد نوشته شده، اجرا نشده** — کد هست ولی هرگز روی محیط واقعی اجرا/کامپایل نشده
- 🟢 **پروتوتایپ بصری** — فقط HTML نمایشی، بدون اتصال به داده
- ✅ **کد شده و تست شده** — کد واقعی + تست پاس

---

## ۰. خلاصه‌ی وضعیت فعلی

```
✅ کد شده و تست شده:  موتور recommendation (۴۲ تست)، frequency capها، منطق زمان محلی
🟡 کد نوشته، اجرا نشده: کل Cloud Functions، rules، لایه‌ی TMDB، دیزاین‌سیستم اندروید، صفحات auth
🔴 شروع‌نشده:          ۱۲ صفحه‌ی اصلی اپ، آمار صفحه‌ی Us، انتشار
```

**دو محدودیت مهم که باید بدانید**:
1. **هیچ کد اندرویدی کامپایل نشده** — محیطی که کد در آن نوشته شد Android SDK نداشت. روی اولین `./gradlew assembleDebug` انتظار خطای کامپایل داشته باشید.
2. **هیچ‌چیز deploy نشده** — نه پروژه‌ی Firebase، نه rules، نه functions.

---

## ۱. مسدودکننده‌ها — فقط دست انسان (بدون این‌ها هیچ‌چیز جلو نمی‌رود)

| مورد | وضعیت |
|---|---|
| ساخت پروژه‌ی Firebase (`moviemate-prod`) | 🔴 |
| فعال‌سازی Firestore (production mode) | 🔴 |
| فعال‌سازی Auth → Email/Password | 🔴 |
| فعال‌سازی Cloud Messaging | 🔴 |
| قرار دادن `google-services.json` در `android/app/` | 🔴 |
| گرفتن TMDB API Key و ست کردنش (`firebase functions:secrets:set`) | 🔴 |
| دانلود فونت‌ها در `android/app/src/main/res/font/` (لیست دقیق در README همان پوشه) | 🔴 |
| ساخت repo + CI/CD (GitHub Actions) | 🔴 |

---

## ۲. راستی‌آزمایی چیزی که ساخته شده (قدم بعدی بلافاصله بعد از بخش ۱)

| مورد | وضعیت | توضیح |
|---|---|---|
| اولین build موفق اندروید | 🔴 | **حتماً اول این** — کد اندروید هرگز کامپایل نشده |
| Deploy کردن `firestore.rules` | 🟡 | فایل آماده و اصلاح‌شده است |
| تست rules با Firebase Emulator | 🔴 | **اولویت بالا** — حفره‌ی `commitStatus` رفع شد ولی تست نشده؛ سناریوی کلیدی: userA نتواند `commitStatus.userB` را عوض کند |
| Deploy کردن Cloud Functions | 🟡 | ۱۱ تابع، TypeScript تمیز کامپایل می‌شود |
| تست `generateDailyMatch` با داده‌ی seed | 🔴 | باید با ۲ کاربر ساختگی و ۱۰ rating هرکدام تست شود |
| اولین فراخوانی واقعی TMDB | 🔴 | کلاینت نوشته شده، هرگز به API واقعی نخورده |
| اولین نوتیفیکیشن واقعی روی دستگاه فیزیکی | 🔴 | |

---

## ۳. لایه‌ی داده و بک‌اند

| مورد | وضعیت |
|---|---|
| Schema (۶ collection) به‌صورت type در کد | ✅ `functions/src/types.ts` + `Models.kt` |
| `firestore.rules` (اصلاح‌شده) | 🟡 |
| `firestore.indexes.json` | 🟡 |
| موتور recommendation (Taste Profile، جریمه‌ی اختلاف، آستانه) | ✅ ۴۲ تست |
| وزن‌های الگوریتم در فایل config جدا | ✅ `config/algorithm.ts` |
| کلاینت TMDB + کش ۶ ماهه | 🟡 |
| `generateDailyMatch` (ساعت ۹ محلی هر pair) | 🟡 |
| `onRatingComplete` → `aBothOnboarded` | 🟡 |
| `onCommitUpdate` → `bothConfirmedAt` | 🟡 |
| `rejectMatch` (fallback پی‌درپی ۱→۲→۳) | 🟡 |
| `createPair` / `joinPair` (کد دعوت + انقضا) | 🟡 |
| `refreshTmdbCache` / `expireInviteCodes` | 🟡 |
| ۷ نوع نوتیفیکیشن + frequency cap | 🟡 (منطق cap ✅ تست شده) |

### ⚠️ شکاف‌های بک‌اند که در اسناد نبودند و هنوز کد ندارند

این‌ها فیلدهایی هستند که در schema تعریف شده‌اند ولی **هیچ کدی آن‌ها را نمی‌نویسد**:

| شکاف | اثر |
|---|---|
| `pairs.streakCount` هرگز زیاد نمی‌شود | streak در صفحه‌ی Us همیشه صفر می‌ماند |
| `watchlist.mutualScore` هرگز محاسبه نمی‌شود | مرتب‌سازی «بر اساس رضایت مشترک» (PRD ۷.۴ بند ۵) کار نمی‌کند |
| `match.status` هرگز به `"watched"` تغییر نمی‌کند | بعد از «We watched it» چرخه بسته نمی‌شود |
| match تاییدشده به watchlist منتقل نمی‌شود | آیتم‌های Ready فقط از افزودن دستی می‌آیند |
| هیچ endpoint‌ای برای استخر فیلم onboarding نیست | صفحه‌ی rate اولیه منبع فیلم ندارد |

**این‌ها باید قبل از شروع صفحات UI مربوطه ساخته شوند**، وگرنه UI روی داده‌ی خالی نوشته می‌شود.

---

## ۴. صفحات اپ — بخش عمده‌ی کار باقی‌مانده

دیزاین‌سیستم و کامپوننت‌های پایه ساخته شده‌اند؛ صفحات هنوز placeholder هستند.

### زیرساخت UI (آماده)
| مورد | وضعیت |
|---|---|
| توکن‌های رنگ / تایپ / spacing (v8 قفل‌شده) | 🟡 |
| دکمه‌ی CTA + micro-interaction | 🟡 |
| تگ‌های pill | 🟡 |
| نوار ناوبری پایین (باگ چیدمان رفع شده) | 🟡 |
| Taste Dial (۰-۱۰۰ + گرادیانت) | 🟡 |
| نوار امتیاز مشترک دو نفره | 🟡 |
| ناوبری + deep link از نوتیف (cold + warm) | 🟡 |
| آیکون‌های سفارشی خطی ۲۴×۲۴ | 🔴 از Material استفاده شده به‌عنوان موقت |

### صفحات (همه 🔴 — فقط placeholder)
| صفحه | وابسته به |
|---|---|
| Sign Up / Sign In / Forgot Password | 🟡 **نوشته شده**، فقط build و تست مانده |
| انتخاب ژانر + دک rating اولیه (۱۰-۱۵ فیلم) | نیاز به endpoint استخر onboarding (بخش ۳) |
| Invite partner (نمایش کد + share) | `createPair` |
| Join with code | `joinPair` |
| درخواست مجوز نوتیف (Android 13+) | — |
| Waiting for partner | `pair.aBothOnboarded` |
| **صفحه‌ی Match** (کارت + We're in دوطرفه + Not feeling it) | هسته‌ی محصول — مهم‌ترین صفحه |
| Fallback سه‌گزینه‌ای (بعد از ۳ رد) | `match.shortlist` (آماده است) |
| حالت No matches | `match.noMatchesReason` (آماده است) |
| Reminder / زمان‌بندی | `scheduleWatch` |
| تایید دستی «We watched it» | نیاز به بستن چرخه (بخش ۳) |
| Taste Dial بعد از تماشا | — |
| Watchlist (Ready / Waiting / Watched + جست‌وجوی TMDB + I'm in too) | نیاز به `mutualScore` (بخش ۳) |
| Us (streak / journey / compatibility) | نیاز به `streakCount` (بخش ۳) |
| Settings + About با attribution رسمی TMDB | الزام قانونی |

---

## ۵. الزامات انتشار (قابل انجام موازی)

| مورد | وضعیت |
|---|---|
| Privacy Policy (ALI-69) | 🔴 |
| Terms of Service | 🔴 |
| متن attribution رسمی TMDB در صفحه‌ی About | 🟡 متن در `strings.xml` هست، صفحه نیست |
| صفحه‌ی Play Store + اسکرین‌شات | 🔴 |
| کلید امضای release + کانفیگ | 🔴 |
| تست ProGuard روی build نسخه‌ی release | 🔴 |

---

## ۶. تصمیمات باز که باید بسته شوند

| سوال | چرا الان مهم است |
|---|---|
| **تناقض فرمول qualityBonus** | سند الگوریتم §۵ می‌نویسد `(rating/10)×10` = ۰-۱۰ ولی §۶ می‌نویسد `rating×10` = ۰-۱۰۰. کد از §۶ پیروی می‌کند. اگر §۵ درست باشد، ۹۰٪ وزن کیفیت بی‌صدا از بین می‌رود. |
| رفتار آفلاین برای rating | الان اتصال لازم است. اگر باید queue شود، روی repository اثر دارد. |
| کیفیت استخر کاندیدا | الان از TMDB discover با مرتب‌سازی popularity و حداقل ۲۰۰ رأی می‌آید — سوگیری به فیلم‌های جدید mainstream دارد. |
| Haptic feedback در swipe | باز |
| orientation افقی | پیش‌فرض فعلی: فقط عمودی (در manifest قفل شده) |

---

## ۷. ترتیب پیشنهادی کار (بر اساس وابستگی فنی)

```
مرحله ۱ — رفع انسداد (چند روز)
  بخش ۱ کامل + اولین build موفق اندروید + deploy rules و functions

مرحله ۲ — بستن شکاف‌های بک‌اند (بخش ۳)
  streak، mutualScore، بستن چرخه‌ی watched، endpoint استخر onboarding
  → بدون این‌ها صفحات UI روی داده‌ی خالی ساخته می‌شوند

مرحله ۳ — مسیر onboarding کامل
  ژانر → rate → invite → مجوز نوتیف → waiting → partner join
  → اولین لحظه‌ای که دو کاربر واقعی به هم وصل می‌شوند

مرحله ۴ — چرخه‌ی روزانه (هسته‌ی محصول)
  Match → We're in دوطرفه → reminder → watched → Taste Dial → Home
  → اولین لحظه‌ای که محصول واقعاً کار می‌کند

مرحله ۵ — Watchlist و Us

مرحله ۶ — ۵ سناریوی edge case + تست با کاربر واقعی + انتشار
```

---

## ۸. Definition of Done — v1 (از PRD §۱۱)

- [ ] هر ۵ سناریوی ALI-73 قابل تست و بدون crash
- [ ] happy path کامل بدون گیر کردن توسط کاربر واقعی
- [ ] Recommendation engine روی حداقل ۲۰ نفر تست واقعی شده
- [ ] Crash rate در بتا زیر ۰.۱٪
- [ ] همه‌ی متن‌های UI مطابق design system v8
- [ ] Privacy Policy و ToS منتشر شده
- [ ] Onboarding بدون کمک بیرونی توسط حداقل ۳ کاربر تست قابل تکمیل
- [ ] Watchlist با هر ۳ دسته‌بندی و جست‌وجوی دستی کار کند
- [ ] **(جدید)** rules با emulator تست شده — خصوصاً این‌که یک نفر نتواند برای هر دو تایید بزند
