package CamNecT.server.global.point.service;

import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.point.model.*;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.global.notification.event.SimpleNotifiableEvent;
import CamNecT.server.global.notification.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private static final String ENSURE_WALLET_SQL = """
            INSERT INTO point_wallet (user_id, balance, version, created_at, updated_at)
            VALUES (?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE user_id = user_id
            """;
    private static final String LOCK_WALLET_BALANCE_SQL = """
            SELECT balance
            FROM point_wallet
            WHERE user_id = ?
            FOR UPDATE
            """;
    private static final String UPDATE_WALLET_SQL = """
            UPDATE point_wallet
            SET balance = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
            """;
    private static final String FIND_BALANCE_SQL = """
            SELECT balance
            FROM point_wallet
            WHERE user_id = ?
            """;
    private static final String LOCK_EVENT_OWNER_SQL = """
            SELECT user_id
            FROM point_transaction
            WHERE event_key = ?
            FOR UPDATE
            """;
    private static final String INSERT_TRANSACTION_SQL = """
            INSERT INTO point_transaction (
                user_id, post_id, request_id, point_change, transaction_type,
                source_type, event_key, balance_after, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void spendPoint(Long userId, int amount, PointEvent event) {
        changePoint(userId, amount, TransactionType.SPEND, event);
    }

    @Transactional
    public void earnPoint(Long userId, int amount, PointEvent event) {
        changePoint(userId, amount, TransactionType.EARN, event);
    }

    @Transactional
    public void changePoint(Long userId, int amount, TransactionType type, PointEvent event) {
        if (userId == null || amount <= 0 || type == null || event == null || event.source() == null) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }

        ensureWallet(userId);
        Integer currentBalance = findBalanceForUpdate(userId);
        if (currentBalance == null) {
            throw new CustomException(UserErrorCode.WALLET_CREATE_FAILED);
        }

        int signedAmount = type == TransactionType.SPEND ? -amount : amount;
        long calculatedBalance = (long) currentBalance + signedAmount;
        int candidateBalanceAfter = (int) calculatedBalance;

        // 먼저 event_key를 claim해야 기존 SPEND 재시도가 현재 잔액 부족으로 오인되지 않는다.
        // 범위/잔액 검증 실패 시 아래 INSERT도 같은 트랜잭션에서 함께 롤백된다.
        if (!insertTransaction(userId, signedAmount, type, event, candidateBalanceAfter)) {
            return;
        }

        if (type == TransactionType.SPEND && currentBalance < amount) {
            throw new CustomException(UserErrorCode.INSUFFICIENT_POINT);
        }
        int balanceAfter = Math.toIntExact(calculatedBalance);

        int updatedRows = jdbcTemplate.update(UPDATE_WALLET_SQL, balanceAfter, userId);
        if (updatedRows != 1) {
            throw new CustomException(UserErrorCode.WALLET_CREATE_FAILED);
        }

        /// 알림 이벤트 발행 (저장 성공 시점)
        NotificationType nType = (type == TransactionType.EARN) ? NotificationType.POINT_EARNED : NotificationType.POINT_SPENT;

        String msg = (type == TransactionType.EARN) ? amount + "P가 지급되었습니다." : amount + "P를 사용했습니다.";

        // postId는 event에 있을 수도 있으니 그대로 연결
        eventPublisher.publishEvent(SimpleNotifiableEvent.ofAllowSelf(
                userId,      // receiver
                null,        // actor (시스템)
                nType,
                msg,
                event.postId(),
                null
        ));
    }

    private void ensureWallet(Long userId) {
        // JdbcTemplate은 JPA 영속성 컨텍스트를 자동 flush하지 않는다. MySQL과 H2 MySQL
        // 모드가 모두 지원하는 upsert로 최초 지갑 생성 경쟁만 원자적으로 해소한다.
        jdbcTemplate.update(ENSURE_WALLET_SQL, userId);
    }

    private Integer findBalanceForUpdate(Long userId) {
        return jdbcTemplate.query(
                LOCK_WALLET_BALANCE_SQL,
                resultSet -> resultSet.next() ? resultSet.getInt("balance") : null,
                userId
        );
    }

    private boolean insertTransaction(
            Long userId,
            int signedAmount,
            TransactionType type,
            PointEvent event,
            int balanceAfter
    ) {
        try {
            jdbcTemplate.update(
                    INSERT_TRANSACTION_SQL,
                    userId,
                    event.postId(),
                    event.requestId(),
                    signedAmount,
                    type.name(),
                    event.source().name(),
                    event.eventKey(),
                    balanceAfter
            );
            return true;
        } catch (DuplicateKeyException exception) {
            if (event.eventKey() == null) {
                throw exception;
            }

            // JDBC의 단일 INSERT 중복은 JPA 영속성 컨텍스트를 실패 상태로 만들지 않는다.
            // 행이 실제로 존재하는 duplicate 경로에서만 잠가 gap lock을 피하고, 같은 사용자의
            // 재시도인지 확인한다. repeatable-read snapshot보다도 최신 커밋을 읽는 locking read다.
            Long existingUserId = jdbcTemplate.query(
                    LOCK_EVENT_OWNER_SQL,
                    resultSet -> resultSet.next() ? resultSet.getLong("user_id") : null,
                    event.eventKey()
            );
            if (userId.equals(existingUserId)) {
                return false;
            }
            throw new CustomException(ErrorCode.CONFLICT, exception);
        }
    }

    @Transactional(readOnly = true)
    public int getBalance(Long userId) {
        Integer balance = jdbcTemplate.query(
                FIND_BALANCE_SQL,
                resultSet -> resultSet.next() ? resultSet.getInt("balance") : null,
                userId
        );
        return balance == null ? 0 : balance;
    }

    @Transactional(readOnly = true)
    public String getEmail(Long userId) {
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        return user.getEmail();
    }
}
