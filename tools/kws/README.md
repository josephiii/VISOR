# Wake-phrase tooling

VISOR listens for its wake phrases ("VISOR, read this", "VISOR GO", ...) on the phone with a
[sherpa-onnx](https://k2-fsa.github.io/sherpa/onnx/kws/index.html) keyword spotter
(`frontend/visor/app/src/main/java/ucf/visor/stt/Listener.kt`). The model reads its phrases from
`frontend/visor/app/src/main/assets/kws/keywords.txt`, written as the model's BPE tokens rather
than plain words. This folder turns plain phrases into that file.

| File | Purpose |
| --- | --- |
| `keywords_raw.txt` | The wake phrases in plain English, one per line, grouped by feature. `:2.0` boosts a phrase, `#0.25` is its trigger threshold. |
| `bpe.model` | The tokenizer of the bundled model (`sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01`). |
| `make_keywords.py` | Writes the app's `keywords.txt` from `keywords_raw.txt`. |

## Adding or changing a wake phrase

1. Edit `keywords_raw.txt`.
2. Regenerate the app's file:
   ```
   pip install -r requirements.txt
   python make_keywords.py
   ```
3. Add the phrase, written as people say it (`"VISOR, what is this"`), to the matching
   `CameraCommand` in `frontend/visor/app/src/main/java/ucf/visor/stt/WakePhrases.kt`. That
   decides which feature the phrase starts, and Settings lists it under that command, which a
   user can turn off. The unit tests (`./gradlew testDebugUnitTest`) fail until
   `keywords_raw.txt` and `WakePhrases.kt` list the same phrases.

The full model (encoder, decoder, joiner and tokens) already ships in the app's `assets/kws/`. The
original download is the
[sherpa-onnx KWS model release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models).
