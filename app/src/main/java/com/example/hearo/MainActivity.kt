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
private const val PROGRESS_POLL_MS = 1000L
private const val SPOTIFY_CHECK_DELAY_MS = 500L

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile
        private var spotifyCheckedOnce = false
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
    private val currentTrackState = mutableStateOf("—")
    private val signedInState = mutableStateOf(false)
    private val nfcReadyState = mutableStateOf(true)

    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressPollRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        signedInState.value = spotifyAuth.hasToken()
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(Modifier.padding(innerPadding)) {
                        HearoScreen(
                                trackTitle = currentTrackState.value,
                                nfcId = nfcIdState.value,
                                playlistUrl = playlistUrlState.value,
                                signedIn = signedInState.value,
                                nfcReady = nfcReadyState.value,
                                onSignInClick = { spotifyAuth.signIn(BuildConfig.SPOTIFY_CLIENT_ID) },
                                onSignOutClick = {
                                    spotifyAuth.signOut()
                                    signedInState.value = false
                                },
                                playlistFieldEnabled = nfcIdState.value.isNotEmpty() && nfcPlaylistRepository.getPlaylistUrl(nfcIdState.value) == null,
                                showSaveButton = nfcIdState.value.isNotEmpty() && nfcPlaylistRepository.getPlaylistUrl(nfcIdState.value) == null,
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
                                    }
                                },
                                onDetachClick = {
                                    val id = nfcIdState.value
                                    if (id.isNotEmpty()) {
                                        nfcPlaylistRepository.remove(id)
                                        fetchCurrentPlaylistUri { uri ->
                                            playlistUrlState.value = uri ?: PLAYLIST_NO_ITEM
                                        }
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
            }
        }
        intent?.let { tryHandleSpotifyRedirect(it) }
        ensureSpotifyOnce()
    }

    private fun ensureSpotifyOnce() {
        progressHandler.postDelayed({
            if (spotifyCheckedOnce) return@postDelayed
            spotifyCheckedOnce = true
            if (!spotifyController.isSpotifyRunning()) {
                spotifyController.launchSpotify()
                progressHandler.postDelayed({
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                }, 200)
            }
        }, SPOTIFY_CHECK_DELAY_MS)
    }

    override fun onResume() {
        super.onResume()
        signedInState.value = spotifyAuth.hasToken()
        nfcReader.enableTagDetection()
    }

    override fun onPause() {
        stopProgressPolling()
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
        nfcIdState.value = tagId
        val savedUrl = nfcPlaylistRepository.getPlaylistUrl(tagId)
        if (savedUrl != null) {
            playlistUrlState.value = savedUrl
            Log.d("HearoPlaylist", "fetching playlist: tagId=$tagId, hasSaved=true, savedUrl=${savedUrl.take(60)}")
            if (spotifyAuth.hasToken()) {
                val progress = nfcPlaylistRepository.getProgress(tagId)
                val resumeFromStart = progress == null || progress.contextUri != savedUrl
                spotifyWebApi.getDevices { devices ->
                    val deviceId = devices.firstOrNull { it.isActive }?.id ?: devices.firstOrNull()?.id
                    val playAction = {
                        if (resumeFromStart) {
                            spotifyWebApi.playContextUri(savedUrl, deviceId = deviceId) { success ->
                                if (!success) Log.w(TAG, "playContextUri failed for $savedUrl")
                                else startProgressPolling(tagId, savedUrl)
                            }
                        } else {
                            spotifyWebApi.playContextUri(
                                savedUrl,
                                progress.trackUri,
                                progress.progressMs,
                                deviceId = deviceId
                            ) { success ->
                                if (!success) Log.w(TAG, "playContextUri (resume) failed for $savedUrl")
                                else startProgressPolling(tagId, savedUrl)
                            }
                        }
                    }
                    playAction()
                }
            } else {
                spotifyController.playUri(savedUrl)
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
        spotifyAuth.handleRedirect(intent) { success ->
            if (success) {
                signedInState.value = true
                setIntent(Intent())
            }
        }
    }

    private fun clearTagUi() {
        nfcIdState.value = ""
        playlistUrlState.value = ""
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
