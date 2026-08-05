(function() {
    let fontSizePercent = 100;

    function applySavedSettings() {
        const highContrast = localStorage.getItem('accessibility-high-contrast') === 'true';
        const negativeContrast = localStorage.getItem('accessibility-negative-contrast') === 'true';
        const grayscale = localStorage.getItem('accessibility-grayscale') === 'true';
        const fontSize = localStorage.getItem('accessibility-font-size');
        
        if (fontSize) {
            fontSizePercent = parseInt(fontSize, 10);
            document.documentElement.style.fontSize = `${fontSizePercent}%`;
        } else {
            document.documentElement.style.fontSize = '';
        }
        
        document.documentElement.classList.toggle('high-contrast-active', highContrast);
        document.documentElement.classList.toggle('negative-contrast-active', negativeContrast);
        document.documentElement.classList.toggle('grayscale-active', grayscale);
        
        updateInputs(highContrast, negativeContrast, grayscale);
    }

    function updateInputs(highContrast, negativeContrast, grayscale) {
        const hcBtn = document.getElementById('high-contrast-btn');
        const ncBtn = document.getElementById('negative-contrast-btn');
        const gsBtn = document.getElementById('grayscale-btn');
        
        if (hcBtn) hcBtn.checked = highContrast;
        if (ncBtn) ncBtn.checked = negativeContrast;
        if (gsBtn) gsBtn.checked = grayscale;
    }

    function bindEvents() {
        const hcBtn = document.getElementById('high-contrast-btn');
        const ncBtn = document.getElementById('negative-contrast-btn');
        const gsBtn = document.getElementById('grayscale-btn');
        const incBtn = document.getElementById('increaseBtn');
        const decBtn = document.getElementById('decreaseBtn');

        if (hcBtn) {
            hcBtn.replaceWith(hcBtn.cloneNode(true));
            const newHcBtn = document.getElementById('high-contrast-btn');
            newHcBtn.checked = localStorage.getItem('accessibility-high-contrast') === 'true';
            newHcBtn.addEventListener('change', (e) => {
                const active = e.target.checked;
                document.documentElement.classList.toggle('high-contrast-active', active);
                localStorage.setItem('accessibility-high-contrast', active);
                if (active) {
                    localStorage.setItem('accessibility-negative-contrast', 'false');
                    document.documentElement.classList.remove('negative-contrast-active');
                    const other = document.getElementById('negative-contrast-btn');
                    if (other) other.checked = false;
                }
            });
        }

        if (ncBtn) {
            ncBtn.replaceWith(ncBtn.cloneNode(true));
            const newNcBtn = document.getElementById('negative-contrast-btn');
            newNcBtn.checked = localStorage.getItem('accessibility-negative-contrast') === 'true';
            newNcBtn.addEventListener('change', (e) => {
                const active = e.target.checked;
                document.documentElement.classList.toggle('negative-contrast-active', active);
                localStorage.setItem('accessibility-negative-contrast', active);
                if (active) {
                    localStorage.setItem('accessibility-high-contrast', 'false');
                    document.documentElement.classList.remove('high-contrast-active');
                    const other = document.getElementById('high-contrast-btn');
                    if (other) other.checked = false;
                }
            });
        }

        if (gsBtn) {
            gsBtn.replaceWith(gsBtn.cloneNode(true));
            const newGsBtn = document.getElementById('grayscale-btn');
            newGsBtn.checked = localStorage.getItem('accessibility-grayscale') === 'true';
            newGsBtn.addEventListener('change', (e) => {
                const active = e.target.checked;
                document.documentElement.classList.toggle('grayscale-active', active);
                localStorage.setItem('accessibility-grayscale', active);
            });
        }

        if (incBtn) {
            incBtn.replaceWith(incBtn.cloneNode(true));
            const newIncBtn = document.getElementById('increaseBtn');
            newIncBtn.addEventListener('click', () => {
                if (fontSizePercent < 150) {
                    fontSizePercent += 10;
                    document.documentElement.style.fontSize = `${fontSizePercent}%`;
                    localStorage.setItem('accessibility-font-size', fontSizePercent);
                }
            });
        }

        if (decBtn) {
            decBtn.replaceWith(decBtn.cloneNode(true));
            const newDecBtn = document.getElementById('decreaseBtn');
            newDecBtn.addEventListener('click', () => {
                if (fontSizePercent > 80) {
                    fontSizePercent -= 10;
                    document.documentElement.style.fontSize = `${fontSizePercent}%`;
                    localStorage.setItem('accessibility-font-size', fontSizePercent);
                }
            });
        }
    }

    // Run once on load to apply settings saved in localStorage
    applySavedSettings();

    // Re-bind events whenever navigation is loaded
    window.addEventListener('navLoaded', () => {
        applySavedSettings();
        bindEvents();
    });
})();
