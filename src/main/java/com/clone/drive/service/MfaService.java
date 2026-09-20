package com.clone.drive.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.stereotype.Service;

@Service
public class MfaService {

    private final GoogleAuthenticator gAuth;
    private static final String ISSUER = "PersonalDrive";

    public MfaService() {
        // Allow codes from 1 time-step before/after (90 second window total)
        GoogleAuthenticatorConfig config = new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                .setWindowSize(3)
                .build();
        this.gAuth = new GoogleAuthenticator(config);
    }

    /**
     * Generates a new TOTP secret key for a user.
     * @return the Base32 encoded secret key.
     */
    public String generateTotpSecret() {
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        return key.getKey();
    }

    /**
     * Validates a given 6-digit TOTP code against the user's secret.
     * @param secret The user's TOTP secret.
     * @param code The 6-digit code provided by the user.
     * @return true if valid, false otherwise.
     */
    public boolean verifyCode(String secret, int code) {
        return gAuth.authorize(secret, code);
    }

    /**
     * Generates the standard otpauth:// URI which can be converted to a QR code.
     */
    public String generateQrCodeUri(String email, String secret) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
                ISSUER, email, secret, ISSUER);
    }
}
