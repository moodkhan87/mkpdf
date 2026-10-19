# MKpdf — Android Studio project and code blueprint

> A source implementation with verified host/Kotlin checks, not an assertion of complete Android runtime validation.

The accompanying source ZIP contains every file below, the Rust lockfile, Gradle wrapper JAR, and the PNG test fixture. PdfCraft is fetched at a pinned revision by the bootstrap script.

## Contents

- `README.md`
- `docs/VALIDATION.md`
- `docs/ARCHITECTURE.md`
- `docs/PDFCRAFT_API_MAP.md`
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `app/build.gradle.kts`
- `rust-toolchain.toml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/styles.xml`
- `scripts/bootstrap.py`
- `scripts/build_native.py`
- `native/Cargo.toml`
- `native/src/lib.rs`
- `.gitignore`
- `THIRD_PARTY_NOTICES.md`
- `app/src/androidTest/java/com/mkpdf/app/NativeSmokeTest.kt`
- `app/src/main/java/com/mkpdf/app/EditorDialogs.kt`
- `app/src/main/java/com/mkpdf/app/EditorViewModel.kt`
- `app/src/main/java/com/mkpdf/app/MainActivity.kt`
- `app/src/main/java/com/mkpdf/app/Models.kt`
- `app/src/main/java/com/mkpdf/app/PageViewer.kt`
- `app/src/main/java/com/mkpdf/app/PdfRepository.kt`
- `app/src/main/java/com/mkpdf/app/RustBridge.kt`
- `app/src/main/java/com/mkpdf/app/SafStore.kt`
- `app/src/test/java/com/mkpdf/app/PageRangesTest.kt`
- `docs/TEST_PLAN.md`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradlew`
- `gradlew.bat`

---

## README.md

````markdown
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
````

## docs/VALIDATION.md

````markdown
# Validation actually performed

Checks completed on October 19, 2026. This report distinguishes build-time checks
from runtime/device verification.

## Passed

1. `cargo check --manifest-path native/Cargo.toml` on Linux x86_64 using Rust
   1.95.0 and the pinned PdfCraft source. This checked the actual engine calls
   and JNI crate types, not a mocked Rust facade.
2. `cargo test --manifest-path native/Cargo.toml --lib --locked`: **5 passed,
   0 failed**. The test groups cover organization/render/search/save, invalid
   input and excluded-operation rejection, existing text rewrite, five kinds
   of AcroForm values with save/reopen, imports/combine/extraction/images.
3. `:app:compileDebugKotlin` with Gradle 8.9, AGP 8.7.3, Kotlin 1.9.24,
   Compose compiler 1.5.14 and Android SDK 34: **passed**.
4. `:app:compileDebugAndroidTestKotlin`: **passed**. This compiles the
   instrumented JNI smoke test; it does not execute that test on a device.
5. `:app:testDebugUnitTest`: **4 passed, 0 failures, 0 errors, 0 skipped**.
6. Python build/bootstrap scripts passed Python syntax compilation.

Android Kotlin/tests were checked with `-x buildRust`. The source was mirrored
onto a normal local filesystem for the Android check because this conversation's
mounted filesystem rejected file metadata operations during Android resource
packaging. No project code was changed to bypass Android resource processing.
The one Compose experimental-API opt-in required by compilation was added to
the delivered viewer source before the final successful check.

Native test output:

```
running 5 tests
test tests::existing_text_replacement ... ok
test tests::import_combine_extract_images ... ok
test tests::forms_roundtrip ... ok
test tests::validates_protocol ... ok
test tests::organize_save_render_search ... ok

test result: ok. 5 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out
```

Android build/test output:

```
> Task :app:compileDebugKotlin
> Task :app:compileDebugUnitTestKotlin
> Task :app:compileDebugAndroidTestKotlin
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
```

## Not performed / not established

- cargo-ndk compilation or ELF validation for any Android ABI.
- A complete installable APK build containing the Rust library.
- Loading the library through JNI on Android or executing device tests.
- Physical-device/emulator gesture, lifecycle, SAF-provider, or 16 KiB testing.
- Broad real-world PDF corpus fidelity, performance, or adversarial-input testing.
- Guaranteed semantic reading order or universal existing-text editability.
- Removing excluded-feature dependencies from the upstream engine graph.

The all-four-ABI build automation is delivered, but its successful execution is
not asserted here. The project is not certified as a fully functional production
app across every required feature/device merely because host/Kotlin checks pass.
````

## docs/ARCHITECTURE.md

```markdown
# MKpdf architecture

## Runtime boundary

Compose → EditorViewModel → PdfRepository → RustBridge → JNI → Session.

All document calls, rendering, and SAF stream IO execute on one process-lifetime,
single-thread executor behind a coroutine Mutex. The native Session lives in
thread-local storage: it is NOT forced into a global `Mutex<Session>` with an
unsafe Send implementation. Rust page-render worker threads created by upstream
are separate from this confinement contract.

The JSON control protocol has an explicit allow-list. JSON replies are
`{"ok":true,"data":...}` or `{"ok":false,"error":"..."}`. No JVM exception is
silently interpreted as success. All JNI entrypoints contain unwinding panics;
a native engine panic poisons the Session and requires a process restart.
OOM aborts and native signals cannot be recovered by Rust catch_unwind.

The raster endpoint returns `[width:u32 LE][height:u32 LE][premultiplied RGBA8]`.
Kotlin checks packet length and pixel budget before constructing its Bitmap.

## Files, saves, and recovery

SAF URIs are not filesystem paths. Inputs are copied through ContentResolver to
an app-private staging file and removed after parsing/import. The most recent
opened URI keeps a persistable READ grant if its provider supports it. No broad
storage or network permissions are declared. No original source is overwritten.

Every successful UI edit writes the current PDF to the single app-private
recovery slot using temporary-file + fsync + rename. Recovery is offered after
process recreation. This is not a multi-document history or a full undo journal.
Native undo and redo last only for the live Session. The app confirms replacing
a dirty document before overwriting the recovery slot.

Save As prepares a snapshot on the native worker, writes it using SAF, closes the
stream, and ONLY THEN calls Session.mark_saved. Failure leaves the live document
unmarked. SAF provider output is not guaranteed atomic; an unsuccessful save may
leave a partial destination copy. The source PDF and local recovery copy remain.
Split writes one extracted PDF at a time; an error reports the number completed.

## Coordinate contract

The viewer, search boxes, text placement, and image placement use PdfCraft's
**display space**, in PDF points, with a top-left origin, after CropBox/UserUnit
and /Rotate. AcroForm widget rectangles start in PDF user space; the native
adapter applies PageInfo.user_to_view before exposing them to Compose. Do not
apply an additional manual y-axis inversion to AddedText or AddImage.

Screen overlays scale from display points to the fitted bitmap layout; both
bitmap and overlays receive the same graphicsLayer zoom/pan transform. Search
highlights are VIEWER UI ONLY; they are never inserted into PDF content.

## State consistency

A document has an opaque numeric DocId (never a raw pointer) and a generation.
Render jobs and edit requests include the expected generation. Stale work fails
instead of mutating a different revision. Compose render effects discard canceled
results. Search runs one page at a time, checks cancellation and generation, and
yields between pages. It is debounced by 180 ms, not a synchronous main-thread
whole-document scan.

The native renderer keeps up to 12 extracted page-text layers. Android bitmaps
use a 32 MiB LRU; JNI rasters are limited to approximately 8 MP. Input files and
combined source totals are limited to 128 MiB. A search page returns at most
1,000 matches and document traversal stops after approximately 10,000. These are
explicit resource limits, not claims of unbounded large-document support.

## Engine operations used

- Session.open, get, close, create_blank, save_bytes, mark_saved, undo, redo.
- Edit.MovePages / DeletePages / InsertBlankPage / InsertPagesFrom / RotatePages.
- Session.combine and extract; splitting is repeated per-page extraction.
- Edit.AddText / EditTextLine / AddImage, Session.create_from_images.
- Edit.SetFieldValue, FieldValue.Text / Check / Radio / Choice.
- PageRenderer.render, RequestKind.Text / Pixels, PageText.find_opts_limited,
  PageText.line_rects, PageInfo.user_to_view.

## Exclusions

The MKpdf UI and allow-listed JNI protocol provide no camera/scanner, OCR,
redaction, PDF-to-other-format export, security/password/permission controls,
compression, stamps, watermarking, signing, or network feature. AcroForm
JavaScript is disabled. Signature fields and push buttons are display-only.
Encryption-protected documents are rejected rather than unlocked or rewritten.
SAF URI grants are Android document access, not PDF document permission tools.

IMPORTANT: upstream pdfcraft-engine is a monolithic facade whose Cargo manifest
unconditionally depends on modules including OCR, optimization, signing,
redaction, export, and cryptographic parsing. This project does not call or
expose those tools, but they remain in the build dependency graph. If “do not
include” means **no such dependencies or code in the binary**, this facade must
be feature-gated in a maintained upstream fork, or replaced with a smaller
adapter over cos/organize/forms/edit/render. This deliverable does NOT claim
that stricter dependency-level exclusion.

## Known limitations, not hidden TODO implementations

1. Existing text is edited a line at a time using the engine's actual content
   stream rewrite. Not every embedded/subset/custom font can encode a replacement;
   substitutions or unsupported cases must be tested on a real PDF corpus. There
   is no universal document reflow or Word-like layout promise.
2. Search uses the renderer's extracted reading-order layout and geometric line
   boxes. It does NOT implement a new structure-tree/tag-order traversal layer.
   It cannot guarantee intended semantic order for every multi-column, tagged,
   RTL/vertical, or malformed PDF. Thus the strongest interpretation of “real
   reading order traversal” is not fully satisfied.
3. Forms are AcroForm controls in a native Compose form panel. Loading the form
   panel also enables shaded widget hit targets in the viewer. Dynamic XFA and
   JavaScript-dependent validation/calculation workflows are not promised.
4. Position/scale for inserted images and text is specified numerically in display
   points. There are no drag/resize handles or WYSIWYG text-caret editing tools.
5. The default Save action is Save As, not destructive in-place source replacement.
6. Android ABI compilation, JNI behavior on devices, pinch/pager interaction,
   and rendering fidelity require the provided on-device acceptance tests.

There are no fake engine methods, TODO bodies, or placeholder-success JNI calls
for these limitations. Some requirements have narrower coverage than the user's
full specification; this is a source implementation and an explicit engineering
baseline, not a certified production-complete PDF editor.
```

## docs/PDFCRAFT_API_MAP.md

```markdown
# Pinned upstream API map

Inspected repository: https://github.com/storytold/pdfcraft
Commit: `43550c448db3ba91753f638e3843c0da036bbd60`
Workspace version: 0.9.0, edition 2024, declared Rust version 1.95.

The source inspection and successful host compilation establish the following
API mappings for this exact revision (not for arbitrary future main-branch APIs):

| MKpdf protocol | Actual PdfCraft API | Upstream location |
|---|---|---|
| open/close/info | Session.open / close / get | crates/engine/src/lib.rs |
| blank | Session.create_blank then open | crates/engine/src/lib.rs |
| move/delete/rotate | Edit.MovePages / DeletePages / RotatePages | crates/engine/src/lib.rs |
| insert_blank/insert_pdf | Edit.InsertBlankPage / InsertPagesFrom | crates/engine/src/lib.rs |
| combine | Session.combine | crates/engine/src/lib.rs |
| extract/split | Session.extract | crates/engine/src/lib.rs |
| add_text | Edit.AddText with AddedText | crates/engine/src/lib.rs; crates/edit/src/added.rs |
| lines/edit_line | Document.text_lines / Edit.EditTextLine | crates/engine/src/lib.rs; crates/edit/src/text.rs |
| add_image | Edit.AddImage | crates/engine/src/lib.rs |
| image_page | Session.create_from_images then InsertPagesFrom | crates/engine/src/lib.rs |
| fields/set_field | Document.form / Edit.SetFieldValue | crates/engine/src/lib.rs; crates/forms/src/lib.rs |
| snapshot/mark_saved | Session.save_bytes / mark_saved | crates/engine/src/lib.rs |
| raster | PageRenderer.render, RequestKind.Pixels | crates/render/src/raster.rs |
| search_page | RequestKind.Text, PageText.find_opts_limited / line_rects | crates/render/src/raster.rs; crates/render/src/text.rs |
| form overlay transforms | PageInfo.user_to_view | crates/render/src/inspect.rs |

