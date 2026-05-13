# TVBox Runtime Compatibility

This page records the current engineering position for TVBox support in Kototoro. It is intentionally scoped to runtime compatibility, not UI parity with TVBox shells.

## Current Position

Kototoro is not at the "TVBox runtime does not exist" stage anymore. The project already has:

- TVBox JSON import for single-repository and multi-repository configurations
- per-site normalized storage instead of storing only the original whole JSON document
- TVBox source management in the JSON source directory and unified source screens
- `type = 4` routing through a QuickJS-based runtime
- `type = 3` / `csp_*` routing through a local JAR spider runtime
- fallback handling for direct media, playlists, text live lists, and simple CMS-style APIs

The unresolved work is compatibility depth. In particular, ordinary JAR spiders and Guard-native JAR spiders must be treated as different classes of runtime behavior.

## Support Matrix

| TVBox source shape | Current status | Expected direction |
| --- | --- | --- |
| Direct media URLs | Supported when the imported config exposes usable playback URLs | Keep stable and avoid unnecessary spider execution |
| M3U / text live lists / simple playlists | Supported for simpler configurations | Improve parsing coverage and diagnostics |
| Simple CMS-style APIs | Partially supported through fallback candidates | Improve candidate detection and per-request logs |
| `type = 4` JavaScript sources | Basic QuickJS bridge exists | Fill gaps around `cat.js`, dependency loading, `js2Proxy`, modules, and unsupported bytecode formats |
| Ordinary `type = 3` / `csp_*` JAR spiders | Local runtime exists and follows the usual TVBoxOS-style Java lifecycle closely enough for continued work | Keep improving host ABI shims, missing classes, proxy handling, and diagnostics |
| Guard-native JAR spiders | Not reliably supported locally | Do not treat as ordinary JAR failures; isolate, classify, and degrade instead of repeatedly crashing local runtime |

## Ordinary JAR vs Guard-Native JAR

Ordinary TVBox JAR spiders are Java/Kotlin bytecode loaded through a `DexClassLoader`, instantiated by class name, initialized with a TVBox-style context, and called through methods such as `homeContent`, `categoryContent`, `detailContent`, `searchContent`, `playerContent`, and `proxyLocal`.

Guard-native JARs are different. They may ship encrypted guard payloads and native libraries, then delegate real spider creation through JNI/native code. Previous investigation showed the failure point had already moved past simple Java-layer issues such as missing `Init.init(context)` or a null `Context.getCacheDir()`. The remaining failures entered native/JNI crash territory.

Therefore:

- Do not classify Guard-native failures as "ordinary JAR runtime missing one more stub."
- Do not repeatedly execute a known fatal Guard source in the main runtime path.
- Keep the local JAR runtime useful for ordinary spiders.
- Surface Guard-native limitations explicitly in logs and user-facing support status.

## Already Tried

The project has already explored more than one approach:

- importing TVBox JSON sources as normalized per-site sources
- supporting multi-repository TVBox JSON files
- adding a QuickJS bridge for `type = 4`
- adding a local `DexClassLoader` runtime for `type = 3` / `csp_*`
- aligning the Java-level loading sequence with TVBoxOS-style shells
- adding TVBox / CatVod host compatibility stubs
- experimenting with an isolated companion / worker process path
- comparing Guard behavior against TVBoxOS-style loading

The important conclusion from that work is narrow: local Java-layer alignment remains valuable for ordinary spiders, but Guard-native JARs are a separate native compatibility problem.

## Diagnostic Policy

When a TVBox source fails, classify the failure before changing runtime code:

- `json_import`: the source JSON could not be fetched, parsed, or normalized
- `multi_repo`: a child repository failed to resolve or produced no valid sites
- `direct_media`: the config exposed a media URL that could not be played directly
- `cms_fallback`: CMS candidate detection or CMS request failed
- `quickjs_missing_feature`: the JavaScript runtime lacks a needed bridge feature
- `ordinary_jar_missing_class`: a local JAR spider references a missing host class
- `ordinary_jar_missing_method`: a local JAR spider references an incompatible host method
- `ordinary_jar_proxy`: `proxy` / `proxyLocal` handling is incomplete
- `guard_native`: a Guard-native spider hits native/JNI failure or is known to require native guard behavior

This keeps fixes small and prevents unrelated runtime paths from being destabilized.

## Product Guidance

TVBox support should be described as a compatibility spectrum:

- stable for direct media, playlists, and simpler JSON/CMS sources
- improving for QuickJS and ordinary JAR spiders
- limited for Guard-native JARs

Avoid promising full compatibility with every TVBox repository. Many public TVBox lists mix direct sources, CMS sources, ordinary spiders, JavaScript spiders, and Guard-native spiders in one file, so a repository can be partially usable even when some entries are not.

## Next Engineering Steps

1. Add explicit runtime classification logs for TVBox source failures.
2. Expose support status per TVBox source using the same categories as the diagnostic policy.
3. Continue ordinary JAR compatibility work only when logs show Java-layer missing class, missing method, initialization, proxy, or response parsing failures.
4. Treat Guard-native failures as isolated limitations unless a dedicated safe execution environment is selected later.
5. Improve multi-repository import diagnostics so each child repository reports fetch, parse, normalization, and emitted-site counts.
