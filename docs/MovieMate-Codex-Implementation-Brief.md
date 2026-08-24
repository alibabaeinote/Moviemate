# MovieMate — Implementation Brief for Codex

**هدف این سند**: یک بریف قابل‌کپی‌پیست برای Codex (یا هر AI coding agent دیگر) تا مرحله‌به‌مرحله زیرساخت و فیچرهای MovieMate را بسازد.  
**نحوه‌ی استفاده**: کل این فایل را به Codex بده، یا هر Phase را جداگانه در یک session بده تا context مدیریت‌پذیر بماند.

---

## 🎯 خلاصه‌ی پروژه (Context برای Codex)

MovieMate یک اپ اندروید (Kotlin + Jetpack Compose) برای **دو نفر** (زوج/دوست) است که هر روز یک فیلم بر اساس تقاطع سلیقه‌شان پیشنهاد می‌دهد. Backend: Firebase (Auth + Firestore + Cloud Functions). محتوا: TMDB API. بدون matching با غریبه — فقط دو نفری که با کد دعوت به هم وصل می‌شوند.

**اصول طراحی که باید در کد رعایت شوند** (این‌ها تصمیمات نهایی‌اند، تغییرشان ندهید بدون تایید):
1. رتبه‌بندی فیلم = عدد پیوسته **۰ تا ۱۰۰** (نه ۵ ستاره)
2. تصمیم‌گیری روی هر پیشنهاد باید **دوطرفه** باشد — هر دو کاربر باید مستقل تایید کنند
3. پیشنهاد روزانه **یکی‌یکی** نمایش داده می‌شود (نه چند گزینه هم‌زمان)
4. تشخیص "دیده شد" **دستی** است — بدون اتصال به تقویم/استریمینگ
5. زبان اپ: انگلیسی

---

## 📦 Phase 0 — زیرساخت خام (پیش‌نیاز، معمولاً دستی توسط انسان انجام می‌شود)

> این فاز شامل کارهایی است که در Firebase Console و Android Studio باید انجام شود. Codex می‌تواند فایل‌های کانفیگ و کد اسکلت را بسازد، ولی ساخت خود پروژه در کنسول‌ها نیاز به دسترسی انسانی دارد.

- [ ] پروژه‌ی Firebase ساخته شده (نام: moviemate-prod)
- [ ] Firestore Database در Production mode فعال شده
- [ ] Authentication → Email/Password فعال شده
- [ ] Cloud Messaging فعال شده
- [ ] فایل `google-services.json` گرفته شده
- [ ] پروژه‌ی Android (Kotlin + Jetpack Compose، Min SDK 26) ساخته شده
- [ ] TMDB API Key گرفته شده

**اگر این‌ها انجام نشده، Codex باید اول این‌ها را به‌عنوان پیش‌نیاز به کاربر یادآوری کند، نه این‌که فرض کند وجود دارند.**

---

## 📦 Phase 1 — Firestore Schema + Security Rules

### دستور برای Codex:
```
با استفاده از schema زیر، Firestore collections را در پروژه‌ی Firebase ایجاد کن
(با یک اسکریپت seed برای mock data)، و فایل firestore.rules زیر را deploy کن.
```

### Schema کامل (۶ collection + فیلد نوتیفیکیشن):

