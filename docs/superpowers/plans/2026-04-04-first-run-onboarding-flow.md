# First-Run Onboarding Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing create-account → incomplete-profile bounce into a real first-run onboarding flow using the current JavaFX profile screen and activation rules.

**Architecture:** Reuse the existing `PROFILE` screen as the onboarding host instead of introducing a brand-new wizard screen. New and incomplete users should arrive at `PROFILE` with explicit onboarding context, and `ProfileViewModel`/`ProfileController` should expose focused first-run guidance derived from the already-authoritative `ProfileActivationPolicy` and `ProfileService` completion output. Once the profile becomes activatable and saves successfully, the flow should transition to `DASHBOARD`.

**Tech Stack:** Java 25, JavaFX 25/FXML, Maven, JUnit 5, existing `AppSession`, `NavigationService`, `ProfileMutationUseCases`, `ProfileActivationPolicy`, `ProfileService`, `JavaFxTestSupport`.

---

## File map

### Create
- `src/main/java/datingapp/ui/OnboardingContext.java` — typed navigation payload describing why the user arrived on `PROFILE` and whether the session is in first-run onboarding mode.
- `src/main/java/datingapp/ui/viewmodel/ProfileOnboardingState.java` — immutable UI-facing onboarding snapshot derived from the current user, completion breakdown, and activation decision.
- `src/test/java/datingapp/ui/screen/OnboardingFlowTest.java` — dedicated source-backed regression test for the first-run journey that is currently missing.

### Modify
- `src/main/java/datingapp/ui/screen/LoginController.java` — continue directly into onboarding after account creation and carry onboarding context for incomplete logins.
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java` — expose a typed post-login decision instead of leaving onboarding intent implicit.
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java` — surface onboarding properties/checklist text, refresh them after load/save, and distinguish draft-save vs activation-ready states.
- `src/main/java/datingapp/ui/screen/ProfileController.java` — render onboarding banner/checklist, update CTA text, and route cancel/back safely for incomplete first-run sessions.
- `src/main/resources/fxml/profile.fxml` — add the onboarding banner/checklist container without replacing the existing editor.

### Update tests
- `src/test/java/datingapp/ui/viewmodel/LoginViewModelTest.java`
- `src/test/java/datingapp/ui/screen/LoginControllerTest.java`
- `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`
- `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`

---

### Task 1: Carry explicit onboarding context out of login/create-account

**Files:**
- Create: `src/main/java/datingapp/ui/OnboardingContext.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/LoginController.java`
- Test: `src/test/java/datingapp/ui/viewmodel/LoginViewModelTest.java`
- Test: `src/test/java/datingapp/ui/screen/LoginControllerTest.java`

- [ ] **Step 1: Write the failing login/create-account tests**

```java
@Test
@DisplayName("create account continues directly into onboarding on the profile screen")
void createAccountContinuesDirectlyIntoOnboardingOnTheProfileScreen() throws Exception {
    TestStorages.Users users = new TestStorages.Users();
    AppConfig config = AppConfig.defaults();
    ProfileService profileService = new ProfileService(users);

    LoginViewModel viewModel = new LoginViewModel(
            new datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore(users),
            config,
            AppSession.getInstance(),
            JavaFxTestSupport.blockingUiDispatcher());
    TrackingLoginController controller = new TrackingLoginController(
            viewModel, profileService, config.safety().userTimeZone());

    User created = viewModel.createUser("Taylor", 29, Gender.OTHER, Gender.FEMALE);

    JavaFxTestSupport.runOnFxAndWait(() -> controller.continueIntoOnboarding(created));

    assertEquals(created, AppSession.getInstance().getCurrentUser());
    assertEquals(NavigationService.ViewType.PROFILE, controller.lastNavigationTarget());
    OnboardingContext context = NavigationService.getInstance()
            .consumeNavigationContext(NavigationService.ViewType.PROFILE, OnboardingContext.class)
            .orElseThrow();
    assertEquals(OnboardingContext.EntryReason.NEW_ACCOUNT, context.entryReason());
    assertTrue(context.firstRun());
}

@Test
@DisplayName("incomplete login resolves to profile onboarding instead of a plain profile hop")
void incompleteLoginResolvesToProfileOnboardingInsteadOfPlainProfileHop() {
    TestStorages.Users users = new TestStorages.Users();
    LoginViewModel viewModel = new LoginViewModel(
            new UiDataAdapters.StorageUiUserStore(users),
            AppConfig.defaults(),
            AppSession.getInstance(),
            new UiAsyncTestSupport.TestUiThreadDispatcher());

    User incomplete = new User(UUID.randomUUID(), "Jamie");
    incomplete.setBirthDate(datingapp.core.AppClock.today().minusYears(28));
    incomplete.setGender(Gender.OTHER);
    incomplete.setInterestedIn(java.util.EnumSet.of(Gender.FEMALE));
    users.save(incomplete);

    viewModel.setSelectedUser(incomplete);

    assertEquals(LoginViewModel.PostLoginDecision.START_ONBOARDING, viewModel.resolvePostLoginDecision());
}
```

