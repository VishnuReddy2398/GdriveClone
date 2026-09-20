package com.clone.drive.config;

import com.clone.drive.domain.User;
import com.clone.drive.security.CustomUserDetailsService;
import com.clone.drive.security.JwtUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.UUID;

/**
 * WebSocket configuration using STOMP protocol.
 * Hardened with STOMP CONNECT JWT authentication and SUBSCRIBE topic isolation.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    public WebSocketConfig(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory message broker for /topic destinations
        config.enableSimpleBroker("/topic");
        // Prefix for messages sent from the client to the server
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // The endpoint clients connect to. SockJS fallback enabled.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null) {
                    // 1. Authenticate STOMP CONNECT frame
                    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                        String authHeader = accessor.getFirstNativeHeader("Authorization");
                        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                            throw new AccessDeniedException("Missing or invalid Authorization header in STOMP CONNECT");
                        }

                        String token = authHeader.substring(7);
                        String email;
                        try {
                            email = jwtUtil.extractUsername(token);
                        } catch (Exception e) {
                            throw new AccessDeniedException("Invalid JWT token format or signature");
                        }

                        if (email == null) {
                            throw new AccessDeniedException("Invalid JWT token subject");
                        }

                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                        if (!jwtUtil.validateToken(token, userDetails)) {
                            throw new AccessDeniedException("Expired or invalid JWT token");
                        }

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                        accessor.setUser(authentication);
                    }

                    // 2. Authorize STOMP SUBSCRIBE frame
                    if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                        Principal principal = accessor.getUser();
                        if (principal == null) {
                            throw new AccessDeniedException("Unauthenticated subscription attempt");
                        }

                        UUID authenticatedUserId = null;
                        if (principal instanceof UsernamePasswordAuthenticationToken auth && auth.getPrincipal() instanceof User user) {
                            authenticatedUserId = user.getId();
                        }

                        if (authenticatedUserId == null) {
                            throw new AccessDeniedException("Unable to determine authenticated user identity");
                        }

                        String destination = accessor.getDestination();
                        if (destination != null && destination.startsWith("/topic/user.")) {
                            String sub = destination.substring("/topic/user.".length());
                            int dotIndex = sub.indexOf('.');
                            String targetUserIdStr = dotIndex > 0 ? sub.substring(0, dotIndex) : sub;

                            try {
                                UUID targetUserId = UUID.fromString(targetUserIdStr);
                                if (!authenticatedUserId.equals(targetUserId)) {
                                    throw new AccessDeniedException("Unauthorized subscription to topic: " + destination);
                                }
                            } catch (IllegalArgumentException e) {
                                throw new AccessDeniedException("Malformed destination topic: " + destination);
                            }
                        }
                    }
                }

                return message;
            }
        });
    }
}
