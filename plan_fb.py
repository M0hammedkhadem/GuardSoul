# كشف دخول مقاطع Reels في فيسبوك — GuardSoul

---

## السؤال

> **ما هي الآلية الأفضل لكشف الدخول لمقاطع Reels في فيسبوك؟ وإذا كان بالإمكان دمج أكثر من تقنية، أخبرني بهم ووصف وجيز لجعل أداة ذكاء اصطناعي تضعها في تطبيق GuardSoul.**

---

## الإجابة

---

## أفضل آليات الكشف

في GuardSoul، عندك خيارات متعددة يمكن دمجها:

---

### 🔍 الآليات المتاحة

#### 1. AccessibilityService — الأساسي والأقوى

تراقب شجرة العناصر (View Hierarchy) وتكشف الـ Reels عبر:

- `viewIdResourceName` يحتوي على:
  - `"com.facebook.katana:id/unified_player"`
  - `"reel"`
  - `"video_container"`
- `contentDescription` يحتوي على كلمة `"Reels"` أو `"ريلز"`
- Package name:
  - `com.facebook.katana`
  - `com.facebook.lite`

> **المشكلة:** فيسبوك يغيّر الـ IDs بشكل متكرر مع التحديثات.

---

#### 2. URL/Navigation Monitoring عبر AccessibilityService

مراقبة `TYPE_WINDOW_CONTENT_CHANGED` والبحث في النصوص عن:

- النصوص: `"Reels"`, `"ريلز"` في الـ window title
- مراقبة التنقل بين الـ tabs

---

#### 3. AI Screen Analysis (TFLite / Vision Model)

لديك بالفعل `ScreenCaptureService` في المشروع — يمكن توسيعه:

- تحليل الصورة للكشف عن وجود فيديو vertical + UI elements خاصة بالـ Reels
- النموذج يكشف:
  - Progress bar في الأسفل
  - أزرار التفاعل الجانبية (like/comment/share عمودية)

---

#### 4. Network Traffic Monitoring (VPN-Based)

لديك بالفعل `VpnService` — يمكن توسيعه لمراقبة:

- طلبات لـ endpoints مثل `graph.facebook.com` مع params خاصة بالـ Reels
- `b-graph.facebook.com/reels`

---

### 🔗 الدمج الأمثل للمشروع

```
AccessibilityService (Primary)   →  وزن 0.60
        +
Network Monitor (Secondary)      →  وزن 0.25
        +
AI Vision (Fallback/Confirmation) →  وزن 0.15
```

---

## 🤖 بناء SmartDetectionEngine

الفكرة المحورية: كل طبقة تُعطي **ثقة (confidence)** من 0.0 إلى 1.0، والـ Engine يجمعها بأوزان:

```kotlin
class SmartDetectionEngine {

    // أوزان كل طبقة
    private val weights = mapOf(
        Layer.ACCESSIBILITY to 0.60f,
        Layer.NETWORK       to 0.25f,
        Layer.AI_VISION     to 0.15f
    )

    private val BLOCK_THRESHOLD = 0.55f

    fun evaluate(signals: Map<Layer, Float>): DetectionResult {
        val score = signals.entries.sumOf { (layer, confidence) ->
            (weights[layer] ?: 0f) * confidence
        }.toFloat()

        return DetectionResult(
            shouldBlock = score >= BLOCK_THRESHOLD,
            confidence  = score,
            triggeredBy = signals.filter { it.value > 0.5f }.keys
        )
    }
}

enum class Layer { ACCESSIBILITY, NETWORK, AI_VISION }
```

---

## معمارية النظام

```
┌─────────────────────────────────────────────────────┐
│         طبقة 1 — AccessibilityService               │
│  Package Name │ View ID Matching │ Content Desc.    │
└──────────────────────┬──────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────┐
│         طبقة 2 — VPN Network Monitor               │
│  Endpoint Detection │ DNS Patterns │ Traffic FP     │
└──────────────────────┬──────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────┐
│         طبقة 3 — AI Vision (Fallback)              │
│  Layout Analysis │ UI Elements │ On-screen OCR      │
└──────────────────────┬──────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────┐
│            SmartDetectionEngine                     │
│   L1×0.6 + L2×0.25 + L3×0.15 ≥ 0.55 → Block       │
└──────────┬──────────────┬──────────────┬────────────┘
           ↓              ↓              ↓
       حجب فوري     تحديث الموديل   إحصائيات
  GLOBAL_ACTION_HOME  ID جديد تلقائي  UsageStats
```

---

## الأولوية العملية لـ GuardSoul

| الطبقة | الوضع الحالي | الخطوة التالية |
|--------|-------------|----------------|
| **AccessibilityService** | ✅ موجود | أضف `viewIdResourceName` matching للـ Reels IDs |
| **VpnService** | ✅ موجود | أضف DNS endpoint filtering لـ `*reels*` |
| **AI Vision (TFLite)** | ✅ موجود | وسّع `ScreenCaptureService` ليكشف vertical UI layout |

---

> **نصيحة عملية:** ابدأ بتعزيز الطبقة الأولى فقط — ستغطي 85% من الحالات.  
> الطبقتان الأخريان كـ fallback لما يغيّر فيسبوك الـ IDs.