The organizer's MovePages target is a position in the **resulting** document;
MKpdf uses page±1 accordingly. Text and image insertion boxes are already in
**display space**, unlike raw PDF annotation rectangles. The actual renderer
pixel type documents premultiplied RGBA8; it is not an assumed BGRA buffer.

No render_to_bitmap, edit_existing_text, PdfCraft.open, or other imagined engine
methods are used. The Android bridge provides its own protocol over verified
upstream APIs rather than assuming PdfCraft ships an Android JNI binding.
```

## settings.gradle.kts

```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "MKpdf"
include(":app")
```

## build.gradle.kts

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
```

## gradle.properties

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

## app/build.gradle.kts

```kotlin
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.mkpdf.app"
    compileSdk = 34
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "com.mkpdf.app"
        minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/rustJniLibs"))
    packaging { jniLibs.useLegacyPackaging = false }
    buildTypes { release { isMinifyEnabled = false } }
}
val buildRust by tasks.registering(Exec::class) {
    group = "build"
    description = "Cross-compile the pinned PdfCraft JNI crate for all four Android ABIs"
    workingDir(rootDir)
    // Python is also available on Windows as `python`; override with -Ppython=py if needed.
    doFirst { commandLine(providers.gradleProperty("python").orElse("python3").get(),
        "scripts/build_native.py", "--ndk", android.ndkDirectory.absolutePath,
        "--output", layout.buildDirectory.dir("generated/rustJniLibs").get().asFile.absolutePath) }
    // cargo performs its own dependency tracking; do not falsely mark vendor changes up-to-date.
}
tasks.named("preBuild").configure { dependsOn(buildRust) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
}
```

## rust-toolchain.toml

```toml
[toolchain]
channel = "1.95.0"
profile = "minimal"
components = ["rustfmt"]
targets = ["aarch64-linux-android", "armv7-linux-androideabi", "i686-linux-android", "x86_64-linux-android"]
```

## app/src/main/AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Deliberately no network, camera, or broad storage permissions. -->
    <application android:label="MKpdf" android:theme="@style/Theme.MKpdf"
        android:allowBackup="false" android:supportsRtl="true">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

## app/src/main/res/values/styles.xml

```xml
<resources><style name="Theme.MKpdf" parent="android:style/Theme.Material.Light.NoActionBar">
    <item name="android:fontFamily">sans</item><item name="android:windowLightStatusBar">true</item>
    <item name="android:colorAccent">#005B50</item>
</style></resources>
```

## scripts/bootstrap.py

```python
#!/usr/bin/env python3
"""Materialize a pinned upstream checkout, preserving its patched renderer dependencies."""
import pathlib, subprocess
ROOT = pathlib.Path(__file__).resolve().parents[1]
REV = "43550c448db3ba91753f638e3843c0da036bbd60"
DEST = ROOT / "vendor/pdfcraft"
def run(*args): subprocess.run(args, cwd=ROOT, check=True)
if not (DEST / ".git").exists():
    DEST.parent.mkdir(parents=True, exist_ok=True)
    run("git", "init", str(DEST))
    run("git", "-C", str(DEST), "remote", "add", "origin", "https://github.com/storytold/pdfcraft.git")
try:
    current = subprocess.check_output(["git", "-C", str(DEST), "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL).strip()
except subprocess.CalledProcessError:
    current = ""
if current != REV:
    run("git", "-C", str(DEST), "fetch", "--depth", "1", "origin", REV)
    run("git", "-C", str(DEST), "checkout", "--detach", REV)
actual = subprocess.check_output(["git", "-C", str(DEST), "rev-parse", "HEAD"], text=True).strip()
if actual != REV: raise SystemExit("PdfCraft revision mismatch")
print("Pinned PdfCraft:", actual)
```

## scripts/build_native.py

```python
#!/usr/bin/env python3
import argparse, os, pathlib, shutil, subprocess, sys
ROOT = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument("--ndk", required=True)
p.add_argument("--output", required=True)
a = p.parse_args()
subprocess.run([sys.executable, str(ROOT / "scripts/bootstrap.py")], check=True)
if not pathlib.Path(a.ndk, "source.properties").is_file(): raise SystemExit("Install the configured Android NDK in SDK Manager")
env = dict(os.environ, ANDROID_NDK_HOME=a.ndk, ANDROID_NDK_ROOT=a.ndk)
# A single artifact format for 4/16-KiB devices, including the 32-bit ABIs.
env["CARGO_ENCODED_RUSTFLAGS"] = "\x1f".join(["-C", "link-arg=-Wl,-z,max-page-size=16384", "-C", "link-arg=-Wl,-z,common-page-size=16384"])
cargo = shutil.which("cargo") or str(pathlib.Path.home() / ".cargo/bin/cargo")
cmd = [cargo, "ndk", "--platform", "26", "-o", str(pathlib.Path(a.output).resolve())]
for abi in ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"]: cmd += ["-t", abi]
cmd += ["build", "--manifest-path", "native/Cargo.toml", "--release", "--locked"]
subprocess.run(cmd, cwd=ROOT, env=env, check=True)
for abi in ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"]:
    if not pathlib.Path(a.output, abi, "libmkpdf_jni.so").is_file(): raise SystemExit("Missing ABI: " + abi)
```

## native/Cargo.toml

```toml
[package]
name = "mkpdf-jni"
version = "0.1.0"
edition = "2024"
rust-version = "1.95"
license = "MIT OR Apache-2.0"
[lib]
name = "mkpdf_jni"
crate-type = ["cdylib", "rlib"]
[dependencies]
jni = "=0.21.1"
serde_json = "1"
pdfcraft-engine = { path = "../vendor/pdfcraft/crates/engine" }
pdfcraft-cos = { path = "../vendor/pdfcraft/crates/cos" }
pdfcraft-organize = { path = "../vendor/pdfcraft/crates/organize" }
pdfcraft-forms = { path = "../vendor/pdfcraft/crates/forms" }
pdfcraft-annot = { path = "../vendor/pdfcraft/crates/annot" }
pdfcraft-render = { path = "../vendor/pdfcraft/crates/render" }
pdfcraft-edit = { path = "../vendor/pdfcraft/crates/edit" }
[patch.crates-io]
hayro-interpret = { path = "../vendor/pdfcraft/vendor/hayro-interpret" }
hayro = { path = "../vendor/pdfcraft/vendor/hayro" }
hayro-syntax = { path = "../vendor/pdfcraft/vendor/hayro-syntax" }
hayro-jbig2 = { path = "../vendor/pdfcraft/vendor/hayro-jbig2" }
lopdf = { path = "../vendor/pdfcraft/vendor/lopdf" }
winit = { path = "../vendor/pdfcraft/vendor/winit" }
egui-wgpu = { path = "../vendor/pdfcraft/vendor/egui-wgpu" }
vello_common = { path = "../vendor/pdfcraft/vendor/vello_common" }
[profile.release]
lto = "thin"
codegen-units = 1
panic = "unwind"
```

## native/src/lib.rs

