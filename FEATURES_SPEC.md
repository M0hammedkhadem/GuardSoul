# GuardSoul — مواصفات الميزات (Features Specification)

> وثيقة مرجعية رسمية لكل ميزات تطبيق **GuardSoul** (حظر الاباحية وتقليل استخدام وسائل التواصل الاجتماعي).
> تُستخدم هذه الوثيقة كمرجع للتنفيذ ولضمان اكتمال كل ميزة قبل الانتقال للتي بعدها.

---

## 0. نظرة عامة على بنية التطبيق

| الطبقة | التقنية | الدور |
|---|---|---|
| **UI** | Jetpack Compose (Material 3) | كل الشاشات والتنقل |
| **State** | ViewModel + StateFlow + DataStore Preferences | الإعدادات والتدفقات |
| **Persistence** | Room (entities + DAOs) + DataStore | الإعدادات + السجلات + القوائم |
| **Background** | AccessibilityService + ForegroundService + VpnService + WorkManager | الرصد والحظر في الخلفية |
| **DI** | Koin | حقن التبعيات |
| **التطبيق نفسه** | `com.agon.app` (app name: **GuardSoul**) | الحزمة الرسمية |

### أعلام التفعيل الرئيسية
- `SHIELD_ACTIVE` (Bool) — مفتاح الدرع الرئيسي.
- `TRIAL_MODE` (Bool) — مفتاح وضع التجربة (منفصل تماماً عن الدرع).
- `DEACTIVATION_DELAY_DAYS` (Int) — مدة تأخير الإيقاف بالأيام.
- `STRICT_MODE` (Bool) — وضع صارم يطلب رمز PIN عند إيقاف الدرع.

### بنية الميزات (شجرة الميزة الواحدة)
```
Shield (Master)
└── Social Media (SocialScreen)
    ├── Snapchat  (on/off)
    ├── X/Twitter (on/off)
    ├── TikTok    (on/off)
    ├── Instagram → off | full | reels
    ├── YouTube   → off | full | shorts
    └── Facebook  → off | full | reels
└── Porn / Content (ContentScreen)
    ├── Porn Blocker (Safe-Search في Google/YouTube)
    └── AI Explorer (فحص شاشة + منطق 3/4د → 15د)
└── Uninstall Protection
└── Black/White List (ListsScreen)
    ├── keywords
    ├── websites
    └── apps (مع منتقي تطبيقات)
└── Deactivation Delay (5 خيارات بالأيام)
└── Strict Mode + PIN
```

---

## 1. الدرع (Shield) — المفتاح الرئيسي

### الوصف
الدرع هو **مفتاح تشغيل/إيقاف شامل** لكل ميزات التطبيق. عندما يكون الدرع متوقفاً، **لا تعمل أي ميزة** (باستثناء وضع التجربة — انظر (1.4)).

### الحالة
- **متوقف** → كل الخدمات الخلفية لا تعمل.
- **متصل** → كل الخدمات تعمل حسب الإعدادات.

### آلية الإيقاف (Soft-Deactivation)
عندما يكون الدرع **متصلاً** ويحاول المستخدم إيقافه:

1. **إذا كان وضع التجربة مفعّل** → يُسمح بإيقاف الدرع فوراً.
2. **إذا كان الإعداد "بدون تأخير"** → يُسمح بإيقاف الدرع فوراً.
3. **خلاف ذلك** → يبدأ **عدّاد تنازلي** بالمدّة المختارة في ميزة "مدة تأخير الإيقاف".
   - يبقى الدرع **متصلاً فعلاً** طوال العدّاد.
   - يمكن للمستخدم **إلغاء** العدّاد → يُحذف وكأن شيئاً لم يحدث ويستمر الدرع متصلاً.
   - عند وصول العدّاد إلى **صفر** → يُسمح للمستخدم بإيقاف الدرع (في وضع صارم يُطلب PIN أولاً).

