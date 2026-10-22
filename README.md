# ChatGPT Subscription Android POC

Open-source, **Android-native** proof of concept for using **Sign in with ChatGPT plan usage** instead of a separate paid OpenAI API key.

**Status:** Verified on a real Samsung Z Fold3 running Android 15 / One UI 7 in DeX on 2026-10-22. Browser OAuth, signed ID-token validation, subscription scope, model retrieval, HTTP 200 inference ending in `response.completed`, forced refresh, session persistence, and local sign-out passed. See [device test report](docs/DEVICE_TEST_REPORT.md) for evidence and limits.

## What is implemented

- New OAuth **dynamic registration**: `client_id=dynamic_agent_client`, issued `oaiapp_...` received on redirect.
- `ext_agent_host_id`: stable random UUID `urn:uuid:...`, persisted per install.
- Browser-based Authorization Code + **PKCE S256**, random `state` and OIDC `nonce`.
- **Real Android loopback HTTP listener** on `127.0.0.1:<ephemeral>/auth/callback` (no WebView, reverse proxy, desktop or backend).
- POST code exchange to `https://auth.openai.com/api/accounts/oauth/token`, no client secret.
- **RS256 ID token verification** against OpenAI's JWKS, validating issuer, audience, signature, nonce, expiry, subject, `iat`.
- Check granted scope `chatgpt.tokens.use.direct` before inference; sign-in alone does not grant plan access.
- Encrypted credentials using **Android Keystore AES-GCM**, no backup, no secret in sources/logs.
- Refresh-token rotation support for short-lived OAuth access tokens.
- `GET /v1/models` account-specific list; respects `models[].visibility == "list"`.
- `POST /v1/responses` with bearer OAuth access token, `store=false`, `stream=true`; parses SSE delta and strictly requires `response.completed` to PASS.
- UI shows sign-in, model picker, input, streaming output, PASS/FAIL, sign-out.

## Build requirements

- JDK 17 (JDK 21 should also work with Gradle 8.9 + AGP 8.7.3)
- Android SDK Platform **35**, build-tools installed, Android SDK configured with `ANDROID_HOME` or `local.properties`.
- Linux/macOS: `bash`, `curl`, `unzip`; Internet to retrieve Gradle / Android Gradle Plugin artifacts for the first build.
- Samsung Z Fold3 with Android USB/Wi-Fi debugging & ADB from Ubuntu (or an Android Emulator with Chrome).

```bash
git clone https://github.com/khuongnd0901/ChatGPT-Subscription-Android-POC.git
cd ChatGPT-Subscription-Android-POC
export ANDROID_HOME="$HOME/Android/Sdk"  # change if necessary
./gradlew :app:assembleDebug
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.kun.siwcpoc/.MainActivity
```

`./gradlew` here is a small auditable Gradle 8.9 HTTPS bootstrap shell script, **not** a stock Gradle-wrapper JAR. This is intentional because binary distribution tools were unavailable in the authoring environment. To replace it with canonical Gradle wrapper locally, run `gradle wrapper --gradle-version 8.9` and commit the resulting jar/scripts.

For a combined build + deployment on Ubuntu:

```bash
./scripts/install-zfold3.sh
# Wi-Fi ADB with multiple devices:
ADB_SERIAL=192.168.1.10:5555 ./scripts/install-zfold3.sh
```

## Repository and Codex workflow

This repository is **already published** at https://github.com/khuongnd0901/ChatGPT-Subscription-Android-POC. Clone it and run Codex from its root; see `AGENTS.md` and `docs/CODEX_PROMPT.md`.

`./scripts/publish-gh.sh` is a historical create-repository helper and is **not needed** for this repo. To push verified fixes, use `git add`, `git commit`, and `git push` with your authenticated GitHub account. Do not modify existing DeX repositories.

## Device acceptance test

1. Launch app. Expect `Not signed in`.
2. Tap **Continue with ChatGPT**. A system browser opens. Grant requested scopes. Verify browser lands on `127.0.0.1` success page. Return to the POC app.
3. Expect `Authenticated. Subscription scope granted`. This only proves OAuth and scope consent.
4. Tap **Fetch models**; verify list is populated for current ChatGPT account.
5. Choose a model, type a Vietnamese prompt, tap **Run inference**. Observe streamed output and **`PASS: response.completed`**.
6. Close and reopen app: encrypted session should survive. Try Fetch models again (refresh may run).
7. Tap **Refresh session (test token rotation)** to exercise refresh immediately; fetch models and run inference again. The test does not inspect secret values to compare rotated tokens.
8. Tap Sign out: credentials cleared, model picker cleared.
9. Test declined consent, offline/network error, invalid scope, expiry/refresh and browser timeout.

**PASS criteria:** authorized ChatGPT plan, model list available, stream finishes via `response.completed`, with **no API key** and no API billing key configured. Failed or missing scope is **FAIL**, not a reason to bypass authorization.

## Known technical constraints / risks

- ChatGPT plan usage is a preview and conditional on authorization and **open-source/local-app** eligibility. A paid/hosted or private proprietary app does not inherit authorization by importing this code.
- On some Android/browser configurations, the system browser may not reach `127.0.0.1` loopback, or Android network policies can interfere. Capture evidence and treat as FAIL pending remediation. Keep callback HTTP loopback as officially documented; do not secretly swap to `chatgpt.com/backend-api`.
- One selected account per POC install. Multi-account/profile management and revocation endpoint are out of scope. Sign out clears **local credentials**, not server-side grants.
- Only RS256 supported for ID token signature in current POC. If OpenAI changes signing algorithm, explicitly update verifier against live OIDC discovery; do **not** skip signature checks.
- Token refresh was device-tested with a real issued dynamic client, followed by successful model retrieval and inference after an app restart. If refresh returns an ID token, this POC deliberately retains the previously validated account hint instead of replacing it without verification.
- Unsupported plan-use features (at time of writing): audio/video input, Files upload, image generation, hosted tools like Code Interpreter, `previous_response_id` HTTP continuation. For DeX voice apps, STT/TTS must be separate.
- Preview protocol may change; use the linked official docs before merging into other apps.
- This is a research POC, not a production-ready reusable authentication SDK.

## Official sources

- https://developers.openai.com/siwc/token-sharing-open-source
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference
- https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations

## License
MIT; see [LICENSE](LICENSE).