```rust
//! MKpdf's deliberately allow-listed protocol. All entry points catch Rust unwinds.
//! Kotlin confines this library to ONE persistent worker thread. Session is not assumed Send.
use jni::{
    JNIEnv,
    objects::{JClass, JString},
    sys::{jbyteArray, jint, jlong, jstring},
};
use pdfcraft_engine::{AddedText, DocId, Edit, FieldValue, Session};
use pdfcraft_render::{PageRenderer, PageText, RenderConfig, RenderRequest, RequestKind};
use serde_json::{Value, json};
use std::{
    cell::RefCell,
    fs,
    panic::{AssertUnwindSafe, catch_unwind},
    path::{Path, PathBuf},
    sync::Arc,
};
type R<T> = Result<T, String>;
const MAX_FILE: u64 = 128 * 1024 * 1024;
struct Cache {
    id: DocId,
    generation: u64,
    renderer: PageRenderer,
    texts: std::collections::HashMap<usize, Arc<PageText>>,
}
struct State {
    session: Session,
    cache: Option<Cache>,
    root: Option<PathBuf>,
    poisoned: bool,
}
impl Default for State {
    fn default() -> Self {
        let mut session = Session::new();
        session.set_javascript(false);
        Self {
            session,
            cache: None,
            root: None,
            poisoned: false,
        }
    }
}
thread_local! { static STATE: RefCell<State> = RefCell::new(State::default()); }
fn integer(v: &Value, key: &str) -> R<usize> {
    v[key]
        .as_u64()
        .and_then(|v| usize::try_from(v).ok())
        .ok_or_else(|| format!("Expected unsigned integer: {key}"))
}
fn string<'a>(v: &'a Value, key: &str) -> R<&'a str> {
    v[key]
        .as_str()
        .ok_or_else(|| format!("Expected string: {key}"))
}
fn number(v: &Value, key: &str) -> R<f64> {
    v[key]
        .as_f64()
        .filter(|x| x.is_finite())
        .ok_or_else(|| format!("Expected finite number: {key}"))
}
fn rect(v: &Value) -> R<[f64; 4]> {
    let xs = v["rect"].as_array().ok_or("Expected rect array")?;
    if xs.len() != 4 {
        return Err("rect must contain four coordinates".into());
    }
    let mut r = [0.0; 4];
    for i in 0..4 {
        r[i] = xs[i]
            .as_f64()
            .filter(|x| x.is_finite() && x.abs() <= 1e6)
            .ok_or("Invalid rectangle")?;
    }
    if r[2] <= r[0] || r[3] <= r[1] {
        return Err("Rectangle must have positive width and height".into());
    }
    Ok(r)
}
fn ids(v: &Value) -> R<Vec<usize>> {
    v.as_array()
        .ok_or("Expected page list")?
        .iter()
        .map(|x| {
            x.as_u64()
                .and_then(|n| usize::try_from(n).ok())
                .ok_or("Invalid page index".into())
        })
        .collect()
}
fn read_file(path: &Path) -> R<Arc<Vec<u8>>> {
    let size = fs::metadata(path).map_err(|e| e.to_string())?.len();
    if size > MAX_FILE {
        return Err("File exceeds MKpdf's 128 MiB input limit".into());
    }
    fs::read(path).map(Arc::new).map_err(|e| e.to_string())
}
fn plain_pdf(bytes: Arc<Vec<u8>>) -> R<Arc<Vec<u8>>> {
    let info = pdfcraft_render::inspect(bytes.clone(), None).map_err(|e| e.to_string())?;
    if info.encrypted {
        return Err("Encrypted PDFs are outside MKpdf's scope".into());
    }
    Ok(bytes)
}
fn atomic_write(path: &Path, bytes: &[u8]) -> R<()> {
    use std::io::Write;
    let tmp = path.with_extension("tmp");
    let mut f = fs::File::create(&tmp).map_err(|e| e.to_string())?;
    f.write_all(bytes)
        .and_then(|_| f.sync_all())
        .map_err(|e| e.to_string())?;
    fs::rename(tmp, path).map_err(|e| e.to_string())?;
    Ok(())
}
impl State {
    fn doc(&self, id: DocId) -> R<&pdfcraft_engine::Document> {
        self.session.get(id).ok_or("Document is closed".into())
    }
    fn private_path(&self, path: &str) -> R<PathBuf> {
        // Inputs must already exist inside the configured app-private workspace.
        let path = fs::canonicalize(path).map_err(|e| e.to_string())?;
        if !path.starts_with(self.root.as_ref().ok_or("Initialize bridge first")?) {
            return Err("Path outside private workspace".into());
        }
        Ok(path)
    }
    fn workfile(&self, name: &str) -> R<PathBuf> {
        Ok(self
            .root
            .as_ref()
            .ok_or("Initialize bridge first")?
            .join(name))
    }
    fn info(&self, id: DocId) -> R<Value> {
        let d = self.doc(id)?;
        Ok(
            json!({"handle":id.0,"name":d.name,"generation":d.edit_generation(),"dirty":d.dirty,
            "undo":d.can_undo().is_some(),"redo":d.can_redo().is_some(),
            "notices":d.edit_notices.iter().map(|x| format!("{x:?}")).collect::<Vec<_>>(),
            "warnings":d.info.warnings,
            "pages":d.info.pages.iter().map(|p| json!({"width":p.width,"height":p.height,"rotation":p.rotation,"label":p.label})).collect::<Vec<_>>() }),
        )
    }
    fn require_generation(&self, id: DocId, expected: u64) -> R<()> {
        if self.doc(id)?.edit_generation() != expected {
            return Err("Document changed; refresh and retry".into());
        }
        Ok(())
    }
    fn cache(&mut self, id: DocId) -> R<&mut Cache> {
        let d = self.doc(id)?;
        let generation = d.edit_generation();
        if !self
            .cache
            .as_ref()
            .is_some_and(|c| c.id == id && c.generation == generation)
        {
            self.cache = Some(Cache {
                id,
                generation,
                renderer: PageRenderer::new(d.display.clone(), RenderConfig::default()),
                texts: Default::default(),
            });
        }
        self.cache.as_mut().ok_or("Renderer unavailable".into())
    }
    fn page_text(&mut self, id: DocId, page: usize) -> R<Arc<PageText>> {
        if page >= self.doc(id)?.info.pages.len() {
            return Err("No such page".into());
        }
        let c = self.cache(id)?;
        if let Some(t) = c.texts.get(&page) {
            return Ok(t.clone());
        }
        let r = c.renderer.render(RenderRequest {
            page,
            kind: RequestKind::Text,
            tile: None,
            scale: 1.0,
            tag: 0,
            preview: false,
        });
        if let Some(e) = r.error {
            return Err(e);
        }
        let text = r.text.ok_or("Renderer returned no text layer")?;
        if c.texts.len() >= 12 {
            c.texts.clear();
        }
        c.texts.insert(page, text.clone());
        Ok(text)
    }
    fn open(&mut self, name: &str, bytes: Arc<Vec<u8>>) -> R<DocId> {
        let id = self
            .session
            .open(name.to_owned(), None, bytes, None)
            .map_err(|e| e.to_string())?;
        if self.doc(id)?.info.encrypted {
            self.session.close(id);
            return Err("Encrypted PDFs are outside MKpdf's scope".into());
        }
        if !self.doc(id)?.editable() {
            self.session.close(id);
            return Err("The engine could not create an editable document".into());
        }
        Ok(id)
    }
    fn request(&mut self, v: Value) -> R<Value> {
        let op = string(&v, "op")?;
        if op == "init" {
            let path = PathBuf::from(string(&v, "root")?);
            fs::create_dir_all(&path).map_err(|e| e.to_string())?;
            self.root = Some(fs::canonicalize(path).map_err(|e| e.to_string())?);
            return Ok(json!({"ready":true}));
        }
        if op == "open" {
            let p = self.private_path(string(&v, "path")?)?;
            let id = self.open(string(&v, "name")?, read_file(&p)?)?;
            return self.info(id);
        }
        if op == "blank" {
            let bytes = self
                .session
                .create_blank(595.0, 842.0, 1)
                .map_err(|e| e.to_string())?;
            let id = self.open("Untitled.pdf", bytes)?;
            return self.info(id);
        }
        if op == "combine" {
            let inputs = v["inputs"].as_array().ok_or("Expected input files")?;
            if inputs.len() < 2 || inputs.len() > 32 {
                return Err("Combine 2 to 32 files at once".into());
            }
            let mut sources = Vec::new();
            let mut total = 0usize;
            for input in inputs {
                let bytes = plain_pdf(read_file(&self.private_path(string(input, "path")?)?)?)?;
                total = total
                    .checked_add(bytes.len())
                    .ok_or("Input size overflow")?;
                if total as u64 > MAX_FILE {
                    return Err("Combined inputs exceed 128 MiB".into());
                }
                sources.push((string(input, "name")?.to_owned(), bytes));
            }
            let bytes = self.session.combine(&sources).map_err(|e| e.to_string())?;
            let id = self.open("Combined.pdf", bytes)?;
            return self.info(id);
        }
        let id = DocId(v["handle"].as_u64().ok_or("Expected handle")?);
        if op == "close" {
            self.session.close(id);
            self.cache = None;
            return Ok(json!({}));
        }
        self.doc(id)?;
        if op == "info" {
            return self.info(id);
        }
        if let Some(generation) = v["generation"].as_u64() {
            self.require_generation(id, generation)?;
        }
        match op {
            "search_page" => {
                let page = integer(&v, "page")?;
                let query = string(&v, "query")?;
                if query.is_empty() {
                    return Ok(json!({"hits":[]}));
                }
                if query.chars().count() > 1024 {
                    return Err("Search query too long".into());
                }
                let text = self.page_text(id, page)?;
                let hits = text.find_opts_limited(query,false,false,1000).into_iter().map(|r| {
                    json!({"page":page,"start":r.start,"end":r.end,"text":text.text_of(r.clone()),"rects":text.line_rects(r)})
                }).collect::<Vec<_>>();
                return Ok(json!({"hits":hits}));
            }
            "lines" => {
                let page = integer(&v, "page")?;
                return Ok(
                    json!({"lines":self.doc(id)?.text_lines(page).iter().enumerate().map(|(i,l)|
                    json!({"index":i,"text":l.text,"rect":l.rect,"editable":l.decodable,"size":l.size,"font":l.base_font})).collect::<Vec<_>>() }),
                );
            }
            "fields" => {
                let d = self.doc(id)?;
                let fields = d.form.iter().map(|f| {
                    let options: Vec<Value> = match f.kind {
                        pdfcraft_forms::FieldKind::Radio => f.widgets.iter().filter_map(|w| w.on_state.as_ref()).map(|s|json!({"value":s,"label":s})).collect(),
                        _ => f.options.iter().map(|(a,b)|json!({"value":a,"label":b})).collect(),
                    };
                    json!({"name":f.name,"kind":format!("{:?}", f.kind),"values":f.value,"options":options,
                        "readonly":f.flags & pdfcraft_forms::flags::READ_ONLY != 0,
                        "multi":f.flags & pdfcraft_forms::flags::MULTI_SELECT != 0,
                        "editableChoice":f.flags & pdfcraft_forms::flags::EDIT != 0,
                        "maxLength":f.max_len,
                        "widgets":f.widgets.iter().map(|w| {
                            let r = w.page.and_then(|i|d.info.pages.get(i)).map(|p| {
                                let a=p.user_to_view(w.rect[0] as f32,w.rect[1] as f32);
                                let b=p.user_to_view(w.rect[2] as f32,w.rect[3] as f32);
                                [a[0].min(b[0]),a[1].min(b[1]),a[0].max(b[0]),a[1].max(b[1])]
                            }); json!({"page":w.page,"rect":r,"hidden":w.hidden,"on":w.on_state})
                        }).collect::<Vec<_>>()})
                }).collect::<Vec<_>>();
                return Ok(json!({"fields":fields}));
            }
            "snapshot" => {
                let bytes = self.session.save_bytes(id).map_err(|e| e.to_string())?;
                let path = self.workfile("snapshot.pdf")?;
                atomic_write(&path, &bytes)?;
                return Ok(
                    json!({"path":path.to_string_lossy(),"generation":self.doc(id)?.edit_generation()}),
                );
            }
            "autosave" => {
                let bytes = self.session.save_bytes(id).map_err(|e| e.to_string())?;
                atomic_write(&self.workfile("recovery.pdf")?, &bytes)?;
                return Ok(json!({}));
            }
            "mark_saved" => {
                let p = self.workfile("snapshot.pdf")?;
                let bytes = Arc::new(fs::read(p).map_err(|e| e.to_string())?);
                self.session
                    .mark_saved(id, bytes, None)
                    .map_err(|e| e.to_string())?;
            }
            "extract" => {
                let bytes = self
                    .session
                    .extract(id, &ids(&v["pages"])?)
                    .map_err(|e| e.to_string())?;
                let path = self.workfile("extracted.pdf")?;
                atomic_write(&path, &bytes)?;
                return Ok(json!({"path":path.to_string_lossy()}));
            }
            "undo" => {
                self.session.undo(id).map_err(|e| e.to_string())?;
            }
            "redo" => {
                self.session.redo(id).map_err(|e| e.to_string())?;
            }
            _ => {
                let page = || integer(&v, "page");
                let edit = match op {
                    "rotate" => {
                        let degrees = v["degrees"].as_i64().ok_or("Expected rotation")?;
                        if ![90, 180, 270].contains(&degrees) {
                            return Err("Rotation must be 90, 180 or 270".into());
                        }
                        Edit::RotatePages {
                            pages: vec![page()?],
                            degrees,
                        }
                    }
                    "delete" => Edit::DeletePages {
                        pages: vec![page()?],
                    },
                    "move" => Edit::MovePages {
                        pages: vec![page()?],
                        to: integer(&v, "to")?,
                    },
                    "insert_blank" => Edit::InsertBlankPage {
                        at: integer(&v, "at")?,
                        width: 595.0,
                        height: 842.0,
                    },
                    "insert_pdf" => Edit::InsertPagesFrom {
                        name: string(&v, "name")?.to_owned(),
                        bytes: plain_pdf(read_file(&self.private_path(string(&v, "path")?)?)?)?,
                        pages: if v["pages"].is_null() {
                            None
                        } else {
                            Some(ids(&v["pages"])?)
                        },
                        at: integer(&v, "at")?,
                        field_conflicts: pdfcraft_organize::FieldConflicts::Rename,
                    },
                    "add_text" => {
                        let size = number(&v, "size")?;
                        if !(1.0..=512.0).contains(&size) {
                            return Err("Font size must be 1–512 pt".into());
                        }
                        Edit::AddText {
                            page: page()?,
                            text: AddedText {
                                rect: rect(&v)?,
                                text: string(&v, "text")?.to_owned(),
                                size,
                                ..Default::default()
                            },
                        }
                    }
                    "edit_line" => Edit::EditTextLine {
                        page: page()?,
                        line: integer(&v, "line")?,
                        text: string(&v, "text")?.to_owned(),
                    },
                    "add_image" => Edit::AddImage {
                        page: page()?,
                        rect: Some(rect(&v)?),
                        name: string(&v, "name")?.to_owned(),
                        bytes: read_file(&self.private_path(string(&v, "path")?)?)?,
                    },
                    "image_page" => {
                        let p = self.private_path(string(&v, "path")?)?;
                        let bytes = read_file(&p)?;
                        let pdf = self
                            .session
                            .create_from_images(&[(
                                string(&v, "name")?.to_owned(),
                                bytes.as_ref().clone(),
                            )])
                            .map_err(|e| e.to_string())?;
                        Edit::InsertPagesFrom {
                            name: "Image.pdf".into(),
                            bytes: pdf,
                            pages: None,
                            at: integer(&v, "at")?,
                            field_conflicts: Default::default(),
                        }
                    }
                    "set_field" => {
                        let name = string(&v, "name")?.to_owned();
                        let kind = self
                            .doc(id)?
                            .form
                            .iter()
                            .find(|f| f.name == name)
                            .ok_or("Unknown field")?
                            .kind;
                        let value = match kind {
                            pdfcraft_forms::FieldKind::Text => {
                                FieldValue::Text(string(&v, "value")?.to_owned())
                            }
                            pdfcraft_forms::FieldKind::CheckBox => FieldValue::Check(
                                v["value"].as_bool().ok_or("Expected checkbox boolean")?,
                            ),
                            pdfcraft_forms::FieldKind::Radio => {
                                FieldValue::Radio(v["value"].as_str().map(str::to_owned))
                            }
                            pdfcraft_forms::FieldKind::Combo | pdfcraft_forms::FieldKind::List => {
                                FieldValue::Choice(
                                    v["value"]
                                        .as_array()
                                        .ok_or("Expected choices")?
                                        .iter()
                                        .map(|x| {
                                            x.as_str()
                                                .map(str::to_owned)
                                                .ok_or("Invalid choice".into())
                                        })
                                        .collect::<R<_>>()?,
                                )
                            }
                            _ => return Err("This field type is not editable in MKpdf".into()),
                        };
                        Edit::SetFieldValue { name, value }
                    }
                    _ => return Err(format!("Operation not allowed: {op}")),
                };
                self.session.apply(id, edit).map_err(|e| e.to_string())?;
            }
        }
        self.cache = None;
        self.info(id)
    }
    fn render(&mut self, id: DocId, page: usize, width: usize, generation: u64) -> R<Vec<u8>> {
        self.require_generation(id, generation)?;
        let p = self.doc(id)?.info.pages.get(page).ok_or("No such page")?;
        if p.width <= 0.0 || p.height <= 0.0 {
            return Err("Invalid page size".into());
        }
        let scale = (width.clamp(64, 4096) as f32 / p.width)
            .min((8_000_000.0 / (p.width * p.height)).sqrt())
            .min(8.0);
        let r = self.cache(id)?.renderer.render(RenderRequest {
            page,
            kind: RequestKind::Pixels,
            tile: None,
            scale,
            tag: 0,
            preview: false,
        });
        if let Some(e) = r.error {
            return Err(e);
        }
        if r.width == 0 || r.height == 0 {
            return Err("Empty raster".into());
        }
        let mut out = Vec::with_capacity(8 + r.rgba.len());
        out.extend(r.width.to_le_bytes());
        out.extend(r.height.to_le_bytes());
        out.extend_from_slice(&r.rgba);
        Ok(out)
    }
}
fn guarded<T>(f: impl FnOnce(&mut State) -> R<T>) -> R<T> {
    STATE.with(|s| {
        let result = catch_unwind(AssertUnwindSafe(|| {
            let mut state = s.borrow_mut();
            if state.poisoned {
                return Err(
                    "Native engine failed; restart the app and recover the working PDF".into(),
                );
            }
            f(&mut state)
        }));
        match result {
            Ok(r) => r,
            Err(_) => {
                s.borrow_mut().poisoned = true;
                Err("Rust panic contained; restart and recover".into())
            }
        }
    })
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_mkpdf_app_RustBridge_nativeCall(
    mut env: JNIEnv,
    _class: JClass,
    request: JString,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| -> R<String> {
        let text: String = env.get_string(&request).map_err(|e| e.to_string())?.into();
        if text.len() > 2 * 1024 * 1024 {
            return Err("Request too large".into());
        }
        let v = serde_json::from_str(&text).map_err(|e| e.to_string())?;
        let result = guarded(|s| s.request(v));
        Ok(match result {
            Ok(data) => json!({"ok":true,"data":data}),
            Err(e) => json!({"ok":false,"error":e}),
        }
        .to_string())
    }));
    match result {
        Ok(Ok(text)) => match env.new_string(text) {
            Ok(s) => s.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        other => {
            let msg = match other {
                Ok(Err(e)) => e,
                _ => "JNI request failed".into(),
            };
            let _ = env.throw_new("java/io/IOException", msg);
            std::ptr::null_mut()
        }
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_mkpdf_app_RustBridge_nativeRender(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    page: jint,
    width: jint,
    generation: jlong,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if handle <= 0 || page < 0 || width <= 0 || generation < 0 {
            return Err("Invalid render parameters".into());
        }
        guarded(|s| {
            s.render(
                DocId(handle as u64),
                page as usize,
                width as usize,
                generation as u64,
            )
        })
    }));
    match result {
        Ok(Ok(bytes)) => match env.byte_array_from_slice(&bytes) {
            Ok(a) => a.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        other => {
            let msg = match other {
                Ok(Err(e)) => e,
                _ => "JNI render failed".into(),
            };
            let _ = env.throw_new("java/io/IOException", msg);
            std::ptr::null_mut()
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    fn setup() -> State {
        let root = std::env::temp_dir().join(format!(
            "mkpdf-test-{}-{:?}",
            std::process::id(),
            std::thread::current().id()
        ));
        let mut s = State::default();
        s.request(json!({"op":"init","root":root})).unwrap();
        s
    }
    #[test]
    fn organize_save_render_search() {
        let mut s = setup();
        let d = s.request(json!({"op":"blank"})).unwrap();
        let h = d["handle"].as_u64().unwrap();
        s.request(json!({"op":"add_text","handle":h,"page":0,"rect":[40,40,300,100],"size":18,"text":"MKpdf local test"})).unwrap();
        let hits = s
            .request(json!({"op":"search_page","handle":h,"page":0,"query":"local"}))
            .unwrap();
        assert!(!hits["hits"].as_array().unwrap().is_empty());
        s.request(json!({"op":"insert_blank","handle":h,"at":1}))
            .unwrap();
        s.request(json!({"op":"rotate","handle":h,"page":1,"degrees":90}))
            .unwrap();
        s.request(json!({"op":"move","handle":h,"page":1,"to":0}))
            .unwrap();
        assert_eq!(s.info(DocId(h)).unwrap()["pages"][0]["rotation"], 90);
        let info = s
            .request(json!({"op":"delete","handle":h,"page":0}))
            .unwrap();
        let rgba = s
            .render(DocId(h), 0, 400, info["generation"].as_u64().unwrap())
            .unwrap();
        assert!(rgba.len() > 8);
        let out = s.request(json!({"op":"snapshot","handle":h})).unwrap();
        let bytes = read_file(Path::new(out["path"].as_str().unwrap())).unwrap();
        assert!(pdfcraft_cos::Document::open(bytes).is_ok());
        assert!(
            s.request(json!({"op":"delete","handle":h,"page":0}))
                .is_err()
        );
    }
    #[test]
    fn validates_protocol() {
        assert!(rect(&json!({"rect":[10,0,0,10]})).is_err());
        let mut s = setup();
        let d = s.request(json!({"op":"blank"})).unwrap();
        assert!(
            s.request(json!({"op":"redact","handle":d["handle"]}))
                .is_err()
        );
        assert!(s.private_path("/etc/passwd").is_err());
    }
    #[test]
    fn forms_roundtrip() {
        use pdfcraft_forms::NewField;
        let mut s = setup();
        let bytes = s.session.create_blank(595.0, 842.0, 1).unwrap();
        let id = s.open("Forms.pdf", bytes).unwrap();
        let specs = vec![
            ("text", NewField::Text { multiline: false }),
            ("check", NewField::CheckBox),
            (
                "choice",
                NewField::Combo {
                    options: vec!["A".into(), "B".into()],
                    editable: false,
                },
            ),
            (
                "list",
                NewField::List {
                    options: vec!["A".into(), "B".into()],
                    multi: true,
                },
            ),
            (
                "radio",
                NewField::Radio {
                    group: Some("radio".into()),
                    export: "First".into(),
                },
            ),
        ];
        for (i, (name, kind)) in specs.into_iter().enumerate() {
            let y = 700.0 - i as f64 * 50.0;
            s.session
                .apply(
                    id,
                    Edit::AddField {
                        page: 0,
                        rect: [40.0, y, 240.0, y + 30.0],
                        kind,
                        name: Some(name.into()),
                    },
                )
                .unwrap();
        }
        for (name, value) in [
            ("text", json!("Hello")),
            ("check", json!(true)),
            ("choice", json!(["B"])),
            ("list", json!(["A", "B"])),
            ("radio", json!("First")),
        ] {
            s.request(json!({"op":"set_field","handle":id.0,"name":name,"value":value}))
                .unwrap();
        }
        let snap = s.request(json!({"op":"snapshot","handle":id.0})).unwrap();
        let reopened = s
            .open(
                "Roundtrip.pdf",
                read_file(Path::new(snap["path"].as_str().unwrap())).unwrap(),
            )
            .unwrap();
        let fields = &s.doc(reopened).unwrap().form;
        assert_eq!(
            fields.iter().find(|f| f.name == "text").unwrap().value,
            vec!["Hello"]
        );
        assert_eq!(
            fields.iter().find(|f| f.name == "list").unwrap().value,
            vec!["A", "B"]
        );
        assert_eq!(
            fields.iter().find(|f| f.name == "radio").unwrap().value,
            vec!["First"]
        );
        assert!(
            fields
                .iter()
                .find(|f| f.name == "check")
                .unwrap()
                .value
                .iter()
                .any(|v| v != "Off")
        );
    }
    #[test]
    fn existing_text_replacement() {
        let mut s = setup();
        let bytes = s
            .session
            .create_from_text("Sample", "Original phrase")
            .unwrap();
        let id = s.open("Text.pdf", bytes).unwrap();
        let lines = s.doc(id).unwrap().text_lines(0);
        let i = lines
            .iter()
            .position(|l| l.text.contains("Original phrase"))
            .unwrap();
        s.request(
            json!({"op":"edit_line","handle":id.0,"page":0,"line":i,"text":"Replaced phrase"}),
        )
        .unwrap();
        assert!(
            s.request(json!({"op":"search_page","handle":id.0,"page":0,"query":"Original phrase"}))
                .unwrap()["hits"]
                .as_array()
                .unwrap()
                .is_empty()
        );
        assert!(
            !s.request(
                json!({"op":"search_page","handle":id.0,"page":0,"query":"Replaced phrase"})
            )
            .unwrap()["hits"]
                .as_array()
                .unwrap()
                .is_empty()
        );
    }
    #[test]
    fn import_combine_extract_images() {
        let mut s = setup();
        let a = s.request(json!({"op":"blank"})).unwrap();
        let id = DocId(a["handle"].as_u64().unwrap());
        let source = s.workfile("source.pdf").unwrap();
        atomic_write(&source, &s.session.create_blank(300.0, 400.0, 2).unwrap()).unwrap();
        s.request(json!({"op":"insert_pdf","handle":id.0,"path":source,"name":"Source.pdf","at":1,"pages":[1,0]})).unwrap();
        assert_eq!(s.doc(id).unwrap().info.pages.len(), 3);
        let out = s
            .request(json!({"op":"extract","handle":id.0,"pages":[1]}))
            .unwrap();
        let extracted = s
            .open(
                "Extracted.pdf",
                read_file(Path::new(out["path"].as_str().unwrap())).unwrap(),
            )
            .unwrap();
        assert_eq!(s.doc(extracted).unwrap().info.pages.len(), 1);
        let combined=s.request(json!({"op":"combine","inputs":[{"name":"a","path":source},{"name":"b","path":source}]})).unwrap();
        assert_eq!(combined["pages"].as_array().unwrap().len(), 4);
        let png: &[u8] = include_bytes!("../../tests/fixtures/one-pixel.png");
        let image = s.workfile("test.png").unwrap();
        atomic_write(&image, png).unwrap();
        s.request(json!({"op":"add_image","handle":id.0,"page":0,"path":image,"name":"test.png","rect":[10,10,90,90]})).unwrap();
        s.request(json!({"op":"image_page","handle":id.0,"at":1,"path":image,"name":"test.png"}))
            .unwrap();
        assert_eq!(s.doc(id).unwrap().info.pages.len(), 4);
    }
}
```

