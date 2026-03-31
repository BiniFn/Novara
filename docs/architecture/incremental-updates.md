# Incremental OTA Updates (bspatch)

Kototoro implements a custom, highly efficient Over-the-Air (OTA) update mechanism to minimize bandwidth usage when downloading new releases.

## The Problem
Standard Android App updates via GitHub Releases require downloading the full `.apk` file (typically 30MB - 100MB+) for every minor update. For users on metered connections or slow networks, this is extremely inefficient.

## The Solution
Instead of forcing a full APK download, Kototoro's CI/CD pipeline and Android application coordinate to deliver **delta updates (patches)**.

### 1. CI/CD Patch Generation (`bsdiff`)
When a new version is released, the GitHub Actions `release.yml` workflow automatically:
1. Downloads the **previous** stable version's APK.
2. Runs the standard `bsdiff` algorithm to compute the exact binary difference between the old APK and the newly compiled APK.
3. Uploads the resulting `.patch` file alongside the standard `.apk` to the GitHub Release.

Because `bsdiff` is highly efficient at detecting shifted offsets in compressed files, the resulting `.patch` file is typically only a few megabytes (often 10% the size of the full APK).

### 2. Pure Kotlin Client-Side Patcher (`bspatch`)
To merge a `bsdiff` patch, the client must run the `bspatch` algorithm.
Typically, Android apps rely on NDK (C/C++) and JNI bindings to execute `bspatch.c`. This approach introduces massive ABI complexity (requiring `.so` builds for `arm64-v8a`, `armeabi-v7a`, `x86`, etc.) and inflates the APK size.

Kototoro avoids all NDK overhead by implementing a **pure Kotlin `bspatch` algorithm** (`PatchUtils.kt`).
The algorithm uses the `commons-compress` library (which is already included in the app for ZIP/CBZ reading) to decompress the BZip2 payload streams (`ctrl`, `diff`, and `extra`) from the `.patch` file natively in the JVM.

### 3. Safety and Version Matching
Delta patches (`bsdiff`) are strictly tied to the exact byte-for-byte state of the old file. If a patch generated for `v1.0.0 -> v1.0.1` is applied to `v0.9.9`, the resulting APK will be corrupted.

To guarantee safety, `AppUpdateRepository` enforces a strict version validation:
- The app fetches the chronological list of all available releases.
- It identifies the latest release and the release immediately preceding it.
- **The `.patch` URL is only selected if the currently installed application version EXACTLY matches the preceding release.**
- If the user skipped a version or is on a custom debug build, the app automatically falls back to downloading the full `.apk`.

### 4. Seamless Installation
When a `.patch` download completes seamlessly in the background (using Android's `DownloadManager`):
1. The `AppUpdateViewModel` extracts the current installation's base APK (`context.applicationInfo.sourceDir`).
2. It runs `PatchUtils.patch()` to natively merge the base APK with the downloaded `.patch` into a temporary `update_merged.apk` in the app's cache directory.
3. The newly generated APK is exposed to the Android Package Installer via `FileProvider`, prompting the user to install the update exactly as if they had downloaded the full file.
