package com.example.hearo

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.hearo.ui.theme.HearoTheme

private const val TAG = "HearoNfc"
private const val PLAYLIST_NO_ITEM = "no item"

/** Spotify context types that do not support offset (track/position). Use context URI only. */
private fun isContextWithoutOffset(uri: String?): Boolean =
    uri != null && uri.startsWith("spotify:artist:", ignoreCase = true)
private const val PROGRESS_POLL_MS = 1000L
private const val SPOTIFY_CHECK_DELAY_MS = 500L
private const val REFRESH_WHEN_UNDER_MS = 15 * 60 * 1000L
private const val NO_DEVICE_RETRY_DELAY_MS = 2000L
private const val PLAY_FAIL_RETRY_DELAY_MS = 1500L
private const val AFTER_TRANSFER_DELAY_MS = 600L
/** Delay before retry after launching Spotify so the app can start and register its device. */
private const val SPOTIFY_LAUNCH_RETRY_DELAY_MS = 3000L
class MainActivity : ComponentActivity() {

    companion object {
        @Volatile
        private var spotifyCheckedOnce = false
        /** Cleared once per process so we require sign-in on app restart but not on activity recreate (e.g. rotation). */
        @Volatile
        private var spotifyClearedThisProcess = false
    }

    private val nfcReader by lazy { NfcReader(this) }
    private val spotifyAuth by lazy { SpotifyAuth(this) }
    private val spotifyWebApi by lazy { SpotifyWebApi(spotifyAuth) }
    private val spotifyController by lazy {
        SpotifyController(this, SpotifyController.notificationListenerComponent(this))
    }
    private val nfcPlaylistRepository by lazy { NfcPlaylistRepository(this) }
    private val preferredDeviceStore by lazy { PreferredDeviceStore(this) }
    private val listeningTimeStore by lazy { ListeningTimeStore(this) }

