/**
 * Authentication utilities for Tujulishane Hub frontend
 * Handles JWT token management, API calls with authentication, and user session
 *
 * BASE_URL is configured centrally in app-config.js - change it there to switch between PROD/DEV
 */

const AUTH_CONFIG = {
  TOKEN_KEY: "accessToken",
  USER_KEY: "currentUser",
};

class AuthManager {
  constructor() {
    // Use the centralized BASE_URL from app-config.js (loaded via window.BASE_URL)
    this.baseUrl =
      window.BASE_URL;
    this.tokenKey = AUTH_CONFIG.TOKEN_KEY;
    this.userKey = AUTH_CONFIG.USER_KEY;

    // Log which backend we're using for debugging
    console.log("AuthManager initialized with backend:", this.baseUrl);
  }

  /**
   * Get stored JWT token
   * @returns {string|null} JWT token or null if not found
   */
  getToken() {
    const token = localStorage.getItem(this.tokenKey);
    console.log("[auth.js] getToken:", token);
    return token;
  }

  /**
   * Store JWT token
   * @param {string} token - JWT token to store
   * @param {boolean} rememberMe - Whether to enable Remember Me
   */
  setToken(token, rememberMe = false) {
    console.log("[auth.js] setToken: storing token", token);
    localStorage.setItem(this.tokenKey, token);
    const stored = localStorage.getItem(this.tokenKey);
    console.log("[auth.js] setToken: token in localStorage now =", stored);
    
    // Initialize session manager if available
    if (window.sessionManager) {
      window.sessionManager.extendSession(rememberMe);
      window.sessionManager.init();
    }
  }

  /**
   * Remove JWT token from storage
   */
  removeToken() {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.userKey);
    
    // Clean up session data if SESSION_CONFIG is available
    if (window.SESSION_CONFIG) {
      localStorage.removeItem(window.SESSION_CONFIG.KEYS.LOGIN_TIME);
      localStorage.removeItem(window.SESSION_CONFIG.KEYS.LAST_ACTIVITY);
      localStorage.removeItem(window.SESSION_CONFIG.KEYS.SESSION_TIMEOUT);
      localStorage.removeItem(window.SESSION_CONFIG.KEYS.REMEMBER_ME);
    }
    
