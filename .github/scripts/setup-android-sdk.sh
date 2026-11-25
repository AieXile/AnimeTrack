#!/usr/bin/env bash
set -euo pipefail

compile_sdk="$(sed -nE 's/^animetrack\.compileSdk[[:blank:]]*=[[:space:]]*([^[:space:]]*)[[:space:]]*$/\1/p' gradle.properties)"
if [[ -z "$compile_sdk" ]]; then
  # 旧标签在 App 的构建文件中直接声明 compileSdk，包含 v1.0.0 的主次版本写法。
  compile_sdk="$(sed -nE \
    -e 's/^[[:blank:]]*compileSdk[[:blank:]]*=[[:blank:]]*([0-9]+)[[:space:]]*$/\1/p' \
    -e 's/^[[:blank:]]*version[[:blank:]]*=[[:blank:]]*release\(([0-9]+)\)[[:blank:]]*\{[[:blank:]]*minorApiLevel[[:blank:]]*=[[:blank:]]*([0-9]+)[[:blank:]]*\}[[:space:]]*$/\1.\2/p' \
    app/build.gradle.kts)"
fi
if [[ ! "$compile_sdk" =~ ^[1-9][0-9]*(\.(0|[1-9][0-9]*))?$ ]]; then
  echo 'Cannot determine compileSdk from the checked-out Android sources.' >&2
  exit 1
fi
sdk_platform="platforms;android-${compile_sdk%.0}"
printf 'Installing %s for compileSdk %s\n' "$sdk_platform" "$compile_sdk"

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$RUNNER_TEMP/android-sdk}}"
tools_version=15859902
tools_sha256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
tools_dir="$(mktemp -d)"
trap 'rm -rf -- "$tools_dir"' EXIT
archive="$tools_dir/commandlinetools.zip"
curl --fail --location --silent --show-error --retry 3 \
  "https://dl.google.com/android/repository/commandlinetools-linux-${tools_version}_latest.zip" \
  --output "$archive"
printf '%s  %s\n' "$tools_sha256" "$archive" | sha256sum --check
unzip -q "$archive" -d "$tools_dir"
sdk_manager="$tools_dir/cmdline-tools/bin/sdkmanager"

mkdir -p "$sdk_root"
(set +o pipefail; yes | "$sdk_manager" --sdk_root="$sdk_root" --licenses >/dev/null)
"$sdk_manager" --sdk_root="$sdk_root" 'platform-tools' "$sdk_platform"
printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$sdk_root" "$sdk_root" >> "$GITHUB_ENV"
