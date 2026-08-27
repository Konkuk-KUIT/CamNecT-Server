package CamNecT.server.domain.community.dto.response;

import CamNecT.server.domain.community.model.enums.ContentAccessStatus;

public record PurchasePostAccessResponse(
        Long postId,
        ContentAccessStatus accessStatus,
        int remainingPoints,
        boolean isAlreadyOwned // 이번 요청에서 새 구매권한을 만들지 않았으면 true(무료·질문 작성자·채택 답변 작성자·기구매 포함)
) {}