```typescript
// users/{userId}
{
  uid: string,
  name: string,
  email: string,
  emailVerified: boolean,
  createdAt: Timestamp,
  pairId: string | null,
  onboardingComplete: boolean,
  ratingCount: number,
  fcmTokens: string[],
  fcmTokenUpdatedAt: Timestamp,
  notificationSettings: {
    dailyMatch: boolean,
    partnerActivity: boolean,
    reminders: boolean
  }
}

// pairs/{pairId}
{
  userA: string,
  userB: string | null,
  inviteCode: string,
  inviteCodeExpiresAt: Timestamp,
  status: "waiting_partner" | "both_rating" | "active",
  createdAt: Timestamp,
  aBothOnboarded: boolean,
  streakCount: number,
  lastMatchGeneratedAt: Timestamp
}

// pairs/{pairId}/ratings/{ratingId}
{
  userId: string,
  filmId: string,
  score: number,          // 0-100 — نه 1-5!
  isInitialOnboarding: boolean,
  reactionEmoji: string | null,
  ratedAt: Timestamp
}

// pairs/{pairId}/matches/{matchId}
{
  filmId: string,
  score: number,
  reason: string,
  suggestedAt: Timestamp,
  status: "suggested" | "watched" | "dismissed",
  attemptNumber: number,   // 1, 2, or 3
  commitStatus: { userA: boolean, userB: boolean },
  bothConfirmedAt: Timestamp | null,
  watchedConfirmedAt: Timestamp | null
}

// pairs/{pairId}/watchlist/{itemId}
{
  filmId: string,
  addedBy: string,
  addedAt: Timestamp,
  source: "match" | "manual_search",
  status: "waiting" | "ready" | "watched",
  commitStatus: { userA: boolean, userB: boolean },
  watchedAt: Timestamp | null,
  mutualScore: number | null
}

// filmCache/{filmId}
{
  tmdbId: string,
  title: string,
  posterPath: string,
  genres: string[],
  releaseYear: number,
  runtime: number,
  overview: string,
  tmdbRating: number,
  cachedAt: Timestamp,
  expiresAt: Timestamp     // = cachedAt + 6 months (الزام قانونی TMDB)
}
```

### Security Rules (فایل کامل آماده — فایل پیوست `firestore.rules` را مستقیم استفاده کن)

اصول کلیدی که باید رعایت شود:
- هر کاربر فقط `pairs/{pairId}` خودش را می‌خواند/می‌نویسد
- `commitStatus.userA` فقط توسط userA قابل تغییر (و برعکس برای userB)
- `ratings` فقط توسط صاحبش نوشته می‌شود
- `score` باید بین ۰ تا ۱۰۰ باشد (اجرا در سطح rule)
- `filmCache` فقط توسط Cloud Functions نوشته می‌شود، کلاینت فقط read

---

## 📦 Phase 2 — Firebase Auth (Email/Password)

### دستور برای Codex:
```
صفحات Sign Up و Sign In را در Jetpack Compose بساز که به Firebase Auth
(Email/Password) وصل باشند. شامل:
- فرم Sign Up (name, email, password) → createUserWithEmailAndPassword
- ارسال خودکار Email Verification بعد از sign up
- فرم Sign In → signInWithEmailAndPassword
- صفحه‌ی Forgot Password → sendPasswordResetEmail
- مدیریت session با Firebase Auth state listener (کاربر لاگین بماند بین اجراها)
- بعد از sign up موفق، رکورد اولیه در users/{uid} با فیلدهای schema بالا ایجاد شود
```

---

## 📦 Phase 3 — TMDB Client + Cache Layer

### دستور برای Codex:
```
یک TMDB API client wrapper در Kotlin بساز (با Retrofit یا Ktor) که:
1. فیلم‌های محبوب/بر اساس ژانر را برای onboarding pool بگیرد
2. جست‌وجوی فیلم برای Watchlist manual-add را پشتیبانی کند
3. قبل از هر فراخوانی API، اول filmCache در Firestore را چک کند
4. اگر cache موجود و expiresAt نگذشته، از cache استفاده کند
5. اگر نبود یا منقضی بود، از TMDB بگیرد و در filmCache با
   expiresAt = now + 6 months ذخیره کند
6. یک Cloud Function روزانه (refreshTmdbCache) بساز که رکوردهای
   منقضی‌شده را پاک/رفرش می‌کند

الزام: هرجا داده‌ی TMDB نمایش داده می‌شود، صفحه‌ی About/Settings باید
attribution رسمی TMDB را نشان دهد (متن دقیق را از تیم legal یا از
https://www.themoviedb.org/about/logos-attribution بگیر).
```

---

## 📦 Phase 4 — Recommendation Algorithm (Cloud Function)

