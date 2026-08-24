# MovieMate — Backend Architecture: Firebase Auth + Firestore Schema

**نسخه**: v1  
**مرجع**: PRD v2.1، ALI-62 (لایه فنی)، ALI-63 (الگوریتم)، ALI-77 (تعهد متقابل)، ALI-61 (TMDB)  
**هدف**: طراحی دقیق schema برای شروع پیاده‌سازی — نه فقط مفهومی، بلکه با field-level detail

---

## ۱. Firebase Auth

### تصمیم قفل‌شده (طبق PRD بخش ۱۲)
- **فقط Email/Password در v1** — Social login (Google/Apple) هنوز open question است و به v1.5/v2 موکول شده
- هر کاربر یک Firebase Auth UID مستقل دارد، حتی اگر عضو یک pair باشد

### چرا نه Social Login در v1
کاهش پیچیدگی اولیه — یک flow واحد auth یعنی تست/دیباگ ساده‌تر برای MVP. این محدودیت دائمی نیست.

### Auth Rules (خلاصه)
```
- ایمیل باید verify شود قبل از دسترسی به rating/matching (جلوگیری از حساب‌های جعلی)
- Reset password استاندارد Firebase
- هیچ داده‌ی حساس (رمز عبور و غیره) خارج از Firebase Auth ذخیره نمی‌شود
```

---

## ۲. Firestore Collections — طراحی کامل

### 2.1 — `users`
اطلاعات فردی هر کاربر (مستقل از pair).

```typescript
users/{userId}
{
  uid: string,                    // = Firebase Auth UID
  name: string,
  email: string,
  emailVerified: boolean,
  createdAt: Timestamp,
  pairId: string | null,          // اشاره به pair فعلی (null اگر هنوز جفت نشده)
  onboardingComplete: boolean,    // آیا ۱۰ فیلم اولیه rate کرده؟
  ratingCount: number,            // تعداد کل rating (برای چک سریع بدون query جدا)
  notificationSettings: {
    dailyMatch: boolean,
    partnerActivity: boolean,
    reminders: boolean
  }
}
```

**چرا `pairId` روی خود user، نه فقط داخل pair؟**  
برای این‌که بدون query اضافه بشه فهمید کاربر عضو کدوم pair است — این یک تصمیم عملکردی (performance) است، نه فقط مدل‌سازی داده.

---

### 2.2 — `pairs`
پروفایل مشترک دو‌نفره — هسته‌ی اصلی مدل داده.

```typescript
pairs/{pairId}
{
  userA: string,                  // uid نفر اول (دعوت‌کننده)
  userB: string | null,           // uid نفر دوم (null تا زمان join)
  inviteCode: string,             // مثل "MVMT-247"
  inviteCodeExpiresAt: Timestamp, // انقضای کد (طبق ALI-73: کد بعد از ۷ روز منقضی می‌شود)
  status: "waiting_partner" | "both_rating" | "active",
  createdAt: Timestamp,
  aBothOnboarded: boolean,        // true وقتی هر دو ۱۰ فیلم rate کردند — trigger اصلی "match ready"
  streakCount: number,            // روزهای متوالی rating مشترک
  lastMatchGeneratedAt: Timestamp
}
```

**نکته‌ی حیاتی معماری**: فیلد `aBothOnboarded` دقیقاً همان چیزی است که در ALI-77 به‌عنوان پیش‌شرط notification "Today's match is ready" مستند شده بود. این فیلد باید توسط یک **Cloud Function trigger** (نه client-side) به‌روزرسانی شود، وگرنه race condition ممکن است رخ دهد (هر دو کاربر همزمان rating نهایی را بفرستند).

---

### 2.3 — `ratings`
رتینگ هر کاربر روی هر فیلم — subcollection زیر pair برای query راحت‌تر.

```typescript
pairs/{pairId}/ratings/{ratingId}
{
  userId: string,
  filmId: string,                 // TMDB film ID
  score: number,                  // 0-100، طبق تصمیم Taste Dial (نه 1-5!)
  isInitialOnboarding: boolean,   // آیا بخشی از ۱۰ فیلم اولیه بوده یا post-watch؟
  reactionEmoji: string | null,   // اختیاری، مکمل Taste Dial
  ratedAt: Timestamp
}
```

**⚠️ خطای رایج که باید جلویش گرفته شود**: اگر تیم dev از عادت قبلی (۵ ستاره) پیروی کند و فیلد را `integer 1-5` بسازد، کل الگوریتم recommendation باید دوباره نوشته شود. این باید در code review اولیه صراحتاً چک شود.

---

### 2.4 — `matches`
نتیجه‌ی الگوریتم recommendation + وضعیت تعهد متقابل.

```typescript
pairs/{pairId}/matches/{matchId}
{
  filmId: string,
  score: number,                  // امتیاز تطابق محاسبه‌شده توسط الگوریتم (٪)
  reason: string,                 // متن توضیح، مثل "Both love sci-fi"
  suggestedAt: Timestamp,
  status: "suggested" | "watched" | "dismissed",
  attemptNumber: number,          // 1, 2, یا 3 — طبق منطق fallback (ALI-76: یکی‌یکی، نه ۳تایی)
  commitStatus: {
    userA: boolean,
    userB: boolean
  },
  bothConfirmedAt: Timestamp | null,  // زمانی که commitStatus هر دو true شد — این معیار "Match واقعی" در گزارش‌هاست
  watchedConfirmedAt: Timestamp | null // زمان تایید دستی "We watched it"
}
```

