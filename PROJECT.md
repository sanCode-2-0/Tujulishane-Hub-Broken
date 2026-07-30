# Tujulishane Hub - Project Documentation

## Table of Contents
- [Overview](#overview)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Deployment](#deployment)
- [API Documentation](#api-documentation)
- [User Roles & Permissions](#user-roles--permissions)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

**Tujulishane Hub** is a comprehensive project management and monitoring platform designed specifically for Reproductive, Maternal, Newborn, Child, and Adolescent Health (RMNCAH) initiatives in Kenya. The platform enables healthcare organizations, NGOs, government agencies, and other stakeholders to collaborate on health projects, track progress, and share reports transparently.

### Purpose

The platform addresses the critical need for:
- **Centralized Project Tracking**: A unified hub for all RMNCAH projects across Kenya
- **Transparency**: Public visibility of health initiatives and their impact
- **Accountability**: Structured reporting mechanisms for project outcomes
- **Collaboration**: Multi-stakeholder engagement in healthcare improvement
- **Geographic Mapping**: Visual representation of project distribution across counties

### Target Users

- **Partners**: Organizations implementing RMNCAH projects (NGOs, healthcare providers, donor agencies)
- **Administrators**: Ministry of Health officials managing and approving projects
- **Public**: Citizens accessing information about health initiatives in their communities

---

## Features

### 🔐 Authentication & User Management
- **OTP-based Authentication**: Passwordless login via email OTP for enhanced security
- **Role-based Access Control**: SUPER_ADMIN and PARTNER roles with granular permissions
- **User Approval Workflow**: Admin approval required for new user registrations
- **Email Verification**: Mandatory email verification for all users

### 🏢 Organization Management
- **Organization Registration**: Support for 10+ organization types (NGO, Government Agency, International Org, etc.)
- **Organization Approval**: Admin review and approval process
- **Organization Profiles**: Complete organization information including contact details and registration numbers

### 📋 Project Management
- **Comprehensive Project Creation**: Detailed project information including objectives, budget, and timeline
- **Project Themes**: Categorization by health themes (GBV, MNH, FP, CH, AH, AYPSRH)
- **Project Status Tracking**: Active, pending, stalled, completed, or abandoned status
- **Geographic Information**: County/sub-county locations with geocoding support
- **Budget Tracking**: Project budget and utilization monitoring
- **Project Search**: Multi-criteria search by partner, title, county, status, and activity type
- **My Projects**: Personalized project portfolio for partners

### 📊 Reporting System
- **Multiple Report Types**: Completion, interim, financial, impact, and evaluation reports
- **Report Workflow**: Draft → Submit → Under Review → Approved → Published
- **Rich Content**: Support for detailed content, outcomes, challenges, and recommendations
- **Attachments**: Document and image upload capabilities
- **Impact Metrics**: Track beneficiaries reached, budget utilized, and completion percentage
- **Report Search**: Filter reports by project, type, status, and keywords

### 🗺️ Geographic Features
- **Interactive Maps**: Leaflet-based map visualization of projects
- **Geocoding**: Automatic coordinate extraction from addresses
- **Bounding Box Search**: Find projects within specific geographic areas
- **Coordinate Coverage**: Track percentage of projects with valid coordinates

### 📈 Analytics & Statistics
- **Project Statistics**: Total projects, status distribution, county distribution
- **Report Statistics**: Report counts by type and status
- **Coverage Metrics**: Geographic coordinate coverage percentage
- **Dashboard Views**: Admin and partner dashboards with key metrics

### 🔒 Security Features
- **JWT Authentication**: Secure token-based authentication
- **Bootstrap Security**: One-time SUPER_ADMIN creation with secret key
- **Permission Checks**: Endpoint-level authorization
- **Email Security**: OTP verification for sensitive operations

---

## Technology Stack

### Backend
- **Framework**: Spring Boot 3.2.0
- **Language**: Java 21
- **Database**: PostgreSQL (Production), H2 (Development)
- **ORM**: Spring Data JPA
- **Security**: Spring Security + JWT (io.jsonwebtoken)
- **Email**: Spring Boot Mail
- **Validation**: Spring Boot Validation
- **API Documentation**: SpringDoc OpenAPI 2.3.0
- **Build Tool**: Gradle 8.x
- **Additional Libraries**:
  - Lombok 1.18.30 (for reducing boilerplate)
  - Jackson (JSON processing)

### Frontend
- **Core**: HTML5, CSS3, JavaScript (Vanilla)
- **CSS Framework**: Tailwind CSS
- **UI Components**: Flowbite, TW Elements
- **Icons**: Font Awesome 6.5.2
- **Maps**: Leaflet 1.9.4
- **State Management**: Alpine.js 3.x
- **Fonts**: Google Fonts (Inter, Montserrat)

### Infrastructure & DevOps
- **Hosting**: Render.com
- **Containerization**: Docker
- **CI/CD**: Configured via render.yaml
- **Environment Management**: Spring Profiles (dev, prod)

### APIs & Services
- **Geocoding**: Google Maps Geocoding API (or similar)
- **Email Service**: SMTP-based email delivery

---

## Architecture

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend Layer                        │
│  (HTML/CSS/JS + Tailwind + Alpine.js + Leaflet)             │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTPS/REST API
┌──────────────────────▼──────────────────────────────────────┐
│                     Spring Boot Backend                       │
│  ┌────────────────────────────────────────────────────────┐  │
│  │              Controllers Layer                         │  │
│  │  - UserController                                      │  │
│  │  - OrganizationController                              │  │
│  │  - ProjectController                                   │  │
│  │  - ProjectReportController                             │  │
│  └────────────┬───────────────────────────────────────────┘  │
│               │                                               │
│  ┌────────────▼───────────────────────────────────────────┐  │
│  │              Services Layer                            │  │
│  │  - UserService                                         │  │
│  │  - OrganizationService                                 │  │
│  │  - ProjectService                                      │  │
│  │  - ProjectReportService                                │  │
│  │  - EmailService                                        │  │
│  │  - GeocodingService                                    │  │
│  └────────────┬───────────────────────────────────────────┘  │
│               │                                               │
│  ┌────────────▼───────────────────────────────────────────┐  │
│  │            Repositories Layer                          │  │
│  │  (Spring Data JPA)                                     │  │
│  └────────────┬───────────────────────────────────────────┘  │
└───────────────┼───────────────────────────────────────────────┘
                │
┌───────────────▼───────────────────────────────────────────┐
│                  PostgreSQL Database                       │
│  Tables: users, organizations, projects, project_reports  │
└────────────────────────────────────────────────────────────┘
```

### Security Architecture

```
┌─────────────────┐
│  Client Request │
└────────┬────────┘
         │
┌────────▼─────────────────────────────┐
│    JWT Request Filter                │
│  - Validates JWT token               │
│  - Sets SecurityContext              │
└────────┬─────────────────────────────┘
         │
┌────────▼─────────────────────────────┐
│   Security Configuration             │
│  - Role-based authorization          │
│  - Endpoint permissions              │
└────────┬─────────────────────────────┘
         │
┌────────▼─────────────────────────────┐
│   Controller Endpoints               │
│  - @PreAuthorize checks              │
│  - Role verification                 │
└──────────────────────────────────────┘
```

### Data Model

**Core Entities:**

1. **User**
   - Authentication and profile information
   - Role (SUPER_ADMIN, PARTNER)
   - Approval status
   - Linked to Organization

2. **Organization**
   - Organization details and type
   - Contact information
   - Approval workflow
   - Registration details

3. **Project**
   - Project information and timeline
   - Theme categorization
   - Geographic location
   - Budget tracking
   - Status management
   - Created by User

4. **ProjectReport**
   - Report content and metrics
   - Report type and status
   - Financial data
   - Attachments and images
   - Linked to Project

---

## Project Structure

```
Tujulishane-Hub/
├── backend/                          # Spring Boot backend application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/tujulishanehub/backend/
│   │   │   │   ├── config/          # Security, CORS, JWT configuration
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   ├── JacksonConfig.java
│   │   │   │   │   ├── JwtRequestFilter.java
│   │   │   │   │   ├── SecurityConfig.java
│   │   │   │   │   ├── RestTemplateConfig.java
│   │   │   │   │   └── WebConfig.java
│   │   │   │   ├── controllers/     # REST API endpoints
│   │   │   │   │   ├── OrganizationController.java
│   │   │   │   │   ├── ProjectController.java
│   │   │   │   │   ├── ProjectReportController.java
│   │   │   │   │   ├── ProtectedController.java
│   │   │   │   │   ├── SpaController.java
│   │   │   │   │   └── UserController.java
│   │   │   │   ├── models/          # JPA entities
│   │   │   │   │   ├── ApprovalStatus.java
│   │   │   │   │   ├── Organization.java
│   │   │   │   │   ├── Project.java
│   │   │   │   │   ├── ProjectReport.java
│   │   │   │   │   ├── ProjectTheme.java
│   │   │   │   │   └── User.java
│   │   │   │   ├── repositories/    # Database access layer
│   │   │   │   │   ├── OrganizationRepository.java
│   │   │   │   │   ├── ProjectReportRepository.java
│   │   │   │   │   ├── ProjectRepository.java
│   │   │   │   │   └── UserRepository.java
│   │   │   │   ├── services/        # Business logic
│   │   │   │   │   ├── EmailService.java
│   │   │   │   │   ├── GeocodingService.java
│   │   │   │   │   ├── OrganizationService.java
│   │   │   │   │   ├── ProjectReportService.java
│   │   │   │   │   ├── ProjectService.java
│   │   │   │   │   └── UserService.java
│   │   │   │   ├── util/            # Utility classes
│   │   │   │   │   └── JwtUtil.java
│   │   │   │   ├── payload/         # DTOs and responses
│   │   │   │   │   └── ApiResponse.java
│   │   │   │   └── TujulishaneHubApplication.java  # Main application
│   │   │   └── resources/
│   │   │       ├── application.properties          # Configuration
│   │   │       └── application-prod.properties     # Production config
│   │   └── test/                    # Unit and integration tests
│   ├── build.gradle                 # Gradle build configuration
│   ├── Dockerfile                   # Docker container definition
│   ├── docker-compose.yml           # Docker Compose for local dev
│   └── gradlew                      # Gradle wrapper
├── frontend/                        # Frontend web application
│   ├── Forms/                       # Form pages
│   │   └── Admin/                   # Admin-specific forms
│   ├── Stakeholders/                # Stakeholder information pages
│   ├── resources/                   # Static resources
│   │   └── images/                  # Image assets
│   ├── styles/                      # CSS and JS files
│   │   ├── main.css
│   │   └── main.js
│   ├── index.html                   # Landing page
│   ├── index-post-login.html        # Authenticated home page
│   ├── dashboard.html               # User dashboard
│   ├── projects.html                # Projects page
│   ├── all-projects.html            # All projects listing
│   ├── members.html                 # Members management
│   ├── otp.html                     # OTP verification page
│   ├── auth.js                      # Authentication utilities
│   └── test-auth.html               # Authentication testing page
├── API_DOCUMENTATION.md             # Comprehensive API documentation
├── README.md                        # Quick start guide
├── PROJECT.md                       # This file - detailed project documentation
├── render.yaml                      # Render.com deployment configuration
└── .gitignore                       # Git ignore rules
```

---

## Getting Started

### Prerequisites

**Backend:**
- Java 21 or higher
- Gradle 8.x (wrapper included)
- PostgreSQL 12+ (for production) or H2 (for development)

**Frontend:**
- Modern web browser (Chrome, Firefox, Safari, Edge)
- Web server (or use backend to serve static files)

### Environment Variables

Create a `.env` file or set the following environment variables:

**Required:**
```bash
# Database Configuration (Production)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tujulishane_db
SPRING_DATASOURCE_USERNAME=your_db_username
SPRING_DATASOURCE_PASSWORD=your_db_password

# JWT Configuration
JWT_SECRET=your-very-long-and-secure-secret-key-here
JWT_EXPIRATION=3600  # Token expiration in seconds (1 hour)

# Email Configuration
MAIL_USERNAME=your-email@example.com
MAIL_PASSWORD=your-email-password
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587

# Bootstrap Configuration (for initial SUPER_ADMIN creation)
BOOTSTRAP_SECRET=your-bootstrap-secret-key
```

**Optional:**
```bash
# Geocoding API
GEOCODING_API_KEY=your-geocoding-api-key

# CORS Configuration
FRONTEND_ORIGIN=http://localhost:8080,https://yourdomain.com

# Spring Profile
SPRING_PROFILES_ACTIVE=dev  # or 'prod' for production
```

### Backend Setup

1. **Clone the Repository**
   ```bash
   git clone https://github.com/sanCode-2-0/Tujulishane-Hub.git
   cd Tujulishane-Hub/backend
   ```

2. **Configure Database**
   
   For development (H2 in-memory database):
   ```properties
   # No configuration needed - H2 is auto-configured
   ```
   
   For production (PostgreSQL):
   ```bash
   # Create database
   createdb tujulishane_db
   
   # Update application-prod.properties with your credentials
   ```

3. **Build the Application**
   ```bash
   ./gradlew clean build
   ```

4. **Run Tests**
   ```bash
   ./gradlew test
   ```

5. **Start the Application**
   
   Development mode:
   ```bash
   ./gradlew bootRun
   ```
   
   Production mode:
   ```bash
   java -jar build/libs/app.jar --spring.profiles.active=prod
   ```

6. **Verify Backend is Running**
   ```bash
   curl http://localhost:8080/api/projects
   ```

### Frontend Setup

The frontend is a static web application that can be served in multiple ways:

**Option 1: Using Backend (Recommended)**
- Place frontend files in `backend/src/main/resources/static/`
- Access via `http://localhost:8080/`

**Option 2: Using a Simple HTTP Server**
```bash
cd frontend
python3 -m http.server 8000
# Access via http://localhost:8000/
```

**Option 3: Using Live Server (VS Code)**
- Install "Live Server" extension
- Right-click `index.html` → "Open with Live Server"

### Initial System Setup

1. **Create SUPER_ADMIN User**
   ```bash
   curl -X POST http://localhost:8080/api/auth/bootstrap/super-admin \
     -H "Content-Type: application/json" \
     -d '{
       "email": "admin@moh.gov.ke",
       "name": "System Administrator",
       "secretKey": "your-bootstrap-secret-key"
     }'
   ```

2. **Verify Email (check email for OTP)**
   ```bash
   curl -X POST http://localhost:8080/api/auth/verify \
     -H "Content-Type: application/json" \
     -d '{
       "email": "admin@moh.gov.ke",
       "otp": "123456"
     }'
   ```

3. **Login**
   ```bash
   # Request OTP
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email": "admin@moh.gov.ke"}'
   
   # Verify OTP and get token
   curl -X POST http://localhost:8080/api/auth/verify/login \
     -H "Content-Type: application/json" \
     -d '{
       "email": "admin@moh.gov.ke",
       "otp": "123456"
     }'
   ```

### Docker Setup

**Build and Run with Docker Compose:**
```bash
cd backend
docker-compose up --build
```

**Build Docker Image:**
```bash
docker build -t tujulishane-hub .
```

**Run Docker Container:**
```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/tujulishane_db \
  -e SPRING_DATASOURCE_USERNAME=your_username \
  -e SPRING_DATASOURCE_PASSWORD=your_password \
  -e JWT_SECRET=your_secret \
  tujulishane-hub
```

---

## Deployment

### Render.com Deployment

The application is configured for deployment on Render.com using `render.yaml`.

**Services:**
1. **Backend Web Service**: Java application running on port $PORT
2. **PostgreSQL Database**: Managed PostgreSQL instance

**Deployment Steps:**

1. **Connect Repository to Render**
   - Go to Render.com dashboard
   - Click "New +" → "Blueprint"
   - Connect your GitHub repository

2. **Configure Environment Variables**
   Set the following in Render dashboard:
   - `JWT_SECRET` (auto-generated or custom)
   - `MAIL_USERNAME`
   - `MAIL_PASSWORD`
   - `GEOCODING_API_KEY` (optional)
   - `BOOTSTRAP_SECRET`

3. **Deploy**
   - Render automatically builds and deploys on push to main branch
   - Build command: `cd backend && ./gradlew clean bootJar -x test`
   - Start command: `cd backend && java -Dserver.port=$PORT -jar build/libs/*.jar`

4. **Access Application**
   - Backend API: `https://api-tujulishane-hub.onrender.com`
   - Frontend: Served by backend

**Health Check:**
- Endpoint: `/api/projects`
- Ensures application is responsive

### Manual Deployment (VPS/Server)

1. **Prepare Server**
   ```bash
   # Install Java 21
   sudo apt update
   sudo apt install openjdk-21-jdk
   
   # Install PostgreSQL
   sudo apt install postgresql postgresql-contrib
   ```

2. **Setup Database**
   ```bash
   sudo -u postgres createdb tujulishane_db
   sudo -u postgres createuser -P tujulishane_user
   ```

3. **Deploy Application**
   ```bash
   # Build application
   ./gradlew clean bootJar
   
   # Copy JAR to server
   scp build/libs/app.jar user@server:/opt/tujulishane-hub/
   
   # Create systemd service (optional)
   sudo nano /etc/systemd/system/tujulishane-hub.service
   ```

4. **Systemd Service Configuration**
   ```ini
   [Unit]
   Description=Tujulishane Hub Application
   After=network.target
   
   [Service]
   Type=simple
   User=tujulishane
   WorkingDirectory=/opt/tujulishane-hub
   ExecStart=/usr/bin/java -jar app.jar
   EnvironmentFile=/opt/tujulishane-hub/.env
   Restart=always
   
   [Install]
   WantedBy=multi-user.target
   ```

5. **Start Service**
   ```bash
   sudo systemctl enable tujulishane-hub
   sudo systemctl start tujulishane-hub
   sudo systemctl status tujulishane-hub
   ```

---

## API Documentation

### Accessing API Documentation

**OpenAPI/Swagger UI:**
- URL: `http://localhost:8080/swagger-ui.html`
- Interactive API documentation with test capabilities

**Detailed Documentation:**
- See [API_DOCUMENTATION.md](./API_DOCUMENTATION.md) for comprehensive endpoint documentation

### API Base URL

- **Development**: `http://localhost:8080/api`
- **Production**: `https://api-tujulishane-hub.onrender.com/api`

### API Categories

1. **Authentication** (`/api/auth`)
   - User registration, verification, login
   - OTP management
   - Profile access

2. **User Management** (`/api/auth/admin`)
   - User approval/rejection
   - User listing by status
   - Role management

3. **Organizations** (`/api/organizations`)
   - CRUD operations
   - Search and filtering
   - Approval workflow

4. **Projects** (`/api/projects`)
   - Project CRUD
   - Search and filtering
   - Geographic queries
   - Statistics

5. **Reports** (`/api/reports`)
   - Report creation and management
   - Report workflow
   - Search and filtering

### Authentication

All protected endpoints require JWT token:

```bash
Authorization: Bearer <your-jwt-token>
```

Get token via login process:
1. Request OTP: `POST /api/auth/login`
2. Verify OTP: `POST /api/auth/verify/login`
3. Use returned token in Authorization header

### Example API Calls

**Create a Project:**
```bash
curl -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "partner": "Health Partners Kenya",
    "title": "Maternal Health Initiative",
    "projectTheme": "MNH",
    "county": "Kakamega",
    "startDate": "2024-02-01",
    "endDate": "2024-12-31",
    "budget": 500000.00
  }'
```

**Get Project Statistics:**
```bash
curl http://localhost:8080/api/projects/statistics
```

**Search Projects:**
```bash
curl "http://localhost:8080/api/projects/search?county=Nairobi&status=active"
```

---

## User Roles & Permissions

### SUPER_ADMIN
**Capabilities:**
- All PARTNER capabilities
- Approve/reject users
- Approve/reject organizations
- Approve/reject projects
- Approve/reject and publish reports
- View all system data
- Access admin endpoints
- Manage system users

**Use Cases:**
- Ministry of Health officials
- System administrators
- Platform managers

### PARTNER
**Capabilities:**
- Create and manage own organizations
- Create and manage own projects
- Create and submit reports for own projects
- View published reports
- Update own profile
- Search public data

**Use Cases:**
- NGO representatives
- Healthcare providers
- International organizations
- Community groups
- Donor agencies

### Public (Unauthenticated)
**Capabilities:**
- View published projects
- View published reports
- Search projects and organizations
- View statistics
- Access map visualizations

---

## Contributing

### Development Workflow

1. **Fork the Repository**
   ```bash
   git clone https://github.com/YOUR_USERNAME/Tujulishane-Hub.git
   cd Tujulishane-Hub
   ```

2. **Create Feature Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make Changes**
   - Follow existing code style
   - Add tests for new features
   - Update documentation

4. **Test Your Changes**
   ```bash
   cd backend
   ./gradlew test
   ./gradlew build
   ```

5. **Commit Changes**
   ```bash
   git add .
   git commit -m "feat: add new feature description"
   ```

6. **Push and Create PR**
   ```bash
   git push origin feature/your-feature-name
   ```

### Code Style Guidelines

**Backend (Java):**
- Follow Java naming conventions
- Use Lombok annotations to reduce boilerplate
- Write comprehensive JavaDoc for public methods
- Use Spring Boot best practices
- Write unit tests for services

**Frontend (JavaScript):**
- Use meaningful variable names
- Comment complex logic
- Keep functions small and focused
- Use async/await for promises
- Follow existing file structure

### Testing

**Backend Tests:**
```bash
cd backend
./gradlew test                    # Run all tests
./gradlew test --tests ClassName  # Run specific test class
```

**Manual Testing:**
- Use Postman/curl for API testing
- Test in multiple browsers for frontend
- Verify email functionality in development

### Submitting Issues

When submitting issues, please include:
- Clear description of the problem
- Steps to reproduce
- Expected vs actual behavior
- Environment details (OS, Java version, browser)
- Screenshots (for UI issues)

---

## License

This project is part of the Tujulishane Hub initiative for improving healthcare project transparency in Kenya.

---

## Contact & Support

**Project Repository**: [https://github.com/sanCode-2-0/Tujulishane-Hub](https://github.com/sanCode-2-0/Tujulishane-Hub)

**API Base URL**: [https://api-tujulishane-hub.onrender.com](https://api-tujulishane-hub.onrender.com)

For questions, issues, or contributions, please open an issue on GitHub.

---

## Acknowledgments

This platform was developed to support the Reproductive, Maternal, Newborn, Child, and Adolescent Health (RMNCAH) initiatives in Kenya, enabling better coordination and transparency in healthcare projects across the nation.
