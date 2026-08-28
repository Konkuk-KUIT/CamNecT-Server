package CamNecT.server.domain.profile.service;

import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProfileServiceAccountStateTest {

    @Mock AccountAccessGuard accountAccessGuard;

    @InjectMocks ProfileService profileService;

    @Test
    void everyProfileMutationRevalidatesTheLockedAccountBeforeSideEffects() {
        doThrow(new CustomException(AuthErrorCode.USER_SUSPENDED))
                .when(accountAccessGuard).requireAccessibleForUpdate(1L);

        assertAll(
                () -> assertThrows(CustomException.class, () -> profileService.updatePrivacy(1L, null)),
                () -> assertThrows(CustomException.class, () -> profileService.updateBio(1L, null)),
                () -> assertThrows(CustomException.class, () -> profileService.createOnboarding(1L, null)),
                () -> assertThrows(CustomException.class, () -> profileService.updateProfileTags(1L, null)),
                () -> assertThrows(CustomException.class, () -> profileService.presignProfileImageUpload(1L, null)),
                () -> assertThrows(CustomException.class, () -> profileService.updateMyProfileImage(1L, null))
        );
        verify(accountAccessGuard, times(6)).requireAccessibleForUpdate(1L);
    }
}
