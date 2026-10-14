from faster_whisper import WhisperModel
m = WhisperModel("medium", device="cpu", compute_type="int8")
out = []
for f in ["v1.mp3", "v2.mp3"]:
    segs, info = m.transcribe(f, language="ja", word_timestamps=True, vad_filter=False)
    out.append("== " + f)
    for s in segs:
        out.append(f"{s.start:.2f}-{s.end:.2f} {s.text}")
        for w in s.words:
            out.append(f"   {w.start:.2f}-{w.end:.2f} {w.word}")
open("result.txt", "w").write("\n".join(out))
