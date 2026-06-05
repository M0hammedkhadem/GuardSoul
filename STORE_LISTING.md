# GuardSoul — Google Play Store Listing (SaaS 1.0.0)

> Copy this content into Google Play Console → Store Listing.

---

## App name
**GuardSoul — Digital Wellbeing & Content Blocker**

## Short description (80 chars max)
Block apps, filter adult content, and reclaim your attention.

## Full description (4000 chars max)

Take back your phone. GuardSoul is the all-in-one digital wellbeing app that blocks distracting apps, filters adult content, and helps you build healthier habits with the people you trust.

**BUILT FOR ANDROID**
- Works on any Android 8.0+ device — no root required
- Native Kotlin + Jetpack Compose UI for a fast, fluid experience
- Lightweight (under 50 MB)

**PROTECT YOUR ATTENTION**
- Block social media apps (Instagram, TikTok, Snapchat, X, YouTube, Facebook) on a schedule, a daily time limit, or fully
- Block specific content inside apps — YouTube Shorts, Instagram Reels, Facebook Reels — without giving up the rest of the app
- Lock critical settings behind a PIN so impulse can't undo your decisions
- Choose a deactivation delay of 2, 7, 15, or 30 days before you can turn the shield off (Bulldog-style cooldown)

**FILTER ADULT CONTENT**
- On-device NSFW screen scanner uses a TensorFlow Lite model — your screenshots never leave your phone
- Optional 3-strike system: 3 sensitive detections in 4 minutes = 15-minute app cooldown
- DNS-based filtering routes adult domains through CleanBrowsing (works on every browser, every app)
- Whitelist & blacklist with default safe-search keywords already loaded

**BUILD LASTING HABITS**
- Day counter shows how long your shield has been active — don't break the streak
- 6-tier progression (Mindful Beginner → Enlightened) with XP and milestones
- Study Room locks you into 60 minutes of focus with a curated allow-list (education, productivity, books, notes)
- Daily pledge & withdrawal timeline to track your recovery
- Weekly share cards (1080×1080 PNG) — celebrate your wins

**SECURE & PRIVATE**
- All NSFW detection happens on-device
- Cloud sync (Pro) is opt-in and end-to-end encrypted in transit
- GDPR & CCPA compliant — analytics and crash reports off by default in the EU/UK
- Open-source privacy policy and terms of service in the app

**FREE vs PRO vs PREMIUM**
- **Free** — core blocking for up to 3 social apps, 50 keywords/websites
- **Pro** — unlimited social apps, cloud sync, advanced statistics, Study Room, custom blocklists
- **Premium** — everything in Pro + AI NSFW scanner + accountability partner + priority support

**SUBSCRIPTION**
- Subscriptions are billed monthly or annually through Google Play
- Cancel anytime in Play Store → Subscriptions
- Free trial available for new subscribers (offered by Google Play)
- Prices vary by region

**WHAT'S NEW IN 1.0**
- Full SaaS subscription model with Google Play Billing
- Account system: sign in with email, Google, or continue as guest
- Cloud sync for Pro/Premium (Firestore)
- In-app Privacy Policy and Terms of Service
- GDPR consent flow for European users
- Firebase Crashlytics for faster crash fixes
- 100+ under-the-hood improvements for stability and performance

GuardSoul is built by a small team that believes the phone should work for you, not the other way around.

## App icon
See `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`

## Feature graphic
1024 × 500 px (TBD before submission)

## Screenshots
Recommended: 8 phone screenshots (16:9), 1080 × 1920 px.

Suggested order:
1. **Home** — Shield orb with day counter
2. **Social** — Per-app blocking mode selector
3. **Content** — Porn Blocker + AI Explorer
4. **Lists** — Whitelist/Blacklist manager
5. **Statistics** — Weekly chart
6. **Schedule** — School time + bedtime
7. **Account** — Profile with tier badge
8. **Upgrade** — Paywall with comparison

## Categorization
- **Category:** Health & Fitness
- **Tags:** digital wellbeing, app blocker, content filter, screen time, focus, NSFW blocker

## Content rating
- **IARC:** Everyone / PEGI 3 (with optional adult-content filtering)
- **Target audience:** 13+

## Privacy
- Privacy policy URL: https://guardsoul.app/privacy
- Data safety form: completed in Play Console
- Data collected: account email (optional), crash reports (opt-in), analytics (opt-in)
- Data NOT collected: contacts, location, photos, microphone

## App access
- No special access required to test (all gated by 5-min free trial)
- Provide test account for Play Console reviewers (TBD)

## Ads
- No ads (subscription-only)

## In-app purchases
- `guardsoul_pro_monthly` — Pro monthly
- `guardsoul_pro_yearly` — Pro yearly (save 40%)
- `guardsoul_premium_monthly` — Premium monthly
- `guardsoul_premium_yearly` — Premium yearly (save 40%)

## Countries
- All countries where Google Play Billing is available (~190)

## Contact
- Developer email: support@guardsoul.app
- Privacy: privacy@guardsoul.app
- Website: https://guardsoul.app

---

## Pre-submission checklist

- [x] Debug APK builds successfully (45.8 MB)
- [x] All 100+ DataStore keys migrated to SaaS tiers
- [x] All R8/ProGuard rules updated for new deps
- [x] Privacy Policy + Terms of Service in app
- [x] GDPR consent (UMP) integrated
- [x] Firebase Crashlytics + Analytics configured
- [x] In-App Review gated by usage threshold
- [x] Multi-language (ar + en) supported
- [ ] Generate upload keystore for production
- [ ] Sign release build with upload keystore
- [ ] Test release build on physical device
- [ ] Create app signing key in Play Console
- [ ] Submit for internal testing
