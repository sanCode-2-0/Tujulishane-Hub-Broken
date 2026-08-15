package com.tujulishanehub.backend.util;

import com.tujulishanehub.backend.models.User;
import com.tujulishanehub.backend.services.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration; // seconds

    @Autowired
    private UserService userService;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        try {
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 64) {
                try {
                    java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-512");
                    keyBytes = digest.digest(keyBytes);
                } catch (java.security.NoSuchAlgorithmException ex) {
                    byte[] paddedBytes = new byte[64];
                    System.arraycopy(keyBytes, 0, paddedBytes, 0, Math.min(keyBytes.length, 64));
                    keyBytes = paddedBytes;
                }
            }
            signingKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT secret is too short for HS512. Provide a 512-bit (64+ byte) secret or a base64-encoded key. See https://tools.ietf.org/html/rfc7518#section-3.2", e);
        }
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public String getRoleFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("role", String.class));
    }

    public Long getUserIdFromToken(String token) {
        return getClaimFromToken(token, claims -> {
            Object userIdObj = claims.get("userId");
            if (userIdObj instanceof Integer) {
                return ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            }
            return null;
        });
    }

    public String getApprovalStatusFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("approvalStatus", String.class));
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, userDetails.getUsername());
    }
    
    public String generateToken(String email) {
        return generateToken(email, false);
    }

    public String generateToken(String email, boolean rememberMe) {
        long tokenExpiration = rememberMe ? 30L * 24 * 60 * 60 : this.expiration;
        try {
            User user = userService.getUserByEmail(email);
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", user.getRole().name());
            claims.put("userId", user.getId());
            claims.put("approvalStatus", user.getApprovalStatus().name());
            return doGenerateToken(claims, email, tokenExpiration);
        } catch (Exception e) {
            // Fallback to basic token if user lookup fails
            return doGenerateToken(email, tokenExpiration);
        }
    }

    private String doGenerateToken(String subject) {
        return doGenerateToken(subject, this.expiration);
    }

    private String doGenerateToken(Map<String, Object> claims, String subject) {
        return doGenerateToken(claims, subject, this.expiration);
    }

    private String doGenerateToken(String subject, long expirationSeconds) {
        return Jwts.builder().setSubject(subject).setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationSeconds * 1000))
                .signWith(signingKey, SignatureAlgorithm.HS512).compact();
    }

    private String doGenerateToken(Map<String, Object> claims, String subject, long expirationSeconds) {
        return Jwts.builder().setClaims(claims).setSubject(subject).setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationSeconds * 1000))
                .signWith(signingKey, SignatureAlgorithm.HS512).compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
    
    public Boolean validateToken(String token, String email) {
        final String username = getUsernameFromToken(token);
        return (username.equals(email) && !isTokenExpired(token));
    }
}
