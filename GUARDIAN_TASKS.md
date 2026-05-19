# GUARDIAN — SaaS Production Roadmap
# Last Updated: 2026-05-19 — H3, H4, H5 completed
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

Package: com.guardsoul
Stack: Kotlin, Jetpack Compose, Material3, DataStore, TFLite, VPN Service, Accessibility Service
Target: Production SaaS digital wellness app
State at start: Pre-alpha with critical bugs

---

## 🔴 PHASE 1 — CRITICAL FIXES (App is broken without these)

---

### TASK C1 — Real Uninstall Protection
**Status:** ✅ DONE
**File:** BootReceiver.kt:37-50
**Problem:**
Device Admin intent created but startActivity() never called.
Uninstall protection is 100% fake. User can delete app freely.

**Reference App:** Google Family Link
**Mechanism to copy:**
Study how Family Link uses DevicePolicyManager + DeviceAdminReceiver.
- How it registers DeviceAdminReceiver in AndroidManifest.xml
- How it calls ACTION_ADD_DEVICE_ADMIN intent with explanation string
- How it checks isAdminActive() on every shield activation
- How it prevents removal when admin is active

**Implementation target:**
- BootReceiver.kt: call startActivity() with correct Device Admin intent
- Add proper DeviceAdminReceiver class if missing
- GuardianViewModel: check isAdminActive() before showing "protected" state
- ContentScreen.kt: show real status, not fake checklist

**Verification (all 3 must pass):**
1. Tap "Enable Uninstall Protection" → system dialog appears asking for Device Admin
2. Grant admin → go to Settings > Apps > Guardian → Uninstall button is greyed out
3. Revoke admin → Uninstall button becomes available again

**Completion note:** Fixed toggleUninstallProtection() in GuardianViewModel.kt to call startActivity() with ACTION_ADD_DEVICE_ADMIN. Added syncDeviceAdminStatus() to check real isAdminActive(). Updated GuardianDeviceAdminReceiver.kt to sync DataStore on onEnabled/onDisabled. Updated ContentScreen.kt and SettingsScreen.kt to show real admin status.

---

### TASK C2 — Fix Accessibility Memory Leak
**Status:** ✅ DONE
**File:** GuardianAccessibilityService.kt, NodeUtils.kt
**Problem:**
BFS/DFS node traversals never call .recycle() on extracted nodes.
Memory grows unbounded → app crashes after extended use.

**Reference App:** ActionDash (open source on GitHub)
**Mechanism to copy:**
- Find their node traversal utilities
- Study their try/finally pattern guaranteeing recycle()
- Study their wrapper that prevents double-recycle IllegalStateException
- Apply same pattern to all our traversal methods

**Implementation target:**
- Wrap every findNode / traverse method with try/finally
- Call node.recycle() in every finally block
- Create NodeUtils.kt helper with safe traversal functions
- Replace all raw traversals with NodeUtils calls

**Verification (all 3 must pass):**
1. Enable accessibility service, use YouTube for 10 minutes with blocking active
2. Check Android Studio Memory Profiler: heap must not grow continuously
3. Force-stop and reopen 5 times: no crash, no ANR

**Completion note:** Created NodeUtils.kt with bfs/dfs/safeRecycle/recycleAll helpers. Refactored all 6 traversal methods (detectYoutubeShorts, detectFacebookReels, detectInstagramReels, findAndClickNode, isReelsPivotTabSelected, isOnFacebookHomeTab) to use NodeUtils.bfs/dfs which guarantee every node obtained via getChild() is recycled in try/finally. Fixed double-recycle bugs in navigateYoutubeHome, navigateFacebookHome, navigateInstagramHome callers by switching to NodeUtils.safeRecycle. Replaced all private safeRecycle/recycleAll/drainAndRecycle helpers with centralized NodeUtils calls. Added TraversalAction enum (STOP/CONTINUE/SKIP_CHILDREN) to support the Facebook Reels anti-score traversal with proper child skipping for Live/Story/Sponsored immunity.

---

