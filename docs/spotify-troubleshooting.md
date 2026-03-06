# Spotify troubleshooting

## 403: "The user is not registered for this application"

If the Web API returns **403** with body *"The user is not registered for this application. Please check your settings on https://developer.spotify.com/dashboard"*, your app is in **Development** mode and the Spotify account you signed in with is not in the allowed users list.

**Fix:**

1. Open the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) and sign in.
2. Open your app (the one whose **Client ID** is used in Hearo).
3. Click **Settings** (gear icon).
4. Under **User Management**, click **Add User** and enter the **Spotify email or username** of the account you use in Hearo (the one you chose in "Sign in with Spotify").
5. Save. The user can take a few minutes to activate; then sign out and sign in again in Hearo if needed.

In **Development** mode, only users you add here can use the app. For unrestricted use (any Spotify account), you must request **Extended Quota Mode** / move the app out of Development (see Dashboard and Spotify’s current policy).

---

## Playback stopped working after changing Spotify account

If you switched to a different Spotify account on your phone (e.g. logged out of the Spotify app and into another account), Hearo may still be using **tokens for the old account**. Play and other controls then fail or behave for the wrong account.

**Fix:**

1. In Hearo, tap **Sign out**.
2. Tap **Sign in with Spotify** and complete the flow with the **new** account.

If you then get **403 "user is not registered"**, add the **new** account in the Dashboard as above (User Management → Add User).
