package org.auth.fullauthenticationotp.service;

import org.auth.fullauthenticationotp.dto.*;
import org.auth.fullauthenticationotp.exception.EmailAlreadyExistsException;
import org.auth.fullauthenticationotp.exception.EmailNotVerifiedException;
import org.auth.fullauthenticationotp.exception.InvalidOTPException;
import org.auth.fullauthenticationotp.model.RefreshToken;
import org.auth.fullauthenticationotp.model.Role;
import org.auth.fullauthenticationotp.model.RoleType;
import org.auth.fullauthenticationotp.model.User;
import org.auth.fullauthenticationotp.repository.OTPRepository;
import org.auth.fullauthenticationotp.repository.RefreshTokenRepository;
import org.auth.fullauthenticationotp.repository.RoleRepository;
import org.auth.fullauthenticationotp.repository.UserRepository;
import org.auth.fullauthenticationotp.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private OTPRepository otpRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private EmailService emailService;
    @Mock private OTPService otpService;

    @InjectMocks
    private AuthService authService;

    private SignupRequest signupRequest;
    private LoginRequest loginRequest;
    private User testUser;
    private Role userRole;

    @BeforeEach
    void setUp() {
        signupRequest = new SignupRequest();
        signupRequest.setEmail("test@example.com");
        signupRequest.setPassword("Test@1234");
        signupRequest.setFirstName("John");
        signupRequest.setLastName("Doe");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("Test@1234");

        userRole = new Role();
        userRole.setId(1L);
        userRole.setName(RoleType.ROLE_USER);

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encodedPassword")
                .firstName("John")
                .lastName("Doe")
                .emailVerified(true)
                .enabled(true)
                .roles(Set.of(userRole))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void signup_ShouldCreateUserSuccessfully() {
        // Arrange
        when(userRepository.existsByEmail(signupRequest.getEmail())).thenReturn(false);
        when(roleRepository.findByName(RoleType.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode(signupRequest.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        doNothing().when(otpService).generateAndSendOTP(anyString());

        // Act
        ApiResponse response = authService.signup(signupRequest);

        // Assert
        assertTrue(response.isSuccess());
        assertTrue(response.getMessage().contains("Registration successful"));
        verify(userRepository).save(any(User.class));
        verify(otpService).generateAndSendOTP(signupRequest.getEmail());
    }

    @Test
    void signup_ShouldThrowException_WhenEmailExists() {
        // Arrange
        when(userRepository.existsByEmail(signupRequest.getEmail())).thenReturn(true);

        // Act & Assert
        assertThrows(EmailAlreadyExistsException.class, () ->
                authService.signup(signupRequest)
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_ShouldReturnAuthResponse_WhenCredentialsValid() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.getEmail()))
                .thenReturn(Optional.of(testUser));
        when(tokenProvider.generateToken(authentication)).thenReturn("accessToken");

        RefreshToken refreshToken = RefreshToken.builder()
                .token("refreshToken")
                .user(testUser)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();
        when(otpService.createRefreshToken(testUser)).thenReturn(refreshToken);

        // Act
        AuthResponse response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("accessToken", response.getAccessToken());
        assertEquals("refreshToken", response.getRefreshToken());
        assertNotNull(response.getUser());
        assertEquals(testUser.getEmail(), response.getUser().getEmail());
    }

    @Test
    void login_ShouldThrowException_WhenEmailNotVerified() {
        // Arrange
        testUser.setEmailVerified(false);
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.getEmail()))
                .thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(EmailNotVerifiedException.class, () ->
                authService.login(loginRequest)
        );
    }

    @Test
    void verifyOTP_ShouldVerifyUser_WhenOTPValid() {
        // Arrange
        OTPVerificationRequest request = new OTPVerificationRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");

        testUser.setEmailVerified(false);
        testUser.setEnabled(false);

        when(otpService.validateOTP(request.getEmail(), request.getOtp())).thenReturn(true);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        ApiResponse response = authService.verifyOTP(request);

        // Assert
        assertTrue(response.isSuccess());
        verify(userRepository).save(argThat(user ->
                user.isEmailVerified() && user.isEnabled()
        ));
    }

    @Test
    void verifyOTP_ShouldThrowException_WhenOTPInvalid() {
        // Arrange
        OTPVerificationRequest request = new OTPVerificationRequest();
        request.setEmail("test@example.com");
        request.setOtp("000000");

        when(otpService.validateOTP(request.getEmail(), request.getOtp())).thenReturn(false);

        // Act & Assert
        assertThrows(InvalidOTPException.class, () ->
                authService.verifyOTP(request)
        );
    }
}
