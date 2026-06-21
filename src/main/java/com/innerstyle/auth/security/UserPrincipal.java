package com.innerstyle.auth.security;

import com.innerstyle.auth.entity.Role;
import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.entity.enums.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Authenticated principal exposed to controllers via {@code @AuthenticationPrincipal}.
 * Can be built from a {@link User} entity (password login) or from JWT claims (stateless).
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final boolean active;
    private final boolean locked;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID id, String email, String passwordHash, boolean active,
                         boolean locked, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.active = active;
        this.locked = locked;
        this.authorities = authorities;
    }

    public static UserPrincipal from(User user) {
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
        List<SimpleGrantedAuthority> auths = user.getRoles().stream()
            .map(Role::getCode)
            .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
            .toList();
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(),
            user.getStatus() == UserStatus.ACTIVE, locked, auths);
    }

    public static UserPrincipal fromClaims(UUID id, String email, List<String> roles) {
        List<SimpleGrantedAuthority> auths = roles.stream()
            .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
            .toList();
        return new UserPrincipal(id, email, null, true, false, auths);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
