# MovieMate — Design System

**Version**: 9.0.0
**Status**: 🟢 Active — extensible by design (v8 was marked "locked", which is why it drifted)
**Source of truth**: [`design/tokens.json`](../design/tokens.json)
**Validator**: `node design/validate-tokens.mjs`

> **How to read this document.** This is the *contract*, not the values. Concrete
> values live in `design/tokens.json` and are mirrored into Kotlin. If this
> document and the token file ever disagree, the token file wins and this
> document is the thing that needs fixing.

---

## ۱. چرا این سند بازنویسی شد

نسخه‌ی v8 «قفل‌شده» علامت خورده بود. قفل کردن یک دیزاین‌سیستم آن را ثابت نگه نمی‌دارد — فقط
تغییرات را غیررسمی می‌کند. نتیجه‌اش این شد که پروتوتایپ‌ها قوانین سند را نقض کردند
(دکمه‌ی مرجانی) و یک رنگ زیر آستانه‌ی دسترسی‌پذیری ماند بدون اینکه کسی متوجه شود.

v9 به‌جای قفل، **ساختار** دارد: سه لایه‌ی توکن، قرارداد نام‌گذاری، و یک اسکریپت که
قوانین را اجرا می‌کند. تغییر دادن سیستم حالا یک عملیات تعریف‌شده است، نه یک تخطی.

---

## ۲. معماری توکن — سه لایه

هر مقدار بصری در اپ دقیقاً در یکی از این سه لایه زندگی می‌کند.

```
┌─────────────────────────────────────────────────────────────┐
│  ref.*    TIER 1 — Primitives                               │
│           مقدار خام. نام خنثی و توصیفی: blue.600            │
│           هیچ کامپوننتی اجازه ندارد مستقیم به اینجا برسد.    │
└────────────────────────┬────────────────────────────────────┘
                         │ فقط لایه‌ی بعد به اینجا اشاره می‌کند
┌────────────────────────▼────────────────────────────────────┐
│  sys.*    TIER 2 — Semantic roles                           │
│           نقش، نه مقدار: text.accent, action.reward.fill    │
│           ✅ تنها لایه‌ای که صفحه/کامپوننت می‌خواند.          │
│           هر نقش برای هر تم جدا تعریف می‌شود.                │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│  comp.*   TIER 3 — Component tokens                         │
│           فقط برای پیچی که یک کامپوننت واقعاً مالکش است.     │
│           بیشتر کامپوننت‌ها اینجا چیزی ندارند.               │
└─────────────────────────────────────────────────────────────┘
```

### چرا این تفکیک ارزش دارد

وارونه کردن کل اپ از روشن به تیره در v9، **دو فایل** را لمس کرد
(`Tokens.kt` و `Semantic.kt`). هیچ صفحه و هیچ کامپوننتی تغییر نکرد. اگر رنگ‌ها
مستقیم در کامپوننت‌ها بودند، همان تغییر یک بازنویسی سراسری بود.

### قانون سخت

> یک کامپوننت که رنگی لازم دارد و آن رنگ در `sys.*` نقشی ندارد،
> **یک نقش کم دارد** — نه اینکه باید به primitive دست بزند.
> نقش را اضافه کن، در هر دو تم تعریفش کن، validator را بزن.

---

## ۳. قرارداد نام‌گذاری

### Primitives (`ref.*`)
نام **توصیفی و خنثی**، هرگز نقش‌محور.

| ✅ درست | ❌ غلط | چرا |
|---|---|---|
| `blue.600` | `primaryColor` | نقش عوض می‌شود، رنگ نه |
| `ink.700` | `mutedText` | همین primitive ممکن است جای دیگر نقش دیگری بگیرد |
| `dimension.4` | `cardPadding` | یک عدد، چند کاربرد |

اعداد فاصله‌دار (`100`, `200`, …) انتخاب شده‌اند تا بشود بینشان قدم اضافه کرد
بدون شماره‌گذاری مجدد.

### Semantic roles (`sys.<theme>.<group>.<role>`)
نام از **کاری که می‌کند** می‌آید، نه از شکلش.

```
sys.dark.text.accent          ← "متن آبی"، نه "#7C89FF"
sys.dark.action.reward.fill   ← "پرکننده‌ی دکمه‌ی پاداش"
sys.dark.surface.raised       ← "سطح بالاآمده"
```

