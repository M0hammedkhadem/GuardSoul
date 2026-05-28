# 📱 GuardSoul — تقييم شامل للمشروع

_آخر تحديث: 20 مايو 2026_

---

## 🏗️ هيكل المشروع الحالي

```
com.agon.app/
├── ui/
│   ├── screens/
│   │   ├── HomeScreen.kt          # الشاشة الرئيسية (الدرع)
│   │   ├── SocialScreen.kt        # إعدادات حظر وسائل التواصل
│   │   ├── ContentScreen.kt       # فلتر المحتوى + AI + حماية الإلغاء
│   │   ├── ListsScreen.kt         # القائمة السوداء/البيضاء
│   │   ├── StatisticsScreen.kt    # الإحصائيات
│   │   ├── ProfileScreen.kt       # الملف الشخصي
│   │   ├── SettingsScreen.kt      # الإعدادات الرئيسية
│   │   ├── TimeLimitsScreen.kt    # الحدود الزمنية اليومية
│   │   ├── ScheduleScreen.kt      # جدولة الحظر
│   │   ├── ExportImportScreen.kt  # تصدير/استيراد القوائم
│   │   ├── OnboardingScreen.kt    # شاشة الترحيب الأولية
│   │   ├── PermissionsScreen.kt   # إدارة الأذونات
│   │   ├── BlockActivity.kt       # نشاط شاشة الحظر
│   │   ├── PinSetupScreen.kt      # إعداد PIN
│   │   └── UpgradeScreen.kt       # شاشة الترقية
│   ├── components/
│   │   └── PinGate.kt             # حماية PIN للمحتوى
│   └── theme/
│       ├── Color.kt               # ألوان التطبيق
│       └── Theme.kt               # الثيم الداكن
├── FacebookBlockerService.kt      # خدمة حظر فيديوهات فيسبوك (AccessibilityService)
├── FacebookSettings.kt            # إعدادات فيسبوك (DataStore)
├── AccessibilityUtils.kt          # أدوات إمكانية الوصول
├── DnsVpnService.kt               # خدمة DNS/VPN (stub)
├── AiScannerService.kt            # خدمة المسح بالذكاء الاصطناعي (stub)
├── BootReceiver.kt                # مستقبل الإقلاع
├── GuardianDeviceAdminReceiver.kt # مشرف الجهاز
├── AppNotificationChannels.kt     # قنوات الإشعارات
├── GuardianApp.kt                 # Application class
├── LanguageManager.kt             # إدارة اللغة (DataStore)
├── UiModels.kt                    # نماذج البيانات
└── MainActivity.kt                # النشاط الرئيسي + التنقل
```

---

## ✅ الميزات المكتملة كلياً (UI + Logic)

### 1. شاشة الأذونات — PermissionsScreen
| الجزء | الحالة | التفاصيل |
|-------|--------|----------|
| عرض البطاقات الخمس | ✅ مكتمل | Accessibility, VPN, Device Admin, Overlay, Usage Access |
| فتح إعدادات النظام | ✅ مكتمل | كل زر يفتح شاشة الإعدادات المناسبة |
| التحقق من الحالة | ✅ مكتمل | قراءة من النظام (`isServiceEnabled`, `canDrawOverlays`, `isAdminActive`, `AppOpsManager`) |
| شريط التقدم | ✅ مكتمل | 0/5 → 5/5 مع لون أخضر عند الاكتمال |

### 2. حظر فيديوهات فيسبوك — FacebookBlockerService
| الجزء | الحالة | التفاصيل |
|-------|--------|----------|
| AccessibilityService | ✅ مكتمل | يكشف النقر على الفيديو ونوافذ المشغل |
| حظر بـ GLOBAL_ACTION_BACK | ✅ مكتمل | 3 ضغطات خلف متتالية |
| إشعار بعد الحظر | ✅ مكتمل | مع catch لـ SecurityException |
| إعدادات عبر DataStore | ✅ مكتمل | تشغيل/إيقاف، وضع الكشف |
| عداد الحظر | ✅ مكتمل | incrementVideoBlockCount |

