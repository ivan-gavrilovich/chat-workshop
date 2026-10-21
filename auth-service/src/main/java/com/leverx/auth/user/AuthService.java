package com.leverx.auth.user;

import com.leverx.auth.common.ConflictException;
import com.leverx.auth.common.NotFoundException;
import com.leverx.auth.common.UnauthorizedException;
import com.leverx.auth.token.TokenService;
import com.leverx.auth.user.AuthDtos.AuthResponse;
import com.leverx.auth.user.AuthDtos.LoginRequest;
import com.leverx.auth.user.AuthDtos.RegisterRequest;
import com.leverx.auth.user.AuthDtos.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        var email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("User with email '%s' is already registered".formatted(email));
        }
        var user = userRepository.save(
                new User(email, request.fullName().strip(), passwordEncoder.encode(request.password())));
        return tokenService.issue(user);
    }

    public AuthResponse login(LoginRequest request) {
        return userRepository.findByEmail(normalize(request.email()))
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .map(tokenService::issue)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
    }

    public UserResponse findById(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User %d not found".formatted(id)));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
