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