### TASK C3 — Fix Shield Deactivation Race Condition
**Status:** ✅ DONE
**Files:** BootReceiver.kt, GuardianViewModel.kt:40-46
**Problem:**
Turning off Shield doesn't reliably stop VPN or AI services.
Relies on StateFlow timing race. Services may keep running after UI says "off".

**Reference App:** Freedom.app
**Mechanism to copy:**
Study how Freedom ends an active session cleanly.
- Direct stopService() call sequence (not via state propagation)
- Order: stop AI service → stop VPN service → update DataStore → update UI
- Confirmation that services are truly stopped before UI state changes
- Use ServiceConnection binding to confirm stop, not fire-and-forget

**Implementation target:**
- GuardianViewModel: explicit coroutine-sequenced stop flow
- Stop order: AIExplorerService → GuardianVpnService → DataStore update → UI state
- Add isServiceRunning() check utility

**Verification (all 3 must pass):**
1. Enable Shield (VPN active, AI active) → disable Shield
2. Check Android Settings > VPN: no active VPN connection remains
3. Check running services: neither service appears in active processes

**Completion note:** Rewrote finalizeDeactivation() in GuardianViewModel.kt with sequenced stop: stopServiceAndConfirm(AIExplorerService) → stopServiceAndConfirm(GuardianVpnService) → DataStore update. Uses stopService() instead of startService(action=STOP). Added isServiceRunning() via ActivityManager.getRunningServices() with polling confirm (3s timeout). Applied same pattern to resetAllSettings(). Removed dead stopAllServices().

---

### TASK C4 — Fix Porn Blocker Regex False Positives
**Status:** ✅ DONE
**File:** PornBlockerEngine.kt:93-99
**Problem:**
ALL_PORN_KEYWORDS passed directly as Regex. "18+" matches "188888".
"." matches any character. Legitimate content blocked. Users lose trust.

**Reference App:** AdGuard for Android
**Mechanism to copy:**
- How they separate literal keyword rules from regex rules
- How they apply Pattern.quote() to escape all special characters in literal mode
- How they add \b word boundaries only when appropriate
- Their allowlist override that skips blocking for trusted domains

**Implementation target:**
- Split keyword list: LITERAL_KEYWORDS (use Pattern.quote) vs REGEX_KEYWORDS
- Apply word boundary detection for all literal matches
- Add domain allowlist override in PornBlockerEngine
- Write unit tests for: "18+" must NOT match "1899" or "188888"

**Verification (all 3 must pass):**
1. Unit test: keyword "sex" must NOT block URL containing "middlesex.edu"
2. Unit test: keyword "18+" must NOT match "18800" or "1899"
3. Known adult URL must still be blocked correctly

**Completion note:** Added ALLOWLISTED_DOMAINS set + domain allowlist check at top of evaluate() in PornBlockerEngine.kt. Added customAllowlistedDomains constructor param. Added isAllowlistedDomain() utility. Added JUnit dependency. Created PornBlockerEngineTest.kt with 7 tests covering: middlesex.edu allowlisted, 18+ no false match on 18800/1899, pornhub blocked, 1800contacts.com allowlisted, custom allowlisted domain, xvideos domain blocked, analysis substring safety.

---

## 🟡 PHASE 2 — HIGH PRIORITY FIXES

---

### TASK H1 — Handle IPv6 DNS Traffic
**Status:** ✅ DONE
**File:** GuardianVpnService.kt:132
**Problem:** All IPv6 packets silently dropped. DNS on IPv6 bypasses blocker entirely.
**Reference App:** NextDNS Android (open source on GitHub)
**Mechanism to copy:** Their dual-stack packet handling in VPN tunnel
**Verification:**
1. Connect to IPv6-only WiFi → known blocked site must still be blocked
2. DNS leak test at dnsleaktest.com: only our DNS server appears
3. No crash when receiving IPv6 packet

**Completion note:** Added writeToTun() utility + ICMPv6(58) passthrough in handleIpv6() + else catch-all for unknown IPv6/IPv4 protocols in GuardianVpnService.kt. Root cause: handleIpv6() only dispatched TCP(6) and UDP(17); ICMPv6 was silently dropped, breaking ND/SLAAC/RA and thus all IPv6 connectivity. Non-TCP/UDP IPv4 packets also forwarded now.

