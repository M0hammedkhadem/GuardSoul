# آلية الحظر الجزئي لليوتيوب (YouTube Shorts Blocker)

## نظرة عامة

يتكون نظام الحظر الجزئي لليوتيوب من 3 أوضاع يتحكم بها المستخدم:

| الوضع | القيمة | السلوك |
|-------|--------|--------|
| **إيقاف** | `"off"` | يوتيوب يعمل بشكل طبيعي دون أي حظر |
| **حظر كامل** | `"full"` | يمنع فتح تطبيق يوتيوب نهائياً (إعادة توجيه إلى `BlockActivity`) |
| **حظر Shorts فقط** | `"shorts"` | يسمح بتصفح المحتوى العادي ويمنع فقط فيديوهات Shorts |

---

## تدفق العمل (Flowchart)

```
AccessibilityEvent (TYPE_WINDOW_STATE_CHANGED / TYPE_WINDOW_CONTENT_CHANGED)
    │
    ▼
GuardSoulAccessibilityService.onAccessibilityEvent()
    │
    ▼
ShortstopEngine.onAccessibilityEvent(event, root)
    │
    ├─► 1. هل التطبيق في white list؟ ──► تجاهل (return)
    │
    ├─► 2. هل التطبيق في temp-ban؟ ──► handleFullBlock(isTempBan=true)
    │
    ├─► 3. هل isFullBlockRequired(pkg)؟
    │       • cachedBlockedApps.contains(pkg)
    │       • cachedYoutubeMode == "full"
    │       └─► handleFullBlock(pkg)
    │
    └─► 4. هل isFeedBlockingEnabled(pkg)?
            • cachedYoutubeMode == "shorts"
            • matcher.signatureFor(pkg) != null
            • matcher.findFeedViewId(root, pkg, sig) != null
            └─► handleFeedBlock(pkg)
                    │
                    ├─► GLOBAL_ACTION_BACK (العودة للصفحة الرئيسية)
                    ├─► FeedBlockOverlay.show() (رسالة عابرة)
                    └─► tempBan.recordStrike(pkg)
                            │
                            └─► 3 ضربات في 4 دقائق → حظر 15 دقيقة
```

---

## تفاصيل التنفيذ البرمجي

### 1. تخزين الإعدادات — AppSettings.kt

**الملف:** `app/src/main/java/com/agon/app/data/settings/AppSettings.kt`

يُخزّن وضع يوتيوب في DataStore تحت المفتاح `youtube_mode`:

```kotlin
// السطر 34 — تعريف المفتاح
val YOUTUBE_MODE = stringPreferencesKey("youtube_mode")

// السطر 97 — Flow للتحديثات اللحظية
val youtubeModeFlow: Flow<String> = context.settingsStore.data.map {
    it[Keys.YOUTUBE_MODE] ?: "off"
}

// السطر 205 — دالة التحديث
suspend fun setYoutubeMode(v: String) {
    context.settingsStore.edit { it[Keys.YOUTUBE_MODE] = v }
}

// السطر 271 — قراءة متزامنة
suspend fun getYoutubeMode(): String =
    context.settingsStore.data.first()[Keys.YOUTUBE_MODE] ?: "off"
```

**الآلية:** DataStore (بروتوكول Jetpack) يخزّن القيم في ملف XML داخل `app_settings.preferences_pb`. التخزين يعتمد على Protobuf → سريع وآمن من التلف. قراءة `youtubeModeFlow` تكون reactive: أي تغيير ينعكس فوراً على `cachedYoutubeMode` في `ShortstopEngine` (السطر 56).

---

### 2. استقبال الأحداث — GuardSoulAccessibilityService.kt

**الملف:** `app/src/main/java/com/agon/app/services/GuardSoulAccessibilityService.kt`

#### التسجيل في الخدمة (onServiceConnected, السطور 46-88)

```kotlin
// السطور 52-59 — تهيئة أنواع الأحداث المطلوبة
info.apply {
    eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
    flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
}
```

