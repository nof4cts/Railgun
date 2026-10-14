from faster_whisper import WhisperModel
m = WhisperModel("medium", device="cpu", compute_type="int8")
out = []
for f in ["v4.mp3", "ref.mp3"]:
    segs, info = m.transcribe(f, word_timestamps=True, vad_filter=False)
    out.append("== " + f + " lang=" + info.language)
    for s in segs:
        out.append(f"{s.start:.2f}-{s.end:.2f} {s.text}")
        for w in s.words:
            out.append(f"   {w.start:.2f}-{w.end:.2f} {w.word}")
open("result.txt", "w").write("\n".join(out))
