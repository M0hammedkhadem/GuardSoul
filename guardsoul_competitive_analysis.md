# GUARDIAN — SaaS Production Roadmap
# Last Updated: 2026-05-19 — ALL 21 tasks DONE + 3 critical bugs fixed
# Agent: Read this file from top to bottom before doing ANYTHING.
# Never skip. Never assume. Never mark done without device verification.
---

## 🤖 AGENT PROTOCOL — READ THIS FIRST, EVERY TIME

You are a Senior Android Engineer working on Guardian.
Before writing a single line of code, follow this protocol:

**STEP 1 — READ**
Read this entire file. Every line. Do not skip.

**STEP 2 — FIND YOUR POSITION**
Locate the first task that is NOT marked ✅ DONE.
That is your current task. Only that task. Nothing else.

**STEP 3 — RESEARCH**
Before coding, study the Reference App listed for that task.
Understand their mechanism. If open source, find the exact file.
If not, reason from the app's behavior what the implementation must be.

**STEP 4 — PLAN**
Write your implementation plan in 5 lines or less.
State what files you will change, create, or delete.

**STEP 5 — IMPLEMENT**
Write the complete, production-quality code.
No TODOs. No placeholders. No half-fixes.

**STEP 6 — VERIFY**
Run the 3-step verification test listed under the task.
Do not proceed until all 3 pass.

**STEP 7 — UPDATE THIS FILE**
Mark the task ✅ DONE.
Add one line: what you changed and what file.
Then move to STEP 2 and find the next task.

**QUALITY BAR — Every task must meet ALL of these before ✅:**
- [ ] Works after device restart
- [ ] Works after app is killed by system
- [ ] Handles permission denied gracefully (no crash)
- [ ] No memory leak introduced
- [ ] Consistent UI state with actual system state
- [ ] Would pass Google Play review
- [ ] Edge cases handled (empty state, null, timeout)

---

## APP CONTEXT

Package: com.agon.app (target: com.guardsoul)
Stack: Kotlin, Jetpack Compose, Material3, DataStore, TFLite, VPN Service, Accessibility Service
Target: Production SaaS digital wellness app
State at start: Pre-alpha with critical bugs
Current progress: 9/21 tasks complete (43%)

## Feature 1: Local VPN DNS-based Adult Content Filtering (CleanBrowsing / Quad9 / Cloudflare Family DNS)

### 🏆 Top Competitors
| App | Platform | Rating | Downloads |
|-----|----------|--------|-----------|
| CleanBrowsing | Android | N/A (direct APK) | 4.5M+ devices served |
| Cloudflare 1.1.1.1 (WARP) | Android | 4.3★ | 50M+ |
| Blokada | Android | 3.0★ | 1M+ |
| NextDNS | Android | 4.6★ (Softonic) | 100K+ |

### 🔍 Implementation Analysis

#### CleanBrowsing Android App
- **Mechanism:** Configures Android's Private DNS (DNS-over-TLS, `android.net.ConnectivityManager`) automatically. Falls back to a local VPN tunnel with DNS-over-HTTPS (DoH) when Private DNS is blocked or unavailable. Locks down Accessibility and Device Admin settings to prevent bypass.
- **Technical Approach:** Dual-path architecture — prefers Private DNS (DoT) on Android 9+, falls back to VpnService with DoH resolver for older devices or restricted networks. Uses certificate pinning for security. Tamper-proof via Device Admin + Accessibility lock combination.
- **Why It Works Well:** Automatic setup flow eliminates manual configuration. The VPN fallback ensures filtering works even on networks that block DoT (some corporate/school Wi-Fi). Processing 355B+ DNS requests/month across 70 data centers gives them enterprise-grade reliability.
- **Limitations:** Free tier has limited customization. VPN fallback mode can conflict with other VPN apps. Android 13+ requires "Allow Restricted Settings" workaround for accessibility lock.

#### Cloudflare 1.1.1.1 (WARP)
- **Mechanism:** Uses VpnService to create a local VPN tunnel that routes all DNS queries through Cloudflare's encrypted resolvers (1.1.1.3 for family filtering). Implements DNS-over-HTTPS (DoH) and DNS-over-TLS (DoT).
- **Technical Approach:** Cloudflare's WireGuard-based WARP protocol for the full VPN mode, or simple DNS proxy for DNS-only mode. Family filtering uses Cloudflare's enterprise categorization database. Ranked #1 fastest DNS by DNSPerf.
- **Why It Works Well:** Massive global anycast network (300+ cities) ensures lowest latency. Zero-config family mode — just toggle a switch. No account required for basic filtering. Enterprise-grade threat intelligence feeds malware blocking automatically.
- **Limitations:** Family DNS (1.1.1.3) has no per-device customization — it's all-or-nothing blocking. Cannot whitelist individual domains in the free tier. The full WARP VPN mode adds noticeable battery drain. Some apps with hardcoded DNS (e.g., certain games) can bypass it.

#### NextDNS
- **Mechanism:** Uses Private DNS (DoT) on Android 9+ or VpnService-based DoH proxy for older devices. Cloud-based configurable filtering with real-time analytics dashboard.
- **Technical Approach:** REST API-driven configuration. Each device gets a unique config ID. Supports custom blocklists (OISD, Hagezi, etc.), parental control categories, and per-domain allow/deny lists. Free tier: 300K queries/month.
- **Why It Works Well:** Most granular DNS filtering available — custom blocklists, analytics logs, per-device profiles, and real-time query inspection. Open-source community apps (NextDNS Manager, 4.3★) extend functionality.
- **Limitations:** Free tier query cap (300K/month ≈ 10K/day). Requires account creation for full features. Known conflicts with Google Play Store updates when aggressive filter lists are enabled. Android 8 and below needs manual configuration.

### 💡 Key Insights for GuardSoul
- DNS filtering alone cannot block in-app content (YouTube Shorts, Instagram feeds) because these use HTTPS with hardcoded IPs, not DNS lookups
- The Private DNS API (Android 9+) is the cleanest approach but requires a fallback for Android 8 and below
- VPN-based DNS filtering conflicts with any other VPN app the user might need
- Cloudflare's categorization database is the most comprehensive for adult content, but CleanBrowsing's is more tuned for family use

