// ═══════════════════════════════════════════════
// Preamble Admin Panel — Shared Utilities
// ═══════════════════════════════════════════════

// Check authentication and populate sidebar user info
async function checkAuth() {
  try {
    const res = await fetch('/api/auth/me');
    if (!res.ok) {
      window.location.href = '/';
      return;
    }
    const data = await res.json();
    const user = data.user;

    // Populate sidebar admin info
    const nameEl = document.getElementById('adminName');
    const emailEl = document.getElementById('adminEmail');
    const avatarEl = document.getElementById('adminAvatar');

    if (nameEl) nameEl.textContent = user.name || user.email;
    if (emailEl) emailEl.textContent = user.email;
    if (avatarEl) {
      if (user.picture) {
        avatarEl.innerHTML = `<img src="${user.picture}" alt="avatar">`;
      } else {
        avatarEl.innerHTML = `<div style="display:flex;align-items:center;justify-content:center;width:100%;height:100%;font-size:0.875rem;font-weight:700;color:var(--gray-400);">${(user.email || '?')[0].toUpperCase()}</div>`;
      }
    }
  } catch (err) {
    window.location.href = '/';
  }
}

// Logout
async function logout() {
  try {
    await fetch('/api/auth/logout', { method: 'POST' });
  } catch (e) { /* ignore */ }
  window.location.href = '/';
}