## .gitignore

```text
.gradle/
.idea/
local.properties
**/build/
native/target/
__pycache__/
*.iml
```

## THIRD_PARTY_NOTICES.md

```markdown
# Third-party notices

MKpdf uses PdfCraft at commit 43550c448db3ba91753f638e3843c0da036bbd60.
PdfCraft source code is dual-licensed MIT OR Apache-2.0; the checkout contains
LICENSE-MIT, LICENSE-APACHE, NOTICE, ATTRIBUTION.md and ATTRIBUTION.toml.
Read these and the attribution/licenses for embedded fonts and vendored crates
before redistributing binaries. A Cargo dependency inventory alone is not a
complete legal review or a replacement for preserving required notices.

MKpdf does not copy the upstream ArtCraft brand/logo assets or use them as its
identity. The upstream brand-assets license is not a general open-source brand
license. This project uses a plain text MKpdf label and no custom logo.

The Gradle wrapper scripts/JAR come from Gradle 8.9.0 (Apache-2.0), and the wrapper
distribution checksum is pinned. AndroidX, Kotlin, Rust dependencies, and their
transitive dependencies have their own licenses. Review those for your release.
The source package is an engineering sample; no legal-compliance certification
is made by the generated notices file.
```

## app/src/androidTest/java/com/mkpdf/app/NativeSmokeTest.kt

