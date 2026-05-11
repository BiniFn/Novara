# Cloudstream Runtime Source Migration

## Summary

Kototoro no longer depends on the sanitized `cloudstream3-pre-release.jar` for build-time integration.
The runtime has been moved to a source module and the parser API has been narrowed back to a generic page model.

## What Changed

- Added a new `:cloudstream-runtime` Android library module.
- Moved the Cloudstream runtime helpers and compatibility shims into source.
- Removed the app-side sanitized Cloudstream jar build pipeline.
- Moved video-specific playback metadata out of `parser-api`'s `ContentPage` usage path.
- Kept only compatibility constructors where needed for older parser jars.

## Boundary Updates

- `ContentPage` now stays generic: `id`, `url`, `preview`, `headers`, `source`.
- Cloudstream playback metadata is handled by app-internal playback models.
- `ParcelableContentPage` no longer serializes Cloudstream-only playback fields.

## Verification

```bash
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" ./gradlew :cloudstream-runtime:compileDebugKotlin :parser-api:compileDebugKotlin :app:compileDebugKotlin --no-daemon -Pksp.incremental=false
```

Result: `BUILD SUCCESSFUL`

## Notes

- The compatibility `ContentPage` constructor remains for old parser jars.
- `ContentExternalTrack` remains only as a compatibility type for the transition period.