### شروط النجاح
- [ ] الدرع يعمل كـ `booleanPreferencesKey` ويحفظ في DataStore.
- [ ] عند تصفير الدرع (إيقافه فعلياً)، عدّاد `daysActive` **يعود لصفر**.
- [ ] عند `deactivationDelay == 0` لا يظهر أي عدّاد — إيقاف فوري.
- [ ] عند الضغط على الدرع وهو متصل: يظهر `CountdownOverlay` مع زر "إلغاء" حقيقي.
- [ ] خدمات الخلفية (AppBlockerService / PornBlockerService / AiScannerService) تتوقف تلقائياً عند إيقاف الدرع، وتُعاد تشغيلها عند تشغيله.

### ملفات مرجعية
- `HomeViewModel.kt::startDeactivation / cancelDeactivation / completeDeactivation`
- `HomeScreen.kt::ShieldOrb + CountdownOverlay`
- `HomeViewModel.kt::init { combine(shield, porn) → services }`

---

## 2. الصفحة الرئيسية (HomeScreen)

### العناصر
1. **الهيدر** — اسم الشاشة + زر اللغة + شارة حالة الدرع (Active/Inactive).
2. **الدرع الكبير** (`ShieldOrb`) — قابل للنقر، يفعّل/يعطّل الدرع.
3. **بطاقتا إحصاء** تحت الدرع:
   - **Days** — عداد تصاعدي لأيام بقاء الدرع متصلاً **دون انقطاع**. عند تصفير الدرع (إيقاف فعلي) يعود العدّاد إلى **صفر**.
   - **Blocks** — عدّاد إجمالي لعدد مرات الحظر التي نفّذها التطبيق (يُسجَّل في `BlockEventEntity`).
4. **بطاقة "وضع التجربة"** — زر مستقل لتشغيل/إيقاف وضع التجربة.
5. **بطاقة "مدة تأخير الإيقاف"** — تعرض الخيار الحالي (مثلاً "7 Days") وتنتقل لإعدادات التأخير.

### شروط النجاح
- [ ] `daysActive` يُحفظ كـ `long` (تاريخ آخر تصفير) ويحسب الفرق بالأيام.
- [ ] `totalBlocks` = مجموع `BlockEventEntity` في كل الأوقات.
- [ ] بطاقة وضع التجربة منفصلة تماماً — تشغيلها/إيقافها **لا** يمسّ الدرع ولا يخضع لمهلة الإيقاف.

---

## 3. قسم وسائل التواصل الاجتماعي (SocialScreen)

### البنية الموحدة
| التطبيق | السلوك |
|---|---|
| **Snapchat** (com.snapchat.android) | زر تشغيل/إيقاف → **منع كامل** للتطبيق |
| **X / Twitter** (com.twitter.android) | زر تشغيل/إيقاف → **منع كامل** للتطبيق |
| **TikTok** (com.zhiliaoapp.musically) | زر تشغيل/إيقاف → **منع كامل** للتطبيق |
| **Instagram** | قائمة منسدلة: `off` \| `full` \| `reels` |
| **YouTube** | قائمة منسدلة: `off` \| `full` \| `shorts` |
| **Facebook** | قائمة منسدلة: `off` \| `full` \| `reels` |

### السلوك لكل وضع
- **`off` (بدون حظر)** — التطبيق يفتح بشكل طبيعي.
- **`full` (منع كامل)** — منع المستخدم من فتح التطبيق نهائياً. يُنفّذ عبر `AppBlockerService` (AccessibilityService + UsageStats).
- **`shorts` / `reels` (منع المحتوى القصير فقط)** — السماح بفتح التطبيق، لكن:
  - عند الدخول إلى فيديو قصير (Shorts/Reels) أو إلى قسم Shorts/Reels → **إعادة قسرية** إلى الصفحة الرئيسية.
  - إظهار رسالة "تم الحظر من طرف GuardSoul" وإغلاق المقطع القصير.
  - يُنفّذ عبر `ShortstopAccessibilityService` (AccessibilityService) — مع طبقة Overlay جراحية تحجب المشغل فقط وتبقي الرسائل والبحث.

### شروط النجاح

