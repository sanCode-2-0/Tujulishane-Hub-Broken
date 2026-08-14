package com.tujulishanehub.backend.config;

import com.tujulishanehub.backend.config.JwtRequestFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tujulishanehub.backend.payload.ApiResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // allow static assets and SPA entry points
                        // Avoid path patterns that PathPatternParser rejects (like /**/*.html). Permit common static locations instead.
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/resources/**", "/static/**", "/frontend/**", "/test/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/users/register", "/api/users/verify-otp", "/api/users/resend-otp").permitAll()  // Public user registration and OTP endpoints
                        // Allow public GET access to specific project endpoints only (for guest/unauthenticated viewing)
                        .requestMatchers(HttpMethod.GET, "/api/projects/thematic-areas", "/api/projects/{id}", "/api/projects/public/statistics").permitAll()
                        // Allow public GET access to thematic area definitions (dynamic, driven by SUPER_ADMIN_APPROVER)
                        .requestMatchers(HttpMethod.GET, "/api/thematic-areas").permitAll()
                        // All other /api/projects/** endpoints require authentication and use @PreAuthorize for role-based access
                        .requestMatchers("/api/announcements", "/api/announcements/{id}").permitAll()  // Allow public access to view announcements
                        .requestMatchers("/api/general-announcements", "/api/general-announcements/{id}").permitAll()  // Allow public access to view general announcements
                        .requestMatchers("/api/organizations", "/api/organizations/**").permitAll()  // Allow public access to organizations
                        .requestMatchers("/h2-console/**").permitAll()
                        // Remove the hardcoded POST restriction - let @PreAuthorize in controllers handle it
                        .anyRequest().authenticated()
                )
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ApiResponse<Object> body = new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(), "Authentication required. Please log in and retry.", null);
                objectMapper.writeValue(response.getOutputStream(), body);
            })
            .accessDeniedHandler((request, response, accessDeniedException) -> {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ApiResponse<Object> body = new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "Access denied: insufficient permissions to perform this action.", null);
                objectMapper.writeValue(response.getOutputStream(), body);
            })
        )
                .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow specific origins instead of wildcard when using credentials
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type", "Accept", "User-Agent", "Pragma"));
        configuration.setAllowCredentials(true); // Enable credentials for authenticated requests
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
