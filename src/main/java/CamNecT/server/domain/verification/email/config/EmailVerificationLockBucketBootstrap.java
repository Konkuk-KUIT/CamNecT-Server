package CamNecT.server.domain.verification.email.config;

import CamNecT.server.domain.verification.email.model.EmailVerificationLockBucket;
import CamNecT.server.domain.verification.email.repository.EmailVerificationLockBucketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.stream.IntStream;

/** Seeds lock rows when local/test uses Hibernate schema creation instead of Flyway. */
@Component
@Profile({"local", "test"})
@RequiredArgsConstructor
public class EmailVerificationLockBucketBootstrap implements ApplicationRunner {

    private static final int BUCKET_COUNT = 64;

    private final EmailVerificationLockBucketRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var existingIds = new HashSet<>(repository.findAll().stream()
                .map(EmailVerificationLockBucket::getBucketId)
                .toList());
        var missingBuckets = IntStream.range(0, BUCKET_COUNT)
                .mapToObj(bucketId -> (short) bucketId)
                .filter(bucketId -> !existingIds.contains(bucketId))
                .map(bucketId -> EmailVerificationLockBucket.builder().bucketId(bucketId).build())
                .toList();
        repository.saveAll(missingBuckets);
    }
}
