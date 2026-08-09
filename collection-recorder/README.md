# MindVoice 수어 영상 녹화 도구

Windows와 macOS에서 별도 패키지 설치 없이 Python과 최신 브라우저로
실행하는 로컬 녹화 도구입니다. 카메라 영상은 외부 서버로 전송되지 않고
이 폴더의 `recordings/` 아래에만 저장됩니다.

## 실행

Windows PowerShell 또는 명령 프롬프트:

```powershell
cd collection-recorder
py -3 server.py
```

또는 `start-windows.bat`을 더블클릭합니다.

`py` 명령을 찾지 못하면 프로젝트에서 사용하는 Python으로 실행합니다.

```powershell
python server.py
```

macOS Terminal:

```bash
cd collection-recorder
python3 server.py
```

또는 Terminal에서 `sh start-macos.command`를 실행합니다. 실행 권한이 설정된
상태라면 Finder에서 직접 열 수도 있습니다.

브라우저가 자동으로 열리지 않으면 다음 주소를 Chrome, Edge 또는 Safari로
엽니다.

```text
http://127.0.0.1:8765
```

종료할 때는 터미널에서 `Ctrl+C`를 누릅니다.

## 권장 촬영 순서

1. 팀원마다 중복되지 않는 `S001`~`S006` ID를 배정합니다.
2. 카메라를 시작하고 상반신과 양손이 화면 안에 있는지 확인합니다.
3. 1차 세션에서 각 클래스의 반복 01~10을 촬영합니다.
4. 휴식 후 가능하면 옷, 조명 또는 배경을 조금 바꿉니다.
5. 2차 세션에서 반복 11~20을 촬영합니다.
6. 전체 진행률이 `220 / 220`인지 확인하고 서버를 종료합니다.

분할은 도구가 다음처럼 강제합니다.

```text
S001~S004: train
S005: validation
S006: test
```

## 저장 결과

```text
recordings/
├── manifest.csv
└── videos/
    └── S001/
        └── train/
            └── HELLO/
                └── s001-hello-001.webm
```

브라우저에 따라 확장자는 `.webm` 또는 `.mp4`가 됩니다. 두 형식 모두 기존
MediaPipe 전처리 파이프라인에서 처리할 수 있습니다. 같은 번호를 다시
촬영하면 확인 후 기존 파일을 안전하게 교체하며 `manifest.csv`도 자동으로
갱신됩니다.

`recordings/`의 영상과 manifest는 개인정보를 포함할 수 있어 Git에서
제외됩니다. 팀원은 폴더 전체를 암호화된 공유 저장소로 전달해야 합니다.

## 촬영 전 확인

- 얼굴이 포함된 학습 영상의 프로젝트 사용 동의를 받습니다.
- 720p 이상, 정면, 역광이 없는 환경을 사용합니다.
- 한 영상 안에 대기 자세 → 수어 → 대기 자세가 들어가게 합니다.
- `NO_SIGN`은 가만히 있기뿐 아니라 얼굴 만지기, 옷 정리, 일반 손짓,
  준비·종료 동작을 섞습니다.
- 촬영 중 브라우저 탭을 전환하거나 컴퓨터를 절전 상태로 두지 않습니다.

## 테스트

```bash
python -m unittest discover -s tests -v
```