- [ ] **Step 2: Run the focused login tests to confirm the red state**

Run: `mvn --% -Dcheckstyle.skip=true -Dtest=LoginViewModelTest,LoginControllerTest test`
Expected: FAIL because `PostLoginDecision`, `OnboardingContext`, and `continueIntoOnboarding(...)` do not exist yet.

- [ ] **Step 3: Implement the typed onboarding contract and controller handoff**

```java
package datingapp.ui;

import java.util.UUID;

public record OnboardingContext(UUID userId, EntryReason entryReason, boolean firstRun) {
    public enum EntryReason {
        NEW_ACCOUNT,
        INCOMPLETE_LOGIN
    }

    public static OnboardingContext newAccount(UUID userId) {
        return new OnboardingContext(userId, EntryReason.NEW_ACCOUNT, true);
    }

    public static OnboardingContext incompleteLogin(UUID userId) {
        return new OnboardingContext(userId, EntryReason.INCOMPLETE_LOGIN, false);
    }
}
```

```java
public enum PostLoginDecision {
    GO_TO_DASHBOARD,
    START_ONBOARDING
}

public PostLoginDecision resolvePostLoginDecision() {
    if (selectedUser == null) {
        return PostLoginDecision.START_ONBOARDING;
    }
    var activationDecision = activationPolicy.canActivate(selectedUser);
    if (selectedUser.getState() == UserState.ACTIVE
            || (activationDecision instanceof datingapp.core.workflow.WorkflowDecision.Denied denied
                    && "ALREADY_ACTIVE".equals(denied.reasonCode()))) {
        return PostLoginDecision.GO_TO_DASHBOARD;
    }
    return PostLoginDecision.START_ONBOARDING;
}
```

```java
void continueIntoOnboarding(User user) {
    if (user == null) {
        return;
    }
    viewModel.setSelectedUser(user);
    if (!viewModel.login()) {
        return;
    }
    NavigationService.getInstance().setNavigationContext(
            NavigationService.ViewType.PROFILE,
            OnboardingContext.newAccount(user.getId()));
    NavigationService.getInstance().navigateTo(NavigationService.ViewType.PROFILE);
}

private void handleLoginSuccess() {
    switch (viewModel.resolvePostLoginDecision()) {
        case GO_TO_DASHBOARD -> NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
        case START_ONBOARDING -> {
            User selected = viewModel.getSelectedUser();
            if (selected != null) {
                NavigationService.getInstance().setNavigationContext(
                        NavigationService.ViewType.PROFILE,
                        OnboardingContext.incompleteLogin(selected.getId()));
            }
            NavigationService.getInstance().navigateTo(NavigationService.ViewType.PROFILE);
        }
    }
}
```

- [ ] **Step 4: Re-run the focused login tests until they pass**