### 3. PIN والحماية
| الجزء | الحالة | التفاصيل |
|-------|--------|----------|
| إعداد PIN | ✅ مكتمل | 3 خطوات (إدخال، تأكيد، نجاح) |
| التحقق من PIN | ✅ مكتمل | SHA-256، لوحة أرقام مخصصة |
| PinGate للمحتوى | ✅ مكتمل | يحمي المحتوى خلف PIN |
| FLAG_SECURE | ✅ مكتمل | يمنع التصوير/التسجيل |

### 4. اللغة
| الجزء | الحالة | التفاصيل |
|-------|--------|----------|
| دعم عربي/إنجليزي | ✅ مكتمل | عبر DataStore و Locale |
| تطبيق اللغة فوراً | ✅ مكتمل | attachBaseContext |

### 5. إدارة الجهاز ومشغل الجهاز
| الجزء | الحالة | التفاصيل |
|-------|--------|----------|
| GuardianDeviceAdminReceiver | ✅ مكتمل | مع Toast ورسالة تحذير |
| BootReceiver | ✅ مكتمل | إعادة تشغيل FacebookBlockerService |
| قنوات الإشعارات | ✅ مكتمل | 6 قنوات معرفة |
| Timber Logging | ✅ مكتمل | DebugTree في GuardianApp |

### 6. تصدير/استيراد القوائم
| الجزء | الحالة | التفاصيل |
|-------|--------|----------|
| تصدير القائمة السوداء/البيضاء | ✅ مكتمل | كتابة إلى URI |
| استيراد وتحليل الملفات | ✅ مكتمل | دعم `kw:` و `app:` و `#` |
| واجهة المستخدم | ✅ مكتمل | بطاقات التصدير والاستيراد مع رسائل الحالة |

### 7. AndroidManifest والأذونات
| الجزء | الحالة | التفاصيل |
|-------|--------|----------|
| جميع الأذونات المطلوبة | ✅ مكتمل | 10 permissions + 3 service-level |
| جميع المكونات معلنة | ✅ مكتمل | 7 components (2 activities, 3 services, 2 receivers) |

---

## 🔶 الميزات الموجودة (UI فقط — تحتاج Logic)

### 1. الشاشة الرئيسية — HomeScreen
| الجزء | الحالة | المشكلة |
|-------|--------|---------|
| ShieldOrb المتحرك | ✅ UI مكتمل | لا يوجد منطق تشغيل/إيقاف فعلي |
| StatsRow | ✅ UI مكتمل | الأرقام hardcoded/فارغة — لا توجد قراءة من قاعدة بيانات |
| TrialModeCard | ✅ UI مكتمل | لا يوجد منطق للوضع التجريبي |
| DeactivationDelayCard | ✅ UI مكتمل | لا يوجد تأخير فعلي عند إلغاء التنشيط |
| **ما يجب بناؤه:** ViewModel يقرأ من repository + SharedPreferences/DataStore لحالة الدرع والإحصائيات |

### 2. حظر وسائل التواصل — SocialScreen
| الجزء | الحالة | المشكلة |
|-------|--------|---------|
| Toggle switches للتطبيقات | ✅ UI مكتمل | حالة التبديل غير محفوظة |
| YouTube mode selector | ✅ UI مكتمل | لا يوجد AccessibilityService ليوتيوب |
| Facebook mode selector | ✅ UI مكتمل | موجود FacebookBlockerService لكن غير مربوط |
| Instagram/Snapchat/Twitter/TikTok | ✅ UI مكتمل | لا يوجد أي blocking logic |
| **ما يجب بناؤه:** DataStore للتبديلات + AccessibilityService لكل تطبيق + UsageStatsManager للكشف |

### 3. فلتر المحتوى — ContentScreen
| الجزء | الحالة | المشكلة |
|-------|--------|---------|
| Porn Blocker toggle | ✅ UI مكتمل | DnsVpnService مجرد stub — لا يوجد فلترة DNS |
| AI Explorer toggle | ✅ UI مكتمل | AiScannerService مجرد stub — لا MediaProjection ولا TFLite |
| Uninstall Protection toggle | ✅ UI مكتمل | Device Admin موجود لكن الحماية غير مفعلة بالكامل |
| **ما يجب بناؤه:** VpnService كامل + MediaProjectionService + TFLite نموذج + Device Admin حماية حقيقية |

