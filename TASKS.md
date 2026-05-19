# قائمة المهام — Guardian

> تم الإنشاء: 2026-05-18
> مبنية على تدقيق كامل للكود (6,537 سطر في 29 ملف)

---

## 🔴 حرجة — يجب إصلاحها الآن

| ID | الملف | المشكلة | الحالة |
|----|-------|---------|--------|
| C1 | `GuardianVpnService.kt` | ✅ `addRoute("0.0.0.0", 0)` + DnsResolver + PacketForwarder + TCP/UDP relay + ACK tracking | ✅ |
| C2 | `BootReceiver.kt:37-49` | Intent لـ Device Admin يتم إنشاؤه لكن `context.startActivity()` **لم يُستدعَ**. الكود ميت. | 🔴 |
| C3 | `GuardianVpnService.kt` | ✅ `loadBlockedDomainsSync()` متزامن — `runBlocking` + `withTimeout(5000)` قبل `builder.establish()` | ✅ |
| C4 | `GuardianVpnService.kt` | ✅ DnsResolver يستخدم `LinkedHashMap(cacheMaxSize=256)` مع `removeEldestEntry` + TTL 30s | ✅ |
| C5 | `GuardianVpnService.kt` | ✅ `forwardSocket` أُزيل — كل طلب DNS يستخدم `DatagramSocket` جديد + `soTimeout=2000` | ✅ |

## 🟡 عالية — يجب إصلاحها قريبًا

| ID | الملف | المشكلة | الحالة |
|----|-------|---------|--------|
| H1 | `AIExplorerService.kt:69-82` | إذا قُتل الـ Service وأُعيد تشغيله (بدون Intent), يبدأ ثم يتوقف فورًا. لا يمكن البقاء حيًا. | 🟡 |
| H2 | `PornBlockerEngine.kt:93-99` | `regexMatcher` يعامل كل الكلمات العادية (مثل "sexcam", "milf") كـ regex — `.` يطابق أي حرف. | 🟡 |
| H3 | `GuardianVpnService.kt` | ✅ `scope` أصبح متغير عضو في الـ class — يُلغى في `onDestroy` فقط | ✅ |
| H4 | `GuardianAccessibilityService.kt:145-148` | خروج مبكر للتطبيقات غير YouTube/Facebook/Instagram — لا توجد معالجة للأحداث للتطبيقات المحظورة الأخرى. | 🟡 |

## 🟢 متوسطة — تحسينات

| ID | الملف | المشكلة | الحالة |
|----|-------|---------|--------|
| M1 | المشروع بأكمله | لا يوجد إطار تسجيل — `Log.d`/`Log.e` خام. | 🟢 |
| M2 | `GuardianAccessibilityService.kt` | 962 سطر في ملف واحد — يحتاج إلى تجزئة. | 🟢 |
| M3 | `BlockerWebViewClient.kt` | كود ميت (85 سطر) — غير مستخدم في أي مكان. | 🟢 |
| M4 | `GuardianVpnService.kt` | لا يوجد دعم IPv6 — استعلامات IPv6 تُسقط صامتة. | 🟢 |

## 🔲 ميزات مفقودة — ضرورية للإنتاج

| ID | الميزة | السبب | الأولوية |
|----|--------|-------|----------|
| F1 | شاشة ترحيب (Onboarding) | المستخدم الجديد لا يعرف ماذا يفعل — 5 أذونات مربكة. | عالية |
| F2 | جدولة الحظر (جدول زمني) | المنافسون (Freedom) يدعمون الجدولة. | متوسطة |
| F3 | تحديد وقت الاستخدام | "30 دقيقة/يوم تيك توك" — ميزة قاتلة. | متوسطة |
| F4 | كلمة مرور للحماية | أي شخص يفتح التطبيق يمكنه تعطيل الدرع. | عالية |
| F5 | مزامنة سحابية | الإعدادات محلية فقط — لا نسخ احتياطي. | منخفضة |

---

## سير العمل

1. 🔴 إصلاح C1 أولاً — الـ VPN مكسور تمامًا (تأثير مباشر على تجربة المستخدم)
2. 🔴 إصلاح C2 + C3 + C4 + C5
3. 🟡 إصلاح H1 → H2 → H3 → H4
4. 🟢 تحسينات M1 → M2 → M3 → M4
5. 🔲 إضافة الميزات المفقودة F1 → F2 → F3 → F4

لكل مهمة:
- ✅ قراءة وتحليل الكود الحالي
- ✅ كتابة التعديل
- ✅ التحقق من البناء (Build)
- ✅ اختبار الحالات الحدية