گروه‌های مجاز: `surface`, `border`, `text`, `action`, `status`, `partner`.
گروه جدید فقط وقتی که واقعاً یک دسته‌ی مفهومی تازه باشد.

### Component tokens (`comp.<component>.<property>`)
```
comp.tasteDial.trackHeight
comp.sharedAxis.pinSize
```

---

## ۴. رنگ

### ۴.۱ تم‌ها

**تیره پیش‌فرض است.** این یک تصمیم کارکردی است، نه مد: پوستر فیلم برای زمینه‌ی تیره
ساخته می‌شود و محتوای اصلی این اپ پوستر است. زمینه‌ی خاکستری گرم با تک‌تک پوسترها می‌جنگد.

تم روشن **نگهداری می‌شود، منسوخ نیست**. هویت v8 هنوز کامل resolve می‌شود؛ فقط نگاشت
نقش‌ها فرق دارد. validator اجبار می‌کند که هر دو تم دقیقاً همان نقش‌ها را داشته باشند،
پس یک کامپوننت هرگز در یک تم نقشی را گم نمی‌کند.

### ۴.۲ آبی دو نقش دارد

مهم‌ترین نکته‌ی پالت v9:

| نقش | مقدار | چرا |
|---|---|---|
| `action.primary.fill` | `blue.600` `#1F2FE3` | سفید رویش **۸.۱۶:۱** |
| `text.accent` (تیره) | `blue.400` `#7C89FF` | **۶.۲۷:۱** روی زمینه |

آبی برند روی زمینه‌ی تیره **۲.۳۵:۱** است — برای متن و آیکون نامرئی. تفکیک این دو نقش
اجباری بود، نه سلیقه‌ای. استفاده از `blue.600` برای متن روی تیره یک باگ دسترسی‌پذیری است.

### ۴.۳ قانون مرجانی

مرجانی `#FF6A46` **تزئینی و وضعیتی است — هرگز پرکننده‌ی پشت متن تعاملی**.

این قانون در v8 هم بود، ولی پروتوتایپ خودش آن را می‌شکست: `.cta-btn.coral` سفید روی
مرجانی می‌گذاشت که **۲.۸۴:۱** است و مردود. در v9 اصلاً `CtaTone.Coral` وجود ندارد —
قانون در type system اجرا می‌شود، نه فقط در متن سند.

### ۴.۴ قانون لیمویی

لیمویی `#CCE83B` **فقط برای تکمیل** است: تعهد متقابل کامل شد، «We watched it»،
بهترین هفته. در v8 روی هر تگی خرج می‌شد و برای همین به‌عنوان تزئین خوانده می‌شد.
یک accent که همه‌جا هست، accent نیست.

### ۴.۵ رنگ شریک‌ها

`partner.a` و `partner.b` **ثابت‌اند** — یک رنگ همیشه یک آدم است، در همه‌ی صفحات.
این چیزی است که محور سلیقه‌ی مشترک را قابل خواندن می‌کند.

### ۴.۶ کدام رنگ کجا — جدول مرجع

این جدول **از `tokens.json` تولید می‌شود**. دستی ویرایشش نکنید؛ فیلد `$usage` را
در توکن عوض کنید و `node design/gen-usage-table.mjs` را بزنید.

validator اجبار می‌کند هر رنگ معنایی یک `$usage` داشته باشد — پس این جدول نمی‌تواند
ناقص بماند.

<!-- AUTOGENERATED:color-usage -->

<!-- Generated by design/gen-usage-table.mjs from design/tokens.json.
     Do not edit by hand — change the `$usage` field in tokens.json. -->

#### Surfaces — what a thing sits on

| Kotlin | Dark | Light | کجا استفاده می‌شود |
|---|---|---|---|
| `colors.surfaceGround` | `#0F0F12` | `#F1F0EC` | App background on every screen. The scaffold behind everything. |
| `colors.surfaceRaised` | `#17171B` | `#FFFFFF` | Cards, list rows, sheets, the bottom nav bar. |
| `colors.surfaceSunken` | `#1F1F25` | `#EFEEE9` | Taste Dial track, shared-axis track, pressed insets. |
| `colors.surfaceAccent` | `#1B1E3A` | `#E4E7FB` | Active bottom-nav pill; the 'waiting on partner' strip. |
| `colors.surfaceWarning` | `#1C1A12` | `#EFEEE9` | A Watchlist row in 'Waiting on you' — an outstanding task. |

