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

OUT="$ROOT/build/jpackage"
RELEASE="$ROOT/build/release"
rm -rf "$OUT"
mkdir -p "$RELEASE"

MODULES="java.base,java.desktop,java.logging,java.net.http,java.prefs,java.management,jdk.crypto.ec,jdk.charsets,jdk.localedata,jdk.unsupported,jdk.zipfs"
"${JAVA_HOME:+$JAVA_HOME/bin/}jpackage" --type dmg \
  --name MyPet \
  --app-version "$VERSION" \
  --vendor "MY_PET" \
  --input "$ROOT/build/dist" \
  --main-jar MyPet.jar \
  --main-class com.mypet.Launcher \
  --add-modules "$MODULES" \
  --java-options "-Dapple.awt.UIElement=true" \
  --mac-package-identifier com.mypet.app \
  --dest "$OUT"

mv "$OUT"/MyPet-*.dmg "$RELEASE/MyPet-macos-$ARCH_NAME.dmg"
cp "$ROOT/build/dist/MyPet.jar" "$RELEASE/MyPet.jar"
echo "완료: $RELEASE/MyPet-macos-$ARCH_NAME.dmg"
