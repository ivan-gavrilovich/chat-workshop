package com.leverx.auth.user;

import com.leverx.auth.common.ConflictException;
import com.leverx.auth.common.UnauthorizedException;
import com.leverx.auth.token.TokenService;
import com.leverx.auth.user.AuthDtos.AuthResponse;
import com.leverx.auth.user.AuthDtos.LoginRequest;
import com.leverx.auth.user.AuthDtos.RegisterRequest;
import com.leverx.auth.user.AuthDtos.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenService);
    }

    @Test
    void registersUserWithNormalizedEmailAndHashedPassword() {
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenService.issue(any())).thenReturn(token());

        authService.register(new RegisterRequest(" Ivan@Example.com ", "Ivan", "secret-password"));

        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("ivan@example.com")
                        && passwordEncoder.matches("secret-password", user.getPasswordHash())));
    }

    @Test
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("ivan@example.com", "Ivan", "secret-password")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void logsInWithCorrectPassword() {
        var user = new User("ivan@example.com", "Ivan", passwordEncoder.encode("secret-password"));
        when(userRepository.findByEmail("ivan@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issue(user)).thenReturn(token());

        assertThat(authService.login(new LoginRequest("IVAN@example.com", "secret-password")).accessToken())
                .isEqualTo("token");
    }

    @Test
    void rejectsWrongPassword() {
        var user = new User("ivan@example.com", "Ivan", passwordEncoder.encode("secret-password"));
        when(userRepository.findByEmail("ivan@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ivan@example.com", "wrong-password")))
                .isInstanceOf(UnauthorizedException.class);
    }

    private static AuthResponse token() {
        return new AuthResponse("token", "Bearer", Instant.now(), new UserResponse(1L, "ivan@example.com", "Ivan"));
    }
}