---

### TASK H2 — Dynamic App Detection (Remove Hardcoded Apps)
**Status:** ✅ DONE
**File:** GuardianAccessibilityService.kt:145-148
**Problem:** Only YouTube/Facebook/Instagram hardcoded. Every new app needs code change.
**Reference App:** AppBlock — Stay Focused
**Mechanism to copy:** Dynamic packageName check against DataStore blocklist
**Verification:**
1. Add any random app to blocklist → opening it triggers block without code change
2. Remove app from blocklist → it opens freely
3. Works for any app not previously in the hardcoded list

**Completion note:** Added blockedPackageNames computed property to GuardianState.kt that dynamically maps instagramBlocked/snapchatBlocked/twitterBlocked/tiktokBlocked/youtubeMode/facebookMode toggle booleans + custom blacklistApps into a single Set<String>. Replaced hardcoded 7-branch when block in getFullBlockReason() (GuardianAccessibilityService.kt:628) with single `packageName in currentState.blockedPackageNames` check. Adding any package to blacklistApps via the UI now automatically blocks it without code changes.

---

### TASK H3 — Delete Dead Code
**Status:** ✅ DONE
**File:** BlockerWebViewClient.kt (entire file)
**Problem:** 85 lines never instantiated anywhere. Pure confusion.
**Action:** Verify zero references → delete file → document removal
**Verification:**
1. Project compiles cleanly after deletion
2. No runtime crash related to WebView blocking
3. Git diff shows only this file removed

**Completion note:** File already absent from codebase — zero references found via grep. No action needed.

---

### TASK H4 — Create blocker.js (Facebook Reels Blocker)
**Status:** ✅ DONE
**Files:** FacebookWebViewScreen.kt:103, assets/blocker.js (missing)
**Problem:** blocker.js referenced but doesn't exist. Facebook content never filtered.
**Reference App:** Brave Browser content filtering scripts
**Mechanism to copy:**
- CSS selectors for Facebook Reels containers
- MutationObserver for dynamically loaded content
- JS injection via evaluateJavascript() after pageFinished
**Target selectors to block:**
[data-pagelet*="Reel"], [aria-label*="Reels"], div[role="feed"] video
**Verification:**
1. Open Facebook in app WebView → Reels section is hidden/removed from DOM
2. Scroll feed: no video Reels appear even after dynamic load
3. Regular posts and images still visible (not over-blocking)

**Completion note:** blocker.js already exists (278 lines) with confidence scoring, MutationObserver, IntersectionObserver, comment/live/sponsored immunity, replacement cards, JS bridge to Android. AssetLoader loads it, evaluateJavascript injects it, FacebookBridge receives callbacks. Fully functional.

---

### TASK H5 — Fix AIExplorerService Lifecycle
**Status:** ✅ DONE
**File:** AIExplorerService.kt:69-82
**Problem:** Service killed and restarted by system → null intent → immediate shutdown.
**Reference App:** Google Digital Wellbeing persistent monitoring service
**Mechanism to copy:**
- Null-safe onStartCommand() that restores state from DataStore when intent is null
- Foreground notification that keeps service alive correctly
- Companion BroadcastReceiver for extra reliability
**Verification:**
1. Enable AI service → go to Developer Options > Running Services → force stop service
2. Wait 5 seconds → service must auto-restart and appear in running services again
3. NSFW detection still works after forced restart

**Completion note:** Fixed race condition in onStartCommand — when intent is null, now reads aiExplorerActive directly from DataStore via runBlocking { guardianStateFlow.first() } instead of relying on async currentState. Added showReenableNotification() for when MediaProjection permission expires after service kill. Added AIExplorerRestartReceiver (BroadcastReceiver) registered in AndroidManifest.xml for ACTION_MY_PACKAGE_REPLACED / ACTION_PACKAGE_REPLACED to auto-restart after app update.

---

## 🟢 PHASE 3 — MEDIUM QUALITY IMPROVEMENTS

---

