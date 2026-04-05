package com.eaglepoint.storehub.security;

import com.eaglepoint.storehub.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String email;
    private final String password;
    private final Long siteId;
    private final String role;
    private final boolean enabled;
    private final Instant lastAuthenticatedAt;
    private final long tokenVersion;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.siteId = user.getSite() != null ? user.getSite().getId() : null;
        this.role = user.getRole().name();
        this.enabled = user.isEnabled();
        this.lastAuthenticatedAt = user.getLastAuthenticatedAt();
        this.tokenVersion = user.getTokenVersion();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return enabled; }
}