**نوعا الأحداث المطلوبان:**
- `TYPE_WINDOW_STATE_CHANGED` — يُرسل عند فتح نافذة جديدة أو تغيير الشاشة بالكامل
- `TYPE_WINDOW_CONTENT_CHANGED` — يُرسل عند تغيير المحتوى داخل النافذة الحالية

**الفلاغات الأساسية:**
- `FLAG_REPORT_VIEW_IDS` — ضروري جداً للبحث عن viewId مثل `reel_watch_fragment_root`
- `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` — يسمح بقراءة شجرة الـ AccessibilityNodeInfo

**إعدادات الخدمة في XML** (`guardsoul_service_config.xml`):

```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews|flagReportViewIds"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="50" />
```

**ملاحظة مهمة:** `packageNames` مضبوط على `null` — هذا يعني أن الخدمة تستمع لجميع التطبيقات، وليس فقط يوتيوب.

#### معالجة الحدث (onAccessibilityEvent, السطور 136-162)

```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent) {
    val settings = (applicationContext as GuardianApp).repository.getAppSettings()
    if (!settings.isShieldActiveSync()) return          // الدرع غير نشط → تجاهل

    val pkg = event.packageName?.toString() ?: return   // الحصول على package name
    var root: AccessibilityNodeInfo? = null
    try {
        root = rootInActiveWindow                       // شجرة الـ View الحالية
        shortstop.onAccessibilityEvent(event, root)     // ← ShortstopEngine
    } finally {
        try { root?.recycle() } catch (_: Exception) {} // تحرير الذاكرة فوراً
    }
}
```

**لماذا `rootInActiveWindow`؟** هذا هو الفرق الجوهري بين الخدمة وطريقة الـ `MediaProjection`: الخدمة لا تحتاج لالتقاط شاشة ولا لطلب أذونات إضافية، بل تقرأ شجرة الواجهة مباشرة.

---

### 3. محرك الحظر — ShortstopEngine.kt

**الملف:** `app/src/main/java/com/agon/app/blocking/ShortstopEngine.kt`

#### التخزين المؤقت للإعدادات (الأسطر 38-62)

يتم تخزين وضع يوتيوب في متغير `@Volatile` يتم تحديثه عبر Flow:

```kotlin
@Volatile private var cachedYoutubeMode = "off"

fun start() {
    serviceScope.launch {
        val app = host.applicationContext as GuardianApp
        val settings = app.repository.getAppSettings()
        
        launch { settings.youtubeModeFlow.collect {
            cachedYoutubeMode = it     // تحديث فوري عند تغيير الإعدادات
        } }
    }
}
```

استخدام `@Volatile` يضمن رؤية القيمة عبر الخيوط — مهم لأن التحديثات تأتي من `Dispatchers.Default` بينما قراءة `cachedYoutubeMode` تحدث على main thread في `onAccessibilityEvent`.

#### منطق الحظر الكامل (الأسطر 100-111)

```kotlin
private fun isFullBlockRequired(pkg: String): Boolean {
    if (cachedBlockedApps.contains(pkg)) return true
    return when {
        pkg == "com.google.android.youtube" && cachedYoutubeMode == "full" -> true
        // ... باقي التطبيقات
        else -> false
    }
}
```

#### منطق حظر Shorts (الأسطر 113-119)

```kotlin
private fun isFeedBlockingEnabled(pkg: String): Boolean {
    return when {
        pkg == "com.google.android.youtube" && cachedYoutubeMode == "shorts" -> true
        // ... باقي التطبيقات
        else -> false
    }
}
```

#### تنفيذ حظر Shorts (الأسطر 141-161)

