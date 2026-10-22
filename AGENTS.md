# Codex implementation / validation instructions

## Goal
Build this **independent open-source** Android Kotlin POC on Ubuntu, deploy to **Samsung Z Fold3**, and demonstrate whether authorized SIWC ChatGPT subscription inference works in reality. Do not merge anything into DeX-Assistant or DeX-Kids-Adventure.

## Commands

1. `git status --short && java -version && adb devices -l`
2. Install/find SDK 35; set `ANDROID_HOME`, ensure `sdkmanager` and `platform-tools` are ready.
3. `./gradlew :app:assembleDebug --stacktrace`.
4. Fix actual Kotlin/Gradle compile errors (do not fake PASS).
5. `./scripts/install-zfold3.sh` or `adb -s DEVICE install -r app/build/outputs/apk/debug/app-debug.apk`.
6. Open app on Fold3 (in DeX as needed). Let parent/user personally approve OAuth in Chrome. Never automate, intercept, or request ChatGPT credentials.
7. Test model retrieval, `response.completed`, browser callback, refresh and logout.

## Security and release gates

- Public open-source repo with MIT license. No API keys, access/refresh/id tokens, browser callback `code`, Android Keystore material, `.auth/`, or private logs in source/issues/PRs.
- Never use `chatgpt.com/backend-api`, scrape session cookies or copy Codex `auth.json`.
- Preserve `state`, `nonce`, PKCE, issued `client_id`, exact loopback callback URI, signed ID-token verification and subscription scope checking.
- The Android browser may reject loopback. Document this with **redacted** evidence. Do not silently switch to an unsupported redirect type.
- API call must always use `store=false`, `stream=true`, supported `input`, account-provided model slug.
- Confirm sign-in works and response emits `response.completed` **on real device** before calling POC successful.

## Deliverables

- Commit any compile/runtime fixes to `main` after reproducible build.
- Record Android version, build command, git SHA, model slug, API response status and PASS/FAIL **without exposing tokens** in `docs/DEVICE_TEST_REPORT.md`.
- If tests fail, report exact failing phase (browser / callback / ID token / scope / model list / SSE / refresh) and safe reproduction steps.
