package datingapp.core;

/** Dimensions for how soon a user wants to go on a first date. */
public enum TimeToFirstDate {
    QUICKLY("Quickly (1-2 days)"),
    FEW_DAYS("A few days"),
    WEEKS("Weeks"),
    MONTHS("Months"),
    WILDCARD("No preference");

    private final String displayName;

    TimeToFirstDate(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