```kotlin
private fun handleFeedBlock(pkg: String, now: Long) {
    val last = lastActionMs[pkg] ?: 0L
    if (now - last < actionCooldownMs) return  // منع التكرار: 1 ثانية
    lastActionMs[pkg] = now

    // 1. إرجاع قسري إلى الشاشة السابقة (Home Feed)
    host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    
    // 2. إظهار رسالة عابرة
    feedBlockOverlay?.show()

    // 3. تسجيل ضربة (3 ضربات = حظر 15 دقيقة)
    tempBan.recordStrike(pkg) { bannedPkg ->
        Timber.w("Temp ban triggered for $bannedPkg")
    }

    // 4. تسجيل الحدث في قاعدة البيانات
    serviceScope.launch {
        val app = (host.applicationContext as GuardianApp)
        app.repository.recordBlock(pkg, matcher.surfaceFor(pkg).label, "social_feed")
    }
}
```

**لماذا `GLOBAL_ACTION_BACK` وليس `GLOBAL_ACTION_HOME`؟**
- `GLOBAL_ACTION_HOME` يطرد المستخدم من التطبيق بالكامل إلى الشاشة الرئيسية — هذا مخصص للحظر الكامل
- `GLOBAL_ACTION_BACK` يعيده إلى الشاشة السابقة داخل التطبيق نفسه (الصفحة الرئيسية ليوتيوب) — المستخدم يبقى داخل يوتيوب لكنه لا يرى Shorts

**منع التكرار:** استخدام `lastActionMs` مع `actionCooldownMs = 1000L` يمنع تنفيذ الحظر أكثر من مرة في الثانية.

---

### 4. محرك الأنماط — PatternMatcher.kt

**الملف:** `app/src/main/java/com/agon/app/blocking/PatternMatcher.kt`

#### تعريف التوقيعات (الأسطر 21-94)

لكل تطبيق، نحدد قائمة من viewId tokens التي تميز شاشة الفيديوهات القصيرة:

```kotlin
"com.google.android.youtube" to Signature(
    surfaceViewIdTokens = listOf(
        "reel_watch_fragment_root",    // شاشة Shorts الرئيسية
        "reel_recycler",               // قائمة Shorts الرأسية
        "reel_player_page_controller",  // تحكم المشغل
        "shorts_player",               // مشغل Shorts
        "reel_player",                 // مشغل Reel قديم
        "shorts_video_player_view",     // عرض الفيديو
        "reels_player",                // مشغل Reels
    ),
)
```

#### آلية البحث (الأسطر 125-134)

```kotlin
fun findFeedViewId(root: AccessibilityNodeInfo, pkg: String, sig: Signature): String? {
    for (token in sig.surfaceViewIdTokens) {
        // البحث يستخدم findAccessibilityNodeInfosByViewId
        val matches = root.findAccessibilityNodeInfosByViewId("$pkg:id/$token")
        if (matches.isNotEmpty()) {
            matches.forEach { it.recycle() }
            return token
        }
    }
    return null
}
```

**ماذا يفعل هذا الكود؟**

في Android، كل View له resource ID (viewId) يتكون من:
- `package name` (مثال: `com.google.android.youtube`)
- `:id/`
- `resource name` (مثال: `reel_watch_fragment_root`)

الدالة `findAccessibilityNodeInfosByViewId` تبحث في شجرة الـ View Nodes عن أي node يحتوي على هذا الـ viewId المحدد. إذا وجدته، هذا يعني أن المستخدم موجود حالياً على شاشة Shorts.

**مصدر هذه الـ viewIds:** يتم استخراجها عبر تحليل APK ليوتيوب (decompile باستخدام jadx/apktool) أو عبر تشغيل يوتيوب مع Android Studio/Layout Inspector ورؤية هيكل الـ Layout الفعلي.

#### الأنماط عبر التحديث عن بُعد (patterns.json)

**الملف:** `app/src/main/assets/patterns.json`

```json
{
  "version": 1,
  "signatures": [
    {
      "id": "youtube_shorts",
      "packageName": "com.google.android.youtube",
      "feedViewIds": [
        "reel_player_fragment",
        "shorts_container",
        "shorts_player",
        "shorts_watch_fragment"
      ],
      "contentDescriptions": ["shorts"],
      "classNames": ["ReelWatchActivity", "ShortsActivity"]
    }
  ]
}
```