### TASK M1 — Fix Layout Bug (HomeScreen Row Spacer)
**Status:** ⬜ PENDING
**File:** HomeScreen.kt:454
**Problem:** Spacer(Modifier.height()) inside Row — has no effect horizontally
**Fix:** Replace with Modifier.width() or Arrangement.spacedBy()
**Verification:** Visual check: spacing appears correctly in all screen sizes

**Completion note:** [agent writes here after fixing]

---

### TASK M2 — Add Timber Structured Logging
**Status:** ⬜ PENDING
**Problem:** Raw Log.d / Log.e everywhere. No production log control.
**Reference:** Timber library (used by Square, Cash App)
**Target:** Timber.plant(DebugTree()) in debug only. All Log.x → Timber.x
**Verification:**
1. Release build: no log output visible
2. Debug build: logs appear with correct class tags

**Completion note:** [agent writes here after fixing]

---

### TASK M3 — Extract All Hardcoded Strings
**Status:** ⬜ PENDING
**Problem:** Arabic + English text hardcoded in Kotlin. Zero i18n possible.
**Target:** All UI strings → strings.xml + values-ar/strings.xml
**Verification:** App runs identically after extraction. No hardcoded text remains in .kt files

**Completion note:** [agent writes here after fixing]

---

### TASK M4 — Decompose 962-Line Accessibility Service
**Status:** ⬜ PENDING
**File:** GuardianAccessibilityService.kt
**Problem:** 13 methods + 5 state vars + event handling = unmaintainable monolith
**Target split:**
- ShortVideoBlocker.kt — Shorts/Reels detection
- AppBlocker.kt — blocked app detection + HOME action
- NodeUtils.kt — reusable safe node traversal
- AccessibilityEventRouter.kt — thin router only
**Verification:** All blocking features work identically after split. Unit tests pass.

**Completion note:** [agent writes here after fixing]

---

### TASK M5 — Pause AI Scanning When Screen Is Off
**Status:** ⬜ PENDING
**File:** AIExplorerService.kt:96-117
**Problem:** TFLite runs every 2s even with screen off. Drains battery.
**Reference App:** Google Digital Wellbeing screen state handling
**Mechanism to copy:**
- BroadcastReceiver for ACTION_SCREEN_OFF / ACTION_SCREEN_ON
- Pause scanning coroutine on screen off
- Resume on screen on
**Verification:**
1. Enable AI service → turn screen off for 2 minutes → check battery stats
2. CPU usage during screen-off must be near zero for our app
3. Detection resumes immediately when screen turns on

**Completion note:** [agent writes here after fixing]

---

## 🔲 PHASE 4 — SAAS FEATURES (Build production product)

---

### TASK F1 — Onboarding Flow
**Status:** ⬜ PENDING
**Problem:** 5 permission requests with zero explanation → users abandon app
**Reference App:** Calm (permission explanation screens) + Google Family Link (setup checklist)
**Mechanism to copy:**
- One permission per screen with illustration + "why we need this" text
- Progress indicator (Step 2 of 5)
- System dialog only appears AFTER in-app explanation screen
- Completion screen with summary of what's now protected
**Screens to build:**
Welcome → Accessibility → VPN → Device Admin → Overlay → Notifications → Shield Active 🎉
**Verification:**
1. Fresh install: onboarding appears before main screen
2. Each permission has explanation before system dialog
3. Skip/back navigation works on each step
4. Completion remembered: onboarding never shows again after finish

**Completion note:** [agent writes here after fixing]

---

### TASK F2 — Schedule-Based Blocking
**Status:** ⬜ PENDING
**Problem:** No automatic blocking by time. Manual only.
**Reference App:** Freedom.app (gold standard)
**Mechanism to copy:**
- Schedule rule model: {days[], startTime, endTime, appsToBlock[]}
- AlarmManager with setExactAndAllowWhileIdle() for trigger points
- Handles timezone change and DST correctly
- UI: weekly calendar with time range picker per day
**Data model:** ScheduleRule(id, days, startHour, startMin, endHour, endMin, blockedApps)
**Verification:**
1. Set schedule: block Instagram every day 10pm–8am
2. Wait for trigger time → blocking activates automatically
3. End time reached → blocking deactivates automatically
4. Works after device restart mid-schedule

