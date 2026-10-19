# MKpdf

An Android Studio source implementation of a local-first PDF editor with a
Jetpack Compose frontend and real PdfCraft Rust JNI operations. Min SDK 26,
compile/target SDK 34. This is not a claim of production-complete PDF support.
Read docs/ARCHITECTURE.md for precise coverage and limitations before shipping.

## Quick start

Install:

- Android Studio with JDK 17 (a compatible newer JDK also works).
- Android SDK Platform 34, Build Tools 34.0.0 and Platform Tools.
- Android NDK 28.2.13676358 (r28c).
- Python 3, Git, Rust/rustup, CMake, and a working host C/C++ build toolchain.
  On Windows use the Visual Studio C++ build tools. Keep Python, Git, cargo and
  CMake on PATH when launching Android Studio. Windows can use `-Ppython=python`.

From this directory:

```sh
python3 scripts/bootstrap.py
rustup show
cargo install cargo-ndk --version 4.1.2 --locked
sh gradlew :app:assembleDebug
```

The Rust toolchain file installs pinned Rust 1.95.0 and its four Android targets.
`bootstrap.py` pins PdfCraft to commit
`43550c448db3ba91753f638e3843c0da036bbd60`, including its patched hayro/lopdf
renderer sources. Do not replace the path dependencies with guessed crates.io
releases; the vendored patches are intentionally mirrored in native/Cargo.toml.
The included native/Cargo.lock pins the resolved dependency graph. Initial
bootstrap/build downloads dependencies. Installed-app PDF work is offline and
requires no account or server; source builds are not network-free on first use.

Windows equivalents:

```powershell
python scripts/bootstrap.py
rustup show
cargo install cargo-ndk --version 4.1.2 --locked
.\gradlew.bat :app:assembleDebug -Ppython=python
```

Open this directory in Android Studio. Let Studio create `local.properties`
with your SDK path, or set ANDROID_HOME yourself. The preBuild task invokes
cargo-ndk automatically for arm64-v8a, armeabi-v7a, x86, and x86_64, API 26.
Its output is app/build/generated/rustJniLibs/<abi>/libmkpdf_jni.so and is included
in the APK. Debug/release Android builds both currently use release-optimized
Rust. The Android release variant is unsigned until you configure your own
signing (no credentials are included).

Do not skip buildRust for an installable APK. Skipping it is useful ONLY for
checking Kotlin/tests when native libraries are not available:

```sh
sh gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest -x buildRust
```

This does not prove native packaging, JNI loading, or runtime functionality.

## Host Rust checks

```sh
python3 scripts/bootstrap.py
cargo check --manifest-path native/Cargo.toml --locked
cargo test --manifest-path native/Cargo.toml --lib --locked
```

For resource-constrained build hosts, set CARGO_BUILD_JOBS=2 and
CARGO_PROFILE_DEV_DEBUG=0. On-device tests and a manual acceptance matrix are
in docs/TEST_PLAN.md. Actual checks run during generation are recorded in
docs/VALIDATION.md; do not infer an Android device test from a host Rust test.

## What the UI implements

- SAF document open, a recent-file read grant, new blank documents, Save As.
- Horizontal paging, pinch zoom/pan, double-tap zoom/reset.
- Earlier/later moves, deletion, blank-page insertion, selected PDF-page import,
  multi-file combine with an order-review UI, per-page splitting, 90/180/270 rotation.
- New text boxes, real existing-line content editing, positioned/scaled images,
  and image insertion as a new PDF page.
- AcroForm text/check/radio/combo/list filling with actual saved appearances.
- Debounced, progressive extracted-text search, viewer-only match rectangles,
  next/previous traversal in the renderer's extracted reading order.
- Native undo/redo and an app-private single-document recovery PDF.

All document work and SAF IO run off the main thread. See the architecture guide
for coordinate conventions, synchronization, resource caps, and save semantics.

## Honest coverage boundaries

The universal version of “fully functional” in the request is not established:
all four ABIs must still be built and tested on devices/emulators. Arbitrary
PDF existing-text editing and intended semantic reading order cannot be promised
for every font, encoding, layout, or malformed document. This implementation
uses AcroForm panels, not a Word-like editor, dynamic-XFA engine UI, or draggable
text/image handles. Save As is intentionally safer than overwriting a source.

No excluded tool is exposed or accepted by the JNI protocol. However, upstream
pdfcraft-engine has unconditional dependencies on several excluded-feature
modules. If exclusion means removing those dependencies from the build graph,
this project does not meet that stricter requirement without an upstream fork.

## Project map

```
MKpdf/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradlew, gradlew.bat, gradle/wrapper/
  rust-toolchain.toml
  scripts/bootstrap.py
  scripts/build_native.py
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/res/values/styles.xml
  app/src/main/java/com/mkpdf/app/
    Models.kt
    RustBridge.kt
    SafStore.kt
    PdfRepository.kt
    EditorViewModel.kt
    PageViewer.kt
    EditorDialogs.kt
    MainActivity.kt
  app/src/test/java/com/mkpdf/app/PageRangesTest.kt
  app/src/androidTest/java/com/mkpdf/app/NativeSmokeTest.kt
  native/Cargo.toml
  native/Cargo.lock
  native/src/lib.rs
  tests/fixtures/one-pixel.png
  docs/ARCHITECTURE.md
  docs/TEST_PLAN.md
  docs/VALIDATION.md
  THIRD_PARTY_NOTICES.md
  vendor/pdfcraft/  # created by bootstrap, intentionally not duplicated in ZIP
```

The source archive contains all MKpdf-authored build/app/native/test files, the
Gradle wrapper JAR, the Rust lockfile, and the tiny PNG fixture. PdfCraft itself
is fetched at its exact commit rather than duplicated. There are no generated
native binaries or installable APKs in this source deliverable.
