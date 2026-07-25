package CamNecT.server.domain.community.model.enums;

public enum ContentAccessStatus {
    GRANTED,
    LOGIN_REQUIRED,
    NEED_PURCHASE,
    INSUFFICIENT_POINTS;

    public boolean canReadProtectedContent() {
        return this == GRANTED;
    }
}