**طبقة إضافية للكشف:** بالإضافة إلى viewId، يمكن الكشف عن Shorts عبر:
1. **contentDescription:** البحث عن أي node في الشاشة يحتوي على وصف "shorts"
2. **className:** التحقق من أن الـ Activity الحالية هي `ReelWatchActivity` أو `ShortsActivity`

هذه الطبقات الثلاث (viewId + contentDescription + className) تعطي **تغطية شاملة** حتى لو تغير أحدهم في تحديث جديد.

---

### 5. رسالة الحظر العابرة — FeedBlockOverlay.kt

**الملف:** `app/src/main/java/com/agon/app/blocking/FeedBlockOverlay.kt`

#### المبدأ

بدلاً من فتح `BlockActivity` (الذي يطرد المستخدم خارج التطبيق)، نعرض overlay عابر لمدة 2.5 ثانية:

```kotlin
fun show(messageResId: Int = R.string.feed_block_message) {
    mainHandler.post {
        removeInternal()  // إزالة أي overlay سابق

        val view = LayoutInflater.from(host.applicationContext)
            .inflate(R.layout.feed_block_toast, null) as TextView
        view.text = "تم الحظر — العودة إلى الصفحة الرئيسية"

        // استخدام TYPE_ACCESSIBILITY_OVERLAY (Android 8+)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            MATCH_PARENT, WRAP_CONTENT, type,
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            y = -200  // أعلى المنتصف بقليل
        }

        val wm = host.getSystemService(WINDOW_SERVICE) as WindowManager
        wm.addView(view, params)
        overlayView = view

        // إخفاء تلقائي بعد 2.5 ثانية
        mainHandler.postDelayed({ removeInternal() }, 2500L)
    }
}
```

**لماذا `TYPE_ACCESSIBILITY_OVERLAY`؟** هذا النوع من النوافذ لا يتطلب إذن `SYSTEM_ALERT_WINDOW`، ويظهر فقط عندما تكون الـ AccessibilityService نشطة — مثالي لتطبيقات الرقابة الأبوية.

---

### 6. نظام الحظر المؤقت (Temp Ban) — TempBanManager.kt

**الملف:** `app/src/main/java/com/agon/app/blocking/TempBanManager.kt`

#### القاعدة: 3 ضربات = 15 دقيقة حظر

```
┌─────────────────────────────────────┐
│ الضربة 1 ──┬── في 4 دقائق           │
│ الضربة 2 ──┼── 3 ضربات              │
│ الضربة 3 ──┘── → حظر 15 دقيقة       │
└─────────────────────────────────────┘
```

#### آلية العمل

```kotlin
fun recordStrike(pkg: String, onTempBanTriggered: ((String) -> Unit)? = null): Boolean {
    val now = System.currentTimeMillis()

    scope.launch {
        context.tempBanStore.edit { prefs ->
            val raw = prefs[strikesKey(pkg)] ?: "[]"
            val strikes = Json.decodeFromString<MutableList<Long>>(raw)

            strikes.add(now)  // إضافة الضربة الجديدة

            // إزالة الضربات الأقدم من 4 دقائق
            val cutoff = now - STRIKES_WINDOW_MS
            strikes.removeAll { it < cutoff }

            prefs[strikesKey(pkg)] = Json.encodeToString(strikes)

            // إذا 3 ضربات → تفعيل الحظر
            if (strikes.size >= STRIKES_THRESHOLD) {
                val cooldownEnd = now + COOLDOWN_MS  // 15 دقيقة
                prefs[cooldownKey(pkg)] = cooldownEnd
                cooldownCache[pkg] = cooldownEnd
                strikes.clear()
                prefs[strikesKey(pkg)] = Json.encodeToString(strikes)
                onTempBanTriggered?.invoke(pkg)
            }
        }
    }

    return isInCooldown(pkg)
}
```

**التخزين:** DataStore باسم `temp_ban` يحتوي على:
- `strikes:com.google.android.youtube` → `[1712345678000, 1712345878000, 1712346078000]`
- `cooldown:com.google.android.youtube` → `1712346978000`

