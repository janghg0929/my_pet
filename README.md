## 소개 Information
- 바탕화면에 펫을 생성합니다
- 이름은 비트(bit)에요
- **Windows 와 macOS** 에서 실행할 수 있어요 (2.0.0 부터 Java 로 다시 만들었어요)

---

## 기능 Feature
- 자유 이동 및 점프
- 쓰다듬기
- 날리기
- 창 위에 올리기
- 반복 클릭시 무지개
- 외형 변경(캐릭터 우클릭)
  - 브라우저(Chrome/Microsoft Edge, macOS 는 Safari 포함)
    - 돋보기
    - Internet Explorer 아이콘
  - Discord
    - 색상 변경
  - Steam
    - 픽셀 하트
  - 음악(YouTube/YouTube Music/Spotify)
    - 헤드셋
- 색깔 변경
  - 기본 색(#4FC3F7)
  - 사용자 지정 색
- 자동 업데이트

---

## 사용 방법 How to use
[최신 릴리스](https://github.com/janghg0929/my_pet/releases/latest)에서 내 컴퓨터에 맞는 파일을 받으세요. Java 를 따로 설치할 필요는 없어요.

| 운영체제 | 파일 | 실행 방법 |
|---|---|---|
| Windows | **`MyPet.exe`** | 받아서 실행. 처음 한 번은 비트를 설치해요 (인터넷 필요 없음) |
| macOS (M1/M2/M3/M4…) | `MyPet-macos-arm64.dmg` | dmg 를 열고 MyPet 을 응용 프로그램 폴더로 끌어다 놓기 |
| macOS (인텔) | `MyPet-macos-x64.dmg` | 위와 같음 |

- Windows: `MyPet.exe` 안에 비트가 통째로 들어 있어요. 처음 실행하면 비트를 `%LOCALAPPDATA%\MyPet`
  (`C:\Users\<사용자>\AppData\Local\MyPet`) 에 설치하고, 그다음부터는 바로 켜요.
  더 새 버전의 `MyPet.exe` 를 받아 실행하면 설정은 그대로 두고 프로그램만 새 버전으로 바꿔요.
  지우고 싶으면 비트를 끄고 이 폴더를 지우면 돼요.
- 설정(색깔, 외형)은 같은 폴더의 `settings.json` 에 저장돼요 (맥은 `~/.my_pet/settings.json`).
  예전 버전에서 쓰던 설정 파일이 있으면 처음 켤 때 알아서 옮겨 와요.
- 캐릭터 우클릭(맥: 두 손가락 클릭 또는 Control+클릭) → 설정 (외형, 색깔, 종료)
- 캐릭터를 클릭한 상태에서 Esc 로 종료할 수 있어요
- 새 버전이 나오면 실행할 때 "업데이트할까요?" 라고 물어봐요. **예** 를 누르면 알아서 받아서 다시 시작해요

### macOS 처음 실행할 때
애플 개발자 서명이 없는 앱이라 "확인되지 않은 개발자" 경고가 떠요.
`시스템 설정 → 개인정보 보호 및 보안` 맨 아래의 **"그래도 열기"** 를 누르면 돼요.
그래도 안 열리면 터미널에서 아래 명령을 한 번 실행하세요.

```bash
xattr -dr com.apple.quarantine /Applications/MyPet.app
```

유튜브 탭을 보고 있을 때 헤드셋을 쓰는 기능은 macOS 의 `화면 기록` 권한이 있어야 동작해요 (창 제목을 읽기 위해서예요). 없어도 나머지 기능은 모두 동작해요.

- `PetApp` – 펫 본체 (파이썬 `my_pet.py` 포팅: 물리, 입력, 그리기, 설정 창)
- `platform/` – 창 감지 (윈도우: Win32, 맥: CoreGraphics/NSWorkspace, JNA 사용)
- `appearance/` – 외형 감지·설정·색상·렌더링
- `update/` – GitHub 릴리스 확인과 업데이트

---

## 라이선스 License
MY_PET 은 [GNU General Public License v3.0](LICENSE) 으로 배포해요.

- 자유롭게 쓰고, 고치고, 다시 배포할 수 있어요.
- 다만 고친 버전을 배포할 때는 **그 소스 코드도 같은 GPL v3 로 공개**해야 해요.

함께 들어 있는 부품의 라이선스:
- [JNA](https://github.com/java-native-access/jna) – Apache 2.0 / LGPL 2.1
- [FlatLaf](https://github.com/JFormDesigner/FlatLaf) – Apache 2.0
- Java 실행 환경, JavaFX ([Liberica JDK](https://bell-sw.com/libericajdk/)) – GPL v2 + Classpath Exception (설치 폴더의 `runtime\legal` 에 원문)

Copyright (C) 2026 janghg0929
