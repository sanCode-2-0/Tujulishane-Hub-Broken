package com.tujulishanehub.backend.controllers;

import com.tujulishanehub.backend.models.CollaborationRequest;
import com.tujulishanehub.backend.payload.ApiResponse;
import com.tujulishanehub.backend.services.CollaborationRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collaboration-requests")
public class CollaborationRequestController {
    
    private static final Logger logger = LoggerFactory.getLogger(CollaborationRequestController.class);
    
    @Autowired
    private CollaborationRequestService collaborationRequestService;
    
    /**
     * Submit collaboration request (Partners only)
     */
    @PostMapping("/announcements/{announcementId}")
    @PreAuthorize("hasRole('PARTNER')")
    public ResponseEntity<ApiResponse<CollaborationRequest>> submitCollaborationRequest(
            @PathVariable Long announcementId,
            @Valid @RequestBody CollaborationRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            
            CollaborationRequest created = collaborationRequestService.createCollaborationRequest(
                announcementId, request, userEmail);
            
            ApiResponse<CollaborationRequest> response = new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "Collaboration request submitted successfully",
                created
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (RuntimeException e) {
            logger.error("Error submitting collaboration request: {}", e.getMessage());
            ApiResponse<CollaborationRequest> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "Failed to submit collaboration request: " + e.getMessage(),
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error submitting collaboration request: {}", e.getMessage(), e);
            ApiResponse<CollaborationRequest> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to submit collaboration request: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get requests for my projects (Partners only)
     */
    @GetMapping("/my-projects")
    @PreAuthorize("hasRole('PARTNER')")
    public ResponseEntity<ApiResponse<List<CollaborationRequest>>> getRequestsForMyProjects() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            
            List<CollaborationRequest> requests = collaborationRequestService.getRequestsForUserProjects(userEmail);
            
            ApiResponse<List<CollaborationRequest>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Collaboration requests retrieved successfully",
                requests
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving collaboration requests: {}", e.getMessage(), e);
            ApiResponse<List<CollaborationRequest>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve collaboration requests",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get my collaboration requests (Partners and Reviewers)
     */
    @GetMapping("/my-requests")
    @PreAuthorize("hasAnyRole('PARTNER', 'DONOR', 'SUPER_ADMIN_REVIEWER')")
    public ResponseEntity<ApiResponse<List<CollaborationRequest>>> getMyCollaborationRequests() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            
            List<CollaborationRequest> requests = collaborationRequestService.getMyCollaborationRequests(userEmail);
            
            ApiResponse<List<CollaborationRequest>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Your collaboration requests retrieved successfully",
                requests
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving user collaboration requests: {}", e.getMessage(), e);
            ApiResponse<List<CollaborationRequest>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve your collaboration requests",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get specific collaboration request by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CollaborationRequest>> getCollaborationRequestById(@PathVariable Long id) {
        try {
            return collaborationRequestService.getCollaborationRequestById(id)
                .map(request -> {
                    ApiResponse<CollaborationRequest> response = new ApiResponse<>(
                        HttpStatus.OK.value(),
                        "Collaboration request retrieved successfully",
                        request
                    );
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    ApiResponse<CollaborationRequest> response = new ApiResponse<>(
                        HttpStatus.NOT_FOUND.value(),
                        "Collaboration request not found",
                        null
                    );
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                });
            
        } catch (Exception e) {
            logger.error("Error retrieving collaboration request: {}", e.getMessage(), e);
            ApiResponse<CollaborationRequest> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve collaboration request",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // ==================== ADMIN ENDPOINTS ====================
    
    /**
     * Get pending collaboration requests (MOH only)
     */
    @GetMapping("/admin/pending")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_REVIEWER') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<List<CollaborationRequest>>> getPendingRequests() {
        try {
            List<CollaborationRequest> requests = collaborationRequestService.getPendingRequests();
            
            ApiResponse<List<CollaborationRequest>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Pending collaboration requests retrieved successfully",
                requests
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving pending collaboration requests: {}", e.getMessage(), e);
            ApiResponse<List<CollaborationRequest>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve pending collaboration requests",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get all collaboration requests (MOH only)
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_REVIEWER') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<List<CollaborationRequest>>> getAllRequests() {
        try {
            List<CollaborationRequest> requests = collaborationRequestService.getAllRequests();
            
            ApiResponse<List<CollaborationRequest>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "All collaboration requests retrieved successfully",
                requests
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving all collaboration requests: {}", e.getMessage(), e);
            ApiResponse<List<CollaborationRequest>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve all collaboration requests",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Approve collaboration request (MOH only)
     */
    @PostMapping("/admin/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_REVIEWER') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Object>> approveCollaborationRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String reviewerEmail = auth.getName();
            String notes = payload != null ? payload.getOrDefault("notes", "") : "";
            
            boolean success = collaborationRequestService.approveCollaborationRequest(id, reviewerEmail, notes);
            
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "Collaboration request approved successfully",
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Collaboration request not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
        } catch (RuntimeException e) {
            logger.error("Error approving collaboration request: {}", e.getMessage());
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error approving collaboration request: {}", e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to approve collaboration request",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Decline collaboration request (MOH only)
     */
    @PostMapping("/admin/{id}/decline")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SUPER_ADMIN_REVIEWER') or hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Object>> declineCollaborationRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String reviewerEmail = auth.getName();
            String notes = payload != null ? payload.getOrDefault("notes", "Declined by MOH") : "Declined by MOH";
            
            boolean success = collaborationRequestService.declineCollaborationRequest(id, reviewerEmail, notes);
            
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "Collaboration request declined",
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Collaboration request not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
        } catch (RuntimeException e) {
            logger.error("Error declining collaboration request: {}", e.getMessage());
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error declining collaboration request: {}", e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to decline collaboration request",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}