Run: `mvn --% -Dcheckstyle.skip=true -Dtest=LoginViewModelTest,LoginControllerTest test`
Expected: PASS with the new typed onboarding decision and navigation context path.

- [ ] **Step 5: Commit the login/onboarding handoff slice**

```bash
git add src/main/java/datingapp/ui/OnboardingContext.java src/main/java/datingapp/ui/viewmodel/LoginViewModel.java src/main/java/datingapp/ui/screen/LoginController.java src/test/java/datingapp/ui/viewmodel/LoginViewModelTest.java src/test/java/datingapp/ui/screen/LoginControllerTest.java
git commit -m "feat: start onboarding directly after account creation"
```

### Task 2: Derive onboarding state from the existing profile rules

**Files:**
- Create: `src/main/java/datingapp/ui/viewmodel/ProfileOnboardingState.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`
- Test: `src/test/java/datingapp/core/workflow/ProfileActivationPolicyTest.java`

- [ ] **Step 1: Write the failing ViewModel tests for onboarding state**

```java
@Test
@DisplayName("loadCurrentUser exposes onboarding guidance for incomplete first-run users")
void loadCurrentUserExposesOnboardingGuidanceForIncompleteFirstRunUsers() {
    TestStorages.Users users = new TestStorages.Users();
    AppConfig config = AppConfig.defaults();
    ProfileService profileService = new ProfileService(users);

    User currentUser = createActivatableIncompleteUser("GuideUser");
    currentUser.setPhotoUrls(List.of());
    users.save(currentUser);
    session.setCurrentUser(currentUser);

    ProfileViewModel viewModel = newMutationBackedProfileViewModel(users, config, profileService);
    viewModel.setOnboardingContext(datingapp.ui.OnboardingContext.newAccount(currentUser.getId()));

    viewModel.loadCurrentUser();

    assertTrue(viewModel.onboardingActiveProperty().get());
    assertTrue(viewModel.onboardingHeadlineProperty().get().contains("Finish your profile"));
    assertFalse(viewModel.onboardingChecklistProperty().isEmpty());
    assertEquals("Finish onboarding", viewModel.primaryActionLabelProperty().get());
}

@Test
@DisplayName("active users do not stay in onboarding mode")
void activeUsersDoNotStayInOnboardingMode() {
    TestStorages.Users users = new TestStorages.Users();
    AppConfig config = AppConfig.defaults();
    ProfileService profileService = new ProfileService(users);

    User currentUser = createActiveUser("DoneUser");
    users.save(currentUser);
    session.setCurrentUser(currentUser);

    ProfileViewModel viewModel = newMutationBackedProfileViewModel(users, config, profileService);
    viewModel.loadCurrentUser();

    assertFalse(viewModel.onboardingActiveProperty().get());
    assertEquals("Save changes", viewModel.primaryActionLabelProperty().get());
}
```

- [ ] **Step 2: Run the focused profile ViewModel tests to verify they fail first**

Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ProfileViewModelTest,ProfileActivationPolicyTest test`
Expected: FAIL because the onboarding properties and setter do not exist yet.

- [ ] **Step 3: Add a dedicated onboarding snapshot and bind it in the ViewModel**

```java
package datingapp.ui.viewmodel;

import datingapp.core.model.User;
import datingapp.core.workflow.WorkflowDecision;
import java.util.List;

public record ProfileOnboardingState(
        boolean active,
        boolean activationReady,
        String headline,
        String summary,
        String primaryActionLabel,
        List<String> checklist) {

    public static ProfileOnboardingState from(User user, WorkflowDecision activationDecision, List<String> nextSteps) {
        boolean onboardingActive = user != null && user.getState() != User.UserState.ACTIVE;
        boolean activationReady = activationDecision != null && activationDecision.isAllowed();
        String headline = activationReady ? "Your profile is ready" : "Finish your profile to start matching";
        String summary = activationReady
                ? "Save once more to activate this profile and continue to the dashboard."
                : "Complete the remaining sections below. The profile screen already contains the required fields.";
        String primaryActionLabel = onboardingActive ? "Finish onboarding" : "Save changes";
        return new ProfileOnboardingState(onboardingActive, activationReady, headline, summary, primaryActionLabel, nextSteps);
    }
}
```

```java
private final javafx.beans.property.BooleanProperty onboardingActive = new SimpleBooleanProperty(false);
private final StringProperty onboardingHeadline = new SimpleStringProperty("");
private final StringProperty onboardingSummary = new SimpleStringProperty("");
private final StringProperty primaryActionLabel = new SimpleStringProperty("Save changes");
private final javafx.collections.ObservableList<String> onboardingChecklist = FXCollections.observableArrayList();
private datingapp.ui.OnboardingContext onboardingContext;

