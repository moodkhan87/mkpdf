#!/usr/bin/env python3
"""Cross-compile the mkpdf JNI crate for Android (called by Gradle's buildRust task)."""
import argparse, os, pathlib, shutil, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]

# Must match minSdk in app/build.gradle.kts.
API = 26

# ABI -> (Rust target triple, NDK clang wrapper prefix).
# Modern NDKs only ship wrappers named "<prefix><API>-clang" (API level included).
# For 32-bit ARM the prefix is "armv7a-linux-androideabi" (with the "a"),
# which differs from the Rust triple "armv7-linux-androideabi".
TARGETS = {
    "arm64-v8a":   ("aarch64-linux-android",   "aarch64-linux-android"),
    "armeabi-v7a": ("armv7-linux-androideabi", "armv7a-linux-androideabi"),
    "x86":         ("i686-linux-android",      "i686-linux-android"),
    "x86_64":      ("x86_64-linux-android",    "x86_64-linux-android"),
}

p = argparse.ArgumentParser()
p.add_argument("--ndk", required=True)
p.add_argument("--output", required=True)
p.add_argument(
    "--abis",
    default="arm64-v8a",
    help="comma-separated ABIs, or 'all' (default: arm64-v8a only)",
)
a = p.parse_args()

if a.abis == "all":
    abis = list(TARGETS)
else:
    abis = [x.strip() for x in a.abis.split(",") if x.strip()]
    unknown = [x for x in abis if x not in TARGETS]
    if unknown:
        raise SystemExit(f"Unknown ABI(s): {unknown}. Valid: {list(TARGETS)}")

# Fetch the pinned PdfCraft sources into vendor/pdfcraft.
subprocess.run([sys.executable, str(ROOT / "scripts/bootstrap.py")], check=True)

ndk = pathlib.Path(a.ndk)
if not (ndk / "source.properties").is_file():
    raise SystemExit(f"NDK source.properties not found at {ndk}")

prebuilt = ndk / "toolchains" / "llvm" / "prebuilt"
llvm_bin = next(
    (d / "bin" for d in sorted(prebuilt.glob("*")) if (d / "bin").is_dir()), None
)
if llvm_bin is None:
    raise SystemExit(f"No LLVM toolchain found under {prebuilt}")
print(f"[Rust] NDK toolchain bin: {llvm_bin}", flush=True)


def tool(name):
    """Return the full path of an NDK tool, or stop with a helpful listing."""
    path = llvm_bin / name
    if not path.is_file():
        wrappers = sorted(x.name for x in llvm_bin.glob("*android*-clang"))
        raise SystemExit(
            f"Missing NDK tool: {path}\n"
            f"Available clang wrappers ({len(wrappers)}): {wrappers[:12]}"
        )
    return str(path)


env = dict(os.environ)
env["ANDROID_NDK_HOME"] = str(ndk)
env["ANDROID_NDK_ROOT"] = str(ndk)
env["PATH"] = str(llvm_bin) + os.pathsep + env.get("PATH", "")
# 16 KB page-size alignment (required for newer Android devices).
env["CARGO_ENCODED_RUSTFLAGS"] = "\x1f".join([
    "-C", "link-arg=-Wl,-z,max-page-size=16384",
    "-C", "link-arg=-Wl,-z,common-page-size=16384",
])

manifest = str((ROOT / "native" / "Cargo.toml").resolve())
out_dir = pathlib.Path(a.output).resolve()
out_dir.mkdir(parents=True, exist_ok=True)

for abi in abis:
    triple, prefix = TARGETS[abi]
    clang = tool(f"{prefix}{API}-clang")
    clangxx = tool(f"{prefix}{API}-clang++")
    ar = tool("llvm-ar")

    key = triple.replace("-", "_")
    build_env = dict(env)
    build_env[f"CC_{key}"] = clang
    build_env[f"CXX_{key}"] = clangxx
    build_env[f"AR_{key}"] = ar
    # cargo links the final .so with this; without it cargo falls back to host "cc".
    build_env[f"CARGO_TARGET_{key.upper()}_LINKER"] = clang

    print(f"\n[Rust] === {abi} ({triple}) ===", flush=True)
    print(f"[Rust] compiler/linker: {clang}", flush=True)
    subprocess.run([clang, "--version"], check=True)  # sanity: wrapper really runs

    cmd = [
        "cargo", "build",
        "--manifest-path", manifest,
        "--target", triple,
        "--release",
        "--lib",
    ]
    # Output streams live (not captured) so the CI log stays in order.
    result = subprocess.run(cmd, cwd=str(ROOT), env=build_env)
    if result.returncode != 0:
        raise SystemExit(f"Failed to build {abi} (cargo exit code {result.returncode})")

    src = ROOT / "native" / "target" / triple / "release" / "libmkpdf_jni.so"
    if not src.is_file():
        raise SystemExit(f"Build reported success but {src} does not exist")
    dst = out_dir / abi / "libmkpdf_jni.so"
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    print(f"[Rust] OK: copied -> {dst}", flush=True)

print("\n[Rust] Done: " + ", ".join(abis), flush=True)
