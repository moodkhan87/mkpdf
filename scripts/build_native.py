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
cmd += ["build", "--manifest-path", "native/Cargo.toml", "--release"]
subprocess.run(cmd, cwd=ROOT, env=env, check=True)
for abi in ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"]:
    if not pathlib.Path(a.output, abi, "libmkpdf_jni.so").is_file(): raise SystemExit("Missing ABI: " + abi)