public void setOnboardingContext(datingapp.ui.OnboardingContext onboardingContext) {
    this.onboardingContext = onboardingContext;
}

private void updateOnboardingState(User user, CompletionResult completion) {
    var activationDecision = new ProfileActivationPolicy().canActivate(user);
    ProfileOnboardingState state = ProfileOnboardingState.from(user, activationDecision, completion.nextSteps());
    onboardingActive.set(state.active() && onboardingContext != null);
    onboardingHeadline.set(state.headline());
    onboardingSummary.set(state.summary());
    primaryActionLabel.set(state.primaryActionLabel());
    onboardingChecklist.setAll(state.checklist());
}
```

```java
public javafx.beans.property.BooleanProperty onboardingActiveProperty() { return onboardingActive; }
public StringProperty onboardingHeadlineProperty() { return onboardingHeadline; }
public StringProperty onboardingSummaryProperty() { return onboardingSummary; }
public StringProperty primaryActionLabelProperty() { return primaryActionLabel; }
public javafx.collections.ObservableList<String> onboardingChecklistProperty() {
    return FXCollections.unmodifiableObservableList(onboardingChecklist);
}
```

Call `updateOnboardingState(user, result)` from both `loadCurrentUser()` and `updateSessionAndCompletion(User user)` so the checklist refreshes after every save.

- [ ] **Step 4: Re-run the focused ViewModel tests until they pass**

Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ProfileViewModelTest,ProfileActivationPolicyTest test`
Expected: PASS with incomplete users showing onboarding guidance and active users dropping back to normal save mode.

- [ ] **Step 5: Commit the ViewModel onboarding state slice**

```bash
git add src/main/java/datingapp/ui/viewmodel/ProfileOnboardingState.java src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java src/test/java/datingapp/core/workflow/ProfileActivationPolicyTest.java
git commit -m "feat: expose onboarding state in profile view model"
```

### Task 3: Render first-run onboarding inside the existing profile screen

**Files:**
- Modify: `src/main/resources/fxml/profile.fxml`
- Modify: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Test: `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`
- Test: `src/test/java/datingapp/ui/screen/ProfileFormValidatorTest.java`

- [ ] **Step 1: Write the failing controller tests for the onboarding banner and cancel/back routing**