- [ ] عند `mode == "full"` وتشغيل الدرع → التطبيق محظور 100% (يظهر `BlockActivity` فوقه).
- [ ] عند `mode == "shorts/reels"` وتشغيل الدرع → المستخدم يدخل التطبيق، لكن أي ضغطة على Shorts/Reels أو دخول قسم Shorts/Reels يحوّله للصفحة الرئيسية فوراً.
- [ ] التحقق من خدمة `ShortstopAccessibilityService` قبل إتاحة القائمة المنسدلة (إذا لم تكن مفعّلة، اعرض Dialog "فعّل خدمة الوصول أولاً").

### ملفات مرجعية

- `SocialScreen.kt` — واجهة بطاقات السحب (DropdownCard).
- `SocialViewModel.kt` — إدارة الحالات + `ensureAppBlockerRunning`.
- `AppBlockerService.kt` — تنفيذ المنع الكامل عبر UsageStats.
- `blocking/ShortstopAccessibilityService.kt` — تنفيذ كشف Shorts/Reels + الإعادة للرئيسية عبر PatternMatcher.

---

## 4. قسم الإباحية (ContentScreen)

### 4.1 ميزة "Porn Blocker" (الحظر عبر البحث الآمن)

#### الهدف
منع ظهور نتائج محتوى حساس (إباحية، قمار) في **Google** و**YouTube** ومحركات البحث الأخرى.

#### الآلية
1. **إذا كان التطبيق Device-Owner** → يستخدم `CLEANBROWSING_FAMILY_HOST` كـ Private DNS على مستوى النظام.
2. **إذا لم يكن Device-Owner** → يعتمد على الكلمات المفتاحية الموجودة في القائمة السوداء كـ fallback.

#### شروط النجاح
- [ ] عند التفعيل والدرع متصل: لا تظهر نتائج إباحية في Google/YouTube.
- [ ] عند التفعيل والدرع متوقف: لا يعمل الفلتر (لأنه معتمد على الدرع).
- [ ] شارة `DNS ACTIVE` تظهر عندما يكون التطبيق Device-Owner.
- [ ] شارة `KEYWORD ACTIVE` تظهر كـ fallback.

### 4.2 ميزة "AI Explorer" (فحص الشاشة)

#### الهدف
فحص محتوى الشاشة **كل 2-3 ثوانٍ** (نختار الرقم المناسب: **1500ms** كمتوازن بين الاستجابة والأداء). إذا اكتُشف محتوى حساس → يُخرَج المستخدم من التطبيق ويُعلَّم بالحظر.

#### المنطق المُحكَّم
1. عند اكتشاف محتوى حساس في تطبيق `X`:
   - تسجيل حدث `ai_sensitive_block` (مرة واحدة، مع cooldown).
   - إغلاق التطبيق → `performGlobalAction(GLOBAL_ACTION_HOME)`.
   - عرض إشعار: "تم اكتشاف محتوى حساس — حُظرت من هذا التطبيق".
2. **عداد التكرار** لكل `packageName`:
   - إذا وقعت **3 حالات حظر** في **4 دقائق** داخل نفس التطبيق → **حظر ذلك التطبيق لمدة 15 دقيقة** كاملة.
   - يُحفظ هذا في DataStore كـ `app_temp_block:{pkg}:until` (timestamp).
3. خلال فترة الحظر المؤقت (15 دقيقة) → أي محاولة لفتح التطبيق تُعالَج عبر `BlockActivity`.

#### شروط النجاح
- [ ] الفحص يعمل فقط عند الدرع **متصل** + AI Explorer **مفعّل**.
- [ ] الفترة بين كل فحص والآخر = 1500ms.
- [ ] `recordAiBlock(pkg)` يحدّث العدّاد؛ إذا وصل 3 ضمن 4 دقائق → `setTempBlock(pkg, 15 * 60_000ms)`.
- [ ] `AppBlockerService` يحترم `tempBlocks` ويرفض فتح التطبيق حتى انتهاء المدة.

### ملفات مرجعية
- `ContentScreen.kt` — بطاقات التبديل.
- `ContentViewModel.kt` — إدارة الحالات.
- `PornBlockerService.kt` + `DnsVpnService.kt` — فلتر DNS.
- `AiScannerService.kt` — خدمة فحص الشاشة.
- `PornBlockerController.kt` — مزامن الإعدادات.

