# AI MVP Specification

## 1. Goal

The first AI milestone recognizes a single, isolated Korean Sign Language word performed in front of a camera and returns a stable label with confidence.

This milestone supports both:

- lecture practice, where the expected label is known; and
- video-call captions, where a short list of supported words is detected in real time.

Continuous sentence translation and Korean-language generation are explicitly out of scope for the first milestone.

## 2. Decisions found in repository history

The repository history establishes only the following AI-adjacent decisions:

- learning content is stored as `Lecture` records with categories and video/image URLs;
- calls have a UUID-like `callId`, caller, callee, and lifecycle status;
- recognized captions are stored as `CallSubtitle.textContent` with a sender and timestamp.

No commit defines a model, landmark format, vocabulary, confidence policy, or AI transport protocol. The remaining decisions in this document are therefore the initial MVP contract.

## 3. Initial vocabulary

The initial vocabulary contains ten common isolated signs:

| Class ID | Korean label | Stable key | Motion type |
| ---: | --- | --- | --- |
| 0 | 안녕하세요 | `HELLO` | dynamic |
| 1 | 감사합니다 | `THANK_YOU` | dynamic |
| 2 | 미안합니다 | `SORRY` | dynamic |
| 3 | 괜찮습니다 | `OKAY` | dynamic |
| 4 | 좋습니다 | `GOOD` | dynamic |
| 5 | 싫습니다 | `DISLIKE` | dynamic |
| 6 | 나 | `ME` | dynamic |
| 7 | 너 | `YOU` | dynamic |
| 8 | 만나다 | `MEET` | dynamic |
| 9 | 가다 | `GO` | dynamic |

Before collecting the final dataset, these labels must be matched against production `Lecture.title` values and authoritative Korean Sign Language references. Class IDs and stable keys must not be silently reordered after a model is published.

Training also requires a `NO_SIGN` class for idle poses, transitions, non-sign gestures, and missing hands. `NO_SIGN` is an internal class and is never emitted as a caption.

## 4. MediaPipe input contract

Landmarks are extracted on the client and sent to the AI service. Video-call media remains on the WebRTC path and is not routed through the Spring application.

Each frame is a flat vector with 258 floating-point values in this fixed order:

1. pose: 33 landmarks x `(x, y, z, visibility)` = 132;
2. left hand: 21 landmarks x `(x, y, z)` = 63;
3. right hand: 21 landmarks x `(x, y, z)` = 63.

Missing landmark groups are zero-filled. A model input window contains the latest 30 frames and therefore has shape `(30, 258)`.

Coordinates must be normalized consistently in training and serving. The baseline normalization will use the shoulder center as the origin and shoulder width as the scale. Raw and normalized feature formats must never be mixed under the same model version.

## 5. WebSocket contract

### Client to AI service

```json
{
  "type": "landmark_frame",
  "sessionId": "8b2dc4f8-7852-40a9-a2fb-20ba5ac85d24",
  "callId": "c6ec34ca-6574-4f98-9cb2-4e81920baef1",
  "sequence": 42,
  "timestampMs": 1723100000123,
  "features": [0.0]
}
```

`features` contains exactly 258 finite numbers. `callId` may be omitted for lecture practice, but `sessionId` is always required.

### AI service to client

During buffer warm-up:

```json
{
  "type": "status",
  "status": "warming_up",
  "bufferedFrames": 12,
  "requiredFrames": 30
}
```

Stable prediction:

```json
{
  "type": "prediction",
  "sessionId": "8b2dc4f8-7852-40a9-a2fb-20ba5ac85d24",
  "callId": "c6ec34ca-6574-4f98-9cb2-4e81920baef1",
  "classId": 0,
  "label": "안녕하세요",
  "confidence": 0.94,
  "stable": true,
  "startTimeMs": 1723100000123,
  "endTimeMs": 1723100001123,
  "modelVersion": "ksl-word-v0.1.0"
}
```

Only stable predictions are eligible for `CallSubtitle` persistence. The Spring service remains responsible for authenticating the user, checking call membership, and storing or broadcasting the finalized caption.

## 6. Baseline model

- task: signer-independent isolated-word classification;
- input: `(30, 258)` normalized landmark sequence;
- baseline: 1D-CNN;
- comparison candidates: GRU and temporal convolutional network;
- output: 10 public labels plus `NO_SIGN`;
- split: by signer, never by random clip alone.

Static fingerspelling may later use a separate angle-feature KNN model. KNN is not the primary model for dynamic words.

## 7. Acceptance criteria

- macro F1 of at least 0.85 on unseen signers for the ten-word isolated test set;
- per-window model inference at or below 100 ms on the deployment CPU target;
- no caption emitted below the configured confidence and stability thresholds;
- repeated predictions do not create duplicate captions without an intervening release or `NO_SIGN` state;
- all responses include a model version;
- malformed frames do not terminate the WebSocket process.

## 8. Data plan

1. Match the ten labels to AI Hub annotations and the production lecture catalog.
2. Extract MediaPipe landmarks from eligible public data.
3. Collect application-camera samples to cover missing labels and domain shift.
4. Keep signer IDs so train, validation, and test sets are disjoint by person.
5. Record dataset source, license, consent, and model-use restrictions.
6. Add diverse `NO_SIGN` and transition samples.

Raw video, derived datasets, and trained model artifacts are not committed to Git.

## 9. Service boundaries

```text
Client
  - camera and WebRTC
  - MediaPipe landmark extraction
  - sends landmark frames to AI service
  - renders captions

Python AI service
  - validates landmark frames
  - keeps a per-session rolling window
  - normalizes features and runs inference
  - applies confidence, smoothing, and duplicate suppression

Spring service
  - JWT authentication
  - call lifecycle and membership
  - persists stable CallSubtitle records
  - exposes caption history and application APIs
```
