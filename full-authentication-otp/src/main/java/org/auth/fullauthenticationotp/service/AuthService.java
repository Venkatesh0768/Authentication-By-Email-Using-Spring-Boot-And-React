package org.auth.fullauthenticationotp.service;

import lombok.RequiredArgsConstructor;
import org.auth.fullauthenticationotp.dto.*;
import org.auth.fullauthenticationotp.exception.*;
import org.auth.fullauthenticationotp.model.RefreshToken;
import org.auth.fullauthenticationotp.model.Role;
import org.auth.fullauthenticationotp.model.RoleType;
import org.auth.fullauthenticationotp.model.User;
import org.auth.fullauthenticationotp.repository.OTPRepository;
import org.auth.fullauthenticationotp.repository.RefreshTokenRepository;
import org.auth.fullauthenticationotp.repository.RoleRepository;
import org.auth.fullauthenticationotp.repository.UserRepository;
import org.auth.fullauthenticationotp.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OTPRepository otpRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final EmailService emailService;
    private final OTPService otpService;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Transactional
    public ApiResponse signup(SignupRequest request) {
        // Validate email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        // Create user
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .emailVerified(false)
                .enabled(false)
                .build();

        // Assign default role
        Role userRole = roleRepository.findByName(RoleType.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Default role not found"));
        user.setRoles(Set.of(userRole));

        userRepository.save(user);

        // Generate and send OTP
        otpService.generateAndSendOTP(user.getEmail());

        return new ApiResponse(true,
                "Registration successful. Please verify your email with OTP sent to " +
                        user.getEmail(), null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Check if email is verified
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Please verify your email first");
        }

        // Generate tokens
        String accessToken = tokenProvider.generateToken(authentication);
        RefreshToken refreshToken = otpService.createRefreshToken(user);

        UserDTO userDTO = convertToUserDTO(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .expiresIn(jwtExpiration / 1000)
                .user(userDTO)
                .build();
    }

    @Transactional
    public ApiResponse verifyOTP(OTPVerificationRequest request) {
        boolean isValid = otpService.validateOTP(request.getEmail(), request.getOtp());

        if (!isValid) {
            throw new InvalidOTPException("Invalid or expired OTP");
        }

        // Update user as verified and enabled
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setEmailVerified(true);
        user.setEnabled(true);
        userRepository.save(user);

        return new ApiResponse(true,
                "Email verified successfully. You can now login", null);
    }

    public ApiResponse resendOTP(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.isEmailVerified()) {
            throw new EmailAlreadyVerifiedException("Email already verified");
        }

        otpService.generateAndSendOTP(email);

        return new ApiResponse(true, "OTP sent successfully to " + email, null);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        return refreshTokenRepository.findByToken(refreshToken)
                .map(otpService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String accessToken = tokenProvider.generateTokenFromUsername(user.getEmail());
                    return AuthResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .expiresIn(jwtExpiration / 1000)
                            .user(convertToUserDTO(user))
                            .build();
                })
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));
    }

    private UserDTO convertToUserDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .emailVerified(user.isEmailVerified())
                .roles(user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .collect(Collectors.toSet()))
                .build();
    }
}