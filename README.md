RTKcat

RTKcat — Android Application Project by TyagiRTK

A reproducible Android application project designed to build reliably on ARM64 Ubuntu environments, including Termux + Ubuntu/PRoot.

RTKcat uses the official RTK Android Portable Toolchain to standardize the Android build environment and reduce Java, Gradle, Android SDK, NDK, CMake and AAPT2 compatibility problems.

---

🚀 Quick Start

For a fresh ARM64 Ubuntu / Termux-Proot environment:

git clone https://github.com/ritiksinghbabura-tyagirtk/RTKcat.git
cd RTKcat

chmod +x setup-rtk.sh doctor.sh build.sh

./setup-rtk.sh
./doctor.sh
./build.sh

The generated debug APK will normally be available under:

app/build/outputs/apk/debug/

---

🔗 Official Build Environment

RTKcat does not commit the complete Android SDK/JDK toolchain into this repository.

Instead, the project uses the official:

RTK Android Portable Toolchain

Repository:

https://github.com/tyagirtk-dev/ubuntu_rtk_apk_maker

The toolchain provides the validated ARM64 Android build environment required by this project.

Standard RTK Toolchain

Component| Version
Architecture| ARM64 / AArch64
Environment| Ubuntu / Termux-Proot
Java| OpenJDK 21.0.12
Android SDK| API 35
Build Tools| 35.0.0
NDK| 26.3.11579264
CMake| 3.30.3
Gradle| 8.10.2
AAPT2| ARM64
Platform Tools| Included
Command Line Tools| Included

Keeping the build environment standardized makes the project easier to reproduce on another ARM64 device.

---

📱 Who Is This Project For?

RTKcat documentation is intended for multiple experience levels.

Beginner

If you have:

- an Android phone
- Termux
- Ubuntu/PRoot
- no traditional PC

you can follow the setup from the beginning.

Intermediate Developer

You can clone the project, configure the environment and use the Gradle wrapper directly.

Professional Developer

The repository provides a predictable toolchain baseline, health checks and reproducible build workflow suitable for maintaining the project over time.

---

🧭 Architecture

Android Phone
      │
      ▼
   Termux
      │
      ▼
 Ubuntu / PRoot
      │
      ▼
RTK Android Toolchain
      │
      ├── JDK 21
      ├── Android SDK 35
      ├── Build Tools 35.0.0
      ├── NDK 26.3.11579264
      ├── CMake 3.30.3
      ├── Gradle 8.10.2
      └── ARM64 AAPT2
      │
      ▼
    RTKcat
      │
      ▼
 Gradle Wrapper
      │
      ▼
     APK

---

🛠️ Requirements

Hardware

ARM64 / AArch64 device or system.

Check:

uname -m

Expected:

aarch64

Operating Environment

Primary supported environment:

Ubuntu ARM64

Termux + Ubuntu/PRoot is also supported by the RTK toolchain.

---

⚙️ Environment Setup

RTKcat uses:

ubuntu_rtk_apk_maker

as its standard Android build environment.

The project should not require every developer to manually install Java, Android SDK, NDK, CMake and AAPT2 separately.

Run:

./setup-rtk.sh

The setup script prepares the official RTK Android toolchain.

After installation, the environment provides variables including:

JAVA_HOME
ANDROID_HOME
ANDROID_SDK_ROOT
GRADLE_HOME
RTK_ANDROID_TOOLCHAIN
RTK_AAPT2

You can verify the environment with:

./doctor.sh

---

🩺 Health Check

Before troubleshooting a Gradle build, run:

./doctor.sh

The doctor checks the important parts of the build environment.

Typical checks include:

[OK] Architecture
[OK] Java
[OK] Android SDK
[OK] Android API 35
[OK] Build Tools 35.0.0
[OK] NDK 26.3.11579264
[OK] CMake 3.30.3
[OK] Gradle
[OK] ARM64 AAPT2
[OK] Gradle Wrapper

If the environment check fails, fix the environment before changing application source code.

---

🔨 Build the Project

Make sure the Gradle wrapper is executable:

chmod +x ./gradlew

Build a debug APK:

./gradlew assembleDebug

The APK is normally generated under:

app/build/outputs/apk/debug/

---

📦 Release Build

For a release build:

./gradlew assembleRelease

Release builds may require signing configuration depending on the project configuration.

Never commit private signing keys or passwords to GitHub.

---

🧹 Clean Build

If Gradle produces stale or inconsistent generated output:

