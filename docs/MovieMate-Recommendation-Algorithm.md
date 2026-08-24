# MovieMate — Recommendation Algorithm v2 (فرمول دقیق وزن‌دهی)

**نسخه**: v2 — جایگزین توضیح کلی قبلی ("تقاطع ژانر + شباهت رتینگ")  
**مرجع**: PRD بخش ۷.۳/۱۲ (سوال باز حل‌شده)، ALI-61، ALI-63، Backend Schema (`matches.score`)  
**نوع محتوا**: ⚠️ این یک طراحی مهندسی با وزن‌های اولیه‌ی پیشنهادی است — **نه یک فرمول اثبات‌شده**. وزن‌ها فرضیات قابل‌تست هستند و باید بعد از جمع‌آوری داده‌ی واقعی کاربر تنظیم (tune) شوند.

---

## ۱. مسئله‌ی واقعی که این الگوریتم باید حل کند

با ورود Taste Dial (۰-۱۰۰ پیوسته به‌جای ۵ ستاره‌ی گسسته)، دو کاربر معمولاً روی **فیلم‌های متفاوتی** رتینگ داده‌اند (نه فیلم‌های مشترک) — چون فیلم پیشنهادی هر روز برای هر دو یکسان است ولی رتینگ اولیه (۱۰-۱۵ فیلم onboarding) مستقل و متفاوت انتخاب می‌شود.

**نتیجه**: این یک مسئله‌ی *content-based filtering* است، نه *collaborative filtering* — چون داده‌ی رتینگ مشترک بین دو کاربر روی یک فیلم مشخص کم است، خصوصاً در روزهای اول. باید از **پروفایل سلیقه** هرکس (بر اساس ژانر/دوران/کشور که در rating‌هایشان ظاهر شده) برای پیش‌بینی امتیاز یک فیلم *جدید* (که هیچ‌کدام هنوز ندیده‌اند) استفاده کرد.

---

## ۲. مرحله ۱ — ساخت Taste Profile هر کاربر

از فیلم‌های rate‌شده‌ی هر کاربر (طبق مدل دو‌مرحله‌ای onboarding: انتخاب ژانر + سواپ ۱۲-۱۵ فیلم که ژانر/زیرژانر/دوران/کشور را پوشش می‌دهد)، یک بردار وزن‌دار ساخته می‌شود:

```
UserProfile = {
  genreAffinity: { "Sci-Fi": 78, "Drama": 65, "Comedy": 30, ... },
  eraAffinity: { "2020s": 82, "2010s": 60, "pre-2000": 25, ... },
  countryAffinity: { "US": 70, "Korea": 85, "France": 55, ... }
}
```

**نحوه‌ی محاسبه‌ی هر مقدار** (مثال برای یک ژانر g):
```
genreAffinity[g] = weighted_average(score_i)
  for every film_i the user rated where g ∈ film_i.genres
  weight = 1 (ساده، در نسخه‌ی بعد می‌تواند بر اساس recency وزن‌دار شود)
```

> این یک میانگین وزنی ساده است، نه یک مدل یادگیری‌ماشین — عمداً ساده نگه داشته شده تا در MVP قابل توضیح به کاربر باشد (چرا این پیشنهاد آمد).

---

## ۳. مرحله ۲ — پیش‌بینی امتیاز فردی برای یک فیلم کاندیدا

برای فیلم کاندیدای C (فیلمی که هیچ‌کدام هنوز rate نکرده‌اند):

```
predictedScore(user, C) =
    0.6 × avg(genreAffinity[g] for g in C.genres)
  + 0.25 × eraAffinity[C.decade]
  + 0.15 × countryAffinity[C.country]
```

**خروجی**: عددی بین ۰-۱۰۰ برای هر کاربر، جدا از هم.

---

## ۴. مرحله ۳ — ترکیب به Joint Match Score

اینجا نقطه‌ی کلیدی الگوریتم است — چون هدف "سلیقه‌ی مشترک" است، نه صرفاً میانگین:

```
avgScore = (predictedScore(A, C) + predictedScore(B, C)) / 2

divergence = |predictedScore(A, C) − predictedScore(B, C)|

divergencePenalty = divergence × 0.4   // هرچه اختلاف بیشتر، جریمه بیشتر

tasteScore = avgScore − divergencePenalty
```

**چرا جریمه‌ی اختلاف مهم است**: فیلمی که یک نفر ۹۵ و دیگری ۴۰ پیش‌بینی می‌شود، میانگینش ۶۷.۵ می‌شود که به‌ظاهر قابل قبول است — ولی این یک فیلم "سلیقه‌ی مشترک" نیست، بلکه فیلمی است که فقط یک نفر دوست دارد. جریمه‌ی اختلاف این را اصلاح می‌کند.

---

## ۵. مرحله ۴ — تعدیل کیفیت (Quality Adjustment)

```
qualityBonus = (film.tmdbRating / 10) × 10   // مقیاس‌بندی به ۰-۱۰۰، سهم کوچک

finalScore = (tasteScore × 0.85) + (qualityBonus × 0.15)
```

