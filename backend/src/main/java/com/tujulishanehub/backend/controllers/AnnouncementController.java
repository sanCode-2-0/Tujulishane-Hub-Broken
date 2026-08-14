package com.tujulishanehub.backend.controllers;

import com.tujulishanehub.backend.models.Announcement;
import com.tujulishanehub.backend.models.Message;
import com.tujulishanehub.backend.payload.ApiResponse;
import com.tujulishanehub.backend.payload.AnnouncementRequest;
import com.tujulishanehub.backend.payload.MessageRequest;
import com.tujulishanehub.backend.services.AnnouncementService;
import com.tujulishanehub.backend.services.MessageService;
import com.tujulishanehub.backend.services.UserService;
import com.tujulishanehub.backend.services.UserService;
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

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    
    private static final Logger logger = LoggerFactory.getLogger(AnnouncementController.class);
    
    @Autowired
    private AnnouncementService announcementService;
    
    @Autowired
    private MessageService messageService;
    
    @Autowired
    private UserService userService;
    
    /**
     * Create new announcement (Admins only)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SUPER_ADMIN_REVIEWER', 'SUPER_ADMIN_APPROVER', 'PARTNER')")
    public ResponseEntity<ApiResponse<Announcement>> createAnnouncement(
            @Valid @RequestBody AnnouncementRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            
            Announcement created = announcementService.createAnnouncement(request, userEmail);
            
            ApiResponse<Announcement> response = new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "Announcement created successfully",
                created
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (RuntimeException e) {
            logger.error("Error creating announcement: {}", e.getMessage());
            ApiResponse<Announcement> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "Failed to create announcement: " + e.getMessage(),
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error creating announcement: {}", e.getMessage(), e);
            ApiResponse<Announcement> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to create announcement: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get all active announcements (Public)
     */
    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Announcement>>> getActiveAnnouncements() {
        try {
            logger.info("Fetching all active announcements");
            List<Announcement> announcements = announcementService.getActiveAnnouncements();
            logger.info("Found {} active announcements", announcements.size());
            
            ApiResponse<List<Announcement>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Announcements retrieved successfully",
                announcements
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving announcements: {}", e.getMessage(), e);
            ApiResponse<List<Announcement>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve announcements: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get my announcements (Partners only)
     */
    @GetMapping("/my-announcements")
    @PreAuthorize("hasRole('PARTNER')")
    public ResponseEntity<ApiResponse<List<Announcement>>> getMyAnnouncements() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            
            List<Announcement> announcements = announcementService.getMyAnnouncements(userEmail);
            
            ApiResponse<List<Announcement>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Your announcements retrieved successfully",
                announcements
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving user announcements: {}", e.getMessage(), e);
            ApiResponse<List<Announcement>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve your announcements",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get specific announcement by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Announcement>> getAnnouncementById(@PathVariable Long id) {
        try {
            return announcementService.getAnnouncementById(id)
                .map(announcement -> {
                    ApiResponse<Announcement> response = new ApiResponse<>(
                        HttpStatus.OK.value(),
                        "Announcement retrieved successfully",
                        announcement
                    );
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    ApiResponse<Announcement> response = new ApiResponse<>(
                        HttpStatus.NOT_FOUND.value(),
                        "Announcement not found",
                        null
                    );
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                });
            
        } catch (Exception e) {
            logger.error("Error retrieving announcement: {}", e.getMessage(), e);
            ApiResponse<Announcement> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve announcement",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Close announcement (Owner or Admin only)
     */
    @PostMapping("/{id}/close")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Object>> closeAnnouncement(@PathVariable Long id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            
            boolean success = announcementService.closeAnnouncement(id, userEmail);
            
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "Announcement closed successfully",
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Announcement not found or permission denied",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error closing announcement: {}", e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to close announcement",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @GetMapping("/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Message>>> getMessages(@PathVariable Long id) {
        try {
            Announcement announcement = announcementService.getAnnouncementById(id).orElse(null);
            if (announcement == null) {
                ApiResponse<List<Message>> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Announcement not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            List<Message> messages = messageService.getMessagesForAnnouncement(announcement);
            
            ApiResponse<List<Message>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Messages retrieved successfully",
                messages
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving messages: {}", e.getMessage(), e);
            ApiResponse<List<Message>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve messages",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @PostMapping("/{id}/messages")
    @PreAuthorize("hasRole('SUPER_ADMIN_REVIEWER')")
    public ResponseEntity<ApiResponse<Message>> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody MessageRequest request) {
        try {
            Announcement announcement = announcementService.getAnnouncementById(id).orElse(null);
            if (announcement == null) {
                ApiResponse<Message> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Announcement not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            
            Message message = messageService.saveMessage(request.getMessage(), userService.getUserByEmail(userEmail), announcement);
            
            ApiResponse<Message> response = new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "Message sent successfully",
                message
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            logger.error("Error sending message: {}", e.getMessage(), e);
            ApiResponse<Message> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to send message",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @PutMapping("/{announcementId}/messages/{messageId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN_REVIEWER', 'SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Message>> updateMessage(
            @PathVariable Long announcementId,
            @PathVariable Long messageId,
            @Valid @RequestBody MessageRequest request) {
        try {
            Announcement announcement = announcementService.getAnnouncementById(announcementId).orElse(null);
            if (announcement == null) {
                ApiResponse<Message> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Announcement not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Message updatedMessage = messageService.updateMessage(messageId, request.getMessage());
            
            ApiResponse<Message> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Message updated successfully",
                updatedMessage
            );
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("Error updating message: {}", e.getMessage());
            ApiResponse<Message> response = new ApiResponse<>(
                HttpStatus.NOT_FOUND.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("Error updating message: {}", e.getMessage(), e);
            ApiResponse<Message> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to update message",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @DeleteMapping("/{announcementId}/messages/{messageId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN_REVIEWER', 'SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Object>> deleteMessage(
            @PathVariable Long announcementId,
            @PathVariable Long messageId) {
        try {
            Announcement announcement = announcementService.getAnnouncementById(announcementId).orElse(null);
            if (announcement == null) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Announcement not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            messageService.deleteMessage(messageId);
            
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Message deleted successfully",
                null
            );
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("Error deleting message: {}", e.getMessage());
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.NOT_FOUND.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("Error deleting message: {}", e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to delete message",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}