---

## 5. حماية الإزالة (Uninstall Protection)

### الهدف
منع المستخدم من حذف تطبيق GuardSoul أو إزالة أذوناته.

### الآلية
1. **Device-Admin Receiver** — عبر `GuardianDeviceAdminReceiver` (مفعّل عبر `BIND_DEVICE_ADMIN`).
2. **منع الدخول إلى إعدادات التطبيق نفسه** — عند تفعيل الميزة:
   - رصد محاولات فتح `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` الخاصة بـ GuardSoul → حجبها.
3. **منع إزالة إذن Device-Admin** — يستوجب PIN في الوضع الصارم.
4. **كشف تغيير DNS** — عبر `Settings.Global.getString(PRIVATE_DNS_MODE)` ومراقبة `ContentObserver`.
5. **رصد محاولات إلغاء التثبيت عبر ADB** — عبر `AdbReceiver`.
6. **رصد النسخ/الاستنساخ** — عبر `CloneReceiver` (PACKAGE_ADDED).

### شروط النجاح
- [ ] عند التفعيل والدرع متصل: لا يمكن للمستخدم فتح إعدادات GuardSoul.
- [ ] عند التفعيل: لا يمكن إزالة إذن Device-Admin بدون PIN.
- [ ] عند تغيير DNS إلى قيمة غير CleanBrowsing → يُسجَّل `TamperAlert` ويُعاد ضبطه.

### ملفات مرجعية
- `GuardianDeviceAdminReceiver.kt` — تطبيق Device-Admin.
- `ContentScreen.kt::FeatureToggleCard(uninstall)` — UI البطاقة.
- `receivers/AdbReceiver.kt` و `receivers/CloneReceiver.kt`.

---

## 6. القوائم: Black List + White List

### الهدف
نفس الشاشة، مفتاحان: `Whitelist` (مسموح) / `Blacklist` (محظور). كل قسم يحتوي على **3 فئات**:
- **keywords** — كلمات مفتاحية (مثلاً `porn`، `xxx`، `إباحي`).
- **websites** — نطاقات (مثلاً `pornhub.com`).
- **apps** — تطبيقات عبر `packageName` (مع منتقي تطبيقات في الجهاز).

### السلوك
- **Blacklist + keyword** → يحظر التطبيقات/المواقع التي تحوي الكلمة.
- **Blacklist + website** → يحظر فتح الموقع (عبر VPN DNS).
- **Blacklist + app** → يحظر التطبيق (AppBlockerService).
- **Whitelist** → يستثني من كل ميزات التطبيق (الدرع لا يحظر، AI لا يفحص، إلخ).

### Defaults (تُحقن في أول تشغيل)
- **Keywords**: `porn, xxx, sex, nude, naked, hentai, adult, erotic, nsfw, fetish, إباحي, جنس, عاري`
- **Websites**: `pornhub.com, xvideos.com, xnxx.com, redtube.com, youporn.com, ...`
- **Apps**: لا توجد تطبيقات افتراضية.

### شروط النجاح
- [ ] شاشة `ListsScreen` تعرض `Whitelist|Blacklist` ومبدّل ثلاثي `Apps|Websites|Keywords`.
- [ ] منتقي التطبيقات يعرض كل التطبيقات المُثبّتة (ما عدا GuardSoul نفسه) مع البحث.
- [ ] الكلمات والمواقع الافتراضية تُحقن مرة واحدة فقط (مفتاح "موجودة مسبقاً").

### ملفات مرجعية
- `ListsScreen.kt`, `ListsViewModel.kt`.
- `BlocklistDao.kt`, `BlocklistItemEntity.kt`.
- `GuardianApp.kt::seedDefaultBlocklists`.

---

## 7. مدة تأخير الإيقاف (Deactivation Delay)

