"""Regenerate the app's wake-phrase file from keywords_raw.txt.

    pip install -r requirements.txt
    python make_keywords.py

Each line of keywords_raw.txt is a plain-English phrase with sherpa-onnx's
optional ":boost #threshold" suffix. The phrase is split into the wake-word
model's BPE tokens and written to the app's assets; suffixes and blank lines
are kept as they are.
"""

import re
from pathlib import Path

from sherpa_onnx import text2token

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / "frontend" / "visor" / "app" / "src" / "main" / "assets" / "kws"

# "VISOR READ THIS :2.0 #0.25" -> ("VISOR READ THIS", " :2.0 #0.25")
LINE = re.compile(r"^(.*?)((?:\s+[:#@]\S+)*)\s*$")


def main() -> None:
    out = []
    for line in (HERE / "keywords_raw.txt").read_text(encoding="utf-8").splitlines():
        if not line.strip():
            out.append("")
            continue
        phrase, suffix = LINE.match(line).groups()
        tokens = text2token(
            [phrase.strip()],
            tokens=str(ASSETS / "tokens.txt"),
            tokens_type="bpe",
            bpe_model=str(HERE / "bpe.model"),
        )[0]
        out.append(" ".join(tokens) + suffix)

    target = ASSETS / "keywords.txt"
    target.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"Wrote {sum(1 for l in out if l)} phrases to {target}")


if __name__ == "__main__":
    main()