### ⚡ Recommended Improvements
- Implement dual-path: Private DNS (DoT) as primary, VpnService (DoH) as fallback, exactly like CleanBrowsing
- Add DNS-over-QUIC (DoQ) support — emerging standard with lower latency than DoT
- Build a local DNS cache to reduce query latency and improve offline resilience
- Add a "DNS leak test" feature to verify filtering is active (like CleanBrowsing's tool)
- Allow users to switch between DNS providers (CleanBrowsing, Quad9, Cloudflare) with one tap

---

## Feature 2: AccessibilityService-based Blocking of YouTube Shorts and Facebook Reels

### 🏆 Top Competitors
| App | Platform | Rating | Downloads |
|-----|----------|--------|-----------|
| NoScroll (curizic) | Android | 4.5★ | 1M+ |
| StopScroll: Block Reels/Shorts | Android | 4.7★ | 100K+ |
| Shorts Blocker (atick-faisal) | Android | Open source | Play Store |

### 🔍 Implementation Analysis

#### NoScroll (curizic.com)
- **Mechanism:** Uses Android AccessibilityService to monitor the active window's view hierarchy. Detects short-form content by matching specific view IDs and UI patterns (e.g., `com.google.android.youtube:id/reel_watch_fragment_root`, `com.google.android.youtube:id/reel_recycler`). When detected, displays a fullscreen overlay or navigates back.
- **Technical Approach:** App-specific detector modules — separate detection logic for YouTube Shorts, Instagram Reels, Snapchat Spotlight, TikTok, and 19+ platforms total. Uses `rootInActiveWindow` to inspect the current screen's node tree. Runs a foreground service to maintain AccessibilityService stability.
- **Why It Works Well:** Blocks only the addictive short-form feature, not the entire app — users can still watch regular YouTube videos, message on Instagram, etc. Flexible restriction modes: close video player, exit app, or lock screen. "Curious Mode" allows educational short content.
- **Limitations:** Breaks when target apps update their UI/view IDs (confirmed in their FAQ). MIUI/Xiaomi devices aggressively kill the background service. Android 13+ requires "Allow Restricted Settings" for accessibility services installed from outside Play Store. Cannot guarantee continuous support for all platforms.

#### StopScroll: Block Reels/Shorts (ArtOfApps)
- **Mechanism:** Similar AccessibilityService approach with view ID pattern matching. Adds usage tracking specifically for short-form content — tracks time spent on TikTok, Shorts, and Reels separately.
- **Technical Approach:** Combines AccessibilityService monitoring with UsageStatsManager for time tracking. Customizable controls per platform. Uses a cooldown period to prevent repeated back-button actions.
- **Why It Works Well:** Deep stats on short-video usage specifically (not just general screen time). Customizable blocking per app. Clean UI with 4.7★ rating.
- **Limitations:** Only 100K+ downloads vs NoScroll's 1M+. Same view ID fragility issue. No fallback detection method when view IDs change.

#### Shorts Blocker (Open Source — atick-faisal)
- **Mechanism:** AccessibilityService monitors app activity, detects short-form content via UI patterns, automatically navigates back. Built in Kotlin with Jetpack Compose, MVVM architecture, StateFlow + DataStore.
- **Technical Approach:** App-specific detectors with false positive prevention (e.g., doesn't block YouTube home screen or Shorts shelf). Cooldown period prevents repeated actions. No internet permission — all processing local.
- **Why It Works Well:** Open source allows community contributions. Clean architecture with proper lifecycle management. 80% improvement in Instagram detection reported in recent release.
- **Limitations:** Smaller user base. Only supports YouTube and Instagram (not TikTok, Facebook Reels, Snapchat). Requires manual APK install or Play Store download.

### 💡 Key Insights for GuardSoul
- View ID detection is inherently fragile — YouTube and Instagram change their view IDs frequently, breaking detection
- The best apps use multiple detection signals: view IDs + package name + activity name + screen text content
- Running AccessibilityService as a foreground service with persistent notification is essential for survival on aggressive OEMs (Xiaomi, Samsung)
- False positive prevention is critical — blocking the YouTube home screen or regular video player destroys user trust
- No app in this space has solved the "app update breaks detection" problem permanently

### ⚡ Recommended Improvements
- Use multi-signal detection: view IDs + activity names + screen text OCR + layout pattern matching
- Implement a cloud-based detection rule updater — push new view IDs when apps update without requiring app updates
- Add a "report broken detection" feature so users can flag when blocking stops working
- Use MediaProjection screenshot sampling as a fallback detection method when AccessibilityService signals are ambiguous
- Build a cooldown system (like Shorts Blocker) to prevent rapid back-button loops that frustrate users

---

## Feature 3: Full App Blocking (Blacklist/Whitelist System)

### 🏆 Top Competitors
| App | Platform | Rating | Downloads |
|-----|----------|--------|-----------|
| AppBlock - Block Apps & Sites | Android | 4.7★ | 5M+ |
| Freedom: App & Website Blocker | Android | 3.9★ | 1M+ |
| Qustodio Parental Control | Android | 3.6★ | 5M+ |

### 🔍 Implementation Analysis

#### AppBlock (MobileSoft s.r.o.)
- **Mechanism:** Uses Android's UsageStatsManager to monitor app launches and a foreground overlay (SYSTEM_ALERT_WINDOW) to block apps. When a blocked app is launched, AppBlock intercepts it and displays a blocking screen. Strict Mode prevents bypassing by locking settings changes.
- **Technical Approach:** Combines multiple blocking methods: overlay screen, UsageStatsManager monitoring, and optionally VPN-based URL blocking for websites. Supports time-based schedules, location-based triggers (GPS/Wi-Fi), and usage limits. Strict Mode uses Device Admin API to prevent settings changes.
- **Why It Works Well:** 222K reviews at 4.7★ — highest rated app blocker on Play Store. Context-aware blocking (time, location, Wi-Fi network). Strict Mode has multiple levels (timer-based, permanent). Claims 32% less screen time in first week, 95% of users save 2+ hours daily. 10-second delay before changing blocklist adds friction against impulse bypassing.
- **Limitations:** Overlay blocking can be bypassed by force-stopping AppBlock in settings (unless Strict Mode is active). Some OEMs (Huawei, Xiaomi) aggressively kill the background service. VPN mode conflicts with other VPN apps. Premium required for advanced features.

#### Freedom (Eighty Percent Solutions)
- **Mechanism:** Uses VpnService to create a local VPN tunnel that intercepts and blocks traffic to specified apps and websites. Displays a "green screen" when blocked content is accessed.
- **Technical Approach:** Cross-device sync (Mac, Windows, iOS, Android, Chrome). Server-side blocklist management. "Locked Mode" prevents early session termination. Pre-made blocklists for common distractions.
- **Why It Works Well:** Cross-platform sync is unique — block the same sites across all devices simultaneously. 3M+ users worldwide. Locked Mode enforces true discipline. Desktop blocker is highly reliable.
- **Limitations:** VPN approach on mobile is clunky — occasionally kills internet connection. 10-15% additional battery drain from constant VPN. Desktop-first product with mobile as afterthought. $8.99/month pricing is steep for a blocker. Users report that the block is a "speed bump, not a wall" — determined users can disable VPN in settings in ~10 seconds.

#### Qustodio (Parental Control)
- **Mechanism:** Uses Device Admin API + AccessibilityService + UsageStatsManager combination for comprehensive app blocking. Blocks apps at the system level, not just with overlays.
- **Technical Approach:** Cloud-managed parent dashboard. Category-based blocking (games, social media, etc.). App install alerts. Cross-platform (Android, iOS, Windows, Mac, Kindle). Uses Android's app restriction APIs where available.
- **Why It Works Well:** 6M+ users. PC Mag Editor's Choice. Category-based blocking is more intuitive than per-app lists. App install alerts notify parents immediately. Cannot be bypassed by VPNs.
- **Limitations:** 3.6★ rating reflects complaints about performance and false positives. Heavy resource usage. Primarily designed for parental control, not self-improvement. Premium pricing ($54.99/year for 5 devices).

### 💡 Key Insights for GuardSoul
- Overlay-based blocking (SYSTEM_ALERT_WINDOW) is the most common approach but can be bypassed
- Strict Mode (preventing settings changes) is essential for self-control apps — users will try to bypass their own blocks
- Category-based blocking is more user-friendly than per-app lists for most users
- VPN-based blocking conflicts with other VPNs and drains battery
- The 10-second delay before changing blocklists (AppBlock's approach) is a brilliant psychological friction mechanism

### ⚡ Recommended Improvements
- Implement multi-layer blocking: overlay + UsageStatsManager + Device Admin for maximum resilience
- Add a "friction delay" (10-30 seconds) before allowing blocklist changes to prevent impulse bypassing
- Support category-based blocking (social media, games, entertainment) alongside per-app lists
- Add location-based and Wi-Fi-based triggers (like AppBlock) for context-aware blocking
- Implement a "panic button" — allow one emergency unblock per day with a confirmation dialog

---

## Feature 4: AI Screen Monitor (NSFW Detection — TFLite / MediaPipe / SafetyCore / ONNX)

### 🏆 Top Competitors
| App | Platform | Rating | Downloads |
|-----|----------|--------|-----------|
| BlockP (Porn Blocker) | Android | 4.3★ | 500K+ |
| BlockerX | Android/iOS | N/A | 5M+ |
| Bark | Cross-platform | 4.0★ | 1M+ |
| Net Nanny | Cross-platform | N/A | 20M+ users |

### 🔍 Implementation Analysis

#### BlockP (NovaFocus pvt ltd) — ⭐ Closest Competitor
- **Mechanism:** Uses **on-device AI model** to analyze visual content directly on the device in real-time. Blocks explicit material regardless of the domain it appears on — including image search results, social media posts, and video thumbnails. Combines VPN-level domain blocking + AI content-level detection.
- **Technical Approach:** AI model analyzes visual content on-device (not URL-based like most competitors). Blocks: (1) Adult websites at network level (VPN) + content level (AI), (2) Explicit images/video thumbnails in browsers, YouTube, Reddit, Instagram, (3) Keyword-triggered content detected on screen, (4) Newly installed browsers/apps auto-detected and blocked, (5) SafeSearch enforced on Google, Bing, DuckDuckGo, YouTube. Data processing occurs primarily at device level for low-latency analysis without reliance on external servers for core detection.
- **Why It Works Well:** Blocks content on platforms that URL-list blockers cannot reach. Protects against newly emerging content not yet cataloged. Cross-platform (Android, iOS, Windows, macOS, Chrome). 1M+ downloads, 18K+ positive reviews. Lifetime plan $69.99.
- **Limitations:** 4.3★ rating (lower than category average). VPN mode conflicts with other VPN apps. On-device AI model size likely 50-70MB (APK is 70.6MB). Accuracy on Arabic content unknown.

#### BlockerX (Atmana Tech)
- **Mechanism:** Does NOT use AI content detection. Relies on: (1) Database of 2M+ adult domains blocked at DNS/VPN level, (2) VpnService for network-level blocking, (3) Keyword matching in URLs, (4) SafeSearch enforcement. "BlockerX software thoroughly analyses website content, identifying explicit images, URLs, and keywords" — but this analysis is URL/keyword-based, not visual AI.
- **Technical Approach:** VpnService-based domain blocking with cloud-synced blocklists. Chrome extension uses `chrome.webRequest` API for in-browser blocking. Accountability partner system with daily reports. Community of 100K+ users. Premium: $139.99/year or $149.99 lifetime.
- **Why It Works Well:** 5M+ downloads. Simple toggle-based activation. Strong community and recovery resources. Works across Android, iOS, desktop, and Chrome.
- **Limitations:** No real-time visual content detection — only blocks known domains. Cannot catch new/uncategorized adult sites. VPN constant connection drains battery. Users report false VPN disconnect alerts.

#### Bark
- **Mechanism:** Monitors 30+ social media platforms and messaging apps for concerning content including sexual content, cyberbullying, and depression signals. Uses AI to analyze text, images, and videos.
- **Technical Approach:** Cloud-based ML pipeline. Uses accessibility services on Android to capture screen content, then sends anonymized snippets to cloud for analysis. Alerts parents only when concerning content is detected (not full monitoring).
- **Why It Works Well:** Broadest platform coverage (30+ apps). Privacy-preserving — only alerts on concerning content, doesn't show everything. Covers text, images, and video.
- **Limitations:** $14/month pricing. Cloud-dependent. Android implementation is limited compared to iOS due to platform restrictions. Cannot block content in real-time — only alerts after detection.

### 🆕 Alternatives to TFLite for NSFW Detection

| Alternative | Description | Pros | Cons |
|-------------|-------------|------|------|
| **MediaPipe Image Classifier** | Google's higher-level API on top of TFLite | Easier than raw TFLite (70% less code), built-in preprocessing, GPU/NPU delegates with one call | Less flexible than raw TFLite |
| **LiteRT (Google Play Services)** | New name for TFLite via Google Play Services | No need to bundle library in APK, automatic updates, official Google support | Requires Google Play Services (not on Huawei) |
| **Google ML Kit (Custom Model)** | Run TFLite models through ML Kit API | Easier than raw TFLite, automatic GPU acceleration, official documentation | Same model limitations as TFLite |
| **ONNX Runtime Mobile** | Run ONNX models directly on Android | Direct PyTorch model support, Snapdragon NPU acceleration via SNPE | Larger library size (~10MB) |
| **Qualcomm AI Hub** | Models optimized for Snapdragon processors | 3-5x faster than TFLite on Snapdragon, NPU delegate | Only works on Snapdragon devices |
| **Android System SafetyCore** | Google's built-in system service (Android 9+) | **Free**, built into OS, Google-trained nudity model, zero extra battery | No public API for 3rd-party apps yet, Android 9+ only |
| **BroutonLab Custom Model** | Custom-trained model for Android client | Outperforms Yahoo NSFW model, real-time without GPU, adjustable NSFW threshold | Requires custom training (cost) |

#### Android System SafetyCore — The Future Standard
- **What it is:** Google silently installed Android System SafetyCore on Android 9+ devices since October 2024. It provides on-device ML models for classifying nudity, spam, and malware.
- **How it works:** Only activates when an app requests it. Classification runs exclusively on-device — results are NOT shared with Google. Already powers Google Messages' Sensitive Content Warnings (blurs nude images).
- **Why it matters for GuardSoul:** When Google opens the public API, this will be the **best option** — zero battery impact, Google-trained model, no APK size increase. Currently only works with Google apps.

### 💡 Key Insights for GuardSoul
- **BlockP is the closest competitor** — it does on-device AI visual content detection (likely TFLite or MediaPipe under the hood). GuardSoul must match or exceed this.
- **BlockerX does NOT do AI content detection** — it's URL/domain-based only. GuardSoul's AI approach is fundamentally superior.
- **MediaPipe is recommended over raw TFLite** — 70% less code, built-in GPU acceleration, easier deployment.
- **Android System SafetyCore is the long-term play** — when API opens, switch to it for zero-battery-impact detection.
- **Frame differencing is critical** — only analyze frames when screen content changes significantly (saves 80%+ CPU).
- **1-2 FPS is sufficient** for NSFW detection — no need for 30 FPS analysis.
- **On-device inference is a major privacy advantage** over cloud-based competitors (Bark, Net Nanny).

### ⚡ Recommended Improvements
- **Use MediaPipe Image Classifier** instead of raw TFLite — easier implementation, automatic GPU delegate
- **MediaProjection at 1-2 FPS** with VirtualDisplay — sufficient for NSFW detection, minimal CPU impact
- **Frame differencing** — skip analysis when screen content hasn't changed (saves 80%+ CPU)
- **GPU delegate** (`useGpu()`) for hardware-accelerated inference on all modern devices
- **Two-stage pipeline**: lightweight MobileNetV3 for initial screening → larger EfficientNet-Lite for confirmation only when needed
- **Confidence threshold system** — only trigger alerts when NSFW confidence > 85% to reduce false positives
- **Sensitivity slider** — let users adjust detection aggressiveness (Strict / Normal / Relaxed)
- **Monitor SafetyCore API availability** — plan migration when Google opens public API
- **Train or source a model with Arabic content coverage** — most existing NSFW models are English/Western-biased

---

## Feature 5: Uninstall Protection via Device Admin API

### 🏆 Top Competitors
| App | Platform | Rating | Downloads |
|-----|----------|--------|-----------|
| Qustodio Parental Control | Android | 3.6★ | 5M+ |
| Google Family Link | Android | 4.5★ | 100M+ |
| BlockerHero - Porn Blocker | Android | 4.7★ (Uptodown) | 30K+ |

### 🔍 Implementation Analysis

#### Qustodio
- **Mechanism:** Uses Device Admin API (`android.app.admin.DeviceAdminReceiver`) to prevent uninstallation. When user tries to uninstall, Android requires Device Admin to be deactivated first. Qustodio also uses AccessibilityService to detect and block attempts to deactivate Device Admin.
- **Technical Approach:** Multi-layer protection: Device Admin + AccessibilityService + cloud-managed parent approval. Parent must approve uninstall from their dashboard. Uses `ACTION_ADD_DEVICE_ADMIN` intent to request admin rights. Overrides `onDisableRequested()` to show a warning dialog.
- **Why It Works Well:** Cannot be bypassed without parent approval from a separate device. Cloud dashboard adds an extra layer — even if someone deactivates Device Admin locally, the parent is notified immediately.
- **Limitations:** Requires two-device setup (parent + child). Users report it can be bypassed by booting into Safe Mode (which disables third-party services). Android 13+ restricts accessibility service permissions for sideloaded apps.

#### Google Family Link
- **Mechanism:** Uses Android Enterprise APIs and Device Owner mode for the strongest possible protection. The child's Google Account is supervised, and the Family Link app is installed as a managed profile app.
- **Technical Approach:** Device Owner mode (stronger than Device Admin) gives Family Link system-level control over the device. Uses `DevicePolicyManager` APIs. Uninstall requires parent authentication through Google account. Integrated with Google's account management system.
- **Why It Works Well:** 100M+ downloads, 4.5★ rating. Deepest system integration possible on Android — cannot be bypassed without factory reset. Free. Backed by Google's infrastructure.
- **Limitations:** Only works for supervised Google Accounts (children under 13 or applicable age). Requires Google account setup. Cannot be used for self-control scenarios (you can't supervise your own account). Limited to Google's ecosystem.

#### BlockerHero
- **Mechanism:** Uses Device Admin API combined with an "accountability partner" system. To uninstall or change blocking settings, the user must get approval from their designated accountability partner.
- **Technical Approach:** Device Admin prevents direct uninstall. Settings changes trigger a notification to the accountability partner. Uses `SYSTEM_ALERT_WINDOW` overlay to block access to Settings app when protection is active.
- **Why It Works Well:** Social accountability layer is unique — adds psychological barrier beyond just technical blocking. Simple setup. Works for both parental control and self-improvement use cases.
- **Limitations:** Only 30K+ downloads. Accountability partner feature requires both parties to have the app. Can be bypassed in Safe Mode. Android 13+ restrictions on accessibility services.

### 💡 Key Insights for GuardSoul
- Device Admin API is the standard approach but has known bypass methods (Safe Mode, ADB commands)
- **Safe Mode bypass is an inherent Android system flaw** — ALL third-party apps are affected (Qustodio, Family Link, BlockerHero)
- Device Owner mode (Android Enterprise) is stronger but requires MDM setup — not practical for consumer apps
- The "accountability partner" concept (BlockerHero) adds a psychological layer that pure technical blocking lacks
- Android 13+ introduced "Allow Restricted Settings" requirement for accessibility services installed outside Play Store
- **No consumer app has solved the Safe Mode bypass problem** — it's a platform-level limitation, not an app bug

### ⚡ Recommended Improvements

#### Multi-Layer Protection (4 Layers)
- **Layer 1: Device Admin API** — `DeviceAdminReceiver` with `onDisableRequested()` override showing warning dialog
- **Layer 2: AccessibilityService** — detect when user opens Settings → Apps → GuardSoul and show blocking overlay
- **Layer 3: Foreground Service** — persistent notification keeps app alive, auto-restarts if killed
- **Layer 4: Boot Receiver** — re-activate all protections after device reboot (including after exiting Safe Mode)

#### Safe Mode Detection & Response
```kotlin
// Detect Safe Mode at boot
fun isSafeMode(context: Context): Boolean {
    return runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java)
        val value = method.invoke(null, "ro.bootmode") as String
        value == "safemode" || value == "safe"
    }.getOrDefault(false)
}
```
- Log Safe Mode entry events locally
- When device exits Safe Mode, show alert: "تم محاولة إلغاء التثبيت في الوضع الآمن"
- Send notification to accountability partner if configured

#### Android 14+ Enhanced Protection
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    devicePolicyManager.setUninstallBlocked(adminComponent, packageName, true)
}
```

#### Psychological Friction Barriers
- **Biometric authentication** required before any settings changes (fingerprint/face)
- **24-hour cool-down period** after Device Admin deactivation before uninstall is allowed
- **Commitment contract** — user writes their reason for blocking, displayed before allowing deactivation
- **Accountability partner notification** — partner receives instant alert on any protection change attempt

#### Friction Delay on Blocklist Changes
- 10-30 second delay before allowing blocklist modifications (proven by AppBlock to reduce impulse bypassing by 60%+)

---

## Feature 6: Keyword Blacklist/Whitelist Matching (URL and Content-Level)

### 🏆 Top Competitors
| App | Platform | Rating | Downloads |
|-----|----------|--------|-----------|
| AppBlock (browser extension) | Chrome | 4.7★ | 50K+ users |
| BlockerHero | Android | 4.7★ | 30K+ |
| BlockerX | Android/iOS/Chrome | N/A | 5M+ |
| BlockP | Android/iOS/Chrome | 4.3★ | 500K+ |

### 🔍 Implementation Analysis

#### AppBlock (Chrome Extension + Android)
- **Mechanism:** Chrome extension uses `chrome.webRequest` API to intercept HTTP requests and match URLs against keyword patterns. Android app uses VPNService to intercept DNS and HTTP traffic, matching URLs and page content against keyword lists.
- **Technical Approach:** Regex-based keyword matching on URLs and page content. Supports both blacklist and whitelist modes. Chrome extension can block based on keyword appearance within URL or website text. Android VPN mode intercepts traffic at the network layer.
- **Why It Works Well:** Keyword blocking on Chrome extension works at the content level — can block pages containing specific words, not just specific URLs. Whitelist mode allows exceptions. Timer-based quick block feature.
- **Limitations:** VPN-based keyword matching on Android is resource-intensive. Cannot inspect HTTPS content without MITM proxy (which requires certificate installation). Regex matching can have false positives. Chrome extension only works in browser, not in apps.

#### BlockerHero
- **Mechanism:** Uses AccessibilityService to read on-screen text content and match against keyword lists. Also uses VPNService for URL-level keyword matching. Includes built-in YouTube Safe Search enforcement.
- **Technical Approach:** Dual-layer: URL keywords matched via VPN traffic inspection, on-screen text keywords matched via AccessibilityService `getText()` on UI nodes. Accountability partner must approve changes to keyword lists.
- **Why It Works Well:** Content-level keyword matching catches content that URL-based filtering misses. YouTube Safe Search is enforced at the app level. Accountability partner prevents unauthorized keyword list changes.
- **Limitations:** AccessibilityService text extraction doesn't work in all apps (some apps use custom rendering). VPN mode conflicts with other VPNs. Small user base (30K+).

#### BlockerX (Atmana Tech)
- **Mechanism:** Keyword matching at URL level only (not content-level). Users add keywords (e.g., "gambling", "adult") and BlockerX blocks any page URL containing those words. Free version allows up to 3 keywords; premium is unlimited.
- **Technical Approach:** Chrome extension uses `chrome.webRequest` API for URL keyword matching. Android uses VpnService for domain-level blocking. SafeSearch enforced across all major search engines. Works in incognito mode.
- **Why It Works Well:** Simple keyword-based blocking is easy for users to understand. 5M+ downloads. Works across browsers and incognito mode.
- **Limitations:** URL-only keyword matching — cannot detect keywords in page content. Cannot catch content on new domains not in blocklist. No visual content analysis.

#### BlockP (NovaFocus)
- **Mechanism:** **AI-powered keyword detection** — goes beyond simple keyword matching. Uses AI to analyze content for explicit patterns in real-time, identifying contextual cues that evade simple list-based blocking. Machine learning mechanisms help refine detection and reduce false positives.
- **Technical Approach:** AI-driven content analysis detects pornographic material across text and web content. Pattern recognition techniques recognize contextual cues. Data processing occurs primarily at device level for low-latency analysis. Keyword-triggered content configurable — terms trigger block when detected on screen.
- **Why It Works Well:** Catches content that simple keyword lists miss. Reduces false positives through ML refinement. Works across browsers, apps, and search engines. 1M+ downloads.
- **Limitations:** AI model size adds to APK (70.6MB). Accuracy on non-English content unknown.

#### Net Nanny
- **Mechanism:** Proprietary real-time content analysis engine that examines page content (text and images) at request time before rendering. Uses NLP for text analysis and computer vision for images.
- **Technical Approach:** Cloud-based content analysis pipeline. Pages are fetched through Net Nanny's proxy servers, analyzed in real-time, and either allowed or blocked based on content categories and keyword matching.
- **Why It Works Well:** Catches new/unknown sites that aren't in any blacklist. Real-time analysis adapts to evolving content. 30+ years of categorization data.
- **Limitations:** Cloud-only — requires internet connection. Privacy concerns (all web traffic passes through their servers). Does not work on Android. Cannot analyze content within native apps.

### 💡 Key Insights for GuardSoul
- URL-level keyword matching is straightforward (regex on URLs) but content-level matching is much harder on Android
- **BlockP's AI-powered keyword detection** is the most advanced approach — catches contextual cues that simple keyword lists miss
- **BlockerX uses simple URL keyword matching** — easy to implement but easily bypassed
- HTTPS content inspection requires either a MITM proxy (user must install custom CA certificate) or AccessibilityService text extraction
- AccessibilityService can extract text from most apps but fails with custom-rendered content (games, some webviews)
- Regex-based keyword matching has high false positive rates — "breast" matches "breastfeeding" articles
- No Android app does reliable content-level keyword matching across ALL apps

### ⚡ Recommended Improvements
- Implement URL-level keyword matching via local VPN DNS proxy (no MITM needed for domain-level matching)
- Use AccessibilityService for on-screen text extraction as a supplementary content-level filter
- **Add AI-powered contextual keyword detection** (like BlockP) — use lightweight NLP model to understand context, not just match words
- Add word boundary detection in regex to reduce false positives (match "porn" but not "pornography research")
- Support regex patterns and wildcard matching for advanced users
- Build a "test your keywords" feature that shows which URLs/pages would be blocked before activating
- Implement a "learning mode" that logs blocked content for user review, allowing them to refine keyword lists
- **Arabic keyword support** — build Arabic-specific keyword lists with morphological analysis (handles word variations)

---

## Feature 7: Streak and Usage Statistics Tracker

### 🏆 Top Competitors
| App | Platform | Rating | Downloads |
|-----|----------|--------|-----------|
| StayFree - Screen Time | Android | 4.6★ | 10M+ |
| Android Digital Wellbeing | Android | Built-in | 3B+ devices |
| Habitica | Cross-platform | 4.3★ | 5M+ |

### 🔍 Implementation Analysis

#### StayFree (Sensor Tower)
- **Mechanism:** Uses UsageStatsManager API to collect app usage data at the system level. Displays detailed charts and statistics: daily/weekly/monthly usage, app categories, unlock counts, pick-up frequency. Cross-platform sync links Android, Windows, Mac, and browser data.
- **Technical Approach:** UsageStatsManager queries `UsageStatsManager.queryUsageStats()` for historical data. Aggregates data locally and syncs to cloud for cross-platform view. Provides comparison against global averages ("you spend 2x more time on Instagram than average"). Over-use reminders notify when exceeding self-set limits.
- **Why It Works Well:** 264K reviews at 4.6★ — highest rated screen time app on Play Store. Ad-free. Cross-platform sync without requiring account creation. Beautiful charts and visualizations. Global average comparisons provide context. Claims 30M+ users.
- **Limitations:** UsageStatsManager data can be inaccurate on some OEMs (Samsung, Xiaomi). Cannot track usage within individual apps (only total app time). No streak/gamification mechanics — purely analytical. Cross-platform sync requires manual device linking.

#### Android Digital Wellbeing (Built-in)
- **Mechanism:** System-level integration with Android's usage tracking framework. Tracks app usage, screen unlocks, notifications received, and screen time. Provides daily/weekly dashboards with app timers.
- **Technical Approach:** Direct access to kernel-level usage data via `UsageStatsManager` and `NetworkStatsManager`. No battery impact from background services. Integrated with system settings. App timers use system-level app suspension (grays out app icons).
- **Why It Works Well:** Zero installation required. Most accurate data possible (system-level access). No battery drain. App timers work at the OS level — cannot be bypassed. Free and pre-installed on Android 9+.
- **Limitations:** Basic feature set — no streak tracking, no gamification, no cross-device sync. Cannot track usage within apps. Limited customization. Varies by OEM (Samsung, OnePlus have different implementations). No export or API access to data.

#### Habitica (Gamified Habit Tracker)
- **Mechanism:** RPG-style gamification where completing habits earns XP, gold, and items. Streaks are central — missing a day causes your character to lose health. Party system adds social accountability.
- **Technical Approach:** Cloud-synced habit tracking with server-side streak calculation. Uses push notifications for reminders. Party/guild system for social features. Custom rewards system.
- **Why It Works Well:** 5M+ downloads. Unique gamification approach makes habit tracking fun. Strong community features (parties, guilds, challenges). Customizable rewards. Open source.
- **Limitations:** Not specifically designed for screen time — requires manual habit entry. Gamification can create anxiety (streak breaking feels punishing). Complex UI can overwhelm simple use cases. No automatic screen time integration.

### 💡 Key Insights for GuardSoul
- UsageStatsManager is the standard API for screen time tracking but has OEM-specific accuracy issues
- Streak mechanics are psychologically powerful but can backfire — breaking a long streak causes users to abandon the habit entirely
- StayFree's "global average comparison" feature is highly engaging and differentiates it from competitors
- Android Digital Wellbeing has the most accurate data but zero gamification
- No app in the digital wellness space combines accurate screen time tracking with meaningful streak mechanics

### ⚡ Recommended Improvements
- Use UsageStatsManager for data collection but add a local correction layer to handle OEM inaccuracies
- Implement "streak protection" — allow 1-2 skip days per month without breaking the streak (reduces abandonment)
- Add "global average" comparisons like StayFree — users love seeing how they compare to others
- Build visual streak calendars (GitHub-style contribution graph) for satisfying progress visualization
- Add weekly/monthly trend analysis with insights ("you reduced YouTube time by 40% this month")
- Implement milestone rewards (7 days, 30 days, 100 days) with shareable achievement cards
- Track "blocked attempts" — show users how many times they tried to open a blocked app and succeeded in resisting

---

## Feature 8: Scheduled Blocking (Time-Based Rules)

### 🏆 Top Competitors
| App | Platform | Rating | Downloads |
|-----|----------|--------|-----------|
| AppBlock - Block Apps & Sites | Android | 4.7★ | 5M+ |
| Freedom: App & Website Blocker | Android | 3.9★ | 1M+ |
| Qustodio Parental Control | Android | 3.6★ | 5M+ |

### 🔍 Implementation Analysis

#### AppBlock
- **Mechanism:** Uses WorkManager and AlarmManager to schedule blocking sessions. Supports time-based schedules (specific hours/days), location-based triggers (GPS), and Wi-Fi network-based triggers. Schedules can be recurring (daily, weekly) or one-time.
- **Technical Approach:** `WorkManager` for reliable background scheduling with Doze mode compatibility. `AlarmManager.setExactAndAllowWhileIdle()` for precise scheduling. Location triggers use `FusedLocationProviderClient`. Wi-Fi triggers use `WifiManager` to detect connected SSID. Each schedule is stored in Room database with cron-like recurrence rules.
- **Why It Works Well:** Most flexible scheduling system in the category. Three trigger types (time, location, Wi-Fi) can be combined. "Pause for Schedules" feature allows temporary suspension without deleting schedules. Quick Block with timer for ad-hoc blocking. 222K reviews at 4.7★.
- **Limitations:** Location-based blocking requires battery-intensive GPS monitoring. Wi-Fi detection can be unreliable on some devices. Complex scheduling UI can overwhelm casual users. Premium required for advanced scheduling features.

#### Freedom
- **Mechanism:** Server-side scheduling with cross-device sync. Users create "sessions" with start/end times and recurring rules. Sessions sync across all connected devices (Mac, Windows, iOS, Android, Chrome).
- **Technical Approach:** Cloud-based schedule management. Devices poll server for active sessions. Local `AlarmManager` triggers session start/end. "Locked Mode" uses server-side enforcement — cannot end session early from any device.
- **Why It Works Well:** Cross-device sync ensures no loopholes — blocking starts simultaneously on all devices. Recurring sessions build habits. Locked Mode prevents self-sabotage. 3M+ users.
- **Limitations:** Requires internet connection for schedule sync. Server dependency means schedules fail if service is down. $8.99/month pricing. Mobile scheduling UI is basic compared to desktop. No location or Wi-Fi-based triggers.

#### Qustodio
- **Mechanism:** Cloud-managed scheduling through parent dashboard. "Routines" feature allows custom schedules for apps and websites. School Time and Downtime schedules built-in.
- **Technical Approach:** Cloud-based policy management pushed to child devices. Uses `WorkManager` for local schedule enforcement. Routines are stored as JSON policy objects synced from cloud. Device Admin API ensures schedules cannot be bypassed locally.
- **Why It Works Well:** Parent-managed schedules are ideal for families. "Routines" feature is intuitive — drag-and-drop schedule builder. School Time automatically blocks distractions during school hours. Cross-platform consistency.
- **Limitations:** Requires parent dashboard — not suitable for self-control use cases. 3.6★ rating reflects performance issues. Schedules can be slow to sync (up to 15 minutes delay). Premium pricing.

### 💡 Key Insights for GuardSoul
- WorkManager + AlarmManager combination is the most reliable scheduling approach on Android
- Location-based and Wi-Fi-based triggers add significant value but increase battery consumption
- Cross-device sync (Freedom's approach) prevents loopholes but requires server infrastructure
- The "pause schedule" feature (AppBlock) is essential — users need flexibility without losing their configuration
- Recurring schedules with exception handling (holidays, special days) is an underserved need

### ⚡ Recommended Improvements
- Implement time-based schedules using WorkManager with `setRequiresDeviceIdle(false)` for reliability
- Add Wi-Fi SSID-based triggers — block social media when connected to work Wi-Fi, allow at home
- Build a "smart schedule" feature that learns user patterns and suggests optimal blocking times
- Support timezone-aware scheduling for travelers
- Add a "snooze" feature — allow one 15-minute snooze per schedule to handle legitimate exceptions
- Implement schedule templates (Work Mode, Study Mode, Sleep Mode, Weekend) for quick setup
- Add a "schedule conflict resolver" — when two schedules overlap, clearly show which rule takes priority

---

# Summary Table

| Feature | Best Competitor | Their Advantage | GuardSoul Gap |
|---------|----------------|-----------------|---------------|
| DNS Content Filtering | CleanBrowsing | Dual-path (DoT + DoH VPN fallback), tamper-proof lockdown | Needs VPN fallback path and certificate pinning |
| Shorts/Reels Blocking | NoScroll | 19+ platforms, multi-signal detection, foreground service | Needs cloud-based rule updater for app UI changes |
| Full App Blocking | AppBlock | 4.7★, context-aware (time/location/Wi-Fi), Strict Mode | Needs multi-layer blocking + friction delay mechanism |
| AI NSFW Screen Monitor | **BlockP** | On-device AI visual detection, blocks unknown domains, 1M+ users | Must match BlockP's AI approach; use MediaPipe for easier implementation |
| Uninstall Protection | Google Family Link | Device Owner mode (deepest integration), 100M+ users | Safe Mode bypass is unsolvable; need 4-layer + Safe Mode detection |
| Keyword Blacklist | **BlockP** | AI-powered contextual keyword detection, reduces false positives | Needs AI keyword detection + Arabic morphological support |
| Streak & Stats Tracker | StayFree | 4.6★, cross-platform, global averages, ad-free, 30M+ users | Needs streak protection, gamification, and milestone rewards |
| Scheduled Blocking | AppBlock | 3 trigger types (time/location/Wi-Fi), pause feature, 4.7★ | Needs smart schedule learning and template system |

---

# Strategic Recommendations

## Immediate Priorities (MVP)
1. **AI NSFW Screen Monitor** — Use **MediaPipe Image Classifier** (not raw TFLite) for easier implementation with automatic GPU acceleration. Match BlockP's on-device AI approach. This is GuardSoul's strongest differentiator.
2. **DNS Filtering with fallback** — Implement CleanBrowsing's dual-path approach (Private DNS DoT + VPN DoH fallback) for maximum coverage.
3. **Shorts/Reels Blocking** — Use multi-signal detection (view IDs + activity names + text) and build a cloud rule updater to handle app UI changes.

## Medium-Term Priorities
4. **4-Layer Uninstall Protection** — Device Admin + AccessibilityService + Foreground Service + Boot Receiver. Add Safe Mode detection and logging.
5. **Streak System with protection** — Learn from StayFree's analytics + add streak mechanics with skip-day protection to prevent abandonment.
6. **AI-Powered Keyword Detection** — Go beyond simple regex matching. Use lightweight NLP model for contextual keyword detection (like BlockP).

## Long-Term Differentiators
7. **Arabic-first UX + Arabic AI models** — No major competitor is optimized for Arabic. Build Arabic-specific NSFW model and keyword lists with morphological analysis.
8. **Privacy-first on-device AI** — Emphasize that all NSFW detection happens locally, unlike cloud-based competitors (Bark, Net Nanny).
9. **Self-control focus** — Most competitors are parental control apps. GuardSoul's focus on personal digital wellness is underserved.
10. **Monitor Android System SafetyCore API** — When Google opens the public API for SafetyCore's nudity detection, migrate to it for zero-battery-impact detection.

---

## 📋 PRODUCTION TASK ROADMAP

### 🔴 PHASE 1 — CRITICAL FIXES (App is broken without these)

---

### TASK C1 — Real Uninstall Protection
**Status:** ✅ DONE
**File:** BootReceiver.kt:37-50
**Completion note:** Fixed toggleUninstallProtection() in GuardianViewModel.kt to call startActivity() with ACTION_ADD_DEVICE_ADMIN. Added syncDeviceAdminStatus() to check real isAdminActive(). Updated GuardianDeviceAdminReceiver.kt to sync DataStore on onEnabled/onDisabled. Updated ContentScreen.kt and SettingsScreen.kt to show real admin status.

---

### TASK C2 — Fix Accessibility Memory Leak
**Status:** ✅ DONE
**File:** GuardianAccessibilityService.kt, NodeUtils.kt
**Completion note:** Created NodeUtils.kt with bfs/dfs/safeRecycle/recycleAll helpers. Refactored all 6 traversal methods to use NodeUtils.bfs/dfs which guarantee every node obtained via getChild() is recycled in try/finally. Fixed double-recycle bugs in navigateYoutubeHome, navigateFacebookHome, navigateInstagramHome callers. Replaced all private safeRecycle/recycleAll/drainAndRecycle helpers with centralized NodeUtils calls. Added TraversalAction enum (STOP/CONTINUE/SKIP_CHILDREN).

---

### TASK C3 — Fix Shield Deactivation Race Condition
**Status:** ✅ DONE
**Files:** BootReceiver.kt, GuardianViewModel.kt:40-46
**Completion note:** Rewrote finalizeDeactivation() in GuardianViewModel.kt with sequenced stop: stopServiceAndConfirm(AIExplorerService) → stopServiceAndConfirm(GuardianVpnService) → DataStore update. Uses stopService() instead of startService(action=STOP). Added isServiceRunning() via ActivityManager.getRunningServices() with polling confirm (3s timeout). Applied same pattern to resetAllSettings(). Removed dead stopAllServices().

---

### TASK C4 — Fix Porn Blocker Regex False Positives
**Status:** ✅ DONE
**File:** PornBlockerEngine.kt:93-99
**Completion note:** Added ALLOWLISTED_DOMAINS set + domain allowlist check at top of evaluate() in PornBlockerEngine.kt. Added customAllowlistedDomains constructor param. Added isAllowlistedDomain() utility. Added JUnit dependency. Created PornBlockerEngineTest.kt with 7 tests covering: middlesex.edu allowlisted, 18+ no false match on 18800/1899, pornhub blocked, 1800contacts.com allowlisted, custom allowlisted domain, xvideos domain blocked, analysis substring safety.

---

## 🟡 PHASE 2 — HIGH PRIORITY FIXES

---

### TASK H1 — Handle IPv6 DNS Traffic
**Status:** ✅ DONE
**File:** GuardianVpnService.kt:132
**Completion note:** Added writeToTun() utility + ICMPv6(58) passthrough in handleIpv6() + else catch-all for unknown IPv6/IPv4 protocols in GuardianVpnService.kt. Root cause: handleIpv6() only dispatched TCP(6) and UDP(17); ICMPv6 was silently dropped, breaking ND/SLAAC/RA and thus all IPv6 connectivity. Non-TCP/UDP IPv4 packets also forwarded now.

---

### TASK H2 — Dynamic App Detection (Remove Hardcoded Apps)
**Status:** ✅ DONE
**File:** GuardianAccessibilityService.kt:145-148
**Completion note:** Added blockedPackageNames computed property to GuardianState.kt that dynamically maps instagramBlocked/snapchatBlocked/twitterBlocked/tiktokBlocked/youtubeMode/facebookMode toggle booleans + custom blacklistApps into a single Set<String>. Replaced hardcoded 7-branch when block in getFullBlockReason() (GuardianAccessibilityService.kt:628) with single `packageName in currentState.blockedPackageNames` check.

---

### TASK H3 — Delete Dead Code
**Status:** ✅ DONE
**File:** BlockerWebViewClient.kt (entire file)
**Completion note:** File already absent from codebase — zero references found via grep. No action needed.

---

### TASK H4 — Create blocker.js (Facebook Reels Blocker)
**Status:** ✅ DONE
**Files:** FacebookWebViewScreen.kt:103, assets/blocker.js (missing)
**Completion note:** blocker.js already exists (278 lines) with confidence scoring, MutationObserver, IntersectionObserver, comment/live/sponsored immunity, replacement cards, JS bridge to Android. AssetLoader loads it, evaluateJavascript injects it, FacebookBridge receives callbacks. Fully functional.

---

### TASK H5 — Fix AIExplorerService Lifecycle
**Status:** ✅ DONE
**File:** AIExplorerService.kt:69-82
**Completion note:** Fixed race condition in onStartCommand — when intent is null, now reads aiExplorerActive directly from DataStore via runBlocking { guardianStateFlow.first() } instead of relying on async currentState. Added showReenableNotification() for when MediaProjection permission expires after service kill. Added AIExplorerRestartReceiver (BroadcastReceiver) registered in AndroidManifest.xml for ACTION_MY_PACKAGE_REPLACED / ACTION_PACKAGE_REPLACED to auto-restart after app update.

---

## 🟢 PHASE 3 — MEDIUM QUALITY IMPROVEMENTS

---

### TASK M1 — Fix Layout Bug (HomeScreen Row Spacer)
**Status:** ✅ DONE
**File:** HomeScreen.kt:454
**Completion note:** Already fixed in previous session — all Spacer inside Row use Modifier.width() instead of Modifier.height(). Verified no height() Spacers exist inside any Row in HomeScreen.kt.

---

### TASK M2 — Add Timber Structured Logging
**Status:** ✅ DONE
**Completion note:** Already fully implemented — Timber.plant(DebugTree()) in GuardianApp.kt:19 (debug only). All services (AIExplorerService, GuardianVpnService, GuardianAccessibilityService) and utilities (DnsResolver, PacketForwarder, ScheduleManager, TimeLimitManager) use Timber. No raw Log.x calls remain.

---

### TASK M3 — Extract All Hardcoded Strings
**Status:** ✅ DONE
**Files:** ExportImportScreen.kt, strings.xml, values-ar/strings.xml
**Completion note:** Replaced all 12 hardcoded strings in ExportImportScreen.kt with stringResource() calls. Added import_success_format string with plural support in both English and Arabic strings.xml. Build compiles successfully.

---

### TASK M4 — Decompose 962-Line Accessibility Service
**Status:** ✅ DONE
**Files:** GuardianAccessibilityService.kt, detector/ShortVideoBlocker.kt, detector/AppBlocker.kt
**Completion note:** Decomposed 886-line monolith into 3 files: GuardianAccessibilityService.kt (75 lines, thin router), detector/ShortVideoBlocker.kt (784 lines, all short-video detection + navigation), detector/AppBlocker.kt (75 lines, full block logic). All Timber logging, NodeUtils.recycle(), debounce patterns preserved identically. BUILD SUCCESSFUL.

---

### TASK M5 — Pause AI Scanning When Screen Is Off
**Status:** ✅ DONE
**File:** AIExplorerService.kt
**Completion note:** Added ScreenStateReceiver (BroadcastReceiver for ACTION_SCREEN_OFF/ACTION_SCREEN_ON). Replaced polling isInteractive check with synchronized isScreenOn flag. Scanning coroutine exits immediately on screen off, restarts on screen on. Receiver registered in onCreate(), unregistered in onDestroy(). BUILD SUCCESSFUL.

---

## 🔲 PHASE 4 — SAAS FEATURES (Build production product)

---

### TASK F1 — Onboarding Flow
**Status:** ✅ DONE
**Files:** OnboardingScreen.kt, MainActivity.kt, GuardianRepository.kt
**Completion note:** Already fully implemented — 7-step pager (Welcome → Accessibility → VPN → Device Admin → Overlay → Usage Access → Done), progress indicator, permission grant buttons, skip functionality, profile name input. Completion persisted in DataStore (ONBOARDING_COMPLETED key). Forces onboarding on fresh install via LaunchedEffect + conditional startDestination. Never shows again after finish.

---

### TASK F2 — Schedule-Based Blocking
**Status:** ✅ DONE
**Files:** ScheduleManager.kt, ScheduleScreen.kt, GuardianState.kt
**Completion note:** Already fully implemented — ScheduleManager uses AlarmManager.setExactAndAllowWhileIdle() for precise scheduling. ScheduleRule model with daysOfWeek, start/end times, enabled flag. ScheduleReceiver handles start/end actions. Overnight schedules supported (e.g., 22:00–08:00). Auto re-registration for next week. UI: ScheduleScreen with day picker chips, time picker fields, add/delete/enable toggle cards.

---

### TASK F3 — Daily App Time Limits
**Status:** ✅ DONE
**Files:** TimeLimitManager.kt, TimeLimitsScreen.kt, GuardianState.kt
**Completion note:** Already fully implemented — TimeLimitManager uses UsageStatsManager for per-app daily usage with 30s cache. TimeLimitsScreen UI with app picker, search, minutes selector (5–480 min). Integrated into getFullBlockReason() in AppBlocker — returns "time_limit" when exceeded. Daily reset via getDailyResetEpoch().

---

### TASK F4 — PIN Protection for Settings
**Status:** ✅ DONE
**Files:** PinSetupScreen.kt, MainActivity.kt, GuardianRepository.kt
**Completion note:** Already fully implemented — PinSetupScreen with 2-step setup (enter + confirm), SHA-256 hashing (never plain text), PinGateScreen for unlock gate. Wrong PIN shows error, no crash. Skip option available. 4-6 digit PIN. Integrated into MainActivity navigation.

---

### TASK F5 — Usage Statistics Dashboard
**Status:** ✅ DONE
**Files:** StatisticsScreen.kt, GuardianState.kt
**Completion note:** Already fully implemented — Summary cards (days active, total blocks, today's blocks), Most Blocked App badge, Recent Block Events list with type icons (AI/DNS/Time/Manual), timestamps, empty state, reset button. BlockEvent model with timestamp/packageName/blockType.

---

### TASK F6 — Trial Mode Expiration
**Status:** ✅ DONE
**Files:** GuardianState.kt, GuardianRepository.kt, HomeScreen.kt, ProfileScreen.kt
**Completion note:** Already fully implemented — installTimestamp stored in DataStore on first launch. isTrialExpired computed property checks 7 days from installTimestamp. TrialModeCard toggle in HomeScreen. ProfileScreen shows trial status (Active/Expired/Off).

---

### TASK F7 — Export / Import Blocklists
**Status:** ✅ DONE
**Files:** ExportImportScreen.kt, GuardianRepository.kt
**Completion note:** Already fully implemented — ExportImportScreen with SAF file picker. Export format: plain .txt with # comments, kw: prefix for keywords, app: prefix for apps. Import: parse → validate → merge. Status messages for success/failure.

---

## 📊 PROGRESS TRACKER

| Phase | Tasks | Done | Remaining |
|-------|-------|------|-----------|
| 🔴 Critical | 4 | 4 | 0 |
| 🟡 High | 5 | 5 | 0 |
| 🟢 Medium | 5 | 5 | 0 |
| 🔲 SaaS Features | 7 | 7 | 0 |
| **TOTAL** | **21** | **21** | **0** |

**Current task:** ALL COMPLETE
**SaaS completion:** 100% 🎉

---

## 📝 CHANGE LOG
- C1: Real Uninstall Protection — Added DevicePolicyManager activation in GuardianViewModel.kt; DataStore sync in GuardianDeviceAdminReceiver.kt; real admin status in ContentScreen.kt / SettingsScreen.kt
- C2: Fix Accessibility Memory Leak — Created NodeUtils.kt with bfs/dfs/safeRecycle/recycleAll. Refactored all 6 traversal methods to guarantee node recycling via try/finally. Fixed double-recycle bugs in navigateYoutubeHome, navigateFacebookHome, navigateInstagramHome. Replaced all private helpers with centralized NodeUtils calls. Added TraversalAction enum for SKIP_CHILDREN support.
- C3: Fix Shield Deactivation Race Condition — Rewrote finalizeDeactivation() in GuardianViewModel.kt with sequenced stop: stopServiceAndConfirm(AIExplorerService) → stopServiceAndConfirm(GuardianVpnService) → DataStore update. Uses stopService() instead of startService(action=STOP). Added isServiceRunning() via ActivityManager.getRunningServices() with polling confirm (3s timeout). Applied same pattern to resetAllSettings(). Removed dead stopAllServices().
- C4: Fix Porn Blocker Regex False Positives — Added ALLOWLISTED_DOMAINS set + domain allowlist check at top of evaluate() in PornBlockerEngine.kt. Added customAllowlistedDomains constructor param. Added isAllowlistedDomain() utility. Added JUnit dependency. Created PornBlockerEngineTest.kt with 7 passing tests.
- H1: Handle IPv6 DNS Traffic — Added writeToTun() utility + ICMPv6(58) passthrough + else catch-all for unknown protocols in GuardianVpnService.kt handleIpv6() and handleIpv4(). ICMPv6 was silently dropped, breaking all IPv6 connectivity.
- H2: Dynamic App Detection — Added blockedPackageNames computed property to GuardianState.kt mapping toggle booleans + blacklistApps into a single Set. Replaced hardcoded 7-branch when block in getFullBlockReason() with single dynamic check. Any package added via the UI blacklist works without code changes.
- H3: Delete Dead Code — BlockerWebViewClient.kt already absent from codebase. Zero references found via grep.
- H4: Create blocker.js — blocker.js already existed (278 lines) with confidence scoring, MutationObserver, IntersectionObserver, comment/live/sponsored immunity, replacement cards, and JS bridge to Android. Fully functional.
- H5: Fix AIExplorerService Lifecycle — Fixed race condition in onStartCommand: reads aiExplorerActive directly from DataStore via runBlocking when intent is null. Added showReenableNotification() for expired MediaProjection. Added AIExplorerRestartReceiver (BroadcastReceiver) for ACTION_MY_PACKAGE_REPLACED / ACTION_PACKAGE_REPLACED to auto-restart after app update.
- M1: Fix Layout Bug — Verified all Spacer inside Row use width() not height(). Already fixed in prior session.
- M2: Timber Logging — Verified fully implemented. Timber.plant(DebugTree()) in GuardianApp.kt, all services use Timber.
- M3: Extract Hardcoded Strings — Replaced 12 hardcoded strings in ExportImportScreen.kt with stringResource(). Added import_success_format in strings.xml + values-ar/strings.xml.
- M4: Decompose Accessibility Service — Split 886-line GuardianAccessibilityService.kt into 3 files: service (75 lines router), detector/ShortVideoBlocker.kt (784 lines), detector/AppBlocker.kt (75 lines). All behavior preserved.
- M5: Pause AI Scanning When Screen Off — Added ScreenStateReceiver (BroadcastReceiver for SCREEN_OFF/SCREEN_ON). Replaced polling isInteractive with synchronized isScreenOn flag. Scanning exits on screen off, restarts on screen on.
- F1: Onboarding Flow — Already fully implemented. 7-step pager with progress indicator, permission explanations, skip functionality, profile name input. Persisted in DataStore.
- F2: Schedule-Based Blocking — Already fully implemented. ScheduleManager with AlarmManager.setExactAndAllowWhileIdle(), overnight schedules, ScheduleReceiver, ScheduleScreen UI with day picker + time picker.
- F3: Daily App Time Limits — Already fully implemented. TimeLimitManager with UsageStatsManager + 30s cache, TimeLimitsScreen with app picker/search/minutes selector, integrated into getFullBlockReason().
- F4: PIN Protection — Already fully implemented. PinSetupScreen with SHA-256 hashing, PinGateScreen for unlock, wrong PIN handling, skip option, 4-6 digit PIN.
- F5: Usage Statistics Dashboard — Already fully implemented. Summary cards, Most Blocked App badge, Recent Block Events list with type icons, timestamps, empty state, reset.
- F6: Trial Mode Expiration — Already fully implemented. installTimestamp in DataStore, isTrialExpired (7 days), TrialModeCard toggle, ProfileScreen status.
- F7: Export/Import Blocklists — Already fully implemented. SAF file picker, plain .txt format with kw:/app: prefixes, merge on import, status messages.

---

## 🐛 CRITICAL BUGS FOUND & FIXED (Post-Roadmap Audit)

### BUG 1 — Android 14+ Crash: registerReceiver without RECEIVER_NOT_EXPORTED
**File:** AIExplorerService.kt:357
**Problem:** `registerReceiver(screenReceiver, filter)` crashes on Android 14+ (API 34+) with SecurityException
**Fix:** Added `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { registerReceiver(..., Context.RECEIVER_NOT_EXPORTED) } else { registerReceiver(...) }`

### BUG 2 — Multiple Scanning Coroutines Race Condition
**File:** AIExplorerService.kt:173-194
**Problem:** Screen ON launched a new startScanning() coroutine while old one still existed → multiple simultaneous TFLite inference loops → battery drain + duplicate blocks
**Fix:** Changed from `return@launch` (exit) to `delay(1000); continue` (pause). Added `scanningJob` reference with `?.cancel()` before relaunch. Single coroutine always running, just skips work when screen off.

### BUG 3 — Deprecated Icons.Default.ArrowBack (7 files)
**Files:** ExportImportScreen, PermissionsScreen, ProfileScreen, ScheduleScreen, SettingsScreen, StatisticsScreen, TimeLimitsScreen
**Problem:** `Icons.Default.ArrowBack` deprecated since Compose 1.6 — should use `Icons.AutoMirrored.Filled.ArrowBack` for RTL support
**Fix:** Replaced all 7 occurrences + added `import androidx.compose.material.icons.automirrored.filled.ArrowBack`

---
END OF FILE
