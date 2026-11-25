#!/usr/bin/env bash
set -euo pipefail

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$RUNNER_TEMP/android-sdk}}"
sdk_manager="$(command -v sdkmanager || true)"
if [[ -z "$sdk_manager" && -x "$sdk_root/cmdline-tools/latest/bin/sdkmanager" ]]; then
  sdk_manager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
fi

if [[ -z "$sdk_manager" ]]; then
  tools_dir="$(mktemp -d)"
  archive="$tools_dir/commandlinetools.zip"
  curl --fail --location --silent --show-error --retry 3 \
    https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip \
    --output "$archive"
  printf '%s  %s\n' 4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583 "$archive" | sha256sum --check
  unzip -q "$archive" -d "$tools_dir"
  mkdir -p "$sdk_root/cmdline-tools/15859902"
  cp -a "$tools_dir/cmdline-tools/." "$sdk_root/cmdline-tools/15859902/"
  sdk_manager="$sdk_root/cmdline-tools/15859902/bin/sdkmanager"
fi

(set +o pipefail; yes | "$sdk_manager" --sdk_root="$sdk_root" --licenses >/dev/null)
"$sdk_manager" --sdk_root="$sdk_root" 'platform-tools' 'platforms;android-37.1'
printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$sdk_root" "$sdk_root" >> "$GITHUB_ENV"
