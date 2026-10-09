package com.tujulishanehub.backend.services;

import com.tujulishanehub.backend.models.Announcement;
import com.tujulishanehub.backend.models.AnnouncementStatus;
import com.tujulishanehub.backend.models.Project;
import com.tujulishanehub.backend.models.User;
import com.tujulishanehub.backend.payload.AnnouncementRequest;
import com.tujulishanehub.backend.repositories.AnnouncementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AnnouncementService {
    
    private static final Logger logger = LoggerFactory.getLogger(AnnouncementService.class);
    
    @Autowired
    private AnnouncementRepository announcementRepository;
    
    @Autowired
    private ProjectService projectService;
    
    @Autowired
    private UserService userService;
    
    /**
     * Create a new announcement from request DTO
     */
    public Announcement createAnnouncement(AnnouncementRequest request, String userEmail) {
        logger.info("Creating announcement for project ID: {} by user: {}", 
                   request.getProjectId(), userEmail);
        
        User user = userService.getUserByEmail(userEmail);
        Project project = projectService.getProjectById(request.getProjectId())
            .orElseThrow(() -> new RuntimeException("Project not found"));
        
        // Verify user owns the project or is admin
        boolean isOwner = (project.getPartner() != null && project.getPartner().equals(userEmail)) 
            || (project.getContactPersonEmail() != null && project.getContactPersonEmail().equals(userEmail));
        if (!isOwner && !user.isSuperAdmin()) {
            throw new RuntimeException("You don't have permission to create announcements for this project");
        }
        
        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcement.setDeadline(request.getDeadline());
        announcement.setStatus(request.getStatus() != null ? request.getStatus() : AnnouncementStatus.ACTIVE);
        announcement.setCreatedBy(user);
        announcement.setProject(project);
        announcement.setCreatedAt(LocalDateTime.now());
        announcement.setUpdatedAt(LocalDateTime.now());
        
        Announcement saved = announcementRepository.save(announcement);
        logger.info("Announcement created successfully with ID: {}", saved.getId());
        return saved;
    }
    
    /**
     * Create a new announcement (legacy method)
     */
    public Announcement createAnnouncement(Announcement announcement, String userEmail) {
        logger.info("Creating announcement for project ID: {} by user: {}", 
                   announcement.getProject().getId(), userEmail);
        
        User user = userService.getUserByEmail(userEmail);
        Project project = projectService.getProjectById(announcement.getProject().getId())
            .orElseThrow(() -> new RuntimeException("Project not found"));
        
        // Verify user owns the project or is admin
        boolean isOwner = (project.getPartner() != null && project.getPartner().equals(userEmail)) 
            || (project.getContactPersonEmail() != null && project.getContactPersonEmail().equals(userEmail));
        if (!isOwner && !user.isSuperAdmin()) {
            throw new RuntimeException("You don't have permission to create announcements for this project");
        }
        
        announcement.setCreatedBy(user);
        announcement.setProject(project);
        announcement.setCreatedAt(LocalDateTime.now());
        announcement.setUpdatedAt(LocalDateTime.now());
        
        Announcement saved = announcementRepository.save(announcement);
        logger.info("Announcement created successfully with ID: {}", saved.getId());
        return saved;
    }
    
    /**
     * Get all active announcements
     */
    public List<Announcement> getActiveAnnouncements() {
        logger.info("Retrieving all active announcements");
        try {
            List<Announcement> announcements = announcementRepository.findByStatusOrderByCreatedAtDesc(AnnouncementStatus.ACTIVE);
            logger.info("Found {} announcements with ACTIVE status", announcements.size());
            return announcements;
        } catch (Exception e) {
            logger.error("Error fetching active announcements: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get announcements created by user
     */
    public List<Announcement> getMyAnnouncements(String userEmail) {
        logger.info("Retrieving announcements for user: {}", userEmail);
        User user = userService.getUserByEmail(userEmail);
        return announcementRepository.findByCreatedByAndStatusOrderByCreatedAtDesc(
            user, AnnouncementStatus.ACTIVE);
    }
    
    /**
     * Close an announcement
     */
    public boolean closeAnnouncement(Long announcementId, String userEmail) {
        logger.info("Closing announcement ID: {} by user: {}", announcementId, userEmail);
        
        Optional<Announcement> announcementOpt = announcementRepository.findById(announcementId);
        if (announcementOpt.isPresent()) {
            Announcement announcement = announcementOpt.get();
            User user = userService.getUserByEmail(userEmail);
            
            // Verify permissions
            if (!announcement.getCreatedBy().equals(user) && !user.isSuperAdmin()) {
                logger.warn("User {} attempted to close announcement {} without permission", userEmail, announcementId);
                return false;
            }
            
            announcement.setStatus(AnnouncementStatus.CLOSED);
            announcement.setUpdatedAt(LocalDateTime.now());
            announcementRepository.save(announcement);
            
            logger.info("Announcement {} closed successfully", announcementId);
            return true;
        }
        
        logger.warn("Announcement {} not found", announcementId);
        return false;
    }
    
    /**
     * Get announcement by ID
     */
    public Optional<Announcement> getAnnouncementById(Long id) {
        return announcementRepository.findById(id);
    }
}