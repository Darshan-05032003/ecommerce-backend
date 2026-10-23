package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.common.security.UserPrincipal;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserPrincipal loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(CustomUserDetailsService::toPrincipal)
                .orElseThrow(() -> new UsernameNotFoundException("No user for the given email"));
    }

    static UserPrincipal toPrincipal(User user) {
        Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toSet());
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPassword(), authorities);
    }
}