#### Borders

| Kotlin | Dark | Light | کجا استفاده می‌شود |
|---|---|---|---|
| `colors.borderHairline` | `#2A2A32` | `#DAD8D0` | Card outlines, nav bar edge, divider between stat cells. |
| `colors.borderAccent` | `#2A3059` | `#E4E7FB` | Edge of the 'waiting on partner' strip. |
| `colors.borderWarning` | `#3A3520` | `#DAD8D0` | Edge of a 'Waiting on you' Watchlist row. |

#### Text

| Kotlin | Dark | Light | کجا استفاده می‌شود |
|---|---|---|---|
| `colors.textPrimary` | `#F1F0EC` | `#101012` | Film titles, headlines, body copy, stat numbers that are not accented. |
| `colors.textSecondary` | `#9B9B96` | `#696963` | Meta lines: '2021 · Sci-Fi', 'Sara added this', nav labels when inactive. |
| `colors.textAccent` | `#7C89FF` | `#1F2FE3` | Match score, active nav label + icon, links. NOT the button fill. |
| `colors.textReward` | `#CCE83B` | `#101012` | 'Ready to watch' group label; a completed-state figure. |
| `colors.textOnFill` | `#FFFFFF` | `#FFFFFF` | Label inside a primary button. |
| `colors.textOnReward` | `#101012` | `#101012` | Label inside a reward button, and inside a lime tag. |

#### Actions — buttons and controls

| Kotlin | Dark | Light | کجا استفاده می‌شود |
|---|---|---|---|
| `colors.actionPrimaryFill` | `#1F2FE3` | `#1F2FE3` | Primary CTA background; the blue pill tag. |
| `colors.actionPrimaryHover` | `#2937F0` | `#2937F0` | Primary CTA, hovered. |
| `colors.actionPrimaryPressed` | `#1826B8` | `#1826B8` | Primary CTA, pressed. |
| `colors.actionRewardFill` | `#CCE83B` | `#CCE83B` | 'We watched it', 'I'm in too', the lime tag, a filled commit ring. |
| `colors.actionRewardHover` | `#D6F04E` | `#D6F04E` | Reward CTA, hovered. |
| `colors.actionQuietBorder` | `#2A2A32` | `#DAD8D0` | Outline of a secondary/quiet button. |
| `colors.actionQuietText` | `#9B9B96` | `#696963` | Label of a secondary/quiet button; 'Not feeling it'. |
| `colors.actionFocusRing` | `#CCE83B` | `#1826B8` | Keyboard focus ring on any interactive element. |

#### Status — what state something is in

| Kotlin | Dark | Light | کجا استفاده می‌شود |
|---|---|---|---|
| `colors.statusDecorative` | `#FF6A46` | `#FF6A46` | 'Waiting on you' group label; ornamental dot clusters. Never a fill behind text. |
| `colors.statusCommitted` | `#CCE83B` | `#101012` | A commit ring that is filled; the checkmark badge. |
| `colors.statusPending` | `#2A2A32` | `#DAD8D0` | A commit ring that is empty; an unfilled progress step. |

#### Partner identity

| Kotlin | Dark | Light | کجا استفاده می‌شود |
|---|---|---|---|
| `colors.partnerA` | `#7C89FF` | `#1F2FE3` | Everything belonging to userA: their avatar ring, their pin on the shared axis. |
| `colors.partnerB` | `#CCE83B` | `#101012` | Everything belonging to userB: their avatar ring, their pin on the shared axis. |

<!-- /AUTOGENERATED:color-usage -->

---

## ۵. تایپوگرافی

دو خانواده، با تفکیک سخت:

- **Big Shoulders Display** (700/800/900) — فقط: هدلاین بزرگ، عنوان فیلم، عدد آماری
- **Inter** (400–800) — همه‌چیز دیگر، از جمله **متن دکمه**

### چرا Inter روی دکمه
قبلاً Archivo Black (خیلی عریض) و Anton (در سایز کوچک ناخوانا) امتحان شد.
Big Shoulders چون واقعاً condensed طراحی شده تا ۱۲sp خوانا می‌ماند — ولی روی دکمه
هنوز به Inter می‌بازد.

### استایل‌های تایپ رنگ ندارند

نقش‌های تایپ در `MovieMateType` **هیچ رنگی حمل نمی‌کنند**. رنگ یک نقش جداست و در
محل استفاده از `MovieMateTheme.colors` می‌آید.

