# Plan: Use phone as Spotify output device

## Goal
When the user taps an NFC tag and is signed in with Spotify, playback should start on **this phone** instead of on whatever device is currently active on the Spotify account (e.g. desktop, another speaker).

## Current behavior
- **MainActivity** calls `spotifyWebApi.getDevices()` then picks:
  - `devices.firstOrNull { it.isActive }?.id` **or** `devices.firstOrNull()?.id`
- That `deviceId` is passed to `playContextUri(..., deviceId = deviceId)`.
- So playback goes to the **active** device (or first in list). If the user last used Spotify on a laptop or speaker, playback goes there, not the phone.

## Root cause
We use the **Web API** only; device selection is a simple “active or first”. We do not identify “this” device (the phone running Hearo) and do not force transfer to it.

## Approach (Web API only – no SDK)

The Web API already supports:
1. **GET /me/player/devices** – returns devices with `id`, `name`, `type` (e.g. `"Smartphone"`, `"Computer"`, `"Speaker"`), `is_active`.
2. **PUT /v1/me/player/play?device_id=...** – start playback on a **specific** device. Passing `device_id` targets that device (and can effectively transfer there).
3. **PUT /me/player** (transfer) – body `{ "device_ids": ["id"], "play": false }` – explicitly transfer playback to a device. Optional if Play with `device_id` is enough.

So we can achieve “play on phone” by:
- **Identifying the phone** in the device list (heuristic: `type == "Smartphone"`).
- **Passing that device’s ID** to `playContextUri(..., deviceId = phoneDeviceId)`.

No new SDK or native device control is required.

---

## Implementation plan

### Step 1: Prefer “this phone” in device selection (main change)
- **Where:** Logic that chooses `deviceId` from `getDevices()` (currently in **MainActivity** `handleTagDetected`).
- **Change:**
  - From the list returned by `getDevices()`, prefer a device whose `type` is **Smartphone** (case-insensitive).
  - If there are multiple smartphones (e.g. two phones), prefer the one that is **active**; otherwise take the first smartphone.
  - If no smartphone is found, **fallback** to current behavior: active device, then first device.
- **Where to put the logic:** Either a small helper in **MainActivity** or a dedicated function in **SpotifyWebApi** (e.g. `pickPreferredDevice(devices, preferSmartphone = true)`) that returns the chosen `SpotifyDevice` or `deviceId`. Recommendation: add `SpotifyWebApi.pickDeviceForPhone(list)` so device-selection rules live in one place and are easy to test/change.

### Step 2: (Optional) Transfer playback before play when phone is not active
- If we see cases where “Play with device_id = phone” still plays on another device, add an explicit **transfer** step:
  - In **SpotifyWebApi**, add `transferPlayback(deviceId: String, play: Boolean, callback: (Boolean) -> Unit)` calling **PUT /me/player** with `{"device_ids": [deviceId], "play": false}`.
  - In **MainActivity**, when we have chosen the phone as target and it is **not** active: call `transferPlayback(phoneDeviceId, play = false)` and on success call `playContextUri(..., deviceId = phoneDeviceId)`. This makes the phone the active device first, then starts playback.
- Try Step 1 first; add Step 2 only if needed.

### Step 3: Empty device list (phone not in list)
- Devices appear in the list only when the Spotify client has been used (or is running) on that device. If the user opens Hearo without having opened Spotify on the phone, the phone might not be in the list.
- **Current:** `ensureSpotifyOnce()` already launches Spotify when the app is opened.
- **Optional improvement:** When we need to play (e.g. after tag scan) and `getDevices()` returns an **empty** list, optionally:
  - Ensure Spotify is in foreground/launched (if not already),
  - Wait ~1–2 seconds,
  - Call `getDevices()` again and then run the same “prefer phone then play” logic.
- This can be a follow-up if users report “playback never starts on phone” when they hadn’t opened Spotify first.

### Step 4: No code change to auth or scopes
- Existing scope `user-modify-playback-state` is enough for Play with `device_id` and for Transfer Playback. No scope change.

---

## Summary (for you)

| What we do | How |
|------------|-----|
| **Detect “phone”** | Use Web API device list; treat device with `type == "Smartphone"` as the phone (heuristic; we can’t get a true “this device” id from the Web API). |
| **Select it** | When choosing `deviceId` for `playContextUri`, prefer Smartphone over active/first. Fallback to current behavior if no smartphone. |
| **Play on it** | Keep using `playContextUri(..., deviceId = phoneDeviceId)`. No new APIs required; optional transfer step only if needed. |
| **Edge cases** | Empty list: keep launching Spotify via `ensureSpotifyOnce`; optionally retry `getDevices` after a short delay. Multiple phones: prefer active smartphone, else first. |

This stays within the **Web API** and does not require the Spotify Android SDK or any “device control” beyond what we already use. Implementation is a small change in device-picking logic plus an optional transfer call if the first approach is not enough.
