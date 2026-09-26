---
paths:
  - "src/main/java/datingapp/ui/**"
  - "src/test/java/datingapp/ui/**"
---

## JavaFX, ViewModels and threading

```text
ui/
  async/            ViewModelAsyncScope
  screen/           controllers + LocationSelectionDialog + CreateAccountDialogFactory
  viewmodel/        BaseViewModel, loaders, coordinators, adapters, ViewModelFactory
  {DatingApp, ImageCache, LocalPhotoStore, NavigationService, OnboardingContext,
   UiAnimations, UiComponents, UiConstants, UiDialogs, UiFeedbackService,
   UiPreferencesStore, UiStyles, UiThemeService, UiUtils}
```

### Startup wiring (`DatingApp.java`)

```java
ViewModelFactory vmFactory = new ViewModelFactory(services);
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
nav.setPreferencesStore(vmFactory.getPreferencesStore());
nav.initialize(primaryStage);
```

`ViewModelFactory` is the JavaFX composition root. Controllers take their
ViewModel from it — never build a service graph inside a controller.

### Never block the FX thread

**`BaseViewModel` + `ViewModelAsyncScope` are the standard async UI seam.** No
ad-hoc `Thread.ofVirtual()` or bare `Platform.runLater()` in normal flows — the
scope owns task policy and keyed delivery, which is what makes cancellation and
stale-result suppression work at all. Matching swipe/undo, safety
verification/delete and chat reset are the paths this exists for.

### Images

Visible controller paths use **`ImageCache.getImageAsync(...)`**, never the
synchronous `getImage(...)`, and guard the callback with a request id — without
one, a slow load for a swiped-past profile paints over the current card.

### Location UI

The profile location flow is owned by `LocationSelectionDialog` (search and
select) and `ProfileViewModel` (resolved label + coordinates). Do not geocode in
a controller and do not give the ViewModel direct lat/lon form ownership; see
`.claude/rules/architecture.md` for the full five-piece chain.

**Only `IL` is currently selectable/fully supported** — do not present the rest
of the country list as usable.

### Test helpers

`src/test/java/datingapp/ui/JavaFxTestSupport.java` and
`ui/async/UiAsyncTestSupport.java` — use these rather than standing up your own
FX toolkit or async harness.

### Do not reintroduce

`AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`, `ui/controller`.
