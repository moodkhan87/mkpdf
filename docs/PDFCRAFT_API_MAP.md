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
