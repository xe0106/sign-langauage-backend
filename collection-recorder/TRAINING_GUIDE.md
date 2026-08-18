# 팀원 영상 학습 가이드

## 데이터 분할

분할은 영상 번호가 아니라 촬영자 ID로 고정한다. 같은 사람의 영상이 학습과
검증 또는 테스트에 섞이면 사람의 자세, 의상, 배경을 외운 모델이 되어 성능이
부풀려진다.

| 역할 | 촬영자 | 영상 수 |
| --- | --- | ---: |
| 학습 | `S001`~`S006` | 1,320개 |
| 검증 | `S007` | 220개 |
| 최종 테스트 | `S008` | 220개 |

각 촬영자는 11개 클래스(`HELLO`, `THANK_YOU`, `SORRY`, `OKAY`, `GOOD`,
`DISLIKE`, `ME`, `YOU`, `MEET`, `GO`, `NO_SIGN`)를 각각 20회 촬영한다.
수집 완료 시 총 영상 수는 1,760개다.

`server.py`는 `S001`~`S006`을 `train`, `S007`을 `validation`, `S008`을
`test`로 자동 배정한다. 웹 녹화 도구로 촬영하면 영상 경로와 `manifest.csv`가
자동으로 생성된다.

## 공통 준비

모든 명령은 `collection-recorder` 폴더에서 실행한다.

```zsh
source .venv/bin/activate
export RUN=training-runs/production-20260813
export MODEL=training-runs/models/holistic_landmarker.task
```

`$MODEL` 파일이 없다면 MediaPipe Holistic Landmarker 모델을 내려받아 해당
경로에 둔다. 실제 전처리를 시작하기 전, 한두 개 영상으로 MediaPipe가 현재
실행 환경에서 정상 동작하는지 확인한다.

## 영상 폴더를 추가, 이동 또는 삭제한 뒤

`recordings/videos/` 아래의 영상 폴더나 파일을 직접 추가, 이동 또는 삭제했다면
`recordings/manifest.csv`를 직접 수정하지 않는다. 아래 명령으로 현재 영상 파일을
기준으로 매니페스트를 다시 생성한다.

```zsh
python -c "import server; print(server.rebuild_manifest())"
```

출력 숫자는 전체 등록 영상 수다. 예를 들어 `S001` 6개를 삭제하고 `S006` 220개만
남았다면 `220`이 출력된다. 특정 촬영자가 정상 등록됐는지는 다음 명령으로 확인한다.

```zsh
python -c 'import csv; print(sum(row["signer_id"] == "S006" for row in csv.DictReader(open("recordings/manifest.csv", encoding="utf-8-sig", newline=""))))'
```

`S006`의 220개 영상이 모두 등록됐다면 `220`이 출력된다.

## 실전 실행 경로

Smoke test 또는 폐기한 영상으로 만든 결과와 실전 결과를 섞지 않기 위해, 실전
전처리는 새 실행 경로에서 시작한다. 이전 `training-runs/production-v1`을 재사용할
필요가 없다.

실전 첫 촬영자인 `S006`을 준비하는 예시는 다음과 같다.

```zsh
python tools/prepare_signer_dataset.py \
  --manifest recordings/manifest.csv \
  --signer-id S006 \
  --model "$MODEL" \
  --output-root "$RUN"
```

성공하면 다음 파일이 생성된다.

```text
training-runs/production-20260813/
├── manifests/S006.csv
└── signers/S006/
    ├── processed/
    └── dataset.npz
```

완료 출력에 `manifestSamples: 220`, `samples: 220`이 포함돼야 한다. 이후에도 같은
`$RUN` 경로에 실전 촬영자별 결과만 추가한다.

## 팀원 한 명의 220개 영상을 받았을 때

받은 팀원 ID에 맞게 `recordings/videos/` 아래에 폴더 전체를 둔다. 예를 들어
`S001`은 `recordings/videos/S001/train/` 아래에 220개 영상이 있어야 한다.

```zsh
python -c "import server; print(server.rebuild_manifest())"

python -m mindvoice_ai.dataset.audit \
  --manifest recordings/manifest.csv \
  --expected-repetitions 20 \
  --output "$RUN/reports/collection-audit.json"
```

