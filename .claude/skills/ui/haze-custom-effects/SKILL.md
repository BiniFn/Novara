---
name: haze-custom-effects
description: Expert guidance on building custom VisualEffect implementations for Haze. Use when creating custom shader effects, non-blur visual effects, or extending Haze with new effect types.
---

# Haze Custom Effects — Building VisualEffect Implementations

## Instructions

Haze is extensible via the `VisualEffect` interface. The blur effect itself is built on this same interface.

### 1. The VisualEffect Interface

```kotlin
interface VisualEffect {
    fun attach(context: VisualEffectContext)
    fun update(context: VisualEffectContext)
    fun detach(context: VisualEffectContext)
    fun DrawScope.draw(context: VisualEffectContext)
}
```

| Method | When Called | Purpose |
|---|---|---|
| `attach` | Effect first attached to composable | Allocate resources (shaders, caches) |
| `update` | State changes, composition locals change | Read snapshot state, call `invalidateDraw()` |
| `detach` | Effect removed from composable | Release resources from `attach()` |
| `draw` | Each render frame | Render the effect (keep allocation-light) |

### 2. VisualEffectContext

Available to all lifecycle methods:

```kotlin
interface VisualEffectContext {
    val position: Offset              // Effect node position
    val size: Size                    // Effect area size
    val layerSize: Size               // Graphics layer size (may differ from size)
    val layerOffset: Offset           // Layer offset relative to node
    val rootBounds: Rect              // Root layout bounds on screen
    val inputScale: HazeInputScale    // Input scale factor
    val windowId: Any?                // Containing window identifier
    val areas: List<HazeArea>         // Source areas to process
    val state: HazeState?             // HazeState (null = foreground mode)
    val coroutineScope: CoroutineScope // Node lifecycle scope

    fun requireDensity(): Density
    fun <T> currentValueOf(local: CompositionLocal<T>): T
    fun requireGraphicsContext(): GraphicsContext
    fun invalidateDraw()
}
```

Background mode: `state != null`. Foreground mode: `state == null`.

### 3. Example: Simple Custom Effect

```kotlin
@OptIn(ExperimentalHazeApi::class)
class SparkVisualEffect : VisualEffect {
    var color: Color = Color.Black
    var alpha: Float = 0.2f

    override fun attach(context: VisualEffectContext) {
        // Allocate resources (shaders, caches, delegates)
        // Geometry may not be resolved yet
    }

    override fun update(context: VisualEffectContext) {
        // Read composition locals or snapshot state
        val newColor = context.currentValueOf(LocalSparkColor)
        if (newColor != color) {
            color = newColor
            context.invalidateDraw()
        }
    }

    override fun detach(context: VisualEffectContext) {
        // Release resources
    }

    override fun DrawScope.draw(context: VisualEffectContext) {
        drawRect(
            color = color.copy(alpha = alpha),
            size = context.size,
        )
    }
}
```

### 4. Builder Extension Pattern

Expose your effect as a `HazeEffectScope` extension:

```kotlin
@OptIn(ExperimentalHazeApi::class)
fun HazeEffectScope.sparkEffect(
    block: SparkVisualEffect.() -> Unit,
) {
    val effect = visualEffect as? SparkVisualEffect ?: SparkVisualEffect()
    visualEffect = effect
    effect.block()
}
```

Usage:

```kotlin
Modifier.hazeEffect(state = hazeState) {
    sparkEffect {
        color = Color.Blue
        alpha = 0.3f
    }
}
```

### 5. Background Source Layers (Primary Pattern)

Sample transformed source layers that your effect processes:

```kotlin
val hazeState = rememberHazeState()

Box(Modifier.fillMaxSize()) {
    AsyncImage(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = 1.06f; translationX = 24f }
            .hazeSource(state = hazeState),
        model = "...", contentDescription = null
    )

    Box(
        modifier = Modifier
            .size(260.dp, 180.dp)
            .graphicsLayer { rotationZ = 10f }
            .hazeSource(state = hazeState, zIndex = 1f),
    )

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .hazeEffect(state = hazeState) {
                sparkEffect { }
            },
    )
}
```

Haze samples the transformed `hazeSource` layers behind the target node. Your `draw()` method controls how that sampled content is rendered.

### 6. Platform-Specific Implementations

Use `expect`/`actual` for platform-specific rendering:

```kotlin
// commonMain
expect fun createPlatformShader(size: Size): Shader

@OptIn(ExperimentalHazeApi::class)
class ShaderEffect : VisualEffect {
    private lateinit var shader: Shader

    override fun attach(context: VisualEffectContext) {
        shader = createPlatformShader(context.size)
    }
}
```

```kotlin
// androidMain
actual fun createPlatformShader(size: Size): Shader {
    // Android RuntimeShader implementation
}
```

```kotlin
// desktopMain / skikoMain
actual fun createPlatformShader(size: Size): Shader {
    // Desktop Skia shader implementation
}
```

### 7. Layer Bounds Override

If your effect needs extra sampling space outside the node bounds:

```kotlin
class MyEffect : VisualEffect {
    override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
        val extra = with(density) { 24.dp.toPx() }
        return rect.inflate(extra)
    }
}
```

Coordinate space: same as input `rect`. Background mode = root/screen-aligned. Foreground mode = local node rect.

### 8. Ownership Model

`VisualEffect` instances are **single-owner**:
- One effect instance → one active `hazeEffect` node
- Don't share the same instance across multiple active nodes
- Create/reuse per node via your builder pattern

### 9. Best Practices

1. **Allocate in `attach`**, release in `detach`
2. **Keep `draw` allocation-free** — hot path, called every frame
3. **Use `update`** for tracked state reads and controlled invalidation via `context.invalidateDraw()`
4. **Respect `inputScale`** and **bounds contracts** for performance and correctness
5. **Validate** with screenshot tests to catch visual regressions
6. **Geometry in `attach`**: position, size, layerSize, layerOffset may be zero/unresolved at attach time

### 10. Checklist

- [ ] Implement `VisualEffect` with all 4 lifecycle methods.
- [ ] Provide `HazeEffectScope` builder extension.
- [ ] Use `expect`/`actual` for platform-specific rendering.
- [ ] Allocate resources in `attach`, clean up in `detach`.
- [ ] Call `context.invalidateDraw()` in `update` when output changes.
- [ ] Keep `draw` hot path allocation-free.
- [ ] Override `calculateLayerBounds` if extra sampling space is needed.
- [ ] Don't share effect instances across multiple active nodes.
