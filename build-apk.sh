#!/usr/bin/env bash
# Compila Ports PS2 sin Gradle: aapt2 + javac + d8 + zipalign + apksigner.
# Uso: ./build-apk.sh   -> genera app-debug.apk (firmado con keystore debug)
set -euo pipefail
cd "$(dirname "$0")"

# --- Toolchain -------------------------------------------------------------
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -d "$HOME/workspace/android-toolchain/jdk" ]; then
    export JAVA_HOME="$HOME/workspace/android-toolchain/jdk"
  elif command -v javac >/dev/null 2>&1; then
    export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
  fi
fi
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"

if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
  elif [ -d "$HOME/workspace/android-toolchain/sdk" ]; then
    export ANDROID_HOME="$HOME/workspace/android-toolchain/sdk"
  elif [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
  fi
fi

BT_VERSION="34.0.0"
PLATFORM_VERSION="34"
BT="$ANDROID_HOME/build-tools/$BT_VERSION"
ANDROID_JAR="$ANDROID_HOME/platforms/android-$PLATFORM_VERSION/android.jar"

for tool in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$ANDROID_JAR"; do
  if [ ! -e "$tool" ]; then
    echo "Falta: $tool" >&2
    echo "Instalá el Android SDK (platform android-$PLATFORM_VERSION y build-tools $BT_VERSION) o definí ANDROID_HOME." >&2
    exit 1
  fi
done
command -v javac >/dev/null 2>&1 || { echo "Falta javac (JDK 17)." >&2; exit 1; }

# --- Build -----------------------------------------------------------------
BUILD=build
rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/gen"

echo "==> aapt2 link (manifest + recursos)"
"$BT/aapt2" link \
  -o "$BUILD/app-unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  --java "$BUILD/gen" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 3 --version-name "1.2"

echo "==> javac"
find src "$BUILD/gen" -name "*.java" > "$BUILD/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 \
  -classpath "$ANDROID_JAR" \
  -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "==> d8 (dex)"
(cd "$BUILD/classes" && jar cf ../classes.jar .)
"$BT/d8" --min-api 26 --output "$BUILD/dex" "$BUILD/classes.jar"

echo "==> empaquetar classes.dex"
cp "$BUILD/app-unsigned.apk" "$BUILD/app-with-dex.apk"
(cd "$BUILD/dex" && zip -q -X "../app-with-dex.apk" classes.dex)

echo "==> zipalign"
"$BT/zipalign" -f -p 4 "$BUILD/app-with-dex.apk" "$BUILD/app-aligned.apk"

echo "==> firma debug"
KEYSTORE="$BUILD/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -v -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign \
  --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out app-debug.apk "$BUILD/app-aligned.apk"

echo "==> verificar"
"$BT/apksigner" verify --print-certs app-debug.apk | head -3
echo "Listo: app-debug.apk"