    // Clean up session manager if available
    if (window.sessionManager) {
      window.sessionManager.cleanup();
    }
  }

  /**
   * Check if user is authenticated
   * @returns {boolean} True if token exists
   */
  isAuthenticated() {
    const token = this.getToken();
    console.log("[auth.js] isAuthenticated: token =", token);
    if (!token) return false;
    // Basic check for token format (should be JWT with 3 parts)
    const parts = token.split(".");
    const valid = parts.length === 3;
    console.log("[auth.js] isAuthenticated: valid =", valid);
    return valid;
  }

  /**
   * Get current user profile from API
   * @returns {Promise<Object>} User profile data
   */
  async getCurrentUser() {
    console.log("[auth.js] getCurrentUser: called");
    try {
      const response = await this.apiCall("/api/auth/profile", "GET");
      console.log("[auth.js] getCurrentUser: response.ok =", response.ok);
      if (response.ok) {
        const data = await response.json();
        console.log("[auth.js] getCurrentUser: received data", data);
        localStorage.setItem(this.userKey, JSON.stringify(data.data));
        // Store user role separately for easy access
        if (data.data && data.data.role) {
          localStorage.setItem('userRole', data.data.role);
          console.log("[auth.js] getCurrentUser: userRole set to", data.data.role);
        }
        const storedUser = localStorage.getItem(this.userKey);
        console.log(
          "[auth.js] getCurrentUser: user in localStorage now =",
          storedUser
        );
        return data.data;
      }
      throw new Error("Failed to get user profile");
    } catch (error) {
      console.error("Error getting current user:", error);
      throw error;
    }
  }

  /**
   * Get cached user data from localStorage
   * @returns {Object|null} User data or null if not cached
   */
  getCachedUser() {
    const userData = localStorage.getItem(this.userKey);
    return userData ? JSON.parse(userData) : null;
  }

  /**
   * Make authenticated API call
   * @param {string} endpoint - API endpoint (e.g., '/api/projects')
   * @param {string} method - HTTP method ('GET', 'POST', 'PUT', 'DELETE')
   * @param {Object} data - Request body data (for POST/PUT) or FormData object
   * @param {Object} options - Additional options { isFormData: true, headers: {...} }
   * @returns {Promise<Response>} Fetch response
   */
  async apiCall(endpoint, method = "GET", data = null, options = {}) {
    const url = `${this.baseUrl}${endpoint}`;
    const token = this.getToken();
    const isFormData = options.isFormData || false;
    const additionalHeaders = options.headers || {};

    // Enhanced browser fingerprinting for debugging
    const browserInfo = {
      userAgent: navigator.userAgent,
      language: navigator.language,
      platform: navigator.platform,
      cookieEnabled: navigator.cookieEnabled,
      onLine: navigator.onLine,
      doNotTrack: navigator.doNotTrack,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      timestamp: new Date().toISOString(),
      url: window.location.href,
      referrer: document.referrer,
      screen: `${screen.width}x${screen.height}`,
      viewport: `${window.innerWidth}x${window.innerHeight}`,
      localStorageAvailable: this.isLocalStorageAvailable(),
      sessionStorageAvailable: this.isSessionStorageAvailable()
    };

    console.log(`[auth.js] apiCall: Browser environment:`, browserInfo);
    console.log(`[auth.js] apiCall: Request origin: ${window.location.origin}`);
    console.log(`[auth.js] apiCall: Target URL: ${url}`);

    const config = {
      method,
      headers: {
        ...additionalHeaders,
      },
    };

    // Only set Content-Type for non-FormData requests
    if (!isFormData) {
      config.headers["Content-Type"] = "application/json";
    }

    // Add authorization header if token exists
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Add body for POST/PUT requests
    if (data && (method === "POST" || method === "PUT")) {
      if (isFormData) {
        // FormData should not be stringified, and Content-Type should not be set
        config.body = data;
      } else {
        config.body = JSON.stringify(data);
      }
    }

    const startTime = performance.now();

    try {
      console.log(`[auth.js] apiCall: Making ${method} request to ${url}`);
      console.log(`[auth.js] apiCall: Full request config:`, {
        method: config.method,
        headers: config.headers,
        bodySize: config.body ? config.body.length : 0,
        url: url
      });
      
      // Log outgoing payload for debugging (if present)
      if (config.body) {
        try {
          console.log(
            `[auth.js] apiCall: Outgoing request body:`,
            JSON.parse(config.body)
          );
        } catch (e) {
          console.log(
            `[auth.js] apiCall: Outgoing request body (raw):`,
            config.body
          );
        }
      }
      console.log(`[auth.js] apiCall: Request headers:`, config.headers);
      
      const response = await fetch(url, config);
      const endTime = performance.now();
      const duration = endTime - startTime;
      
      console.log(
        `[auth.js] apiCall: Response received - Status: ${response.status}, OK: ${response.ok}, Duration: ${duration.toFixed(2)}ms`
      );

      // Log to network debugger if available
      if (window.networkDebugger) {
        window.networkDebugger.logRequest(
          `apiCall-${method}`,
          { ...config, url },
          response,
          duration
        );
      }

      // Log response headers for debugging
      console.log(`[auth.js] apiCall: Response headers:`, {
        'content-type': response.headers.get('content-type'),
        'access-control-allow-origin': response.headers.get('access-control-allow-origin'),
        'access-control-allow-credentials': response.headers.get('access-control-allow-credentials'),
        'access-control-allow-headers': response.headers.get('access-control-allow-headers'),
        'access-control-allow-methods': response.headers.get('access-control-allow-methods'),
        'content-length': response.headers.get('content-length'),
        'server': response.headers.get('server'),
        'date': response.headers.get('date')
      });

      // If unauthorized, redirect to login
      if (response.status === 401) {
        console.log("[auth.js] apiCall: Unauthorized, logging out");
        this.logout();
        // window.location.href = "/frontend/index.html";
        return response;
      }

      // Log response details for debugging
      if (!response.ok) {
        console.log(`[auth.js] apiCall: Error response details:`, {
          status: response.status,
          statusText: response.statusText,
          url: url,
          duration: `${duration.toFixed(2)}ms`,
        });
        try {
          // Clone response, read and store the body text for later use
          const errorText = await response.clone().text();
          console.log(`[auth.js] apiCall: Error response body:`, errorText);
          // Attach the raw error text to the Response object
        } catch (parseError) {
          console.warn("[auth.js] apiCall: Could not read error response body:", parseError);
        }
      }

      return response;
    } catch (error) {
      const endTime = performance.now();
      const duration = endTime - startTime;
      console.error(`[auth.js] apiCall: Network or fetch error after ${duration.toFixed(2)}ms:`, error);
      console.error(`[auth.js] apiCall: Error details:`, {
        name: error.name,
        message: error.message,
        stack: error.stack,
        url: url,
        method: method,
        browserInfo: browserInfo
      });

      // Log error to network debugger if available
      if (window.networkDebugger) {
        window.networkDebugger.logError(
          `apiCall-${method}`,
          { ...config, url },
          error,
          duration
        );
      }

      throw error;
    }
  }

  /**
   * Check if localStorage is available
   * @returns {boolean}
   */
  isLocalStorageAvailable() {
    try {
      const test = '__test__';
      localStorage.setItem(test, test);
      localStorage.removeItem(test);
      return true;
    } catch (e) {
      return false;
    }
  }

  /**
   * Check if sessionStorage is available
   * @returns {boolean}
   */
  isSessionStorageAvailable() {
    try {
      const test = '__test__';
      sessionStorage.setItem(test, test);
      sessionStorage.removeItem(test);
      return true;
    } catch (e) {
      return false;
    }
  }

  /**
   * Logout user and clear session data
   * @param {string} message - Optional logout message
   */
  logout(message = null) {
    // Store logout message in sessionStorage if provided
    if (message) {
      sessionStorage.setItem('logoutMessage', message);
    }
    
    this.removeToken();
    
    // Redirect to frontend index in a way that works when the site is served
    // from a repo subpath (e.g. GitHub Pages: /<owner>/<repo>/frontend)
    const frontendSegment = "/frontend";
    const pathname = window.location.pathname;
    const idx = pathname.indexOf(frontendSegment);
    if (idx !== -1) {
      // build origin + up to /frontend then append index.html
      window.location.href =
        window.location.origin +
        pathname.slice(0, idx + frontendSegment.length) +
        "/index.html";
    } else {
      // fallback: go to index.html in the current directory (or site root)
      const baseDir = pathname.endsWith("/")
        ? pathname
        : pathname.substring(0, pathname.lastIndexOf("/") + 1);
      window.location.href = window.location.origin + baseDir + "index.html";
    }
  }

  /**
   * Check if user has required role
   * @param {string} requiredRole - Required role ('SUPER_ADMIN', 'PARTNER')
   * @returns {boolean} True if user has required role
   */
  hasRole(requiredRole) {
    const user = this.getCachedUser();
    return user && user.role === requiredRole;
  }

  /**
   * Check if user is approved
   * @returns {boolean} True if user is approved
   */
  isApproved() {
    const user = this.getCachedUser();
    return user && user.approvalStatus === "APPROVED";
  }

  checkPageRestrictions() {
    const user = this.getCachedUser();
    if (user && user.role === "DONOR") {
      const restrictedPages = ["new-project.html", "my-projects.html", "past-projects.html", "projects.html"];
      const pathname = window.location.pathname;
      const currentPage = pathname.substring(pathname.lastIndexOf("/") + 1);
      if (restrictedPages.includes(currentPage)) {
        console.warn("[Security] Donors are restricted from accessing: " + currentPage);
        alert("Access Denied: Donors are not allowed to access this workspace.");
        if (document.referrer && document.referrer !== window.location.href) {
          window.location.href = document.referrer;
        } else {
          window.location.href = "dashboard.html";
        }
      }
    }
  }

  /**
   * Redirect to login if not authenticated
   */
  requireAuth() {
    if (!this.isAuthenticated()) {
      console.error("[auth.js] requireAuth: Not authenticated!");
      // Show error message on page instead of redirecting
      let msg = document.getElementById("authErrorMsg");
      if (!msg) {
        msg = document.createElement("div");
        msg.id = "authErrorMsg";
        msg.style =
          "color: red; font-weight: bold; margin: 2em; text-align: center;";
        msg.textContent =
          "You must be logged in to access this page. (See console for details)";
        document.body.prepend(msg);
      }
      return false;
    }
    return true;
  }

  /**
   * Initialize authentication on page load
   * Checks if user is authenticated and loads user data
   */
  async init() {
    this.checkPageRestrictions();
    if (this.isAuthenticated()) {
      try {
        const user = await this.getCurrentUser();
        console.log("[auth.js] init: user loaded", user);
        this.checkPageRestrictions();
      } catch (error) {
        console.error("Failed to load user data:", error);
        this.checkPageRestrictions();
      }
    } else {
      console.log("[auth.js] init: not authenticated, skipping getCurrentUser");
    }
  }
}

// Create global instance
const authManager = new AuthManager();

// Auto-initialize on DOM load
document.addEventListener("DOMContentLoaded", async () => {
  await authManager.init();
});

// Export for use in other scripts
window.authManager = authManager;
