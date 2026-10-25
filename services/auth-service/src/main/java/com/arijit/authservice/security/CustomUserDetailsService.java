package com.arijit.authservice.security;

import com.arijit.authservice.entity.User;
import com.arijit.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.arijit.arceus.events.logging.LogMasker;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.debug("User details lookup failed: no user for email={}", LogMasker.email(email));
                    return new UsernameNotFoundException("No user found with email: " + email);
                });

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(role.name()))
                .toList();

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword() == null ? "" : user.getPassword())
                .authorities(authorities)
                .disabled(!user.isFullyVerified())
                .accountLocked(!user.isAccountEnabled())
                .build();
    }
}
