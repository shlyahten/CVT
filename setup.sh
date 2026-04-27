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

+++ setup.sh (修改后)
#!/bin/bash
set -e

echo "=== Android Project Setup ==="

# Check if JAVA_HOME is set, if not try to find Java
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
    echo "JAVA_HOME set to: $JAVA_HOME"
fi

# Verify Java version (project requires Java 11+)
java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "Java version: $java_version"
if [ "$java_version" -lt 11 ]; then
    echo "ERROR: Java 11 or higher is required. Current version: $java_version"
    exit 1
fi

# Set up Android SDK directory
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
echo "Android SDK will be installed to: $ANDROID_SDK_ROOT"

# Create SDK directory if it doesn't exist
mkdir -p "$ANDROID_SDK_ROOT"

# Download Android command-line tools if not already present
if [ ! -f "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "Downloading Android command-line tools..."
    CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip"
    TEMP_ZIP="/tmp/android-commandlinetools.zip"

    wget -O "$TEMP_ZIP" "$CMDLINE_TOOLS_URL"

    # Create cmdline-tools structure
    mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
    unzip -q "$TEMP_ZIP" -d "$ANDROID_SDK_ROOT/cmdline-tools"

    # Rename 'cmdline-tools' folder to 'latest'
    if [ -d "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" ]; then
        mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
    fi

    rm "$TEMP_ZIP"
    echo "Command-line tools installed."
else
    echo "Command-line tools already present."
fi

# Accept all licenses
echo "Accepting Android SDK licenses..."
yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true

# Install required SDK components
echo "Installing Android SDK components..."
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
    "platforms;android-36" \
    "platform-tools" \
    "build-tools;36.1.0"

# Initialize Git submodules if any
echo "Initializing Git submodules..."
git submodule update --init --recursive

# Update PATH
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

echo ""
echo "=== Setup Complete ==="
echo "Environment variables set:"
echo "  JAVA_HOME=$JAVA_HOME"
echo "  ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
echo "  ANDROID_HOME=$ANDROID_HOME"
echo ""
echo "To use in current shell, run:"
echo "  export ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
echo "  export ANDROID_HOME=$ANDROID_SDK_ROOT"
echo "  export PATH=\$ANDROID_SDK_ROOT/platform-tools:\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$PATH"
echo ""
echo "You can now build the project with: ./gradlew assembleDebug"
