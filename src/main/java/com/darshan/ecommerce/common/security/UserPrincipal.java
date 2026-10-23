package com.darshan.ecommerce.common.security;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

// The authenticated identity: controllers take the user id from here, never from the URL.
public final class UserPrincipal implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String email;
    private String passwordHash;
    private final Set<GrantedAuthority> authorities;

    public UserPrincipal(Long id, String email, String passwordHash, Set<GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.authorities = Set.copyOf(authorities);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public String toString() {
        return "UserPrincipal[id=" + id + "]";
    }
}