**چرا فقط ۱۵٪ وزن**: کیفیت عمومی TMDB نباید بر سلیقه‌ی شخصی غالب شود — یک فیلم با امتیاز پایین‌تر IMDb/TMDB ولی match بالا برای این دو نفر، باید بالاتر رتبه بگیرد. این وزن پایین عمدی است.

---

## ۶. فرمول نهایی (خلاصه)

```
finalScore = 0.85 × [ (avg(predictedA, predictedB)) − (0.4 × |predictedA − predictedB|) ]
           + 0.15 × (tmdbRating × 10)
```

خروجی: عدد ۰-۱۰۰ که در `matches.score` ذخیره می‌شود و همان چیزی است که در UI به شکل «۹۸% shared taste» نمایش داده می‌شود.

---

## ۷. تولید متن دلیل (Reason Text)

قالب ساده بر پایه‌ی بالاترین‌amount مشترک:
```
اگر بالاترین genreAffinity مشترک > 70:
   "Both of you love {genre} with {secondary trait}"
وگرنه اگر eraAffinity مشترک بالا بود:
   "You both gravitate toward {decade} films"
وگرنه:
   "A pick that fits both your tastes"
```

---

## ۸. آستانه‌ها و منطق fallback (اتصال به تصمیم قبلی — ALI-76)

```
candidatePool = تمام فیلم‌هایی که هیچ‌کدام rate/watch نکرده‌اند
sortedCandidates = مرتب‌شده بر اساس finalScore نزولی

اگر sortedCandidates[0].finalScore < 40:
   → سناریوی "No Matches" (ALI-73) فعال می‌شود؛ به‌جای فیلم، پیام تشویقی برای rating بیشتر نمایش داده می‌شود

وگرنه:
   attempt 1 → sortedCandidates[0]
   اگر رد شد: attempt 2 → sortedCandidates[1]
   اگر رد شد: attempt 3 → sortedCandidates[2]
   اگر رد شد: صفحه‌ی fallback هر ۳ گزینه را نشان می‌دهد (طبق تصمیم قبلی، نه هم‌زمان از ابتدا)
```

---

## ۹. Pseudocode برای Cloud Function `generateDailyMatch`

```javascript
async function generateDailyMatch(pairId) {
  const pair = await getPair(pairId);
  if (!pair.aBothOnboarded) return; // پیش‌شرط طبق ALI-62

  const profileA = buildTasteProfile(pair.userA);
  const profileB = buildTasteProfile(pair.userB);

  const candidates = await getUnratedCandidates(pair); // از filmCache + TMDB discover

  const scored = candidates.map(film => {
    const predA = predictScore(profileA, film);
    const predB = predictScore(profileB, film);
    const divergence = Math.abs(predA - predB);
    const tasteScore = (predA + predB) / 2 - (divergence * 0.4);
    const qualityBonus = (film.tmdbRating / 10) * 10;
    const finalScore = (tasteScore * 0.85) + (qualityBonus * 0.15);
    return { film, finalScore, reason: buildReason(profileA, profileB, film) };
  }).sort((a, b) => b.finalScore - a.finalScore);

  if (scored[0].finalScore < 40) {
    await triggerNoMatchesScenario(pairId);
    return;
  }

  await createMatchDoc(pairId, scored[0], attemptNumber = 1);
}
```

---

## ۱۰. ⚠️ فرضیات صریح که هنوز تست نشده‌اند

| فرض | باید چطور validate شود |
|---|---|
| وزن‌های ۰.۶/۰.۲۵/۰.۱۵ برای ژانر/دوران/کشور | بعد از جمع‌آوری داده‌ی واقعی، با آزمایش وزن‌های مختلف و سنجش نرخ "We're in" مقایسه شود |
| ضریب جریمه‌ی اختلاف (۰.۴) | باید تست شود که آیا این ضریب باعث حذف بیش‌ازحد گزینه‌های خوب می‌شود یا نه |
| آستانه‌ی ۴۰ برای "No Matches" | باید با نرخ واقعی مواجهه‌ی کاربران با این سناریو تنظیم شود — ممکن است خیلی سخت‌گیرانه یا خیلی سهل‌گیرانه باشد |
| وزن ۱۵٪ برای کیفیت TMDB | فرض طراحی، نه داده — ممکن است کاربران کیفیت کلی را بیشتر از این ارزش بدهند |

---

## ۱۱. محدودیت شناخته‌شده (Cold Start)

در روزهای اول (قبل از این‌که کاربر فیلم‌های زیادی rate کند)، `UserProfile` ناقص است و پیش‌بینی‌ها نویزی خواهند بود. این یک محدودیت ذاتی content-based filtering با داده‌ی کم است — راه‌حل بلندمدت (خارج از scope v1): افزودن سیگنال‌های TMDB Keywords/Cast برای غنی‌سازی پروفایل با داده‌ی کمتر.
