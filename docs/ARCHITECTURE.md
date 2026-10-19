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
