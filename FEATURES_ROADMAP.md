# GuardSoul — خارطة طريق استنساخ أفضل الآليات

> وثيقة تشغيلية سريعة، تُحدَّث عند إنجاز كل ميزة.
> الهدف: استنساخ أفضل آليات التطبيقات المنافسة لكل ميزة في GuardSoul.

---

## 📊 جدول التتبع

| # | الميزة | المنافس المرجعي | الآلية المُستنسَخة | الحالة |
|---|---|---|---|---|
| 1 | Shield + Deactivation Delay | Cold Turkey + Bulldog | Strict Mode Cooldown 15min + Accountability Partner (6-digit code via email) | ✅ منجز |
| 2 | Trial Mode | فريد | لا يحتاج تحسين | ✅ مكتمل سابقاً |
| 3 | Days Counter + Streak | I Am Sober + Nomo | Daily Pledge + 8 Milestones (1d→365d, +XP) + 6-phase Withdrawal Timeline | ✅ منجز |
| 4 | AI Explorer (3/4د → 15د) | Canopy + Bulldog | Back button trick (pop back-stack + HOME) + Camera forced-exit (14 OEMs) | ✅ منجز |
| 5 | Uninstall Protection | Qustodio + BlockerPlus | Phone Manager block + Force Stop blocking + Time tamper + Email alerts + Multi-user | ✅ منجز (6 phases) |
| 6 | Black/White List | Qustodio + Net Nanny | 32 Categories × 250+ packages auto-detected (Social/Porn/Games/VPN/AI/…) | ✅ منجز |
| 7 | Strict Mode + PIN | AppBlock + Bulldog | Multi-level Strict + 15min Cooldown + Accountability Partner (#1) | ✅ منجز |
| 8 | Social Media Blocking | Shortstop + Blokr | Hardened detection + Deep Link blocking | ✅ منجز |
| 9 | Porn Blocker (DNS) | CleanBrowsing + Bulldog | App-level temp-block 15min (3 strikes/4min) + AI detection | ✅ منجز |
| 10 | Schedule (School+Bedtime) | Family Link + Screen Time | Bonus time release valve (5min grants, 30min/day cap) + Bedtime grayscale filter | ✅ منجز |
| 11 | Time Limits (per app) | ScreenZen + Screen Stoic | Discipline score (streak+milestones−porn+pledge) + 6 tiers (Mind Beginner → Enlightened) | ✅ منجز |
| 12 | Statistics | Screen Stoic + I Am Sober | Share card (1080×1080 PNG via chooser) + weekly report aggregation | ✅ منجز |
| 13 | XP/Level | Forest + Screen Stoic | 6 tiers (#11) + Study Room (60min focus, allow-list Education/Productivity/Books/Notes/AI/Browsers) | ✅ منجز |
| 14 | Accountability Partner | Bulldog + Canopy + Nomo | مدمج في #1 (email-based أبسط، بدون server) | ✅ منجز (#1) |

**الرموز**: ⏳ قيد العمل | ✅ منجز | ❌ ملغي/مدمج

---

## 🎯 الترتيب التشغيلي

### المرحلة 1: الأساسيات
- **#5 Uninstall Protection** (الأعلى أثر)
- **#1 Strict Mode + Cooldown + Accountability Partner** (تحديث)

### المرحلة 2: التحفيز
- **#3 Daily Pledge + Milestones + Withdrawal Timeline**
- **#4 Forced image removal + Camera block**
- **#9 App-level temp-block 15min for porn**

### المرحلة 3: المرونة
- **#11 Open limits + Discipline score + 6 tiers**
- **#6 30+ Categories + Auto-suggest**
- **#10 Bonus time + Auto-grayscale**

### المرحلة 4: الكمال
- **#12 Weekly reports + Share cards**
- **#13 6-tier progression + Study room**

### (مُنجَز سابقاً)
- **#8 Social Media Blocking** (Hardened detection + Deep Links)

---

## 🛠️ المعماريات المعتمدة

### Constants
- `BlockingConfig.kt` مركزية لكل thresholds/retries/cooldowns.

### DataStore Keys
- `AppSettings.kt` مركزية لكل preferences + flows + setters.

### Background Services
- `AppBlockerService.kt` (AccessibilityService) — الحظر الكلي.
- `blocking/ShortstopAccessibilityService.kt` (AccessibilityService) — الحظر الجراحي Shorts/Reels عبر PatternMatcher.
- `PornBlockerService.kt` + `DnsVpnService.kt` — فلتر DNS.
- `AiScannerService.kt` — فحص الشاشة بالـ AI.
- `GuardianAccessibilityService.kt` — uninstall guard + keyword filter.

### Receivers
- `BootReceiver` — إعادة تشغيل الخدمات.
- `CloneReceiver` — PACKAGE_ADDED.
- `AdbReceiver` — كشف ADB.
- `ShortsIntentReceiver` — Deep Link blocking (منجز).
- `ScheduleReceiver` — جدولة زمنية.

### ViewModels
- `HomeViewModel`, `SocialViewModel`, `ContentViewModel`, `ListsViewModel`, `SettingsViewModel`, `ScheduleViewModel`, `TimeLimitsViewModel`.

---

## 📝 ملاحظات التنفيذ

1. **البناء بعد كل ميزة**: `gradlew.bat compileDebugKotlin` و `gradlew.bat assembleDebug`.
2. **الـ APK في**: `C:\Users\moham\gard\app\build\outputs\apk\debug\app-debug.apk`.
3. **Package**: `com.agon.app`.
4. **minSdk=26, targetSdk=35, compileSdk=36, Kotlin 2.1.0, Java 21**.

---

**آخر تحديث**: 2026-06-04