    private val nfcIdState = mutableStateOf("")
    private val playlistUrlState = mutableStateOf("")
    private val artistState = mutableStateOf("Artist")
    private val trackTitleState = mutableStateOf("Title")
    private val progressMsState = mutableStateOf(0L)
    private val durationMsState = mutableStateOf(0L)
    private val signedInState = mutableStateOf(false)
    private val nfcReadyState = mutableStateOf(true)
    /** True after Save is clicked for current tag; reset when tag is removed so Save is shown again when tag is presented. */
    private val justSavedState = mutableStateOf(false)
    /** Show "Please sign in before playing" when a tag was tapped while signed out. */
    private val showSignInBannerState = mutableStateOf(false)
    /** True while processing auth redirect; show neutral screen to avoid flashing InitialScreen. */
    private val handlingRedirectState = mutableStateOf(false)
    /** Preferred Spotify device: id for playback, display string for UI. */
    private val preferredDeviceIdState = mutableStateOf<String?>(null)
    private val preferredDeviceDisplayState = mutableStateOf("No preferred device")
    /** True when device selection screen is shown. */
    private val showDeviceSelectorState = mutableStateOf(false)
    /** Current devices list for the selector (fetched when selector opens). */
    private val deviceListForSelectorState = mutableStateOf<List<SpotifyDevice>>(emptyList())
    /** Listening time: counter seconds since last reset. */
    private val listeningCounterState = mutableStateOf(0L)
    /** Formatted listening limit for UI (e.g. "1:30:00"). */
    private val listeningLimitDisplayState = mutableStateOf("1:00:00")
    /** True when counter reached limit: playback paused, controls blocked until reset. */
    private val listeningLimitReachedState = mutableStateOf(false)
    /** True when the listening limit time picker should be shown. */
    private val showListeningLimitPickerState = mutableStateOf(false)

    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressPollRunnable: Runnable? = null
    private var proactiveRefreshRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Require fresh sign-in when app process starts (not on activity recreate e.g. rotation)
        if (!spotifyClearedThisProcess) {
            spotifyAuth.signOut()
            spotifyClearedThisProcess = true
        }
        signedInState.value = spotifyAuth.hasToken()
        spotifyAuth.onRefreshFailed = { handleSpotifyRefreshFailed() }
        nfcReader.init(
            onTagDetected = ::handleTagDetected,
            onTagRemoved = {
                val tagId = nfcIdState.value
                Log.d(TAG, "onTagRemoved: saving progress for tag, pausing, clearing")
                stopProgressPolling()
                if (spotifyAuth.hasToken() && tagId.isNotEmpty()) {
                    spotifyWebApi.getPlayerState { state ->
                        if (state != null && state.contextUri != null && state.trackUri != null) {
                            nfcPlaylistRepository.setProgress(
                                tagId,
                                state.contextUri,
                                state.trackUri,
                                state.progressMs
                            )
                        }
                        if (spotifyAuth.hasToken()) {
                            spotifyWebApi.pause { clearTagUi() }
                        } else {
                            spotifyController.pause()
                            clearTagUi()
                        }
                    }
                } else {
                    if (spotifyAuth.hasToken()) spotifyWebApi.pause { clearTagUi() }
                    else {
                        spotifyController.pause()
                        clearTagUi()
                    }
                }
            }
        )
        enableEdgeToEdge()
        setContent {
            HearoTheme {
                when {
                    signedInState.value -> Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Box(Modifier.padding(innerPadding)) {
                            HearoScreen(
                                artistName = artistState.value,
                                trackTitle = trackTitleState.value,
                                progressMs = progressMsState.value,
                                durationMs = durationMsState.value,
                                nfcId = nfcIdState.value,
                                playlistUrl = playlistUrlState.value,
                                signedIn = signedInState.value,
                                nfcReady = nfcReadyState.value,
                                showSignInBanner = showSignInBannerState.value,
                                onSignInClick = { spotifyAuth.signIn(BuildConfig.SPOTIFY_CLIENT_ID) },
                                onSignOutClick = {
                                    cancelProactiveRefresh()
                                    clearTagUi()
                                    spotifyAuth.signOut()
                                    signedInState.value = false
                                },
                                playlistFieldEnabled = nfcIdState.value.isNotEmpty() && nfcPlaylistRepository.getPlaylistUrl(nfcIdState.value) == null,
                                showSaveButton = nfcIdState.value.isNotEmpty() && nfcPlaylistRepository.getPlaylistUrl(nfcIdState.value) == null && !justSavedState.value,
                                saveButtonEnabled = nfcIdState.value.isNotEmpty() &&
                                    playlistUrlState.value.isNotBlank() &&
                                    playlistUrlState.value != PLAYLIST_NO_ITEM,
                                showDetachButton = nfcIdState.value.isNotEmpty() && nfcPlaylistRepository.getPlaylistUrl(nfcIdState.value) != null,
                                onPlaylistUrlChange = { playlistUrlState.value = it },
                                onSaveClick = {
                                    val id = nfcIdState.value
                                    val url = playlistUrlState.value
                                    if (id.isNotEmpty() && url.isNotBlank() && url != PLAYLIST_NO_ITEM) {
                                        nfcPlaylistRepository.setPlaylistUrl(id, url)
                                        playlistUrlState.value = url
                                        justSavedState.value = true
                                    }
                                },
                                onDetachClick = {
                                    val id = nfcIdState.value
                                    if (id.isNotEmpty()) {
                                        nfcPlaylistRepository.remove(id)
                                        playlistUrlState.value = ""
                                    }
                                },
                                onSkipBack = {
                                    if (spotifyAuth.hasToken()) spotifyWebApi.skipPrevious()
                                    else spotifyController.skipToPrevious()
                                },
                                onSeekBack = {
                                    if (spotifyAuth.hasToken()) spotifyWebApi.seekRelative(-15_000)
                                    else spotifyController.seekRelative(-15_000)
                                },
                                onSeekFwd = {
                                    if (spotifyAuth.hasToken()) spotifyWebApi.seekRelative(15_000)
                                    else spotifyController.seekRelative(15_000)
                                },
                                onSkipFwd = {
                                    if (spotifyAuth.hasToken()) spotifyWebApi.skipNext()
                                    else spotifyController.skipToNext()
                                },
                                preferredDeviceDisplay = preferredDeviceDisplayState.value,
                                onEditPreferredDeviceClick = {
                                    showDeviceSelectorState.value = true
                                    spotifyWebApi.getDevices { deviceListForSelectorState.value = it }
                                },
                                listeningCounterDisplay = formatListeningTime(listeningCounterState.value),
                                listeningLimitDisplay = listeningLimitDisplayState.value,
                                listeningLimitReached = listeningLimitReachedState.value,
                                listeningProgress = run {
                                    val active = listeningTimeStore.getActiveLimitSeconds()
                                    if (active <= 0) 0f else (listeningCounterState.value.toFloat() / active).coerceIn(0f, 1f)
                                },
                                onResetListeningTime = {
                                    listeningTimeStore.applyReset()
                                    listeningLimitReachedState.value = false
                                    refreshListeningTimeDisplay()
                                },
                                onEditListeningLimitClick = { showListeningLimitPickerState.value = true }
                            )
                            if (showListeningLimitPickerState.value) {
                                ListeningLimitPickerDialog(
                                    initialLimitSeconds = listeningTimeStore.getLimitSeconds(),
                                    onConfirm = {
                                        listeningTimeStore.setLimitSeconds(it)
                                        listeningLimitDisplayState.value = formatListeningTime(it.toLong())
                                        showListeningLimitPickerState.value = false
                                    },
                                    onDismiss = { showListeningLimitPickerState.value = false }
                                )
                            }
                            if (showDeviceSelectorState.value) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White)
                                ) {
                                    DeviceSelectionScreen(
                                        currentDevices = deviceListForSelectorState.value,
                                        currentPreferredId = preferredDeviceIdState.value,
                                        onSave = { deviceId, deviceName, deviceType ->
                                            preferredDeviceStore.setPreferredDevice(deviceId, deviceName, deviceType)
                                            preferredDeviceIdState.value = deviceId
                                            preferredDeviceDisplayState.value = when {
                                                deviceId.isNullOrBlank() -> "No preferred device"
                                                deviceName != null && deviceType != null -> "$deviceName ($deviceType)"
                                                deviceName != null -> deviceName
                                                else -> deviceId
                                            }
                                            showDeviceSelectorState.value = false
                                        },
                                        onCancel = { showDeviceSelectorState.value = false }
                                    )
                                }
                            }
                        }
                    }
                    handlingRedirectState.value -> RedirectHandlingScreen()
                    else -> InitialScreen(onSignInClick = { spotifyAuth.signIn(BuildConfig.SPOTIFY_CLIENT_ID) })
                }
            }
        }
        intent?.let { tryHandleSpotifyRedirect(it) }
    }

    /** Launches Spotify and brings the user back to Hearo. Bring-to-front is called immediately (same thread) so we're still in foreground and BAL does not block. */
    private fun launchSpotifyAndReturnToHearo() {
        spotifyController.launchSpotify()
        bringHearoToFront()
    }

    private fun bringHearoToFront() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private fun formatListeningTime(seconds: Long): String {
        val total = seconds.toInt().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
            else -> "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
        }
    }

    private fun refreshListeningTimeDisplay() {
        listeningCounterState.value = listeningTimeStore.getCounterSeconds()
        listeningLimitDisplayState.value = formatListeningTime(listeningTimeStore.getLimitSeconds().toLong())
        val active = listeningTimeStore.getActiveLimitSeconds()
        if (listeningCounterState.value >= active) {
            listeningLimitReachedState.value = true
        }
    }

    private fun playLimitReachedSound() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
            progressHandler.postDelayed({ tone.release() }, 300)
        } catch (_: Exception) { }
    }

    private fun ensureSpotifyOnce() {
        progressHandler.postDelayed({
            if (spotifyCheckedOnce) return@postDelayed
            spotifyCheckedOnce = true
            if (!spotifyController.isSpotifyRunning()) {
                launchSpotifyAndReturnToHearo()
            }
        }, SPOTIFY_CHECK_DELAY_MS)
    }

    override fun onResume() {
        super.onResume()
        signedInState.value = spotifyAuth.hasToken()
        preferredDeviceIdState.value = preferredDeviceStore.getPreferredDeviceId()
        preferredDeviceDisplayState.value = preferredDeviceStore.getPreferredDeviceDisplay() ?: "No preferred device"
        if (listeningTimeStore.checkAndApplyMidnightReset()) {
            listeningLimitReachedState.value = false
        }
        refreshListeningTimeDisplay()
        if (spotifyAuth.hasToken()) {
            val expiresAt = spotifyAuth.getExpiresAtMs()
            if (System.currentTimeMillis() >= expiresAt - REFRESH_WHEN_UNDER_MS) {
                runProactiveRefreshNow()
            } else {
                scheduleProactiveRefresh()
            }
            nfcReader.enableTagDetection()
            spotifyWebApi.getPlayerState { updateTrackDisplay(it) }
        } else {
            nfcReader.disableTagDetection()
        }
    }

    override fun onPause() {
        stopProgressPolling()
        cancelProactiveRefresh()
        nfcReader.disableTagDetection()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tryHandleSpotifyRedirect(intent)
    }

    private fun handleTagDetected(tagId: String) {
        Log.d(TAG, "handleTagDetected id=$tagId")
        if (tagId != nfcIdState.value) justSavedState.value = false
        nfcIdState.value = tagId
        if (!spotifyAuth.hasToken()) {
            showSignInBannerState.value = true
            val savedUrl = nfcPlaylistRepository.getPlaylistUrl(tagId)
            if (savedUrl != null) playlistUrlState.value = savedUrl
            return
        }
        val savedUrl = nfcPlaylistRepository.getPlaylistUrl(tagId)
        if (savedUrl != null) {
            playlistUrlState.value = savedUrl
            Log.d("HearoPlaylist", "fetching playlist: tagId=$tagId, hasSaved=true, savedUrl=${savedUrl.take(60)}")
            if (spotifyAuth.hasToken()) {
                if (listeningTimeStore.checkAndApplyMidnightReset()) {
                    listeningLimitReachedState.value = false
                }
                refreshListeningTimeDisplay()
                if (listeningLimitReachedState.value) return
                val progress = nfcPlaylistRepository.getProgress(tagId)
                val resumeFromStart = progress == null || progress.contextUri != savedUrl
                val noOffset = isContextWithoutOffset(savedUrl)
                fun doPlay(deviceId: String?, withTransferRetry: Boolean) {
                    val onPlayDone: (Boolean) -> Unit = { success ->
                        if (success) {
                            startProgressPolling(tagId, savedUrl)
                        } else {
                            if (withTransferRetry) {
                                // Play failed (e.g. NO_ACTIVE_DEVICE). Launch Spotify so it can register, then retry.
                                launchSpotifyAndReturnToHearo()
                                progressHandler.postDelayed({
                                    spotifyWebApi.getDevices { retryDevices ->
                                        val targetId = retryDevices.firstOrNull()?.id
                                        if (!targetId.isNullOrBlank()) {
                                            spotifyWebApi.transferPlayback(targetId) {
                                                progressHandler.postDelayed({
                                                    doPlay(targetId, false)
                                                }, AFTER_TRANSFER_DELAY_MS)
                                            }
                                        } else {
                                            doPlay(null, false)
                                        }
                                    }
                                }, SPOTIFY_LAUNCH_RETRY_DELAY_MS)
                            } else {
                                Log.w(TAG, "playContextUri failed for $savedUrl")
                            }
                        }
                    }
                    if (resumeFromStart || noOffset) {
                        spotifyWebApi.playContextUri(savedUrl, deviceId = deviceId, callback = onPlayDone)
                    } else {
                        spotifyWebApi.playContextUri(
                            savedUrl,
                            progress!!.trackUri,
                            progress.progressMs,
                            deviceId = deviceId,
                            callback = onPlayDone
                        )
                    }
                }
                val preferredId = preferredDeviceIdState.value
                if (!preferredId.isNullOrBlank()) {
                    // Always try saved device first: transfer to it then play. Works even when getDevices is empty or device not yet "active".
                    spotifyWebApi.transferPlayback(preferredId) { transferOk ->
                        if (transferOk) {
                            progressHandler.postDelayed({ doPlay(preferredId, true) }, AFTER_TRANSFER_DELAY_MS)
                        } else {
                            // Transfer failed (e.g. Spotify not running, device not found). Launch Spotify and retry.
                            launchSpotifyAndReturnToHearo()
                            progressHandler.postDelayed({
                                spotifyWebApi.transferPlayback(preferredId) {
                                    progressHandler.postDelayed({ doPlay(preferredId, true) }, AFTER_TRANSFER_DELAY_MS)
                                }
                            }, SPOTIFY_LAUNCH_RETRY_DELAY_MS)
                        }
                    }
                } else {
                    spotifyWebApi.getDevices { devices ->
                        val deviceId = devices.firstOrNull { it.isActive }?.id ?: devices.firstOrNull()?.id
                        if (deviceId == null && devices.isEmpty()) {
                            // No devices yet (e.g. Spotify not started). Launch Spotify and retry after it can register.
                            launchSpotifyAndReturnToHearo()
                            progressHandler.postDelayed({
                                spotifyWebApi.getDevices { retryDevices ->
                                    val retryId = retryDevices.firstOrNull { it.isActive }?.id ?: retryDevices.firstOrNull()?.id
                                    doPlay(retryId, true)
                                }
                            }, SPOTIFY_LAUNCH_RETRY_DELAY_MS)
                        } else if (!deviceId.isNullOrBlank()) {
                            spotifyWebApi.transferPlayback(deviceId) { transferOk ->
                                if (transferOk) {
                                    progressHandler.postDelayed({ doPlay(deviceId, true) }, AFTER_TRANSFER_DELAY_MS)
                                } else {
                                    launchSpotifyAndReturnToHearo()
                                    progressHandler.postDelayed({
                                        spotifyWebApi.transferPlayback(deviceId) {
                                            progressHandler.postDelayed({ doPlay(deviceId, true) }, AFTER_TRANSFER_DELAY_MS)
                                        }
                                    }, SPOTIFY_LAUNCH_RETRY_DELAY_MS)
                                }
                            }
                        } else {
                            doPlay(null, true)
                        }
                    }
                }
            }
        } else {
            Log.d("HearoPlaylist", "fetching playlist: tagId=$tagId, hasSaved=false")
            fetchCurrentPlaylistUri { uri ->
                val value = uri ?: PLAYLIST_NO_ITEM
                Log.d("HearoPlaylist", "fetch result: uri=${value.take(80)}")
                playlistUrlState.value = value
            }
        }
    }

    private fun fetchCurrentPlaylistUri(onResult: (String?) -> Unit) {
        if (spotifyAuth.hasToken()) {
            spotifyWebApi.getCurrentContextUri(onResult)
        } else {
            val fallback = spotifyController.getCurrentPlaylistOrContextUri()
            onResult(fallback)
        }
    }

    private fun tryHandleSpotifyRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "hearo" || data.host != "callback") return
        Log.d(TAG, "Received Spotify redirect intent, handling...")
        handlingRedirectState.value = true
        spotifyAuth.handleRedirect(intent) { success ->
            handlingRedirectState.value = false
            if (success) {
                signedInState.value = true
                showSignInBannerState.value = false
                setIntent(Intent())
                nfcReader.enableTagDetection()
            }
        }
    }

    private fun clearTagUi() {
        nfcIdState.value = ""
        playlistUrlState.value = ""
        justSavedState.value = false
        showSignInBannerState.value = false
        artistState.value = "Artist"
        trackTitleState.value = "Title"
        progressMsState.value = 0L
        durationMsState.value = 0L
    }

    private fun handleSpotifyRefreshFailed() {
        spotifyAuth.signOut()
        signedInState.value = false
        cancelProactiveRefresh()
    }

    private fun runProactiveRefreshNow() {
        Thread {
            val token = spotifyAuth.refreshAccessToken()
            progressHandler.post {
                if (token != null) {
                    scheduleProactiveRefresh()
                } else {
                    handleSpotifyRefreshFailed()
                }
            }
        }.start()
    }

    private fun scheduleProactiveRefresh() {
        if (!spotifyAuth.hasToken()) return
        val dueAt = spotifyAuth.getRefreshDueAtMs() ?: return
        val delay = dueAt - System.currentTimeMillis()
        proactiveRefreshRunnable = Runnable { runProactiveRefreshNow() }
        if (delay <= 0) {
            runProactiveRefreshNow()
        } else {
            progressHandler.postDelayed(proactiveRefreshRunnable!!, delay)
        }
    }

    private fun cancelProactiveRefresh() {
        proactiveRefreshRunnable?.let { progressHandler.removeCallbacks(it) }
        proactiveRefreshRunnable = null
    }

    private fun updateTrackDisplay(state: com.example.hearo.PlayerState?) {
        if (state == null) {
            artistState.value = "Artist"
            trackTitleState.value = "Title"
            progressMsState.value = 0L
            durationMsState.value = 0L
        } else {
            artistState.value = state.artistName?.takeIf { it.isNotBlank() } ?: "Artist"
            trackTitleState.value = state.trackName?.takeIf { it.isNotBlank() } ?: "Title"
            progressMsState.value = state.progressMs
            durationMsState.value = state.durationMs
        }
    }

    private fun startProgressPolling(tagId: String, contextUri: String) {
        stopProgressPolling()
        val runnable = object : Runnable {
            override fun run() {
                if (nfcIdState.value != tagId) return
                spotifyWebApi.getPlayerState { state ->
                    if (state != null && state.contextUri == contextUri &&
                        state.trackUri != null && nfcIdState.value == tagId
                    ) {
                        nfcPlaylistRepository.setProgress(
                            tagId,
                            state.contextUri,
                            state.trackUri,
                            state.progressMs
                        )
                        updateTrackDisplay(state)
                        if (state.isPlaying) {
                            if (listeningTimeStore.getLastResetMillis() == 0L) {
                                listeningTimeStore.applyReset()
                            } else if (listeningTimeStore.checkAndApplyMidnightReset()) {
                                listeningLimitReachedState.value = false
                            }
                            listeningTimeStore.addCounterSeconds(1L)
                            listeningCounterState.value = listeningTimeStore.getCounterSeconds()
                            val active = listeningTimeStore.getActiveLimitSeconds()
                            if (listeningTimeStore.getCounterSeconds() >= active) {
                                if (!listeningLimitReachedState.value) {
                                    listeningLimitReachedState.value = true
                                    spotifyWebApi.pause { }
                                    playLimitReachedSound()
                                }
                            }
                        }
                    } else if (nfcIdState.value == tagId) {
                        updateTrackDisplay(state)
                    }
                    if (nfcIdState.value == tagId) {
                        progressHandler.postDelayed(this, PROGRESS_POLL_MS)
                    }
                }
            }
        }
        progressPollRunnable = runnable
        progressHandler.postDelayed(runnable, PROGRESS_POLL_MS)
    }

    private fun stopProgressPolling() {
        progressPollRunnable?.let { progressHandler.removeCallbacks(it) }
        progressPollRunnable = null
    }
}
