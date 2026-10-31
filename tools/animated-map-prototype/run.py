#!/usr/bin/env python3
"""Isolated Linux JavaFX 22 experiment; does not build or modify the MapTool application."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import signal
import subprocess
import urllib.request

ARTIFACTS = {
    "javafx-base": "82e4b5258646033e0cbd8ce860a0595860c8c0d78eadf5ca590f45369b3b3d0f",
    "javafx-graphics": "002ae99d0f0c2f60a587bd9f91168fee5bd9d802781f4797575956cf489c24bb",
    "javafx-media": "9151b4ae4913463050d96bd34c840ea2fea23da278ef5051caf0e7f257644d1a",
    "javafx-swing": "25376bc4390f3706efdf095f40b18066df435a4365b9f4bdf4618e58565ec590",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=Path(__file__).resolve().parent / "build")
    parser.add_argument("--output", type=Path, help="Copy the measurements JSON to this path")
    args = parser.parse_args()
    if platform.system() != "Linux" or platform.machine() != "x86_64":
        raise SystemExit("This reproducible fixture currently pins Linux x86_64 native artifacts only.")
    root = args.cache.resolve()
    deps = root / "deps"
    classes = root / "classes"
    deps.mkdir(parents=True, exist_ok=True)
    classes.mkdir(parents=True, exist_ok=True)
    for artifact, checksum in ARTIFACTS.items():
        jar = deps / f"{artifact}-22-linux.jar"
        if not jar.exists():
            url = f"https://repo.maven.apache.org/maven2/org/openjfx/{artifact}/22/{jar.name}"
            with urllib.request.urlopen(url, timeout=60) as response:
                data = response.read()
            if hashlib.sha256(data).hexdigest() != checksum:
                raise SystemExit(f"SHA-256 mismatch for {artifact}")
            jar.write_bytes(data)
        if hashlib.sha256(jar.read_bytes()).hexdigest() != checksum:
            raise SystemExit(f"Cached SHA-256 mismatch for {artifact}")
    jdk = Path(os.environ["JAVA_HOME"]) / "bin" if "JAVA_HOME" in os.environ else None
    java = str(jdk / "java") if jdk else shutil.which("java")
    javac = str(jdk / "javac") if jdk else shutil.which("javac")
    if not javac or not java:
        raise SystemExit("A JDK (javac and java), ffmpeg, and a display are required.")
    clip = root / "testsrc-640x360-15fps.mp4"
    if not clip.exists():
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i",
                        "testsrc2=size=640x360:rate=15", "-t", "4", "-an", "-c:v", "libx264",
                        "-profile:v", "baseline", "-level", "3.0", "-pix_fmt", "yuv420p",
                        "-movflags", "+faststart", "-threads", "1", "-y", str(clip)], check=True)
    source = Path(__file__).resolve().parent / "AnimatedMapPrototype.java"
    modules = ["--module-path", str(deps), "--add-modules", "javafx.media,javafx.swing"]
    subprocess.run([javac, *modules, "-d", str(classes), str(source)], check=True)
    output = root / "measurement.json"
    (root / "native-cache").mkdir(exist_ok=True)
    (root / "user-home").mkdir(exist_ok=True)
    (root / "xdg-cache").mkdir(exist_ok=True)
    command = [java, "-Xms64m", "-Xmx256m", "-Dprism.order=sw",
               f"-Djavafx.cachedir={root / 'native-cache'}", f"-Duser.home={root / 'user-home'}",
               *modules, "-cp", str(classes),
               "AnimatedMapPrototype", str(clip), str(output)]
    if not os.environ.get("DISPLAY"):
        if not shutil.which("xvfb-run"):
            raise SystemExit("No DISPLAY or xvfb-run. Supply a display; this runner does not install packages.")
        command = ["xvfb-run", "-a", "-s", "-screen 0 1280x1024x24", *command]
    process_env = os.environ.copy()
    process_env["XDG_CACHE_HOME"] = str(root / "xdg-cache")
    process_env["GSETTINGS_BACKEND"] = "memory"
    process = subprocess.Popen(command, env=process_env, start_new_session=True)
    try:
        returncode = process.wait(timeout=25)
    except subprocess.TimeoutExpired:
        # Terminate only this fixture's new process group, including its own Xvfb.
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
        raise SystemExit("Fixture timed out; its process group was terminated.")
    if returncode:
        raise SystemExit(f"Fixture failed with status {returncode}")
    result = json.loads(output.read_text())
    result["fixture_sha256"] = hashlib.sha256(clip.read_bytes()).hexdigest()
    result["fixture_bytes"] = clip.stat().st_size
    result["java_version"] = subprocess.check_output([java, "-version"], stderr=subprocess.STDOUT, text=True).strip()
    result["host"] = platform.platform()
    result["logical_cpus"] = os.cpu_count()
    output.write_text(json.dumps(result, indent=2) + "\n")
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output.read_text())
    print(output.read_text())
    if not result["decoded_video_verified"]:
        print("Native video decoding was NOT verified. See native_error; fallback-only measurements are labelled.")


if __name__ == "__main__":
    main()
