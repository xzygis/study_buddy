#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT_DIR"

: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 installation.}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory.}"

./gradlew :android:testDebugUnitTest :android:lintDebug :android:assembleDebug

APK="$ROOT_DIR/android/build/outputs/apk/debug/android-debug.apk"
test -f "$APK"
echo "Verified APK: $APK"
