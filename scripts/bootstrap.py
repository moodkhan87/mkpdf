#!/usr/bin/env python3
"""Materialize a pinned upstream checkout, preserving its patched renderer dependencies."""
import pathlib, subprocess
ROOT = pathlib.Path(__file__).resolve().parents[1]
REV = "43550c448db3ba91753f638e3843c0da036bbd60"
DEST = ROOT / "vendor/pdfcraft"
def run(*args): subprocess.run(args, cwd=ROOT, check=True)
if not (DEST / ".git").exists():
    DEST.parent.mkdir(parents=True, exist_ok=True)
    run("git", "init", str(DEST))
    run("git", "-C", str(DEST), "remote", "add", "origin", "https://github.com/storytold/pdfcraft.git")
try:
    current = subprocess.check_output(["git", "-C", str(DEST), "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL).strip()
except subprocess.CalledProcessError:
    current = ""
if current != REV:
    run("git", "-C", str(DEST), "fetch", "--depth", "1", "origin", REV)
    run("git", "-C", str(DEST), "checkout", "--detach", REV)
actual = subprocess.check_output(["git", "-C", str(DEST), "rev-parse", "HEAD"], text=True).strip()
if actual != REV: raise SystemExit("PdfCraft revision mismatch")
print("Pinned PdfCraft:", actual)