```kotlin
Text(
    text = "98",
    style = MovieMateType.statNumber,   // فرم
    color = colors.textAccent,          // نقش رنگ
)
```

بیکینگ رنگ داخل استایل تایپ همان چیزی است که به‌محض وجود تم دوم، شما را مجبور به
ساخت یک ست استایل کامل دوم می‌کند.

### مقیاس

نقش‌ها با نام صدا زده می‌شوند، نه با اندازه. یک صفحه هرگز `34.sp` نمی‌خواهد؛
`MovieMateType.filmTitle` می‌خواهد.

---

## ۶. فاصله‌گذاری و شکل

پایه: **گرید ۴px**. validator هر مقداری خارج از این گرید را رد می‌کند.

فاصله‌ها با **قصد** نام‌گذاری شده‌اند، نه اندازه:

```kotlin
Space.inlineTight    // دو آیتم روی یک خط، فشرده
Space.inline         // دو آیتم روی یک خط
Space.stackTight     // آیتم‌های روی‌هم داخل یک گروه
Space.stack          // بین گروه‌ها
Space.screenGutter   // حاشیه‌ی چپ/راست صفحه
Space.sectionGap     // بین بخش‌های اصلی
Space.screenTop      // بالای اولین عنصر
```

این یعنی «فاصله‌ی بین بلوک‌های روی‌هم» یک‌جا قابل تنظیم است.

### رادیوس

| نام | مقدار | کجا |
|---|---|---|
| `Radius.chip` | 12dp | چیپ کوچک درون‌خطی |
| `Radius.card` | 24dp | کارت استاندارد |
| `Radius.hero` | 28dp | کارت بزرگ |
| `Radius.pill` | 999dp | دکمه، تگ، آواتار، نوار ناوبری |

---

## ۷. نسبت تصویر — اصلاح‌شده در v9

| توکن | نسبت | کاربرد |
|---|---|---|
| `ratio.posterHero` | **۴:۵** | کارت match |
| `ratio.posterThumb` | **۲:۳** | تصویر بندانگشتی لیست — نسبت واقعی پوستر |
| `ratio.still` | ۱۶:۱۰ | فقط استیل افقی |

**چه چیزی عوض شد**: v8 نسبت ۱۶:۱۰ را برای پوستر تعیین کرده بود. پوستر فیلم عمودی
است؛ قاب افقی یا بد کراپ می‌کند یا نصف کارت را letterbox می‌کند. ۱۶:۱۰ فقط جایی
می‌ماند که واقعاً استیل نمایش داده می‌شود.

---

## ۸. کامپوننت‌ها

### نوار ناوبری پایین — قانونی که نباید شکسته شود

هر سه آیتم **یک ساختار عمودی یکسان** دارند: آیکون بالای لیبل، وسط‌چین.
آیتم فعال **فقط** با `surfaceAccent` پشتش و `textAccent` رویش فرق دارد —
چیدمانش هرگز عوض نمی‌شود.

یک پیش‌نویس اولیه‌ی v8 آیتم فعال را افقی می‌چید و بقیه را عمودی. این باگ بود.
ساختار فعلی در `BottomNav.kt` طوری نوشته شده که برگرداندن آن باگ نیاز به
بازنویسی کامپوننت دارد، نه یک تغییر تصادفی.

### محور سلیقه‌ی مشترک — کامپوننت امضا

دو شریک روی **یک خط** پین می‌شوند و باند بینشان **همان** توافق است.

دو عدد جدا همان داده را نشان می‌دهد و داستان دیگری می‌گوید. سلیقه‌ی مشترک
خودِ محصول است، پس یک محور می‌گیرد.

### Taste Dial

پیوسته **۰ تا ۱۰۰**. هرگز پنج ستاره‌ی گسسته.
کل موتور recommendation این را به‌عنوان float می‌خواند؛ تبدیلش به عدد صحیح ۱-۵
هر پروفایل سلیقه در سیستم را بی‌اعتبار می‌کند.

### آیکون‌ها

SVG خطی، گرید ۲۴×۲۴، stroke ۱.۷۵–۲px، گوشه‌های گرد.
**ایموجی هرگز آیکون اصلی UI نیست** — فقط در واکنش سریع اختیاری بعد از تماشا.