```java
@Test
@DisplayName("onboarding users see the checklist banner and a finish-onboarding CTA")
void onboardingUsersSeeChecklistBannerAndFinishOnboardingCta() throws Exception {
    TestStorages.Users users = new TestStorages.Users();
    AppConfig config = AppConfig.defaults();
    ProfileService profileService = new ProfileService(users);

    User currentUser = createActivatableIncompleteUser("Banner User");
    users.save(currentUser);
    AppSession.getInstance().setCurrentUser(currentUser);

    ProfileViewModel viewModel = createViewModel(users, config, profileService);
    viewModel.setOnboardingContext(datingapp.ui.OnboardingContext.newAccount(currentUser.getId()));
    TrackingProfileController controller = new TrackingProfileController(viewModel);

    JavaFxTestSupport.LoadedFxml loaded = JavaFxTestSupport.loadFxml("/fxml/profile.fxml", () -> controller);
    Parent root = loaded.root();

    Label onboardingHeadline = JavaFxTestSupport.lookup(root, "#onboardingHeadlineLabel", Label.class);
    Button saveButton = JavaFxTestSupport.lookup(root, "#saveButton", Button.class);

    assertTrue(JavaFxTestSupport.callOnFxAndWait(onboardingHeadline::isVisible));
    assertEquals("Finish onboarding", JavaFxTestSupport.callOnFxAndWait(saveButton::getText));
}

@Test
@DisplayName("cancel during first-run onboarding returns to login instead of dashboard")
void cancelDuringFirstRunOnboardingReturnsToLoginInsteadOfDashboard() throws Exception {
    TestStorages.Users users = new TestStorages.Users();
    AppConfig config = AppConfig.defaults();
    ProfileService profileService = new ProfileService(users);

    User currentUser = createActivatableIncompleteUser("Cancel User");
    users.save(currentUser);
    AppSession.getInstance().setCurrentUser(currentUser);

    ProfileViewModel viewModel = createViewModel(users, config, profileService);
    viewModel.setOnboardingContext(datingapp.ui.OnboardingContext.newAccount(currentUser.getId()));
    TrackingProfileController controller = new TrackingProfileController(viewModel);

    JavaFxTestSupport.LoadedFxml loaded = JavaFxTestSupport.loadFxml("/fxml/profile.fxml", () -> controller);
    Parent root = loaded.root();
    Button cancelButton = JavaFxTestSupport.lookup(root, "#cancelButton", Button.class);

    JavaFxTestSupport.runOnFxAndWait(cancelButton::fire);

    assertEquals(NavigationService.ViewType.LOGIN, controller.lastNavigationTarget());
}
```

- [ ] **Step 2: Run the focused profile controller tests and confirm the missing UI red state**

Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ProfileControllerTest,ProfileFormValidatorTest test`
Expected: FAIL because the onboarding banner nodes and onboarding-specific cancel/save behavior do not exist.

- [ ] **Step 3: Add onboarding UI nodes and bind them in the controller**

```xml
<VBox fx:id="onboardingBanner" spacing="8.0" visible="false" managed="false" styleClass="card-panel">
    <Label fx:id="onboardingHeadlineLabel" styleClass="subheading" text="Finish your profile to start matching" />
    <Label fx:id="onboardingSummaryLabel" wrapText="true" styleClass="text-secondary" />
    <VBox fx:id="onboardingChecklistBox" spacing="6.0" />
</VBox>
```

```java
@FXML
private VBox onboardingBanner;
@FXML
private Label onboardingHeadlineLabel;
@FXML
private Label onboardingSummaryLabel;
@FXML
private VBox onboardingChecklistBox;

private void bindOnboardingBanner() {
    if (onboardingBanner == null) {
        return;
    }
    onboardingBanner.visibleProperty().bind(viewModel.onboardingActiveProperty());
    onboardingBanner.managedProperty().bind(viewModel.onboardingActiveProperty());
    onboardingHeadlineLabel.textProperty().bind(viewModel.onboardingHeadlineProperty());
    onboardingSummaryLabel.textProperty().bind(viewModel.onboardingSummaryProperty());
    saveButton.textProperty().bind(viewModel.primaryActionLabelProperty());
    rebuildOnboardingChecklist();
    viewModel.onboardingChecklistProperty().addListener(
            (javafx.collections.ListChangeListener<String>) change -> rebuildOnboardingChecklist());
}

private void rebuildOnboardingChecklist() {
    onboardingChecklistBox.getChildren().clear();
    for (String step : viewModel.onboardingChecklistProperty()) {
        Label item = new Label("• " + step);
        item.getStyleClass().add("text-secondary");
        onboardingChecklistBox.getChildren().add(item);
    }
}
```

```java
private void navigateAfterCancel() {
    if (viewModel.onboardingActiveProperty().get()) {
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.LOGIN);
        return;
    }
    NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
}
```

Call `bindOnboardingBanner()` from `initialize(...)`, and replace the current hard-coded cancel navigation with `navigateAfterCancel()`.

- [ ] **Step 4: Re-run the focused controller tests until they pass**

Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ProfileControllerTest,ProfileFormValidatorTest test`
Expected: PASS with the onboarding banner rendered, save button text updated, and cancel/back respecting first-run onboarding.

