// SplitFriend JavaScript

// PWA Install Prompt
let deferredPrompt;

window.addEventListener('beforeinstallprompt', function(e) {
    console.log('beforeinstallprompt fired');
    e.preventDefault();
    deferredPrompt = e;
    // Show install button if it exists
    var installBtn = document.getElementById('pwa-install-btn');
    if (installBtn) {
        installBtn.style.display = 'block';
    }
    // Show toast notification
    showInstallToast();
});

window.addEventListener('appinstalled', function() {
    console.log('PWA was installed');
    deferredPrompt = null;
    var installBtn = document.getElementById('pwa-install-btn');
    if (installBtn) {
        installBtn.style.display = 'none';
    }
});

function installPWA() {
    if (!deferredPrompt) {
        // Show manual instructions
        showManualInstallInstructions();
        return;
    }
    deferredPrompt.prompt();
    deferredPrompt.userChoice.then(function(choiceResult) {
        if (choiceResult.outcome === 'accepted') {
            console.log('User accepted the install prompt');
        }
        deferredPrompt = null;
    });
}

function showInstallToast() {
    var toast = document.createElement('div');
    toast.className = 'toast-container position-fixed bottom-0 start-50 translate-middle-x p-3';
    toast.style.zIndex = '1100';
    toast.innerHTML =
        '<div class="toast show align-items-center text-white bg-dark border-0" role="alert">' +
        '  <div class="d-flex">' +
        '    <div class="toast-body d-flex align-items-center">' +
        '      <i class="bi bi-download me-2"></i> Install SplitFriend app?' +
        '      <button class="btn btn-sm btn-success ms-3" onclick="installPWA(); this.closest(\'.toast-container\').remove();">Install</button>' +
        '    </div>' +
        '    <button type="button" class="btn-close btn-close-white me-2 m-auto" onclick="this.closest(\'.toast-container\').remove();"></button>' +
        '  </div>' +
        '</div>';
    document.body.appendChild(toast);
}

function showManualInstallInstructions() {
    var isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent);
    var isAndroid = /Android/.test(navigator.userAgent);
    var message = '';

    if (isIOS) {
        message = 'To install: tap the Share button, then "Add to Home Screen"';
    } else if (isAndroid) {
        message = 'To install: tap the menu (3 dots), then "Add to Home Screen" or "Install app"';
    } else {
        message = 'To install: look for the install icon in your browser\'s address bar, or use browser menu';
    }

    showToast(message);
}

// Register Service Worker for PWA
if ('serviceWorker' in navigator) {
    window.addEventListener('load', function() {
        navigator.serviceWorker.register('/sw.js')
            .then(function(registration) {
                console.log('ServiceWorker registered with scope:', registration.scope);
            })
            .catch(function(error) {
                console.log('ServiceWorker registration failed:', error);
            });
    });
}

document.addEventListener('DOMContentLoaded', function() {
    // Initialize theme
    initializeTheme();

    // Initialize Bootstrap tooltips
    var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });

    // Auto-dismiss alerts after 5 seconds
    var alerts = document.querySelectorAll('.alert:not(.alert-permanent)');
    alerts.forEach(function(alert) {
        setTimeout(function() {
            var bsAlert = new bootstrap.Alert(alert);
            bsAlert.close();
        }, 5000);
    });

    // Confirm delete actions
    document.querySelectorAll('[data-confirm]').forEach(function(element) {
        element.addEventListener('click', function(e) {
            if (!confirm(this.dataset.confirm)) {
                e.preventDefault();
            }
        });
    });

    // Form validation styling
    var forms = document.querySelectorAll('.needs-validation');
    forms.forEach(function(form) {
        form.addEventListener('submit', function(event) {
            if (!form.checkValidity()) {
                event.preventDefault();
                event.stopPropagation();
            }
            form.classList.add('was-validated');
        });
    });

    // Split type change handler for expense form
    var splitTypeSelect = document.getElementById('splitType');
    if (splitTypeSelect) {
        splitTypeSelect.addEventListener('change', updateSplitUI);
    }

    // Amount input formatting
    var amountInputs = document.querySelectorAll('input[type="number"][step="0.01"]');
    amountInputs.forEach(function(input) {
        input.addEventListener('blur', function() {
            if (this.value) {
                this.value = parseFloat(this.value).toFixed(2);
            }
        });
    });

    // Participant checkbox handler
    var participantCheckboxes = document.querySelectorAll('input[name="participantIds"]');
    participantCheckboxes.forEach(function(checkbox) {
        checkbox.addEventListener('change', function() {
            var row = this.closest('.participant-row');
            var inputs = row.querySelectorAll('.split-input-container input');
            inputs.forEach(function(input) {
                input.disabled = !checkbox.checked;
                if (!checkbox.checked) {
                    input.value = '';
                }
            });
        });
    });
});