### 4. القوائم — ListsScreen
| الجزء | الحالة | المشكلة |
|-------|--------|---------|
| Blacklist/Whitelist tabs | ✅ UI مكتمل | |
| Keywords/Websites/Apps tabs | ✅ UI مكتمل | |
| Add/Remove items | ✅ UI مكتمل | |
| App picker dialog | ✅ UI مكتمل | |
| **ما يجب بناؤه:** حفظ القوائم في DataStore/Room + ربطها بـ DnsVpnService و AccessibilityService |

### 5. الإحصائيات — StatisticsScreen
| الجزء | الحالة | المشكلة |
|-------|--------|---------|
| بطاقات الإحصائيات | ✅ UI مكتمل | البيانات hardcoded/فارغة |
| Most blocked app | ✅ UI مكتمل | لا يوجد مصدر بيانات |
| Today's breakdown | ✅ UI مكتمل | رسم بياني بلا بيانات |
| Recent block events | ✅ UI مكتمل | قائمة فارغة |
| **ما يجب بناؤه:** Room DB للأحداث + Repository + ViewModel + MPAndroidChart ربط |

### 6. الملف الشخصي — ProfileScreen
| الجزء | الحالة | المشكلة |
|-------|--------|---------|
| عرض الاسم | ✅ UI مكتمل | لا حفظ في DataStore |
| Account summary | ✅ UI مكتمل | كل القيم hardcoded |
| **ما يجب بناؤه:** DataStore للاسم + ربط بالإحصائيات الحقيقية |

### 7. الإعدادات — SettingsScreen
| الجزء | الحالة | المشكلة |
|-------|--------|---------|
| أزرار التنقل السريع | ✅ UI مكتمل | تعمل جميعها |
| Status mini-cards | ✅ UI مكتمل | حالة كل ميزة غير مقروءة من النظام |
| Blocked apps overview | ✅ UI مكتمل | فارغ — لا يوجد مصدر |
| **ما يجب بناؤه:** قراءة حالة الميزات من DataStore/SystemServices |

### 8. الحدود الزمنية — TimeLimitsScreen
| الجزء | الحالة | المشكلة |
|-------|--------|---------|
| قائمة الحدود الزمنية | ✅ UI مكتمل | الحالة في الذاكرة فقط (emptyList pass) |
| إضافة حد مع picker | ✅ UI مكتمل | |
| عداد التقدم | ✅ UI مكتمل | لا يوجد تتبع وقت فعلي |
| **ما يجب بناؤه:** DataStore/Room للحدود + UsageStatsManager Worker دوري لمراقبة الوقت |

### 9. جدولة الحظر — ScheduleScreen
| الجزء | الحالة | المشكلة |
|-------|--------|---------|
| قائمة القواعد | ✅ UI مكتمل | الحالة في الذاكرة فقط (emptyList pass) |
| إضافة قاعدة مع day/time pickers | ✅ UI مكتمل | |
| تفعيل/تعطيل وحذف | ✅ UI مكتمل | |
| **ما يجب بناؤه:** DataStore/Room للقواعد + Worker/AlarmManager لتفعيل الجدولة |

### 10. Onboarding
| الجزء | الحالة | المشكلة |
|-------|--------|---------|
| 7 خطوات مع pager | ✅ UI مكتمل | |
| طلب الأذونات | ✅ UI مكتمل | يفتح الإعدادات |
| **ما يجب بناؤه:** حفظ حالة الإكمال في DataStore + عدم عرض Onboarding مرة أخرى |

### 11. شاشة الحظر — BlockActivity
| الجزء | الحالة | المشكلة |
|-------|--------|---------|
| عرض شاشة الحظر | ✅ UI + Logic مكتمل | نشاط مستقل بملء الشاشة |
| Go Back → Home | ✅ UI + Logic مكتمل | |
| **ما يجب بناؤه:** ربطها مع UsageStatsManager/AccessibilityService لفتحها تلقائياً |

---

## ❌ الميزات الغير موجودة بالمرة (يجب بناؤها من الصفر)

### 1. **App Blocker Service** — حظر التطبيقات
| المطلوب | الحالة |
|---------|--------|
| مراقبة التطبيق الأمامي بـ UsageStatsManager | ❌ غير موجود |
| حظر كامل عبر BlockActivity | ❌ غير مربوط |
| حظر بحد زمني يومي | ❌ غير موجود |
| حظر بجدول زمني | ❌ غير مربوط مع ScheduleScreen |
| Strict Mode (PIN لمنع التعطيل) | ❌ غير موجود |
| كشف فتح التطبيق المحظور | ❌ غير موجود |

