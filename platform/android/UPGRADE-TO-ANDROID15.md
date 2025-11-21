OpenDroidPDF — Android 15 (API 35) Modernization Notes (former Pen&PDF lineage)

What changed
- Build system: Switched from legacy Ant project to a single-module Gradle app under `platform/android`.
- AndroidX: Migrated support libraries to AndroidX (appcompat, core, viewpager, recyclerview, cardview, material).
- Manifest updates: Added `android:exported` to activities with intent-filters, migrated `FileProvider` to `androidx.core.content.FileProvider`, removed legacy storage permissions.
- Storage Access Framework (SAF): On Android 10+ (API 29+), `PenAndPDFFileChooser` now launches the system file pickers (OPEN/CREATE DOCUMENT) instead of the custom file browser.
- NDK: Reuses the existing ndk-build makefiles via Gradle’s `externalNativeBuild`.

How to build
1) Open `platform/android` in Android Studio (Koala or newer).
2) Let it install the Android 15 (API 35) SDK and NDK if prompted.
3) Build the app: Build → Make Project, or use `./gradlew assembleDebug` once the wrapper is created by Android Studio.

Notes
- For devices running API < 29 the legacy in-app file browser remains available. For API ≥ 29 the app uses the system SAF pickers and no longer requires external storage permissions.
- If you need more ABIs than `arm64-v8a`, `armeabi-v7a`, and `x86_64`, adjust `abiFilters` in `platform/android/build.gradle`.
- If the NDK raises warnings with newer toolchains, consider updating the `Android.mk` includes or migrating to CMake in a follow-up.
