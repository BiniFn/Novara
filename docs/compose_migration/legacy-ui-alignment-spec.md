# Legacy UI Alignment Specification

This document serves as the implementation guide for aligning the new Jetpack Compose Kototoro UI components back to their legacy XML structural and layout paradigms based on precise legacy reference screenshots. 

Use this documentation for the next phase of the UI refinement migration.

## 1. Home Screen (Grid & List Modes)

### Top Application Bar (Search Bar)
- **Structure**: Floating rounded-corner search bar (Shape: `RoundedCornerShape(percent = 50)` or `CircleShape` for full roundness).
- **Icons**: Include Document, Filter, and Overflow Menu icons aligned correctly dynamically interacting with nested scrolling behaviors.

### Content Section (Grid Mode: `主页-网格模式.jpg`)
- **Combined History/Update/Recommendation Card**:
  - Encapsulate the History, Updates, and Recommendation blocks collectively into a **single overarching `ElevatedCard` or `Surface`** with distinct corner radiuses.
  - Ensure each block title header (e.g., `历史 36`) has a `更多` ("More") clickable label aligned to the right.
  - Within each block, horizontal scroll rows for Manga cards.
- **Quick Links Card (快捷入口)**:
  - Add a dedicated `<Card>` container mirroring the above aesthetic structure.
  - Within it, use a `FlowRow` or `LazyVerticalGrid` to host outlined pill buttons containing an icon and standard label (e.g., Home, Local, Download, Random, Translate).

### Content Section (List Mode: `主页-列表模式.jpg`)
- **Layout Switch**: As already implemented in the Compose foundation, the combined 3-in-1 stack must restructure horizontally (using a `Row` displaying all three lists side-by-side if screen capacity holds).

---

## 2. Source Content Page (`源-网格模式.jpg`)

### Filtering & Categories
- **Top Bar**: Search input with a trailing Filter and Update action element.
- **Category Chips**: 
  - Immediately beneath the app bar, establish a fast-scrolling horizontal `LazyRow` featuring `FilterChip` (or filled rounded buttons) for categories: *Romance*, *Comedy*, *Adventure*, *School*, *Fantasy*.
  - Color palettes should reflect a mild tinted background (secondary container colored) rather than pure outlined.

### Media Listing Grid
- Retain minimum width constraints on items.
- Ensure the overlay badge located on the bottom-left of the cover correctly represents Language (Flag) / Source icon, tightly clipped into a rounded corner rectangle overlay.

---

## 3. Details Page (`详情-全景-卡片数据-书签-底栏.jpg`)

### Hero Panorama & Top App Bar
- **Panorama Blur**: Ensure the panoramic background image heavily bleeds into the status bar area (Edge-To-Edge enabled).
- **Navigation Tools**: The back, share, download, and options floating actions must overlay cleanly on the panorama layer employing translucent button backgrounds or heavy drop shadows for contrast.

### Information Layout Structure
- **Cover & Title Core**: Use a prominent `AsyncImage` for the primary cover aligned over the blur backdrop. Title is mounted immediately to the right, scaling logically based on string length.
- **Favorite Button**: The Favorite action (`收藏`) rests solidly anchored beneath the Title. It uses an outlined style with rounded drop corners mirroring its old position, rather than floating independently.
- **Statistics Metadata Formatted Card**: 
  - The attributes: **Source, Author, Translation/Language, Chapters/Duration**.
  - Must be consolidated into a **prominent uniform Grey `Card`** extending the width of the display. Avoid fragmented floating text, matching the legacy robust block feel.
- **Synopsis and Tags**:
  - `Text` description followed dynamically by an outlined `FlowRow` for tags (`Action`, `Fantasy`, `Ongoing`).

### Fixed Action Bottom Navigation Area
- **Structure**: Maintain a pinned `BottomAppBar` or constrained `Surface` at alignment bottom.
- **Left Actions**: Icon actions layout side-by-side (`List`, `Grid`, `Bookmark`).
- **Primary Action (Read Call-To-Action)**: 
  - On the right alignment, an elongated Pill Button carrying a dynamic "Read" (`阅读`) state, integrated seamlessly tightly accompanied by a segmented Chapter selection dropdown block.


### Follow-Through Action Items
1. Adapt `HomeScreen.kt` injecting the new Quick Access links matrix.
2. Adapt `FilterStrip` into `SourceScreen` resolving the rounded filter capsules.
3. Migrate `DetailsHeader.kt` verifying the Metadata `Card` background hues identically match the provided material mockups.
4. Integrate the Read Button/Navigation dock onto `DetailsScreen.kt` using constrained `.align(Alignment.BottomCenter)`.
