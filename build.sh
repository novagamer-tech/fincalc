#!/bin/bash
# FinCalc build: plain javac + aapt2 + d8, no Gradle.
# Requires JDK 17 and an Android SDK with platform android-34 and build-tools 34.0.0.
set -e
cd "$(dirname "$0")"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 installation}"
: "${ANDROID_SDK:?Set ANDROID_SDK to your Android SDK directory}"
export PATH="$JAVA_HOME/bin:$PATH"
ANDROID_JAR="$ANDROID_SDK/platforms/android-34/android.jar"
BT="$ANDROID_SDK/build-tools/34.0.0"
BUILD=build
rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"

# 1. Compile + link resources
"$BT/aapt2" compile --dir res -o "$BUILD/res.zip"
"$BT/aapt2" link -o "$BUILD/app-unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  -R "$BUILD/res.zip" \
  --java "$BUILD/gen" \
  --auto-add-overlay

# 2. Compile Java
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -cp "$ANDROID_JAR" \
  -d "$BUILD/classes" \
  src/com/noufal/fincalc/MainActivity.java

# 3. Dex
"$BT/d8" --lib "$ANDROID_JAR" --min-api 26 --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class')

# 4. Add dex to APK, align
(cd "$BUILD/dex" && zip -q -j "../app-unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$BUILD/app-unsigned.apk" "$BUILD/app-aligned.apk"

# 5. Sign with a debug key (generated on first build)
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -storepass android \
    -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android \
  --key-pass pass:android --out FinCalc.apk "$BUILD/app-aligned.apk"

"$BT/apksigner" verify FinCalc.apk
echo "BUILD OK: FinCalc.apk"
