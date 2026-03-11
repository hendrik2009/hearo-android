package com.example.hearo

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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

    private val nfcIdState = mutableStateOf("")
    private val playlistUrlState = mutableStateOf("")
    private val artistState = mutableStateOf("—")
    private val trackTitleState = mutableStateOf("—")
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
                                }
                            )
                        }
                    }
                    handlingRedirectState.value -> RedirectHandlingScreen()
                    else -> InitialScreen(onSignInClick = { spotifyAuth.signIn(BuildConfig.SPOTIFY_CLIENT_ID) })
                }
            }
        }
        intent?.let { tryHandleSpotifyRedirect(it) }
    }

    private fun ensureSpotifyOnce() {
        progressHandler.postDelayed({
            if (spotifyCheckedOnce) return@postDelayed
            spotifyCheckedOnce = true
            if (!spotifyController.isSpotifyRunning()) {
                spotifyController.launchSpotify()
            }
        }, SPOTIFY_CHECK_DELAY_MS)
    }

    override fun onResume() {
        super.onResume()
        signedInState.value = spotifyAuth.hasToken()
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
                val progress = nfcPlaylistRepository.getProgress(tagId)
                val resumeFromStart = progress == null || progress.contextUri != savedUrl
                val noOffset = isContextWithoutOffset(savedUrl)
                fun doPlay(deviceId: String?, withTransferRetry: Boolean) {
                    val onPlayDone: (Boolean) -> Unit = { success ->
                        if (success) {
                            startProgressPolling(tagId, savedUrl)
                        } else {
                            if (withTransferRetry) {
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
                                }, PLAY_FAIL_RETRY_DELAY_MS)
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
                spotifyWebApi.getDevices { devices ->
                    val deviceId = devices.firstOrNull { it.isActive }?.id ?: devices.firstOrNull()?.id
                    if (deviceId == null && devices.isEmpty()) {
                        progressHandler.postDelayed({
                            spotifyWebApi.getDevices { retryDevices ->
                                val retryId = retryDevices.firstOrNull { it.isActive }?.id ?: retryDevices.firstOrNull()?.id
                                doPlay(retryId, true)
                            }
                        }, NO_DEVICE_RETRY_DELAY_MS)
                    } else {
                        doPlay(deviceId, true)
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
        artistState.value = "—"
        trackTitleState.value = "—"
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
            artistState.value = "—"
            trackTitleState.value = "—"
            progressMsState.value = 0L
            durationMsState.value = 0L
        } else {
            artistState.value = state.artistName?.takeIf { it.isNotBlank() } ?: "—"
            trackTitleState.value = state.trackName?.takeIf { it.isNotBlank() } ?: "—"
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