### دستور برای Codex:
```
یک Cloud Function به نام generateDailyMatch بساز که:

1. فقط برای pairs با aBothOnboarded == true اجرا شود (زمان‌بندی‌شده، ۹ صبح
   به وقت محلی هر pair — اگر timezone در schema نیست، اضافه کن)

2. برای هر pair:
   a. Taste Profile هر کاربر را از subcollection ratings بساز:
      - genreAffinity[genre] = میانگین وزنی امتیازها روی فیلم‌های آن ژانر
      - eraAffinity[decade] = همین‌طور برای دهه‌ی انتشار
      - countryAffinity[country] = همین‌طور برای کشور تولید

   b. برای هر فیلم کاندیدا (فیلمی که هیچ‌کدام rate/watch نکرده‌اند):
      predictedScore(user, film) =
          0.6 × avg(genreAffinity[g] for g in film.genres)
        + 0.25 × eraAffinity[film.decade]
        + 0.15 × countryAffinity[film.country]

      avgScore = (predictedA + predictedB) / 2
      divergence = |predictedA - predictedB|
      tasteScore = avgScore - (divergence × 0.4)
      qualityBonus = (film.tmdbRating / 10) × 10
      finalScore = (tasteScore × 0.85) + (qualityBonus × 0.15)

   c. کاندیداها را بر اساس finalScore نزولی مرتب کن

   d. اگر بالاترین finalScore کمتر از 40 بود:
      → به‌جای ساخت match، یک وضعیت "no_matches" برای UI ثبت کن
        (بدون پیشنهاد فیلم بی‌کیفیت)

   e. در غیر این‌صورت، یک رکورد در matches بساز با:
      attemptNumber: 1, status: "suggested", score: finalScore,
      reason: [متن بر اساس بالاترین affinity مشترک — قالب را در
      MovieMate-Recommendation-Algorithm.md بخش ۷ ببین]

3. اگر کاربر روی attempt فعلی "Not feeling it" زد (این باید یک
   Cloud Function جدا onMatchRejected باشد که attemptNumber را ۱ واحد
   زیاد می‌کند و کاندیدای بعدی از لیست مرتب‌شده را پیشنهاد می‌دهد.
   بعد از attemptNumber=3 که رد شد، هر ۳ گزینه با هم برای انتخاب
   نهایی نمایش داده شوند (نه پیشنهاد اول).

⚠️ توجه: تمام وزن‌های بالا (۰.۶/۰.۲۵/۰.۱۵/۰.۴/۸۵٪/۱۵٪/آستانه‌ی ۴۰)
فرضیات طراحی اولیه هستند، نه اعداد نهایی. آن‌ها را به‌صورت constant
قابل‌تنظیم در یک فایل config جدا بگذار، نه hardcode پخش‌شده در کد،
تا بعداً با داده‌ی واقعی قابل tune باشند.
```

---

## 📦 Phase 5 — Notification System (FCM)

### دستور برای Codex:
```
1. FCM را در اپ Android راه‌اندازی کن:
   - در Android 13+ (API 33+)، مجوز POST_NOTIFICATIONS را در onboarding
     (بعد از صفحه‌ی Invite Partner، قبل از صفحه‌ی Waiting) درخواست کن
   - FCM token را بگیر و در users/{uid}.fcmTokens ذخیره کن
   - هر بار startup، چک کن token تغییر نکرده باشد

2. یک تابع مشترک sendNotification(userId, type, payload) در Cloud
   Functions بساز که:
   - notificationSettings کاربر را چک می‌کند قبل از ارسال
   - Frequency cap را رعایت می‌کند (حداکثر ۱ نوتیف روزانه برای موارد
     غیرضروری؛ اگر کاربر همین الان اپ را باز کرده، ارسال نکن)

3. این ۷ نوع نوتیف را با Cloud Functions جدا پیاده کن:
   - Daily Match Ready (از generateDailyMatch trigger می‌شود)
   - Partner Joined (وقتی userB پر می‌شود)
   - Partner Rated / منتظر توئه (وقتی یکی تمام کرد، دیگری نه)
   - Partner Committed ("We're in" یک‌طرفه)
   - Both Confirmed (تعهد متقابل کامل شد)
   - Scheduled Reminder (۱۵ دقیقه قبل از زمان تماشا)
   - Watchlist Activity (اضافه‌شدن دستی فیلم)

4. Deep linking سمت Android:
   payload نوتیف باید فیلد data.deepLinkTarget و data.pairId داشته باشد.
   در MainActivity، بر اساس این فیلدها با Navigation Compose به صفحه‌ی
   درست هدایت کن (هم برای cold start و هم background — هر دو حالت را
   تست کن، رفتارشان در اندروید متفاوت است).
```