`collection-audit.json`에서 받은 촬영자의 클래스별 수량, 재생 불가 영상,
중복 영상, 길이와 해상도 경고를 확인한다. 오류가 있으면 그 팀원의 영상을
보완한 후 다시 매니페스트와 감사 결과를 갱신한다.

품질 확인을 통과한 팀원은 바로 MediaPipe 전처리와 고정 길이 학습 배열 생성을
실행한다. 이 작업이 전체 시간의 대부분을 차지하므로, 수집할 때마다 실행하면
마지막에 8명 영상을 다시 전처리할 필요가 없다.

```zsh
python tools/prepare_signer_dataset.py \
  --manifest recordings/manifest.csv \
  --signer-id S001 \
  --model "$MODEL" \
  --output-root "$RUN"
```

결과는 `$RUN/signers/S001/processed/`와 `$RUN/signers/S001/dataset.npz`에
저장된다. 이미 만든 해당 팀원의 결과를 새 영상으로 다시 만들 때만
`--overwrite`를 붙인다.

이 단계는 모델 가중치 학습이 아니라 개인별 영상 전처리와 학습 데이터 준비다.
한 명만으로는 검증과 테스트가 없어 최종 모델을 올바르게 학습하거나 평가할 수
없다. 현재 학습기는 학습 시 각 클래스가 포함된 `train`, `validation` 분할을
요구하고, 최종 평가는 별도의 `test` 분할로 실행한다.

## 7명 영상으로 학습 및 검증하기

`S001`~`S007`이 준비되고 각 팀원의 개별 준비가 끝난 후 매니페스트를
갱신하고 전체 품질을 확인한다.

```zsh
python -c "import server; print(server.rebuild_manifest())"

python -m mindvoice_ai.dataset.audit \
  --manifest recordings/manifest.csv \
  --expected-repetitions 20 \
  --output "$RUN/reports/preprocess-audit.json"
```

감사 결과의 `errors`가 0일 때만 다음 단계로 진행한다. 아직 개별 준비를 하지
않은 팀원은 앞 절의 명령으로 한 명씩 처리한다. 예를 들어 `S002`~`S007`은
다음처럼 실행한다.

```zsh
for SIGNER in S002 S003 S004 S005 S006 S007; do
  python tools/prepare_signer_dataset.py \
    --manifest recordings/manifest.csv \
    --signer-id "$SIGNER" \
    --model "$MODEL" \
    --output-root "$RUN"
done
```

준비된 7명의 데이터셋을 병합한다. 이 명령은 `S001`~`S006`을 학습, `S007`을
검증으로 유지한다. `S008`은 이 단계에 포함하지 않는다.

```zsh
python tools/merge_signer_datasets.py \
  --input-root "$RUN" \
  --signers S001 S002 S003 S004 S005 S006 S007 \
  --allow-missing-test \
  --output "$RUN/training/dataset.npz"
```

```zsh
python -m mindvoice_ai.training.train \
  --dataset "$RUN/training/dataset.npz" \
  --output-dir "$RUN/artifacts/ksl-word-v1" \
  --model-version ksl-word-v1 \
  --epochs 100 \
  --batch-size 32 \
  --patience 12
```

## 랜드마크 증강 실험

원본 영상이나 전처리 데이터셋을 복사하거나 수정하지 않고, `train` 분할(`S001`~`S006`)
메모리 배열에만 변형본을 추가한다. `S007` 검증과 `S008` 테스트는 항상 원본 그대로여서
점수 비교가 가능하다. 기본값은 증강을 끈 상태다.

첫 증강 실험은 원본 1,320개에 증강본 1,320개를 더해 총 2,640개로 학습한다. 각 증강본은
좌표 미세 흔들림(표준편차 `0.01`), 최대 3프레임 시간 이동, 0.9~1.1배 속도 재표본화를
한 번씩 적용한다. pose visibility와 비어 있는 손 랜드마크의 0 값은 변경하지 않는다.

