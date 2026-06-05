# PROJECT_MAP.md
_آخر تحديث: 5 يونيو 2026 — طبقة SaaS كاملة، الـ release APK موقّع 29.8 MB (v2) جاهز للرفع_

## Build Status

| Variant | Size | Signed | Verified |
|---|---|---|---|
| `app-debug.apk`   | 50.1 MB | n/a (debug) | ✅ assembles |
| `app-release.apk` | 29.8 MB | v2 (APK Sig v2) | ✅ verifies, R8 minified |

Release command: `gradlew.bat assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease -x uploadCrashlyticsMappingFileRelease --no-daemon`
(Lint و Crashlytics upload يحتاجان `google-services.json` حقيقي — راجع `FIREBASE_SETUP.md`.)

## [TECH_STACK]
| التقنية | الإصدار | الغرض |
|---------|---------|-------|
| Android Gradle Plugin | 8.10.1 | Build system |
| Kotlin | 2.1.0 | اللغة (K2 compiler) |
| Java | 21 | JVM target |
| compileSdk | 36 | Latest |
| minSdk | 26 | Android 8.0+ |
| targetSdk | 35 | Android 15 |
| versionCode | 100 | First SaaS-ready release |
| versionName | 1.0.0 | SaaS milestone |
| Compose BOM | 2026.01.01 | UI |
| Material 3 | من BOM | Design system |
| Navigation-Compose | 2.8.9 | التنقل |
| Lifecycle | 2.10.0 | ViewModel + collectAsStateWithLifecycle |
| Activity-Compose | 1.10.1 | Activity integration |
| DataStore | 1.2.0 | Preferences |
| Security-Crypto | 1.1.0 | EncryptedSharedPreferences (PIN, OAuth) |
| Room | 2.8.4 | Local DB + DAOs |
| KSP | 2.1.0-1.0.29 | Room codegen |
| Coroutines | 1.10.1 | Async |
| Firebase BoM | 33.12.0 | Auth + Firestore + FCM + Analytics + Crashlytics |
| Firebase Auth | 33.12.0 | تسجيل الدخول (Email + Google + Anonymous) |
| Firebase Firestore | 33.12.0 | Cloud sync (blocklists, settings) |
| Firebase Crashlytics | 33.12.0 | Crash reporting |
| Firebase Analytics | 33.12.0 | Event tracking |
| Play Billing | 7.1.1 | اشتراكات SaaS |
| Play In-App Review | 2.0.2 | طلب التقييم |
| UMP (User Messaging Platform) | 2.2.0 | GDPR/CCPA consent |
| Koin | 4.0.2 | Dependency injection |
| Timber | 5.0.1 | Logging |
| MPAndroidChart | v3.1.0 | رسوم بيانية |
| TensorFlow Lite | 2.16.1 | NSFW on-device AI |

## [ARCHITECTURE]
```
com.agon.app (GuardSoul)
│
├── GuardianApp.kt              ← Application + Koin + Firebase init + consent
├── MainActivity.kt              ← Compose root + NavHost
│
├── billing/                     ← SaaS monetization layer
│   ├── BillingClientWrapper.kt  ← Play Billing v7.1.1 (suspend surface)
│   ├── BillingManager.kt        ← Tier gate + product/feature map
│   └── SubscriptionTier.kt      ← enum FREE / PRO / PREMIUM
│
├── account/                     ← SaaS account layer
│   ├── AuthRepository.kt        ← Firebase Auth (email/google/anonymous)
│   ├── UserSession.kt           ← sealed SignedIn / SignedOut
│   └── CloudSyncRepository.kt   ← Firestore push/pull (blocklists + settings)
│
├── analytics/                   ← SaaS observability layer
│   ├── AnalyticsManager.kt      ← Firebase Analytics facade (gated by consent)
│   ├── CrashReporter.kt         ← Crashlytics facade (gated by consent)
│   └── ReviewPrompt.kt          ← In-App Review with eligibility
│
├── consent/                     ← GDPR/CCPA
│   └── ConsentManager.kt        ← Google UMP wrapper + DataStore persistence
│
├── data/                        ← Core persistence
│   ├── settings/AppSettings.kt  ← 100+ typed DataStore keys
│   ├── local/ (Room)            ← AppDatabase + DAOs
│   ├── repository/AppRepository.kt
│   └── remote/FirebaseManager.kt
│
├── services/                    ← Background services
│   ├── AppBlockerService        ← AccessibilityService + UsageStats
│   ├── ShortstopAccessibilityService ← Surgical blocking (FB Reels, YT Shorts, IG Reels, TikTok, Snap)
│   ├── PornBlockerService       ← DNS keyword filter
│   ├── DnsVpnService            ← VpnService to CleanBrowsing
│   ├── AiScannerService         ← MediaProjection + TFLite
│   └── GuardianAccessibilityService
│
├── ui/
│   ├── screens/
│   │   ├── HomeScreen           ← Shield orb + counters
│   │   ├── UpgradeScreen        ← NEW SaaS paywall (3-tier comparison)
│   │   ├── AccountScreen        ← NEW SaaS account hub
│   │   ├── AuthScreen           ← NEW email/Google/anonymous
│   │   ├── PrivacyPolicyScreen  ← NEW
│   │   ├── TermsOfServiceScreen ← NEW
│   │   ├── SocialScreen, ContentScreen, ListsScreen, …
│   │   └── OnboardingScreen
│   ├── components/PinGate.kt
│   └── theme/
│
├── receivers/                   ← 8 broadcast receivers (boot, adb, clones, …)
├── blocking/                    ← Shortstop engine: PatternMatcher, ScrollInterception, ShortsMaskOverlay, BlockingConfig, RuleEngine, AiBlockTracker, DayOfWeekUtil
├── utils/                       ← AppLogger, CategoryRegistry, StudyRoom, …
│
└── di/AppModule.kt              ← Koin modules: database, settings, repository, utils, analytics, billing, account
```

