#!/usr/bin/env python3
"""Exercise the actual LAME JNI bridge on the host; requires CMake, a JDK and FFmpeg."""
import json
import math
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


with tempfile.TemporaryDirectory(prefix="kruxx-mp3-check-") as directory:
    work = Path(directory)
    env = os.environ.copy()
    env.setdefault("JAVA_HOME", str(Path(shutil.which("javac")).resolve().parents[1]))
    run("cmake", "-S", str(ROOT / "composeApp/src/androidMain/cpp"), "-B", str(work / "build"),
        "-DCMAKE_BUILD_TYPE=Release", env=env, stdout=subprocess.DEVNULL)
    run("cmake", "--build", str(work / "build"), "--parallel", "4", stdout=subprocess.DEVNULL)
    java = work / "Mp3Encoder.java"
    java.write_text(r'''
package app.kreate.android.downloads;
import java.io.*;
public class Mp3Encoder {
    static { System.loadLibrary("kruxx_mp3"); }
    private native long create(int rate, int channels);
    private native int encode(long handle, short[] pcm, int samples, byte[] output);
    private native int flush(long handle, byte[] output);
    private native void close(long handle);
    public static void main(String[] args) throws Exception {
        Mp3Encoder encoder = new Mp3Encoder();
        if (encoder.create(48000, 3) != 0) throw new AssertionError("Invalid channels accepted");
        for (int[] format : new int[][] {{48000, 2}, {44100, 1}, {24000, 2}}) {
            int rate = format[0], channels = format[1], frames = rate * 3;
            long handle = encoder.create(rate, channels);
            if (handle == 0) throw new AssertionError("Cannot initialize");
            try (OutputStream out = new FileOutputStream(args[0]+"/"+rate+"-"+channels+".mp3")) {
                byte[] encoded = new byte[10000];
                if (encoder.encode(handle, new short[0], 1, encoded) >= 0)
                    throw new AssertionError("Invalid input length accepted");
                for (int offset = 0; offset < frames; offset += 1024) {
                    int count = Math.min(1024, frames-offset);
                    short[] pcm = new short[count*channels];
                    for (int frame = 0; frame < count; frame++)
                        for (int channel = 0; channel < channels; channel++)
                            pcm[frame*channels+channel] = (short)(12000*Math.sin(2*Math.PI*440*(offset+frame)/rate));
                    int bytes = encoder.encode(handle, pcm, count, encoded);
                    if (bytes < 0) throw new AssertionError("Encode error "+bytes);
                    out.write(encoded, 0, bytes);
                }
                int bytes = encoder.flush(handle, encoded);
                if (bytes < 0) throw new AssertionError("Flush error");
                out.write(encoded, 0, bytes);
            } finally { encoder.close(handle); }
        }
    }
}
''')
    run("javac", "-d", str(work), str(java))
    run("java", "-Djava.library.path=" + str(work / "build"), "-cp", str(work),
        "app.kreate.android.downloads.Mp3Encoder", str(work))
    for path in sorted(work.glob("*.mp3")):
        rate, channels = map(int, path.stem.split("-"))
        probe = json.loads(subprocess.check_output([
            "ffprobe", "-v", "error", "-show_streams", "-of", "json", str(path)]))
        stream = probe["streams"][0]
        assert stream["codec_name"] == "mp3", stream
        assert int(stream["bit_rate"]) == 320000, stream
        assert int(stream["sample_rate"]) == (48000 if rate >= 48000 else 44100), stream
        assert stream["channels"] == channels, stream
        assert 3 <= float(stream["duration"]) < 3.2, stream
        pcm = subprocess.check_output(["ffmpeg", "-v", "error", "-i", str(path),
            "-af", "pan=mono|c0=c0", "-f", "f32le", "-"])
        samples = struct.unpack("<" + "f" * (len(pcm) // 4), pcm)
        rms = math.sqrt(sum(sample * sample for sample in samples) / len(samples))
        assert 0.2 < rms < 0.3, rms
        print(f"{rate} Hz / {channels} channel(s): MP3 320 kbit/s, audible PCM, duration OK")
