# Implementation summary: Preferred Spotify device selection

**Check-in summary before implementation.** Please confirm or adjust so we can implement.

---

## 1. Stored preferred device (Option 2)

- **Persist a preferred Spotify device ID** (and optionally display name) in app storage (e.g. SharedPreferences, same style as `NfcPlaylistRepository` or a small `SpotifyPreferences`).
- **UI:** A way for the user to **select** the playback device:
  - Call `GET /me/player/devices`, show a list (e.g. name + type for each device).
  - User taps one → we save that device’s `id` (and optionally `name` for showing “Playing on: …” later).
- **Playback flow:** When starting playback (e.g. after NFC tap):
  - Call `getDevices()`.
  - If we have a **stored preferred device ID** and it appears in the list → use it: **transfer playback** to that device, then **start play** (context URI) on it.
  - If preferred device is not in the list (e.g. device was off / not running Spotify) → either show a short error like “Preferred device unavailable” and do not start playback, or fall back to default Spotify behaviour (play on active device); your choice.
  - If no preferred device is stored → fall back to current behaviour (e.g. active device or first device).

So: **store the correct device, then always aim playback at that device** (including transfer from standby if needed).

---

## 2. Transfer playback (Option 2)

- Add **Transfer Playback** in `SpotifyWebApi`: e.g. `transferPlayback(deviceId: String, play: Boolean, callback: (Boolean) -> Unit)` calling **PUT** `https://api.spotify.com/v1/me/player` with body `{ "device_ids": [deviceId], "play": false }`.
- When we have a preferred device and it’s in the list: **first** call `transferPlayback(preferredDeviceId, play = false)`, **then** call `playContextUri(..., deviceId = preferredDeviceId)`. This makes the chosen device active (and can wake it from standby), then starts playback there.

---

## 3. Empty device list

- When we need to play and `getDevices()` returns an **empty** list:
  - **Either:** Show an **error message** (e.g. “No Spotify devices available. Open Spotify on this phone or another device and try again.”) and do not start playback.
  - **Or:** Fall back to **default Spotify behaviour** (call play without `device_id`, so Spotify uses whatever is active).
- No retry/delay loop: keep it simple.

---

## 4. Scopes and APIs

- **Current scopes:** `user-read-playback-state user-modify-playback-state`.
  - **user-read-playback-state:** needed for `GET /me/player` and **GET /me/player/devices** (device list).
  - **user-modify-playback-state:** needed for **PUT /me/player** (transfer), **PUT /me/player/play** (start playback), pause, skip, seek.
- **Conclusion:** No scope change for this feature. If you want more control later (e.g. richer device or playback info), we could revisit; for “preferred device + transfer + play” the current scopes are enough.

---

## 5. How we can “ensure” it’s the current device

The Spotify **Web API does not tell us which device the app is running on**. There is no “current device” or “this device” ID returned to the client. So we cannot programmatically “ensure” in the sense of “the API guarantees this is the phone.”

We can only:

1. **Let the user choose and store the device**  
   User opens “Playback device” (or similar), sees the list from `getDevices()`, and selects the device they want (e.g. this phone). We save that ID. From then on we **treat** that as “the correct device” and always target it (transfer + play). So we “ensure” it in the sense of **user-confirmed preference**.

2. **Help the user pick “this” device**  
   To make it obvious which entry is likely this phone:
   - **Type:** Prefer or mark devices with `type == "Smartphone"` (Spotify’s type for phones).
   - **Name (optional):** Compare Spotify device `name` with something we can get on Android, e.g.:
     - Bluetooth name: `BluetoothAdapter.getDefaultAdapter()?.name` (often matches “Phone name” in settings; needs `BLUETOOTH` permission),
     - or `Build.MODEL` / `Build.BRAND` (e.g. “Pixel 7”), or `Settings.Global.getString(contentResolver, DEVICE_NAME)` where available.
   - In the device picker we can show one item as **“Likely this device”** when it’s type Smartphone and (if we implement it) name matches. User can confirm and save.

So in practice: **“ensure it’s the current device” = user selects that device from the list and we store it; we can improve confidence by suggesting “Likely this device” via type + optional name match.**

---

## 6. Concrete implementation steps (for your OK)

| Step | What |
|------|------|
| **A** | Add persistence for preferred device: e.g. `preferred_spotify_device_id: String?` and optionally `preferred_spotify_device_name: String?` (SharedPreferences or small repo). |
| **B** | In `SpotifyWebApi`: add `transferPlayback(deviceId, play, callback)`. |
| **C** | In `MainActivity` (or wherever playback is triggered): when starting playback with Web API, if we have a stored preferred device ID, get devices → if that ID is in the list, call `transferPlayback(preferredId, false)` then `playContextUri(..., deviceId = preferredId)`; if preferred not in list, either show error or fall back to current behaviour; if no preferred stored, keep current logic (active/first). |
| **D** | Device picker UI: new screen or bottom sheet that calls `getDevices()`, shows list (name, type), allows selecting one and saving to preferences. Optionally mark “Likely this device” (Smartphone + name match). Add an entry point (e.g. “Playback device” in HearoScreen or settings). |
| **E** | Empty device list: when we need devices and list is empty, show error message or fall back to default Spotify behaviour (no retry). |
| **F** | No scope or auth changes. |

---

## 7. Check-in

Please confirm:

1. **Stored preferred device + transfer then play** as above is what you want (Option 2).
2. **Empty list:** Prefer **error message** or **fall back to default Spotify behaviour**?
3. **“Likely this device”** in the picker: do you want it (type Smartphone + optional name match), or is a plain list enough for the first version?
4. **Where** the device picker should live: e.g. a button/setting on the main Hearo screen, or a separate “Settings” screen.

Once you confirm, implementation can follow this summary.
