#!/bin/bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$project_dir/Verification"
mkdir -p "$project_dir/.xcode-cache/CompilationCache.noindex"
mkdir -p "$project_dir/.xcode-cache/SDKStatCaches.noindex"
swift test --package-path "$project_dir" 2>&1 | tee "$project_dir/Verification/core-tests.log"
xcodebuild \
  -project "$project_dir/StudyAlarm.xcodeproj" \
  -target StudyAlarm \
  -sdk iphoneos \
  -configuration Debug \
  SYMROOT="$project_dir/build" \
  OBJROOT="$project_dir/build" \
  CACHE_ROOT="$project_dir/.xcode-cache" \
  COMPILATION_CACHE_CAS_PATH="$project_dir/.xcode-cache/CompilationCache.noindex" \
  SDK_STAT_CACHE_DIR="$project_dir/.xcode-cache/SDKStatCaches.noindex" \
  CODE_SIGNING_ALLOWED=NO \
  build 2>&1 | tee "$project_dir/Verification/build-device.log"
printf '\nCore tests and unsigned iOS build completed. Device ringing is NOT verified by this script.\n'
