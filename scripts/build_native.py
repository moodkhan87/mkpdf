#!/usr/bin/env python3
import argparse, os, pathlib, shutil, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument("--ndk", required=True)
p.add_argument("--output", required=True)
a = p.parse_args()

# Bootstrap PdfCraft
subprocess.run([sys.executable, str(ROOT / "scripts/bootstrap.py")], check=True)

if not pathlib.Path(a.ndk, "source.properties").is_file():
    raise SystemExit("Install the configured Android NDK in SDK Manager")

env = dict(os.environ, ANDROID_NDK_HOME=a.ndk, ANDROID_NDK_ROOT=a.ndk)
env["CARGO_ENCODED_RUSTFLAGS"] = "\x1f".join([
    "-C", "link-arg=-Wl,-z,max-page-size=16384",
    "-C", "link-arg=-Wl,-z,common-page-size=16384"
])

cargo = shutil.which("cargo") or str(pathlib.Path.home() / ".cargo/bin/cargo")
manifest_abs = str((ROOT / "native" / "Cargo.toml").resolve())
out_abs = str(pathlib.Path(a.output).resolve())

print(f"[Rust] Manifest: {manifest_abs}")
print(f"[Rust] Output:   {out_abs}")
print(f"[Rust] CWD:      {ROOT}")

# Verify manifest is readable by attempting to open it
try:
    with open(manifest_abs, 'r') as f:
        first_line = f.readline().strip()
    print(f"[Rust] Manifest first line: {first_line}")
except Exception as e:
    raise SystemExit(f"Cannot read manifest: {e}")

cmd = [
    cargo, "ndk",
    "--platform", "26",
    "-o", out_abs,
    "-t", "arm64-v8a",
    "-t", "armeabi-v7a",
    "-t", "x86",
    "-t", "x86_64",
    "build",
    "--manifest-path", manifest_abs,
    "--release"
]

print(f"[Rust] Command: {' '.join(cmd)}")

result = subprocess.run(cmd, cwd=str(ROOT), env=env, capture_output=True, text=True)
print(result.stdout)
if result.stderr:
    print(result.stderr, file=sys.stderr)

if result.returncode != 0:
    raise SystemExit(f"cargo-ndk failed with exit code {result.returncode}")

for abi in ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"]:
    lib_path = pathlib.Path(out_abs, abi, "libmkpdf_jni.so")
    if not lib_path.is_file():
        raise SystemExit(f"Missing ABI library: {lib_path}")

print("[Rust] Build successful")
