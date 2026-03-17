package com.example.yearend.user.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.user.api.UserDtos;
import com.example.yearend.user.domain.User;
import com.example.yearend.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email.toLowerCase())
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UserDtos.UserProfileResponse getMe(String email) {
        return toProfile(getByEmail(email));
    }

    @Transactional
    public UserDtos.UserProfileResponse updateMe(String email, UserDtos.UpdateMeRequest request) {
        User user = getByEmail(email);
        user.setName(request.name().trim());
        return toProfile(user);
    }

    public UserDtos.UserProfileResponse toProfile(User user) {
        return new UserDtos.UserProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getRole(),
            user.getStatus(),
            user.getLastLoginAt()
        );
    }
}
