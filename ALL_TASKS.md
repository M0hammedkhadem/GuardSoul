# 🛡️ Guardian — خطة التطوير الشاملة

> تاريخ الإنشاء: 2026-05-18
> الحالة: قيد التنفيذ

---

## جدول المحتويات
- [أولوية 🔴 حرجة](#أولوية--حرجة)
- [أولوية 🟡 عالية](#أولوية--عالية)
- [أولوية 🟢 متوسطة](#أولوية--متوسطة)
- [أولوية ⚪ مستقبلية](#أولوية--مستقبلية)

---

## أولوية 🔴 حرجة

### 1. 🔴 الـ VPN لا يفعل شيئًا — Porn Blocker وهمي

**الملف:** `app/src/main/java/com/agon/app/services/GuardianVpnService.kt`

**المشكلة:**
الـ VPN ينشئ واجهة `VpnService.Builder` مع:
- DNS: 1.1.1.3 / 1.0.0.3 (Cloudflare for Families)
- Route: `192.0.2.0/24` (نطاق RFC-5737 وهمي — لا يمرر بيانات)
- `setBlocking(false)` — الحزم تمر بدون معالجة

**النتيجة:** ميزة "Porn Blocker" في واجهة المستخدم تُظهر "VPN ACTIVE" لكن لا يتم حظر أي محتوى إباحي.

**الحل:**
- [x] إضافة `addRoute("0.0.0.0", 0)` لتمرير كل حركة المرور عبر VPN
- [x] إضافة `Builder.setMtu()` بالقيمة المناسبة
- [x] إنشاء `HandlerThread` لقراءة الحزم من `ParcelFileDescriptor`
- [x] تحليل حزم DNS (UDP port 53) باستخدام `DatagramPacket` / `ByteBuffer`
- [x] مقارنة أسماء النطاقات مع قائمة المواقع المحظورة من `PornBlockerEngine`
- [x] إعادة توجيه DNS المسموح به إلى Cloudflare 1.1.1.3
- [x] إرجاع `NXDOMAIN` للنطاقات المحظورة

**اختبار التحقق:**
- [ ] التأكد من أن الـ VPN يظهر كـ "متصل" في الإعدادات
- [ ] اختبار حظر نطاق إباحي معروف
- [ ] اختبار أن النطاقات العادية لا تتأثر
- [ ] اختبار إيقاف الـ VPN وعودة الإنترنت للعمل

---

### 2. 🔴 BootReceiver فارغ — لا شيء يُعاد بعد إعادة التشغيل

**الملف:** `app/src/main/java/com/agon/app/receivers/BootReceiver.kt`

**المشكلة:**
بعد إعادة تشغيل الجهاز، الخدمات التالية لا تُعاد:
- ✅ AccessibilityService (Android يعيدها تلقائيًا)
- ❌ GuardianVpnService — لا يُعاد
- ❌ AIExplorerService — لا يُعاد
- ❌ حالة Shield لا تُستعاد في الذاكرة

**الحل:**
- [x] قراءة حالة Shield من DataStore
- [x] إذا كان Porn Blocker نشطًا → إعادة تشغيل VPN
- [x] إذا كان AI Explorer نشطًا → إعادة تشغيل الـ Service (يحتاج MediaProjection، سينتظر حتى يُطلب)

**اختبار التحقق:**
- [ ] إعادة تشغيل الجهاز مع Shield نشط و Porn Blocker
- [ ] التأكد من أن الـ VPN يعمل تلقائيًا بعد الـ Reboot
- [ ] التأكد من أن الـ AI Explorer لا يُبدأ تلقائيًا (يحتاج موافقة المستخدم)

---

### 3. 🔴 ~1000 سطر كود ميت في Engine Layer

**الملفات:**
- `engine/BlockingEngine.kt` (123 سطر)
- `engine/social/SocialBlocker.kt` (75 سطر)
- `engine/social/YouTubeBlocker.kt` (95 سطر)
- `engine/social/SocialMediaRule.kt` (69 سطر)
- `engine/safe/PornBlockerEngine.kt` (181 سطر)
- `engine/safe/SafeSearchEngine.kt` (140 سطر)
- `engine/safe/ContentScanner.kt` (129 سطر)
- `engine/filter/FilterEngine.kt` (164 سطر)
- `engine/filter/AppMatcher.kt` (91 سطر)
- `engine/filter/DomainMatcher.kt` (100 سطر)
- `engine/filter/KeywordMatcher.kt` (87 سطر)

**المشكلة:**
كل هذه الملفات تحتوي على منطق ممتاز لكنها غير مرتبطة بأي Service في التطبيق.

**الحل:**
- [x] إعادة تنظيم (إزالة أو أرشفة) الملفات التي لن تُستخدم
- [x] الاحتفاظ بـ `PornBlockerEngine` للـ VPN المُصلح
- [x] الاحتفاظ بـ `DomainMatcher` و `KeywordMatcher` كأدوات

**ملاحظة:** سيتم الاحتفاظ بـ PornBlockerEngine لاستخدامه في الـ VPN المُصلح. بقية الملفات سيتم نقلها إلى مجلد `_archive` لحين الحاجة.

---

## أولوية 🟡 عالية

### 4. 🟡 BroadcastReceiver مكشوف — ثغرة MediaProjection

**الملف:** `app/src/main/java/com/agon/app/MainActivity.kt` (السطر 78)

**المشكلة:**
```kotlin
registerReceiver(mediaProjectionReceiver, IntentFilter(...), RECEIVER_EXPORTED)
```
أي تطبيق على الجهاز يمكنه إرسال `Broadcast` وهمي يؤدي إلى ظهور نافذة طلب تصوير الشاشة.

**الحل:**
- [x] تغيير `RECEIVER_EXPORTED` إلى `RECEIVER_NOT_EXPORTED` (API 33+)
- [x] للإصدارات الأقدم: إضافة إذن مخصص أو استخدام `LocalBroadcastManager`
- [x] بديل أفضل: استبدال `BroadcastReceiver` بـ `ActivityResultLauncher` مباشر

**اختبار التحقق:**
- [ ] التأكد من أن طلب MediaProjection لا يزال يعمل
- [ ] محاولة إرسال Broadcast من تطبيق آخر — يجب أن يفشل

---

### 5. 🟡 سباق خيوط (Race Condition) في AIExplorerService

**الملف:** `app/src/main/java/com/agon/app/services/AIExplorerService.kt`

**المشكلة:**
- `bannedAppsStatic` هو `MutableMap` بدون أي `synchronized` أو `ConcurrentHashMap`
- يُقرأ من `isAppBanned()` (قد يكون من أي خيط)
- يُكتب من `handleUnsafeContent()` (من خيط coroutine)

**الحل:**
- [x] استبدال `mutableMapOf<String, Long>()` بـ `ConcurrentHashMap<String, Long>()`

**اختبار التحقق:**
- [ ] لا توجد أخطاء وقت التشغيل متعلقة بـ ConcurrentModificationException

---

### 6. 🟡 القوائم المفصولة بفواصل تنكسر

**الملف:** `app/src/main/java/com/agon/app/data/GuardianRepository.kt`

**المشكلة:**
```kotlin
prefs[BLACKLIST_KEYWORDS]?.split(",")?.filter { it.isNotEmpty() }
```
إذا احتوى عنصر على فاصلة (مثل "adult, sex" أو "porn, free")، ينقسم إلى عنصرين.

**الحل:**
- [x] استخدام `encodeBase64` لكل عنصر قبل التخزين
- [x] أو استخدام `JSONArray` بدل الفواصل
- [x] الاحتفاظ بالتوافق مع الإصدارات السابقة (ترحيل البيانات)

**اختبار التحقق:**
- [ ] إضافة عنصر بفاصلة "porn, free" ثم إعادة تشغيل التطبيق — يجب بقاؤه عنصرًا واحدًا
- [ ] إضافة عناصر عادية — لا تتأثر

---

### 7. 🟡 التقاط الشاشة مشوه (Bitmap padding)

**الملف:** `app/src/main/java/com/agon/app/services/AIExplorerService.kt`

**المشكلة:**
```kotlin
val rowPadding = rowStride - pixelStride * image.width
val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, ...)
```
هذا الحساب غير دقيق وقد ينتج صورًا مشوهة أو منزاحة على بعض الأجهزة.

**الحل:**
- [x] استخدام `ImageReader` مع `ImageFormat.YUV_420_888` وتحويل أكثر دقة
- [x] أو استخدام طريقة `PixelCopy` API إن أمكن
- [x] تبسيط المنطق: استخدام `Bitmap.createBitmap(width, height)` بحجم الشاشة الفعلي ونسخ البكسلات سطرًا سطرًا

**اختبار التحقق:**
- [ ] تشغيل AI Explorer على أجهزة مختلفة
- [ ] التأكد من أن الصورة الملتقطة غير مشوهة

---

## أولوية 🟢 متوسطة

### 8. 🟢 إضافة كشف Instagram Reels

**الملف:** `app/src/main/java/com/agon/app/services/GuardianAccessibilityService.kt`

**المشكلة:**
Instagram Reels لا يتم كشفه أو حظره حاليًا.

**الحل:**
- [ ] إضافة `com.instagram.android` إلى قائمة الكشف
- [ ] البحث عن ViewIDs خاصة بـ Instagram Reels
- [ ] استخدام نفس منطق الـ 3 طبقات

---

### 9. 🟢 تحسين BlockActivity

**الملف:** `app/src/main/java/com/agon/app/ui/screens/BlockActivity.kt`

**المشكلة:**
زر "Go Back" يغلق الشاشة فقط لكن لا يمنع المستخدم من إعادة فتح التطبيق المحظور.

**الحل:**
- [ ] إضافة إعادة توجيه إلى الشاشة الرئيسية بدل إغلاق BlockActivity فقط
- [ ] استخدام `Intent(Intent.ACTION_MAIN)` مع `CATEGORY_HOME`
- [ ] إضافة Close button ثانوي للسماح بالخروج

---

### 10. 🟢 Bug Spacer داخل Row في HomeScreen

**الملف:** `app/src/main/java/com/agon/app/ui/screens/HomeScreen.kt` (السطر 454)

**المشكلة:**
```kotlin
Row(...) {
    ...
    if (!permissionsGranted) {
        Spacer(modifier = Modifier.height(32.dp))  // ← يدمّر Row!
        Box(...)
    }
}
```
`Spacer` بارتفاع 32dp داخل `Row` يأخذ مساحة رأسية ويفسد التنسيق.

**الحل:**
- [ ] إزالة `Spacer` أو استخدام `Spacer(Modifier.width(8.dp))` بدل height

---

## أولوية ⚪ مستقبلية

### 11. 🔲 Uninstall Protection الفعلي — (مؤجل)

**الملفات:** `GuardianDeviceAdminReceiver.kt`, `ContentScreen.kt`

عند التفعيل:
- استخدام `DevicePolicyManager.setUninstallBlocked()`
- استخدام `DevicePolicyManager.setLockTaskPackages()` للإصدارات المدعومة
- إضافة إعلام عند محاولة إزالة الصلاحيات

**الحالة:** ⏸️ مؤجل — لا تشرع فيه الآن.

---

### 12. 🔲 Instagram Reels Blocking

إضافة كامل دعم Instagram Reels:
- View IDs للكشف
- 3 طبقات: نقر على تبويبة، سحب أفقي، شبكة أمان

---

### 13. 🔲 تحسينات UI إضافية
- إضافة شاشة إحصائيات متقدمة
- إضافة دعم الجداول الزمنية للمنصات
- إضافة دعم حد الاستخدام اليومي

---

## سير العمل

1. 🔴 يتم إصلاح كل مشكلة 🔴 أولاً
2. 🟡 ثم 🟡
3. 🟢 ثم 🟢

لكل مهمة:
- ✅ قراءة وتحليل الكود الحالي
- ✅ كتابة التعديل
- ✅ التحقق من البناء (Build)
- ✅ اختبار الحالات الحدية
