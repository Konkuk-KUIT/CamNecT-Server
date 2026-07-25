package CamNecT.server.domain.community.model.enums;

public enum CommentStatus {
    PUBLISHED,
    HIDDEN,
    DELETED;

    public boolean isPublished() {
        return this == PUBLISHED;
    }

    public boolean isDeleted() {
        return this == DELETED;
    }
}