### الخيارات (بالأيام)
| القيمة | تفسير |
|---|---|
| **0** (بدون تأخير) | إيقاف فوري |
| **2 يوم** | 2 × 24 × 60 دقيقة |
| **7 أيام** | 7 × 24 × 60 دقيقة |
| **15 يوم** | 15 × 24 × 60 دقيقة |
| **1 شهر** | 30 × 24 × 60 دقيقة (أو تقويم شهر) |

### السلوك
- عند الضغط على الدرع وهو متصل → إذا كان التأخير > 0 يبدأ العدّاد.
- يُعرض للمستخدم كم تبقى (دقائق:ثواني) بوضوح.
- زر "إلغاء" → حذف العدّاد (يبقى الدرع متصلاً).

### شروط النجاح
- [ ] `DEACTIVATION_DELAY_DAYS` (Int) — محفوظ في DataStore.
- [ ] الخمسة الخيارات معرّفة في قائمة منسدلة / chips.
- [ ] الدرع يحترم هذا الإعداد عند طلب الإيقاف.

> ⚠️ **ملاحظة تنفيذية**: الكود الحالي يستخدم `DEACTIVATION_DELAY_MINUTES` (بالدقائق) — يجب تحويله إلى `DEACTIVATION_DELAY_DAYS` وعرض الخيارات الخمسة بالأيام.

### ملفات مرجعية
- `AppSettings.kt::DEACTIVATION_DELAY_MINUTES` (يحتاج إعادة تسمية).
- `HomeViewModel.kt::startDeactivation` (يستخدم القيمة لحساب الثواني).

---

## 8. وضع التجربة (Trial Mode)

### الهدف
زر مستقل على الشاشة الرئيسية يتيح للمستخدم **تجربة الميزات** دون أن يمرّ بآلية تأخير الإيقاف.

### السلوك
- **مفعّل** → المستخدم يستطيع:
  - تشغيل/إيقاف الدرع **فورياً** (بدون عدّاد).
  - تجربة كل الميزات وكأنها تعمل.
- **معطّل** → الدرع يخضع لقواعد الإيقاف العادية (تأخير + PIN إن كان صارماً).

### العلاقة بالدرع
- وضع التجربة **لا يؤثّر** على الدرع ولا العكس.
- الدرع يحتفظ بالأولوية: إذا أراد المستخدم إيقاف الدرع → يمرّ عبر الدرع نفسه.
- إذا أراد المستخدم إيقاف الدرع مع مفعّل وضع التجربة → يوقف فورياً.

### شروط النجاح
- [ ] `TRIAL_MODE` (Bool) — محفوظ في DataStore.
- [ ] في `HomeViewModel::startDeactivation`:
  - إذا `trialMode == true` أو `delay == 0` → `completeDeactivation` فوراً.
- [ ] بطاقة "وضع التجربة" على Home تعرض الحالة (ON/OFF) وقابلة للنقر للتبديل.

### ملفات مرجعية
- `HomeScreen.kt::ActionTile(card_trial_title)`.
- `HomeViewModel.kt::trialMode / startDeactivation`.

---

## 9. الوضع الصارم + PIN (Strict Mode + PIN)

### الهدف
طبقة حماية إضافية: عند محاولة إيقاف الدرع (بعد انتهاء العدّاد) في الوضع الصارم، يُطلب **رمز PIN**.

### شروط النجاح
- [ ] إذا `STRICT_MODE == true && hasPin == true` → بعد انتهاء العدّاد تظهر نافذة PIN قبل `completeDeactivation`.
- [ ] إذا فشلت 3 محاولات PIN → تسجيل `TamperAlert` (`type=pin_failed`).

### ملفات مرجعية
- `HomeViewModel.kt::verifyPin` و `showPinDialog`.
- `PinSetupScreen.kt` و `EncryptedPrefs.kt`.
- `SecurityUtils.kt::verifyPinAgainstHash`.

---

## 10. المزامنة والتشغيل التلقائي

| المحفّز | السلوك |
|---|---|
| `BOOT_COMPLETED` | إعادة تشغيل كل الخدمات حسب الإعدادات |
| `PACKAGE_ADDED` / `PACKAGE_REPLACED` | تحديث منتقي التطبيقات وإشعار المستخدم |
| `VPN` revoked | تنبيه فوري + محاولة إعادة التشغيل |
| `Safe Mode` | تسجيل tamper + إشعار |

