// set-base-url.js
// Validates that BASE_URL is properly set from app-config.js
(function () {
  if (typeof window.BASE_URL === "undefined" || window.BASE_URL === "") {
    // Emergency fallback - should not normally be reached if app-config.js loads first
    console.error(
      "❌ BASE_URL not found! Make sure app-config.js is loaded before set-base-url.js"
    );
    window.BASE_URL =
      "https://cohub.go.ke";
    console.warn("⚠️ Using emergency fallback URL:", window.BASE_URL);
  } else {
    console.log("✓ BASE_URL loaded from app-config.js:", window.BASE_URL);
    console.log(
      "  Environment:",
      window.USE_PROD ? "PRODUCTION" : "DEVELOPMENT"
    );
  }
})();
