package datingapp.cli;

import datingapp.core.User;

public class UserSession {
  private User currentUser;

  public User getCurrentUser() {
    return currentUser;
  }

  public void setCurrentUser(User currentUser) {
    this.currentUser = currentUser;
  }

  public boolean isLoggedIn() {
    return currentUser != null;
  }

  public boolean isActive() {
    return currentUser != null && currentUser.getState() == User.State.ACTIVE;
  }
}
