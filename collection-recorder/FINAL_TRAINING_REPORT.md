# 최종 학습 결과 정리

작성일: 2026-08-17

## 결론

최종 후보는 3개의 1D CNN 확률을 가중 평균하는 앙상블이다. S001~S007로 재학습한 뒤
S008의 220개 영상을 평가한 결과는 다음과 같다.

| 지표 | 결과 |
| --- | ---: |
| 정확도 | 86.82% (191 / 220) |
| Macro F1 | 86.52% |
| S007 모델 선택 정확도 | 90.00% |
| S007 모델 선택 Macro F1 | 90.18% |

S007에서의 90%가 S008에서 그대로 재현되지는 않았다. 따라서 현재 데이터 기준으로
서비스 성능을 설명할 때는 S008의 **86.82%**를 사용한다.

최종 평가 원본은
[`ensemble-test-evaluation.json`](training-runs/final-refit-ensemble-20260817/ensemble-test-evaluation.json)에
있다.

## 데이터 분할

| 역할 | 촬영자 | 영상 수 | 사용 시점 |
| --- | --- | ---: | --- |
| 모델 학습 | S001~S006 | 1,320 | 모든 후보 학습 |
| 모델 선택 검증 | S007 | 220 | 증강, 구조, 정규화, 앙상블 가중치 선택 |
| 독립 테스트 | S008 | 220 | 선택 완료 후 최종 평가 |

각 촬영자는 11개 클래스의 영상을 각 20개씩 제공한다. 클래스는 `HELLO`,
`THANK_YOU`, `SORRY`, `OKAY`, `GOOD`, `DISLIKE`, `ME`, `YOU`, `MEET`, `GO`,
`NO_SIGN`이다.

최종 재학습 단계에서는 선택이 끝난 뒤 S007도 학습 데이터에 포함했다. 따라서
S001~S007의 1,540개 원본 영상을 학습에 사용했고, S008만 평가용으로 남겼다.

## 입력과 전처리

- 입력 shape: 영상당 `30 x 258`
- 특징 순서: pose 33개 `x,y,z,visibility` (132개), 왼손 21개 `x,y,z` (63개),
  오른손 21개 `x,y,z` (63개)
- 정규화: 양 어깨 중심 이동과 3차원 어깨 너비 scale 정규화
- 전처리 데이터: `training-runs/production-20260816/signers/S001~S008/dataset.npz`
- 병합 학습 데이터: `training-runs/production-8signers-20260817/evaluation/dataset-with-test.npz`

## 선택된 학습 방식

### 랜드마크 증강

증강은 학습 split에만 적용했다. 검증 S007과 테스트 S008은 원본 랜드마크를 그대로
사용했다.

| 항목 | 값 |
| --- | ---: |
| 증강본 수 | 원본당 1개 |
| 학습 배열 수 | 1,540개 원본 + 1,540개 증강본 = 3,080개 |
| 좌표 jitter 표준편차 | 0.002 |
| 시간 이동 | 최대 1프레임 |
| 속도 재표본화 | 0.98~1.02배 |

pose visibility는 변경하지 않고, 누락된 손 랜드마크의 `0` 값에는 noise를 넣지 않는다.
좌우 반전은 왼손과 오른손의 의미가 바뀔 수 있으므로 적용하지 않는다.

### 모델

모든 구성 모델은 `1d_cnn_v1` 구조다.

- LayerNormalization
- Conv1D 128, kernel 5 -> BatchNormalization -> MaxPooling
- Conv1D 128, kernel 3 -> BatchNormalization
- Conv1D 64, kernel 3 -> GlobalAveragePooling
- Dropout 0.5 -> Dense 96 -> 11-class Softmax
- Adam learning rate `0.0003`
- L2 weight decay `0.0001`

### 앙상블

세 모델의 Softmax 확률을 아래 비율로 평균한 뒤 가장 높은 클래스를 선택한다.

| 모델 | 학습 epoch | batch size | seed | 가중치 |
| --- | ---: | ---: | ---: | ---: |
| `cnn-reg-refit-v1` | 24 | 32 | 42 | 20 |
| `cnn-scheduled-refit-v1` | 22 | 16 | 42 | 7 |
| `cnn-seed123-refit-v1` | 27 | 32 | 123 | 7 |

