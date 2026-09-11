#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "== ABODI DEX / Termux build =="
command -v java >/dev/null || { echo "Java 17 غير مثبت. نفّذ: pkg install openjdk-17 -y"; exit 1; }
command -v gradle >/dev/null || { echo "Gradle غير مثبت. نفّذ: pkg install gradle -y"; exit 1; }

if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -d "$HOME/android-sdk" ]; then
    export ANDROID_HOME="$HOME/android-sdk"
  elif [ -d "$PREFIX/opt/android-sdk" ]; then
    export ANDROID_HOME="$PREFIX/opt/android-sdk"
  fi
fi

if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME" ]; then
  echo "ANDROID_HOME غير مضبوط. ثبّت Android SDK واضبط المسار أولاً."
  exit 1
fi

export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

gradle --no-daemon assembleDebug

echo
echo "تم البناء بنجاح:"
echo "app/build/outputs/apk/debug/app-debug.apk"
