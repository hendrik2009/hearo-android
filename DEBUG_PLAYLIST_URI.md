# Debugging "playlist URL not retrieved"

Use this to find why the playlist URI field is empty (e.g. after restart or when presenting a tag).

## 1. Add logging in your code

Add `Log.d("HearoPlaylist", ...)` in these three places so we can see the full flow.

### A. When you decide to fetch the URL

**Where:** In `MainActivity`, in the code that runs when an NFC tag is detected (e.g. `handleTagDetected` or wherever you call `fetchCurrentPlaylistUri`).

**Add:**
```kotlin
import android.util.Log
// ...
// When you're about to fetch (e.g. after setting nfcIdState):
Log.d("HearoPlaylist", "fetching playlist: tagId=$nfcId, hasSaved=${savedUrl != null}, savedUrl=$savedUrl")
fetchCurrentPlaylistUri { uri ->
    Log.d("HearoPlaylist", "fetch result: uri=${uri.take(80)}")
    playlistUrlState.value = uri ?: PLAYLIST_NO_ITEM
}
```

This confirms (1) we actually run the fetch for this tag, and (2) what value we get back.

### B. Inside the Web API call (GET /v1/me/player)

**Where:** In `SpotifyWebApi` (or wherever you call `GET https://api.spotify.com/v1/me/player` and read `context.uri`).

**Add:**
```kotlin
import android.util.Log
// ...
// Before the request:
Log.d("HearoPlaylist", "Web API: GET /me/player, token present=${token != null}")
// After you get the response (success or not):
Log.d("HearoPlaylist", "Web API: code=${response.code}, body=${response.body?.string()?.take(200)}")
// When you parse context.uri:
Log.d("HearoPlaylist", "Web API: context.uri=$contextUri")
```

This shows whether the token is used, what Spotify returns (e.g. 200 with context, 204 no content, 401 unauthorized), and the parsed URI.

### C. Fallback path (MediaSession / no Web API)

**Where:** If you have a fallback when not signed in (e.g. `SpotifyController.getCurrentPlaylistOrContextUri()`).

**Add:**
```kotlin
Log.d("HearoPlaylist", "fallback getCurrentPlaylistOrContextUri: $result")
```

So we can see when we use the fallback and what it returns.

---

## 2. Capture logcat

1. In Android Studio: **Logcat** tab → filter by `HearoPlaylist`.
2. Clear log, then: present an NFC tag (or restart and present tag).
3. Copy the new `HearoPlaylist` lines.

## 3. How to interpret

| What you see | Likely cause |
|--------------|----------------|
| No "fetching playlist" log | Fetch never triggered (tag handler not run or condition not met). |
| "fetching playlist" but no "fetch result" | Callback never called (crash, or fetch not invoked). |
| "fetch result: uri=null" or empty | Web API returned no context (204, or no `context.uri`). |
| "Web API: code=401" | Token expired or invalid; refresh token or re-sign-in. |
| "Web API: code=204" | No active player; start playback on a device first. |
| "fetch result: uri=spotify:..." but UI still empty | State not updating UI (e.g. wrong thread or state not used in Compose). |

---

## 4. Quick checklist

- [ ] INTERNET permission in manifest (needed for Web API).
- [ ] Token present: `spotifyAuth.hasToken()` true when signed in.
- [ ] Spotify playback active on some device (otherwise `/me/player` often returns 204).
- [ ] On tag detection you call `fetchCurrentPlaylistUri` and set `playlistUrlState` in the callback (same on cold start and when app was already open).

Once you have the `HearoPlaylist` log lines from a run where the URL is missing, we can pinpoint the step that fails.
