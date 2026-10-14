import subprocess, os, glob
subprocess.run(["python3", "-m", "demucs", "--two-stems=vocals", "-n", "htdemucs", "-o", "sep", "mix.mp3"], check=False)
voc = (glob.glob("sep/htdemucs/mix/vocals.*") or ["mix.mp3"])[0]
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", voc, "-ac", "1", "-b:a", "96k", "vocals.mp3"], check=False)
from faster_whisper import WhisperModel
m = WhisperModel("medium", device="cpu", compute_type="int8")
out = ["vocals from " + voc]
segs, info = m.transcribe("vocals.mp3", language="ja", word_timestamps=True, vad_filter=False, condition_on_previous_text=False)
for s in segs:
    out.append(f"{s.start:.2f}-{s.end:.2f} {s.text}")
    for w in s.words:
        out.append(f"   {w.start:.2f}-{w.end:.2f} {w.word}")
open("result.txt", "w").write("\n".join(out))
