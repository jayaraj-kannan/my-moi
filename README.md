<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Moi

Moi tracks gift-giving traditions. Log gifts and monetary contributions made or received during family events, import CSV histories with intuitive mapping, and search records cleanly.

## Prerequisites

- [Android Studio](https://developer.android.com/studio) installed or Java/Android SDK configured.
- A physical Android device with **USB Debugging** enabled, connected via USB (or an active Android Emulator).

## Compile and Install on Android Device (Debug)

You can compile and install the debug version of this app directly to your connected device using Gradle from the command line:

1. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example).
2. Open your terminal in the root directory of this project.
3. Run the following command to compile the app and install it onto your device:

   ```bash
   # On macOS/Linux:
   ./gradlew installDebug
   ```
   
   *(If prompted with permissions for Gradle, accept them)*

4. Once the build completes successfully, look for the **Moi** app installed on your Android device and open it!
