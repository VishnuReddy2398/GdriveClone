package com.clone.drive.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.clone.drive.domain.UserSession;
import com.clone.drive.repository.UserSessionRepository;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Intercepts incoming HTTP requests to validate JWTs.
 * Ensures that endpoints are secure and protected against unauthorized access.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final UserSessionRepository userSessionRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, 
                                   CustomUserDetailsService userDetailsService,
                                   UserSessionRepository userSessionRepository) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 1. Check if the Authorization header exists and starts with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract JWT and email
        jwt = authHeader.substring(7);
        try {
            userEmail = jwtUtil.extractUsername(jwt);
        } catch (Exception e) {
            // Invalid token format, signature mismatch, or expired
            filterChain.doFilter(request, response);
            return;
        }

        // 3. If user is found and no authentication exists in the context yet
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            
            // Extract session ID and check against DB for stateful validation
            String sessionIdStr = jwtUtil.extractSessionId(jwt);
            boolean isSessionValid = false;
            
            if (sessionIdStr != null) {
                try {
                    UUID sessionId = UUID.fromString(sessionIdStr);
                    Optional<UserSession> sessionOpt = userSessionRepository.findById(sessionId);
                    
                    if (sessionOpt.isPresent()) {
                        UserSession session = sessionOpt.get();
                        if (!session.isRevoked()) {
                            isSessionValid = true;
                            // Optionally update lastActiveAt here, but it might cause too many DB writes per request
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Invalid UUID format
                }
            }
            
            // Only proceed if session is valid in DB
            if (isSessionValid) {
                // Load user from DB
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                
                // 4. Validate the token
                if (jwtUtil.validateToken(jwt, userDetails)) {
                    // Token is valid! Set up Spring Security context
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    
                    // Add request details
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    // Finally, update the SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }
        
        // 5. Continue filter chain
        filterChain.doFilter(request, response);
    }
}
