#!/usr/bin/env python3
import argparse, os, pathlib, shutil, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument("--ndk", required=True)
p.add_argument("--output", required=True)
a = p.parse_args()

# Bootstrap PdfCraft
subprocess.run([sys.executable, str(ROOT / "scripts/bootstrap.py")], check=True)

ndk_path = pathlib.Path(a.ndk)
if not (ndk_path / "source.properties").is_file():
    raise SystemExit(f"NDK source.properties not found at {ndk_path}")

# Dynamically find the LLVM toolchain bin directory
# NDK layouts vary: some use linux-x86_64, others may differ
llvm_bin = None
for candidate in [
    ndk_path / "toolchains" / "llvm" / "prebuilt" / "linux-x86_64" / "bin",
    ndk_path / "toolchains" / "llvm" / "prebuilt" / "linux-aarch64" / "bin",
]:
    if candidate.is_dir():
        llvm_bin = candidate
        break

if llvm_bin is None:
    # Last resort: search recursively for aarch64-linux-android-clang
    clang_candidates = list(ndk_path.rglob("aarch64-linux-android-clang"))
    if clang_candidates:
        llvm_bin = clang_candidates[0].parent
    else:
        raise SystemExit(
            f"Could not find NDK LLVM toolchain in {ndk_path}. "
            f"Contents of toolchains dir: {list((ndk_path / 'toolchains').iterdir()) if (ndk_path / 'toolchains').exists() else 'N/A'}"
        )

print(f"[Rust] Using NDK toolchain: {llvm_bin}")

env = dict(os.environ)
env["ANDROID_NDK_HOME"] = a.ndk
env["ANDROID_NDK_ROOT"] = a.ndk

targets = {
    "arm64-v8a": "aarch64-linux-android",
    "armeabi-v7a": "armv7-linux-androideabi",
    "x86": "i686-linux-android",
    "x86_64": "x86_64-linux-android",
}

for triple in targets.values():
    clang = str(llvm_bin / f"{triple}-clang")
    clangxx = str(llvm_bin / f"{triple}-clang++")
    ar = str(llvm_bin / "llvm-ar")
    
    env_key = triple.replace("-", "_")
    env[f"CC_{env_key}"] = clang
    env[f"CXX_{env_key}"] = clangxx
    env[f"AR_{env_key}"] = ar
    
    print(f"[Rust] {env_key}: CC={clang}")

env["CARGO_ENCODED_RUSTFLAGS"] = "\x1f".join([
    "-C", "link-arg=-Wl,-z,max-page-size=16384",
    "-C", "link-arg=-Wl,-z,common-page-size=16384"
])

manifest_abs = str((ROOT / "native" / "Cargo.toml").resolve())
out_abs = pathlib.Path(a.output).resolve()
out_abs.mkdir(parents=True, exist_ok=True)

for abi, target in targets.items():
    print(f"\n[Rust] Building {abi} ({target})...")
    cmd = [
        "cargo", "build",
        "--manifest-path", manifest_abs,
        "--target", target,
        "--release",
        "--lib"
    ]
    result = subprocess.run(cmd, cwd=str(ROOT), env=env, capture_output=True, text=True)
    
    # Print stdout always, stderr only on failure
    if result.stdout.strip():
        print(result.stdout)
    
    if result.returncode != 0:
        print(result.stderr, file=sys.stderr)
        raise SystemExit(f"Failed to build {abi} (exit code {result.returncode})")

    src = ROOT / "native" / "target" / target / "release" / "libmkpdf_jni.so"
    dst = out_abs / abi / "libmkpdf_jni.so"
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    print(f"[Rust] ✓ Copied libmkpdf_jni.so -> {dst}")

print("\n[Rust] All ABIs built successfully!")