> 🔴 **کار باقی‌مانده**: ست آیکون سفارشی هنوز ساخته نشده. کد فعلاً از آیکون‌های
> Material به‌عنوان جایگزین موقت استفاده می‌کند.

---

## ۹. حرکت

هر میکرو-اینتراکشن **زیر ۳۰۰ms** تمام می‌شود.

| توکن | مقدار | کجا |
|---|---|---|
| `Motion.instantMs` | 120ms | تغییر رنگ، fade |
| `Motion.quickMs` | 160ms | فشردن دکمه |
| `Motion.settleMs` | 280ms | نشستن کارت، ورود چک‌مارک |
| `Motion.pressScale` | 0.97 | مقیاس هنگام فشردن |

همه‌ی انیمیشن‌ها باید `prefers-reduced-motion` را محترم بشمارند.

### دانه‌ی فیلم (Grain)

`opacity.grain = 0.04`. کارهای امروزی ۳–۵٪ استفاده می‌کنند تا گرما بدهد؛
۲۰٪ شبیه فیلتر ۲۰۱۴ می‌شود. کارش این است که نگذارد سطوح تیره‌ی بزرگ مرده به‌نظر برسند.

---

## ۹.۵ دسترسی‌پذیری — اجراشده، نه توصیه‌شده

| معیار | آستانه |
|---|---|
| متن معمولی | ۴.۵:۱ (WCAG 2.2 AA) |
| متن بزرگ و اجزای UI | ۳:۱ |
| حداقل هدف لمسی | ۴۸dp |

هر توکن رنگی که فیلد `$on` دارد، توسط validator سنجیده می‌شود. اضافه کردن نقشی که
آستانه را رد نکند، اسکریپت را با exit code ۱ متوقف می‌کند.

---

## ۱۰. گرید، چیدمان و رفتار واکنش‌گرا

### ۱۰.۱ دو گرید متفاوت

اشتباه رایج: یکی‌گرفتن این دو.

| گرید | مقدار | کارش |
|---|---|---|
| **پایه‌ی فاصله** | ۴dp | هر padding/margin/gap مضربی از این است. validator اجبارش می‌کند. |
| **گرید چیدمان** | ۴ / ۸ / ۱۲ ستون | ساختار صفحه، بسته به کلاس اندازه‌ی پنجره |

### ۱۰.۲ کلاس‌های اندازه‌ی پنجره

از استاندارد Material 3 پیروی می‌کنیم تا اپ با پلتفرم هم‌زبان باشد، نه اینکه
breakpoint اختراع کند.

| کلاس | عرض | دستگاه | ستون | gutter |
|---|---|---|---|---|
| `compact` | < 600dp | گوشی عمودی | ۴ | 16dp |
| `medium` | ≥ 600dp | تاشوی بازشده، تبلت عمودی | ۸ | 24dp |
| `expanded` | ≥ 840dp | تبلت افقی | ۱۲ | 24dp |

### ۱۰.۳ رفتار واکنش‌گرا

**v1 فقط `compact` را می‌سازد.** دو کلاس دیگر تعریف شده‌اند تا اولین تاشو یک
*تغییر چیدمان* باشد، نه یک بازطراحی.

- **compact** — تک‌ستونی، نوار ناوبری پایین، اکشن اصلی در یک‌سوم پایینی صفحه
  (دسترس شست)
- **medium** — محتوا در `Layout.contentMaxWidth` (۶۰۰dp) محدود و وسط‌چین می‌شود؛
  نوار پایین به navigation rail کناری تبدیل می‌شود
- **expanded** — دو پنل: لیست چپ، جزئیات راست. 🔴 **ساخته نشده** — فقط ثبت شده که
  تصمیمش گرفته شده باشد

> چرا `contentMaxWidth`: متن روی کل عرض تبلت خط‌های ۱۲۰ کاراکتری می‌سازد که
> خواندنشان سخت است. سقف عرض یعنی خط خواندنی می‌ماند.

---

## ۱۱. ارتفاع (Elevation)

روی UI تیره، عمق بیشتر از سایه با **روشنایی سطح** خوانده می‌شود — سایه‌ی مشکی روی
زمینه‌ی نزدیک‌به‌مشکی نامرئی است. پس هر سطح یک سطح رنگی دارد و سایه فقط تقویت‌کننده است.

