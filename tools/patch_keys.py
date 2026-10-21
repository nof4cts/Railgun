"""Lets the kit's key system hold 20 move slots (five pages of 1-4) instead of 16, so Modulo Yuji fits without
Sneak combinations. Copies Keys.class out of libs/jjk-1.0.0.jar and changes one constant: `new VKey[16]` in its
static initialiser becomes `new VKey[20]`. Everything else in the kit already sizes itself from that array and
from each sorcerer's own move count, so other sorcerers are unaffected.

    python3 tools/patch_keys.py      # writes patches/com/jjk/mod/client/Keys.class
"""
import os
import zipfile

ROOT = os.path.join(os.path.dirname(__file__), "..")
SLOTS = 20
with zipfile.ZipFile(os.path.join(ROOT, "libs", "jjk-1.0.0.jar")) as z:
    data = bytearray(z.read("com/jjk/mod/client/Keys.class"))
needle = bytes([0x10, 16, 0xBD])            # bipush 16; anewarray
at = data.find(needle)
assert at >= 0 and data.find(needle, at + 1) < 0, "expected exactly one new VKey[16]"
data[at + 1] = SLOTS
out = os.path.join(ROOT, "patches", "com", "jjk", "mod", "client", "Keys.class")
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "wb") as f:
    f.write(data)
print("patched Keys.class at byte", at, "->", SLOTS, "slots")
