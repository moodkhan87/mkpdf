#!/usr/bin/env python3
import argparse, os, pathlib, shutil, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]

p = argparse.ArgumentParser()
p.add_argument("--ndk", required=True)
p.add_argument("--output", required=True)
a = p.parse_args()

# 1. Bootstrap PdfCraft vendor directory
subprocess.run([sys.executable, str(ROOT / "scripts/bootstrap.py")], check=True)

# 2. Validate NDK
if not pathlib.Path(a.ndk, "source.properties").is_file(): 
    raise SystemExit("Install the configured Android NDK in SDK Manager")

# 3. Ensure output directory exists
out_path = pathlib.Path(a.output).resolve()
out_path.mkdir(parents=True, exist_ok=True)

# 4. Convert manifest path to ABSOLUTE string to fix CI resolution issues
manifest_path = str((ROOT / "native" / "Cargo.toml").resolve())

env = dict(os.environ, ANDROID_NDK_HOME=a.ndk, ANDROID_NDK_ROOT=a.ndk)
env["CARGO_ENCODED_RUSTFLAGS"] = "\x1f".join([
    "-C", "link-arg=-Wl,-z,max-page-size=16384", 
    "-C", "link-arg=-Wl,-z,common-page-size=16384"
])

cargo = shutil.which("cargo") or str(pathlib.Path.home() / ".cargo/bin/cargo")

# 5. Build command with absolute paths
cmd = [
    cargo, "ndk", 
    "--platform", "26", 
    "-o", str(out_path)
]

for abi in ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"]: 
    cmd += ["-t", abi]

cmd += [
    "build", 
    "--manifest-path", manifest_path,  # <-- CRITICAL FIX: Absolute path
    "--release"
]

print(f"[Rust] Building with manifest: {manifest_path}")
print(f"[Rust] Working directory: {ROOT}")
# ADD THESE LINES RIGHT BEFORE subprocess.run(cmd, ...)
import stat
manifest = pathlib.Path(manifest_path)
print(f"[DIAG] Manifest exists: {manifest.exists()}")
print(f"[DIAG] Manifest is file: {manifest.is_file()}")
print(f"[DIAG] Manifest permissions: {oct(manifest.stat().st_mode)}")
print(f"[DIAG] Manifest owner UID/GID: {manifest.stat().st_uid}/{manifest.stat().st_gid}")
print(f"[DIAG] Parent dir writable: {pathlib.access(str(manifest.parent), os.W_OK)}")
print(f"[DIAG] Current user: {os.getuid()}")
# List native directory contents
for item in (ROOT / "native").iterdir():
    print(f"[DIAG] Native item: {item.name} (is_file={item.is_file()}, is_symlink={item.is_symlink()})")
    
# 6. Execute with explicit cwd
subprocess.run(cmd, cwd=str(ROOT), env=env, check=True)

# 7. Verification
for abi in ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"]:
    lib_path = out_path / abi / "libmkpdf_jni.so"
    if not lib_path.is_file(): 
        raise SystemExit(f"Missing ABI library: {lib_path}")