### 2. **YouTube Shorts Blocker** — حفظ مقاطع يوتيوب القصيرة
| المطلوب | الحالة |
|---------|--------|
| AccessibilityService ليوتيوب | ❌ غير موجود |
| كشف ViewId/ClassName لـ Shorts | ❌ غير موجود |
| Silent Redirect (GLOBAL_ACTION_BACK) | ❌ غير موجود — موجود فقط لفيسبوك |

### 3. **Anti-Scroll Detection** — كشف التمرير اللانهائي
| المطلوب | الحالة |
|---------|--------|
| TYPE_VIEW_SCROLLED في AccessibilityService | ❌ غير موجود |
| Dialog توقف للحظة | ❌ غير موجود |
| Cooldown system | ❌ غير موجود |

### 4. **NSFW Detection** — كشف المحتوى الإباحي
| المطلوب | الحالة |
|---------|--------|
| MediaProjection API للتصوير | ❌ غير موجود (AiScannerService مجرد stub) |
| TFLite MobileNetV2 | ❌ غير موجود (التبعية موجودة في build.gradle فقط) |
| Strike System (3 كشف → 15 دقيقة حظر) | ❌ غير موجود |
| معالجة الصور على الجهاز | ❌ غير موجود |

### 5. **DNS Filtering** — تصفية DNS
| المطلوب | الحالة |
|---------|--------|
| VpnService يعيد توجيه DNS لـ CleanBrowsing | ❌ غير موجود (DnsVpnService مجرد stub) |
| يعمل في الخلفية بصمت | ❌ غير موجود |
| ربط مع ContentScreen | ❌ غير موجود |

### 6. **نظام الإحصائيات والتقارير**
| المطلوب | الحالة |
|---------|--------|
| حفظ أحداث الحظر في قاعدة بيانات | ❌ غير موجود |
| وقت استخدام كل تطبيق | ❌ غير موجود |
| رسوم بيانية (MPAndroidChart) | ❌ غير موجود (التبعية غير موجودة أصلاً) |
| عداد الأيام المتتالية (streak) | ❌ موجود في `calculateStreak` لكن بلا بيانات |

### 7. **الأمان والحماية المتقدمة**
| المطلوب | الحالة |
|---------|--------|
| منع الحذف عبر Device Admin | ✅ معلن لكن غير مفعل بالكامل |
| EncryptedSharedPreferences لكلمة المرور | ❌ غير موجود (SHA-256 في UiModels فقط) |
| إعادة تشغيل جميع الخدمات بعد BOOT_COMPLETED | 🔶 جزئي (فقط FacebookBlockerService) |

---

## 🔧 ما يحتاج تعديل

### 1. **بنية المعمارية — Architecture**
```
❌ الحالي: لا يوجد ViewModels, لا Room, لا Repository
✅ المطلوب: MVVM + Clean Architecture

ما يجب إضافته:
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt           # Room DB
│   │   ├── BlockEventDao.kt
│   │   ├── AppLimitDao.kt
│   │   ├── ScheduleRuleDao.kt
│   │   └── BlocklistDao.kt
│   └── repository/
│       ├── BlockRepository.kt
│       ├── StatsRepository.kt
│       └── SettingsRepository.kt
├── domain/
│   ├── model/                        # Domain models
│   └── usecase/                      # Use cases
└── di/                               # Dependency injection (Hilt/Koin)
```

### 2. **إعادة تنظيم الملفات**
| الملف الحالي | المشكلة |
|-------------|---------|
| `PermissionsScreen.kt:317` | دالة `checkDeviceAdmin` و `checkUsageAccess` في ملف UI — يجب نقلها إلى utility أو ViewModel |
| `UiModels.kt` | يحتوي على `hashPin()` — يجب نقله إلى domain/utils |
| `PinGate.kt:69` | `FLAG_SECURE` جيد لكن يحتاج ViewModel للتحقق من PIN |

