package org.auth.fullauthenticationotp.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.auth.fullauthenticationotp.dto.*;
import org.auth.fullauthenticationotp.exception.InvalidOTPException;
import org.auth.fullauthenticationotp.exception.UserNotFoundException;
import org.auth.fullauthenticationotp.model.User;
import org.auth.fullauthenticationotp.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173/")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse> signup(@Valid @RequestBody SignupRequest request) {
        ApiResponse response = authService.signup(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse> verifyOTP(
            @Valid @RequestBody OTPVerificationRequest request) {
        ApiResponse response = authService.verifyOTP(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse> resendOTP(@RequestParam String email) {
        ApiResponse response = authService.resendOTP(email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(
            @RequestBody(required = false) Map<String, String> request,
            @RequestParam(value = "email", required = false) String emailParam) {
        String email = null;
        if (request != null) {
            email = request.get("email");
        }
        if (email == null || email.isBlank()) {
            email = emailParam;
        }
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Email is required", null));
        }
        authService.requestPasswordReset(email.trim());
        return ResponseEntity.ok(new ApiResponse(true,
                "If an account exists for this email, an OTP has been sent.", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestBody(required = false) Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        String token = null;
        if (request != null) {
            token = request.get("refreshToken");
        }
        if ((token == null || token.isBlank()) && authorizationHeader != null) {
            token = authorizationHeader;
        }
        if (token != null) {
            token = token.trim();
            if (token.length() >= 7 && token.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = token.substring(7).trim();
            }
        }
        if (token == null || token.isBlank()) {
            throw new org.auth.fullauthenticationotp.exception.InvalidTokenException("Refresh token is required");
        }
        AuthResponse response = authService.refreshToken(token);
        return ResponseEntity.ok(response);
    }
}