| نقش | سایه | سطح | کجا |
|---|---|---|---|
| `Elevation.flat` | 0dp | `surfaceGround` | هم‌سطح زمینه |
| `Elevation.card` | 1dp | `surfaceRaised` | کارت، ردیف Watchlist |
| `Elevation.sheet` | 8dp | `surfaceRaised` | bottom sheet، منو |
| `Elevation.floating` | 16dp | `surfaceRaised` | نوار ناوبری شناور، دیالوگ |

**نه هر چیزی کارت است.** حاشیه، پرکننده، رادیوس و سایه هرکدام می‌گویند «شیء جدا».
خرج کردن همه‌شان روی هر بلوک، سلسله‌مراتب را صاف می‌کند.

---

## ۱۲. حاشیه (Border)

ضخامت و رنگ **دو نقش جدا** هستند. ضخامت از `BorderWidth`، رنگ از `colors.border*`.

| نقش | ضخامت | کجا |
|---|---|---|
| `BorderWidth.divider` | 1dp | بین سلول‌های آماری، بین بخش‌های لیست |
| `BorderWidth.container` | 1dp | خط دور کارت و نوار ناوبری |
| `BorderWidth.input` | 1.5dp | فیلد ورودی |
| `BorderWidth.commitRing` | 2.5dp | رینگ آواتار — نشان‌دهنده‌ی تعهد |
| `BorderWidth.focus` | 2dp | نشانگر فوکوس |

---

## ۱۳. آیکون و تصویرسازی

### ۱۳.۱ آیکون

SVG خطی، گرید ۲۴×۲۴، stroke ۱.۷۵–۲dp، `round` برای cap و join.

| نقش | اندازه | کجا |
|---|---|---|
| `IconSize.inline` | 16dp | داخل یک خط meta |
| `IconSize.nav` | 21dp | نوار ناوبری |
| `IconSize.default` | 24dp | همه‌جای دیگر |
| `IconSize.feature` | 32dp | حالت خالی |

**ایموجی هرگز آیکون UI نیست** — فقط واکنش سریع اختیاری بعد از تماشا.

> 🔴 **کار باقی‌مانده**: ست سفارشی ساخته نشده. کد فعلاً آیکون Material استفاده می‌کند.

### ۱۳.۲ تصویرسازی

قانون است، نه کتابخانه‌ی asset. v1 به کتابخانه‌ی تصویرسازی نیاز ندارد.

| مورد | قانون |
|---|---|
| پوستر در حال بارگذاری / موجود‌نبودن | بلوک گرادیانت مشتق از ژانر غالب — **نه** مربع خاکستری، **نه** آیکون تصویر شکسته |
| حالت خالی | تایپوگرافیک، نه تصویرسازی‌شده. «No matches» همان وزن فونت نمایشی یک روز خوب را می‌گیرد، چون یک وضعیت واقعی است نه خطا |
| stock / ایموجی به‌عنوان تصویر | ممنوع |

---

## ۱۴. تراکم (Density) و فوکوس

### تراکم

| نقش | ارتفاع | کجا |
|---|---|---|
| `Density.rowDefault` | 72dp | ردیف‌های Ready / Waiting |
| `Density.rowCompact` | 56dp | لیست بلند Watched — اسکن‌کردن مهم‌تر از تنفس است |
| `Density.touchMin` | **48dp** | کف مطلق هر هدف لمسی، در هر تراکمی |

### فوکوس

هر عنصر تعاملی **باید** نشانگر فوکوس داشته باشد — برای صفحه‌کلید و switch access.

| ویژگی | مقدار |
|---|---|
| رنگ | `colors.actionFocusRing` (لیمویی روی تیره) |
| ضخامت | `Focus.ringWidth` — 2dp |
| فاصله | `Focus.ringOffset` — 4dp |

> چرا لیمویی و نه آبی: رینگ باید هم روی زمینه و هم روی **پرکننده‌ی آبی دکمه** دیده
> شود. آبی روی آبی نامرئی است.

---

## ۱۵. چطور سیستم را تغییر دهیم

### تغییر مقدار یک رنگ موجود
1. `design/tokens.json` → مقدار primitive را عوض کن
2. `node design/validate-tokens.mjs`
3. همان مقدار را در `ui/theme/Tokens.kt` آینه کن
4. تغییر را در بخش ۱۶ ثبت کن

**بدون تغییر در هیچ کامپوننتی.** این کل هدف لایه‌بندی است.

### افزودن یک نقش معنایی جدید
1. در `tokens.json` زیر **هر دو** `sys.dark` و `sys.light` اضافه کن
   (validator اگر فقط یکی را اضافه کنی fail می‌شود)