- [ ] **Step 5: Commit the profile-screen onboarding slice**

```bash
git add src/main/resources/fxml/profile.fxml src/main/java/datingapp/ui/screen/ProfileController.java src/test/java/datingapp/ui/screen/ProfileControllerTest.java src/test/java/datingapp/ui/screen/ProfileFormValidatorTest.java
git commit -m "feat: guide first-run onboarding inside profile screen"
```

### Task 4: Add the missing dedicated onboarding regression test

**Files:**
- Create: `src/test/java/datingapp/ui/screen/OnboardingFlowTest.java`
- Modify: `src/test/java/datingapp/ui/screen/LoginControllerTest.java`
- Modify: `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`

- [ ] **Step 1: Write a dedicated journey test that reproduces the current product seam**

```java
@Test
@DisplayName("new account enters onboarding, stays on profile while incomplete, then reaches dashboard once activated")
void newAccountEntersOnboardingAndReachesDashboardOnceActivated() throws Exception {
    TestStorages.Users users = new TestStorages.Users();
    AppConfig config = AppConfig.defaults();
    ProfileService profileService = new ProfileService(users);

    LoginViewModel loginViewModel = new LoginViewModel(
            new UiDataAdapters.StorageUiUserStore(users),
            config,
            AppSession.getInstance(),
            JavaFxTestSupport.blockingUiDispatcher());
    TrackingLoginController loginController = new TrackingLoginController(
            loginViewModel, profileService, config.safety().userTimeZone());

    User created = loginViewModel.createUser("Taylor", 29, Gender.OTHER, Gender.FEMALE);
    created.addPhotoUrl("https://example.com/taylor.jpg");
    created.setPacePreferences(new datingapp.core.profile.MatchPreferences.PacePreferences(
        datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency.OFTEN,
        datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate.FEW_DAYS,
        datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
        datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference.DEEP_CHAT));
    users.save(created);
    JavaFxTestSupport.runOnFxAndWait(() -> loginController.continueIntoOnboarding(created));

    ProfileViewModel profileViewModel = createProfileViewModel(users, config, profileService);
    profileViewModel.setOnboardingContext(
            NavigationService.getInstance()
                    .consumeNavigationContext(NavigationService.ViewType.PROFILE, OnboardingContext.class)
                    .orElseThrow());
    TrackingProfileController profileController = new TrackingProfileController(profileViewModel);

    JavaFxTestSupport.loadFxml("/fxml/profile.fxml", () -> profileController);

    profileViewModel.bioProperty().set("Onboarding bio");
    profileViewModel.setLocationCoordinates(32.0853, 34.7818);
    profileViewModel.getInterestedInGenders().clear();
    profileViewModel.getInterestedInGenders().addAll(java.util.EnumSet.of(Gender.FEMALE));
    profileViewModel.saveAsync(outcome -> { });

    assertTrue(JavaFxTestSupport.waitUntil(profileController::navigatedToDashboard, 5000));
    assertEquals(User.UserState.ACTIVE, AppSession.getInstance().getCurrentUser().getState());
}
```

- [ ] **Step 2: Run the onboarding-only regression test in isolation**

Run: `mvn --% -Dcheckstyle.skip=true -Dtest=OnboardingFlowTest test`
Expected: FAIL until the login handoff, onboarding banner state, and activation path all work together.

- [ ] **Step 3: Implement the minimum glue needed for the flow test to pass**

```java
// LoginController.java
if (result.isPresent()) {
    continueIntoOnboarding(result.get());
}
```

```java
// ProfileController.java inside save callback
case SAVED_DRAFT -> showSaveSuccessStatus(
        "Profile saved. Complete the remaining onboarding checklist to activate your profile.");
case ACTIVATED -> {
    showSaveSuccessStatus("Profile saved and activated!");
    cleanup();
    navigateToDashboard();
}
```

