CLOUDGPU HARDWARE INSPECTOR — FIRST BUILD

WHAT THIS IS
A small Android app project. It checks Android-reported device information and runs local CPU and storage tests. It does not download test files.

PRIVACY
- It does not open personal photos, messages, documents, passwords, or other personal files.
- Storage testing creates a temporary 256 MiB file inside this app's private cache, reads it back, checks the data, and deletes it.
- The app requests Phone permission only to read the mobile network type (for example, LTE or 5G NR). If denied, that check is skipped.
- No internet permission is included in the app.

IMPORTANT LIMITS
- Android system information can be spoofed by modified or dishonest firmware.
- RAM values are OS-reported, not independent proof of physical RAM chips.
- The storage test checks 256 MiB only. It does NOT prove a claimed 1 TB capacity.
- The displayed storage total is the Android data partition, not necessarily the full physical storage chip.
- 5G NR is reported only when Android says the current mobile data connection is 5G. A 4G result does not prove the modem lacks 5G.
- This is a diagnostic tool, not a certification of authenticity.

HOW TO BUILD THE APK FOR FREE WITH GITHUB
1. Sign in or create a free GitHub account at https://github.com/
2. Create a NEW repository. Name it CloudGPU-Hardware-Inspector.
3. Set it to Private or Public (your choice), then create it.
4. On the new repository page, choose "uploading an existing file".
5. Extract this ZIP on your computer.
6. Upload the CONTENTS of the extracted CloudGPU_Hardware_Inspector folder, including the hidden .github folder and its workflow file. If the browser hides .github, use GitHub's Add file > Create new file option and create:
   .github/workflows/build-apk.yml
   Then paste the contents from the same file in this project.
7. Commit the uploaded files to the main branch.
8. Open the repository's Actions tab. Select "Build Android APK" and click "Run workflow" if it has not started automatically.
9. Wait for the workflow to finish with a green check mark.
10. Open the completed workflow run. Under Artifacts, download "CloudGPU-Hardware-Inspector-APK".
11. Extract the downloaded artifact ZIP. It contains app-debug.apk.
12. Transfer app-debug.apk to the Android tablet and install it. Android may ask you to allow installation from that source.

FREE BUILD NOTE
GitHub Actions offers free usage limits for public repositories and limited included minutes for private repositories. This project uses a hosted Linux runner; it does not require Android Studio on your ARM Windows laptop. GitHub may ask you to verify your account or enable Actions.

PROJECT SETTINGS
- Android minimum version: Android 8.0 (API 26)
- Compile SDK: 35
- Android Gradle Plugin: 8.9.2
- Gradle: 8.11.1
- Java: 17
