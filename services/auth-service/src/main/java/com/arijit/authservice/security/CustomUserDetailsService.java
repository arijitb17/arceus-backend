package com.arijit.authservice.security;

import com.arijit.authservice.entity.User;
import com.arijit.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

// Spring Security doesn't know about our `User` entity - it only understands
// its own `UserDetails` interface. This class is the translator between the
// two: given an email, look up our User row and wrap it as a UserDetails
// Spring Security can authenticate against.
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email: " + email));

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(role.name()))
                .toList();

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword() == null ? "" : user.getPassword())
                .authorities(authorities)
                // Spring Security throws DisabledException automatically at
                // login if this is true - one less thing we check by hand.
                .disabled(!user.isFullyVerified())
                // Separate signal from the above: an admin-suspended account
                // throws LockedException instead, so callers (and our own
                // GlobalExceptionHandler) can tell "never finished verifying"
                // apart from "an admin turned this off".
                .accountLocked(!user.isAccountEnabled())
                .build();
    }
}