**التحقق السريع:** يستخدم `cooldownCache` في الذاكرة (HashMap) لتجنب قراءة DataStore في كل حدث:

```kotlin
fun isInCooldown(pkg: String): Boolean {
    val cached = cooldownCache[pkg]
    if (cached != null) {
        if (System.currentTimeMillis() < cached) return true
        cooldownCache.remove(pkg)  // انتهت المدة
    }
    return false
}
```

---

### 7. الحظر الكامل — BlockActivity.kt

**الملف:** `app/src/main/java/com/agon/app/ui/BlockActivity.kt`

عند الحظر الكامل:

```kotlin
// في GuardSoulAccessibilityService.kt — السطور 112-134
fun blockApp(pkg: String) {
    // 1. إرجاع للخلف (لا نستخدم HOME)
    performGlobalAction(GLOBAL_ACTION_BACK)

    // 2. بعد 150ms نفتح شاشة الحظر
    mainHandler.postDelayed({
        val intent = Intent(this, BlockActivity::class.java).apply {
            putExtra("app_name", appName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(intent)
    }, 150L)
}
```

**لماذا 150ms تأخير؟** ننتظر حتى يكتمل الـ GLOBAL_ACTION_BACK قبل فتح BlockActivity لتجنب مشاكل تزامن النشاطات.

---

## ملخص دقيق للخوارزمية

```
لكل حدث وصولية (AccessibilityEvent):

1. تجاهل إذا كان الدرع غير نشط (shieldActive == false)
2. تجاهل إذا كان Package Name فارغاً
3. تجاهل إذا package في القائمة البيضاء (whitelist)
4. تجاهل إذا package له حظر مؤقت (temp-ban فعال) → حظر كامل فوري

5. إذا كان الحظر الكامل مفعّلاً على هذا التطبيق:
   → GLOBAL_ACTION_BACK + افتح BlockActivity بعد 150ms
   → سجّل ضربة للـ temp-ban

6. إذا كان حظر Shorts مفعّلاً:
   ابحث عن viewId الخاص بـ Shorts في شجرة الواجهة
   إذا وُجِد:
     → GLOBAL_ACTION_BACK (ارجع للصفحة الرئيسية)
     → FeedBlockOverlay.show() (رسالة عابرة لمدة 2.5 ثانية)
     → سجّل ضربة للـ temp-ban
     → إذا وصلت 3 ضربات → حظر 15 دقيقة
```

## الملفات المعنية كاملة

| الملف | الدور |
|-------|-------|
| `services/GuardSoulAccessibilityService.kt` | استقبال الأحداث من النظام وتوزيعها |
| `blocking/ShortstopEngine.kt` | محرك الحظر الرئيسي — قرار الحظر الكامل/الجزئي |
| `blocking/PatternMatcher.kt` | التعرف على شاشات Shorts عبر viewId |
| `blocking/FeedBlockOverlay.kt` | عرض رسالة حظر عابرة بدون طرد المستخدم |
| `blocking/TempBanManager.kt` | 3 ضربات في 4 دقائق = حظر 15 دقيقة |
| `blocking/WhitelistCache.kt` | القائمة البيضاء — إعفاء تطبيقات/كلمات/مواقع |
| `data/settings/AppSettings.kt` | تخزين وضع يوتيوب (off/full/shorts) |
| `ui/BlockActivity.kt` | شاشة الحظر الكامل (للتطبيقات الممنوعة بالكامل) |
| `assets/patterns.json` | أنماط محدثة عن بُعد لجميع التطبيقات |

## متطلبات النظام

- **Android 8.0+ (API 26)** كحد أدنى — لاستخدام `TYPE_ACCESSIBILITY_OVERLAY`
- **إذن AccessibilityService** مع `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` و `FLAG_REPORT_VIEW_IDS`
- **لا حاجة لـ MediaProjection** — كل شيء يتم عبر AccessibilityNodeInfo
