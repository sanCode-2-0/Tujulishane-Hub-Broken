// Populate user dropdown with data from authManager
// Listens for "navLoaded" event (dispatched by loadNav.js after nav HTML is injected)

function populateUserNav() {
  const user = authManager.getCachedUser();
  console.log("[authnav] Loading user data for dropdown:", user);

  if (!user) {
    console.warn("[authnav] No user data found in cache");
    return;
  }

  // --------------------------
  // Populate user info
  // --------------------------
  document.querySelectorAll("#userDisplayName").forEach(el => el.textContent = user.name || user.email || "User");
  document.querySelectorAll("#userNameDisplay").forEach(el => el.textContent = user.name || "User");
  document.querySelectorAll("#userEmailDisplay").forEach(el => el.textContent = user.email || "");
  document.querySelectorAll("#userRoleDisplay").forEach(el => el.textContent = user.role || "USER");
  document.querySelectorAll("#userStatusDisplay").forEach(el => el.textContent = user.approvalStatus || "PENDING");

  // --------------------------
  // Role-based navigation logic
  // --------------------------
  console.log("[authnav] User role:", user.role);

  // ADMIN and SUPER_ADMIN (use same nav)
  if (user.role === "ADMIN" || user.role === "SUPER_ADMIN") {
    document.querySelectorAll("#adminNav").forEach(nav => nav.classList.remove("hidden"));
    if (typeof loadPendingRequestsCount === "function") loadPendingRequestsCount();
  }

  // SUPER_ADMIN_REVIEWER
  else if (user.role === "SUPER_ADMIN_REVIEWER") {
    document.querySelectorAll("#adminNav").forEach(nav => nav.classList.remove("hidden"));
    if (typeof loadPendingRequestsCount === "function") loadPendingRequestsCount();
    document.querySelectorAll(".approval-only").forEach(btn => btn.style.display = "none");
  }

  // SUPER_ADMIN_APPROVER
  else if (user.role === "SUPER_ADMIN_APPROVER") {
    document.querySelectorAll("#adminNav").forEach(nav => nav.classList.remove("hidden"));
    if (typeof loadPendingRequestsCount === "function") loadPendingRequestsCount();
    const approvalPanel = document.getElementById("approval-panel");
    if (approvalPanel) approvalPanel.style.display = "block";
  }

  // DONOR
  else if (user.role === "DONOR") {
    document.querySelectorAll("#donorNav").forEach(nav => nav.classList.remove("hidden"));
    
    // Show top-level Donor Management links
    const topNavDM = document.getElementById("topNavDonorManagement");
    if (topNavDM) topNavDM.classList.remove("hidden");
    const mTopNavDM = document.getElementById("mTopNavDonorManagement");
    if (mTopNavDM) mTopNavDM.classList.remove("hidden");
    
    // Hide Projects dropdown completely
    const navProjectsDropdown = document.getElementById("navProjectsDropdown");
    if (navProjectsDropdown) navProjectsDropdown.style.display = "none";
    const mNavProjectsDropdown = document.getElementById("mNavProjectsDropdown");
    if (mNavProjectsDropdown) mNavProjectsDropdown.style.display = "none";
    
    // Restrict navigation options for Donor role
    document.querySelectorAll('a[href="new-project.html"]').forEach(el => el.parentElement.style.display = "none");
    document.querySelectorAll('a[href="my-projects.html"]').forEach(el => el.parentElement.style.display = "none");
    document.querySelectorAll('a[href="my-collaborations.html"]').forEach(el => el.parentElement.style.display = "none");
    document.querySelectorAll('a[href="announcements.html"]').forEach(el => el.parentElement.style.display = "none");
  }

  // --------------------------
  // Common admin-type elements
  // --------------------------
  if (["ADMIN", "SUPER_ADMIN", "SUPER_ADMIN_REVIEWER", "SUPER_ADMIN_APPROVER"].includes(user.role)) {
    const adminCollabItem = document.getElementById("admin-collab-item");
    if (adminCollabItem) adminCollabItem.style.display = "block";

    const adminCollabMobile = document.getElementById("admin-collab-mobile");
    if (adminCollabMobile) adminCollabMobile.style.display = "block";
  }
}

// Run when nav is loaded (fired by loadNav.js)
window.addEventListener("navLoaded", populateUserNav);

// Also run on DOMContentLoaded as fallback (for pages with inline nav)
document.addEventListener("DOMContentLoaded", () => {
  // Only run if nav-placeholder already has content (inline nav or nav already loaded)
  const placeholder = document.getElementById("nav-placeholder");
  if (placeholder && placeholder.children.length > 0) {
    populateUserNav();
  }
});

// Dropdown toggle function for navigation menus
window.navDrop = function(id) {
  console.log("[authnav] navDrop called for:", id);
  const target = document.getElementById(id);
  console.log("[authnav] target exists in DOM:", !!target);
  
  const placeholder = document.getElementById("nav-placeholder");
  if (placeholder) {
    console.log("[authnav] nav-placeholder HTML has 'm-dd-user' string?", placeholder.innerHTML.includes("m-dd-user"));
    console.log("[authnav] nav-placeholder HTML has 'id=\"m-dd-user\"'?", placeholder.innerHTML.includes('id="m-dd-user"'));
    console.log("[authnav] nav-placeholder HTML has '<div id=\"m-dd-user\"'?", placeholder.innerHTML.includes('<div id="m-dd-user"'));
    console.log("[authnav] nav-placeholder HTML has 'm-dd-admin' string?", placeholder.innerHTML.includes("m-dd-admin"));
    console.log("[authnav] nav-placeholder HTML has 'id=\"m-dd-admin\"'?", placeholder.innerHTML.includes('id="m-dd-admin"'));
    
    const adminEl = document.getElementById("m-dd-admin");
    if (adminEl && adminEl.parentElement && adminEl.parentElement.parentElement) {
      console.log("[authnav] Mobile menu UL innerHTML:", adminEl.parentElement.parentElement.innerHTML);
    } else {
      console.log("[authnav] Mobile admin parent elements not found");
    }
  }
  
  console.log("[authnav] All dropdown IDs currently in DOM:", Array.from(document.querySelectorAll('.nav-dropdown')).map(el => el.id));
  
  document.querySelectorAll('.nav-dropdown').forEach(function(el) {
    if (el.id !== id) el.classList.add('hidden');
  });
  
  if (target) {
    target.classList.toggle('hidden');
    console.log("[authnav] Dropdown target toggled:", id);
  } else {
    console.warn("[authnav] Dropdown target not found:", id);
  }
};

// Global click listener to close dropdowns when clicking outside
document.addEventListener('click', function(e) {
  if (!e.target.closest('nav') && !e.target.closest('header')) {
    document.querySelectorAll('.nav-dropdown').forEach(function(el) {
      el.classList.add('hidden');
    });
  }
});