가중 평균 식은 다음과 같다.

```text
probability = (20 * model_1 + 7 * model_2 + 7 * model_3) / 34
```

구성 모델은 다음 경로에 있다.

```text
training-runs/final-refit-ensemble-20260817/artifacts/
├── cnn-reg-refit-v1/
├── cnn-scheduled-refit-v1/
└── cnn-seed123-refit-v1/
```

개별 모델의 S008 정확도는 각각 76.36%, 77.73%, 77.73%였고, 앙상블은 86.82%였다.

## 실험 요약

| 후보 | S007 선택 결과 | 판단 |
| --- | ---: | --- |
| 기존 단일 CNN | 69.55% | 기준선 |
| 강한 증강 CNN | 65.45% | 증강이 과도해 제외 |
| 약한 증강 CNN | 80.00% | 개선 |
| 약한 증강 + L2 + Dropout | 85.45% | 최선 단일 모델 |
| 세 모델 가중 앙상블 | 90.00% | 최종 선택 |

BiGRU, 위치+속도 Temporal CNN, 작은 batch와 학습률 감소 스케줄러 단독 적용도 비교했지만
최종 앙상블보다 좋은 S007 결과를 내지 못했다.

## 재현 절차

모든 명령은 `collection-recorder`에서 실행한다.

```zsh
source .venv/bin/activate
export RUN=training-runs/production-8signers-20260817
```

후보 선택용 학습은 S001~S006 학습과 S007 검증을 유지한다.

```zsh
cd ../ai-server

../collection-recorder/.venv/bin/python -m mindvoice_ai.training.train \
  --dataset ../collection-recorder/$RUN/training/dataset.npz \
  --output-dir ../collection-recorder/training-runs/model-selection-20260817/artifacts/cnn-weak-augmentation-regularized-v1 \
  --model-version cnn-weak-augmentation-regularized-v1 \
  --epochs 140 --batch-size 32 --patience 20 \
  --architecture 1d_cnn_v1 --learning-rate 0.0003 \
  --dropout 0.5 --l2-weight-decay 0.0001 \
  --augmentation-copies 1 --augmentation-jitter-std 0.002 \
  --augmentation-max-frame-shift 1 \
  --augmentation-min-speed 0.98 --augmentation-max-speed 1.02
```

후보 선택을 끝낸 뒤에만 `--refit-validation`으로 S001~S007을 합쳐 고정 epoch 재학습한다.
S008은 이 명령에서 학습에 포함되지 않는다.

```zsh
../collection-recorder/.venv/bin/python -m mindvoice_ai.training.train \
  --dataset ../collection-recorder/$RUN/evaluation/dataset-with-test.npz \
  --output-dir ../collection-recorder/training-runs/final-refit-ensemble-20260817/artifacts/cnn-reg-refit-v1 \
  --model-version cnn-reg-refit-v1 \
  --epochs 24 --batch-size 32 \
  --architecture 1d_cnn_v1 --learning-rate 0.0003 \
  --dropout 0.5 --l2-weight-decay 0.0001 \
  --augmentation-copies 1 --augmentation-jitter-std 0.002 \
  --augmentation-max-frame-shift 1 \
  --augmentation-min-speed 0.98 --augmentation-max-speed 1.02 \
  --refit-validation
```

나머지 두 구성 모델은 위 표의 epoch, batch size, seed만 바꿔 같은 방식으로 재학습한다.

## 배포 상태와 주의점

- 현재 AI 서버는 단일 `model.keras`를 로드하는 구조다.
- 최종 결과는 3개 모델의 확률 평균이므로, 이 결과를 서비스에 쓰려면 AI 서버 예측기에
  앙상블 로딩과 가중 평균 기능을 추가해야 한다.
- 단일 모델 하나만 배포하면 최종 앙상블의 86.82% 성능을 기대할 수 없다.
- S008은 이전 기준선과 중간 앙상블 평가에서도 이미 확인했다. 이번 최종 재학습 설정은 그
  결과에 맞춰 변경하지 않았지만, 완전히 새로운 최종 성능 수치가 필요하면 새 촬영자 또는
  별도의 숨겨진 테스트 데이터를 추가해야 한다.
- S008 결과를 보고 다시 증강, 모델, 가중치를 조정하면 S008은 더 이상 독립 테스트가 아니다.
