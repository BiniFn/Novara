# Standalone Plugin Repository Guide

To maintain a plugin collection (e.g., `kototoro-parsers`) independently from the main Kototoro app, you should create a dedicated GitHub repository. This repository will automatically compile the parsers, convert them into Android-compatible Dalvik bytecode (via the `d8` dexer), and publish them as `.jar` releases using GitHub Actions.

## 1. Project Structure
Your new repository should be a standard Kotlin/JVM or Android Library project.
```text
kototoro-parsers/
├── build.gradle.kts
├── settings.gradle.kts
├── .github/
│   └── workflows/
│       └── release.yml
└── src/
    └── main/
        └── kotlin/
            └── org/Kototoro-app/Kototoro/parsers/
                └── ... (Your parser implementations)
```

## 2. Depending on `parser-api`
Since the plugin must implement Kototoro's `ContentParser` and `ContentSource` interfaces without bundling them (to achieve zero-overhead class loading), it must pull `parser-api` as a **compile-only** dependency.

To do this, you can expose the `parser-api` module via **JitPack**.
Ensure the main Kototoro repository has JitPack enabled, and then in your plugin's `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // Rely on exactly the API surface, but DO NOT bundle it into the final jar
    compileOnly("com.github.skepsun.Kototoro:parser-api:main-SNAPSHOT")
    
    // You can bundle specific dependencies like jsoup if Kototoro doesn't provide them,
    // though Kototoro provides okhttp and jsoup from the context natively in most cases.
    compileOnly("org.jsoup:jsoup:1.17.2") 
}

tasks.jar {
    archiveBaseName.set("kototoro-parsers-raw")
}
```

## 3. GitHub Actions CI/CD Pipeline
Android requires `.dex` bytecode rather than standard Java `.class` bytecode. We use the Android SDK's **`d8`** tool to convert the generated JAR. 

Create `.github/workflows/release.yml` in your parsers repository:

```yaml
name: Build and Release Plugin

on:
  push:
    tags:
      - 'v*' # Triggers when you create a new tag like v1.0.0

jobs:
  build_plugin:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build Raw JAR
        run: ./gradlew jar --no-daemon

      - name: Convert to Dalvik Bytecode (d8 Dex)
        run: |
          # The ubuntu-latest runner comes with the Android SDK pre-installed.
          # Find the latest build-tools directory
          BUILD_TOOLS_DIR=$(ls -d $ANDROID_HOME/build-tools/* | tail -1)
          D8_PATH="$BUILD_TOOLS_DIR/d8"
          ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
          
          cd build/libs
          
          # Convert the standard Desktop jar to Android Dex
          # This creates a classes.dex file
          $D8_PATH --release --lib $ANDROID_JAR --output . kototoro-parsers-raw.jar
          
          # Package the classes.dex into the final distribution plugin.jar
          jar cvf kototoro-parsers.jar classes.dex
          
      - name: Publish GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          files: build/libs/kototoro-parsers.jar
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## 4. Development Workflow
1. Write and update your parsers in the standalone repository.
2. Push your code.
3. When ready to distribute, create a GitHub Tag (e.g., `git tag v1.0.1 && git push origin v1.0.1`).
4. GitHub Actions will automatically catch the tag, compile the code, run the `d8` conversion, and upload `kototoro-parsers.jar` to a new GitHub Release.
5. Users download the `.jar` from GitHub Releases and use the **Import Plugin** button in Kototoro to install it.
