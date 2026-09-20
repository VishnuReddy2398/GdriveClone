package com.clone.drive.controller;

import com.clone.drive.controller.dto.AuthRequest;
import com.clone.drive.controller.dto.AuthResponse;
import com.clone.drive.controller.dto.MfaVerifyRequest;
import com.clone.drive.controller.dto.RegisterRequest;
import com.clone.drive.controller.dto.RegisterResponse;
import com.clone.drive.domain.User;
import com.clone.drive.domain.UserSession;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.repository.UserSessionRepository;
import com.clone.drive.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller managing public authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();

    private Bucket resolveLoginBucket(String ip) {
        return loginBuckets.computeIfAbsent(ip, k -> Bucket.builder()
                // Max 5 login attempts per minute per IP
                .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1))))
                .build());
    }

    private Bucket resolveRegisterBucket(String ip) {
        return registerBuckets.computeIfAbsent(ip, k -> Bucket.builder()
                // Max 3 registration attempts per minute per IP
                .addLimit(Bandwidth.classic(3, Refill.greedy(3, Duration.ofMinutes(1))))
                .build());
    }

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.clone.drive.service.MfaService mfaService;
    private final UserSessionRepository userSessionRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          UserDetailsService userDetailsService,
                          JwtUtil jwtUtil,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          com.clone.drive.service.MfaService mfaService,
                          UserSessionRepository userSessionRepository) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mfaService = mfaService;
        this.userSessionRepository = userSessionRepository;
    }

    /**
     * Endpoint for new user registration.
     * Validates input, hashes password, and creates the user.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr() != null ? httpRequest.getRemoteAddr() : "unknown";
        Bucket bucket = resolveRegisterBucket(ip);

        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many registration attempts. Please try again later.");
        }

        // Prevent duplicate emails
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email is already in use.");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        // CRITICAL: Hash password before saving to DB
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Registration successful. MFA is optional and can be set up in settings.
        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new RegisterResponse("User registered successfully. You can set up 2FA in your Security Settings.", null)
        );
    }

    /**
     * Endpoint for user login.
     * Authenticates credentials and issues a JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr() != null ? httpRequest.getRemoteAddr() : "unknown";
        Bucket bucket = resolveLoginBucket(ip);

        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many login attempts. Please try again later.");
        }

        // Spring Security's AuthenticationManager handles the password verification against the hashed DB password
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // If authenticate() didn't throw an exception, credentials are valid.
        final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if(userOpt.isPresent()) {
            User user = userOpt.get();
            
            if (user.isMfaEnabled()) {
                // MFA is enabled for this user, validate the code
                if (request.getMfaCode() == null || request.getMfaCode().trim().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body("MFA code required");
                }
                int codeInt;
                try {
                    codeInt = Integer.parseInt(request.getMfaCode().trim());
                } catch (NumberFormatException e) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid MFA code");
                }
                boolean valid = mfaService.verifyCode(user.getTotpSecret(), codeInt);
                if (!valid) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid MFA code");
                }
            }
            
            // Create UserSession
            UserSession session = new UserSession();
            session.setUser(user);
            session.setIpAddress(httpRequest.getRemoteAddr() != null ? httpRequest.getRemoteAddr() : "unknown");
            String userAgent = httpRequest.getHeader("User-Agent");
            session.setUserAgent(userAgent != null ? (userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent) : "unknown");
            userSessionRepository.save(session);
            
            final String jwt = jwtUtil.generateToken(userDetails, session.getId().toString());

            return ResponseEntity.ok(new AuthResponse(jwt, user.getEmail(), user.getName(), user.isMfaEnabled()));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("User lookup failed post-authentication.");
    }

    /**
     * Endpoint for existing users to generate a new MFA secret.
     * Returns a QR code URI but does NOT enable MFA yet.
     */
    @PostMapping("/mfa/setup")
    public ResponseEntity<?> setupMfa(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Generate a new secret, but we shouldn't save it as active until verified.
            // For simplicity, we can temporarily store it in the user's totpSecret field, 
            // but keep isMfaEnabled as false until they verify it.
            String newSecret = mfaService.generateTotpSecret();
            user.setTotpSecret(newSecret);
            userRepository.save(user);

            String mfaUri = mfaService.generateQrCodeUri(user.getEmail(), newSecret);
            return ResponseEntity.ok(new RegisterResponse("Scan the QR code to set up 2FA.", mfaUri));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Endpoint for existing users to verify and enable MFA.
     */
    @PostMapping("/mfa/verify")
    public ResponseEntity<?> verifyMfa(@RequestBody MfaVerifyRequest request, Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (request.getMfaCode() == null || request.getMfaCode().trim().isEmpty()) {

                return ResponseEntity.badRequest().body("MFA code is required");
            }
            int codeInt;
            try {
                codeInt = Integer.parseInt(request.getMfaCode().trim());
            } catch (NumberFormatException e) {

                return ResponseEntity.badRequest().body("MFA code must be numeric");
            }
            if (mfaService.verifyCode(user.getTotpSecret(), codeInt)) {
                user.setMfaEnabled(true);
                userRepository.save(user);
                return ResponseEntity.ok("MFA successfully enabled");
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid MFA code");
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Endpoint for existing users to disable MFA.
     */
    @PostMapping("/mfa/disable")
    public ResponseEntity<?> disableMfa(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setMfaEnabled(false);
            user.setTotpSecret(null);
            userRepository.save(user);
            return ResponseEntity.ok("MFA disabled successfully");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Endpoint for user logout.
     * Revokes the current session.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            try {
                String sessionIdStr = jwtUtil.extractSessionId(jwt);
                if (sessionIdStr != null) {
                    java.util.UUID sessionId = java.util.UUID.fromString(sessionIdStr);
                    Optional<UserSession> sessionOpt = userSessionRepository.findById(sessionId);
                    if (sessionOpt.isPresent()) {
                        UserSession session = sessionOpt.get();
                        session.setRevoked(true);
                        userSessionRepository.save(session);
                        return ResponseEntity.ok("Logged out successfully");
                    }
                }
            } catch (Exception e) {
                // Ignore token parsing errors here
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or missing token");
    }
}