./gradlew clean
./gradlew assembleDebug

You can also remove generated build output:

rm -rf app/build

Generated build directories are intentionally excluded from Git.

---

🔍 Useful Diagnostics

Java

java -version

Gradle

./gradlew --version

Android SDK

echo "$ANDROID_HOME"

AAPT2

"$RTK_AAPT2" version

Architecture

uname -m

Environment

env | grep -E 'JAVA_HOME|ANDROID_HOME|ANDROID_SDK_ROOT|GRADLE_HOME|RTK_'

---

📁 Project Structure

The project follows a standard Android/Gradle structure.

RTKcat/
│
├── app/
│   └── src/
│
├── gradle/
│   └── wrapper/
│
├── gradlew
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
│
├── setup-rtk.sh
├── doctor.sh
├── build.sh
│
├── .gitignore
└── README.md

Generated files such as Gradle caches and build outputs are not part of the source repository.

---

🔐 Security

Do not commit:

local.properties
*.jks
*.keystore
*.p12
*.pem
*.key

Do not commit:

- API keys
- Telegram bot tokens
- passwords
- private signing keys
- personal credentials
- production secrets

Use environment variables, Android Keystore or another appropriate secure storage mechanism for sensitive values.

---

🧠 Beginner Workflow

If you are completely new to the project:

1. Clone

git clone https://github.com/ritiksinghbabura-tyagirtk/RTKcat.git
cd RTKcat

2. Setup environment

./setup-rtk.sh

3. Check environment

./doctor.sh

4. Build

./build.sh

5. Find APK

find app/build/outputs -name "*.apk" -type f

---

👨‍💻 Professional Workflow

For development:

git pull --rebase
./doctor.sh
./gradlew assembleDebug

Before committing:

git status
git diff

Then:

git add .
git commit -m "Describe the change"
git push

Never commit generated build artifacts or local machine configuration.

---

🧩 Why Use a Separate Toolchain Repository?

The Android build environment is intentionally separated from the application source.

This provides a clean relationship:

RTK Android Toolchain
        │
        │ provides build environment
        ▼
      RTKcat
        │
        │ contains application source
        ▼
       APK

The toolchain repository can evolve independently while RTKcat documentation continues to define the expected compatible environment.

This also avoids storing multi-gigabyte SDK/JDK/NDK files inside the application Git repository.

---

🛠️ Troubleshooting

"Permission denied"

Run:

chmod +x setup-rtk.sh doctor.sh build.sh gradlew

---

"JAVA_HOME" is wrong

Check:

echo "$JAVA_HOME"
java -version

Then reload the environment:

source "$HOME/RTK_ANDROID_TOOLCHAIN/env.sh"

---

Android SDK not found

Check:

echo "$ANDROID_HOME"
echo "$ANDROID_SDK_ROOT"

Then:

./doctor.sh

---

AAPT2 error

Check:

echo "$RTK_AAPT2"
"$RTK_AAPT2" version

The RTK toolchain provides an ARM64 AAPT2 specifically for ARM64 environments.

---

Gradle build fails

First run:

./doctor.sh

Then:

./gradlew --version

Then retry:

./gradlew clean
./gradlew assembleDebug

Do not immediately delete the entire project or reinstall everything.

---

♻️ Reproducibility

RTKcat aims to keep the build environment predictable.

The project documents a known toolchain baseline instead of relying on whatever versions happen to be installed on a developer's machine.

This helps reduce:

- Java/Gradle incompatibility
- Android SDK version mismatch
- Build Tools mismatch
- NDK mismatch
- CMake mismatch
- AAPT2 architecture problems
- inconsistent ARM64 build environments

---

🤝 Contributing

Contributions and improvements are welcome.

Before submitting changes:

1. Keep ARM64 compatibility in mind.
2. Avoid committing generated files.
3. Never commit secrets.
4. Test the Gradle build.
5. Run "./doctor.sh".
6. Update documentation when build requirements change.
7. Keep toolchain version references accurate.

---

📄 License

See the repository license for the applicable terms.

---

🔗 Related Project

RTK Android Portable Toolchain

Official build environment:

https://github.com/tyagirtk-dev/ubuntu_rtk_apk_maker

RTKcat depends conceptually on this toolchain for its standardized ARM64 Android build environment.

---

RTK Development Philosophy

Source Code
    ↓
Known Toolchain
    ↓
Health Check
    ↓
Reproducible Build
    ↓
APK

Build once. Verify everything. Keep the environment reproducible.
