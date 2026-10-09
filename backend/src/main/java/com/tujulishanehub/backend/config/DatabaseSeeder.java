package com.tujulishanehub.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tujulishanehub.backend.models.ApprovalStatus;
import com.tujulishanehub.backend.models.User;
import com.tujulishanehub.backend.models.Project;
import com.tujulishanehub.backend.models.ProjectLocation;
import com.tujulishanehub.backend.models.ProjectTheme;
import com.tujulishanehub.backend.models.ReviewerThematicArea;
import com.tujulishanehub.backend.repositories.UserRepository;
import com.tujulishanehub.backend.repositories.ProjectRepository;
import com.tujulishanehub.backend.repositories.ReviewerThematicAreaRepository;
import com.tujulishanehub.backend.repositories.ThematicAreaDefinitionRepository;
import com.tujulishanehub.backend.models.ThematicAreaDefinition;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class DatabaseSeeder {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSeeder.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ReviewerThematicAreaRepository reviewerThematicAreaRepository;

    @Autowired
    private ThematicAreaDefinitionRepository thematicAreaDefinitionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Bean
    public CommandLineRunner seedDatabase() {
        return args -> {
            logger.info("Initializing database seeding...");
            
            // Seed dynamic thematic area definitions first
            seedThematicAreas();
            
            String defaultPassword = passwordEncoder.encode("Password@123");

            // Lomogan Reviewer (SUPER_ADMIN_REVIEWER)
            User lomoganReviewer = seedUser("lomogantech@gmail.com", "Lomogan Reviewer", User.Role.SUPER_ADMIN_REVIEWER, defaultPassword);

            // Keegan Kariuki (SUPER_ADMIN_APPROVER)
            User keeganApprover = seedUser("kariukikeegan@gmail.com", "Keegan Kariuki", User.Role.SUPER_ADMIN_APPROVER, defaultPassword);

            // Braine Kapolon (SUPER_ADMIN_APPROVER)
            User braineApprover = seedUser("kapolonbraine@gmail.com", "Braine Kapolon", User.Role.SUPER_ADMIN_APPROVER, defaultPassword);

            // Hubert Manduku (SUPER_ADMIN_APPROVER)
            User hubertApprover = seedUser("hubertmanduku@gmail.com", "Hubert Manduku", User.Role.SUPER_ADMIN_APPROVER, defaultPassword);

            // Seed reviewer thematic area assignments
            if (lomoganReviewer != null) {
                seedReviewerTheme(lomoganReviewer, ProjectTheme.MNH);
                seedReviewerTheme(lomoganReviewer, ProjectTheme.AYPSRH);
                seedReviewerTheme(lomoganReviewer, ProjectTheme.FP);
            }

            // Backfill county for any existing projects where county is missing or 'Kenya'
            backfillProjectCounties();

            logger.info("Database seeding completed successfully!");
        };
    }

    private void backfillProjectCounties() {
        try {
            org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> {
                List<Project> allProjects = projectRepository.findAllWithLocations();
                for (Project p : allProjects) {
                    if (p.getCounty() == null || p.getCounty().trim().isEmpty() || "Kenya".equalsIgnoreCase(p.getCounty().trim())) {
                        try {
                            if (p.getLocations() != null && !p.getLocations().isEmpty()) {
                                String c = p.getLocations().stream()
                                    .map(ProjectLocation::getCounty)
                                    .filter(locCounty -> locCounty != null && !locCounty.trim().isEmpty() && !"Kenya".equalsIgnoreCase(locCounty.trim()))
                                    .findFirst()
                                    .orElse(null);
                                if (c != null) {
                                    p.setCounty(c);
                                    projectRepository.save(p);
                                    logger.info("Backfilled county for existing project {}: {}", p.getProjectNo(), c);
                                }
                            }
                        } catch (Exception pe) {
                            logger.warn("Could not backfill county for project {}: {}", p.getProjectNo(), pe.getMessage());
                        }
                    }
                }
            });
        } catch (Exception e) {
            logger.warn("Could not backfill project counties on startup: {}", e.getMessage());
        }
    }

    private User seedUser(String email, String name, User.Role role, String encodedPassword) {
        java.util.Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isEmpty()) {
            User user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setPassword(encodedPassword);
            user.setRole(role);
            user.setStatus("ACTIVE");
            user.setEmailVerified(true);
            user.setVerified(true);
            user.setApprovalStatus(ApprovalStatus.APPROVED);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            user.setApprovedAt(LocalDateTime.now());
            User saved = userRepository.save(user);
            logger.info("Seeded user: {} ({}) with role: {}", name, email, role);
            return saved;
        } else {
            logger.info("User already exists: {} ({})", name, email);
            return existing.get();
        }
    }

    private void seedReviewerTheme(User reviewer, ProjectTheme theme) {
        if (!reviewerRepositoryHasAssignment(reviewer.getId(), theme)) {
            ReviewerThematicArea assignment = new ReviewerThematicArea(reviewer, theme, 1L); // assigned by admin (1)
            reviewerThematicAreaRepository.save(assignment);
            logger.info("Assigned theme {} to reviewer: {}", theme, reviewer.getEmail());
        }
    }

    private boolean reviewerRepositoryHasAssignment(Long userId, ProjectTheme theme) {
        try {
            return reviewerThematicAreaRepository.existsByUserIdAndThematicArea(userId, theme);
        } catch (Exception e) {
            return false;
        }
    }

    private void seedThematicAreas() {
        if (thematicAreaDefinitionRepository.count() == 0) {
            logger.info("Seeding thematic area definitions...");
            seedThematicArea("GBV", "Gender-Based Violence", "Programs focused on preventing and responding to gender-based violence across communities.", "fas fa-venus-mars", "text-pink-400");
            seedThematicArea("AYPSRH", "Adolescent and Young People Sexual and Reproductive Health", "Comprehensive sexual and reproductive health services for adolescents and young people.", "far fa-heart px-2", "text-red-400");
            seedThematicArea("MNH", "Maternal and Newborn Health", "Programs dedicated to improving maternal and newborn health outcomes.", "fas fa-baby", "text-blue-400");
            seedThematicArea("FP", "Family Planning", "Family planning services and education to support reproductive choices.", "fas fa-users", "text-green-400");
            seedThematicArea("CH", "Child Health", "Comprehensive child health programs and interventions.", "fas fa-child", "text-purple-400");
            seedThematicArea("AH", "Adolescent Health", "Health programs specifically designed for adolescents.", "fas fa-user-graduate", "text-orange-400");
            seedThematicArea("ADV_SBC", "Advocacy and SBC (Social & Behavior Change)", "Advocacy and strategic social/behavioral communication initiatives to promote health outcomes.", "fas fa-bullhorn", "text-teal-400");
            seedThematicArea("MONITORING_EVALUATION", "Monitoring and Evaluation", "Systems and frameworks designed to track progress, evaluate outcomes, and measure overall project impact.", "fas fa-chart-line", "text-indigo-400");
            seedThematicArea("RESEARCH_LEARNING", "Research and Learning", "Research, data collection, and academic learning processes designed to generate evidence for health policy implementation.", "fas fa-book-open", "text-rose-400");
        }
    }

    private void seedThematicArea(String code, String title, String description, String icon, String color) {
        ThematicAreaDefinition def = new ThematicAreaDefinition();
        def.setCode(code);
        def.setTitle(title);
        def.setDescription(description);
        def.setIcon(icon);
        def.setColor(color);
        thematicAreaDefinitionRepository.save(def);
        logger.info("Seeded thematic area: {} ({})", title, code);
    }
}
