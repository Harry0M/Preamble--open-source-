// Shared bits for Home + Calendar screens — ring checkboxes, task rows,
// bottom nav, FAB, filter icons. Preamble-accurate archetype rings.

// Archetype ring — 'oneday' solid, 'active' half-dashed, 'repeat' full-dashed
const RingIcon = ({ kind = 'oneday', size = 22, color = '#000', done = false, strike = false }) => {
  const r = (size - 3) / 2;
  const cx = size / 2;
  const c = 2 * Math.PI * r;
  const dashes = {
    oneday: { array: `${c}`, offset: 0 },
    active: { array: `${c/2} ${c/2}`, offset: 0 },
    repeat: { array: '3 3', offset: 0 },
  }[kind];
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ flexShrink: 0 }}>
      <circle cx={cx} cy={size/2} r={r} fill="none"
        stroke={done ? color : (strike ? color : color)}
        strokeOpacity={strike ? 0.35 : 1}
        strokeWidth="1.5"
        strokeDasharray={dashes.array}
        strokeLinecap="round"/>
      {done && <path d={`M${size*0.3} ${size*0.52} L${size*0.44} ${size*0.66} L${size*0.72} ${size*0.36}`}
        fill="none" stroke={color} strokeOpacity={0.35} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>}
    </svg>
  );
};

// Bottom nav — pill style, 4 tabs
const BottomNav = ({ active = 'tasks', theme = 'light', onChange }) => {
  const dark = theme === 'dark';
  const fg = dark ? '#fff' : '#000';
  const surface = dark ? '#1A1A1A' : '#F2F0EC';
  const muted = dark ? 'rgba(255,255,255,0.5)' : 'rgba(0,0,0,0.45)';
  const tabs = [
    { id: 'tasks', icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
      </svg>
    ), label: 'Tasks' },
    { id: 'stats', icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 20V10M9 20V4M15 20v-6M21 20v-10"/>
      </svg>
    ), label: 'Stats' },
    { id: 'cal', icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="4" width="18" height="18" rx="3"/>
        <path d="M3 10h18M8 2v4M16 2v4"/>
      </svg>
    ), label: 'Calendar' },
    { id: 'settings', icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="3"/>
        <path d="M12 2v2M12 20v2M4.9 4.9l1.5 1.5M17.6 17.6l1.5 1.5M2 12h2M20 12h2M4.9 19.1l1.5-1.5M17.6 6.4l1.5-1.5"/>
      </svg>
    ), label: 'Settings' },
  ];
  return (
    <div style={{
      position: 'absolute', left: 0, right: 0, bottom: 28,
      display: 'flex', justifyContent: 'center', pointerEvents: 'none', zIndex: 5,
    }}>
      <div style={{
        background: surface,
        borderRadius: 999,
        padding: 6,
        display: 'flex', gap: 2,
        boxShadow: dark ? '0 4px 14px rgba(0,0,0,0.5)' : '0 4px 14px rgba(0,0,0,0.08)',
        pointerEvents: 'auto',
      }}>
        {tabs.map(t => {
          const isActive = active === t.id;
          return (
            <div key={t.id} onClick={() => onChange?.(t.id)} style={{
              padding: isActive ? '8px 14px' : '8px 10px',
              borderRadius: 999,
              background: isActive ? (dark ? '#0A0A0A' : '#fff') : 'transparent',
              color: isActive ? fg : muted,
              display: 'flex', alignItems: 'center', gap: 6,
              fontSize: 12, fontWeight: 600,
              cursor: 'pointer',
              boxShadow: isActive ? (dark ? 'inset 0 0 0 1px rgba(255,255,255,0.08)' : 'inset 0 0 0 1px rgba(0,0,0,0.06)') : 'none',
            }}>
              {t.icon}
              {isActive && <span>{t.label}</span>}
            </div>
          );
        })}
      </div>
    </div>
  );
};

// FAB
const Fab = ({ theme = 'light', bottom = 104, right = 20 }) => {
  const dark = theme === 'dark';
  return (
    <div style={{
      position: 'absolute', right, bottom,
      width: 56, height: 56, borderRadius: 999,
      background: dark ? '#fff' : '#000',
      color: dark ? '#000' : '#fff',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      boxShadow: '0 6px 20px rgba(0,0,0,0.25)',
      zIndex: 4,
    }}>
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
        <path d="M12 5v14M5 12h14"/>
      </svg>
    </div>
  );
};

// Filter + more icons
const FilterIcon = ({ c = '#000' }) => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M4 6h16M7 12h10M10 18h4"/>
  </svg>
);
const MoreIcon = ({ c = '#000' }) => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill={c}>
    <circle cx="12" cy="5" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="12" cy="19" r="1.5"/>
  </svg>
);
const ChevronIcon = ({ dir = 'left', c = '#000' }) => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ transform: dir === 'right' ? 'rotate(180deg)' : 'none' }}>
    <path d="M15 18l-6-6 6-6"/>
  </svg>
);

// Segmented capsule — shared across calendar views
const SegCapsule = ({ options, active, theme = 'light' }) => {
  const dark = theme === 'dark';
  const chipBg = dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.04)';
  const fg = dark ? '#fff' : '#000';
  const bg = dark ? '#0E0E0E' : '#fff';
  const muted = dark ? 'rgba(255,255,255,0.5)' : 'rgba(0,0,0,0.5)';
  return (
    <div style={{
      display: 'flex', padding: 4, background: chipBg, borderRadius: 999, gap: 0,
    }}>
      {options.map(o => {
        const isA = o === active;
        return (
          <div key={o} style={{
            flex: 1, textAlign: 'center', padding: '8px 0',
            borderRadius: 999,
            background: isA ? fg : 'transparent',
            color: isA ? bg : muted,
            fontSize: 13, fontWeight: 600,
          }}>{o}</div>
        );
      })}
    </div>
  );
};

Object.assign(window, { RingIcon, BottomNav, Fab, FilterIcon, MoreIcon, ChevronIcon, SegCapsule });