```kotlin
package com.mkpdf.app
import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class NativeSmokeTest {
    @Test fun nativeLibraryEditsAndRenders()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val repo=PdfRepository(app)
        repo.run {
            init()
            var d=call("blank").document()
            try {
                d=call("add_text",d,"page" to 0,"rect" to listOf(40.0,40.0,300.0,100.0),"size" to 18,"text" to "JNI smoke test").document()
                assertEquals(1,d.pages.size)
                val bitmap=render(d,0,400)
                assertTrue(bitmap.width>0);assertTrue(bitmap.height>0)
                val hits=call("search_page",d,"page" to 0,"query" to "smoke").getJSONArray("hits")
                assertTrue(hits.length()>0)
                val saved=call("snapshot",d)
                assertTrue(java.io.File(saved.getString("path")).length()>0)
            }finally {call("close",d)}
        }
    }
}
```

## app/src/main/java/com/mkpdf/app/EditorDialogs.kt

```kotlin
package com.mkpdf.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun Panel(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth(.96f).fillMaxHeight(.9f)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(title,style=MaterialTheme.typography.titleLarge);TextButton(onClick=onDismiss){Text("Close")}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),content=content)
            }
        }
    }
}
@Composable fun PlacementDialog(image:Boolean,onDismiss:()->Unit,onApply:(String,List<Double>,Double)->Unit) {
    var text by remember {mutableStateOf("")}
    var x by remember {mutableStateOf("36")};var y by remember {mutableStateOf("36")}
    var w by remember {mutableStateOf(if(image)"240" else "300")};var h by remember {mutableStateOf(if(image)"180" else "72")}
    var size by remember {mutableStateOf("18")};var error by remember {mutableStateOf<String?>(null)}
    Panel(if(image)"Place an image" else "Add text box",onDismiss) {
        Text("Coordinates are displayed-page points (1/72 inch), from the top-left. Width and height control placement and scale.")
        if(!image)OutlinedTextField(text,{text=it},label={Text("Text")},modifier=Modifier.fillMaxWidth())
        Row {OutlinedTextField(x,{x=it},label={Text("Left")},modifier=Modifier.weight(1f));OutlinedTextField(y,{y=it},label={Text("Top")},modifier=Modifier.weight(1f))}
        Row {OutlinedTextField(w,{w=it},label={Text("Width")},modifier=Modifier.weight(1f));OutlinedTextField(h,{h=it},label={Text("Height")},modifier=Modifier.weight(1f))}
        if(!image)OutlinedTextField(size,{size=it},label={Text("Font size (pt)")})
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={
            val a=x.toDoubleOrNull();val b=y.toDoubleOrNull();val c=w.toDoubleOrNull();val d=h.toDoubleOrNull();val fs=size.toDoubleOrNull()
            if(a==null||b==null||c==null||d==null||fs==null||!listOf(a,b,c,d,fs).all {it.isFinite()}||c<=0||d<=0||fs !in 1.0..512.0||(!image&&text.isBlank()))error="Enter valid coordinates, positive dimensions, and text."
            else onApply(text,listOf(a,b,a+c,b+d),fs)
        }) {Text(if(image)"Choose image" else "Add to page")}
    }
}
@Composable fun ExistingTextDialog(lines:List<TextLine>,busy:Boolean,onDismiss:()->Unit,onSave:(TextLine,String)->Unit) {
    var selected by remember {mutableStateOf<TextLine?>(null)}
    var text by remember {mutableStateOf("")}
    Panel("Edit existing PDF text",onDismiss) {
        Text("This changes content-stream text, not a cover rectangle. Unsupported encodings or fonts may prevent faithful replacement.")
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(lines.isEmpty()&&!busy)Text("No editable text lines were extracted on this page.")
        if(selected==null)lines.forEach {l ->
            ListItem(headlineContent={Text(l.text)},supportingContent={if(!l.editable)Text("Undecodable — unavailable")},
                modifier=Modifier.clickable(enabled=l.editable){selected=l;text=l.text})
        }else {
            OutlinedTextField(text,{text=it},label={Text("Replacement")},modifier=Modifier.fillMaxWidth())
            Button(onClick={selected?.let {onSave(it,text)}},enabled=!busy){Text("Replace original text")}
            TextButton(onClick={selected=null}){Text("Back to lines")}
        }
    }
}
@Composable fun FieldEditor(f:FormField,busy:Boolean,onSave:(Any?)->Unit) {
    var text by remember(f.name,f.values) {mutableStateOf(f.values.firstOrNull().orEmpty())}
    var chosen by remember(f.name,f.values) {mutableStateOf(f.values.toSet())}
    var checked by remember(f.name,f.values) {mutableStateOf(f.values.any {it!="Off"})}
    val enabled=!f.readOnly&&!busy
    Text(f.name,style=MaterialTheme.typography.titleMedium)
    if(f.readOnly)Text("Read-only field")
    when(f.kind) {
        "Text" -> {
            OutlinedTextField(text,{v -> text=if(f.maxLength!=null)v.take(f.maxLength) else v},enabled=enabled,modifier=Modifier.fillMaxWidth())
            TextButton(onClick={onSave(text)},enabled=enabled){Text("Apply text")}
        }
        "CheckBox" -> {
            Row {Checkbox(checked,{checked=it},enabled=enabled);Text(if(checked)"Checked" else "Unchecked")}
            TextButton(onClick={onSave(checked)},enabled=enabled){Text("Apply checkbox")}
        }
        "Radio","List","Combo" -> {
            f.options.forEach {o ->
                Row(Modifier.fillMaxWidth().clickable(enabled=enabled) {
                    chosen=if(f.multi) {if(o.value in chosen)chosen-o.value else chosen+o.value} else setOf(o.value)
                }) {
                    if(f.multi)Checkbox(o.value in chosen,null,enabled=enabled)
                    else RadioButton(o.value in chosen,null,enabled=enabled)
                    Text(o.label,Modifier.padding(top=12.dp))
                }
            }
            if(f.editableChoice)OutlinedTextField(text,{text=it},enabled=enabled,label={Text("Or type a custom value")})
            TextButton(onClick={
                onSave(if(f.kind=="Radio")chosen.firstOrNull() else if(f.editableChoice&&text!=f.values.firstOrNull().orEmpty())listOf(text) else chosen.toList())
            },enabled=enabled){Text("Apply selection")}
        }
        else -> Text("${f.kind} fields are not editable in MKpdf.")
    }
    HorizontalDivider(Modifier.padding(vertical=8.dp))
}
fun parsePageRange(text:String):List<Int>? {
    if(text.isBlank()||text.trim().equals("all",true))return null
    val pages=mutableListOf<Int>()
    for(part in text.split(',')) {
        val ends=part.trim().split('-')
        require(ends.size in 1..2){"Use pages such as 1,3-5"}
        val first=ends[0].trim().toInt();val last=if(ends.size==2)ends[1].trim().toInt()else first
        require(first>=1&&last>=first&&last-first<10000){"Invalid page range"}
        pages+=(first..last).map {it-1}
        require(pages.size<=10000){"Too many selected pages"}
    }
    return pages.distinct()
}
```

## app/src/main/java/com/mkpdf/app/EditorViewModel.kt

