// CALENDAR SCREENS — Month, Simple, Week, Day views
// Editorial monochrome, pale accent, hairlines, capsules.

const CalHeader = ({ theme, pad, view, fg, fgMuted, hair, mono }) => (
  <>
    <div style={{
      padding: `14px ${pad}px 10px`,
      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{ width: 30, height: 30, borderRadius: 999, border: `1px solid ${hair}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <ChevronIcon c={fg}/>
        </div>
        <div>
          <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 1.6, color: fgMuted, textTransform: 'uppercase', fontFamily: mono }}>2026</div>
          <div style={{ fontSize: 20, fontWeight: 800, letterSpacing: -0.6, lineHeight: '22px' }}>April</div>
        </div>
      </div>
      <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        <div style={{ width: 30, height: 30, borderRadius: 999, border: `1px solid ${hair}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <FilterIcon c={fg}/>
        </div>
        <div style={{ width: 30, height: 30, borderRadius: 999, border: `1px solid ${hair}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <ChevronIcon dir="right" c={fg}/>
        </div>
      </div>
    </div>
    <div style={{ padding: `0 ${pad}px 14px` }}>
      <SegCapsule options={['Month','Simple','Week','Day']} active={view} theme={theme}/>
    </div>
  </>
);

// ─── Month view ─────────────────────────────────────────────
const CalMonth = ({ accent = '#F5EBE0', theme = 'light', density = 'comfortable' }) => {
  const dark = theme === 'dark';
  const fg = dark ? '#FFFFFF' : '#000000';
  const fgMuted = dark ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.52)';
  const fgFaint = dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.22)';
  const hair = dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.08)';
  const chipBg = dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)';
  const surface = dark ? '#0E0E0E' : '#FFFFFF';
  const pad = density === 'compact' ? 14 : density === 'spacious' ? 22 : 18;
  const family = "'SF Pro Display','SF Pro Text',-apple-system,Inter,sans-serif";
  const mono = "'JetBrains Mono',monospace";

  // Events per day (sparse) using accent + sky for categories
  const sky = '#D6EAF8';
  // day → array of event label objects
  const EV = {
    1:['shamp','trip','Dame ...'],2:['shamp','trip','Daily ...'],3:['shamp','trip'],4:['shamp','trip','Padh...'],
    5:['shamp','trip','Padh...'],6:['shamp','trip','Padh...'],7:['shamp','trip','Padh...'],
    8:['Daily ...','Morni ...','Colleg...'],9:['Padh...','Main ...','Phase ...'],
    10:['Daily ...','Morni ...','Marke...'],11:['Daily ...','From ...','Main ...'],
    12:['Daily ...','Coachi...','Morni...'],13:['Padh...','Daily ...'],14:['train p...','Daily ...','Morni ...'],
    15:['Happy ...','Bahag ...','Daily ...'],16:['Daily ...','ye hai ...','Main ...','Morni ...'],
    17:['Daily ...','Morni ...'],18:['Daily ...','Groceri...','gdibbd'],
    19:['clamp','Main ...','House...'],
    20:['Daily ...','train p ...','Morni ...'],21:['Daily ...','Main ...'],
    22:['Daily ...','Colleg...','Gihokn'],23:['Daily ...','Main ...','Phase ...'],
    24:['Morni ...','Marke...'],25:['Daily ...','Morni ...','Buffer ...'],
    26:['Daily ...','Morni ...','House...'],27:['DSA D...'],28:['train p ...','Morni ...','Androi...'],
    29:['Main ...','Colleg...'],30:['Daily ...','Morni ...','Play S...'],
  };
  const TODAY = 19;

  // Build 6×7 grid starting Sunday. April 2026 starts on Wednesday.
  // Apr 1 = Wed. Sunday before is Mar 29.
  const cells = [];
  // prev month tail
  [29,30,31].forEach(d => cells.push({ d, month: 'prev' }));
  for (let d = 1; d <= 30; d++) cells.push({ d, month: 'cur' });
  // next month head to fill 35 cells (5 rows)
  [1,2].forEach(d => cells.push({ d, month: 'next' }));

  const evColor = (label) => {
    const trip = /shamp|trip|Padh|Happy|Bahag|gdibbd|ye hai|House|Play|clamp/i.test(label);
    return trip ? accent : sky;
  };

  return (
    <div style={{ fontFamily: family, background: surface, color: fg, minHeight: '100%', paddingBottom: 100 }}>
      <CalHeader theme={theme} pad={pad} view="Month" fg={fg} fgMuted={fgMuted} hair={hair} mono={mono}/>

      {/* Day headers */}
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', padding: `0 ${pad}px`,
        marginBottom: 6,
      }}>
        {['S','M','T','W','T','F','S'].map((d, i) => (
          <div key={i} style={{
            fontFamily: mono, fontSize: 10, fontWeight: 700, letterSpacing: 1,
            color: fgMuted, textAlign: 'center',
          }}>{d}</div>
        ))}
      </div>

      {/* Grid */}
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)',
        padding: `0 ${pad}px`, gap: 4,
      }}>
        {cells.map((c, i) => {
          const isToday = c.month === 'cur' && c.d === TODAY;
          const events = c.month === 'cur' ? (EV[c.d] || []) : [];
          const faded = c.month !== 'cur';
          return (
            <div key={i} style={{
              aspectRatio: '0.62 / 1',
              padding: '5px 3px',
              borderRadius: 10,
              background: isToday ? fg : 'transparent',
              color: isToday ? surface : (faded ? fgFaint : fg),
              display: 'flex', flexDirection: 'column', gap: 2,
              overflow: 'hidden', minWidth: 0,
            }}>
              <div style={{
                fontSize: 11, fontWeight: 700, letterSpacing: -0.2,
                textAlign: 'center',
                fontFamily: mono,
                marginBottom: 2,
              }}>{c.d}</div>
              {events.slice(0, 3).map((e, j) => (
                <div key={j} style={{
                  fontSize: 8, lineHeight: '10px', fontWeight: 600,
                  padding: '2px 4px', borderRadius: 4,
                  background: isToday ? 'rgba(255,255,255,0.16)' : evColor(e),
                  color: isToday ? surface : '#000',
                  whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                  textWrap: 'nowrap',
                }}>{e}</div>
              ))}
              {events.length > 3 && (
                <div style={{
                  fontSize: 9, color: isToday ? 'rgba(255,255,255,0.6)' : fgMuted,
                  textAlign: 'center', fontFamily: mono, letterSpacing: 0.3,
                }}>+{events.length - 3}</div>
              )}
            </div>
          );
        })}
      </div>

      <Fab theme={theme}/>
      <BottomNav active="cal" theme={theme}/>
    </div>
  );
};

// ─── Simple view (heatmap of days) ──────────────────────────
const CalSimple = ({ accent = '#F5EBE0', theme = 'light', density = 'comfortable' }) => {
  const dark = theme === 'dark';
  const fg = dark ? '#FFFFFF' : '#000000';
  const fgMuted = dark ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.52)';
  const fgFaint = dark ? 'rgba(255,255,255,0.30)' : 'rgba(0,0,0,0.28)';
  const hair = dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.08)';
  const chipBg = dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.05)';
  const surface = dark ? '#0E0E0E' : '#FFFFFF';
  const pad = density === 'compact' ? 18 : density === 'spacious' ? 26 : 22;
  const family = "'SF Pro Display','SF Pro Text',-apple-system,Inter,sans-serif";
  const mono = "'JetBrains Mono',monospace";

  const TODAY = 19;
  // intensity per day (0–4) — same-ish distribution as screenshot
  const counts = {
    1:1,2:2,3:1,4:1,5:1,6:1,7:1,8:1,9:1,10:1,11:1,
    12:1,13:1,14:1,15:3,16:4,17:1,18:3,19:0,20:1,21:1,22:1,23:1,24:1,25:1,
    26:1,27:1,28:1,29:1,30:1
  };

  const bgFor = (lvl) => {
    if (lvl === 0) return chipBg;
    const alphas = [0, 0.3, 0.55, 0.78, 1];
    return `color-mix(in oklab, ${accent} ${alphas[lvl] * 100}%, ${surface})`;
  };
  // for darker intensity we override using grey tones for highest
  const greyFor = (lvl) => {
    if (lvl === 0) return chipBg;
    const alphas = [0, 0.12, 0.22, 0.55, 0.92];
    return dark
      ? `rgba(255,255,255, ${alphas[lvl]})`
      : `rgba(0,0,0, ${alphas[lvl]})`;
  };

  // Build 5 rows × 7 cols; Apr 1 Wed
  const cells = [];
  [null, null, null].forEach(() => cells.push(null));
  for (let d = 1; d <= 30; d++) cells.push(d);
  while (cells.length % 7 !== 0) cells.push(null);

  return (
    <div style={{ fontFamily: family, background: surface, color: fg, minHeight: '100%', paddingBottom: 100 }}>
      <CalHeader theme={theme} pad={pad} view="Simple" fg={fg} fgMuted={fgMuted} hair={hair} mono={mono}/>

      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)',
        padding: `0 ${pad}px`, marginBottom: 8,
      }}>
        {['S','M','T','W','T','F','S'].map((d, i) => (
          <div key={i} style={{
            fontFamily: mono, fontSize: 10, fontWeight: 700, letterSpacing: 1,
            color: fgMuted, textAlign: 'center',
          }}>{d}</div>
        ))}
      </div>

      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)',
        padding: `0 ${pad}px`, gap: 8, marginBottom: 10,
      }}>
        {cells.map((d, i) => {
          if (d === null) return <div key={i}/>;
          const isToday = d === TODAY;
          const lvl = counts[d] || 0;
          return (
            <div key={i} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
              <div style={{
                width: 34, height: 34, borderRadius: 999,
                background: isToday ? fg : greyFor(lvl),
                color: isToday ? surface : (lvl >= 3 ? (dark ? '#000' : '#fff') : fg),
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 13, fontWeight: 700, letterSpacing: -0.2,
                fontFamily: mono,
              }}>{d}</div>
            </div>
          );
        })}
      </div>

      {/* legend */}
      <div style={{ padding: `4px ${pad}px 16px`, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
        <span style={{ fontSize: 10, fontFamily: mono, letterSpacing: 1, color: fgMuted }}>LESS</span>
        {[0,1,2,3,4].map(l => (
          <div key={l} style={{ width: 12, height: 12, borderRadius: 999, background: greyFor(l) }}/>
        ))}
        <span style={{ fontSize: 10, fontFamily: mono, letterSpacing: 1, color: fgMuted }}>MORE</span>
      </div>

      <div style={{ height: 1, background: hair, margin: `0 ${pad}px` }}/>

      {/* Today preview */}
      <div style={{ padding: `${pad}px ${pad}px 0` }}>
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 10 }}>
          <div>
            <div style={{ fontSize: 10, fontFamily: mono, fontWeight: 700, letterSpacing: 1.4, color: fgMuted, textTransform: 'uppercase' }}>Today</div>
            <div style={{ fontSize: 22, fontWeight: 800, letterSpacing: -0.8, marginTop: 2 }}>Sun, 19 Apr</div>
          </div>
          <div style={{ fontFamily: mono, fontSize: 11, color: fgMuted, letterSpacing: 0.5 }}>2 / 5</div>
        </div>
        <MiniAgenda theme={theme} accent={accent} hair={hair} fg={fg} fgMuted={fgMuted} mono={mono}/>
      </div>

      <Fab theme={theme}/>
      <BottomNav active="cal" theme={theme}/>
    </div>
  );
};

// small agenda shared
const MiniAgenda = ({ theme, accent, hair, fg, fgMuted, mono }) => {
  const dark = theme === 'dark';
  const chipBg = dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.04)';
  const items = [
    { k: 'active', t: 'clamp',  time: null },
    { k: 'active', t: 'Main Work Block: Big Task focus', time: '10:00', accent: true, sub: 'Focus only on the day\u2019s BIG TASK.' },
    { k: 'active', t: 'Household + Mother + Reset (Weekly Focus)', time: null },
    { k: 'oneday', t: 'Daily Micro-Habits (Revision, Skin...)', time: '21:00', done: true },
  ];
  return (
    <div>
      {items.map((it, i) => (
        <div key={i} style={{
          display: 'flex', gap: 10, alignItems: 'flex-start',
          padding: '10px 0',
          borderBottom: i < items.length - 1 ? `1px solid ${hair}` : 'none',
        }}>
          <div style={{ paddingTop: 1 }}>
            <RingIcon kind={it.k} size={18} color={fg} done={it.done}/>
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
              <div style={{
                fontSize: 13, fontWeight: 600, lineHeight: '17px', flex: 1,
                color: it.done ? fgMuted : fg,
                textDecoration: it.done ? 'line-through' : 'none',
              }}>{it.t}</div>
              {it.time && (
                <div style={{
                  fontFamily: mono, fontSize: 11, fontWeight: 600,
                  padding: '2px 8px', borderRadius: 999,
                  background: it.accent ? accent : chipBg,
                  color: it.accent ? '#000' : fgMuted,
                  whiteSpace: 'nowrap',
                }}>{it.accent ? `! ${it.time}` : it.time}</div>
              )}
            </div>
            {it.sub && (
              <div style={{ fontSize: 11, color: fgMuted, marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{it.sub}</div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
};

// ─── Week view ──────────────────────────────────────────────
const CalWeek = ({ accent = '#F5EBE0', theme = 'light', density = 'comfortable' }) => {
  const dark = theme === 'dark';
  const fg = dark ? '#FFFFFF' : '#000000';
  const fgMuted = dark ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.52)';
  const hair = dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.08)';
  const chipBg = dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.04)';
  const surface = dark ? '#0E0E0E' : '#FFFFFF';
  const pad = density === 'compact' ? 18 : density === 'spacious' ? 24 : 20;
  const family = "'SF Pro Display','SF Pro Text',-apple-system,Inter,sans-serif";
  const mono = "'JetBrains Mono',monospace";

  const days = [
    { d: 19, l: 'S', dots: 3, today: true },
    { d: 20, l: 'M', dots: 2 },
    { d: 21, l: 'T', dots: 4 },
    { d: 22, l: 'W', dots: 1 },
    { d: 23, l: 'T', dots: 2 },
    { d: 24, l: 'F', dots: 0 },
    { d: 25, l: 'S', dots: 1 },
  ];

  return (
    <div style={{ fontFamily: family, background: surface, color: fg, minHeight: '100%', paddingBottom: 100 }}>
      <CalHeader theme={theme} pad={pad} view="Week" fg={fg} fgMuted={fgMuted} hair={hair} mono={mono}/>

      {/* Week pill row */}
      <div style={{
        padding: `0 ${pad}px 14px`,
        display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 5,
      }}>
        {days.map((day, i) => (
          <div key={i} style={{
            padding: '10px 0 8px',
            borderRadius: 14,
            background: day.today ? fg : chipBg,
            color: day.today ? surface : fg,
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6,
            minHeight: 60,
          }}>
            <div style={{ fontFamily: mono, fontSize: 9, fontWeight: 700, letterSpacing: 1, opacity: 0.6 }}>{day.l}</div>
            <div style={{ fontSize: 18, fontWeight: 800, letterSpacing: -0.4, lineHeight: 1 }}>{day.d}</div>
            <div style={{ display: 'flex', gap: 2 }}>
              {Array.from({ length: Math.min(day.dots, 4) }).map((_, k) => (
                <div key={k} style={{
                  width: 3, height: 3, borderRadius: 999,
                  background: day.today ? 'rgba(255,255,255,0.7)' : fgMuted,
                }}/>
              ))}
              {day.dots === 0 && (
                <div style={{ width: 3, height: 3, borderRadius: 999, background: 'transparent' }}/>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Section header */}
      <div style={{
        padding: `0 ${pad}px 6px`,
        display: 'flex', alignItems: 'baseline', justifyContent: 'space-between',
      }}>
        <div>
          <div style={{ fontSize: 10, fontFamily: mono, fontWeight: 700, letterSpacing: 1.4, color: fgMuted, textTransform: 'uppercase' }}>Today</div>
          <div style={{ fontSize: 24, fontWeight: 800, letterSpacing: -0.8, marginTop: 2 }}>Sun, 19 Apr</div>
        </div>
        <div style={{ fontFamily: mono, fontSize: 11, color: fgMuted, letterSpacing: 0.5 }}>2 / 5 · 40%</div>
      </div>

      {/* progress bar */}
      <div style={{ padding: `0 ${pad}px 14px` }}>
        <div style={{ height: 4, borderRadius: 999, background: chipBg, overflow: 'hidden' }}>
          <div style={{ width: '40%', height: '100%', background: fg }}/>
        </div>
      </div>

      {/* Agenda */}
      <div style={{ padding: `0 ${pad}px 0` }}>
        <MiniAgenda theme={theme} accent={accent} hair={hair} fg={fg} fgMuted={fgMuted} mono={mono}/>
      </div>

      <Fab theme={theme}/>
      <BottomNav active="cal" theme={theme}/>
    </div>
  );
};

// ─── Day view ───────────────────────────────────────────────
const CalDay = ({ accent = '#F5EBE0', theme = 'light', density = 'comfortable' }) => {
  const dark = theme === 'dark';
  const fg = dark ? '#FFFFFF' : '#000000';
  const fgMuted = dark ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.52)';
  const hair = dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.08)';
  const chipBg = dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.04)';
  const surface = dark ? '#0E0E0E' : '#FFFFFF';
  const pad = density === 'compact' ? 18 : density === 'spacious' ? 24 : 20;
  const family = "'SF Pro Display','SF Pro Text',-apple-system,Inter,sans-serif";
  const mono = "'JetBrains Mono',monospace";

  const hourStart = 6;
  const hourEnd = 22;
  const hours = hourEnd - hourStart; // 16
  const ROW = 44;

  const events = [
    { start: 7,    end: 7.67, title: 'Morning Routine', sub: 'Exercise, bath, breakfast', kind: 'oneday', done: true, accent: false },
    { start: 10,   end: 12,   title: 'Main Work Block', sub: 'Big Task focus', kind: 'active', accent: true, overdue: true },
    { start: 13,   end: 14,   title: 'Household + Reset', sub: 'Weekly focus', kind: 'active' },
    { start: 15.5, end: 16.5, title: 'clamp review', kind: 'active' },
    { start: 21,   end: 21.5, title: 'Daily Micro-Habits', sub: '20 min DSA, Skin care', kind: 'oneday' },
  ];

  // Current time indicator at 10:12 (index position)
  const NOW = 10.2;

  return (
    <div style={{ fontFamily: family, background: surface, color: fg, minHeight: '100%', paddingBottom: 100 }}>
      <CalHeader theme={theme} pad={pad} view="Day" fg={fg} fgMuted={fgMuted} hair={hair} mono={mono}/>

      {/* Date row */}
      <div style={{ padding: `0 ${pad}px 12px`, display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}>
          <div style={{ fontSize: 44, fontWeight: 800, letterSpacing: -2, lineHeight: '40px' }}>19</div>
          <div>
            <div style={{ fontSize: 14, fontWeight: 700, letterSpacing: -0.2 }}>Sunday</div>
            <div style={{ fontSize: 11, fontFamily: mono, color: fgMuted, letterSpacing: 0.5 }}>APR · WEEK 16</div>
          </div>
        </div>
        <div style={{
          padding: '4px 10px', borderRadius: 999,
          background: accent, color: '#000',
          fontSize: 10, fontWeight: 700, letterSpacing: 0.4,
        }}>NOW 10:12</div>
      </div>

      {/* Timeline */}
      <div style={{ padding: `0 ${pad}px` }}>
        <div style={{ position: 'relative' }}>
          {/* hour lines */}
          {Array.from({ length: hours + 1 }).map((_, i) => {
            const h = hourStart + i;
            return (
              <div key={i} style={{
                position: 'relative',
                height: ROW,
                display: 'flex', alignItems: 'flex-start',
              }}>
                <div style={{
                  width: 36, flexShrink: 0,
                  fontFamily: mono, fontSize: 10, color: fgMuted, letterSpacing: 0.5,
                  paddingTop: 0, fontWeight: 600,
                }}>{String(h).padStart(2,'0')}:00</div>
                <div style={{ flex: 1, borderTop: `1px solid ${hair}` }}/>
              </div>
            );
          })}

          {/* events */}
          {events.map((ev, i) => {
            const top = (ev.start - hourStart) * ROW;
            const height = (ev.end - ev.start) * ROW;
            return (
              <div key={i} style={{
                position: 'absolute',
                top, left: 44, right: 4,
                height: Math.max(height - 3, 26),
                borderRadius: 12,
                padding: '6px 10px',
                background: ev.accent ? accent : (ev.done ? chipBg : (dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.05)')),
                color: '#000',
                display: 'flex', flexDirection: 'column', gap: 2,
                overflow: 'hidden',
                borderLeft: `3px solid ${ev.accent ? '#000' : fg}`,
                opacity: ev.done ? 0.55 : 1,
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <RingIcon kind={ev.kind} size={14} color="#000" done={ev.done}/>
                  <div style={{
                    fontSize: 12, fontWeight: 700, letterSpacing: -0.1,
                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                    textDecoration: ev.done ? 'line-through' : 'none',
                    color: dark && !ev.accent ? fg : '#000',
                  }}>{ev.title}</div>
                </div>
                {ev.sub && height > 30 && (
                  <div style={{
                    fontSize: 10, color: 'rgba(0,0,0,0.6)',
                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                  }}>{ev.sub}</div>
                )}
              </div>
            );
          })}

          {/* Now indicator */}
          <div style={{
            position: 'absolute',
            top: (NOW - hourStart) * ROW,
            left: 0, right: 0,
            display: 'flex', alignItems: 'center', gap: 4,
            pointerEvents: 'none',
          }}>
            <div style={{
              width: 36, paddingRight: 4, textAlign: 'right',
              fontFamily: mono, fontSize: 10, fontWeight: 800, color: '#000',
              background: accent, borderRadius: 999, padding: '2px 6px',
            }}>10:12</div>
            <div style={{ flex: 1, height: 2, background: '#000', borderRadius: 999 }}/>
            <div style={{ width: 8, height: 8, borderRadius: 999, background: '#000' }}/>
          </div>
        </div>
      </div>

      <Fab theme={theme}/>
      <BottomNav active="cal" theme={theme}/>
    </div>
  );
};

Object.assign(window, { CalMonth, CalSimple, CalWeek, CalDay });
