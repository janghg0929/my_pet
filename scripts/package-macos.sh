#!/usr/bin/env bash
# macOS 배포 패키지(MyPet-macos-<arch>.dmg) 만들기 — macOS 에서만 실행 가능 (GitHub Actions 가 대신 실행)
# 사용법: ./scripts/package-macos.sh 2.0.0
set -euo pipefail
VERSION="${1:?버전을 지정하세요 (예: 2.0.0)}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

./gradlew fatJar "-PappVersion=$VERSION" --console=plain

ARCH="$(uname -m)"
case "$ARCH" in
  arm64) ARCH_NAME=arm64 ;;
  *) ARCH_NAME=x64 ;;
esac

# 설정 창(HTML)에 JavaFX WebView 가 필요하다: JavaFX 가 포함된 JDK(예: Liberica Full JDK 21)를 JAVA_HOME 으로
if [ ! -f "${JAVA_HOME:-/nonexistent}/jmods/javafx.web.jmod" ]; then
  echo "JAVA_HOME 이 JavaFX 포함 JDK 가 아닙니다 (jmods/javafx.web.jmod 없음). Liberica Full JDK 21 을 지정하세요." >&2
  exit 1
fi

OUT="$ROOT/build/jpackage"
RELEASE="$ROOT/build/release"
rm -rf "$OUT"
mkdir -p "$RELEASE"

MODULES="java.base,java.desktop,java.logging,java.net.http,java.prefs,java.management,jdk.crypto.ec,jdk.charsets,jdk.localedata,jdk.unsupported,jdk.zipfs,javafx.base,javafx.graphics,javafx.controls,javafx.media,javafx.web,javafx.swing,jdk.jsobject"
"${JAVA_HOME:+$JAVA_HOME/bin/}jpackage" --type dmg \
  --name MyPet \
  --app-version "$VERSION" \
  --vendor "MY_PET" \
  --input "$ROOT/build/dist" \
  --main-jar MyPet.jar \
  --main-class com.mypet.Launcher \
  --icon "$ROOT/packaging/MyPet.icns" \
  --add-modules "$MODULES" \
  --java-options "-Dapple.awt.UIElement=true" \
  --mac-package-identifier com.mypet.app \
  --dest "$OUT"

mv "$OUT"/MyPet-*.dmg "$RELEASE/MyPet-macos-$ARCH_NAME.dmg"
cp "$ROOT/build/dist/MyPet.jar" "$RELEASE/MyPet.jar"
echo "완료: $RELEASE/MyPet-macos-$ARCH_NAME.dmg"
