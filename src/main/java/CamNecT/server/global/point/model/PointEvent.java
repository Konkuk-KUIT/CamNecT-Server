package CamNecT.server.global.point.model;

public record PointEvent(
        //여기에는 추후 수정 반드시 필요(임시로 post쪽만 고려해서 작성)
        PointSource source,
        Long postId,
        Long requestId,
        String eventKey
) {
    public static PointEvent postAccess(Long userId, Long postId) {
        String key = "POST_ACCESS:" + userId + ":" + postId;
        return new PointEvent(PointSource.POST_ACCESS_PURCHASE, postId, null, key);
    }

    public static PointEvent signup(Long userId) {
        String key = "SIGNUP:" + userId;
        return new PointEvent(PointSource.SIGNUP,null, null, key);
    }

    public static PointEvent threeLikeReward(Long userId, Long postId) {
        String key = "THREELIKES_REWARD:" + userId + ":" + postId;
        return new PointEvent(PointSource.THREELIKES_REWARD,postId, null, key);
    }

    public static PointEvent gifticonPurchase(Long userId, Long purchaseId) {
        String key = "GIFTICON_PURCHASE:" + userId + ":" + purchaseId;

        // requestId 자리에 purchaseId를 담습니다(기존 필드 재활용)
        return new PointEvent(PointSource.GIFTICON_PURCHASE, null, purchaseId, key);
    }

    public static PointEvent coffeeChatAccepted(Long userId, Long requestId) {
        String key = "COFFEECHAT_ACCEPTANCE:" + userId + ":" + requestId;
        return new PointEvent(PointSource.COFFEECHAT_ACCEPTANCE, null, requestId, key);
    }

    public static PointEvent commentSelection(Long postId, Long commentId) {
        String key = "COMMENT_SELECTION:" + postId + ":" + commentId;
        return new PointEvent(PointSource.COMMENT_SELECTION, postId, null, key);
    }
}
