// HOME SCREEN — editorial, monochrome, pale accent.
// Sections: large greeting, momentum ladder, date spine, task groups.

const HomeScreen = ({ accent = '#F5EBE0', theme = 'light', density = 'comfortable' }) => {
  const dark = theme === 'dark';
  const fg = dark ? '#FFFFFF' : '#000000';
  const fgMuted = dark ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.52)';
  const fgSubtle = dark ? 'rgba(255,255,255,0.35)' : 'rgba(0,0,0,0.30)';
  const hair = dark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.10)';
  const chipBg = dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.04)';
  const surface = dark ? '#0E0E0E' : '#FFFFFF';
  const pad = density === 'compact' ? 18 : density === 'spacious' ? 26 : 22;
  const gap = density === 'compact' ? 18 : density === 'spacious' ? 30 : 24;

  const family = "'SF Pro Display','SF Pro Text',-apple-system,BlinkMacSystemFont,Inter,sans-serif";
  const mono = "'JetBrains Mono',ui-monospace,monospace";

  const tasks = [
    { kind: 'active',  section: 'clamp',         bold: false, accentRow: false,
      title: 'Main Work Block: Big Task focus', sub: 'Focus only on the day\u2019s BIG TASK.',
      email: 'palhariom698@gmail.com', time: '10:00', overdue: true, strike: false, done: false },
    { kind: 'active',  section: null, title: 'Household + Mother + Reset (Weekly Focus)',
      sub: null, email: 'palhariom698@gmail.com', done: false },
    { kind: 'oneday',  section: null, title: 'Daily Micro-Habits (Revision, Skin...)',
      sub: '20 min DSA/Android revision, Skin care, Hydration',
      email: 'palhariom698@gmail.com', time: '21:00', done: true },
    { kind: 'oneday',  section: null, title: 'Morning Routine (Health & Infrastructure)',
      sub: 'Exercise (20\u201330 min), Bath, Breakfast, Desk setup',
      email: 'palhariom698@gmail.com', time: '07:00', done: true },
  ];

  const yesterdayTasks = [
    { kind: 'oneday', title: 'Daily Micro-Habits (Revision, Skin, ...)',
      sub: '20 min DSA/Android revision, Skin care', email: 'palhariom698@gmail.com', time: '21:00', done: true },
    { kind: 'oneday', title: 'Grocery items laana — daal, chutney, veg',
      sub: 'Grocery shopping list for essential kitchen items', email: 'palhariom698@gmail.com', done: true },
  ];

  const TaskRow = ({ t }) => {
    const accentRow = t.overdue;
    return (
      <div style={{
        padding: `14px 0`,
        borderBottom: `1px solid ${hair}`,
        display: 'flex', gap: 12, alignItems: 'flex-start',
      }}>
        <div style={{ paddingTop: 2 }}>
          <RingIcon kind={t.kind} size={20} color={fg} done={t.done}/>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8,
          }}>
            <div style={{
              fontSize: 14, fontWeight: 600, lineHeight: '18px',
              color: t.done ? fgSubtle : fg,
              textDecoration: t.done ? 'line-through' : 'none',
              textDecorationColor: fgSubtle,
              flex: 1,
            }}>{t.title}</div>
            {t.time && (
              <div style={{
                fontFamily: mono, fontSize: 11, fontWeight: 600, letterSpacing: 0.3,
                padding: '3px 8px', borderRadius: 999,
                background: t.overdue ? accent : chipBg,
                color: t.overdue ? '#000' : fgMuted,
                flexShrink: 0, whiteSpace: 'nowrap',
              }}>{t.overdue ? `! ${t.time}` : t.time}</div>
            )}
          </div>
          {t.sub && (
            <div style={{
              fontSize: 12, lineHeight: '16px', marginTop: 3,
              color: t.done ? fgSubtle : fgMuted,
              textDecoration: t.done ? 'line-through' : 'none',
              overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
            }}>{t.sub}</div>
          )}
          {t.email && (
            <div style={{
              fontSize: 11, marginTop: 6, color: fgSubtle,
              display: 'flex', alignItems: 'center', gap: 4,
            }}>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                <rect x="3" y="4" width="18" height="18" rx="3"/>
                <path d="M3 10h18M8 2v4M16 2v4"/>
              </svg>
              {t.email}
            </div>
          )}
        </div>
      </div>
    );
  };

  return (
    <div style={{ fontFamily: family, background: surface, color: fg, minHeight: '100%', paddingBottom: 140 }}>
      {/* Top row: title + icons */}
      <div style={{
        padding: `14px ${pad}px 6px`,
        display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
      }}>
        <div>
          <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 1.8, color: fgMuted, textTransform: 'uppercase', fontFamily: mono }}>Good Morning</div>
          <div style={{ fontSize: 34, fontWeight: 800, letterSpacing: -1.4, lineHeight: '36px', marginTop: 4 }}>Preamble.</div>
        </div>
        <div style={{ display: 'flex', gap: 4 }}>
          <div style={{ width: 34, height: 34, borderRadius: 999, border: `1px solid ${hair}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <FilterIcon c={fg}/>
          </div>
          <div style={{ width: 34, height: 34, borderRadius: 999, border: `1px solid ${hair}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={fg} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="13" r="8"/>
              <path d="M12 9v4l2 2M5 3L2 6M19 3l3 3"/>
            </svg>
          </div>
        </div>
      </div>

      {/* Date pill */}
      <div style={{ padding: `8px ${pad}px 0`, display: 'flex', alignItems: 'center', gap: 8 }}>
        <div style={{
          padding: '4px 12px', borderRadius: 999,
          border: `1px solid ${hair}`,
          fontSize: 12, fontWeight: 600,
        }}>Sunday</div>
        <div style={{ fontSize: 13, color: fgMuted, fontWeight: 500 }}>19 April · Week 16</div>
      </div>

      {/* MOMENTUM — hero progress */}
      <div style={{ padding: `${gap}px ${pad}px 0` }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
          <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 1.6, color: fgMuted, textTransform: 'uppercase', fontFamily: mono }}>Momentum</div>
          <div style={{ fontSize: 11, fontFamily: mono, color: fgMuted, letterSpacing: 0.5 }}>2 / 5 DONE</div>
        </div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}>
          <div style={{ fontSize: 72, fontWeight: 800, letterSpacing: -3, lineHeight: '60px' }}>40<span style={{ fontSize: 28, color: fgMuted, fontWeight: 600 }}>%</span></div>
          <div style={{
            padding: '3px 8px', borderRadius: 999,
            background: accent, color: '#000',
            fontSize: 10, fontWeight: 700, letterSpacing: 0.5,
          }}>GAINING</div>
        </div>
        {/* ladder */}
        <div style={{ display: 'flex', gap: 3, marginTop: 12 }}>
          {Array.from({ length: 22 }).map((_, i) => {
            const filled = i < 9;
            return (
              <div key={i} style={{
                flex: 1, height: 22, borderRadius: 999,
                background: filled ? fg : chipBg,
              }}/>
            );
          })}
        </div>
        <div style={{ fontSize: 12, color: fgMuted, marginTop: 10, textWrap: 'pretty', lineHeight: '16px' }}>
          3 left today. Your Big Task is next — <b style={{ color: fg }}>10:00</b>.
        </div>
      </div>

      {/* Section: clamp */}
      <div style={{ padding: `${gap}px ${pad}px 0` }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6,
        }}>
          <RingIcon kind="active" size={16} color={fg}/>
          <div style={{ fontSize: 14, fontWeight: 700 }}>clamp</div>
          <div style={{ fontFamily: mono, fontSize: 10, color: fgMuted, letterSpacing: 0.5, marginLeft: 'auto' }}>4 ACTIVE</div>
        </div>
        <div>
          {tasks.map((t, i) => <TaskRow key={i} t={t}/>)}
        </div>
      </div>

      {/* Yesterday group */}
      <div style={{ padding: `${gap}px ${pad}px 0` }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6,
        }}>
          <div style={{ padding: '4px 12px', borderRadius: 999, background: chipBg, fontSize: 12, fontWeight: 600 }}>Saturday</div>
          <div style={{ fontSize: 13, color: fgMuted, fontWeight: 500 }}>18 April</div>
          <div style={{ fontFamily: mono, fontSize: 10, color: fgMuted, letterSpacing: 0.5, marginLeft: 'auto' }}>2 / 2</div>
        </div>
        <div>
          {yesterdayTasks.map((t, i) => <TaskRow key={i} t={t}/>)}
        </div>
      </div>

      {/* Bottom reveal — closer line */}
      <div style={{ padding: `${gap}px ${pad}px 40px` }}>
        <div style={{
          padding: '14px 16px',
          borderRadius: 16,
          background: accent,
          color: '#000',
          fontSize: 13, lineHeight: '18px',
          textWrap: 'pretty',
        }}>
          One more before lunch and you beat yesterday's pace.
        </div>
      </div>

      {/* FAB stack */}
      <div style={{ position: 'absolute', right: 16, bottom: 104, display: 'flex', flexDirection: 'column', gap: 10, alignItems: 'center', zIndex: 4 }}>
        <div style={{
          width: 44, height: 44, borderRadius: 999,
          background: dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.06)',
          color: fg, display: 'flex', alignItems: 'center', justifyContent: 'center',
          backdropFilter: 'blur(6px)',
        }}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="13" r="8"/>
            <path d="M12 9v4l2 2"/>
          </svg>
        </div>
        <div style={{
          width: 44, height: 44, borderRadius: 999,
          background: dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.06)',
          color: fg, display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3zM19 11a7 7 0 0 1-14 0M12 18v3"/>
          </svg>
        </div>
        <div style={{
          padding: '12px 18px', borderRadius: 999,
          background: dark ? '#fff' : '#000', color: dark ? '#000' : '#fff',
          display: 'flex', alignItems: 'center', gap: 8,
          fontSize: 13, fontWeight: 700,
          boxShadow: '0 6px 18px rgba(0,0,0,0.25)',
        }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
            <path d="M12 5v14M5 12h14"/>
          </svg>
          Add Task
        </div>
      </div>

      <BottomNav active="tasks" theme={theme}/>
    </div>
  );
};

Object.assign(window, { HomeScreen });
