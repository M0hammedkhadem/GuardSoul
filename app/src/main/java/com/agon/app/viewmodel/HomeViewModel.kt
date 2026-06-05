package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.AppBlockerService
import com.agon.app.AiScannerService
import com.agon.app.PornBlockerService
import com.agon.app.GuardianApp
import com.agon.app.data.local.dao.MostBlockedApp
import com.agon.app.data.settings.AppSettings
import com.agon.app.guardianApp
import com.agon.app.utils.AccountabilityPartner
import com.agon.app.utils.DisciplineScore
import com.agon.app.utils.DisciplineTier
import com.agon.app.utils.DisciplineTiers
import com.agon.app.utils.Milestones
import com.agon.app.utils.SecurityUtils
import com.agon.app.utils.ShareCardData
import com.agon.app.utils.StudyRoom
import com.agon.app.utils.WithdrawalTimeline
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application.guardianApp()!!).repository
    private val settings = repo.getAppSettings()

    val shieldActive: StateFlow<Boolean> = settings.shieldActiveFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    val deactivationDelay: StateFlow<Int> = settings.deactivationDelayFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        
    val trialMode: StateFlow<Boolean> = settings.trialModeFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val strictMode: StateFlow<Boolean> = settings.strictModeFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Wall-clock end timestamp (ms) for the strict-mode cooldown. While
     * non-zero and in the future the user can't flip Strict Mode off
     * (15-min "cool down" before they can soften protection again).
     */
    val strictModeCooldownEndAt: StateFlow<Long> = settings.strictModeCooldownEndAtFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val accountabilityEnabled: StateFlow<Boolean> = settings.accountabilityEnabledFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val accountabilityEmail: StateFlow<String> = settings.accountabilityEmailFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val totalBlocks: StateFlow<Int> = repo.totalBlocksFlow()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val blocksToday: StateFlow<Int> = repo.blocksTodayFlow()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val mostBlockedApp: StateFlow<MostBlockedApp?> = repo.mostBlockedAppFlow()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Number of full days the shield has been continuously active.
     * Resets to 0 the moment the shield is turned off (activatedAt is cleared).
     * Ticks once a minute so the UI doesn't spam recompositions.
     */
    val daysActive: StateFlow<Int> = combine(
        settings.shieldActiveFlow,
        settings.shieldActivatedAtFlow,
        timeTickFlow(60_000L)
    ) { active, activatedAt, _ ->
        if (!active || activatedAt <= 0L) 0
        else AppSettings.calculateDaysActive(activatedAt)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val streakCount: StateFlow<Int> = settings.streakCountFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val profileName: StateFlow<String> = settings.profileNameFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val hasPin: StateFlow<Boolean> = settings.pinHashFlow.map { it.isNotBlank() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val xpPoints: StateFlow<Int> = settings.xpPointsFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val level: StateFlow<Int> = settings.levelFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val pornBlockerActive: StateFlow<Boolean> = settings.pornBlockerFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val aiScannerActive: StateFlow<Boolean> = settings.aiScannerFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _countdownActive = MutableStateFlow(false)
    val countdownActive: StateFlow<Boolean> = _countdownActive.asStateFlow()

    /**
     * Wall-clock end timestamp (ms) for the current deactivation countdown.
     * The UI derives the displayed remaining time from this every second so
     * the progress bar animates fluidly even when the logic loop only
     * wakes every 60s.
     */
    private val _countdownEndAt = MutableStateFlow(0L)

    /**
     * Seconds remaining in the current deactivation countdown. Updates once
     * per second; clamped to zero. The CountdownOverlay reads this for both
     * the digits and the linear progress bar.
     */
    val remainingSeconds: StateFlow<Int> = flow {
        while (true) {
            val end = _countdownEndAt.value
            val remaining = if (end <= 0L) 0
                            else ((end - System.currentTimeMillis()) / 1_000L).toInt().coerceAtLeast(0)
            emit(remaining)
            delay(1_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog.asStateFlow()

    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    /**
     * Dialog for the Accountability-Partner approval flow. When
     * non-null the dialog is shown and the user must enter the
     * 6-digit code their partner received by email.
     */
    private val _showPartnerDialog = MutableStateFlow(false)
    val showPartnerDialog: StateFlow<Boolean> = _showPartnerDialog.asStateFlow()

    private val _partnerError = MutableStateFlow<String?>(null)
    val partnerError: StateFlow<String?> = _partnerError.asStateFlow()

    /** Set when a new partner unlock code has been generated and emailed. */
    private val _partnerRequestInFlight = MutableStateFlow(false)
    val partnerRequestInFlight: StateFlow<Boolean> = _partnerRequestInFlight.asStateFlow()

    /** Number of consecutive failed PIN attempts in the current dialog session. */
    private var pinFailCount = 0

    private var countdownJob: Job? = null

    init {
        // Single combine so shield × porn-blocker × ai-scanner transitions are
        // atomic — no double-start/stop, no race when the user flips toggles
        // while the shield is off.
        viewModelScope.launch {
            combine(
                shieldActive,
                pornBlockerActive,
                aiScannerActive
            ) { shield, porn, ai -> Triple(shield, porn, ai) }
                .distinctUntilChanged()
                .collect { (active, porn, ai) ->
                    val context = getApplication<GuardianApp>()
                    if (active) {
                        AppBlockerService.start(context)
                        if (porn) PornBlockerService.start(context)
                        else PornBlockerService.stop(context)
                        if (ai) AiScannerService.start(context)
                    } else {
                        AppBlockerService.stop(context)
                        PornBlockerService.stop(context)
                        AiScannerService.stop(context)
                    }
                }
        }

        // Auto-award milestones whenever the streak day advances.
        // distinctUntilChanged is already applied to `daysActive` so
        // we only fire on actual day changes (not every minute).
        viewModelScope.launch {
            daysActive
                .filter { it > 0 }
                .collect { checkMilestones() }
        }
    }

    fun toggleShield() {
        viewModelScope.launch {
            val current = shieldActive.value
            if (!current) {
                val now = System.currentTimeMillis()
                settings.setLastActiveDate(now)
                settings.setShieldActivatedAt(now)
                settings.setShieldActive(true)
            } else {
                startDeactivation()
            }
        }
    }

    fun startDeactivation() {
        if (trialMode.value || deactivationDelay.value <= 0) {
            // When the user has no delay, still go through the
            // partner/strict gates if they're enabled. Strict-mode
            // PIN and Accountability Partner both block immediate
            // deactivation even when `deactivationDelay == 0`.
            if (accountabilityEnabled.value && accountabilityEmail.value.isNotBlank()) {
                requestPartnerApproval()
                return
            }
            if (strictMode.value && hasPin.value) {
                _showPinDialog.value = true
                return
            }
            completeDeactivation()
            return
        }

        countdownJob?.cancel()
        _countdownActive.value = true
        // `deactivationDelay` is stored in DAYS; convert to a wall-clock
        // end timestamp. The UI's remainingSeconds flow derives the
        // displayed time from this every second so the progress bar
        // animates smoothly even though this job only wakes every 60s
        // (keeps the JVM out of the foreground for a 30-day timer).
        val totalSeconds = deactivationDelay.value.toLong() * 24L * 60L * 60L
        _countdownEndAt.value = System.currentTimeMillis() + totalSeconds * 1_000L
        countdownJob = viewModelScope.launch {
            // Drive the countdown by wall-clock. The 60-second wake keeps
            // the JVM idle for the long options; the UI's 1-Hz tick
            // covers the user-visible side.
            val endAt = _countdownEndAt.value
            while (System.currentTimeMillis() < endAt) {
                val remainingMs = endAt - System.currentTimeMillis()
                val tickChunk = if (remainingMs > 60_000L) 60_000L else 1_000L
                delay(tickChunk)
            }
            _countdownEndAt.value = 0L
            _countdownActive.value = false
            // After the delay: run Accountability Partner gate first,
            // then strict-mode PIN, then full deactivation.
            if (accountabilityEnabled.value && accountabilityEmail.value.isNotBlank()) {
                requestPartnerApproval()
            } else if (strictMode.value && hasPin.value) {
                _showPinDialog.value = true
            } else {
                completeDeactivation()
            }
        }
    }

    /**
     * Sends an unlock request to the partner and opens the
     * partner-approval dialog. The user must type back the 6-digit
     * code their partner received by email within 5 minutes.
     */
    private fun requestPartnerApproval() {
        val email = accountabilityEmail.value
        if (email.isBlank()) {
            // Misconfigured: no email. Fall back to strict-mode PIN
            // or just complete if neither is configured.
            if (strictMode.value && hasPin.value) _showPinDialog.value = true
            else completeDeactivation()
            return
        }
        viewModelScope.launch {
            _partnerRequestInFlight.value = true
            _partnerError.value = null
            try {
                AccountabilityPartner.requestUnlock(getApplication(), settings, email)
                _showPartnerDialog.value = true
            } catch (t: Throwable) {
                _partnerError.value = "Failed to request approval: ${t.message}"
            } finally {
                _partnerRequestInFlight.value = false
            }
        }
    }

    /**
     * User typed the partner's 6-digit code back. Verifies it against
     * the pending request, then either proceeds to the next gate
     * (strict PIN) or completes the deactivation.
     */
    fun verifyPartnerCode(input: String) {
        viewModelScope.launch {
            when (AccountabilityPartner.verify(settings, input)) {
                AccountabilityPartner.Result.OK -> {
                    _showPartnerDialog.value = false
                    _partnerError.value = null
                    if (strictMode.value && hasPin.value) {
                        _showPinDialog.value = true
                    } else {
                        completeDeactivation()
                    }
                }
                AccountabilityPartner.Result.EXPIRED -> {
                    _partnerError.value = "Code expired. Request a new one."
                    // Auto-retry once to be friendly.
                    requestPartnerApproval()
                }
                AccountabilityPartner.Result.MISMATCH -> {
                    _partnerError.value = "Wrong code. Try again or request a new one."
                }
                AccountabilityPartner.Result.NO_PENDING_REQUEST -> {
                    _partnerError.value = "No pending request. Tap 'Send new code'."
                }
            }
        }
    }

    fun dismissPartnerDialog() {
        _showPartnerDialog.value = false
        _partnerError.value = null
        viewModelScope.launch { settings.clearPendingUnlockCode() }
    }

    /** Re-send a fresh unlock request (used when the previous code expired). */
    fun resendPartnerRequest() {
        requestPartnerApproval()
    }

    fun verifyPin(input: String) {
        viewModelScope.launch {
            val storedHash = settings.getPinHash()
            if (SecurityUtils.verifyPinAgainstHash(input, storedHash)) {
                pinFailCount = 0
                _showPinDialog.value = false
                completeDeactivation()
            } else {
                pinFailCount += 1
                _pinError.value = true
                // FEATURES_SPEC §9: log a tamper alert after 3 failed attempts.
                if (pinFailCount >= 3) {
                    try {
                        repo.recordTamperAlert(
                            type = "pin_failed",
                            detail = "3+ consecutive PIN failures on shield deactivation"
                        )
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun cancelDeactivation() {
        countdownJob?.cancel()
        _countdownActive.value = false
        _countdownEndAt.value = 0L
    }

    fun dismissPinDialog() {
        _showPinDialog.value = false
        _pinError.value = false
        pinFailCount = 0
    }

    /**
     * Clears the inline PIN error highlight without dismissing the dialog.
     * Called from the UI when the user starts re-typing after a failed
     * attempt so the field doesn't stay red across the next entry.
     */
    fun dismissPinError() {
        _pinError.value = false
    }

    private fun completeDeactivation() {
        viewModelScope.launch {
            settings.setShieldActive(false)
            settings.setShieldActivatedAt(0L)
            _countdownActive.value = false
            _countdownEndAt.value = 0L
        }
    }

    /**
     * Emits [Unit] immediately and then once every [intervalMs]. Used to keep
     * the `daysActive` counter rolling forward without the user having to
     * interact with the app. Auto-cancels with the ViewModel scope.
     */
    private fun timeTickFlow(intervalMs: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(intervalMs)
        }
    }

    /**
     * Toggle trial mode on/off. Independent of the shield — the user can
     * flip it any time and the shield keeps running unaffected.
     */
    fun toggleTrialMode() {
        viewModelScope.launch { settings.setTrialMode(!trialMode.value) }
    }

    /**
     * Update the deactivation delay (in days) from the home screen chip selector.
     */
    fun setDeactivationDelay(days: Int) {
        viewModelScope.launch { settings.setDeactivationDelay(days) }
    }

    /**
     * Toggle Strict Mode. Mirrors AppBlock + Stay Focused: the moment
     * you turn it ON, a 15-minute cooldown starts during which you
     * can't turn it back off — prevents the "I just turned it on
     * five minutes ago and now I regret it" impulse.
     *
     * Toggle OFF is also blocked while a cooldown is active.
     */
    fun toggleStrictMode() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cooldownEnd = strictModeCooldownEndAt.value
            val isOn = strictMode.value
            if (isOn) {
                // Turn OFF: blocked if we're still in the 15-min cooldown
                if (cooldownEnd > now) {
                    return@launch
                }
                settings.setStrictMode(false)
                settings.setStrictModeCooldownEndAt(0L)
            } else {
                // Turn ON: start a fresh 15-min cooldown.
                settings.setStrictMode(true)
                settings.setStrictModeCooldownEndAt(now + 15L * 60L * 1_000L)
            }
        }
    }

    /** How many seconds remain in the current strict-mode cooldown (0 if none). */
    val strictModeCooldownRemainingSeconds: StateFlow<Int> = flow {
        while (true) {
            val end = strictModeCooldownEndAt.value
            val remaining = if (end <= 0L) 0
                            else ((end - System.currentTimeMillis()) / 1_000L).toInt().coerceAtLeast(0)
            emit(remaining)
            delay(1_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ----------------------------------------------------------------
    // Daily pledge + milestones (I Am Sober style).
    // ----------------------------------------------------------------

    /**
     * `true` once the user has explicitly taken the daily pledge for
     * today's date. The dialog re-arms itself at midnight local time.
     */
    val dailyPledgeTaken: StateFlow<Boolean> = settings.dailyPledgeDateFlow
        .map { it == todayKey() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _pendingMilestone = MutableStateFlow<Milestones.Milestone?>(null)
    val pendingMilestone: StateFlow<Milestones.Milestone?> = _pendingMilestone.asStateFlow()

    /**
     * Current withdrawal phase derived from the streak day. Recomputes
     * whenever `daysActive` changes so the home screen can show the
     * matching phase card.
     */
    val withdrawalPhase: StateFlow<WithdrawalTimeline.Phase?> = daysActive
        .map { WithdrawalTimeline.phaseFor(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Count of porn-related blocks in the last 7 days. Used as the
     * negative component of the discipline score (the more attempts, the
     * less XP is awarded for that signal). Mirrors the way Screen Stoic
     * and I Am Sober penalise relapse.
     *
     * Block types counted: anything that starts with `ai_sensitive` or
     * is a blacklisted keyword hit. We exclude `app_blocker` since
     * that's a school-time / bedtime block, not a user attempt.
     */
    val pornAttemptsLast7Days: StateFlow<Int> = repo
        .getBlockEventsByDateRange(System.currentTimeMillis() - 7L * 86_400_000L, System.currentTimeMillis())
        .map { events ->
            events.count { it.blockType.startsWith("ai_sensitive") ||
                           it.blockType.contains("porn", ignoreCase = true) ||
                           it.blockType == "blacklist_keyword" }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Composite discipline score — see [DisciplineScore] for the
     * formula. Recomputed whenever any of its inputs change.
     */
    val disciplineScore: StateFlow<Int> = combine(
        daysActive,
        settings.milestonesAchievedFlow,
        pornAttemptsLast7Days,
        dailyPledgeTaken
    ) { streak, achieved, attempts, pledge ->
        DisciplineScore.compute(
            streakDays = streak,
            milestonesAchieved = achieved.size,
            weeklyPornAttempts = attempts,
            todayPledgeTaken = pledge
        )
    }.distinctUntilChanged()
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Tier for the current score — display only. */
    val tier: StateFlow<DisciplineTier> = disciplineScore
        .map { DisciplineTiers.tierFor(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DisciplineTiers.MIND_BEGINNER)

    /**
     * Whether a Study Room is currently active. Driven by a 1Hz
     * ticker on top of `studyRoomActiveUntilFlow` so the timer
     * counts down smoothly in the UI without spamming DataStore.
     */
    val studyRoomActiveUntil: StateFlow<Long> = settings.studyRoomActiveUntilFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val studyRoomRemainingMs: StateFlow<Long> = studyRoomActiveUntil
        .map { StudyRoom.remainingMs(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /**
     * Open a Study Room for [durationMinutes] starting now. While
     * active, every non-education / non-productivity app is
     * blocked by `AppBlockerService`. Total focused minutes
     * accumulate in [AppSettings.studyRoomTotalMinutesFocused].
     */
    fun startStudyRoom(durationMinutes: Int = StudyRoom.DEFAULT_DURATION_MINUTES) {
        viewModelScope.launch {
            val until = System.currentTimeMillis() + durationMinutes * 60_000L
            settings.setStudyRoomActiveUntil(until)
            settings.setStudyRoomDurationMinutes(durationMinutes)
        }
    }

    fun stopStudyRoom() {
        viewModelScope.launch {
            val current = settings.studyRoomActiveUntilFlow.first()
            val remaining = StudyRoom.remainingMs(current)
            // Roll the *used* minutes into the persistent total.
            if (remaining > 0L) {
                val usedMs = StudyRoom.DEFAULT_DURATION_MINUTES * 60_000L - remaining
                val usedMin = (usedMs / 60_000L).toInt()
                val total = settings.studyRoomTotalMinutesFocusedFlow.first()
                settings.setStudyRoomTotalMinutesFocused(total + usedMin)
            }
            settings.setStudyRoomActiveUntil(0L)
        }
    }

    fun takeDailyPledge() {
        viewModelScope.launch {
            settings.setDailyPledgeDate(todayKey())
            checkMilestones()
        }
    }

    /**
     * Walk the user's current streak and award any milestones they
     * just hit. XP is bumped in-line; a celebration dialog is queued
     * via [pendingMilestone] for the UI to render.
     *
     * Idempotent: each milestone is in [milestonesAchievedFlow] so we
     * never double-award.
     */
    fun checkMilestones() {
        viewModelScope.launch {
            val streak = daysActive.value
            val achieved = settings.milestonesAchievedFlow.first()
            val newOnes = Milestones.newlyAchieved(streak, achieved)
            if (newOnes.isEmpty()) return@launch
            val updated = achieved + newOnes.map { it.id }
            settings.setMilestonesAchieved(updated)
            settings.setLastMilestoneCheck(System.currentTimeMillis())
            val xpBoost = newOnes.sumOf { it.xpReward }
            val newXp = settings.xpPointsFlow.first() + xpBoost
            settings.setXpPoints(newXp)
            // Recompute level: 100 XP per level, 1-indexed.
            settings.setLevel((newXp / 100) + 1)
            // Queue the first new milestone for the celebration dialog.
            // The UI clears it by calling [dismissMilestoneDialog].
            _pendingMilestone.value = newOnes.first()
        }
    }

    fun dismissMilestoneDialog() {
        _pendingMilestone.value = null
    }

    /**
     * Build a snapshot of the home-screen state suitable for the
     * share-card generator. Pulls the latest values from the
     * `StateFlow`s so the rendered card matches what the user
     * sees on screen at the moment of tapping Share.
     *
     * Suspended because we need to read the milestones-achieved
     * `Flow.first()` — the other inputs are `StateFlow.value`
     * which is non-suspending.
     */
    suspend fun buildShareCardData(weeklyBlockCount: Int): ShareCardData = ShareCardData(
        daysActive = daysActive.value,
        milestonesAchieved = settings.milestonesAchievedFlow.first().size,
        disciplineScore = disciplineScore.value,
        todayPledgeTaken = dailyPledgeTaken.value,
        weeklyBlockCount = weeklyBlockCount
    )

    /** YYYYMMDD in the device's local timezone — used as the daily-pledge key. */
    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        return "%04d%02d%02d".format(y, m, d)
    }
}