2. اگر متن روی سطح است، `$on` بگذار تا سنجیده شود
3. فیلد را به `MovieMateColorScheme` و هر دو scheme اضافه کن
4. validator + build

### افزودن یک کامپوننت جدید
1. اول از نقش‌های موجود `sys.*` استفاده کن
2. فقط اگر پیچی داشت که هیچ کامپوننت دیگری شریکش نیست، `comp.*` بساز
3. اگر رنگی لازم داشت که نقش ندارد → **نقش اضافه کن**، به primitive دست نزن

### تغییر شکسته (breaking)
تغییر نام یک primitive یا حذف یک نقش، breaking است. minor version را بالا ببر،
در بخش ۱۶ ثبت کن، و نام قدیمی را یک نسخه با `@Deprecated` نگه دار.

---

## ۱۶. تاریخچه‌ی نسخه‌ها

### v9.0.0 — ۲۰۲۶/۰۹/۰۴

**تغییرات شکننده**
- زمینه‌ی پیش‌فرض از روشن به **تیره** رفت. پوستر فیلم برای زمینه‌ی تیره ساخته می‌شود.
- `MovieMateColors` (آبجکت تخت) حذف شد → `MovieMateTheme.colors` (نقش‌های معنایی)
- `Spacing.s1…s7` → `Space.*` با نام‌های مبتنی بر قصد
- استایل‌های تایپ دیگر رنگ حمل نمی‌کنند

**اصلاحات**
- `ink.500` از `#6E6E68` به `#696963`. مقدار v8 روی زمینه‌ی خودش **۴.۴۹۹۰:۱** بود و
  با اختلاف ۰.۰۰۱ از AA می‌افتاد. این رنگ هر خط meta در اپ است.
  **این ایراد توسط validator در همان اولین اجرا پیدا شد.**
- آبی به دو نقش تقسیم شد (`fill` در برابر `text.accent`) — بخش ۴.۲
- `CtaTone.Coral` حذف شد. پروتوتایپ v8 سفید روی مرجانی می‌گذاشت: **۲.۸۴:۱**،
  که هم مردود بود و هم قانون خود سند را می‌شکست.
- نسبت پوستر از ۱۶:۱۰ به ۴:۵ و ۲:۳ — بخش ۷

**افزوده‌ها**
- `design/tokens.json` به‌عنوان منبع واحد حقیقت (فرمت W3C DTCG)
- `design/validate-tokens.mjs` — ارجاع، چرخه، انضباط لایه، کنتراست، تقارن تم، گرید
- نقش‌های `partner.a` / `partner.b`
- `surface.warning` برای ردیف «کار باقی‌مانده» در Watchlist
- توکن‌های `comp.sharedAxis.*` برای کامپوننت امضا

### v8 — ۲۰۲۶/۰۸
آبی/لیمویی روی خاکستری گرم، Big Shoulders + Inter، مقیاس فاصله‌ی ۷ مرحله‌ای.
هویت پایه که v9 روی آن ساخته شده — پالت و جفت فونت از v8 دست‌نخورده مانده‌اند.

### v2–v7 — منسوخ
کاوش‌های lime/black/white و blue/lime condensed. فقط برای تاریخچه‌ی تصمیم نگه داشته شده‌اند.

---

## ۱۷. فایل‌های مرتبط

| فایل | نقش |
|---|---|
| `design/tokens.json` | منبع واحد حقیقت |
| `design/validate-tokens.mjs` | اجراکننده‌ی قوانین |
| `android/.../ui/theme/Tokens.kt` | لایه ۱ — primitives |
| `android/.../ui/theme/Semantic.kt` | لایه ۲ — نقش‌ها و تم‌ها |
| `android/.../ui/theme/Theme.kt` | اتصال، CompositionLocal |
| `android/.../ui/theme/Type.kt` | نقش‌های تایپ (بدون رنگ) |
| `docs/prototypes/*.html` | 🔶 پروتوتایپ‌های v8 — **منسوخ**، قوانین ۴.۳ و ۷ را نقض می‌کنند |

> پروتوتایپ‌های HTML هنوز v8 هستند و هنوز دکمه‌ی مرجانی و نسبت ۱۶:۱۰ را دارند.
> به‌عنوان مرجع بصری استفاده نشوند تا بازتولید شوند.