```zsh
python -m mindvoice_ai.training.train \
  --dataset "$RUN/training/dataset.npz" \
  --output-dir "$RUN/artifacts/ksl-word-augmentation-v1" \
  --model-version ksl-word-augmentation-v1 \
  --epochs 100 \
  --batch-size 32 \
  --patience 12 \
  --augmentation-copies 1 \
  --augmentation-jitter-std 0.01 \
  --augmentation-max-frame-shift 3 \
  --augmentation-min-speed 0.9 \
  --augmentation-max-speed 1.1
```

증강 파라미터와 시드는 생성된 `metadata.json`의 `trainingParameters`에 기록된다. 증강 모델
선택은 `S007` 검증 결과로만 하고, 확정한 모델 하나에 대해서만 `S008` 테스트를 실행한다.
모델 구조(`--architecture`), learning rate, dropout도 같은 방식으로 S007만 사용해 비교한다.

후보를 하나로 확정한 뒤에는 선택 당시 기록한 epoch 수를 고정하고 `--refit-validation`으로
S001~S007 전체를 한 번 재학습할 수 있다. 이 단계에서는 조기 종료와 학습률 스케줄러를 쓰지
않으며 S008은 여전히 평가 전까지 읽지 않는다.

동일 버전을 다시 만들 때만 `--overwrite`를 추가한다. 이전 결과를 보존하려면
`production-v2`, `ksl-word-v2`처럼 새 실행 및 모델 버전 이름을 사용한다.

## `S008`을 받은 뒤 최종 테스트하기

학습 중 매 epoch마다 `S007` 검증 데이터의 손실과 정확도를 확인하고, 가장 좋은
검증 손실의 가중치를 저장한다. `S008`을 받기 전에는 모델을 다시 학습할 필요가
없다. 도착한 `S008` 영상만 개별 준비한다.

```zsh
python -c "import server; print(server.rebuild_manifest())"

python tools/prepare_signer_dataset.py \
  --manifest recordings/manifest.csv \
  --signer-id S008 \
  --model "$MODEL" \
  --output-root "$RUN"
```

평가용 데이터셋은 `S008`의 테스트 분할을 포함해야 하므로, 준비된 8명 데이터를
병합해 별도로 만든다. 이 명령은 모델을 다시 학습하지 않는다.

```zsh
python tools/merge_signer_datasets.py \
  --input-root "$RUN" \
  --signers S001 S002 S003 S004 S005 S006 S007 S008 \
  --output "$RUN/evaluation/dataset-with-test.npz"

python -m mindvoice_ai.training.evaluate \
  --dataset "$RUN/evaluation/dataset-with-test.npz" \
  --model-dir "$RUN/artifacts/ksl-word-v1" \
  --output "$RUN/artifacts/ksl-word-v1/test-evaluation.json"
```

평가는 이미 저장된 최적 가중치로 `S008` 테스트 데이터만 사용한다. 평가 결과를
본 뒤 모델 설정이나 학습 횟수를 변경하지 않는다.

결과 파일은 다음과 같다.

| 파일 | 확인 내용 |
| --- | --- |
| `artifacts/ksl-word-v1/model.keras` | 배포 가능한 모델 |
| `artifacts/ksl-word-v1/metadata.json` | 클래스 순서, 입력 규격, 모델 해시 |
| `artifacts/ksl-word-v1/evaluation.json` | 학습·검증 이력과 테스트 미실행 상태 |
| `artifacts/ksl-word-v1/test-evaluation.json` | `S008` 테스트 macro F1, 정확도, 클래스별 지표, 혼동 행렬 |

모델 구조, epoch 수, 배치 크기, 신뢰도 임계값 같은 선택은 `S007` 검증 결과로
결정한다. `S008` 테스트 수치를 보고 설정을 반복 변경하지 않는다. 최종 테스트는
선택이 끝난 모델에 대해 한 번만 해석한다.

8명 전체가 모이면 사람별 성능 편차도 확인할 수 있다. 이 분석은 최종 테스트와
별개로 실험 보고용으로 사용한다.

```zsh
python -m mindvoice_ai.training.cross_validate \
  --dataset "$RUN/training/dataset.npz" \
  --output "$RUN/reports/signer-cross-validation.json"
```
