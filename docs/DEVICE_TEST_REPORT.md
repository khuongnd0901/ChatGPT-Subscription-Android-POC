# Samsung Z Fold3 Device Verification

**Result: PASS for authorized SIWC subscription inference on this real device/account.**

- Test date: 2026-10-22, approximately 21:27–21:40 (Asia/Ho_Chi_Minh).
- Tested application source commit: `baa636da42d86267bd0a6e3bf2181fa3432dbddc` on `main` (runtime fixes plus regression tests).
- Initial source: `5c67aa34b261dc47fdf2eee15fb5319df9a2998f`.
- Device: Samsung Galaxy Z Fold3, SM-F926B; Android 15 / API 35; One UI 7 (`70000`).
- Android build: `AP3A.240905.015.A2.F926BXXSHJZBB`.
- DeX: enabled, dual-display mode; desktop display 2 at 1920×1080, external HDMI monitor.
- Chrome: `155.0.8059.79`, on the same Fold3.
- Toolchain: Temurin JDK 17.0.20.1; SDK platform 35; build-tools 35.0.0 available; AGP 8.7.3; Kotlin 1.9.25; Gradle 8.9.
- Package: `dev.kun.siwcpoc`, debug version `0.1.0`.

## Results

| Phase | Result | Observed evidence |
| --- | --- | --- |
| Build | PASS | Initial assemble succeeded in 2m16s; repeat with all tasks rerun succeeded in 12s. Fixed source assembled successfully in 10s. Final full rebuild plus unit tests succeeded in 19s. |
| Regression tests | PASS | 3 tests, 0 failures/errors: fragmented UTF-8 body through EOF, empty body, oversized body rejected without echoing content. |
| ADB installation / DeX launch | PASS | `adb install -r` returned Success; launch on display 2 returned Status ok / COLD. |
| Browser / loopback callback | PASS | User personally completed Chrome authentication/consent; Chrome displayed the loopback callback received page. Callback scheme, host and path remained HTTP / 127.0.0.1 / auth/callback with an ephemeral port. |
| Token exchange / ID token | PASS | App reached Authenticated only after real token exchange and existing RS256 signature, issuer, issued-client audience, nonce, lifetime and subject checks. No verifier bypass was introduced. |
| Subscription scope | PASS | App displayed Authenticated. Subscription scope granted after checking returned token scopes for `chatgpt.tokens.use.direct`. |
| Model list | PASS | `GET https://api.openai.com/v1/models`: HTTP 200, 7 visible models. [Screenshot](evidence/models-pass.png). |
| Initial inference | PASS | `gpt-6-astra`: HTTP 200, `response.completed`. [Screenshot](evidence/inference-pass.png). |
| Forced token refresh | PASS | Refresh button completed a real refresh grant; subscription scope retained. [Screenshot](evidence/refresh-pass.png). Secret values were not inspected to compare rotation. |
| Persistence / refreshed inference | PASS | After force-stop/relaunch, model retrieval again returned HTTP 200 with 7 models. `gpt-6.1-sol` then returned HTTP 200 and `response.completed`. [Screenshot](evidence/inference-after-refresh-restart-pass.png). |
| Local logout | PASS | Signed out (host ID retained); model picker and output cleared. [Screenshot](evidence/logout-pass.png). |
| Logout persistence | PASS | Force-stop/relaunch followed by Fetch models returned Not signed in, as expected. [Screenshot](evidence/logout-restart-pass.png). |

Prompt: `Hãy trả lời chính xác một câu: Xin chào từ ChatGPT subscription!`

Observed output for both models: `Xin chào từ ChatGPT subscription!`

Requests used OAuth from this app's own browser registration, account-provided model slugs, supported user-message input, `store=false` and `stream=true`, through the public Responses endpoint. No separate API billing key was configured for this POC. No ChatGPT backend-api, session cookies, copied Codex credentials, desktop callback relay or alternate redirect was used.

## Runtime failure found and fixed

The original build compiled but failed during **token-response JSON parsing**, after the browser callback succeeded and before ID-token validation. Its HTTP helper called `Reader.read()` only once; network reads may return fewer characters than the complete response. The on-device failure was an unterminated JSON string, and the JSON exception included a sensitive response fragment in the status UI. Raw failure screenshots and response content are excluded from this repository/report.

Fix: read through EOF with a 1 Mi-character bound; hide JSON exception bodies in both the HTTP helper and UI; display only constrained provider error codes instead of provider messages. Added regression coverage with a stream returning seven bytes per read and a body larger than the original buffer. A fresh browser authorization after installing the fix passed all positive phases above.

Safe reproduction of the original defect: run the fragmented-body regression test against the initial HTTP reader using synthetic text only. Do not reproduce using real token responses or retain raw JSON exception text.

## Reproduce build and device test

```bash
export JAVA_HOME=/home/kunrg/.local/opt/jdk-17.0.20.1+1
export ANDROID_HOME=/home/kunrg/Android/Sdk
export GRADLE_BIN=/home/kunrg/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
./gradlew :app:assembleDebug --stacktrace
./gradlew :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --stacktrace
adb -s DEVICE install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE shell am start --display 2 -W -n dev.kun.siwcpoc/.MainActivity
```

Use the local JDK/SDK/Gradle paths on other machines and replace DEVICE with the current ADB serial. DeX display IDs can change. In the app, personally authorize Continue with ChatGPT in Chrome; return to the app, fetch models, select an offered model and run inference. Expect HTTP 200 and `response.completed`. Exercise Refresh session, force-stop/reopen, fetch models and run again. Sign out, reopen, and verify fetching models refuses an absent session.

## Limits

This establishes a successful real-device POC for the consenting account at test time. It does not establish availability for every account, model or Android browser. Consent denial, revoked permission, expiry-driven automatic refresh, offline behavior, adversarial OIDC/callback cases and multi-account behavior were not device-tested. Refresh was explicitly forced without altering expiry or reading credentials. Logout here clears local credentials; server-side session revocation and retained account/client mapping are not implemented and remain follow-up work. The current official accounts/session guidance describes those additional behaviors.

Gradle emitted an SDK XML-version warning on the initial build; assembly still passed. The app was left installed and locally signed out after the logout test.

Official references checked: [registration and sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in), [models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference), [accounts and sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions).

No OAuth codes, token values, account email, Keystore material or private logs are included in this report or its committed evidence.
