# Boards Plan (Android, Rust + Tauri 2)

The study planner web page wrapped as a native Android app. Rust (Tauri) hosts the WebView;
all progress is stored on the phone (localStorage).

## Get the APK with no setup (GitHub Actions)
1. Create a new GitHub repository and upload everything in this folder (keep the `.github` folder).
2. Open the repository's **Actions** tab, choose **Build Android APK**, then **Run workflow**.
3. After about 10 to 15 minutes, open the finished run and download the **boards-plan-apk** artifact.
4. Unzip it, copy the `.apk` to your phone, open it and allow "Install unknown apps" when asked.

It is a debug-signed APK, fine for installing on your own phone. The workflow builds for all
Android CPU types in one universal APK.

## Build on your own computer
Install Rust, Node 20, JDK 17 and Android Studio (SDK, NDK 27), set `ANDROID_HOME` and `NDK_HOME`, then:
```
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
npm install
npx tauri icon app-icon.png
npx tauri android init
npx tauri android build --apk --debug
```
The APK ends up in `src-tauri/gen/android/app/build/outputs/apk/`.

To change the plan, edit `dist/index.html`.