```kotlin
package com.mkpdf.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

class EditorViewModel(app:Application):AndroidViewModel(app) {
    private val repo=PdfRepository(app)
    private val _ui=MutableStateFlow(UiState())
    val ui=_ui.asStateFlow()
    private var searchJob:Job?=null
    init { perform {repo.run {init()};_ui.update {it.copy(recoveryAvailable=repo.saf.recovery.isFile)}} }
    fun dismissError() {_ui.update {it.copy(error=null)}}
    fun dismissMessage() {_ui.update {it.copy(message=null)}}
    private fun perform(block:suspend ()->Unit) {
        if(_ui.value.busy)return
        viewModelScope.launch {
            _ui.update {it.copy(busy=true,error=null)}
            try {block()} catch(e:CancellationException){throw e}
            catch(e:Exception){_ui.update {it.copy(error=e.message?:"Operation failed")}}
            finally {_ui.update {it.copy(busy=false)}}
        }
    }
    private suspend fun publish(value:JSONObject) {
        val d=value.document()
        _ui.update {it.copy(doc=d,lines=emptyList(),fields=emptyList())}
        try {repo.run {call("autosave",d)}}
        catch(e:Exception) {_ui.update {it.copy(error="Edit succeeded, but local recovery could not be saved: ${e.message}")}}
        search(_ui.value.query)
    }
    private suspend fun replace(value:JSONObject) {
        val old=_ui.value.doc
        searchJob?.cancel()
        publish(value)
        if(old!=null && old.handle!=value.getLong("handle"))repo.run {call("close",old);clearCache()}
    }
    fun newDocument()=perform {replace(repo.run {call("blank")})}
    fun open(uri:Uri)=perform {
        val value=repo.run {
            val (name,file)=saf.stage(uri)
            try {val d=call("open",null,"path" to file.absolutePath,"name" to name);saf.persistRead(uri);d}
            finally {file.delete()}
        }; replace(value)
    }
    fun reopenRecent() {repo.saf.recent()?.let(::open)}
    fun recover()=perform {
        val d=repo.run {call("open",null,"path" to saf.recovery.absolutePath,"name" to "Recovered.pdf")}
        replace(d);_ui.update {it.copy(recoveryAvailable=false)}
    }
    fun edit(op:String,vararg args:Pair<String,Any?>) {
        val d=_ui.value.doc?:return
        searchJob?.cancel()
        perform {publish(repo.run {call(op,d,*args)})}
    }
    fun importPdf(uri:Uri,at:Int,pages:List<Int>?=null) {
        val d=_ui.value.doc?:return
        perform {
            val value=repo.run {
                val (name,file)=saf.stage(uri)
                try {call("insert_pdf",d,"path" to file.absolutePath,"name" to name,"at" to at,"pages" to pages)}
                finally {file.delete()}
            };publish(value)
        }
    }
    fun importImage(uri:Uri,page:Int,rect:List<Double>?,asPage:Boolean) {
        val d=_ui.value.doc?:return
        perform {
            val value=repo.run {
                val (name,file)=saf.stage(uri)
                try {call(if(asPage)"image_page" else "add_image",d,"path" to file.absolutePath,"name" to name,
                    "page" to page,"at" to (page+1),"rect" to rect)} finally {file.delete()}
            };publish(value)
        }
    }
    fun combine(uris:List<Uri>)=perform {
        if(uris.size<2)throw IllegalArgumentException("Select at least two PDFs")
        val value=repo.run {
            val staged=mutableListOf<Pair<String,File>>()
            try {
                uris.forEach {staged+=saf.stage(it)}
                call("combine",null,"inputs" to staged.map {(name,file)->obj("name" to name,"path" to file.absolutePath)})
            } finally {staged.forEach {it.second.delete()}}
        };replace(value)
    }
    fun saveAs(uri:Uri) {
        val d=_ui.value.doc?:return
        perform {
            val value=repo.run {
                val snap=call("snapshot",d)
                saf.write(uri,File(snap.getString("path")))
                call("mark_saved",d)
            };publish(value);_ui.update {it.copy(message="Saved a PDF copy. Original source was not overwritten.")}
        }
    }
    fun splitAll(tree:Uri) {
        val d=_ui.value.doc?:return
        perform {
            var count=0
            try {
                repo.run {
                    for(page in d.pages.indices) {
                        coroutineContext.ensureActive()
                        val out=call("extract",d,"pages" to listOf(page))
                        val dest=saf.child(tree,"${d.name.substringBeforeLast('.')}-${page+1}.pdf")
                        saf.write(dest,File(out.getString("path")));count++
                    }
                }
                _ui.update {it.copy(message="Wrote $count separate PDF pages.")}
            }catch(e:Exception){throw IllegalStateException("Split stopped after $count files: ${e.message}",e)}
        }
    }
    fun loadLines(page:Int) {
        val d=_ui.value.doc?:return
        perform {
            val lines=repo.run {call("lines",d,"page" to page).getJSONArray("lines").objects().map {
                TextLine(it.getInt("index"),it.getString("text"),it.getBoolean("editable"))
            }}
            _ui.update {it.copy(lines=lines)}
        }
    }
    fun loadFields() {
        val d=_ui.value.doc?:return
        perform {val fields=repo.run {call("fields",d).formFields()};_ui.update {it.copy(fields=fields)}}
    }
    fun setField(name:String,value:Any?) {
        val d=_ui.value.doc?:return
        perform {
            val result=repo.run {call("set_field",d,"name" to name,"value" to value)}
            publish(result)
            val fields=repo.run {call("fields",_ui.value.doc).formFields()}
            _ui.update {it.copy(fields=fields)}
        }
    }
    suspend fun render(d:Document,page:Int,width:Int):Bitmap=repo.run {render(d,page,width)}
    fun search(query:String) {
        searchJob?.cancel()
        _ui.update {it.copy(query=query,hits=emptyList(),selectedHit=-1,searching=false)}
        val d=_ui.value.doc?:return
        if(query.isBlank())return
        searchJob=viewModelScope.launch {
            delay(180)
            _ui.update {it.copy(searching=true)}
            try {
                for(page in d.pages.indices) {
                    coroutineContext.ensureActive()
                    val results=repo.run {call("search_page",d,"page" to page,"query" to query).getJSONArray("hits").objects().map {h ->
                        val rs=h.getJSONArray("rects")
                        Hit(h.getInt("page"),h.getInt("start"),(0 until rs.length()).map {rs.getJSONArray(it).rect()})
                    }}
                    coroutineContext.ensureActive()
                    if(_ui.value.doc?.generation!=d.generation || _ui.value.doc?.handle!=d.handle)return@launch
                    _ui.update {it.copy(hits=it.hits+results)}
                    // Let rendering and edits use the worker between page searches.
                    delay(1)
                    if(_ui.value.hits.size>=10_000){_ui.update {it.copy(message="Search stopped at 10,000 matches; refine the query.")};break}
                }
            } catch(e:CancellationException){throw e}
            catch(e:Exception){_ui.update {it.copy(error="Search failed: ${e.message}")}}
            finally {if(_ui.value.query==query)_ui.update {it.copy(searching=false)}}
        }
    }
    fun navigateHit(delta:Int) {
        _ui.update {s -> if(s.hits.isEmpty())s else s.copy(selectedHit=if(s.selectedHit<0)0 else (s.selectedHit+delta+s.hits.size)%s.hits.size)}
    }
    override fun onCleared() {
        val d=_ui.value.doc
        // A separate scope allows native close after viewModelScope has been cancelled.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repo.run {d?.let {call("close",it)};clearCache()}
        }
        super.onCleared()
    }
}
```

## app/src/main/java/com/mkpdf/app/MainActivity.kt

```kotlin
package com.mkpdf.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity() {
    private val vm:EditorViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {MaterialTheme(colorScheme=lightColorScheme(primary=androidx.compose.ui.graphics.Color(0xFF005B50))) {EditorApp(vm)}}
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun EditorApp(vm:EditorViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val doc=ui.doc
    val scope=rememberCoroutineScope()
    val pager=rememberPagerState(pageCount={doc?.pages?.size?:0})
    val page=pager.currentPage.coerceAtMost((doc?.pages?.size?:1)-1).coerceAtLeast(0)
    var menu by remember {mutableStateOf(false)}
    var dialog by remember {mutableStateOf("")}
    var range by remember {mutableStateOf("all")}
    var rangeError by remember {mutableStateOf<String?>(null)}
    var importPages by remember {mutableStateOf<List<Int>?>(null)}
    var importAt by remember {mutableIntStateOf(0)}
    var imageRect by remember {mutableStateOf<List<Double>?>(null)}
    var imageAsPage by remember {mutableStateOf(false)}
    var imagePage by remember {mutableIntStateOf(0)}
    var selectedField by remember {mutableStateOf<String?>(null)}
    var combineUris by remember {mutableStateOf<List<Uri>>(emptyList())}
    var discardAction by remember {mutableStateOf<(() -> Unit)?>(null)}
    fun replaceSafely(action:()->Unit) {if(doc?.dirty==true)discardAction=action else action()}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let(vm::open)}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {it?.let(vm::saveAs)}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let {uri->vm.importPdf(uri,importAt,importPages)}}
    val images=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let {uri->vm.importImage(uri,imagePage,imageRect,imageAsPage)}}
    val combine=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {if(it.size>=2){combineUris=it;dialog="combine"}}
    val split=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {it?.let(vm::splitAll)}
    LaunchedEffect(doc?.handle) {if(doc!=null)pager.scrollToPage(0)}
    LaunchedEffect(ui.selectedHit,ui.hits) {ui.hits.getOrNull(ui.selectedHit)?.let {pager.animateScrollToPage(it.page)}}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            TextButton(onClick={replaceSafely {open.launch(arrayOf("application/pdf"))}},enabled=!ui.busy){Text("Open")}
            TextButton(onClick={replaceSafely {vm.newDocument()}},enabled=!ui.busy){Text("New")}
            TextButton(onClick={save.launch(doc?.name?:"MKpdf.pdf")},enabled=doc!=null&&!ui.busy){Text("Save as")}
            TextButton(onClick={vm.edit("undo")},enabled=doc?.undo==true&&!ui.busy){Text("Undo")}
            TextButton(onClick={vm.edit("redo")},enabled=doc?.redo==true&&!ui.busy){Text("Redo")}
            Box {
                TextButton(onClick={menu=true},enabled=!ui.busy){Text("Tools")}
                DropdownMenu(menu,{menu=false}) {
                    fun close(action:()->Unit) {menu=false;action()}
                    DropdownMenuItem(text={Text("Reopen recent PDF")},onClick={close {replaceSafely {vm.reopenRecent()}}})
                    DropdownMenuItem(text={Text("Combine PDF files…")},onClick={close {replaceSafely {combine.launch(arrayOf("application/pdf"))}}})
                    if(doc!=null) {
                        DropdownMenuItem(text={Text("Move page earlier")},enabled=page>0,onClick={close {vm.edit("move","page" to page,"to" to page-1)}})
                        DropdownMenuItem(text={Text("Move page later")},enabled=page<doc.pages.lastIndex,onClick={close {vm.edit("move","page" to page,"to" to page+1)}})
                        DropdownMenuItem(text={Text("Delete current page…")},onClick={close {dialog="delete"}})
                        DropdownMenuItem(text={Text("Insert blank after page")},onClick={close {vm.edit("insert_blank","at" to page+1)}})
                        DropdownMenuItem(text={Text("Insert pages from PDF…")},onClick={close {range="all";rangeError=null;dialog="import"}})
                        for(degrees in listOf(90,180,270))DropdownMenuItem(text={Text("Rotate page $degrees°")},onClick={close {vm.edit("rotate","page" to page,"degrees" to degrees)}})
                        DropdownMenuItem(text={Text("Add text box…")},onClick={close {dialog="text"}})
                        DropdownMenuItem(text={Text("Edit existing text…")},onClick={close {vm.loadLines(page);dialog="lines"}})
                        DropdownMenuItem(text={Text("Place image on page…")},onClick={close {dialog="image"}})
                        DropdownMenuItem(text={Text("Insert image as new page…")},onClick={close {imagePage=page;imageAsPage=true;imageRect=null;images.launch(arrayOf("image/png","image/jpeg"))}})
                        DropdownMenuItem(text={Text("Fill form fields…")},onClick={close {selectedField=null;vm.loadFields();dialog="fields"}})
                        DropdownMenuItem(text={Text("Split into individual PDFs…")},onClick={close {dialog="split"}})
                    }
                }
            }
        }
        Text("MKpdf · ${doc?.name?:"local PDF editor"}${if(doc?.dirty==true)" *" else ""}",Modifier.padding(horizontal=12.dp),maxLines=1)
        if(ui.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(ui.recoveryAvailable)Row {
            TextButton(onClick={replaceSafely {vm.recover()}},enabled=!ui.busy){Text("Recover last local working PDF")}
        }
        if(doc==null) {
            Column(Modifier.weight(1f).padding(24.dp)) {
                Text("Open a PDF or create a blank document.",style=MaterialTheme.typography.titleLarge)
                Text("Files stay on this device. Use Save as to write your edited PDF through Android's file picker.")
            }
        } else {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(ui.query,{vm.search(it)},label={Text("Search text")},singleLine=true,modifier=Modifier.weight(1f),enabled=!ui.busy)
                TextButton(onClick={vm.navigateHit(-1)},enabled=ui.hits.isNotEmpty()){Text("‹")}
                TextButton(onClick={vm.navigateHit(1)},enabled=ui.hits.isNotEmpty()){Text("›")}
            }
            if(ui.query.isNotBlank())Text("${if(ui.selectedHit>=0)ui.selectedHit+1 else 0} / ${ui.hits.size} matches${if(ui.searching)" · searching…" else ""}",Modifier.padding(horizontal=12.dp))
            Box(Modifier.weight(1f)) {
                HorizontalPager(pager,beyondBoundsPageCount=0,key={"${doc.handle}:$it"},userScrollEnabled=!ui.busy) {index ->
                    PageViewer(doc,index,ui.hits,ui.hits.getOrNull(ui.selectedHit),ui.fields,vm) {f ->selectedField=f.name;dialog="fields"}
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={scope.launch {pager.animateScrollToPage((page-1).coerceAtLeast(0))}},enabled=page>0&&!ui.busy){Text("Previous")}
                Text("${page+1} / ${doc.pages.size}",Modifier.padding(top=12.dp))
                TextButton(onClick={scope.launch {pager.animateScrollToPage((page+1).coerceAtMost(doc.pages.lastIndex))}},enabled=page<doc.pages.lastIndex&&!ui.busy){Text("Next")}
            }
            Text("Pinch to zoom · double-tap to reset/zoom",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.labelSmall)
            if(doc.notices.isNotEmpty())Text(doc.notices.joinToString("\n"),Modifier.heightIn(max=80.dp).padding(8.dp),color=MaterialTheme.colorScheme.error)
        }
    }
    if(discardAction!=null)AlertDialog(onDismissRequest={discardAction=null},title={Text("Replace the working document?")},
        text={Text("Save a copy first if you need these edits. Continuing replaces the single local recovery slot.")},
        confirmButton={TextButton(onClick={val action=discardAction;discardAction=null;action?.invoke()}){Text("Continue")}},
        dismissButton={TextButton(onClick={discardAction=null}){Text("Cancel")}})
    ui.error?.let {AlertDialog(onDismissRequest=vm::dismissError,title={Text("MKpdf")},text={Text(it)},confirmButton={TextButton(onClick=vm::dismissError){Text("OK")}})}
    ui.message?.let {AlertDialog(onDismissRequest=vm::dismissMessage,title={Text("MKpdf")},text={Text(it)},confirmButton={TextButton(onClick=vm::dismissMessage){Text("OK")}})}
    when(dialog) {
        "delete" -> AlertDialog(onDismissRequest={dialog=""},title={Text("Delete page ${page+1}?")},text={Text("Undo is available. The final page cannot be deleted.")},
            confirmButton={TextButton(onClick={dialog="";vm.edit("delete","page" to page)}){Text("Delete")}},dismissButton={TextButton(onClick={dialog=""}){Text("Cancel")}})
        "text","image" -> PlacementDialog(dialog=="image",{dialog=""}) {text,rect,size ->
            if(dialog=="text")vm.edit("add_text","page" to page,"text" to text,"rect" to rect,"size" to size)
            else {imagePage=page;imageRect=rect;imageAsPage=false;images.launch(arrayOf("image/png","image/jpeg"))}
            dialog=""
        }
        "lines" -> ExistingTextDialog(ui.lines,ui.busy,{dialog=""}) {line,text->vm.edit("edit_line","page" to page,"line" to line.index,"text" to text);dialog=""}
        "fields" -> Panel("Fill AcroForm fields",{dialog=""}) {
            if(ui.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(ui.fields.isEmpty()&&!ui.busy)Text("No AcroForm fields found. This is not OCR.")
            val fields=ui.fields.sortedBy {if(it.name==selectedField)0 else 1}
            fields.forEach {f->key(f.name) {FieldEditor(f,ui.busy) {vm.setField(f.name,it)}}}
        }
        "import" -> AlertDialog(onDismissRequest={dialog=""},title={Text("Import pages after page ${page+1}")},
            text={Column {Text("Source pages: all, or 1,3-5. Imported form name collisions are renamed.")
                OutlinedTextField(range,{range=it},label={Text("Pages")});rangeError?.let {Text(it)}}},
            confirmButton={TextButton(onClick={try {importPages=parsePageRange(range);importAt=page+1;dialog="";import.launch(arrayOf("application/pdf"))}catch(e:Exception){rangeError=e.message}}){Text("Choose PDF")}},
            dismissButton={TextButton(onClick={dialog=""}){Text("Cancel")}})
        "split" -> AlertDialog(onDismissRequest={dialog=""},title={Text("Split into individual PDF pages?")},
            text={Text("Choose an output folder. Each page becomes its own PDF. If a provider fails, already-written files remain; the original is unchanged.")},
            confirmButton={TextButton(onClick={dialog="";split.launch(null)}){Text("Choose folder")}},dismissButton={TextButton(onClick={dialog=""}){Text("Cancel")}})
        "combine" -> Panel("Combine in this order",{dialog=""}) {
            combineUris.forEachIndexed {i,uri ->
                Text("${i+1}. ${uri.lastPathSegment?:uri}")
                Row {
                    TextButton(onClick={val a=combineUris.toMutableList();val x=a.removeAt(i);a.add(i-1,x);combineUris=a},enabled=i>0){Text("Earlier")}
                    TextButton(onClick={val a=combineUris.toMutableList();val x=a.removeAt(i);a.add(i+1,x);combineUris=a},enabled=i<combineUris.lastIndex){Text("Later")}
                }
            }
            Button(onClick={vm.combine(combineUris);dialog=""},enabled=!ui.busy){Text("Combine PDFs")}
        }
    }
}
```

