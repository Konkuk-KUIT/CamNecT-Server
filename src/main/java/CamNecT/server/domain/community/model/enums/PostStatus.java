package CamNecT.server.domain.community.model.enums;

public enum PostStatus {
    PUBLISHED,
    HIDDEN,
    DELETED;

    public boolean isPublished() {
        return this == PUBLISHED;
    }
}
