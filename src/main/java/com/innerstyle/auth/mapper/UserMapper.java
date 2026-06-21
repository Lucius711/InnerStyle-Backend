package com.innerstyle.auth.mapper;

import com.innerstyle.auth.dto.response.UserProfileResponse;
import com.innerstyle.auth.entity.Role;
import com.innerstyle.auth.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps {@link User} to its public {@link UserProfileResponse}. Avatar relative paths are
 * left as-is for now (a CDN/base-url resolver can be plugged in here later per rule 16).
 */
@Component
public class UserMapper {

    public UserProfileResponse toProfile(User user) {
        List<String> roles = user.getRoles().stream()
            .map(Role::getCode)
            .sorted()
            .toList();
        return new UserProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getAvatarUrl(),
            user.getStatus().name(),
            user.isEmailVerified(),
            roles,
            user.getCreatedAt());
    }
}
