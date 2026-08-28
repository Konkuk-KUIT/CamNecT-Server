package CamNecT.server.global.point.service;

import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.common.config.QuerydslConfig;
import CamNecT.server.global.point.model.PointEvent;
import CamNecT.server.global.point.model.PointSource;
import CamNecT.server.global.point.model.PointTransaction;
import CamNecT.server.global.point.model.PointWallet;
import CamNecT.server.global.point.repository.PointTransactionRepository;
import CamNecT.server.global.point.repository.PointWalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({PointService.class, QuerydslConfig.class})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PointServiceConcurrencyIntegrationTest {

    @Autowired PointService pointService;
    @Autowired PointWalletRepository walletRepository;
    @Autowired PointTransactionRepository transactionRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void concurrentFirstChangesCreateOneWalletAndPreserveBothBalances() throws Exception {
        Long userId = 9_100_001L;
        PointEvent firstEvent = event("DISTINCT:" + UUID.randomUUID());
        PointEvent secondEvent = event("DISTINCT:" + UUID.randomUUID());

        runConcurrently(
                () -> pointService.earnPoint(userId, 100, firstEvent),
                () -> pointService.earnPoint(userId, 100, secondEvent)
        );

        PointWallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        List<PointTransaction> transactions = transactionRepository.findAll();

        assertThat(walletRepository.count()).isEqualTo(1L);
        assertThat(wallet.getBalance()).isEqualTo(200);
        assertThat(transactions).hasSize(2);
        assertThat(transactions).extracting(PointTransaction::getBalanceAfter)
                .containsExactlyInAnyOrder(100, 200);
    }

    @Test
    void concurrentRetriesOfOneEventChangeTheWalletOnlyOnce() throws Exception {
        Long userId = 9_100_002L;
        PointEvent sameEvent = event("IDEMPOTENT:" + UUID.randomUUID());

        runConcurrently(
                () -> pointService.earnPoint(userId, 100, sameEvent),
                () -> pointService.earnPoint(userId, 100, sameEvent)
        );

        PointWallet wallet = walletRepository.findByUserId(userId).orElseThrow();

        assertThat(wallet.getBalance()).isEqualTo(100);
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    void duplicateSpendIsIdempotentEvenWhenTheCurrentBalanceNoLongerCoversIt() {
        Long userId = 9_100_013L;
        pointService.earnPoint(userId, 100, event("SPEND-SEED:" + UUID.randomUUID()));
        PointEvent spendEvent = event("SPEND-ONCE:" + UUID.randomUUID());
        pointService.spendPoint(userId, 100, spendEvent);

        pointService.spendPoint(userId, 100, spendEvent);

        assertThat(pointService.getBalance(userId)).isZero();
        assertThat(transactionRepository.findAll()).hasSize(2);
    }

    @Test
    void differentUsersAndEventsCanChangePointsConcurrently() throws Exception {
        Long firstUserId = 9_100_009L;
        Long secondUserId = 9_100_010L;

        runConcurrently(
                () -> pointService.earnPoint(firstUserId, 70, event("USER-A:" + UUID.randomUUID())),
                () -> pointService.earnPoint(secondUserId, 80, event("USER-B:" + UUID.randomUUID()))
        );

        assertThat(walletRepository.findByUserId(firstUserId).orElseThrow().getBalance()).isEqualTo(70);
        assertThat(walletRepository.findByUserId(secondUserId).orElseThrow().getBalance()).isEqualTo(80);
        assertThat(transactionRepository.findAll()).hasSize(2);
    }

    @Test
    void eventKeyOwnedByAnotherUserIsAConflictAndDoesNotChangeTheirWallet() {
        Long firstUserId = 9_100_011L;
        Long secondUserId = 9_100_012L;
        PointEvent sharedEvent = event("CROSS-USER:" + UUID.randomUUID());
        pointService.earnPoint(firstUserId, 100, sharedEvent);
        walletRepository.saveAndFlush(PointWallet.builder()
                .userId(secondUserId)
                .balance(0)
                .build());

        assertThatThrownBy(() -> pointService.earnPoint(secondUserId, 100, sharedEvent))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        assertThat(walletRepository.findByUserId(secondUserId).orElseThrow().getBalance()).isZero();
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    void transactionInsertFailureLeavesWalletAndLedgerUnchanged() {
        Long userId = 9_100_003L;
        walletRepository.saveAndFlush(PointWallet.builder()
                .userId(userId)
                .balance(0)
                .build());
        PointEvent oversizedKey = event("X".repeat(65));

        assertThatThrownBy(() -> pointService.earnPoint(userId, 100, oversizedKey))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(walletRepository.findByUserId(userId).orElseThrow().getBalance()).isZero();
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void surroundingTransactionFailureRollsBackTheWalletAndLedgerTogether() {
        Long userId = 9_100_008L;
        IllegalStateException outerFailure = new IllegalStateException("outer business failure");

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            pointService.earnPoint(userId, 100, event("ROLLBACK:" + UUID.randomUUID()));
            throw outerFailure;
        })).isSameAs(outerFailure);

        assertThat(walletRepository.findByUserId(userId)).isEmpty();
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void nullTransactionTypeIsRejectedBeforeCreatingAWallet() {
        Long userId = 9_100_004L;

        assertThatThrownBy(() -> pointService.changePoint(userId, 100, null, event("TYPE:" + UUID.randomUUID())))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));

        assertThat(walletRepository.findByUserId(userId)).isEmpty();
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void cachedWalletDoesNotHideAConcurrentCommittedBalanceBeforeTheLockedChange() throws Exception {
        Long userId = 9_100_005L;
        pointService.earnPoint(userId, 100, event("SEED:" + UUID.randomUUID()));

        readCommittedTransaction().executeWithoutResult(status -> {
            // 같은 영속성 컨텍스트에 과거 wallet 상태를 올려 둔다.
            assertThat(walletRepository.findByUserId(userId).orElseThrow().getBalance()).isEqualTo(100);
            assertThat(pointService.getBalance(userId)).isEqualTo(100);

            runInAnotherThread(() -> pointService.earnPoint(
                    userId, 50, event("CONCURRENT:" + UUID.randomUUID())));

            // 잠금 읽기는 JPA 1차 캐시의 100이 아니라 DB의 최신 150을 기준으로 차감해야 한다.
            pointService.spendPoint(userId, 120, event("SPEND:" + UUID.randomUUID()));
            assertThat(pointService.getBalance(userId)).isEqualTo(30);
        });

        assertThat(pointService.getBalance(userId)).isEqualTo(30);
        assertThat(transactionRepository.findAll()).hasSize(3);
    }

    @Test
    void consecutiveChangesInOneOuterTransactionKeepTheirUncommittedBalance() {
        Long userId = 9_100_006L;

        repeatableReadTransaction().executeWithoutResult(status -> {
            pointService.earnPoint(userId, 100, event("OUTER-EARN:" + UUID.randomUUID()));
            pointService.spendPoint(userId, 40, event("OUTER-SPEND:" + UUID.randomUUID()));
            assertThat(pointService.getBalance(userId)).isEqualTo(60);
        });

        assertThat(pointService.getBalance(userId)).isEqualTo(60);
        assertThat(transactionRepository.findAll()).hasSize(2);
    }

    @Test
    void idempotencyRecheckSeesAnEventCommittedAfterAnEarlierRead() {
        Long userId = 9_100_007L;
        pointService.earnPoint(userId, 100, event("IDEMPOTENCY-SEED:" + UUID.randomUUID()));
        PointEvent concurrentEvent = event("LATE-EVENT:" + UUID.randomUUID());

        readCommittedTransaction().executeWithoutResult(status -> {
            // 먼저 일반 읽기를 한 뒤 별도 트랜잭션이 동일 이벤트를 커밋한다.
            assertThat(pointService.getBalance(userId)).isEqualTo(100);
            runInAnotherThread(() -> pointService.earnPoint(userId, 50, concurrentEvent));

            pointService.earnPoint(userId, 50, concurrentEvent);
        });

        assertThat(pointService.getBalance(userId)).isEqualTo(150);
        assertThat(transactionRepository.findAll()).hasSize(2);
    }

    private PointEvent event(String eventKey) {
        return new PointEvent(PointSource.ADMIN_ADJUSTMENT, null, null, eventKey);
    }

    private TransactionTemplate repeatableReadTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction;
    }

    private TransactionTemplate readCommittedTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    private void runConcurrently(Runnable firstTask, Runnable secondTask) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> runAfterBarrier(firstTask, ready, start));
            Future<?> second = executor.submit(() -> runAfterBarrier(secondTask, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void runAfterBarrier(Runnable task, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent point test barrier timed out");
            }
            task.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent point test interrupted", e);
        }
    }

    private void runInAnotherThread(Runnable task) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(task).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("concurrent point update failed", e);
        } finally {
            executor.shutdownNow();
        }
    }
}
