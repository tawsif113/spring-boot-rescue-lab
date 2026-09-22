package com.tawsif.rescuelab.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class RescueUserPrincipal implements UserDetails {

    private final UUID customerId;
    private final String username;
    private final String password;
    private final List<GrantedAuthority> authorities;

    private RescueUserPrincipal(
            UUID customerId,
            String username,
            String password,
            String role
    ) {
        this.customerId = customerId;
        this.username = username;
        this.password = password;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public static RescueUserPrincipal customer(UUID customerId, String username, String password) {
        return new RescueUserPrincipal(customerId, username, password, "CUSTOMER");
    }

    public static RescueUserPrincipal administrator(String username, String password) {
        return new RescueUserPrincipal(null, username, password, "ADMIN");
    }

    public UUID customerId() {
        if (customerId == null) {
            throw new IllegalStateException("Administrative principals do not have a customer ID");
        }
        return customerId;
    }

    public boolean isAdministrator() {
        return authorities.stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
