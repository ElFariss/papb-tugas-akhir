# :ai-client (AI-1)

On-device SIBI A-Z recognition and Indonesian speech-to-text. Standalone Android library: no dependency on other
modules.

```
CameraX -> MediaPipe Hand Landmarker -> Fingerpose port -> smoothing -> temporal validation -> word -> query payload
```

| Package | Ported from |
|---|---|
| `gesture.fingerpose` | `fingerpose` 0.1.0, `components/handsigns/Asign.ts`..`Zsign.ts` |
| `gesture.pipeline` | `gesture-recognition.ts`, `components/gesture/gesture-recognition.tsx` |
| `gesture.mediapipe` | `handpose-service.ts`, with CameraX and MediaPipe instead of TensorFlow.js |
| `gesture.SibiConfig` | `sibi-config.ts` |
| `speech` | `SpeechToText.tsx` |
| `validation` | `gesture_validation_service.py` |
| `query` | `GestureTextRequest` in `gesture.py` |

## Use it from Compose

FE needs `androidx.camera:camera-view` for `PreviewView`, and must request the permissions first.

```kotlin
class SibiViewModel(app: Application) : AndroidViewModel(app) {
    val recognizer: GestureRecognizer = MediaPipeGestureRecognizer(app)
    val speech: SpeechToTextEngine = AndroidSpeechToTextEngine(app)

    init { viewModelScope.launch { recognizer.initialize() } }

    override fun onCleared() {
        recognizer.close()
        speech.close()
    }
}

@Composable
fun SibiCamera(vm: SibiViewModel) { // only shown once CAMERA is granted
    val owner = LocalLifecycleOwner.current
    val status by vm.recognizer.status.collectAsState()
    val result by vm.recognizer.result.collectAsState()
    val word by vm.recognizer.word.collectAsState()
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val ready = status == RecognizerStatus.Ready || status == RecognizerStatus.Running

    AndroidView(factory = { PreviewView(it).also { view -> previewView = view } })
    LaunchedEffect(previewView, ready) {
        val view = previewView ?: return@LaunchedEffect
        if (ready) vm.recognizer.start(owner, view.surfaceProvider)
    }
    DisposableEffect(Unit) { onDispose { vm.recognizer.stop() } }

    Text("${result.letter ?: "-"}  ${word.text}")
}
```

| Flow | Meaning |
|---|---|
| `status` | `Uninitialized`, `Initializing`, `Ready`, `Running`, `Closed`, `Failed(error)` |
| `result` | current letter, confidence, alternatives and landmarks for an overlay |
| `word` | the word so far; edit it with `appendSpace`, `removeLastLetter`, `clearWord` |
| `committedLetters` | one event per accepted letter, for haptics or TalkBack announcements |

Landmarks in `result.hand` are pixels of the upright analysis image, already mirrored for the front camera. With the
back camera the preview is not mirrored, so flip them: `x = imageWidth - x`.

Sending the word:

```kotlin
when (val query = GestureQueryFormatter.format(recognizer.word.value, sessionId)) {
    is GestureQueryResult.Valid -> api.post("/api/v1/gesture/ask", GestureQueryFormatter.toJson(query.request))
    is GestureQueryResult.Invalid -> showError(query.reason)
}
```

## Differences from the web app

Classification itself is identical and tested as such. These are deliberate changes:

| Web app | Here |
|---|---|
| Score 0..10 clamped to 1.0, so confidence is always 100% and the 0.75 gate never rejects | Confidence is score / maximum score |
| Stability check reads the previous render's state | Current frame is included |
| Old frames survive when the hand disappears | Smoothing and validation are reset |
| An unrecognised shape becomes the letter `Unknown` | `null`; never added to the word |
| Payload confidence is the last frame's | Mean over the letters in the word |
| `tfjs_model` (24 classes) is in the repo | Not used: its weight files are missing, so it cannot be converted to LiteRT, and 24 classes cannot cover A-Z |

## Known limitations

- **Accuracy on real people has not been measured.** There is no labelled hand data in the reference project.
- **S is read as N.** With an index finger pointing diagonally, both definitions match fully and N comes first.
  A/T, M/N and U/V/R are also close.
- 19 sign files put the thumb's direction on the index finger (copy-paste upstream). Kept for parity; fixing it needs
  real data to prove it helps.
- J and Z are motion signs, but are recognised as static poses like in the web app.
- Definitions assume a right hand.
- Speech quality, offline support and the start/stop beep depend on the phone's recognition service.

## Tests

```bash
./gradlew :ai-client:testDebugUnitTest
```

```bash
./gradlew :ai-client:connectedDebugAndroidTest
```

Parity fixtures come from the *original* code (`tools/`): 200 hands with every curl, direction and score from the
real `fingerpose` package, and 1134 cases from the Python validator.

## Benchmark

Reports are written to `build/reports/sibi-benchmark-*.md`.

- **synthetic** runs always. Its hands are generated from the sign definitions, so it measures whether the definitions
  can be told apart, **not** accuracy. Current: 233/260, same as the web app.
- **real** runs when `benchmark-data/*.csv` exists, and is skipped otherwise. One hand per line, in the coordinates of
  `result.hand.landmarks`:

```
letter,x0,y0,z0,x1,y1,z1,...,x20,y20,z20
```

Fail below a minimum accuracy with `-Psibi.benchmark.min=0.8`. Collect several people per letter, with their consent;
the folder is git-ignored because hand captures are personal data.
