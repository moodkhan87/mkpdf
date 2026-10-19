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

manifest_abs = str((ROOT / "native" / "Cargo.toml").resolve())
out_abs = pathlib.Path(a.output).resolve()
out_abs.mkdir(parents=True, exist_ok=True)

targets = {
    "arm64-v8a": "aarch64-linux-android",
    "armeabi-v7a": "armv7-linux-androideabi",
    "x86": "i686-linux-android",
    "x86_64": "x86_64-linux-android",
}

for abi, target in targets.items():
    print(f"[Rust] Building {abi} ({target})...")
    cmd = [
        "cargo", "build",
        "--manifest-path", manifest_abs,
        "--target", target,
        "--release",
        "--lib"
    ]
    result = subprocess.run(cmd, cwd=str(ROOT), env=env, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout)
        print(result.stderr, file=sys.stderr)
        raise SystemExit(f"Failed to build {abi}")

    # Copy .so to output directory
    src = ROOT / "native" / "target" / target / "release" / "libmkpdf_jni.so"
    dst = out_abs / abi / "libmkpdf_jni.so"
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    print(f"[Rust] Copied {src.name} -> {dst}")

print("[Rust] Build successful")
