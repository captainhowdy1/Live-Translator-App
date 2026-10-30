# Puente — Live Voice Translator

> **Android app (APK):** the native app lives in `android/`. See [`android/BUILD_GUIDE.md`](android/BUILD_GUIDE.md) to build or download the APK. GitHub builds it automatically via `.github/workflows/build-apk.yml`. It supports 15 languages, with English ↔ Spanish as the default, and translates on-device for free.

## Purpose

Puente is a live, turn-by-turn voice interpreter between English and Spanish. It listens to speech, translates it, and speaks the translation aloud — then waits for the other person to reply and does the same in reverse, for as long as the session runs.

It is a **live-only** service: nothing is recorded, saved, or accessible after the session ends.

## Scope

- **Phone call mode** — put your call on speakerphone; the app opens with a short Spanish announcement telling the caller a live translation agent is active, then interprets both directions of the conversation.
- **In person mode** — lay the phone between two people; the app announces itself in both languages and interprets between them.
- Auto ping-pongs between languages (after speaking Spanish it listens for Spanish, and vice versa), with manual English/Español override buttons.
- A sentence is treated as finished after a short silence (adjustable), or immediately via the **Translate now** button.
- Live English-only transcript on screen (both sides shown in English), plus **Pause/Resume** and **Replay last translation** controls.

**Limitations:** no app (web or native) can tap directly into the phone-call audio stream — Android blocks this for privacy — so speakerphone is required. Speech recognition requires Chrome (or another Chromium browser) and an internet connection.

## Use

1. Open the app in Chrome and allow microphone access when prompted.
2. Pick a mode, tap **Start translating**.
3. Speak naturally, then pause briefly — the app repeats you in the other language.
4. It then listens for the reply and repeats it to you in English. Repeat until done; tap **End session** to stop.

On an Android phone you can use Chrome's **Add to Home Screen** so it launches full-screen like an installed app.

## Running from the GitHub files (Windows)

The app is a static web page — there is no build step. It just needs to be served over HTTP (browsers block microphone access on `file://` pages).

**Prerequisites:** [Google Chrome](https://www.google.com/chrome/) and one of the options below.

**Option A — Python (simplest):**
1. Install Python from [python.org](https://www.python.org/downloads/) (check "Add to PATH" during install).
2. Open Command Prompt in the folder containing `Live Translator.dc.html` (in File Explorer, type `cmd` in the address bar).
3. Run: `python -m http.server 8000`
4. Open Chrome at `http://localhost:8000/Live%20Translator.dc.html`

**Option B — Node.js:**
1. Install [Node.js](https://nodejs.org/).
2. In the project folder run: `npx serve .`
3. Open the printed URL and click `Live Translator.dc.html`.

**Using it on your phone:** the microphone only works on `localhost` or HTTPS, so for phone use, host the files somewhere with HTTPS — the free [GitHub Pages](https://pages.github.com/) is the easiest: in your repo go to **Settings → Pages**, deploy from your main branch, then open the published URL on your Pixel in Chrome.

Keep the `_ds/` folder next to `Live Translator.dc.html` — it carries the app's styling and components.

> **Note on translation:** speech recognition and text-to-speech run in the browser, but the translation step uses an AI service that is wired up automatically inside the design workspace where this app was built. When self-hosting from GitHub, that hookup isn't present — translation calls will fail until an equivalent API is connected. The app was primarily built to run (and be shared) from its original workspace.