## [SYSTEM_FLOW]

### First launch (onboarding)
```
Splash → UMP consent (EEA only) → Anonymous Firebase Auth →
Onboarding 7-step pager (Welcome, Language, Name, Accessibility, VPN,
Device Admin, Overlay, Usage, Notifications) → setOnboardingComplete(true) →
Cloud sync anonymous profile → HomeScreen
```

### Subscription purchase
```
HomeScreen → tap Pro/Premium → UpgradeScreen renders products →
tap Subscribe → BillingManager.purchase(activity, sku) →
BillingClientWrapper launches Play Billing flow →
onPurchasesUpdated → setSubscriptionTier in DataStore →
canAccess(feature) returns true → re-render UI
```

### Cloud sync (Pro+ only)
```
User toggles cloudSyncEnabled in AccountScreen →
CloudSyncRepository.enable(true) → pushAsync() →
FirebaseAuth.currentUser?.uid → firestore.set("users/$uid", snapshot)
Pull: pullAsync() on app start if enabled → applySnapshot()
```

### Crash reporting
```
Uncaught exception → Crashlytics.recordException() (only if consent=true) →
Firebase Crashlytics dashboard
```

### GDPR/CCPA consent
```
MainActivity.onCreate → ConsentManager.ensureConsent(activity) →
if EEA + first launch → loadConsentForm → show → persistDecisions() →
consentAnalytics + consentCrash written to DataStore →
applyPersistedDecisions() gates AnalyticsManager + CrashReporter
```

## [VERIFIABLE_GOALS]
- [x] **G1** — Google Play Billing wired (BillingClientWrapper, BillingManager)
- [x] **G2** — 3 plans: Free / Pro / Premium with comparison table
- [x] **G3** — Cloud sync (CloudSyncRepository push/pull with Firestore)
- [x] **G4** — Firebase Auth (Email + Google + Anonymous)
- [x] **G5** — Crashlytics (CrashReporter with consent gate)
- [x] **G6** — Firebase Analytics (AnalyticsManager with consent gate)
- [x] **G7** — In-App Review (ReviewPrompt with usage threshold)
- [x] **G8** — Privacy Policy + Terms of Service (LegalScreens)
- [x] **G9** — GDPR/UMP consent (ConsentManager)
- [x] **G10** — Onboarding (7 steps + welcome)
- [x] **G11** — Settings Hub (Account/Subscription/Privacy/Terms/About)
- [x] **G12** — Multi-language (ar + en)
- [x] **G13** — App version 1.0.0
- [x] **G14** — Debug APK built (45.8 MB)
- [x] **G15** — ProGuard rules updated for new deps
- [x] **G16** — PROJECT_MAP.md updated

## [ORPHANS & PENDING]
> _كل ما لم يُربط بعد يظهر هنا. يُحذف فور اكتماله في مرحلة التنفيذ._

_(empty — all planned SaaS features shipped and wired)_