### ملفات مرجعية
- `BootReceiver.kt`, `VpnRevocationWorker.kt`, `VpnStateMonitor.kt`.
- `receivers/CloneReceiver.kt`, `receivers/AdbReceiver.kt`.

---

## 11. مخطط التطبيق (Navigation)

```
MainApp
├── onboarding (في أول تشغيل)
├── pin_setup
├── home (الدرع)               ← ShieldOrb + days + blocks + trial + delay
├── social                     ← Snapchat/X/TikTok + IG/YT/FB dropdowns
├── content                    ← Porn Blocker + AI Explorer + Uninstall + Strong
├── lists                      ← Whitelist|Blacklist × Apps|Websites|Keywords
├── permissions
├── settings (hub)
├── profile
├── schedule
├── time_limits
├── statistics
└── export_import
```

---

## 12. معايير النجاح العامة (لكل ميزة)

1. **التعريف**: كل ميزة لها Preference/DataStore entry واضح في `AppSettings`.
2. **التدفّق**: كل ميزة لها `Flow<…>` متاح في `AppSettings` + setter معلَّق.
3. **الربط بالدرع**: كل ميزة (ما عدا trial mode) تحترم `SHIELD_ACTIVE`.
4. **اختبار**: بناء التطبيق بلا أخطاء (lint + compile) بعد كل إضافة.
5. **سجلّ**: كل عملية حظر تسجَّل في `BlockEventEntity` (لاحتساب عداد Blocks).
6. **عزل**: لا تستخدم متغيّرات globals خارج `AppSettings`/`AppRepository`.

---

## 13. خطة التنفيذ (مرتّبة)

| # | الميزة | الأولوية | الحالة |
|---|---|---|---|
| 1 | الدرع + مدة تأخير الإيقاف بالأيام | حرجة | يبدأ فوراً |
| 2 | وضع التجربة (Trial Mode) المستقل | حرجة | يبدأ فوراً |
| 3 | عدّاد الأيام (يعود لصفر عند تصفير الدرع) | حرجة | يبدأ فوراً |
| 4 | AI Explorer (فحص شاشة + 3/4د → 15د) | حرجة | بعد 1-3 |
| 5 | مراجعة Uninstall Protection | متوسطة | بعد 4 |
| 6 | مراجعة Black/White list | متوسطة | بعد 5 |
| 7 | تنظيف الأكواد الميتة | متوسطة | بعد كل ميزة |

---

## 14. المرجع السريع للإعدادات (DataStore Keys)

| المفتاح | النوع | الافتراضي | الملف |
|---|---|---|---|
| `shield_active` | Bool | false | AppSettings |
| `trial_mode` | Bool | false | AppSettings |
| `deactivation_delay_days` | Int | 0 | AppSettings **(يحتاج إضافة)** |
| `strict_mode` | Bool | false | AppSettings |
| `social_instagram` | Bool | false | AppSettings |
| `instagram_mode` | String ("off"/"full"/"reels") | "off" | AppSettings |
| `social_snapchat` | Bool | false | AppSettings |
| `social_twitter` | Bool | false | AppSettings |
| `social_tiktok` | Bool | false | AppSettings |
| `youtube_mode` | String ("off"/"full"/"shorts") | "off" | AppSettings |
| `facebook_mode` | String ("off"/"full"/"reels") | "off" | AppSettings |
| `porn_blocker` | Bool | false | AppSettings |
| `ai_scanner` | Bool | false | AppSettings |
| `uninstall_protection` | Bool | false | AppSettings |
| `strong_protection` | Bool | false | AppSettings |
| `ai_temp_block:{pkg}` | Long (timestamp) | 0 | AppSettings **(يحتاج إضافة)** |
| `ai_block_count:{pkg}` | Int + Long[] (داخل 4د) | [] | AppSettings **(يحتاج إضافة)** |

---

**آخر تحديث**: تم إنشاء هذه الوثيقة لتكون المرجع الرسمي أثناء تطوير ميزات GuardSoul.