// ===== THEME MANAGEMENT =====

function initializeTheme() {
    var currentTheme = localStorage.getItem('splitfriend-theme') || 'theme-emerald';
    document.documentElement.setAttribute('data-theme', currentTheme);
    updateThemeSelection(currentTheme);

    // Add click handlers to theme options
    document.querySelectorAll('.theme-option').forEach(function(option) {
        option.addEventListener('click', function(e) {
            e.preventDefault();
            var theme = this.getAttribute('data-theme');
            setTheme(theme);
        });
    });
}

function setTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('splitfriend-theme', theme);
    updateThemeSelection(theme);
    showToast('Theme changed successfully!');
}

function updateThemeSelection(currentTheme) {
    document.querySelectorAll('.theme-option').forEach(function(option) {
        option.classList.remove('active');
        if (option.getAttribute('data-theme') === currentTheme) {
            option.classList.add('active');
        }
    });
}

// ===== SPLIT UI MANAGEMENT =====

function updateSplitUI() {
    var splitType = document.getElementById('splitType').value;
    var containers = document.querySelectorAll('.split-input-container');
    var exactInputs = document.querySelectorAll('.exact-input');
    var percentageInputs = document.querySelectorAll('.percentage-input');
    var sharesInputs = document.querySelectorAll('.shares-input');

    // Hide all first
    containers.forEach(function(c) { c.style.display = 'none'; });
    exactInputs.forEach(function(i) { i.style.display = 'none'; });
    percentageInputs.forEach(function(i) { i.style.display = 'none'; });
    sharesInputs.forEach(function(i) { i.style.display = 'none'; });

    if (splitType === 'EQUAL') {
        return;
    }

    containers.forEach(function(c) { c.style.display = 'block'; });

    if (splitType === 'EXACT') {
        exactInputs.forEach(function(i) { i.style.display = 'block'; });
    } else if (splitType === 'PERCENTAGE') {
        percentageInputs.forEach(function(i) { i.style.display = 'block'; });
    } else if (splitType === 'SHARES') {
        sharesInputs.forEach(function(i) { i.style.display = 'block'; });
    }
}

// Calculate and display totals for split validation
function validateSplitTotals() {
    var splitType = document.getElementById('splitType').value;
    var totalAmount = parseFloat(document.getElementById('amount').value) || 0;

    if (splitType === 'EXACT') {
        var exactTotal = 0;
        document.querySelectorAll('.exact-input input').forEach(function(input) {
            if (!input.disabled) {
                exactTotal += parseFloat(input.value) || 0;
            }
        });
        if (Math.abs(exactTotal - totalAmount) > 0.01) {
            alert('Exact amounts must sum to ' + totalAmount.toFixed(2) + '. Current total: ' + exactTotal.toFixed(2));
            return false;
        }
    } else if (splitType === 'PERCENTAGE') {
        var percentTotal = 0;
        document.querySelectorAll('.percentage-input input').forEach(function(input) {
            if (!input.disabled) {
                percentTotal += parseFloat(input.value) || 0;
            }
        });
        if (Math.abs(percentTotal - 100) > 0.01) {
            alert('Percentages must sum to 100%. Current total: ' + percentTotal.toFixed(2) + '%');
            return false;
        }
    }

    return true;
}

// ===== UTILITY FUNCTIONS =====

// Copy to clipboard functionality
function copyToClipboard(text) {
    navigator.clipboard.writeText(text).then(function() {
        showToast('Copied to clipboard!');
    }).catch(function(err) {
        console.error('Failed to copy: ', err);
    });
}

// Show toast notification
function showToast(message) {
    var existingToast = document.querySelector('.toast-container');
    if (existingToast) {
        existingToast.remove();
    }

    var toast = document.createElement('div');
    toast.className = 'toast-container position-fixed bottom-0 end-0 p-3';
    toast.style.zIndex = '1100';
    toast.innerHTML =
        '<div class="toast show align-items-center text-white bg-dark border-0" role="alert">' +
        '  <div class="d-flex">' +
        '    <div class="toast-body d-flex align-items-center">' +
        '      <i class="bi bi-check-circle me-2"></i>' + message +
        '    </div>' +
        '    <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>' +
        '  </div>' +
        '</div>';
    document.body.appendChild(toast);

    setTimeout(function() {
        toast.remove();
    }, 3000);
}

// Format currency for display
function formatCurrency(amount, currency) {
    currency = currency || 'USD';
    return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: currency
    }).format(amount);
}

// Animate number change
function animateValue(element, start, end, duration) {
    var range = end - start;
    var current = start;
    var increment = end > start ? 1 : -1;
    var stepTime = Math.abs(Math.floor(duration / range));
    var timer = setInterval(function() {
        current += increment;
        element.textContent = current;
        if (current == end) {
            clearInterval(timer);
        }
    }, stepTime);
}
