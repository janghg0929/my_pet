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
| Windows | `MyPet-windows-x64.zip` | 압축을 풀고 `MyPet` 폴더 안의 `MyPet.exe` 실행 |
| macOS (M1/M2/M3/M4…) | `MyPet-macos-arm64.dmg` | dmg 를 열고 MyPet 을 응용 프로그램 폴더로 끌어다 놓기 |
| macOS (인텔) | `MyPet-macos-x64.dmg` | 위와 같음 |

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

---

## 개발자용 Development

### 새 버전 배포하기
태그만 푸시하면 GitHub Actions 가 윈도우 zip, 맥 dmg, 업데이트용 `MyPet.jar` 를 빌드해서 릴리스를 만들어요.

```bash
git tag v2.0.1
git push origin v2.0.1
```

- 앱 버전은 태그에서 가져와요 (`v2.0.1` → `2.0.1`). 코드의 버전 번호를 고칠 필요 없어요.
- 사용자 앱은 시작할 때 `https://github.com/janghg0929/my_pet/releases/latest` 로 최신 태그를 확인해요.
  숫자 버전이 아닌 태그(예: 예전 `download`)는 무시해요.
- 업데이트는 `MyPet.jar` 만 받아서 앱 데이터 폴더(`%LOCALAPPDATA%\MyPet\app`, 맥은 `~/.my_pet/app`)에 두고,
  다음 실행 때 `Launcher` 가 그 jar 로 실행해요. 설치 폴더를 건드리지 않아서 관리자 권한이 필요 없어요.

### 설정 창 디자인 (HTML/CSS)
우클릭하면 나오는 설정 창은 HTML/CSS 로 그려요. 아래 파일만 고치면 디자인이 바뀌어요.

- `src/main/resources/com/mypet/web/settings.css` – 디자인 (색, 여백, 테두리 …)
- `src/main/resources/com/mypet/web/settings.html` – 화면 구조
- `src/main/resources/com/mypet/web/settings.js` – 동작 (Java 와 주고받기)

`settings.html` 을 일반 브라우저로 열면(로컬 서버 필요: 그 폴더에서 `python -m http.server`) 예시 데이터로 미리 볼 수 있어요.
HTML 화면은 JavaFX WebView 로 보여주는데, JavaFX 가 없는 런타임(예: 2.0.0 설치본에 자동 업데이트만 받은 경우)에서는 Swing 으로 만든 설정 창이 대신 나와요.

### 빌드 / 실행 / 테스트
패키지를 만들 때는 **JavaFX 가 포함된 JDK 21**(예: [Liberica Full JDK 21](https://bell-sw.com/pages/downloads/))을 `JAVA_HOME` 으로 지정해야 해요. GitHub Actions 는 자동으로 이 JDK 를 써요.


```bash
./gradlew fatJar                                  # build/dist/MyPet.jar
java -jar build/dist/MyPet.jar                    # 바로 실행 (업데이트 확인은 패키지로 실행할 때만)
./gradlew test
.\scripts\package-windows.ps1 -Version 2.0.0      # 윈도우 패키지 → build/release
./scripts/package-macos.sh 2.0.0                  # 맥 패키지 (macOS 에서만)
```

> 윈도우에서 프로젝트 경로에 한글이 있으면 `gradlew test` 가 `ClassNotFoundException` 으로 실패하는 Gradle 문제가 있어요. 영문 경로에서는 정상이고, GitHub Actions 에서도 문제없어요.

### 구조
- `Launcher` – 진입점. 내려받은 업데이트 jar 가 더 새 버전이면 그걸로 실행
- `PetApp` – 펫 본체 (파이썬 `my_pet.py` 포팅: 물리, 입력, 그리기, 설정 창)
- `platform/` – 창 감지 (윈도우: Win32, 맥: CoreGraphics/NSWorkspace, JNA 사용)
- `appearance/` – 외형 감지·설정·색상·렌더링
- `update/` – GitHub 릴리스 확인과 업데이트
