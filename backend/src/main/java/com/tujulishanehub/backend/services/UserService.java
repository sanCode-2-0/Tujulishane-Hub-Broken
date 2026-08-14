package com.tujulishanehub.backend.services;

import com.tujulishanehub.backend.models.ApprovalStatus;
import com.tujulishanehub.backend.models.Organization;
import com.tujulishanehub.backend.models.User;
import com.tujulishanehub.backend.models.UserDocument;
import com.tujulishanehub.backend.models.Announcement;
import com.tujulishanehub.backend.models.CollaborationRequest;
import com.tujulishanehub.backend.models.ProjectCollaborator;
import com.tujulishanehub.backend.models.ReviewerThematicArea;
import com.tujulishanehub.backend.repositories.UserRepository;
import com.tujulishanehub.backend.repositories.UserDocumentRepository;
import com.tujulishanehub.backend.repositories.AnnouncementRepository;
import com.tujulishanehub.backend.repositories.CollaborationRequestRepository;
import com.tujulishanehub.backend.repositories.ProjectCollaboratorRepository;
import com.tujulishanehub.backend.repositories.ReviewerThematicAreaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.Random;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDocumentRepository userDocumentRepository;

    @Autowired
    private EmailService emailService;
    
    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private CollaborationRequestRepository collaborationRequestRepository;

    @Autowired
    private ProjectCollaboratorRepository projectCollaboratorRepository;
    
    @Autowired(required = false)
    private ReviewerThematicAreaRepository reviewerThematicAreaRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    // Register user (name + email + optional organization). Create INACTIVE account and send OTP
    public User registerUser(String name, String email, Long organizationId) {
        return registerUser(name, email, organizationId, User.Role.PARTNER, null);
    }
    
    // Register donor account
    public User registerDonor(String name, String email, Long organizationId) {
        return registerUser(name, email, organizationId, User.Role.DONOR, null);
    }
    
    // Register partner account with optional parent donor
    public User registerPartner(String name, String email, Long organizationId, Long parentDonorId) {
        return registerUser(name, email, organizationId, User.Role.PARTNER, parentDonorId);
    }
    
    // Main registration method with role and parent donor support
    private User registerUser(String name, String email, Long organizationId, User.Role role, Long parentDonorId) {
        // Check if user already exists
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            throw new RuntimeException("User with this email already exists");
        }
        
        // Validate parent donor if provided
        if (parentDonorId != null) {
            Optional<User> parentDonor = userRepository.findById(parentDonorId);
            if (!parentDonor.isPresent() || parentDonor.get().getRole() != User.Role.DONOR) {
                throw new RuntimeException("Invalid parent donor ID");
            }
        }
        
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setStatus("ACTIVE");  // Set to ACTIVE for new registrations
        user.setApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus.PENDING);
        user.setRole(role);
        
        // Set parent donor for partners
        if (role == User.Role.PARTNER && parentDonorId != null) {
            User parentDonor = userRepository.findById(parentDonorId).orElse(null);
            user.setParentDonor(parentDonor);
        }
        
        String otp = generateOtp();
        user.setOtp(otp);
        user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(10));
        if (organizationId != null) {
            organizationService.getOrganizationById(organizationId)
                .ifPresent(user::setOrganization);
        }
        User savedUser = userRepository.save(user);
        
        return savedUser;
    }
    
    // Backward compatibility method
    public User registerUser(String name, String email) {
        return registerUser(name, email, null);
    }

    // Verify OTP and mark email as verified (but user still needs admin approval)
    public boolean verifyOtp(String email, String otp) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return false;
        
        User user = userOpt.get();
        
        // Check if OTP matches and hasn't expired
        if (otp != null && otp.equals(user.getOtp()) && 
            user.getOtpExpiry() != null && 
            user.getOtpExpiry().isAfter(java.time.LocalDateTime.now())) {
            
            // Mark email as verified but keep account INACTIVE until admin approves
            user.setEmailVerified(true);
            user.setVerified(true);
            user.setOtp(null);
            user.setOtpExpiry(null);
            userRepository.save(user);
            return true;
        }
        return false;
    }

    // Login using email + otp. User must be ACTIVE and APPROVED.
    public boolean loginWithOtp(String email, String otp) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return false;
        
        User user = userOpt.get();
        
        // User must be ACTIVE and APPROVED
        if (!"ACTIVE".equals(user.getStatus()) || !user.isApproved()) {
            return false;
        }
        
        // Verify OTP matches and hasn't expired
        if (otp != null && otp.equals(user.getOtp()) && 
            user.getOtpExpiry() != null && 
            user.getOtpExpiry().isAfter(java.time.LocalDateTime.now())) {
            
            user.setLastLogin(java.time.LocalDateTime.now());
            user.setOtp(null);
            user.setOtpExpiry(null);
            userRepository.save(user);
            return true;
        }
        return false;
    }

    private String generateOtp() {
        // Generate a random 6-digit OTP
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User getUserByEmail(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        // Organization is now eagerly loaded due to FetchType.EAGER
        // But we can explicitly access it to ensure it's loaded
        if (user != null && user.getOrganization() != null) {
            // This will trigger loading if for some reason it wasn't loaded
            user.getOrganization().getName();
        }
        return user;
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    // Helper to (re)send login OTP for ACTIVE and APPROVED users
    public String sendLoginOtp(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        
        User user = userOpt.get();
        
        // User must be ACTIVE and APPROVED to receive login OTP
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Account not active");
        }
        
        if (!user.isApproved()) {
            throw new RuntimeException("Account not approved by administrator");
        }
        
        // Generate and save new OTP
        String otp = generateOtp();
        user.setOtp(otp);
        user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);
        
        // Send OTP via email
        String subject = "RMNCAH Hub - Your Login OTP";
        String body = String.format(
            "Hello %s,\n\n" +
            "You requested to log in to the RMNCAH Coordination Hub.\n" +
            "Your One-Time Password (OTP) is: %s\n\n" +
            "This OTP will expire in 10 minutes. For your security, please do not share this code with anyone.\n\n" +
            "If you did not request this login, you can safely ignore this email.\n\n" +
            "Best regards,\n" +
            "RMNCAH Coordination Hub Team",
            user.getName(), otp
        );
        emailService.sendEmail(email, subject, body);
        return otp;
    }

    // ========== ADMIN METHODS ==========

    /**
     * Get all users (Super-Admin only)
     */
    public java.util.List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Get total users count
     */
    public long getAllUsersCount() {
        return userRepository.count();
    }

    /**
     * Get users by approval status
     */
    public java.util.List<User> getUsersByApprovalStatus(ApprovalStatus approvalStatus) {
        return userRepository.findByApprovalStatus(approvalStatus);
    }

    /**
     * Get pending users
     */
    public java.util.List<User> getPendingUsers() {
        return userRepository.findByApprovalStatus(ApprovalStatus.PENDING);
    }

    /**
     * Approve user (Super-Admin only)
     */
    public boolean approveUser(Long userId, Long approvedBy) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setApprovalStatus(ApprovalStatus.APPROVED);
            user.setApprovedBy(approvedBy);
            user.setApprovedAt(java.time.LocalDateTime.now());
            user.setRejectionReason(null);
            user.setStatus("ACTIVE"); // Activate user when approved
            user.setEmailVerified(true); // Email is considered verified when manually approved by admin
            
            userRepository.save(user);
            
            // Send detailed onboarding email
            String subject = "Welcome to the RMNCAH Coordination Hub - Your Account is Approved";
            String body = String.format(
                "Hello %s,\n\n" +
                "Thank you for registering with the RMNCAH Coordination Hub.\n" +
                "We’re pleased to let you know that your account has been reviewed and approved by the Ministry of Health. You can now log in and start using the platform.\n\n" +
                "With the RMNCAH Coordination Hub, you can:\n" +
                "• Submit Projects – Create and submit health-related projects for review.\n" +
                "• Collaborate – Connect and work with partner organisations on projects.\n" +
                "• Stay Informed – Keep up to date with Ministry of Health announcements and collaboration opportunities.\n" +
                "• Track Projects – Monitor the status and progress of your submitted projects.\n\n" +
                "Getting Started:\n" +
                "1. Log in using your registered email address.\n" +
                "2. Complete your profile with your organisation details and contact information.\n" +
                "3. Explore active projects and collaboration opportunities.\n" +
                "4. Submit your project when you are ready.\n\n" +
                "Access the RMNCAH Coordination Hub: https://cohub.go.ke\n\n" +
                "If you have any questions or need assistance, please contact your designated Ministry of Health focal person or the platform support team.\n\n" +
                "We look forward to having you on the platform.\n\n" +
                "Best regards,\n" +
                "RMNCAH Coordination Hub Team",
                user.getName()
            );
            emailService.sendEmail(user.getEmail(), subject, body);

            return true;
        }
        return false;
    }

    /**
     * Reject user (Super-Admin only)
     */
    public boolean rejectUser(Long userId, Long rejectedBy, String reason) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setApprovalStatus(ApprovalStatus.REJECTED);
            user.setApprovedBy(rejectedBy);
            user.setRejectionReason(reason);
            user.setStatus("INACTIVE"); // Deactivate rejected user
            
            userRepository.save(user);
            
            // Send notification to user
            String subject = "RMNCAH Hub - Update on Your Account Application";
            String body = String.format(
                "Hello %s,\n\n" +
                "Thank you for your interest in joining the RMNCAH Coordination Hub.\n" +
                "After reviewing your application, we’re unable to approve your account at this time because it did not meet the required activation criteria.\n\n" +
                "Reason for Rejection: %s\n\n" +
                "If you believe this decision was made in error or would like further clarification, please contact the platform support team.\n\n" +
                "We appreciate your interest in the RMNCAH Coordination Hub and encourage you to address the issue outlined above where applicable.\n\n" +
                "Best regards,\n" +
                "RMNCAH Coordination Hub Team",
                user.getName(), reason
            );
            emailService.sendEmail(user.getEmail(), subject, body);
            
            return true;
        }
        return false;
    }

    /**
     * Update user role (Super-Admin only)
     */
    public boolean updateUserRole(Long userId, User.Role newRole) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            User.Role oldRole = user.getRole();
            user.setRole(newRole);
            userRepository.save(user);
            
            // Send notification to user
            String subject = "RMNCAH Hub - Your Platform Role Has Been Updated";
            String body = String.format(
                "Hello %s,\n\n" +
                "We’d like to let you know that your role on the RMNCAH Coordination Hub has been updated.\n\n" +
                "Previous Role: %s\n" +
                "New Role: %s\n\n" +
                "Your new role is now active, and your access to the platform has been updated accordingly.\n\n" +
                "If you have any questions about this change, please contact the platform support team.\n\n" +
                "Best regards,\n" +
                "RMNCAH Coordination Hub Team",
                user.getName(), oldRole, newRole
            );
            emailService.sendEmail(user.getEmail(), subject, body);
            
            return true;
        }
        return false;
    }

    /**
     * Check if user can create projects
     */
    public boolean canUserCreateProjects(String email) {
        User user = getUserByEmail(email);
        return user != null && user.canCreateProjects();
    }
    
    /**
     * Get users by role
     */
    public java.util.List<User> getUsersByRole(User.Role role) {
        return userRepository.findByRole(role);
    }

    // Get all partner users linked to a specific parent donor
    public java.util.List<User> getUsersByParentDonorId(Long parentDonorId) {
        return userRepository.findByParentDonorId(parentDonorId);
    }
    
    /**
     * Save user (for direct database operations)
     */
    public User saveUser(User user) {
        return userRepository.save(user);
    }
    
    /**
     * Get all partners linked to a specific donor
     */
    public java.util.List<User> getPartnersByDonor(Long donorId) {
        return userRepository.findByParentDonorId(donorId);
    }
    
    /**
     * Get all partners linked to a specific donor email
     */
    public java.util.List<User> getPartnersByDonorEmail(String donorEmail) {
        Optional<User> donor = userRepository.findByEmail(donorEmail);
        if (donor.isPresent() && donor.get().getRole() == User.Role.DONOR) {
            return getPartnersByDonor(donor.get().getId());
        }
        return java.util.Collections.emptyList();
    }
    
    // ==================== TWO-TIER ADMIN MANAGEMENT ====================
    
    /**
     * Assign multiple thematic areas to a reviewer (NEW - supports many-to-many)
     */
    @Transactional
    public boolean assignThematicAreas(Long userId, java.util.List<com.tujulishanehub.backend.models.ProjectTheme> thematicAreas, Long assignedById) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            
            // Validate user is a reviewer
            if (user.getRole() != User.Role.SUPER_ADMIN_REVIEWER) {
                throw new IllegalArgumentException("User must be a SUPER_ADMIN_REVIEWER to have thematic areas assigned");
            }
            
            // Check if repository is available (table exists)
            if (reviewerThematicAreaRepository == null) {
                throw new IllegalStateException("Reviewer thematic area feature not available. Please run database migration first.");
            }
            
            // Clear existing assignments
            reviewerThematicAreaRepository.deleteByUserId(userId);
            user.getThematicAreaAssignments().clear();
            
            // Flush to ensure deletions are committed before inserts
            entityManager.flush();
            
            // Add new assignments
            for (com.tujulishanehub.backend.models.ProjectTheme thematicArea : thematicAreas) {
                ReviewerThematicArea assignment = new ReviewerThematicArea(user, thematicArea, assignedById);
                user.addThematicArea(assignment);
                reviewerThematicAreaRepository.save(assignment);
            }
            
            userRepository.save(user);
            
            // Send notification to user
            String areasString = thematicAreas.stream()
                .map(com.tujulishanehub.backend.models.ProjectTheme::getDisplayName)
                .collect(java.util.stream.Collectors.joining(", "));
            
            // Send notification to user
            String subject = "RMNCAH Hub - New Thematic Areas Assigned";
            String body = String.format(
                "Hello %s,\n\n" +
                "You have been assigned to review projects under the following thematic areas:\n" +
                "%s\n\n" +
                "You can now access and review projects submitted under these thematic areas through the Review Requests page.\n\n" +
                "Thank you for supporting the project review process.\n\n" +
                "Best regards,\n" +
                "RMNCAH Coordination Hub Team",
                user.getName(), areasString
            );
            emailService.sendEmail(user.getEmail(), subject, body);
            
            return true;
        }
        return false;
    }
    
    /**
     * Assign thematic area to a reviewer (LEGACY - backward compatible)
     * @deprecated Use assignThematicAreas for multiple area support
     */
    @Deprecated
    public boolean assignThematicArea(Long userId, com.tujulishanehub.backend.models.ProjectTheme thematicArea) {
        // Convert single area to list and use new method
        return assignThematicAreas(userId, java.util.Arrays.asList(thematicArea), null);
    }
    
    /**
     * Add a single thematic area to a reviewer's existing assignments
     */
    @Transactional
    public boolean addThematicArea(Long userId, com.tujulishanehub.backend.models.ProjectTheme thematicArea, Long assignedById) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            
            // Validate user is a reviewer
            if (user.getRole() != User.Role.SUPER_ADMIN_REVIEWER) {
                throw new IllegalArgumentException("User must be a SUPER_ADMIN_REVIEWER to have thematic areas assigned");
            }
            
            // Check if repository is available (table exists)
            if (reviewerThematicAreaRepository == null) {
                throw new IllegalStateException("Reviewer thematic area feature not available. Please run database migration first.");
            }
            
            // Check if already assigned
            if (reviewerThematicAreaRepository.existsByUserIdAndThematicArea(userId, thematicArea)) {
                throw new IllegalArgumentException("Reviewer is already assigned to this thematic area");
            }
            
            // Add new assignment
            ReviewerThematicArea assignment = new ReviewerThematicArea(user, thematicArea, assignedById);
            user.addThematicArea(assignment);
            reviewerThematicAreaRepository.save(assignment);
            userRepository.save(user);
            
            // Send notification to user
            // Send notification to user
            String subject = "RMNCAH Hub - New Thematic Area Assigned";
            String body = String.format(
                "Hello %s,\n\n" +
                "A new thematic area has been assigned to you for project review:\n" +
                "%s\n\n" +
                "You can now review projects submitted under this thematic area through the Review Requests page.\n\n" +
                "Thank you for your continued support.\n\n" +
                "Best regards,\n" +
                "RMNCAH Coordination Hub Team",
                user.getName(), thematicArea.getDisplayName()
            );
            emailService.sendEmail(user.getEmail(), subject, body);
            
            return true;
        }
        return false;
    }
    
    /**
     * Remove a thematic area from a reviewer
     */
    @Transactional
    public boolean removeThematicArea(Long userId, com.tujulishanehub.backend.models.ProjectTheme thematicArea) {
        // Check if repository is available (table exists)
        if (reviewerThematicAreaRepository == null) {
            throw new IllegalStateException("Reviewer thematic area feature not available. Please run database migration first.");
        }
        
        Optional<ReviewerThematicArea> assignmentOpt = reviewerThematicAreaRepository
            .findByUserIdAndThematicArea(userId, thematicArea);
        
        if (assignmentOpt.isPresent()) {
            ReviewerThematicArea assignment = assignmentOpt.get();
            User user = assignment.getUser();
            
            user.removeThematicArea(assignment);
            reviewerThematicAreaRepository.delete(assignment);
            userRepository.save(user);
            
            // Send notification to user
            String subject = "RMNCAH Hub - Thematic Area Assignment Updated";
            String body = String.format(
                "Hello %s,\n\n" +
                "We’d like to let you know that your Reviewer assignment has been updated.\n\n" +
                "You will no longer be assigned to review projects under the following thematic area:\n" +
                "%s\n\n" +
                "Your other Reviewer assignments, if applicable, remain unchanged.\n\n" +
                "If you have any questions or believe this update was made in error, please contact the platform support team.\n\n" +
                "Thank you for your continued support of the RMNCAH Coordination Hub.\n\n" +
                "Best regards,\n" +
                "RMNCAH Coordination Hub Team",
                user.getName(), thematicArea.getDisplayName()
            );
            emailService.sendEmail(user.getEmail(), subject, body);
            
            return true;
        }
        return false;
    }
    
    /**
     * Update user role with thematic area (for creating reviewers)
     */
    public boolean updateUserRoleWithThematicArea(Long userId, User.Role newRole, com.tujulishanehub.backend.models.ProjectTheme thematicArea) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            User.Role oldRole = user.getRole();
            user.setRole(newRole);
            
            // If assigning as reviewer, set thematic area
            if (newRole == User.Role.SUPER_ADMIN_REVIEWER && thematicArea != null) {
                user.setThematicArea(thematicArea);
            }
            
            // If changing from reviewer to another role, clear thematic area
            if (oldRole == User.Role.SUPER_ADMIN_REVIEWER && newRole != User.Role.SUPER_ADMIN_REVIEWER) {
                user.setThematicArea(null);
            }
            
            userRepository.save(user);
            
            // Send notification to user
            String subject;
            String body;
            if (newRole == User.Role.SUPER_ADMIN_REVIEWER) {
                String thematicAreaName = thematicArea != null ? thematicArea.getDisplayName() : "N/A";
                subject = "RMNCAH Hub - You Have Been Assigned as a Reviewer";
                body = String.format(
                    "Hello %s,\n\n" +
                    "We’d like to let you know that your role on the RMNCAH Coordination Hub has been updated from %s to Reviewer.\n\n" +
                    "You have been assigned to review projects in the following thematic areas:\n" +
                    "%s\n\n" +
                    "You can now access the Review Requests page to review projects awaiting your attention.\n\n" +
                    "Thank you for supporting the RMNCAH Coordination Hub review process.\n\n" +
                    "Best regards,\n" +
                    "RMNCAH Coordination Hub Team",
                    user.getName(), oldRole, thematicAreaName
                );
            } else {
                subject = "RMNCAH Hub - Your Platform Role Has Been Updated";
                body = String.format(
                    "Hello %s,\n\n" +
                    "We’d like to let you know that your role on the RMNCAH Coordination Hub has been updated.\n\n" +
                    "Previous Role: %s\n" +
                    "New Role: %s\n\n" +
                    "Your new role is now active, and your access to the platform has been updated accordingly.\n\n" +
                    "If you have any questions about this change, please contact the platform support team.\n\n" +
                    "Best regards,\n" +
                    "RMNCAH Coordination Hub Team",
                    user.getName(), oldRole, newRole
                );
            }
            
            emailService.sendEmail(user.getEmail(), subject, body);
            
            return true;
        }
        return false;
    }
    
    /**
     * Get all reviewers by thematic area (supports many-to-many)
     */
    public java.util.List<User> getReviewersByThematicArea(com.tujulishanehub.backend.models.ProjectTheme thematicArea) {
        java.util.List<User> reviewers = new java.util.ArrayList<>();
        
        // Use new many-to-many relationship if available
        if (reviewerThematicAreaRepository != null) {
            reviewers.addAll(reviewerThematicAreaRepository.findReviewersByThematicArea(thematicArea));
        }
        
        // Make effectively final for lambda usage
        final java.util.List<User> existingReviewers = reviewers;
        
        // Also include reviewers with legacy single thematic area assignment (backward compatibility)
        java.util.List<User> legacyReviewers = userRepository.findByRole(User.Role.SUPER_ADMIN_REVIEWER).stream()
            .filter(user -> user.getThematicArea() == thematicArea)
            .filter(user -> !existingReviewers.contains(user)) // Avoid duplicates
            .collect(java.util.stream.Collectors.toList());
        
        reviewers.addAll(legacyReviewers);
        return reviewers;
    }
    
    /**
     * Get all reviewers
     */
    public java.util.List<User> getAllReviewers() {
        return userRepository.findByRole(User.Role.SUPER_ADMIN_REVIEWER);
    }
    
    /**
     * Get all approvers
     */
    public java.util.List<User> getAllApprovers() {
        java.util.List<User> approvers = new java.util.ArrayList<>();
        approvers.addAll(userRepository.findByRole(User.Role.SUPER_ADMIN_APPROVER));
        approvers.addAll(userRepository.findByRole(User.Role.SUPER_ADMIN)); // Include legacy SUPER_ADMIN
        return approvers;
    }
    
    /**
     * Link a partner to a donor
     */
    public boolean linkPartnerToDonor(Long partnerId, Long donorId) {
        Optional<User> partnerOptional = userRepository.findById(partnerId);
        Optional<User> donorOptional = userRepository.findById(donorId);
        
        if (partnerOptional.isPresent() && donorOptional.isPresent()) {
            User partner = partnerOptional.get();
            User donor = donorOptional.get();
            
            if (partner.getRole() == User.Role.PARTNER && donor.getRole() == User.Role.DONOR) {
                partner.setParentDonor(donor);
                userRepository.save(partner);
                
                // Send email notifications
                try {
                    // Notify the partner
                    String partnerMessage = String.format(
                        "Hello %s,\n\n" +
                        "We’re pleased to let you know that your organisation has been successfully linked to the following donor organisation:\n\n" +
                        "Donor Organisation: %s\n" +
                        "Donor Contact: %s\n" +
                        "Email: %s\n\n" +
                        "This partnership will support collaboration and improve coordination of projects through the RMNCAH Coordination Hub.\n\n" +
                        "You can view the partnership details through the Donor Management section of the platform.\n\n" +
                        "If you have any questions about this partnership, please contact the platform support team.\n\n" +
                        "Best regards,\n" +
                        "RMNCAH Coordination Hub Team",
                        partner.getName(),
                        donor.getOrganization() != null ? donor.getOrganization().getName() : "N/A",
                        donor.getName(),
                        donor.getEmail()
                    );
                    
                    emailService.sendEmail(
                        partner.getEmail(),
                        "RMNCAH Hub - Partnership Successfully Linked",
                        partnerMessage
                    );
                    
                    // Notify the donor
                    String donorMessage = String.format(
                        "Hello %s,\n\n" +
                        "We’re pleased to let you know that a new partner organisation has been successfully linked to your account.\n\n" +
                        "Partner Organisation: %s\n" +
                        "Partner Contact: %s\n" +
                        "Email: %s\n" +
                        "Thematic Area: %s\n\n" +
                        "You can now view and manage this partnership through the Donor Management section of the RMNCAH Coordination Hub.\n\n" +
                        "We look forward to supporting continued collaboration through the platform.\n\n" +
                        "Best regards,\n" +
                        "RMNCAH Coordination Hub Team",
                        donor.getName(),
                        partner.getOrganization() != null ? partner.getOrganization().getName() : "N/A",
                        partner.getName(),
                        partner.getEmail(),
                        partner.getThematicArea() != null ? partner.getThematicArea().getDisplayName() : "N/A"
                    );
                    
                    emailService.sendEmail(
                        donor.getEmail(),
                        "RMNCAH Hub - New Partner Organisation Linked",
                        donorMessage
                    );
                } catch (Exception e) {
                    // Log error but don't fail the operation
                    System.err.println("Failed to send partnership notification emails: " + e.getMessage());
                }
                
                return true;
            }
        }
        return false;
    }
    
    /**
     * Unlink a partner from their donor
     */
    public boolean unlinkPartnerFromDonor(Long partnerId) {
        Optional<User> partnerOptional = userRepository.findById(partnerId);
        
        if (partnerOptional.isPresent()) {
            User partner = partnerOptional.get();
            if (partner.getRole() == User.Role.PARTNER) {
                User donor = partner.getParentDonor();
                partner.setParentDonor(null);
                userRepository.save(partner);
                
                // Send email notifications if donor existed
                if (donor != null) {
                    try {
                        // Notify the partner
                        String partnerMessage = String.format(
                            "Hello %s,\n\n" +
                            "We’d like to let you know that your organisation is no longer linked to the following donor organisation:\n\n" +
                            "Donor Organisation: %s\n\n" +
                            "This update has been reflected on the RMNCAH Coordination Hub.\n\n" +
                            "If you have any questions or concerns regarding this change, please contact the platform administrator or support team.\n\n" +
                            "Best regards,\n" +
                            "RMNCAH Coordination Hub Team",
                            partner.getName(),
                            donor.getOrganization() != null ? donor.getOrganization().getName() : donor.getName()
                        );
                        
                        emailService.sendEmail(
                            partner.getEmail(),
                            "RMNCAH Hub - Partnership Update",
                            partnerMessage
                        );
                        
                        // Notify the donor
                        String donorMessage = String.format(
                            "Hello %s,\n\n" +
                            "We’d like to let you know that the following partner organisation has been unlinked from your account:\n\n" +
                            "Partner Organisation: %s\n" +
                            "Partner Contact: %s\n" +
                            "Email: %s\n\n" +
                            "This update has been reflected on the RMNCAH Coordination Hub.\n\n" +
                            "If you have any questions or concerns regarding this change, please contact the platform administrator or support team.\n\n" +
                            "Best regards,\n" +
                            "RMNCAH Coordination Hub Team",
                            donor.getName(),
                            partner.getOrganization() != null ? partner.getOrganization().getName() : "N/A",
                            partner.getName(),
                            partner.getEmail()
                        );
                        
                        emailService.sendEmail(
                            donor.getEmail(),
                            "RMNCAH Hub - Partnership Update",
                            donorMessage
                        );
                    } catch (Exception e) {
                        // Log error but don't fail the operation
                        System.err.println("Failed to send unlink notification emails: " + e.getMessage());
                    }
                }
                
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get all donors
     */
    public java.util.List<User> getAllDonors() {
        return getUsersByRole(User.Role.DONOR);
    }
    
    /**
     * Get available partners (approved partners not linked to any donor)
     */
    public java.util.List<User> getAvailablePartners() {
        return userRepository.findByRoleAndParentDonorIsNull(User.Role.PARTNER)
                .stream()
                .filter(user -> user.getApprovalStatus() == ApprovalStatus.APPROVED)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Check if user can manage partners (donors can manage their linked partners)
     */
    public boolean canUserManagePartner(String userEmail, Long partnerId) {
        Optional<User> userOptional = userRepository.findByEmail(userEmail);
        if (!userOptional.isPresent()) {
            return false;
        }
        
        User user = userOptional.get();
        
        // Super admins can manage all partners
        if (user.getRole() == User.Role.SUPER_ADMIN) {
            return true;
        }
        
        // Donors can manage their linked partners
        if (user.getRole() == User.Role.DONOR) {
            Optional<User> partnerOptional = userRepository.findById(partnerId);
            if (partnerOptional.isPresent()) {
                User partner = partnerOptional.get();
                return partner.getParentDonor() != null && partner.getParentDonor().getId().equals(user.getId());
            }
        }
        
        return false;
    }

    /**
     * Delete user (Super-Admin only)
     */
    @Transactional
    public boolean deleteUser(Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            
            // Delete dependent records first
            
            // Delete supporting documents for this user
            java.util.List<UserDocument> userDocuments = userDocumentRepository.findByUserId(userId);
            userDocumentRepository.deleteAll(userDocuments);
            
            // Delete announcements created by this user
            java.util.List<Announcement> announcements = announcementRepository.findByCreatedBy(user);
            for (Announcement announcement : announcements) {
                // Delete collaboration requests on this announcement
                java.util.List<CollaborationRequest> requestsOnAnnouncement = collaborationRequestRepository.findByAnnouncementIdOrderByCreatedAtDesc(announcement.getId());
                collaborationRequestRepository.deleteAll(requestsOnAnnouncement);
            }
            announcementRepository.deleteAll(announcements);
            
            // Delete collaboration requests by this user
            java.util.List<CollaborationRequest> collaborationRequests = collaborationRequestRepository.findByRequestingUserOrderByCreatedAtDesc(user);
            collaborationRequestRepository.deleteAll(collaborationRequests);
            
            // Delete project collaborators for this user
            java.util.List<ProjectCollaborator> collaborators = projectCollaboratorRepository.findByUser(user);
            projectCollaboratorRepository.deleteAll(collaborators);
            
            // Finally delete the user
            userRepository.delete(user);
            return true;
        }
        return false;
    }
    
    /**
     * Search users by name or email (for reviewer conversion)
     */
    public java.util.List<User> searchUsersByNameOrEmail(String query) {
        java.util.List<User> allUsers = userRepository.findAll();
        String lowerQuery = query.toLowerCase();
        
        return allUsers.stream()
            .filter(user -> 
                user.getName().toLowerCase().contains(lowerQuery) || 
                user.getEmail().toLowerCase().contains(lowerQuery)
            )
            .limit(10)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Convert existing user to reviewer role with thematic areas
     */
    @Transactional
    public User convertToReviewer(Long userId, java.util.List<String> thematicAreaCodes, Long convertedById) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (!userOptional.isPresent()) {
            return null;
        }
        
        User user = userOptional.get();
        User.Role oldRole = user.getRole();
        
        // Update role to reviewer
        user.setRole(User.Role.SUPER_ADMIN_REVIEWER);
        
        // Assign thematic areas
        if (reviewerThematicAreaRepository != null) {
            // Clear existing thematic areas
            reviewerThematicAreaRepository.deleteByUserId(userId);
            
            // Add new thematic areas
            for (String code : thematicAreaCodes) {
                try {
                    com.tujulishanehub.backend.models.ProjectTheme theme = 
                        com.tujulishanehub.backend.models.ProjectTheme.valueOf(code);
                    
                    com.tujulishanehub.backend.models.ReviewerThematicArea reviewerTheme = 
                        new com.tujulishanehub.backend.models.ReviewerThematicArea();
                    reviewerTheme.setUser(user);
                    reviewerTheme.setThematicArea(theme);
                    reviewerThematicAreaRepository.save(reviewerTheme);
                } catch (IllegalArgumentException e) {
                    logger.error("Invalid thematic area code: {}", code);
                }
            }
        }
        
        User savedUser = userRepository.save(user);
        
        // Send notification email
        String areasString = thematicAreaCodes.stream()
            .map(code -> {
                try {
                    return com.tujulishanehub.backend.models.ProjectTheme.valueOf(code).getDisplayName();
                } catch (IllegalArgumentException e) {
                    return code;
                }
            })
            .collect(java.util.stream.Collectors.joining(", "));
        
        String subject = "RMNCAH Hub - You Have Been Assigned as a Reviewer";
        String body = String.format(
            "Hello %s,\n\n" +
            "We’d like to let you know that your role on the RMNCAH Coordination Hub has been updated from %s to Reviewer.\n\n" +
            "You have been assigned to review projects in the following thematic areas:\n" +
            "%s\n\n" +
            "You can now access the Review Requests page to review projects awaiting your attention.\n\n" +
            "Thank you for supporting the RMNCAH Coordination Hub review process.\n\n" +
            "Best regards,\n" +
            "RMNCAH Coordination Hub Team",
            user.getName(),
            oldRole,
            areasString
        );
        emailService.sendEmail(user.getEmail(), subject, body);
        logger.info("User {} converted to reviewer with thematic areas: {}", userId, areasString);
        
        return savedUser;
    }
    
    /**
     * Create new reviewer account and send invitation
     */
    @Transactional
    public User createReviewer(String name, String email, java.util.List<String> thematicAreaCodes, Long createdById) {
        // Create user with SUPER_ADMIN_REVIEWER role
        User reviewer = new User();
        reviewer.setName(name);
        reviewer.setEmail(email);
        reviewer.setRole(User.Role.SUPER_ADMIN_REVIEWER);
        reviewer.setApprovalStatus(ApprovalStatus.APPROVED); // Auto-approve reviewers
        reviewer.setStatus("ACTIVE");
        reviewer.setEmailVerified(false);
        reviewer.setApprovedBy(createdById);
        reviewer.setApprovedAt(java.time.LocalDateTime.now());
        
        // Generate OTP for account activation
        String otp = generateOtp();
        reviewer.setOtp(otp);
        reviewer.setOtpExpiry(java.time.LocalDateTime.now().plusHours(24)); // 24 hour expiry for invitations
        
        User savedReviewer = userRepository.save(reviewer);
        
        // Assign thematic areas
        if (reviewerThematicAreaRepository != null) {
            for (String code : thematicAreaCodes) {
                try {
                    com.tujulishanehub.backend.models.ProjectTheme theme = 
                        com.tujulishanehub.backend.models.ProjectTheme.valueOf(code);
                    
                    com.tujulishanehub.backend.models.ReviewerThematicArea reviewerTheme = 
                        new com.tujulishanehub.backend.models.ReviewerThematicArea();
                    reviewerTheme.setUser(savedReviewer);
                    reviewerTheme.setThematicArea(theme);
                    reviewerThematicAreaRepository.save(reviewerTheme);
                } catch (IllegalArgumentException e) {
                    logger.error("Invalid thematic area code: {}", code);
                }
            }
        }
        
        // Send invitation email
        String areasString = thematicAreaCodes.stream()
            .map(code -> {
                try {
                    return com.tujulishanehub.backend.models.ProjectTheme.valueOf(code).getDisplayName();
                } catch (IllegalArgumentException e) {
                    return code;
                }
            })
            .collect(java.util.stream.Collectors.joining(", "));
        
        String subject = "Invitation to Join the RMNCAH Coordination Hub as a Reviewer";
        String body = String.format(
            "Hello %s,\n\n" +
            "You have been invited to join the RMNCAH Coordination Hub as a Reviewer.\n\n" +
            "You have been assigned to review projects in the following thematic areas:\n" +
            "%s\n\n" +
            "To activate your account, please use the following One-Time Password (OTP): %s\n\n" +
            "This OTP will expire in 24 hours.\n\n" +
            "Once your account is activated, you can access the Review Requests page to view and review projects awaiting your attention.\n\n" +
            "We’re pleased to have you supporting the review process on the RMNCAH Coordination Hub.\n\n" +
            "Best regards,\n" +
            "RMNCAH Coordination Hub Team",
            name,
            areasString,
            otp
        );
        emailService.sendEmail(email, subject, body);
        logger.info("Reviewer created: {} with thematic areas: {}", email, areasString);

        return savedReviewer;
    }

}