```java
// ProfileViewModel.java after updateSessionAndCompletion(savedUser)
if (savedUser != null && savedUser.getState() == User.UserState.ACTIVE) {
    onboardingChecklist.clear();
    onboardingActive.set(false);
    primaryActionLabel.set("Save changes");
}
```

- [ ] **Step 4: Re-run the onboarding-only regression, then the whole onboarding-focused test pack**

Run: `mvn --% -Dcheckstyle.skip=true -Dtest=OnboardingFlowTest,LoginControllerTest,LoginViewModelTest,ProfileControllerTest,ProfileViewModelTest test`
Expected: PASS with a dedicated flow regression covering the missing first-run journey.

- [ ] **Step 5: Commit the onboarding regression slice**

```bash
git add src/test/java/datingapp/ui/screen/OnboardingFlowTest.java src/test/java/datingapp/ui/screen/LoginControllerTest.java src/test/java/datingapp/ui/screen/ProfileControllerTest.java src/test/java/datingapp/ui/viewmodel/LoginViewModelTest.java src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java
git commit -m "test: cover the first-run onboarding journey"
```

### Task 5: Run the repository verification steps for the onboarding change set

**Files:**
- Verify only: `src/main/java/datingapp/ui/OnboardingContext.java`
- Verify only: `src/main/java/datingapp/ui/viewmodel/ProfileOnboardingState.java`
- Verify only: `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- Verify only: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Verify only: `src/main/java/datingapp/ui/screen/LoginController.java`
- Verify only: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Verify only: `src/main/resources/fxml/profile.fxml`
- Verify only: `src/test/java/datingapp/ui/screen/OnboardingFlowTest.java`

- [ ] **Step 1: Check touched files for IDE/compiler errors**

Run: inspect Problems for the touched files or use the workspace error check on the onboarding-related Java files.
Expected: no new Java or FXML errors in the onboarding change set.

- [ ] **Step 2: Run the focused onboarding regression pack**

Run: `mvn --% -Dcheckstyle.skip=true -Dtest=OnboardingFlowTest,LoginControllerTest,LoginViewModelTest,ProfileControllerTest,ProfileViewModelTest test`
Expected: PASS.

- [ ] **Step 3: Run a broader profile/use-case smoke pack**

Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ProfileMutationUseCasesTest,ProfileUseCasesTest,ProfileActivationPolicyTest test`
Expected: PASS, proving the onboarding changes did not break the activation/completion seam.

- [ ] **Step 4: Run the repo quality gate before concluding**

Run: `mvn spotless:apply verify`
Expected: PASS.

- [ ] **Step 5: Commit the final verified change set**

```bash
git add src/main/java/datingapp/ui/OnboardingContext.java src/main/java/datingapp/ui/viewmodel/ProfileOnboardingState.java src/main/java/datingapp/ui/viewmodel/LoginViewModel.java src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java src/main/java/datingapp/ui/screen/LoginController.java src/main/java/datingapp/ui/screen/ProfileController.java src/main/resources/fxml/profile.fxml src/test/java/datingapp/ui/screen/OnboardingFlowTest.java src/test/java/datingapp/ui/screen/LoginControllerTest.java src/test/java/datingapp/ui/viewmodel/LoginViewModelTest.java src/test/java/datingapp/ui/screen/ProfileControllerTest.java src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java
git commit -m "feat: add guided first-run onboarding flow"
```

---

## Self-review

- Spec coverage: the plan covers the missing source seam end to end — create-account handoff, incomplete-login routing, profile-screen onboarding guidance, activation transition, and dedicated regression coverage.
- Placeholder scan: removed vague language like “improve onboarding UX” and replaced it with exact files, properties, tests, and commands.
- Consistency check: the plan keeps `PROFILE` as the onboarding host throughout; it does not introduce a second completion rules engine or a second onboarding screen.
