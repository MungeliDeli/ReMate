#!/usr/bin/env bash
# Installs the Android command-line tools + the SDK packages ReplyMate needs.
# Safe to re-run. Used by the devcontainer and usable by hand in any Codespace.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-14742923_latest.zip"
PLATFORM="platforms;android-37.0"
BUILD_TOOLS="build-tools;36.0.0"   # AGP 9.4 default

mkdir -p "$ANDROID_HOME/cmdline-tools"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Downloading Android command-line tools..."
  tmp="$(mktemp -d)"
  curl -fsSL "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" -o "$tmp/tools.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses > /dev/null || true
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" "platform-tools" "$PLATFORM" "$BUILD_TOOLS"

# Let Gradle find the SDK even if ANDROID_HOME isn't exported in a shell.
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
echo "sdk.dir=$ANDROID_HOME" > "$repo_root/local.properties"
echo "Android SDK ready at $ANDROID_HOME"
