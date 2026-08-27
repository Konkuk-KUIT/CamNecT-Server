package CamNecT.server.domain.community.dto.request;

public final class CommunityRequestLimits {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_POST_CONTENT_LENGTH = 20_000;
    public static final int MAX_COMMENT_CONTENT_LENGTH = 5_000;
    public static final int MAX_TAGS_PER_POST = 5;
    public static final int MAX_ATTACHMENTS_PER_POST = 3;
    public static final int MAX_FILE_KEY_LENGTH = 500;
    public static final int MAX_SEARCH_KEYWORD_LENGTH = 100;

    private CommunityRequestLimits() {
    }
}
