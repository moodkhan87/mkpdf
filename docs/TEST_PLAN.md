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