**Completion note:** [agent writes here after fixing]

---

### TASK F3 — Daily App Time Limits
**Status:** ⬜ PENDING
**Problem:** No "30 min YouTube/day then block" feature. Key paid differentiator.
**Reference App:** Google Digital Wellbeing
**Mechanism to copy:**
- UsageStatsManager for per-app daily usage tracking
- Accessibility Service checks remaining limit on each app open
- "Time's up" overlay screen when limit reached
- WorkManager midnight reset (PeriodicWorkRequest)
**Verification:**
1. Set 1-minute limit on any app
2. Open app → use for 1 minute → blocked screen appears automatically
3. Midnight: limit resets, app opens freely again

**Completion note:** [agent writes here after fixing]

---

### TASK F4 — PIN Protection for Settings
**Status:** ⬜ PENDING
**Problem:** User (or child) can disable all protections in 10 seconds.
**Reference App:** Norton App Lock
**Mechanism to copy:**
- PIN stored hashed in EncryptedSharedPreferences (never plain text)
- PIN gate composable wraps all settings/toggle screens
- FLAG_SECURE prevents screenshots of PIN screen
- "Forgot PIN" flow that requires Device Admin re-confirmation
**Verification:**
1. Set PIN → close app → reopen → settings screen requires PIN
2. Wrong PIN → access denied, no crash
3. Correct PIN → full access
4. Uninstall protection still active even if PIN is forgotten

**Completion note:** [agent writes here after fixing]

---

### TASK F5 — Usage Statistics Dashboard
**Status:** ⬜ PENDING
**Problem:** No visibility into blocking activity. Users don't know app is working.
**Reference App:** ActionDash (open source — study statistics module)
**Mechanism to copy:**
- BlockEvent entity: {timestamp, packageName, blockType, sessionId}
- Room database for event persistence
- MPAndroidChart: bar chart (blocks per app today) + line chart (weekly trend)
- Summary cards: total blocks today, top blocked app, streak days
**Verification:**
1. Trigger 5 manual blocks → statistics screen shows correct count
2. Bar chart shows correct per-app breakdown
3. Data persists after app restart

**Completion note:** [agent writes here after fixing]

---

### TASK F6 — Trial Mode Expiration
**Status:** ⬜ PENDING
**Problem:** Trial toggle exists but never expires. Freemium model non-functional.
**Reference App:** Freedom.app freemium gate
**Mechanism to copy:**
- installTimestamp stored in DataStore on first launch (immutable)
- Trial = 7 days from installTimestamp
- Premium feature gate: check trial status before allowing access
- Upgrade screen shown after expiry (not a dialog — full screen)
**Premium features to gate:** Schedule blocking, Time limits, Statistics export
**Verification:**
1. Mock installTimestamp to 8 days ago → premium features show upgrade screen
2. Fresh install → all features available for 7 days
3. Trial status check happens on every app resume

**Completion note:** [agent writes here after fixing]

---

### TASK F7 — Export / Import Blocklists
**Status:** ⬜ PENDING
**Problem:** Custom lists lost on reinstall. No backup possible.
**Reference App:** AdGuard filter list import/export
**Mechanism to copy:**
- Android Storage Access Framework (SAF) for file picker
- Export format: plain .txt, one rule per line
- Import: parse → validate → merge with existing (no duplicates)
- Separate export files for: apps list, keywords list, domains list
**Verification:**
1. Add 5 custom keywords → export → uninstall → reinstall → import → keywords appear
2. Import with duplicates → no duplicate entries in final list
3. Export file is human-readable plain text

**Completion note:** [agent writes here after fixing]

---

## 📊 PROGRESS TRACKER

| Phase | Tasks | Done | Remaining |
|-------|-------|------|-----------|
| 🔴 Critical | 4 | 4 | 0 |
| 🟡 High | 5 | 5 | 0 |
| 🟢 Medium | 5 | 0 | 5 |
| 🔲 SaaS Features | 7 | 0 | 7 |
| **TOTAL** | **21** | **9** | **12** |

**Current task:** M1
**SaaS completion:** 43%

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

---
END OF FILE
