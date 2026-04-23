# GEMINI.md - Kototoro Project Context

This file provides essential context and instructions for the Kototoro project, an open-source Android application for manga, novels, and video.

## Project Overview
Kototoro is a comprehensive media reader for Android that integrates manga, novels, and video into a single application.
- **Core Features**: Local OCR + translation, video super-resolution (Anime4K), multi-platform progress tracking (MAL, Bangumi, etc.), and multi-device sync via WebDAV.
- **Extensibility**: Supports external source ecosystems like Mihon (Tachiyomi), Aniyomi, Legado, and TVBox. Uses a dynamic UI plugin system.
- **Technologies**: Pure Kotlin, Jetpack Compose (migrating), Room Database, Hilt DI, KSP, and VitePress for documentation.

## Architecture
The project is modularized and follows a Clean Architecture-inspired pattern (Data, Domain, UI layers).
- **`:app`**: Main application module.
  - `core/`: Database (`db`), Network (`network`), Parser Engine (`parser`), and Common Models.
  - `mihon/`, `aniyomi/`, `ireader/`: Integration layers for external extensions.
  - `reader/`: Manga/Novel reading logic.
  - `video/`: Video playback and super-resolution logic.
  - `image/`: OCR, translation, and image processing.
  - `sync/`: WebDAV synchronization.
- **`:parser-api`**: Shared interfaces for content parsers.
- **`docs/`**: Documentation source (VitePress).

## Building and Running
### Android Build
- **Assemble Debug APK**: `./gradlew :app:assembleDebug`
- **Assemble Release APK**: `./gradlew :app:assembleRelease` (requires signing config in `local.properties`)
- **Fast Compile**: `./gradlew :app:compileDebugKotlin --no-daemon`
- **Clean**: `./gradlew clean`

### Testing
- **JVM Unit Tests**: `./gradlew :app:testDebugUnitTest --no-daemon`
- **Instrumented Tests**: `./gradlew :app:connectedDebugAndroidTest`
- **Full Check**: `./gradlew :app:check`

### Documentation
- **Run Docs Dev Server**: `npm run docs:dev`
- **Build Docs**: `npm run docs:build`

## Development Conventions
- **Code Style**:
  - Follow `.editorconfig` (4-space indent, 120 char limit).
  - Kotlin-first, PascalCase for classes, camelCase for methods/properties.
  - Android resources: `lower_snake_case`.
- **Git Commits**: Use [Conventional Commits](https://www.conventionalcommits.org/) (e.g., `feat:`, `fix(reader):`, `docs:`).
- **Database**:
  - Room schemas are in `app/schemas/`.
  - Migrations are implemented in `core/db/migrations/`.
  - Always update the schema and register migrations in `KototoroDatabase`.
- **Translations**: Strings are managed via Weblate; avoid direct bulk edits to `strings.xml`.
- **Dependency Injection**: Uses Hilt. Run KSP processors after modifying injected classes.

## Important Files
- `CLAUDE.md`: Detailed developer guide and command reference.
- `gradle/libs.versions.toml`: Centralized dependency management.
- `docs/architecture/`: Detailed design documents for core systems.
- `.github/RELEASE_GUIDE.md`: Instructions for the release process.
