---
name: haze-patterns
description: Expert guidance on common Haze usage patterns and recipes. Use when implementing Scaffold blur, sticky headers, overlapping effects, dialog blur, or deep hierarchy state sharing with Haze.
---

# Haze Patterns — Common Recipes

## Instructions

### 1. Scaffold with Blurred App Bars

Multiple `hazeEffect` modifiers share one `HazeState`:

```kotlin
val hazeState = rememberHazeState()

Scaffold(
    topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.largeTopAppBarColors(Color.Transparent),
            modifier = Modifier.hazeEffect(hazeState) {
                blurEffect { style = HazeMaterials.thin() }
            }
        )
    },
    bottomBar = {
        NavigationBar(
            containerColor = Color.Transparent,
            modifier = Modifier.hazeEffect(hazeState) {
                blurEffect { style = HazeMaterials.thin() }
            }
        )
    }
) { padding ->
    LazyColumn(
        modifier = Modifier.padding(padding).hazeSource(hazeState)
    ) { /* content */ }
}
```

> [!KEY]
> App bars must have transparent background colors to see the blur effect.

### 2. Dialog Blur

Blur dialog backgrounds over underlying content:

```kotlin
val hazeState = rememberHazeState()
var showDialog by remember { mutableStateOf(false) }

Box {
    // Mark background as blur source FIRST
    LazyVerticalGrid(
        modifier = Modifier.hazeSource(state = hazeState)
    ) { /* content */ }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
            ) {
                Box(Modifier.hazeEffect(state = hazeState) {
                    blurEffect { style = HazeMaterials.regular() }
                }) {
                    // dialog content
                }
            }
        }
    }
}
```

> [!IMPORTANT]
> With dialogs, `colorEffects` tints display as a scrim. Use a translucent `color` on the dialog `Surface` instead.

### 3. Sticky Headers

Apply `hazeSource` on all non-header items, `hazeEffect` on the sticky header:

```kotlin
val hazeState = rememberHazeState()

LazyColumn {
    stickyHeader {
        Header(
            modifier = Modifier.hazeEffect(hazeState) {
                blurEffect { style = HazeMaterials.thin() }
            }
        )
    }
    items(list) { item ->
        Foo(modifier = Modifier.hazeSource(hazeState))
    }
}
```

> Avoid recursive drawing: don't put `hazeSource` on the `LazyColumn` and `hazeEffect` on items within it.

### 4. Overlapping Blurred Cards

A composable can be both source and effect target simultaneously using `zIndex`:

```kotlin
Box {
    val hazeState = rememberHazeState()

    // Background at zIndex 0
    AsyncImage(
        modifier = Modifier.fillMaxSize().hazeSource(hazeState, zIndex = 0f),
        model = "...", contentDescription = null
    )

    // Rear card — draws background blur, serves as source for cards above
    CreditCard(
        modifier = Modifier
            .hazeSource(hazeState, zIndex = 1f)
            .hazeEffect(hazeState) { blurEffect { style = HazeMaterials.thin() } }
    )

    // Middle card
    CreditCard(
        modifier = Modifier
            .hazeSource(hazeState, zIndex = 2f)
            .hazeEffect(hazeState) { blurEffect { style = HazeMaterials.thin() } }
    )

    // Front card
    CreditCard(
        modifier = Modifier
            .hazeSource(hazeState, zIndex = 3f)
            .hazeEffect(hazeState) { blurEffect { style = HazeMaterials.thin() } }
    )
}
```

> [!NOTE]
> By default, a `hazeEffect` draws all layers with `zIndex` less than its sibling `hazeSource`.

### 5. Filtering Source Areas

Exclude specific layers from an effect via `canDrawArea`:

```kotlin
CreditCard(
    modifier = Modifier
        .hazeSource(hazeState, zIndex = 2f, key = "self")
        .hazeEffect(hazeState) {
            canDrawArea = { area -> area.key != "self" }  // Exclude self
            blurEffect { /* ... */ }
        }
)
```

### 6. Deep Hierarchy — Composition Local

Avoid prop drilling through nested composables:

```kotlin
val LocalHazeState = compositionLocalOf { HazeState() }

@Composable
fun Screen() {
    val hazeState = rememberHazeState()
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box {
            ContentLayer()   // internally uses LocalHazeState.current for hazeSource
            OverlayLayer()   // internally uses LocalHazeState.current for hazeEffect
        }
    }
}

@Composable
fun OverlayLayer() {
    TopAppBar(
        modifier = Modifier.hazeEffect(state = LocalHazeState.current) {
            blurEffect { style = HazeMaterials.thin() }
        }
    )
}
```

### 7. Integrated Source and Effect on Same Node

A layout can use both `hazeSource` and `hazeEffect` on itself:

```kotlin
Card(
    modifier = Modifier
        .hazeSource(hazeState, zIndex = 1f)
        .hazeEffect(hazeState) {
            blurEffect { style = HazeMaterials.thin() }
        }
) {
    // Card content that blurs background AND serves as blur source for overlays above
}
```

### 8. Position Strategy for Split-Window

Fix blur misalignment in split-window modes (e.g., Huawei Parallel Space):

```kotlin
// Keep same-window coordinates (immune to split-window offset issues)
val hazeState = rememberHazeState(positionStrategy = HazePositionStrategy.Local)
```

### 9. Checklist

- [ ] Transparent overlay backgrounds to reveal the blur.
- [ ] Dialogs: translucent surface `color` instead of `colorEffects`.
- [ ] Sticky headers: `hazeSource` on items, not on the `LazyColumn`.
- [ ] `zIndex` ordering for overlapping effects (lower = further back).
- [ ] `CompositionLocal` for deep hierarchies.
