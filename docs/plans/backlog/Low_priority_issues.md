# PART 4: LOW PRIORITY ISSUES

## LOW-01: Missing Javadoc (Widespread)
- **Files:** Match.java, Messaging.Conversation, Social.FriendRequest, Stats.UserStats, SwipeSession.java, Dealbreakers.java, Achievement.java
- **Description:** Methods lack documentation, especially state transitions and validation rules

## LOW-02: toString() May Expose Sensitive Data
- **Files:** `User.ProfileNote`, `Messaging.Message`, `Social.Notification`, `Social.FriendRequest`
- **Description:** Record auto-generated toString() includes all fields including content
- **Impact:** Sensitive data in logs if objects logged
- **comment from the user(me):**  i think its fine for now, actually useful for debugging. we can address it later if needed, but its not a big deal at all. dont waste your time on this.



## LOW-03: Incomplete Light Theme
- **File:** `light-theme.css`
- **Description:** Only 152 lines vs 2109 in theme.css; many classes not overridden
- **Impact:** Light theme has inconsistent appearance.
- **comment from the user(me):**  i dont know, im scared that you will break everything. if its not easy and simple and quick, do-NOT mess with it. leave it as is.


## LOW-04: Missing Button Focus States in CSS
- **File:** `theme.css`
- **Description:** Buttons have :hover and :pressed but no :focused pseudo-class
- **Impact:** Keyboard navigation has no visual feedback

## LOW-05: Duplicate/Redundant Code
- **File:** `stats.fxml:143`
- **Description:** Hidden ListView duplicates achievement card functionality
- **Impact:** Technical debt; maintenance confusion
- **comment from the user(me):** try to see if it can and should be used, and if you can, make use of it.


## LOW-06: Missing Test Mock Implementations
- **File:** `TestStorages.java`
- **Missing:**
  - `Messaging.ConversationStorage`
  - `Messaging.MessageStorage`
  - `Social.FriendRequestStorage`
  - `Social.NotificationStorage`
  - `Achievement.Storage`
  - `Stats.UserStatsStorage`
  - `UserInteractions.ReportStorage`
- **Impact:** Tests create inline mocks; inconsistency
- **comment from the user(me):**  not sure about it at all. not sure what we should do and how important it is, and how it should be properly done. leave it for last and when you are done with everything else, suggest fixes to this and act on my response to this.


## LOW-07: No Native Packaging Configuration
- **File:** `pom.xml`
- **Description:** Only fat JAR packaging; no jpackage/installer config
- **Impact:** Users must have Java installed
- **comment from the user(me):**  it not relevant at all for now. we will do it later when we are ready to release the app.


## LOW-08: Missing @Timeout on Tests
- **Files:** All test files
- **Description:** No timeout annotations; tests could hang
- **Impact:** CI pipeline could stall on infinite loops

---