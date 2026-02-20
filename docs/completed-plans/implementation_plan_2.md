# Implementation Plan: UI Feature Parity and Refactoring

## Goal Description
The objective is to achieve feature parity between the CLI/Core and the JavaFX UI for Standouts, Social features (Friend Requests, Notifications, Profile Notes), and Trust & Safety actions (Block, Report). Additionally, this plan addresses the [ProfileController](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/ProfileController.java#47-978) bloat ("God Controller") by extracting common JavaFX styling and ComboBox converters into a reusable `UiUtils` component. We will also add missing Dashboard polish (notification counts) and multi-photo support in the candidate view.

## Proposed Changes

### UI Code Refactoring (FI-CONS-002)
- **[NEW] `src/main/java/datingapp/ui/UiUtils.java`**
  - Create a utility class with static methods to handle repetitive JavaFX logic.
  - Extract the following from [ProfileController](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/ProfileController.java#47-978): [createEnumStringConverter()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/ProfileController.java#431-447), [createDisplayCell()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/ProfileController.java#448-461), [setupResponsiveListener()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/DashboardController.java#123-140), UI animation helpers (if standalone), and logic to style specific buttons (like interest chips).
- **[MODIFY] [src/main/java/datingapp/ui/screen/ProfileController.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/ProfileController.java)**
  - Remove redundant methods and import `UiUtils` instead to handle ComboBox formatting.

### Trust & Safety UI (FI-AUD-001 related & parity)
- **[MODIFY] `src/main/resources/datingapp/ui/fxml/match_popup.fxml`**
  - Add "Block" and "Report" buttons to the candidate profile view.
- **[MODIFY] [src/main/java/datingapp/ui/screen/MatchingController.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/MatchingController.java)**
  - Add event handlers for the Block and Report buttons that delegate to `MatchingViewModel`.
- **[MODIFY] [src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java)**
  - Add `blockCandidate()` and `reportCandidate()` methods that delegate upstream (or via `MatchingHandler`-equivalent logic to `TrustSafetyService`).
- **[MODIFY] `src/main/resources/datingapp/ui/fxml/chat.fxml` & `ChatController.java`**
  - Add Block and Report options to the chat view. Connect to `ChatViewModel`.

### Dashboard Polish & Multi-Photo Gallery
- **[MODIFY] [src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java) & [DashboardController.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/DashboardController.java)**
  - Fix the `unreadBadgeLabel` wiring if needed and introduce general notification counts if applicable (using `CommunicationStorage.countUnreadNotifications`).
- **[MODIFY] `src/main/resources/datingapp/ui/fxml/matching.fxml` & `MatchingController.java`**
  - Add `prevPhotoButton` and `nextPhotoButton` controls overlaid on the candidate's photo. Wire these to `MatchingViewModel` to iterate through the current candidate's photo URLs.

### Standouts UI (FI-AUD-004)
- **[NEW] `src/main/resources/datingapp/ui/fxml/standouts.fxml`**
  - Create a grid/list UI showing top Standout candidates.
- **[NEW] `src/main/java/datingapp/ui/screen/StandoutsController.java`**
  - Controller to handle user interactions on the Standouts page.
- **[NEW] `src/main/java/datingapp/ui/viewmodel/StandoutsViewModel.java`**
  - ViewModel to load standouts via `RecommendationService` / `StandoutsService`.
- **[MODIFY] [src/main/java/datingapp/ui/NavigationService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/NavigationService.java)**
  - Add `STANDOUTS` view type and load the corresponding FXML.
- **[MODIFY] [src/main/java/datingapp/ui/screen/DashboardController.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/DashboardController.java)**
  - Add a "Standouts" button sending users to the Standouts view.

### Social UI (Friend Requests, Notifications, Notes)
- **[NEW] `src/main/resources/datingapp/ui/fxml/social.fxml`**
  - Create a tabbed view for "Notifications", "Friend Requests", and "Notes".
- **[NEW] `src/main/java/datingapp/ui/screen/SocialController.java` & `SocialViewModel.java`**
  - Fetch and display the social connections from `CommunicationStorage` and `User` (Profile Notes).
- **[MODIFY] [src/main/java/datingapp/ui/NavigationService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/NavigationService.java) & [DashboardController.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/DashboardController.java)**
  - Add `SOCIAL` view type.

## Verification Plan

### Automated Tests
1. **Compilation & Formatting:**
   - Run `mvn clean compile spotless:apply verify` to ensure the project continues to build without checkstyle or PMD violations.
2. **ViewModel Tests:**
   - Create `StandoutsViewModelTest.java` and `SocialViewModelTest.java` alongside the new ViewModels.
   - Use `TestStorages` in these tests to mock repository behavior and explicitly verify that async tasks complete successfully using the project's latch patterns (similar to `DashboardViewModelTest`).

### Manual Verification
1. Run the Application via:
   ```shell
   mvn clean javafx:run
   ```
2. Navigate to Profile: Verify that the refactored [ProfileController](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/ProfileController.java#47-978) still correctly initializes ComboBoxes (e.g. Setting Smoking, Drinking).
3. Navigate to Dashboard: Verify the Presence of "Standouts" and "Social" navigation buttons.
4. Social View: Open social screen and test toggling between Notifications and Friend Requests tabs.
5. Trust & Safety: Go to Matching (Browse). Find a candidate. Press the "Report" button and confirm it shows a success toast.
6. Gallery: Go to Matching. Check if the candidate has multiple photos, and click the Next/Prev buttons to cycle through them.
