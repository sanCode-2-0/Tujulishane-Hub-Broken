package com.tujulishanehub.backend.controllers;

import com.tujulishanehub.backend.models.ApprovalStatus;
import com.tujulishanehub.backend.models.ApprovalWorkflowStatus;
import com.tujulishanehub.backend.models.User;
import com.tujulishanehub.backend.models.UserDocument;
import com.tujulishanehub.backend.payload.ApiResponse;
import java.util.Arrays;
import com.tujulishanehub.backend.payload.UserProfileDTO;
import com.tujulishanehub.backend.repositories.UserDocumentRepository;
import com.tujulishanehub.backend.services.OrganizationService;
import com.tujulishanehub.backend.services.UserService;
import com.tujulishanehub.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDocumentRepository userDocumentRepository;

    @Value("${jwt.expiration:3600}")
    private Long jwtExpiration;

    // Simple health check endpoint to test JSON serialization
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        ApiResponse<String> response = new ApiResponse<>(200, "API is working", "OK");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Object>> register(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam(value = "organization", required = false) String organization,
            @RequestParam(value = "accountType", defaultValue = "PARTNER") String accountType,
            @RequestParam(value = "parentDonorId", required = false) Long parentDonorId,
            @RequestParam(value = "organizationId", required = false) Long organizationId,
            @RequestParam(value = "documents", required = false) List<MultipartFile> documents) {
        try {
            logger.info("Registration request received for email: {}, accountType: {}, organization: {}", email, accountType, organization);
            
            if (name == null || name.isEmpty() || email == null || email.isEmpty()) {
                ApiResponse<Object> response = new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "Name and email are required.", null);
                return ResponseEntity.badRequest().body(response);
            }
            
            // Default to PARTNER if accountType not specified (backward compatibility)
            if (accountType == null) {
                accountType = "PARTNER";
            }
            
            // Handle organization name - find or create organization
            Long finalOrganizationId = organizationId;
            if (organization != null && !organization.trim().isEmpty()) {
                finalOrganizationId = organizationService.findOrCreateOrganization(organization);
                logger.info("Organization '{}' resolved to ID: {}", organization, finalOrganizationId);
            }
            
            // Register based on account type
            User registeredUser = null;
            if ("DONOR".equalsIgnoreCase(accountType)) {
                registeredUser = userService.registerDonor(name, email, finalOrganizationId);
            } else if ("PARTNER".equalsIgnoreCase(accountType)) {
                registeredUser = userService.registerPartner(name, email, finalOrganizationId, parentDonorId);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "Invalid account type. Must be DONOR or PARTNER.", null);
                return ResponseEntity.badRequest().body(response);
            }
            
            // Handle supporting documents if provided (OPTIONAL)
            if (documents != null && !documents.isEmpty() && registeredUser != null) {
                logger.info("Processing {} supporting documents for user: {}", documents.size(), email);
                try {
                    for (MultipartFile file : documents) {
                        if (!file.isEmpty()) {
                            UserDocument document = new UserDocument();
                            document.setUser(registeredUser);
                            document.setFileName(file.getOriginalFilename());
                            document.setFileType(file.getContentType());
                            document.setFileSize(file.getSize());
                            document.setFileData(file.getBytes());
                            
                            // Set additional fields
                            document.setUploadedBy(registeredUser); // User uploads their own documents
                            LocalDateTime now = LocalDateTime.now();
                            document.setUploadDate(now);
                            document.setCreatedAt(now);
                            
                            userDocumentRepository.save(document);
                            logger.info("Saved supporting document: {} for user: {}", file.getOriginalFilename(), email);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error saving supporting documents for user {}: {}", email, e.getMessage(), e);
                    // Don't fail registration if documents fail - they're optional
                }
            } else {
                logger.info("No supporting documents provided for user: {} (this is optional)", email);
            }
            
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.OK.value(), "you'll receive a confirmation email within 12 hours", null);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            // Log full stacktrace for diagnostics (EmailService will also log)
            logger.error("Error during registration for {}: {}", email, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Object>> verify(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String otp = payload.get("otp");
        
        // Trim whitespace from inputs
        if (email != null) email = email.trim();
        if (otp != null) otp = otp.trim();
        
        logger.info("OTP verification attempt for email: {}, OTP: {}", email, otp);
        
        if (userService.verifyOtp(email, otp)) {
            logger.info("OTP verification successful for email: {}", email);
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.OK.value(), "User verified successfully.", null);
            return ResponseEntity.ok(response);
        } else {
            logger.warn("OTP verification failed for email: {}", email);
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "Invalid OTP.", null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Object>> login(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        if (email == null || email.isEmpty()) {
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "Email is required.", null);
            return ResponseEntity.badRequest().body(response);
        }

        var userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "User not found. Please sign up.", null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        var user = userOpt.get();
        if (!"ACTIVE".equals(user.getStatus())) {
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "Account not active. Complete verification first.", null);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // Send a fresh login OTP
        try {
            String otp = userService.sendLoginOtp(email);
            Map<String, Object> data = null;
            String testingMode = System.getenv("OTP_TESTING_MODE");
            if ("true".equalsIgnoreCase(testingMode)) {
                data = new java.util.HashMap<>();
                data.put("otp", otp);
            }
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.OK.value(), "Login OTP sent to your email.", data);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("Failed to send login OTP to {}: {}", email, e.getMessage(), e);
            
            // Check for specific error conditions
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("not approved")) {
                ApiResponse<Object> response = new ApiResponse<>(HttpStatus.FORBIDDEN.value(), 
                    "Your account is pending administrator approval. Please wait for approval before logging in.", null);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            } else if (errorMessage != null && errorMessage.contains("not active")) {
                ApiResponse<Object> response = new ApiResponse<>(HttpStatus.FORBIDDEN.value(), 
                    "Account not active. Complete verification first.", null);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            // Generic error for other cases
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to send login OTP: " + errorMessage, null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/verify/login")
    public ResponseEntity<ApiResponse<Object>> verifyLogin(@RequestBody Map<String, Object> payload) {
        String email = (String) payload.get("email");
        String otp = (String) payload.get("otp");
        boolean rememberMe = false;
        if (payload.containsKey("rememberMe")) {
            Object rm = payload.get("rememberMe");
            if (rm instanceof Boolean) {
                rememberMe = (Boolean) rm;
            } else if (rm instanceof String) {
                rememberMe = "true".equalsIgnoreCase((String) rm);
            }
        }

        if (email == null || email.isEmpty() || otp == null || otp.isEmpty()) {
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "Email and OTP are required.", null);
            return ResponseEntity.badRequest().body(response);
        }

        var userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "User not found.", null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        var user = userOpt.get();
        if (!"ACTIVE".equals(user.getStatus())) {
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "Account not active. Complete verification first.", null);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        boolean ok = userService.loginWithOtp(email, otp);
        if (!ok) {
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(), "Invalid OTP.", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String token = jwtUtil.generateToken(email, rememberMe);
        HashMap<String, Object> data = new HashMap<>();
        data.put("token", token);
        long responseExpiration = rememberMe ? 30L * 24 * 60 * 60 : jwtExpiration;
        data.put("expiresIn", responseExpiration);

        ApiResponse<Object> response = new ApiResponse<>(HttpStatus.OK.value(), "Login successful.", data);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/send-login-otp")
    public ResponseEntity<ApiResponse<Object>> sendLoginOtp(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        if (email == null || email.isEmpty()) {
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "Email is required.", null);
            return ResponseEntity.badRequest().body(response);
        }

        var userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "User not found. Please sign up.", null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        var user = userOpt.get();
        if (!"ACTIVE".equals(user.getStatus())) {
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "Account not active. Complete verification first.", null);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        try {
            String otp = userService.sendLoginOtp(email);
            Map<String, Object> data = null;
            String testingMode = System.getenv("OTP_TESTING_MODE");
            if ("true".equalsIgnoreCase(testingMode)) {
                data = new java.util.HashMap<>();
                data.put("otp", otp);
            }
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.OK.value(), "Login OTP sent to your email.", data);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("Failed to send login OTP to {}: {}", email, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to send email", null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // ==================== BOOTSTRAP ENDPOINT ====================
    
    /**
     * Bootstrap the first SUPER_ADMIN user (USE ONCE ONLY)
     * This endpoint is for initial system setup and should be removed after use
     */
    @PostMapping(value = "/bootstrap/super-admin", produces = "application/json")
    public ResponseEntity<ApiResponse<Object>> bootstrapSuperAdmin(@RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email");
            String name = payload.get("name");
            String secretKey = payload.get("secretKey");
            
            // Security check - require a secret key from environment
            String expectedSecret = System.getenv("BOOTSTRAP_SECRET");
            if (expectedSecret == null || !expectedSecret.equals(secretKey)) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.FORBIDDEN.value(), 
                    "Invalid bootstrap secret key", 
                    null
                );
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            if (email == null || email.isEmpty() || name == null || name.isEmpty()) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(), 
                    "Email and name are required", 
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            // Check if any SUPER_ADMIN users already exist
            List<User> existingSuperAdmins = userService.getUsersByRole(User.Role.SUPER_ADMIN);
            if (!existingSuperAdmins.isEmpty()) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.CONFLICT.value(), 
                    "SUPER_ADMIN users already exist. This endpoint can only be used once.", 
                    null
                );
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            
            // Create or update user as SUPER_ADMIN
            Optional<User> existingUser = userService.findByEmail(email);
            User user;
            
            if (existingUser.isPresent()) {
                user = existingUser.get();
                user.setName(name);
            } else {
                user = new User();
                user.setEmail(email);
                user.setName(name);
            }
            
            user.setRole(User.Role.SUPER_ADMIN);
            user.setApprovalStatus(ApprovalStatus.APPROVED);
            user.setStatus("ACTIVE");
            user.setVerified(true);
            user.setEmailVerified(true);
            user.setApprovedAt(java.time.LocalDateTime.now());
            user.setApprovedBy(0L); // Self-approved bootstrap
            // Set demo OTP for bootstrap login
            user.setOtp("123456");
            user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(10));

            userService.saveUser(user);

            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "SUPER_ADMIN user created successfully. You can now login with OTP 123456.", 
                null
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error bootstrapping SUPER_ADMIN: {}", e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to bootstrap SUPER_ADMIN user", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // ==================== ADMIN ENDPOINTS ====================
    
    /**
     * Get all pending users (Admin only)
     */
    @GetMapping("/admin/pending-users")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER') or hasRole('SUPER_ADMIN_REVIEWER')")
    public ResponseEntity<ApiResponse<List<User>>> getPendingUsers() {
        try {
            List<User> pendingUsers = userService.getPendingUsers();
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Pending users retrieved successfully", 
                pendingUsers
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving pending users: {}", e.getMessage(), e);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve pending users", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Approve a user (Admin only)
     */
    @PostMapping("/admin/approve-user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Object>> approveUser(@PathVariable Long userId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminEmail = auth.getName();
            User admin = userService.getUserByEmail(adminEmail);
            
            boolean success = userService.approveUser(userId, admin.getId());
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(), 
                    "User approved successfully", 
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(), 
                    "User not found", 
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("Error approving user {}: {}", userId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to approve user", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Reject a user (Admin only)
     */
    @PostMapping("/admin/reject-user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Object>> rejectUser(
            @PathVariable Long userId, 
            @RequestBody Map<String, String> payload) {
        try {
            String reason = payload.getOrDefault("reason", "No reason provided");
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminEmail = auth.getName();
            User admin = userService.getUserByEmail(adminEmail);
            
            boolean success = userService.rejectUser(userId, admin.getId(), reason);
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(), 
                    "User rejected successfully", 
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(), 
                    "User not found", 
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("Error rejecting user {}: {}", userId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to reject user", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Delete a user (Super-Admin only)
     */
    @DeleteMapping("/admin/delete-user/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Object>> deleteUser(@PathVariable Long userId) {
        try {
            boolean success = userService.deleteUser(userId);
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(), 
                    "User deleted successfully", 
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(), 
                    "User not found", 
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("Error deleting user {}: {}", userId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to delete user", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get users by approval status (Admin only)
     */
    @GetMapping("/admin/users/status/{status}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER') or hasRole('SUPER_ADMIN_REVIEWER')")
    public ResponseEntity<ApiResponse<List<User>>> getUsersByStatus(@PathVariable String status) {
        try {
            ApprovalStatus approvalStatus = ApprovalStatus.valueOf(status.toUpperCase());
            List<User> users = userService.getUsersByApprovalStatus(approvalStatus);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Users retrieved successfully", 
                users
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(), 
                "Invalid approval status. Valid values: PENDING, APPROVED, REJECTED", 
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error retrieving users by status {}: {}", status, e.getMessage(), e);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve users", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get all users (Admin only)
     */
    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER') or hasRole('SUPER_ADMIN_REVIEWER')")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        try {
            List<User> users = userService.getAllUsers();
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "All users retrieved successfully", 
                users
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving all users: {}", e.getMessage(), e);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve users", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get available partners for collaboration (Authenticated users only)
     * Returns all active, approved partners that can be added as collaborators
     */
    @GetMapping("/partners/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UserProfileDTO>>> getAvailablePartners() {
        try {
            List<UserProfileDTO> availablePartners = userService.getUsersByRole(User.Role.PARTNER).stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()) &&
                            ApprovalStatus.APPROVED.equals(p.getApprovalStatus()))
                .map(UserProfileDTO::new)
                .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Available partners retrieved successfully", availablePartners));
        } catch (Exception e) {
            logger.error("Error retrieving available partners: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to retrieve available partners", null));
        }
    }
    
    /**
     * Get current user profile
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileDTO>> getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            
            // Check if user is authenticated
            if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getName().equals("anonymousUser")) {
                ApiResponse<UserProfileDTO> response = new ApiResponse<>(
                    HttpStatus.UNAUTHORIZED.value(), 
                    "Authentication required", 
                    null
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            String email = auth.getName();
            User user = userService.getUserByEmail(email);
            
            UserProfileDTO userProfile = new UserProfileDTO(user);
            
            ApiResponse<UserProfileDTO> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "User profile retrieved successfully", 
                userProfile
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving user profile: {}", e.getMessage(), e);
            ApiResponse<UserProfileDTO> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve user profile", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Update current user's own profile (name only)
     */
    @RequestMapping(value = {"/profile", "/profile/update"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<ApiResponse<UserProfileDTO>> updateCurrentUser(@RequestBody java.util.Map<String, String> body) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(), "Authentication required", null));
            }
            User user = userService.getUserByEmail(auth.getName());
            String newName = body.get("name");
            if (newName != null && !newName.trim().isEmpty()) {
                user.setName(newName.trim());
            }
            if (body.containsKey("phone")) user.setPhone(body.get("phone"));
            if (body.containsKey("linkedinUrl")) user.setLinkedinUrl(body.get("linkedinUrl"));
            if (body.containsKey("websiteUrl")) user.setWebsiteUrl(body.get("websiteUrl"));
            if (body.containsKey("bio")) user.setBio(body.get("bio"));
            userService.saveUser(user);
            return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Profile updated successfully", new UserProfileDTO(user)));
        } catch (Exception e) {
            logger.error("Error updating user profile: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to update profile", null));
        }
    }

    // ========== SUPER-ADMIN ENDPOINTS ==========

    /**
     * Get users by approval status (Super-Admin only)
     */
    @GetMapping("/admin/users/by-status/{status}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<User>>> getUsersByApprovalStatus(@PathVariable String status) {
        try {
            ApprovalStatus approvalStatus = ApprovalStatus.valueOf(status.toUpperCase());
            List<User> users = userService.getUsersByApprovalStatus(approvalStatus);
            
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Users retrieved successfully", 
                users
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(), 
                "Invalid approval status: " + status, 
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error retrieving users by approval status: {}", e.getMessage(), e);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve users", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // ==================== TWO-TIER ADMIN MANAGEMENT ENDPOINTS ====================
    
    /**
     * Assign thematic area to a reviewer
     */
    @PostMapping("/admin/assign-thematic-area/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Object>> assignThematicArea(
            @PathVariable Long userId,
            @RequestBody Map<String, String> payload) {
        try {
            String thematicAreaCode = payload.get("thematicArea");
            if (thematicAreaCode == null || thematicAreaCode.isEmpty()) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Thematic area is required",
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            com.tujulishanehub.backend.models.ProjectTheme thematicArea = 
                com.tujulishanehub.backend.models.ProjectTheme.fromCode(thematicAreaCode);
            
            boolean success = userService.assignThematicArea(userId, thematicArea);
            
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "Thematic area assigned successfully",
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "User not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for assigning thematic area: {}", e.getMessage());
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error assigning thematic area to user {}: {}", userId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to assign thematic area",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Update user role with optional thematic area
     */
    @PostMapping("/admin/update-role-with-theme/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Object>> updateUserRoleWithThematicArea(
            @PathVariable Long userId,
            @RequestBody Map<String, String> payload) {
        try {
            String roleStr = payload.get("role");
            String thematicAreaCode = payload.get("thematicArea");
            
            if (roleStr == null || roleStr.isEmpty()) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Role is required",
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            User.Role newRole = User.Role.valueOf(roleStr);
            com.tujulishanehub.backend.models.ProjectTheme thematicArea = null;
            
            if (thematicAreaCode != null && !thematicAreaCode.isEmpty()) {
                thematicArea = com.tujulishanehub.backend.models.ProjectTheme.fromCode(thematicAreaCode);
            }
            
            boolean success = userService.updateUserRoleWithThematicArea(userId, newRole, thematicArea);
            
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "User role updated successfully",
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "User not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for updating user role: {}", e.getMessage());
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error updating user role {}: {}", userId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to update user role",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get all reviewers
     */
    @GetMapping("/admin/reviewers")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<List<User>>> getAllReviewers() {
        try {
            List<User> reviewers = userService.getAllReviewers();
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Reviewers retrieved successfully",
                reviewers
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving reviewers: {}", e.getMessage(), e);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve reviewers",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get reviewers by thematic area
     */
    @GetMapping("/admin/reviewers/by-theme/{themeCode}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<List<User>>> getReviewersByThematicArea(@PathVariable String themeCode) {
        try {
            com.tujulishanehub.backend.models.ProjectTheme thematicArea = 
                com.tujulishanehub.backend.models.ProjectTheme.fromCode(themeCode);
            
            List<User> reviewers = userService.getReviewersByThematicArea(thematicArea);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Reviewers for thematic area retrieved successfully",
                reviewers
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid thematic area code: {}", themeCode);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid thematic area code: " + themeCode,
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error retrieving reviewers by thematic area: {}", e.getMessage(), e);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve reviewers",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get all approvers
     */
    @GetMapping("/admin/approvers")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<List<User>>> getAllApprovers() {
        try {
            List<User> approvers = userService.getAllApprovers();
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Approvers retrieved successfully",
                approvers
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving approvers: {}", e.getMessage(), e);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve approvers",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Assign multiple thematic areas to a reviewer (NEW - supports many-to-many)
     */
    @PostMapping("/admin/assign-thematic-areas/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Object>> assignThematicAreas(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<String> thematicAreaCodes = (java.util.List<String>) payload.get("thematicAreas");
            
            if (thematicAreaCodes == null || thematicAreaCodes.isEmpty()) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "At least one thematic area is required",
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            // Convert codes to ProjectTheme enums
            java.util.List<com.tujulishanehub.backend.models.ProjectTheme> thematicAreas = 
                new java.util.ArrayList<>();
            for (String code : thematicAreaCodes) {
                thematicAreas.add(com.tujulishanehub.backend.models.ProjectTheme.fromCode(code));
            }
            
            // Get current user ID for audit trail
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminEmail = auth.getName();
            User admin = userService.getUserByEmail(adminEmail);
            
            boolean success = userService.assignThematicAreas(userId, thematicAreas, admin.getId());
            
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "Thematic areas assigned successfully",
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "User not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for assigning thematic areas: {}", e.getMessage());
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error assigning thematic areas to user {}: {}", userId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to assign thematic areas",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Add a single thematic area to a reviewer's existing assignments
     */
    @PostMapping("/admin/add-thematic-area/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Object>> addThematicArea(
            @PathVariable Long userId,
            @RequestBody Map<String, String> payload) {
        try {
            String thematicAreaCode = payload.get("thematicArea");
            if (thematicAreaCode == null || thematicAreaCode.isEmpty()) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Thematic area is required",
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            com.tujulishanehub.backend.models.ProjectTheme thematicArea = 
                com.tujulishanehub.backend.models.ProjectTheme.fromCode(thematicAreaCode);
            
            // Get current user ID for audit trail
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminEmail = auth.getName();
            User admin = userService.getUserByEmail(adminEmail);
            
            boolean success = userService.addThematicArea(userId, thematicArea, admin.getId());
            
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "Thematic area added successfully",
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "User not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for adding thematic area: {}", e.getMessage());
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error adding thematic area to user {}: {}", userId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to add thematic area",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Remove a thematic area from a reviewer
     */
    @DeleteMapping("/admin/remove-thematic-area/{userId}/{thematicAreaCode}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Object>> removeThematicArea(
            @PathVariable Long userId,
            @PathVariable String thematicAreaCode) {
        try {
            com.tujulishanehub.backend.models.ProjectTheme thematicArea = 
                com.tujulishanehub.backend.models.ProjectTheme.fromCode(thematicAreaCode);
            
            boolean success = userService.removeThematicArea(userId, thematicArea);
            
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "Thematic area removed successfully",
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Assignment not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid thematic area code: {}", thematicAreaCode);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid thematic area code",
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error removing thematic area from user {}: {}", userId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to remove thematic area",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // Donor-Partner Management Endpoints
    
    @GetMapping("/donors")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<List<User>>> getAllDonors() {
        try {
            List<User> donors = userService.getAllDonors();
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Donors retrieved successfully", 
                donors
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving donors: {}", e.getMessage(), e);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve donors", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @GetMapping("/donor/{donorId}/partners")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER') or hasRole('DONOR')")
    public ResponseEntity<ApiResponse<List<User>>> getPartnersByDonor(@PathVariable Long donorId) {
        try {
            // Check if donor can access this endpoint
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String currentUserEmail = authentication.getName();
            User currentUser = userService.getUserByEmail(currentUserEmail);
            
            // Super admins and approvers can see all, donors can only see their own partners
            if (currentUser.getRole() != User.Role.SUPER_ADMIN 
                && currentUser.getRole() != User.Role.SUPER_ADMIN_APPROVER 
                && !currentUser.getId().equals(donorId)) {
                ApiResponse<List<User>> response = new ApiResponse<>(
                    HttpStatus.FORBIDDEN.value(), 
                    "You can only view your own partners", 
                    null
                );
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            List<User> partners = userService.getPartnersByDonor(donorId);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Partners retrieved successfully", 
                partners
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving partners for donor {}: {}", donorId, e.getMessage(), e);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve partners", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @PostMapping("/partner/{partnerId}/link-donor/{donorId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER') or hasRole('DONOR')")
    public ResponseEntity<ApiResponse<String>> linkPartnerToDonor(
            @PathVariable Long partnerId, 
            @PathVariable Long donorId,
            @RequestBody(required = false) java.util.Map<String, String> payload) {
        try {
            // Check if donor can link this partner
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String currentUserEmail = authentication.getName();
            User currentUser = userService.getUserByEmail(currentUserEmail);
            
            // Super admins can link any partner to any donor
            // Donors can only link partners to themselves
            if (currentUser.getRole() != User.Role.SUPER_ADMIN && !currentUser.getId().equals(donorId)) {
                ApiResponse<String> response = new ApiResponse<>(
                    HttpStatus.FORBIDDEN.value(), 
                    "Access denied. You can only link partners to your own account.", 
                    null
                );
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            java.time.LocalDate endDate = null;
            String description = null;
            if (payload != null) {
                String endDateStr = payload.get("endDate");
                if (endDateStr != null && !endDateStr.trim().isEmpty()) {
                    endDate = java.time.LocalDate.parse(endDateStr);
                }
                description = payload.get("description");
            }
            
            boolean success = userService.linkPartnerToDonor(partnerId, donorId, endDate, description);
            if (success) {
                ApiResponse<String> response = new ApiResponse<>(
                    HttpStatus.OK.value(), 
                    "Partner linked to donor successfully", 
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<String> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(), 
                    "Failed to link partner to donor. Check IDs and roles.", 
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Error linking partner {} to donor {}: {}", partnerId, donorId, e.getMessage(), e);
            ApiResponse<String> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to link partner to donor", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @PostMapping("/partner/{partnerId}/unlink-donor")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER') or hasRole('DONOR')")
    public ResponseEntity<ApiResponse<String>> unlinkPartnerFromDonor(@PathVariable Long partnerId) {
        try {
            // Check if donor can unlink this partner
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String currentUserEmail = authentication.getName();
            User currentUser = userService.getUserByEmail(currentUserEmail);
            
            // For donors, check if they own this partner
            if (currentUser.getRole() == User.Role.DONOR) {
                User partner = userService.getUserById(partnerId);
                if (partner == null) {
                    ApiResponse<String> response = new ApiResponse<>(
                        HttpStatus.NOT_FOUND.value(), 
                        "Partner not found", 
                        null
                    );
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
                
                if (partner.getParentDonor() == null || !partner.getParentDonor().getId().equals(currentUser.getId())) {
                    ApiResponse<String> response = new ApiResponse<>(
                        HttpStatus.FORBIDDEN.value(), 
                        "Access denied. You can only unlink your own partners.", 
                        null
                    );
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
                }
            }
            
            boolean success = userService.unlinkPartnerFromDonor(partnerId);
            if (success) {
                ApiResponse<String> response = new ApiResponse<>(
                    HttpStatus.OK.value(), 
                    "Partner unlinked from donor successfully", 
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<String> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(), 
                    "Failed to unlink partner from donor", 
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Error unlinking partner {} from donor: {}", partnerId, e.getMessage(), e);
            ApiResponse<String> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to unlink partner from donor", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @GetMapping("/admin/partnerships-for-review")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_REVIEWER')")
    public ResponseEntity<ApiResponse<List<User>>> getPartnershipsForReview() {
        try {
            List<User> partners = userService.getPartnershipsByWorkflowStatus(
                Arrays.asList(ApprovalWorkflowStatus.PENDING_REVIEW, ApprovalWorkflowStatus.UNDER_REVIEW)
            );
            ApiResponse<List<User>> response = new ApiResponse<>(HttpStatus.OK.value(), "Partnerships for review retrieved successfully", partners);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving partnerships for review", e);
            ApiResponse<List<User>> response = new ApiResponse<>(500, "Error: " + e.getMessage(), null);
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/admin/partnerships-awaiting-final-approval")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<List<User>>> getPartnershipsAwaitingFinalApproval() {
        try {
            List<User> partners = userService.getPartnershipsByWorkflowStatus(
                Arrays.asList(ApprovalWorkflowStatus.PENDING_FINAL_APPROVAL)
            );
            ApiResponse<List<User>> response = new ApiResponse<>(HttpStatus.OK.value(), "Partnerships awaiting final approval retrieved successfully", partners);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving partnerships awaiting final approval", e);
            ApiResponse<List<User>> response = new ApiResponse<>(500, "Error: " + e.getMessage(), null);
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/admin/partnerships-approved")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SUPER_ADMIN_REVIEWER', 'SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<List<User>>> getApprovedPartnerships() {
        try {
            List<User> partners = userService.getPartnershipsByWorkflowStatus(
                Arrays.asList(ApprovalWorkflowStatus.APPROVED)
            );
            ApiResponse<List<User>> response = new ApiResponse<>(HttpStatus.OK.value(), "Approved partnerships retrieved successfully", partners);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving approved partnerships", e);
            ApiResponse<List<User>> response = new ApiResponse<>(500, "Error: " + e.getMessage(), null);
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/admin/partner/{partnerId}/recommend-partnership")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_REVIEWER')")
    public ResponseEntity<ApiResponse<String>> recommendPartnership(@PathVariable Long partnerId) {
        try {
            boolean success = userService.updatePartnershipStatus(
                partnerId, 
                ApprovalStatus.PENDING, 
                ApprovalWorkflowStatus.PENDING_FINAL_APPROVAL
            );
            if (success) {
                return ResponseEntity.ok(new ApiResponse<>(200, "Partnership recommended to approver successfully", null));
            }
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Failed to recommend partnership", null));
        } catch (Exception e) {
            logger.error("Error recommending partnership", e);
            return ResponseEntity.status(500).body(new ApiResponse<>(500, "Error: " + e.getMessage(), null));
        }
    }

    @PostMapping("/admin/partner/{partnerId}/approve-partnership")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<String>> approvePartnership(@PathVariable Long partnerId) {
        try {
            boolean success = userService.updatePartnershipStatus(
                partnerId, 
                ApprovalStatus.APPROVED, 
                ApprovalWorkflowStatus.APPROVED
            );
            if (success) {
                User partner = userService.getUserById(partnerId);
                if (partner != null && partner.getParentDonor() != null) {
                    User donor = partner.getParentDonor();
                    try {
                        String partnerMsg = String.format(
                            "Hello %s,\n\n" +
                            "We are pleased to inform you that your request to link with the following donor organisation has been APPROVED:\n\n" +
                            "Donor Organisation: %s\n\n" +
                            "You can now manage projects under this donor funding stream on the platform.\n\n" +
                            "Best regards,\n" +
                            "RMNCAH Coordination Hub Team",
                            partner.getName(),
                            donor.getOrganization() != null ? donor.getOrganization().getName() : donor.getName()
                        );
                        userService.getEmailService().sendEmail(partner.getEmail(), "RMNCAH Hub - Partnership Link APPROVED", partnerMsg);

                        String donorMsg = String.format(
                            "Hello %s,\n\n" +
                            "We are pleased to inform you that your partnership request with the following partner has been APPROVED:\n\n" +
                            "Partner Organisation: %s\n\n" +
                            "You can now track their reports and project metrics under your Donor Dashboard.\n\n" +
                            "Best regards,\n" +
                            "RMNCAH Coordination Hub Team",
                            donor.getName(),
                            partner.getName()
                        );
                        userService.getEmailService().sendEmail(donor.getEmail(), "RMNCAH Hub - Partnership Link APPROVED", donorMsg);
                    } catch (Exception ex) {
                        logger.error("Error sending approved partnership emails", ex);
                    }
                }
                return ResponseEntity.ok(new ApiResponse<>(200, "Partnership approved successfully", null));
            }
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Failed to approve partnership", null));
        } catch (Exception e) {
            logger.error("Error approving partnership", e);
            return ResponseEntity.status(500).body(new ApiResponse<>(500, "Error: " + e.getMessage(), null));
        }
    }

    @PostMapping("/admin/partner/{partnerId}/reject-partnership")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_APPROVER') or hasRole('SUPER_ADMIN_REVIEWER')")
    public ResponseEntity<ApiResponse<String>> rejectPartnership(@PathVariable Long partnerId) {
        try {
            User partner = userService.getUserById(partnerId);
            if (partner != null && partner.getParentDonor() != null) {
                User donor = partner.getParentDonor();
                try {
                    String partnerMsg = String.format(
                        "Hello %s,\n\n" +
                        "We regret to inform you that your request to link with the following donor organisation has been REJECTED:\n\n" +
                        "Donor Organisation: %s\n\n" +
                        "For details, please contact platform administrators.\n\n" +
                        "Best regards,\n" +
                        "RMNCAH Coordination Hub Team",
                        partner.getName(),
                        donor.getOrganization() != null ? donor.getOrganization().getName() : donor.getName()
                    );
                    userService.getEmailService().sendEmail(partner.getEmail(), "RMNCAH Hub - Partnership Link REJECTED", partnerMsg);
                } catch (Exception ex) {
                    logger.error("Error sending rejection partnership email", ex);
                }
            }
            boolean success = userService.unlinkPartnerFromDonor(partnerId);
            if (success) {
                return ResponseEntity.ok(new ApiResponse<>(200, "Partnership rejected and cleared successfully", null));
            }
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Failed to reject partnership", null));
        } catch (Exception e) {
            logger.error("Error rejecting partnership", e);
            return ResponseEntity.status(500).body(new ApiResponse<>(500, "Error: " + e.getMessage(), null));
        }
    }

    @GetMapping("/me/donor")
    @PreAuthorize("hasRole('PARTNER')")
    public ResponseEntity<ApiResponse<User>> getMyDonor() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String currentUserEmail = authentication.getName();
            User currentUser = userService.getUserByEmail(currentUserEmail);
            
            if (currentUser.getParentDonor() == null) {
                ApiResponse<User> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(), 
                    "You are not linked to any donor", 
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            User donor = currentUser.getParentDonor();
            ApiResponse<User> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Donor retrieved successfully", 
                donor
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving donor for current user: {}", e.getMessage(), e);
            ApiResponse<User> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve donor", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get all supporting documents for a user
     * GET /api/auth/users/{userId}/supporting-documents
     */
    @GetMapping("/users/{userId}/supporting-documents")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SUPER_ADMIN_APPROVER', 'SUPER_ADMIN_REVIEWER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUserSupportingDocuments(@PathVariable Long userId) {
        try {
            logger.info("Fetching supporting documents for user ID: {}", userId);
            
            List<UserDocument> documents = userDocumentRepository.findByUserId(userId);
            
            List<Map<String, Object>> documentMetadata = documents.stream()
                .map(doc -> {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("id", doc.getId());
                    metadata.put("fileName", doc.getFileName());
                    metadata.put("fileType", doc.getFileType());
                    metadata.put("fileSize", doc.getFileSize() != null ? doc.getFileSize() : 0);
                    metadata.put("uploadedAt", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null);
                    metadata.put("documentType", doc.getFileName()); // Using fileName as documentType for now
                    return metadata;
                })
                .collect(Collectors.toList());

            logger.info("Found {} supporting documents for user ID: {}", documentMetadata.size(), userId);

            ApiResponse<List<Map<String, Object>>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Supporting documents retrieved successfully",
                documentMetadata
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error retrieving supporting documents for user {}: {}", userId, e.getMessage(), e);
            ApiResponse<List<Map<String, Object>>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve supporting documents: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Download a specific supporting document
     * GET /api/auth/users/{userId}/supporting-documents/{documentId}/download
     */
    @GetMapping("/users/{userId}/supporting-documents/{documentId}/download")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SUPER_ADMIN_APPROVER', 'SUPER_ADMIN_REVIEWER')")
    public ResponseEntity<?> downloadUserDocument(@PathVariable Long userId, @PathVariable Long documentId) {
        try {
            logger.info("Downloading document ID: {} for user ID: {}", documentId, userId);
            
            Optional<UserDocument> documentOpt = userDocumentRepository.findById(documentId);
            
            if (documentOpt.isEmpty()) {
                logger.warn("Document not found with ID: {}", documentId);
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Document not found with ID: " + documentId,
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            UserDocument document = documentOpt.get();
            
            // Verify the document belongs to the specified user
            if (!document.getUser().getId().equals(userId)) {
                logger.warn("Document {} does not belong to user {}", documentId, userId);
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Document does not belong to the specified user",
                    null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            logger.info("Returning document: {} ({})", document.getFileName(), document.getFileType());

            // Return the file with appropriate headers for download
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getFileType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + document.getFileName() + "\"")
                .body(document.getFileData());

        } catch (Exception e) {
            logger.error("Error downloading document {} for user {}: {}", documentId, userId, e.getMessage(), e);
            ApiResponse<Void> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to download document: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * View a specific supporting document (inline viewing)
     * GET /api/auth/users/{userId}/supporting-documents/{documentId}/view
     */
    @GetMapping("/users/{userId}/supporting-documents/{documentId}/view")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SUPER_ADMIN_APPROVER', 'SUPER_ADMIN_REVIEWER')")
    public ResponseEntity<?> viewUserDocument(@PathVariable Long userId, @PathVariable Long documentId) {
        try {
            logger.info("Viewing document ID: {} for user ID: {}", documentId, userId);
            
            Optional<UserDocument> documentOpt = userDocumentRepository.findById(documentId);
            
            if (documentOpt.isEmpty()) {
                logger.warn("Document not found with ID: {}", documentId);
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Document not found with ID: " + documentId,
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            UserDocument document = documentOpt.get();
            
            // Verify the document belongs to the specified user
            if (!document.getUser().getId().equals(userId)) {
                logger.warn("Document {} does not belong to user {}", documentId, userId);
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Document does not belong to the specified user",
                    null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            logger.info("Returning document for inline view: {} ({})", document.getFileName(), document.getFileType());

            // Return the file with appropriate headers for inline viewing
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getFileType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, 
                    "inline; filename=\"" + document.getFileName() + "\"")
                .body(document.getFileData());

        } catch (Exception e) {
            logger.error("Error viewing document {} for user {}: {}", documentId, userId, e.getMessage(), e);
            ApiResponse<Void> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to view document: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get user counts summary
     */
    @GetMapping("/counts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPER_ADMIN_REVIEWER', 'SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserCounts() {
        try {
            Map<String, Object> counts = new HashMap<>();
            
            // Get total users count
            long totalUsers = userService.getAllUsersCount();
            counts.put("total", totalUsers);
            
            // Count by role
            long partnersCount = userService.getUsersByRole(User.Role.PARTNER).size();
            long donorsCount = userService.getUsersByRole(User.Role.DONOR).size();
            long superAdminsCount = userService.getUsersByRole(User.Role.SUPER_ADMIN).size();
            long superAdminApproversCount = userService.getUsersByRole(User.Role.SUPER_ADMIN_APPROVER).size();
            long superAdminReviewersCount = userService.getUsersByRole(User.Role.SUPER_ADMIN_REVIEWER).size();
            
            counts.put("partners", partnersCount);
            counts.put("donors", donorsCount);
            counts.put("admins", superAdminsCount + superAdminApproversCount + superAdminReviewersCount);
            counts.put("superAdmins", superAdminsCount);
            
            // Count by approval status
            long pendingCount = userService.getUsersByApprovalStatus(ApprovalStatus.PENDING).size();
            long approvedCount = userService.getUsersByApprovalStatus(ApprovalStatus.APPROVED).size();
            long rejectedCount = userService.getUsersByApprovalStatus(ApprovalStatus.REJECTED).size();
            
            counts.put("pending", pendingCount);
            counts.put("approved", approvedCount);
            counts.put("rejected", rejectedCount);
            counts.put("active", approvedCount); // Active = Approved
            
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "User counts retrieved successfully",
                counts
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving user counts: {}", e.getMessage(), e);
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve user counts: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Search users by name or email (for reviewer conversion)
     */
    @GetMapping("/admin/users/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<List<User>>> searchUsers(@RequestParam String query) {
        try {
            if (query == null || query.trim().length() < 2) {
                ApiResponse<List<User>> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Search query must be at least 2 characters",
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            List<User> users = userService.searchUsersByNameOrEmail(query.trim());
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Users found",
                users
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error searching users: {}", e.getMessage(), e);
            ApiResponse<List<User>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to search users",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Convert existing user to reviewer role with thematic areas
     */
    @PostMapping("/admin/convert-to-reviewer/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<User>> convertToReviewer(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            List<String> thematicAreaCodes = (List<String>) payload.get("thematicAreas");
            
            if (thematicAreaCodes == null || thematicAreaCodes.isEmpty()) {
                ApiResponse<User> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "At least one thematic area is required",
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            // Get current user for audit trail
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String currentUserEmail = authentication.getName();
            User currentUser = userService.getUserByEmail(currentUserEmail);
            
            // Convert user to reviewer
            User updatedUser = userService.convertToReviewer(userId, thematicAreaCodes, currentUser.getId());
            
            if (updatedUser != null) {
                ApiResponse<User> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "User successfully converted to reviewer",
                    updatedUser
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<User> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "User not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("Error converting user to reviewer: {}", e.getMessage(), e);
            ApiResponse<User> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to convert user to reviewer: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Create new reviewer account and send invitation
     */
    @PostMapping("/admin/create-reviewer")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<User>> createReviewer(@RequestBody Map<String, Object> payload) {
        try {
            String name = (String) payload.get("name");
            String email = (String) payload.get("email");
            @SuppressWarnings("unchecked")
            List<String> thematicAreaCodes = (List<String>) payload.get("thematicAreas");
            
            if (name == null || name.trim().isEmpty()) {
                ApiResponse<User> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Name is required",
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            if (email == null || email.trim().isEmpty()) {
                ApiResponse<User> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Email is required",
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            // Validate email format
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                ApiResponse<User> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Invalid email format",
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            if (thematicAreaCodes == null || thematicAreaCodes.isEmpty()) {
                ApiResponse<User> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "At least one thematic area is required",
                    null
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            // Check if user already exists
            if (userService.findByEmail(email).isPresent()) {
                ApiResponse<User> response = new ApiResponse<>(
                    HttpStatus.CONFLICT.value(),
                    "A user with this email already exists",
                    null
                );
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            
            // Get current user for audit trail
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String currentUserEmail = authentication.getName();
            User currentUser = userService.getUserByEmail(currentUserEmail);
            
            // Create reviewer account
            User newReviewer = userService.createReviewer(name, email, thematicAreaCodes, currentUser.getId());
            
            ApiResponse<User> response = new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "Reviewer created successfully. Invitation email sent.",
                newReviewer
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            logger.error("Error creating reviewer: {}", e.getMessage(), e);
            ApiResponse<User> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to create reviewer: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