**اتصال مستقیم به تصمیم امروز**: فیلد `bothConfirmedAt` دقیقاً معیاریست که در گزارش "Us" برای شمارش "Matches — both confirmed" استفاده می‌شود. **فقط matchهایی که این فیلد را پر دارند** در آمار شمرده می‌شوند — نه هر رکورد `suggested`.

---

### 2.5 — `watchlist`
طبق ۶ ویژگی که در طراحی UI قفل شد (بخش ۷.۴ PRD).

```typescript
pairs/{pairId}/watchlist/{itemId}
{
  filmId: string,
  addedBy: string,                // uid — برای نمایش "Sara added this"
  addedAt: Timestamp,
  source: "match" | "manual_search",  // از پیشنهاد روزانه اومده یا جست‌وجوی دستی؟
  status: "waiting" | "ready" | "watched",
  commitStatus: {
    userA: boolean,
    userB: boolean
  },
  watchedAt: Timestamp | null,
  mutualScore: number | null      // بعد از watched، میانگین دو Taste Dial — برای مرتب‌سازی
}
```

---

### 2.6 — `filmCache` (متادیتای TMDB، طبق تصمیم امروز)

```typescript
filmCache/{filmId}
{
  tmdbId: string,
  title: string,
  posterPath: string,
  genres: string[],
  releaseYear: number,
  runtime: number,
  overview: string,
  tmdbRating: number,
  cachedAt: Timestamp,             // برای invalidation
  expiresAt: Timestamp             // = cachedAt + 6 months (الزام TMDB ToU)
}
```

**⚠️ الزام قانونی، نه انتخاب معماری**: طبق تصمیم امروز (ALI-61)، `expiresAt` باید دقیقاً ۶ ماه بعد از `cachedAt` باشد و یک Cloud Function زمان‌بندی‌شده باید رکوردهای منقضی را حذف/رفرش کند.

---

## ۳. Cloud Functions — نقاط کلیدی منطق سمت سرور

این‌ها **نباید** روی client پیاده‌سازی شوند (چون هماهنگی بین دو کاربر نیاز به منبع واحد حقیقت دارد):

| Function | Trigger | کار |
|---|---|---|
| `onRatingComplete` | نوشتن rating جدید | چک می‌کند آیا کاربر به ۱۰ رسیده؛ اگر هر دو رسیدند، `pairs.aBothOnboarded = true` |
| `generateDailyMatch` | Scheduled، هر روز ساعت ۹ صبح (به‌وقت محلی pair) | فقط برای pairsی که `aBothOnboarded == true` هستند اجرا می‌شود |
| `onCommitUpdate` | نوشتن commitStatus | اگر هر دو true شدند، `bothConfirmedAt` را ست می‌کند و notification reminder را trigger می‌کند |
| `refreshTmdbCache` | Scheduled، روزانه | رکوردهای `filmCache` که `expiresAt` گذشته را حذف/رفرش می‌کند |
| `expireInviteCode` | Scheduled | کدهای دعوت بعد از ۷ روز (طبق ALI-73) غیرفعال می‌شوند |

---

## ۴. Security Rules — اصول کلی (فایل جدا: `firestore.rules`)

- هر کاربر فقط می‌تواند داده‌ی `pairs/{pairId}` خودش را بخواند/بنویسد (چک با `userA`/`userB` در Auth token یا custom claims)
- `ratings` فقط توسط صاحب همان `userId` قابل نوشتن است، نه توسط partner
- `commitStatus.userA` فقط توسط userA قابل تغییر است، `commitStatus.userB` فقط توسط userB — جلوگیری از این‌که یک نفر برای هر دو تایید بزند
- `filmCache` فقط read برای کاربران احراز‌هویت‌شده؛ write فقط از طریق Cloud Functions (service account)

---

## ۵. سوالات باز باقی‌مانده (از PRD، هنوز مرتبط با این لایه)

| سوال | اثر روی این schema |
|---|---|
| رفتار آفلاین برای rating؟ | اگر بله، نیاز به local queue + conflict resolution در sync |
| فرمول دقیق وزن‌دهی الگوریتم با ورودی ۰-۱۰۰ | مستقیماً روی نحوه‌ی محاسبه‌ی `matches.score` اثر دارد — باید قبل از نوشتن Cloud Function نهایی شود |

---

## ۶. Definition of Done — این لایه

- [ ] هر ۶ collection بالا در Firestore ساخته و با mock data تست شده
- [ ] Security rules نوشته و با Firebase Emulator تست شده (شامل تست منفی: کاربر نتواند برای partner خودش commit بزند)
- [ ] Cloud Function `onRatingComplete` تست‌شده با سناریوی race condition (هر دو کاربر همزمان rating نهایی بفرستند)
- [ ] TMDB cache expiry (۶ ماه) با تست زمان‌شبیه‌سازی‌شده تایید شده
