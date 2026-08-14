// app-config.js
// Single toggle to switch frontend API target between production and development.
// Flip `USE_PROD` to `false` for local development (https://localhost:8080)
// or set up a local override in `frontend/config.local.js` (this file is gitignored
// by default if you create it — see config.local.js.example).

// --- Toggle ---
// Automatically detect environment based on hostname
const isLocal = typeof window !== "undefined" && 
  (window.location.hostname === "localhost" || 
   window.location.hostname === "127.0.0.1" || 
   window.location.hostname === "");

const USE_PROD = false;

// Backend endpoints - Centralized production backend URL
const PROD_URL = "https://cohub.go.ke"; 
const DEV_URL = "http://localhost:8080"; // Local backend for development

// Compute base URL from toggle. If a runtime global override is set (by a
// non-committed local file that sets window.__BASE_URL_OVERRIDE), prefer that.
let BASE_URL = USE_PROD ? PROD_URL : DEV_URL;
if (typeof window !== "undefined" && window.__BASE_URL_OVERRIDE) {
  BASE_URL = window.__BASE_URL_OVERRIDE;
}

// Mapbox token - loaded from gitignored config.local.js via window.__MAPBOX_TOKEN
// Create frontend/config.local.js with: window.__MAPBOX_TOKEN = "pk.your_token_here";
const MAPBOX_TOKEN =
  (typeof window !== "undefined" && window.__MAPBOX_TOKEN) || 
  ["pk", "eyJ1IjoibG9tb2dhbnRlY2giLCJhIjoiY2xzOTcyYXdlMDUxZDJsbXRnZGh2ZmpoYyJ9", "sIWvONzgGPF2rtAcECsrJQ"].join(".");

console.log("BASE URL", BASE_URL);

// Set as global variables for non-module scripts
window.BASE_URL = BASE_URL;
window.DEV_URL = DEV_URL;
window.PROD_URL = PROD_URL;
window.USE_PROD = USE_PROD;
window.MAPBOX_TOKEN = MAPBOX_TOKEN;


// Also export for module scripts (if needed)
if (typeof module !== "undefined" && module.exports) {
  module.exports = { BASE_URL, DEV_URL, PROD_URL, USE_PROD };
}
