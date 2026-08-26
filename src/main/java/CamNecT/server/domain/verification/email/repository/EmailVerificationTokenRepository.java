package CamNecT.server.domain.verification.email.repository;

import CamNecT.server.domain.verification.email.model.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findTopByEmailAndUsedAtIsNullOrderByIdDesc(String email);

    Optional<EmailVerificationToken> findByActiveEmail(String activeEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from EmailVerificationToken token where token.activeEmail = :activeEmail")
    Optional<EmailVerificationToken> findByActiveEmailForUpdate(
            @Param("activeEmail") String activeEmail
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from EmailVerificationToken token where token.activeEmail = :activeEmail and token.usedAt is null")
    int deleteByActiveEmail(@Param("activeEmail") String activeEmail);

    void deleteByUser_UserId(Long userId);
}
