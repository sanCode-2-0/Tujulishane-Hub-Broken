document.addEventListener("DOMContentLoaded", () => {
    console.log("Loading navigation...");
    fetch("nav-v4.html?v=" + Date.now())
        .then((response) => {
            console.log("Navigation fetch response:", response.status);
            if (!response.ok) throw new Error("Failed to load navigation");
            return response.text();
        })
        .then((data) => {
            console.log("Navigation HTML loaded, length:", data.length);
            console.log("[loadNav] HTML string contains m-dd-user?", data.includes('id="m-dd-user"'));
            console.log("[loadNav] HTML string contains m-dd-admin?", data.includes('id="m-dd-admin"'));
            const placeholder = document.getElementById("nav-placeholder");
            placeholder.innerHTML = data;
            console.log("[loadNav] Immediately after innerHTML assignment:");
            console.log("[loadNav] - DOM has m-dd-user?", !!document.getElementById("m-dd-user"));
            console.log("[loadNav] - DOM has m-dd-admin?", !!document.getElementById("m-dd-admin"));
            
            // Re-execute any <script> tags injected with the nav HTML BEFORE Alpine initializes
            placeholder.querySelectorAll("script").forEach((s) => {
                const script = document.createElement("script");
                if (s.src) {
                    script.src = s.src;
                    if (s.defer) script.defer = true;
                    if (s.async) script.async = true;
                } else {
                    script.textContent = s.textContent;
                }
                document.head.appendChild(script);
                s.remove();
            });
            console.log("Navigation inserted into DOM and scripts re-executed");

            // Initialize Alpine.js on the clean DOM tree
            if (window.Alpine) {
                console.log("Alpine.js found, initializing tree");
                window.Alpine.initTree(placeholder);
            } else {
                console.log("Alpine.js not found");
            }

            // Trigger navigation loaded event
            window.dispatchEvent(new CustomEvent("navLoaded"));
            console.log("Navigation loaded event dispatched");
        })
        .catch((error) => console.error("Error loading navigation:", error));
});
