# MainActivity Compose Migration Tasks

- [x] Phase 1: Create Compose Root Shell (`KototoroApp.kt` & `AppNavGraph.kt`)
  - `KototoroApp` Scaffold structure
  - Interop placeholder for Fragment host
- [x] Phase 2: Refactor `MainActivity.kt` and remove Legacy XML
  - Delete `activity_main.xml`
  - Refactor `MainActivity` layout inflation to use `setContent { KototoroApp() }`
  - Remove/Deprecate `MainNavigationDelegate` and XML binding logic
- [/] Phase 3: Pure Compose TopAppBar & Native Search
  - [ ] Overhaul `activity_main.xml` to pure `FrameLayout` overlay
  - [ ] Implement `KototoroTopBar.kt` (M3 TopAppBar & SearchBar)
  - [ ] Integrate explicit NestedScrolling dispatch from `MainActivity` FrameLayout to Compose
- [x] Phase 4: Refactor Fragment Spacing (`AppBarOwner` Removal)
  - [x] Remove `AppBarOwner` interface
  - [x] Remove `AppBarOwner` dependencies from fragment layout logic
  - [ ] Inject uniform Top/Bottom padding via `MainActivity` into `FragmentContainerView`
- [ ] Final Verification & Walkthrough
