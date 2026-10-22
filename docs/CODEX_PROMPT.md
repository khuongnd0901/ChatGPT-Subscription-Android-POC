# Prompt for Codex (copy into the Codex terminal in this extracted project directory)

You are the Main Agent for the project `ChatGPT-Subscription-Android-POC`.

Goal: build the already-published independent open-source Android POC and deploy a real debug APK to my Samsung Z Fold3 from Ubuntu. Clone https://github.com/khuongnd0901/ChatGPT-Subscription-Android-POC and work from its root, which contains `AGENTS.md`.

Tasks, in order:
1. Read `README.md`, `AGENTS.md`, and official documentation linked in README. Verify the preview requirements; do not use ChatGPT's internal APIs or my Codex auth files.
2. Confirm `git remote -v` points only to `khuongnd0901/ChatGPT-Subscription-Android-POC` and the checked-out source is on `main`. **The GitHub repo already exists; do not run `./scripts/publish-gh.sh` or create it again.** Verify GitHub authentication before committing/pushing fixes. Do not write to the DeX repositories.
3. On Ubuntu, check Java 17/21, Gradle, ANDROID_HOME and Android SDK Platform 35. Build `./gradlew :app:assembleDebug --stacktrace`. If build fails, fix source/Gradle issues and rerun until a real APK is produced; commit and push fixes. Never claim PASS from static code inspection alone.
4. Check `adb devices -l`. My Fold3 is usually reachable through Wi-Fi ADB; if present run `./scripts/install-zfold3.sh` with ADB_SERIAL if necessary. Start `dev.kun.siwcpoc/.MainActivity`. If the device is not connected, tell me the exact adb connect/authorization command needed; don't assume install passed.
5. I will personally tap `Continue with ChatGPT` in the Z Fold3 Chrome browser and approve access. Observe only sanitized application status. Do not scrape cookies, reveal callback codes or tokens, or bypass user consent.
6. Test full flow: loopback callback; ID token verification + granted plan scope; account model catalog; streamed Responses API result ending `response.completed`; app relaunch; refresh path if expiry; sign out.
7. Save an accurate report to `docs/DEVICE_TEST_REPORT.md`: commit SHA, build result, device/Android info, pass/fail by phase and sanitized error codes. Commit/push changes. If a test cannot be executed, explicitly mark NOT TESTED and explain blocker.

Security: No secrets or user PII in logs, PRs, commits or screenshots. Reject invalid OAuth state, bad token signatures and denied subscription scope. No fallback to paid API key. Do not silently modify the OAuth redirect URI scheme or hardcode developer credentials. This POC must not be merged into DeX-Assistant nor DeX-Kids-Adventure until separately approved.