### 3. **تطبيق MVVM تدريجياً**
| الشاشة | ViewModel المطلوب |
|--------|-------------------|
| HomeScreen | `HomeViewModel` — حالة الدرع، الإحصائيات، التأخير |
| SocialScreen | `SocialViewModel` — إعدادات الحظر لكل تطبيق |
| ContentScreen | `ContentViewModel` — حالة VPN، AI Scanner، Device Admin |
| ListsScreen | `ListsViewModel` — القوائم السوداء/البيضاء مع persistence |
| StatisticsScreen | `StatisticsViewModel` — إحصائيات من Room |
| TimeLimitsScreen | `TimeLimitsViewModel` — الحدود الزمنية مع التتبع |
| ScheduleScreen | `ScheduleViewModel` — قواعد الجدولة مع alarm manager |
| ProfileScreen | `ProfileViewModel` — اسم المستخدم والإحصائيات |

### 4. **إصلاحات فورية (High Priority)**
| المشكلة | الموقع | الإصلاح |
|---------|--------|---------|
| Onboarding يعرض كل مرة | `MainActivity.kt` | إضافة فحص `onboarding_complete` في DataStore |
| حالة الأذونات تُقرأ مرة واحدة فقط | `PermissionsScreen.kt:42-47` | إضافة `LaunchedEffect` دوري |
| SocialScreen toggles غير محفوظة | `SocialScreen.kt` | ربط مع DataStore |
| Blocked apps overview فارغ | `SettingsScreen.kt` | ربط مع UsageStatsManager |
| calculateStreak بلا بيانات | `StatisticsScreen.kt:325` | يحتاج Room DB |
| BootReceiver يبدأ خدمة واحدة فقط | `BootReceiver.kt:13` | بدء جميع الخدمات |

### 5. **تبعيات جديدة مطلوبة**
| المكتبة | الغرض |
|---------|-------|
| `androidx.room:room-ktx` | قاعدة بيانات محلية |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | ViewModels (موجود لكن غير مستخدم) |
| `androidx.hilt:hilt-navigation-compose` OR `koin-android` | DI |
| `com.github.PhilJay:MPAndroidChart` | رسوم بيانية (مذكور في المتطلبات) |
| `androidx.security:security-crypto` | تشفير PIN (موجود لكن غير مستخدم) |

---

## 📊 ملخص الأرقام

| الفئة | العدد |
|-------|-------|
| 🟢 UI مكتمل بالكامل | 15 شاشة |
| 🟢 UI + Logic مكتمل | 3 (PermissionsScreen, FacebookBlockerService, ExportImport) |
| 🔶 UI فقط — يحتاج Logic | 12 شاشة |
| ❌ غير موجود بالمرة | 6 ميزات رئيسية |
| 🔧 يحتاج تعديل | 7 نقاط |
| 📦 تبعيات موجودة غير مستخدمة | 3 (TFLite, Security-Crypto, Lifecycle-ViewModel) |

---

## 🗺️ خريطة الطريق المقترحة

### المرحلة 1 — البنية التحتية (الأسبوع 1)
- [ ] إضافة Room DB + DAOs
- [ ] إنشاء Repositories
- [ ] إنشاء ViewModels للشاشات الأساسية
- [ ] ربط DataStore بالموجود

### المرحلة 2 — الحظر والمراقبة (الأسبوع 2)
- [ ] UsageStatsManager Worker لمراقبة التطبيق الأمامي
- [ ] ربط BlockActivity مع AccessiblityService
- [ ] YouTube AccessibilityService
- [ ] تطبيق الحدود الزمنية والجدولة

### المرحلة 3 — الأمان والذكاء الاصطناعي (الأسبوع 3)
- [ ] VpnService الكامل لتصفية DNS
- [ ] MediaProjection + TFLite لـ NSFW
- [ ] EncryptedSharedPreferences لكلمة المرور
- [ ] Deactivation delay الحقيقي
- [ ] Device Admin الكامل

### المرحلة 4 — الإحصائيات والتقارير (الأسبوع 4)
- [ ] تسجيل الأحداث في Room
- [ ] MPAndroidChart للرسوم البيانية
- [ ] عداد الأيام المتتالية
- [ ] تصدير/استيراد الإحصائيات

---

> تم إعداد هذا التقرير بناءً على تحليل كامل لكود المصدر في 20 مايو 2026.
