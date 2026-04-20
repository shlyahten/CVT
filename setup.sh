echo "Getting Android Studio"
wget -O android-commandlinetools.zip https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip

echo "Unpacking Android Studio"
unzip android-commandlinetools.zip -d /usr/lib/android-sdk
rm android-commandlinetools.zip

echo "Updating sdkmanager"
/usr/lib/android-sdk/cmdline-tools/bin/sdkmanager --sdk_root=/usr/lib/android-sdk/ --update

echo "Installing Android SDK"
bash -c 'yes | /usr/lib/android-sdk/cmdline-tools/bin/sdkmanager --sdk_root=/usr/lib/android-sdk/ "platforms;android-36" \
  "platform-tools" \
  "build-tools;36.1.0"'

echo "Git Submodule Init"
git submodule update --init --recursive

export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/bin:$PATH"
echo "Starting complete"