---

## 📦 Phase 6 — اتصال UI موجود به داده‌ی واقعی

> پروتوتایپ‌های HTML این پروژه (که در فایل‌های جدا موجودند) باید به‌عنوان **مرجع دقیق طراحی بصری** استفاده شوند — نه کپی مستقیم HTML، بلکه بازسازی همان UI/UX در Jetpack Compose با همان design tokens (رنگ، تایپوگرافی، spacing).

### Design Tokens (از design system قفل‌شده):
```kotlin
val Ink = Color(0xFF101012)
val Blue = Color(0xFF1F2FE3)
val Lime = Color(0xFFCCE83B)
val Coral = Color(0xFFFF6A46)
val Background = Color(0xFFF1F0EC)

// Typography: Big Shoulders Display (headlines/titles/stat numbers)
//             Inter (body, meta, buttons, nav labels)

// Spacing scale: 4, 8, 12, 16, 20, 28, 36 dp
```

### ترتیب اتصال صفحات (به ترتیب وابستگی):
```
1. Onboarding (Welcome → Sign Up → Rate 10 films → Invite) 
   → اتصال به Phase 2 (Auth) + Phase 3 (TMDB) 
2. Partner join flow (Notification → Join code → Rate → Completion)
   → همان اتصالات Phase 2/3
3. Match screen (کارت پیشنهاد، دکمه‌ی We're in دوطرفه)
   → اتصال به Phase 4 (Algorithm) — خواندن/نوشتن matches collection
4. Reminder + Watched confirmation + Taste Dial rating
   → نوشتن در ratings collection، trigger کردن Phase 4 دوباره
5. Watchlist (سه‌بخشی: Ready/Waiting/Watched)
   → خواندن/نوشتن watchlist collection، جست‌وجوی TMDB واقعی
6. Us (پروفایل، streak، journey، compatibility، settings)
   → محاسبه‌ی این آمارها از matches/ratings واقعی (نه UI ثابت)
```

---

## ⚠️ نکات مهم برای Codex (خطاهای رایج که باید جلوگیری شود)

1. **هرگز rating را به‌صورت integer ۱-۵ پیاده نکن** — باید عدد پیوسته ۰-۱۰۰ باشد
2. **`aBothOnboarded` و `commitStatus` را فقط از Cloud Function (نه client مستقیم) آپدیت کن** — جلوگیری از race condition
3. **در schema، فیلم‌ها را hardcode نکن** — همه باید از TMDB واقعی (با cache) بیایند
4. **قبل از هر Cloud Function که چیزی می‌نویسد، Security Rules را چک کن** — که آیا واقعاً کلاینت اجازه‌ی این نوع نوشتن را ندارد (بعضی نوشتن‌ها باید فقط از سمت سرور باشند)
5. **وزن‌های الگوریتم را hardcode پخش‌شده نکن** — در یک فایل config جدا نگه‌دار

---

## 📎 فایل‌های پیوست که Codex باید مستقیم استفاده کند

- `firestore.rules` — فایل کامل و آماده، مستقیم deploy شود
- `MovieMate-Recommendation-Algorithm.md` — جزئیات کامل‌تر فرمول با مثال
- `MovieMate-Notification-Architecture.md` — جزئیات کامل معماری نوتیف
- `MovieMate-PRD.md` — مرجع کامل تصمیمات محصولی برای هر ابهام

---

## ✅ خروجی مورد انتظار از Codex در پایان هر Phase

هر Phase باید با یک خلاصه‌ی کوتاه تمام شود: چه فایل‌هایی ساخته شد، چه چیزی تست شد، و چه چیزی هنوز نیاز به تایید انسانی دارد (مثلاً API key واقعی، تست روی دستگاه فیزیکی).