// Format timestamp to readable date string
function formatDate(timestamp) {
  if (!timestamp) return 'N/A';
  const date = new Date(timestamp);
  const now = new Date();
  const diff = now - date;

  // Less than 1 minute
  if (diff < 60000) return 'Just now';
  // Less than 1 hour
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
  // Less than 24 hours
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
  // Less than 7 days
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}d ago`;

  // Full date
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}

// Escape HTML to prevent XSS
function escapeHtml(text) {
  if (!text) return '';
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

// Toast notification
function showToast(message, type = 'success') {
  const container = document.getElementById('toastContainer');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.textContent = message;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(20px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 3000);
}

// Global AI Initialization
function initGlobalAI() {
  if (document.getElementById('aiFab')) return; // already init

  const container = document.createElement('div');
  container.innerHTML = `
    <div class="ai-fab" id="aiFab" onclick="toggleGlobalAiPanel()">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24">
        <path d="M12 2a10 10 0 1 0 10 10H12V2z"></path>
        <path d="M12 12L2.5 7.5"></path>
        <path d="M12 12l9.5-4.5"></path>
      </svg>
    </div>

    <div class="ai-panel" id="globalAiPanel">
      <div class="ai-panel-header">
        <h3>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
          </svg>
          Preamble AI
        </h3>
        <button class="btn btn-ghost btn-sm" style="padding: 0.2rem; border: none;" onclick="toggleGlobalAiPanel()">✕</button>
      </div>
      <div class="ai-panel-messages" id="globalAiMessages">
        <div class="ai-message ai">Hello Admin! I'm your Preamble AI assistant. I'm here to automate and speed up any admin task. How can I help?</div>
      </div>
      <div class="ai-panel-input">
        <input type="text" id="globalAiInput" placeholder="Ask Preamble AI..." onkeypress="if(event.key === 'Enter') sendGlobalAiMessage()">
        <button onclick="sendGlobalAiMessage()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <line x1="22" y1="2" x2="11" y2="13"></line>
            <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
          </svg>
        </button>
      </div>
    </div>
  `;
  document.body.appendChild(container);
}

function toggleGlobalAiPanel() {
  const panel = document.getElementById('globalAiPanel');
  panel.classList.toggle('active');
  if (panel.classList.contains('active')) {
    document.getElementById('globalAiInput').focus();
  }
}

async function sendGlobalAiMessage() {
  const input = document.getElementById('globalAiInput');
  const text = input.value.trim();
  if (!text) return;

  input.value = '';
  appendGlobalAiMessage(text, 'user');

  try {
    const res = await fetch('/api/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: text })
    });
    const data = await res.json();
    
    if (data.reply) {
      const jsonMatch = data.reply.match(/```json\n([\s\S]*?)\n```/);
      if (jsonMatch) {
        try {
          const action = JSON.parse(jsonMatch[1]);
          handleGlobalAiAction(action);
        } catch(e) {}
      }
      const displayText = data.reply.replace(/```json\n([\s\S]*?)\n```/, '').trim();
      if (displayText) {
        appendGlobalAiMessage(displayText, 'ai');
      }
    }
  } catch (err) {
    appendGlobalAiMessage("Error reaching Preamble AI.", 'ai');
  }
}

function appendGlobalAiMessage(text, sender) {
  const msgs = document.getElementById('globalAiMessages');
  const div = document.createElement('div');
  div.className = `ai-message ${sender}`;
  
  // Minimal markdown processing for lists/bold
  let html = escapeHtml(text)
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>');
  div.innerHTML = html;
  
  msgs.appendChild(div);
  msgs.scrollTop = msgs.scrollHeight;
}

async function handleGlobalAiAction(actionObj) {
  if (actionObj.action === 'create_broadcast') {
    try {
      const res = await fetch('/api/broadcasts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: actionObj.payload.title,
          description: actionObj.payload.description,
          targetType: actionObj.payload.targetType || 'all',
          autoNotify: true
        })
      });
      const data = await res.json();
      if (data.success) {
        appendGlobalAiMessage(`✅ Broadcast sent: "${actionObj.payload.title}"`, 'ai');
        // Refresh page to show new broadcast if on broadcasts page
        if (window.location.pathname === '/broadcasts') location.reload();
      } else {
        appendGlobalAiMessage(`❌ Failed to create broadcast.`, 'ai');
      }
    } catch(e) {
      appendGlobalAiMessage(`❌ Error creating broadcast.`, 'ai');
    }
  } else if (actionObj.action === 'mass_delete_tasks') {
    try {
      const res = await fetch('/api/tasks/mass_delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          status: actionObj.payload.status,
          beforeDate: actionObj.payload.beforeDate
        })
      });
      const data = await res.json();
      if (data.success) {
        appendGlobalAiMessage(`✅ Successfully deleted ${data.deletedCount} tasks.`, 'ai');
        if (window.location.pathname === '/dashboard') location.reload();
      } else {
        appendGlobalAiMessage(`❌ Failed to mass delete tasks.`, 'ai');
      }
    } catch(e) {
      appendGlobalAiMessage(`❌ Error mass deleting tasks.`, 'ai');
    }
  } else if (actionObj.action === 'mass_delete_broadcasts') {
    try {
      const res = await fetch('/api/broadcasts/mass_delete', { method: 'POST', headers: {'Content-Type':'application/json'} });
      const data = await res.json();
      if (data.success) {
        appendGlobalAiMessage(`✅ Successfully deleted ${data.deletedCount} broadcasts.`, 'ai');
        if (window.location.pathname === '/broadcasts') location.reload();
      } else {
        appendGlobalAiMessage(`❌ Failed to mass delete broadcasts.`, 'ai');
      }
    } catch(e) {
      appendGlobalAiMessage(`❌ Error mass deleting broadcasts.`, 'ai');
    }
  } else if (actionObj.action === 'mass_delete_notifications') {
    try {
      const res = await fetch('/api/notifications/mass_delete', { method: 'POST', headers: {'Content-Type':'application/json'} });
      const data = await res.json();
      if (data.success) {
        appendGlobalAiMessage(`✅ Successfully cleared ${data.deletedCount} notification logs.`, 'ai');
        if (window.location.pathname === '/notifications') location.reload();
      } else {
        appendGlobalAiMessage(`❌ Failed to clear notifications.`, 'ai');
      }
    } catch(e) {
      appendGlobalAiMessage(`❌ Error clearing notifications.`, 'ai');
    }
  }
}

document.addEventListener('DOMContentLoaded', () => {
    // Check if we are on a page where we want AI
    if (window.location.pathname !== '/' && window.location.pathname !== '/index.html') {
        initGlobalAI();
    }
});
