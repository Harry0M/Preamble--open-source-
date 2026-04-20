// Minimal Android-ish phone frame tuned for Preamble monochrome.
// Scrollable inner content, crisp edges, no decorative chrome.

const PhoneFrame = ({ children, label, theme = 'light', accent = '#F5EBE0', width = 380, height = 780 }) => {
  const isDark = theme === 'dark';
  const bg = isDark ? '#0E0E0E' : '#FFFFFF';
  const fg = isDark ? '#FFFFFF' : '#000000';
  const bezel = isDark ? '#1A1A1A' : '#E6E6E6';
  const outer = isDark ? '#000000' : '#FAFAFA';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 }}>
      <div style={{
        width, height,
        background: outer,
        borderRadius: 44,
        padding: 8,
        boxShadow: isDark
          ? '0 30px 80px rgba(0,0,0,0.55), 0 0 0 1px rgba(255,255,255,0.04)'
          : '0 30px 80px rgba(0,0,0,0.10), 0 0 0 1px rgba(0,0,0,0.06)',
        boxSizing: 'border-box',
      }}>
        <div style={{
          width: '100%', height: '100%',
          background: bg, color: fg,
          borderRadius: 36,
          overflow: 'hidden',
          position: 'relative',
          border: `1px solid ${bezel}`,
          display: 'flex', flexDirection: 'column',
        }}>
          {/* Status bar */}
          <div style={{
            flexShrink: 0,
            height: 40,
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            padding: '0 24px',
            fontSize: 13, fontWeight: 600,
            letterSpacing: 0.2,
            fontFamily: "'SF Pro Display','SF Pro Text',-apple-system,BlinkMacSystemFont,Inter,sans-serif",
          }}>
            <span>9:41</span>
            <div style={{
              position: 'absolute', left: '50%', top: 10, transform: 'translateX(-50%)',
              width: 90, height: 22, background: '#000', borderRadius: 999,
              opacity: isDark ? 0.9 : 1,
            }} />
            <div style={{ display: 'flex', gap: 5, alignItems: 'center' }}>
              {/* signal */}
              <svg width="16" height="10" viewBox="0 0 16 10" fill={fg}>
                <rect x="0" y="6" width="3" height="4" rx="0.5"/>
                <rect x="4" y="4" width="3" height="6" rx="0.5"/>
                <rect x="8" y="2" width="3" height="8" rx="0.5"/>
                <rect x="12" y="0" width="3" height="10" rx="0.5"/>
              </svg>
              {/* battery */}
              <svg width="24" height="12" viewBox="0 0 24 12">
                <rect x="0.5" y="0.5" width="20" height="11" rx="2.5" fill="none" stroke={fg} strokeOpacity="0.4"/>
                <rect x="2" y="2" width="15" height="8" rx="1.5" fill={fg}/>
                <rect x="21.5" y="4" width="2" height="4" rx="1" fill={fg} fillOpacity="0.4"/>
              </svg>
            </div>
          </div>

          {/* Scrollable content region */}
          <div style={{ flex: 1, overflow: 'auto', position: 'relative' }}>
            {children}
          </div>

          {/* Home indicator */}
          <div style={{
            flexShrink: 0,
            height: 28,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            background: bg,
          }}>
            <div style={{ width: 120, height: 4, borderRadius: 2, background: fg, opacity: 0.85 }} />
          </div>
        </div>
      </div>
      {label && (
        <div style={{
          fontFamily: "'SF Pro Display','SF Pro Text',-apple-system,Inter,sans-serif",
          fontSize: 13, letterSpacing: 0.3, color: '#666',
          textTransform: 'uppercase', fontWeight: 600,
        }}>{label}</div>
      )}
    </div>
  );
};

Object.assign(window, { PhoneFrame });
