package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ViewModel architecture consistency")
class ViewModelArchitectureConsistencyTest {

    @Test
    @DisplayName("ChatViewModel follows the shared BaseViewModel lifecycle pattern")
    void chatViewModelFollowsSharedBaseViewModelLifecyclePattern() {
        assertTrue(
                BaseViewModel.class.isAssignableFrom(ChatViewModel.class), "ChatViewModel should extend BaseViewModel");
    }

    @Test
    @DisplayName("ProfileViewModel follows the shared BaseViewModel lifecycle pattern")
    void profileViewModelFollowsSharedBaseViewModelLifecyclePattern() {
        assertTrue(
                BaseViewModel.class.isAssignableFrom(ProfileViewModel.class),
                "ProfileViewModel should extend BaseViewModel");
    }
}