## app/src/main/java/com/mkpdf/app/Models.kt

```kotlin
package com.mkpdf.app

import org.json.JSONArray
import org.json.JSONObject

data class Page(val width: Float, val height: Float, val rotation: Int, val label: String)
data class Document(val handle: Long, val name: String, val generation: Long, val dirty: Boolean,
    val undo: Boolean, val redo: Boolean, val pages: List<Page>, val notices: List<String>)
data class PdfRect(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class Hit(val page: Int, val start: Int, val rects: List<PdfRect>)
data class TextLine(val index: Int, val text: String, val editable: Boolean)
data class Option(val value: String, val label: String)
data class Widget(val page: Int, val rect: PdfRect, val hidden: Boolean)
data class FormField(val name: String, val kind: String, val values: List<String>, val options: List<Option>,
    val readOnly: Boolean, val multi: Boolean, val editableChoice: Boolean, val maxLength: Int?, val widgets: List<Widget>)
data class UiState(val doc: Document? = null, val busy: Boolean = false, val error: String? = null,
    val message: String? = null, val query: String = "", val hits: List<Hit> = emptyList(),
    val searching: Boolean = false, val selectedHit: Int = -1, val fields: List<FormField> = emptyList(),
    val lines: List<TextLine> = emptyList(), val recoveryAvailable: Boolean = false)
fun obj(vararg values: Pair<String, Any?>) = JSONObject().apply {
    values.forEach { (k,v) -> put(k, when(v) { null -> JSONObject.NULL; is List<*> -> JSONArray(v); else -> v }) }
}
fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings() = (0 until length()).map { getString(it) }
fun JSONArray.rect() = PdfRect(getDouble(0).toFloat(),getDouble(1).toFloat(),getDouble(2).toFloat(),getDouble(3).toFloat())
fun JSONObject.document() = Document(getLong("handle"),getString("name"),getLong("generation"),getBoolean("dirty"),
    getBoolean("undo"),getBoolean("redo"),getJSONArray("pages").objects().map {
        Page(it.getDouble("width").toFloat(),it.getDouble("height").toFloat(),it.getInt("rotation"),it.getString("label"))
    }, getJSONArray("notices").strings() + getJSONArray("warnings").strings())
fun JSONObject.formFields() = getJSONArray("fields").objects().map { f ->
    FormField(f.getString("name"),f.getString("kind"),f.getJSONArray("values").strings(),
        f.getJSONArray("options").objects().map { Option(it.getString("value"),it.getString("label")) }.distinctBy { it.value },
        f.getBoolean("readonly"),f.getBoolean("multi"),f.getBoolean("editableChoice"),
        if(f.isNull("maxLength")) null else f.getInt("maxLength"),
        f.getJSONArray("widgets").objects().filter { !it.isNull("page") && !it.isNull("rect") }.map {
            Widget(it.getInt("page"),it.getJSONArray("rect").rect(),it.getBoolean("hidden"))
        })
}
```

## app/src/main/java/com/mkpdf/app/PageViewer.kt

```kotlin
package com.mkpdf.app

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageViewer(doc:Document,pageIndex:Int,hits:List<Hit>,active:Hit?,fields:List<FormField>,vm:EditorViewModel,onField:(FormField)->Unit) {
    val page=doc.pages[pageIndex]
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    var zoom by remember(doc.handle,doc.generation,pageIndex) {mutableFloatStateOf(1f)}
    var pan by remember(doc.handle,doc.generation,pageIndex) {mutableStateOf(Offset.Zero)}
    val fit=if(viewport.width==0)1f else min(viewport.width/page.width,viewport.height/page.height)
    val pw=page.width*fit;val ph=page.height*fit
    fun bound(offset:Offset,z:Float):Offset {
        val x=max(0f,(pw*z-viewport.width)/2);val y=max(0f,(ph*z-viewport.height)/2)
        return Offset(offset.x.coerceIn(-x,x),offset.y.coerceIn(-y,y))
    }
    val transform=rememberTransformableState {factor,delta,_ ->
        val next=(zoom*factor).coerceIn(1f,6f)
        pan=bound(pan*(next/zoom)+delta,next);zoom=next
    }
    var bitmap by remember(doc.handle,doc.generation,pageIndex) {mutableStateOf<Bitmap?>(null)}
    var error by remember(doc.handle,doc.generation,pageIndex) {mutableStateOf<String?>(null)}
    val desired=(pw*zoom).toInt().coerceIn(64,4096)
    LaunchedEffect(doc.handle,doc.generation,pageIndex,desired) {
        if(viewport.width==0)return@LaunchedEffect
        if(bitmap!=null)delay(180) // scale immediately, refresh detail after a pinch settles
        try {bitmap=vm.render(doc,pageIndex,desired);error=null}
        catch(e:CancellationException){throw e}
        catch(e:Exception){error=e.message}
    }
    // Search navigation brings the match into the visible area at fit scale.
    LaunchedEffect(active?.start,active?.page) {if(active?.page==pageIndex){zoom=1f;pan=Offset.Zero}}
    val density=LocalDensity.current
    Box(Modifier.fillMaxSize().background(Color(0xFFE3E8E6)).onSizeChanged {viewport=it}
        .transformable(transform,canPan={zoom>1.01f}),contentAlignment=Alignment.Center) {
        if(viewport.width>0) {
            Box(Modifier.size(with(density){pw.toDp()},with(density){ph.toDp()})
                .graphicsLayer {scaleX=zoom;scaleY=zoom;translationX=pan.x;translationY=pan.y}
                .background(Color.White)
                .pointerInput(doc.handle,doc.generation,pageIndex,fields) {
                    detectTapGestures(onDoubleTap={zoom=if(zoom>1.1f)1f else 2f;pan=Offset.Zero},onTap={pos ->
                        val x=pos.x/size.width*page.width;val y=pos.y/size.height*page.height
                        fields.firstOrNull {f -> f.widgets.any {w-> !w.hidden && w.page==pageIndex &&
                            x>=w.rect.left && x<=w.rect.right && y>=w.rect.top && y<=w.rect.bottom }}?.let(onField)
                    })
                }) {
                bitmap?.let {Image(it.asImageBitmap(),"PDF page ${pageIndex+1}",Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)}
                Canvas(Modifier.fillMaxSize()) {
                    val sx=size.width/page.width;val sy=size.height/page.height
                    fun paint(r:PdfRect,color:Color) {drawRect(color,Offset(r.left*sx,r.top*sy),Size((r.right-r.left)*sx,(r.bottom-r.top)*sy))}
                    hits.filter {it.page==pageIndex}.forEach {h ->h.rects.forEach {paint(it,if(h==active)Color(0x88FF8800)else Color(0x66FFE500))}}
                    fields.flatMap {it.widgets}.filter {it.page==pageIndex && !it.hidden}.forEach {paint(it.rect,Color(0x18008877))}
                }
            }
        }
        if(bitmap==null || error!=null)Text(error?:"Rendering…",color=if(error!=null)Color.Red else Color.DarkGray)
    }
}
```

## app/src/main/java/com/mkpdf/app/PdfRepository.kt

```kotlin
package com.mkpdf.app

import android.app.Application
import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import org.json.JSONObject

/** JNI TLS owns a non-Send Session. The executor must remain alive for the process lifetime. */
private object NativeWorker {
    val dispatcher=Executors.newSingleThreadExecutor { r -> Thread(r,"MKpdf-Rust") }.asCoroutineDispatcher()
    val gate=Mutex()
}
class PdfRepository(application: Application) {
    val saf=SafStore(application)
    private val cache=object:LruCache<String,Bitmap>(32*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
    }
    suspend fun <T> run(block:suspend PdfRepository.()->T):T = withContext(NativeWorker.dispatcher) {
        NativeWorker.gate.withLock { block() }
    }
    fun call(op:String, doc:Document?=null, vararg args:Pair<String,Any?>):JSONObject {
        val request=obj("op" to op,*args)
        doc?.let {request.put("handle",it.handle);request.put("generation",it.generation)}
        return RustBridge.call(request)
    }
    fun init() {call("init",null,"root" to saf.root.absolutePath);saf.cleanInputs()}
    fun render(doc:Document,page:Int,width:Int):Bitmap {
        val key="${doc.handle}:${doc.generation}:$page:$width"
        return cache.get(key) ?: RustBridge.render(doc,page,width).also {cache.put(key,it)}
    }
    fun clearCache() = cache.evictAll()
}
```

