# Building the Puente APK (Windows)

Puente is a native Android app. It uses:

- **Android speech recognition** to hear each speaker
- **Google ML Kit on-device translation**, which is free, needs no API key, and runs offline once language packs are downloaded
- **Android text-to-speech** to speak translations

There are two ways to get the APK. **Path A** needs no software on your PC. **Path B** uses Android Studio.

---

## Path A: Let GitHub build it (no installs)

1. Upload the whole project to your GitHub repository. Include the `android/` folder and the `.github/` folder, and keep them at the top level of the repo.
   - On github.com: **Add file → Upload files**, then drag both folders in. If `.github` doesn't show in File Explorer, turn on **View → Show → Hidden items**.
2. Open your repo's **Actions** tab. A workflow called **Build APK** starts on every push to `main`. To start it by hand, click **Build APK → Run workflow**.
3. Wait about 5–10 minutes for the green check mark. Open the run, scroll to **Artifacts**, and download **Puente-apk**. This is a zip containing `Puente.apk`.
4. Install it on your phone (see "Installing on your Pixel" below).

---

## Path B: Build with Android Studio

### 1. Install Android Studio
- Download it from https://developer.android.com/studio and run the installer with the default options.
- On first launch, choose **Standard** setup and let it download the Android SDK (several GB).

### 2. Open the project
- **File → Open**, then select the **`android`** folder. Select the `android` folder itself, not the repo root.
- Click **Trust Project** if asked.
- Wait for **Gradle sync** to finish (progress bar at the bottom). The first time can take 5–15 minutes.
- If Android Studio offers to upgrade the Android Gradle Plugin, you can accept or dismiss it. Either works.

### 3. Build the APK
- Menu: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. In newer versions this is **Build → Generate App Bundles or APKs → Generate APKs**.
- When it finishes, click **locate** in the pop-up. The file is at `android\app\build\outputs\apk\debug\app-debug.apk`.

### 4. (Optional) Install straight from Android Studio
1. On the Pixel: **Settings → About phone**, then tap **Build number** 7 times to enable Developer options.
2. Go to **Settings → System → Developer options** and turn on **USB debugging**.
3. Plug the phone into your PC and accept the prompt on the phone.
4. Pick your Pixel in the device dropdown at the top of Android Studio and press **Run ▶**.

### 5. A signed APK for sharing
APKs from Path A and step 3 use a temporary debug key. That's fine for personal use. To share publicly and be able to ship updates:
1. **Build → Generate Signed App Bundle or APK → APK → Next**.
2. **Create new…** keystore. Choose a location and passwords, and fill in your name.
3. **Back up this keystore file and its passwords.** You need the same key for every future update.
4. Choose the **release** build, then **Create**. The output is at `android\app\release\app-release.apk`.

---

## Installing on your Pixel
1. Get the APK onto the phone: download it from GitHub on the phone, use Google Drive, or copy it over USB.
2. Tap the file. When asked, allow **Install unknown apps** for that app (Files, Chrome or Drive).
3. Play Protect may warn about an unrecognized developer. Tap **More details → Install anyway**.
4. Open **Puente** and allow microphone access.

**First use of each language pair:** the app downloads an offline language pack (about 30 MB per language). Be on Wi-Fi the first time.

**More/better voices:** Settings → System → Languages → Text-to-speech output → Google → install voice data for your languages. In the app, **Try a voice** cycles through the installed voices.

## Sharing with others
- Create a GitHub **Release**: repo → **Releases → Draft a new release**, attach `Puente.apk`, then **Publish**. Others download it from there.
- Each person installs it with the steps above.

## Known limitations
- **Phone calls:** while a cellular call is active, Android generally gives the microphone to the call. Other apps, including Puente, may hear only silence. Test this on your phone. If it doesn't work, run Puente on a second device (tablet or old phone) next to the speakerphone, or use **In person** mode.
- Keep Puente on screen while translating. Android stops background apps from using the microphone.
- Speech recognition uses Google's speech service on the phone and may need internet for some languages. Translation itself runs on-device.
- Translations from the on-device models are good for conversation but less polished than cloud services.

## Troubleshooting
- **"SDK location not found"**: open the project through Android Studio (step 2). It creates `local.properties` automatically.
- **Gradle JDK error**: go to **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**, and set **Gradle JDK** to the bundled **jbr-17** or **jbr-21**.
- **"App not installed"**: uninstall any older Puente first. APKs signed with different keys can't update each other.
