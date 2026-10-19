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