## app/src/main/java/com/mkpdf/app/RustBridge.kt

```kotlin
package com.mkpdf.app

import android.graphics.Bitmap
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject

internal object RustBridge {
    init { System.loadLibrary("mkpdf_jni") }
    private var owner: Long? = null
    private fun confined() {
        val thread = Thread.currentThread().id
        check(owner == null || owner == thread) { "JNI must stay on the persistent MKpdf worker" }
        owner = thread
    }
    @JvmStatic private external fun nativeCall(request: String): String?
    @JvmStatic private external fun nativeRender(handle: Long, page: Int, width: Int, generation: Long): ByteArray?
    fun call(request: JSONObject): JSONObject {
        confined()
        val envelope = JSONObject(nativeCall(request.toString()) ?: throw IOException("Empty JNI reply"))
        if (!envelope.getBoolean("ok")) throw IOException(envelope.optString("error", "Native operation failed"))
        return envelope.getJSONObject("data")
    }
    fun render(doc: Document, page: Int, width: Int): Bitmap {
        confined()
        val bytes = nativeRender(doc.handle,page,width,doc.generation) ?: throw IOException("Empty native raster")
        if (bytes.size < 8) throw IOException("Truncated raster header")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val w = buffer.int; val h = buffer.int
        if (w <= 0 || h <= 0 || w.toLong()*h > 8_100_000 || 8L+w.toLong()*h*4 != bytes.size.toLong())
            throw IOException("Invalid raster dimensions")
        // Engine pixels are premultiplied RGBA8, matching Android ARGB_8888's native byte layout.
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also {
            it.setPremultiplied(true); it.copyPixelsFromBuffer(buffer.slice())
        }
    }
}
```

## app/src/main/java/com/mkpdf/app/SafStore.kt

```kotlin
package com.mkpdf.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID

class SafStore(private val context: Context) {
    val root = File(context.filesDir,"workspace").apply { mkdirs() }
    val recovery get() = File(root,"recovery.pdf")
    private val resolver get() = context.contentResolver
    private val prefs = context.getSharedPreferences("documents",Context.MODE_PRIVATE)
    fun persistRead(uri: Uri) {
        // OpenDocument grants read access. Some providers do not support persistence.
        try { resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { /* This provider remains usable for this session. */ }
        val previous=prefs.getString("recent",null)?.let(Uri::parse)
        if(previous!=null && previous!=uri) {
            try {resolver.releasePersistableUriPermission(previous,Intent.FLAG_GRANT_READ_URI_PERMISSION)} catch (_:SecurityException) {}
        }
        prefs.edit().putString("recent",uri.toString()).apply()
    }
    fun recent(): Uri? = prefs.getString("recent",null)?.let(Uri::parse)
    fun name(uri: Uri): String = resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c ->
        if(c.moveToFirst()) c.getString(0) else null
    } ?: "Document.pdf"
    fun stage(uri: Uri): Pair<String,File> {
        val name=name(uri);val file=File(root,"input-${UUID.randomUUID()}.bin")
        try {
            val input=resolver.openInputStream(uri) ?: throw IOException("Cannot open selected document")
            input.use { src -> file.outputStream().use { dst ->
                val buf=ByteArray(64*1024);var count=0L
                while(true) {val n=src.read(buf);if(n<0)break; count+=n
                    if(count>128L*1024*1024) throw IOException("Input exceeds 128 MiB")
                    dst.write(buf,0,n)
                }
            } }
            return name to file
        } catch(e:Exception) {file.delete();throw e}
    }
    fun write(uri: Uri, file: File) {
        // SAF cannot promise atomic replacement across providers. Save As preserves the source.
        val output=resolver.openOutputStream(uri,"wt") ?: throw IOException("Cannot write destination")
        output.use { dst -> file.inputStream().use { it.copyTo(dst,64*1024) }; dst.flush() }
    }
    fun child(tree: Uri, name: String): Uri {
        val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(resolver,parent,"application/pdf",name)
            ?: throw IOException("Provider could not create $name")
    }
    fun cleanInputs() {root.listFiles()?.filter { it.name.startsWith("input-") }?.forEach {it.delete()} }
}
```

## app/src/test/java/com/mkpdf/app/PageRangesTest.kt

```kotlin
package com.mkpdf.app
import org.junit.Assert.*
import org.junit.Test
class PageRangesTest {
    @Test fun indexesAreZeroBased() {assertEquals(listOf(0,2,3,4),parsePageRange("1,3-5"))}
    @Test fun allIsNull() {assertNull(parsePageRange("all"));assertNull(parsePageRange(""))}
    @Test(expected=IllegalArgumentException::class) fun rejectsZero() {parsePageRange("0")}
    @Test(expected=IllegalArgumentException::class) fun rejectsReverse() {parsePageRange("4-2")}
}
```

## docs/TEST_PLAN.md

```markdown
# Acceptance test plan

## Automated host engine tests

Run `cargo test --manifest-path native/Cargo.toml --lib --locked` after bootstrap.
The native tests cover:

- actual blank-document creation, text insertion, search boxes, rendering,
  page insertion/rotation/reordering/deletion, valid save output;
- rejection of deleting the last page, invalid rectangles, paths outside the
  workspace, and excluded operation names;
- filling and reopening text, checkbox, radio, combo and multi-select list fields;
- actual existing-text replacement and searching for old/new text;
- selected-page import, file combine, page extraction, image insertion and image pages.

Host tests use unwrap/assert inside test functions only, so failures are visible.
They are NOT Android tests and do not validate NDK linking or gesture behavior.

## Android tests

Run `sh gradlew :app:testDebugUnitTest` for range parser tests.
Run `sh gradlew :app:connectedDebugAndroidTest` on each required ABI.
The included instrumented JNI smoke test creates a PDF, changes its content,
renders, searches, and saves through the real JNI library.

## Manual device matrix

Test API 26, API 34, a recent Android device, and a 16 KiB page-size emulator where
available. Cover arm64-v8a, armeabi-v7a, x86 and x86_64 separately. Check each APK
contains lib/<abi>/libmkpdf_jni.so. Check ELF load segment alignment is at least
0x4000 and check zip alignment with Android build-tools zipalign.

- Open PDFs from local DocumentsUI providers, including a read-only provider;
  rotate the device and force-stop/relaunch. Reopen recent and recover local work.
- Deny/revoke a persisted grant, remove a recent source, cancel each picker.
  Errors must be visible and existing edits must survive.
- Swipe both ways; pinch to 6x, pan to all corners, double-tap, then swipe at fit
  scale. Search highlights must remain aligned under rotation, crop, and zoom.
- Move first/last/middle pages, delete all but one, rotate by all three angles,
  insert/import at several locations and check the result in another PDF reader.
- Combine PDFs with colliding field names and inspect the result's forms.
- Split a multi-page PDF into a folder, verify output count and page contents;
  simulate a failing output provider and check the reported completed count.
- Add text; edit an original text line; add/scale images; insert image pages.
  Save/reopen in an independent viewer and verify extracted text, not just pixels.
- Fill text, checkboxes, radio groups, combo boxes, and multi-select list boxes;
  save and reopen in an independent viewer to verify values and appearances.
- Search Latin, ligatures, mixed RTL, CJK, vertical text, rotated pages and
  multi-column/tagged PDFs. Record layout/semantic-order deviations, not a false
  “passed” assertion. Image-only PDFs should return no text without OCR.
- Test font substitutions, unsupported encodings and malformed files.
- Test full storage, failed SAF output, oversized input, out-of-memory pressure,
  very long files, search cancellation and opening a different document.

No excluded tool should appear in the UI or be accepted by the JNI protocol.
```

## gradle/wrapper/gradle-wrapper.properties

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
distributionSha256Sum=d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab
```

## gradlew

```sh
#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
#

##############################################################################
#
#   Gradle start up script for POSIX generated by Gradle.
#
#   Important for running:
#
#   (1) You need a POSIX-compliant shell to run this script. If your /bin/sh is
#       noncompliant, but you have some other compliant shell such as ksh or
#       bash, then to run this script, type that shell name before the whole
#       command line, like:
#
#           ksh Gradle
#
#       Busybox and similar reduced shells will NOT work, because this script
#       requires all of these POSIX shell features:
#         * functions;
#         * expansions «$var», «${var}», «${var:-default}», «${var+SET}»,
#           «${var#prefix}», «${var%suffix}», and «$( cmd )»;
#         * compound commands having a testable exit status, especially «case»;
#         * various built-in commands including «command», «set», and «ulimit».
#
#   Important for patching:
#
#   (2) This script targets any POSIX shell, so it avoids extensions provided
#       by Bash, Ksh, etc; in particular arrays are avoided.
#
#       The "traditional" practice of packing multiple parameters into a
#       space-separated string is a well documented source of bugs and security
#       problems, so this is (mostly) avoided, by progressively accumulating
#       options in "$@", and eventually passing that to Java.
#
#       Where the inherited environment variables (DEFAULT_JVM_OPTS, JAVA_OPTS,
#       and GRADLE_OPTS) rely on word-splitting, this is performed explicitly;
#       see the in-line comments for details.
#
#       There are tweaks for specific operating systems such as AIX, CygWin,
#       Darwin, MinGW, and NonStop.
#
#   (3) This script is generated from the Groovy template
#       https://github.com/gradle/gradle/blob/HEAD/platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
#       within the Gradle project.
#
#       You can find Gradle at https://github.com/gradle/gradle/.
#
##############################################################################

# Attempt to set APP_HOME

# Resolve links: $0 may be a link
app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# This is normally unused
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
# Discard cd standard output in case $CDPATH is set (https://github.com/gradle/gradle/issues/25036)
APP_HOME=$( cd -P "${APP_HOME:-./}" > /dev/null && printf '%s
' "$PWD" ) || exit

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar


# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # In POSIX sh, ulimit -H is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "Could not query maximum file descriptor limit"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        ulimit -n "$MAX_FD" ||
            warn "Could not set maximum file descriptor limit to $MAX_FD"
    esac
fi

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

# For Cygwin or MSYS, switch paths to Windows format before running java
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )
    CLASSPATH=$( cygpath --path --mixed "$CLASSPATH" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # don't mess with options #(
              /?*)  t=${arg#/} t=/${t%%/*}              # looks like a POSIX filepath
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # Roll the args list around exactly as many times as the number of
        # args, so each arg winds up back in the position where it started, but
        # possibly modified.
        #
        # NB: a `for` loop captures its iteration list before it begins, so
        # changing the positional parameters here affects neither the number of
        # iterations, nor the values presented in `arg`.
        shift                   # remove old arg
        set -- "$@" "$arg"      # push replacement arg
    done
fi


# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='-Dfile.encoding=UTF-8 "-Xmx64m" "-Xms64m"'

# Collect all arguments for the java command:
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, JAVA_OPTS, and optsEnvironmentVar are not allowed to contain shell fragments,
#     and any embedded shellness will be escaped.
#   * For example: A user cannot expect ${Hostname} to be expanded, as it is an environment variable and will be
#     treated as '${Hostname}' itself on the command line.

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
fi

# Use "xargs" to parse quoted args.
#
# With -n1 it outputs one arg per line, with the quotes and backslashes removed.
#
# In Bash we could simply go:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# but POSIX shell has neither arrays nor command substitution, so instead we
# post-process each arg (as a line of input to sed) to backslash-escape any
# character that might be a shell metacharacter, then use eval to reverse
# that process (while maintaining the separation between arguments), and wrap
# the whole thing up as a single "set" statement.
#
# This will of course break if any of these variables contains a newline or
# an unmatched quote.
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
```

## gradlew.bat

```bat
@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=-Dfile.encoding=UTF-8 "-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
```
