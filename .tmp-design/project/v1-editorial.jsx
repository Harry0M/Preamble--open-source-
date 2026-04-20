// Variant 1 — EDITORIAL
// Huge typographic score, hairline dividers, capsule time selector,
// sparse color, calendar heatmap as editorial grid, tag leaderboard.

const V1Editorial = ({ accent = '#F5EBE0', theme = 'light', density = 'comfortable' }) => {
  const dark = theme === 'dark';
  const fg = dark ? '#FFFFFF' : '#000000';
  const fgMuted = dark ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.52)';
  const hair = dark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.12)';
  const chipBg = dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.04)';
  const surface = dark ? '#0E0E0E' : '#FFFFFF';
  const pad = density === 'compact' ? 16 : density === 'spacious' ? 28 : 20;
  const gap = density === 'compact' ? 20 : density === 'spacious' ? 36 : 28;

  const family = "'SF Pro Display','SF Pro Text',-apple-system,BlinkMacSystemFont,Inter,sans-serif";

  // Heatmap — 7 rows × 18 weeks
  const weeks = 18;
  const heat = Array.from({ length: weeks * 7 }, (_, i) => {
    const v = Math.sin(i * 0.41) * 0.5 + Math.cos(i * 0.17) * 0.5;
    const n = Math.max(0, Math.min(1, (v + 1) / 2));
    return n > 0.78 ? 4 : n > 0.58 ? 3 : n > 0.35 ? 2 : n > 0.15 ? 1 : 0;
  });

  const heatColor = (lvl) => {
    if (lvl === 0) return dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)';
    // interpolate accent intensity
    const alphas = [0, 0.25, 0.5, 0.75, 1];
    return `color-mix(in oklab, ${accent} ${alphas[lvl] * 100}%, ${dark ? '#0E0E0E' : '#FFFFFF'})`;
  };

  const Hair = () => <div style={{ height: 1, background: hair, width: '100%' }} />;

  const Section = ({ kicker, children, style }) => (
    <div style={{ padding: `${gap}px ${pad}px`, ...style }}>
      {kicker && (
        <div style={{
          fontSize: 11, fontWeight: 700, letterSpacing: 1.6,
          color: fgMuted, textTransform: 'uppercase', marginBottom: 14,
          fontFamily: family,
        }}>{kicker}</div>
      )}
      {children}
    </div>
  );

  // Time of day data
  const todBars = [0.15, 0.35, 0.62, 0.88, 0.72, 0.45, 0.58, 0.82, 0.55, 0.28, 0.18, 0.12];
  const todLabels = ['6','8','10','12','2','4','6','8','10','12','2','4'];

  // Tag leaderboard
  const tags = [
    { name: 'Deep work',  pct: 38, count: 42 },
    { name: 'Errands',    pct: 22, count: 24 },
    { name: 'Writing',    pct: 18, count: 19 },
    { name: 'Health',     pct: 14, count: 15 },
    { name: 'Other',      pct:  8, count:  9 },
  ];

  return (
    <div style={{ fontFamily: family, background: surface, color: fg, minHeight: '100%' }}>
      {/* Top app bar */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: `14px ${pad}px 6px`,
      }}>
        <div style={{ fontSize: 17, fontWeight: 700, letterSpacing: -0.2 }}>Stats</div>
        <div style={{
          width: 34, height: 34, borderRadius: 999,
          border: `1px solid ${hair}`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={fg} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>
          </svg>
        </div>
      </div>

      {/* Time range capsule */}
      <div style={{ padding: `4px ${pad}px 0` }}>
        <div style={{
          display: 'flex', padding: 4, gap: 0,
          background: chipBg, borderRadius: 999,
        }}>
          {['Week','Month','Year','All'].map((r, i) => {
            const active = i === 1;
            return (
              <div key={r} style={{
                flex: 1, textAlign: 'center', padding: '8px 0',
                borderRadius: 999,
                background: active ? (dark ? '#fff' : '#000') : 'transparent',
                color: active ? (dark ? '#000' : '#fff') : fgMuted,
                fontSize: 13, fontWeight: 600, letterSpacing: 0.1,
              }}>{r}</div>
            );
          })}
        </div>
      </div>

      {/* HERO — huge score */}
      <Section kicker="Productivity index · April">
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}>
          <div style={{
            fontSize: 132, lineHeight: '112px', fontWeight: 800, letterSpacing: -6,
            fontFamily: family,
          }}>
            87
          </div>
          <div style={{ fontSize: 22, fontWeight: 500, color: fgMuted, letterSpacing: -0.4 }}>/100</div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 14 }}>
          <div style={{
            padding: '4px 10px', borderRadius: 999,
            background: accent, color: '#000',
            fontSize: 12, fontWeight: 700, letterSpacing: 0.3,
            display: 'flex', alignItems: 'center', gap: 4,
          }}>
            <svg width="10" height="10" viewBox="0 0 24 24" fill="#000">
              <path d="M7 14l5-5 5 5z"/>
            </svg>
            +12 vs March
          </div>
          <div style={{ fontSize: 13, color: fgMuted }}>Best month this year</div>
        </div>
      </Section>

      <Hair />

      {/* Streak */}
      <Section kicker="Streak">
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ fontSize: 56, lineHeight: '56px', fontWeight: 800, letterSpacing: -2 }}>23</div>
          <div style={{ fontSize: 14, color: fgMuted }}>days in a row</div>
        </div>
        {/* Dotted streak ladder — 30 days */}
        <div style={{ display: 'flex', gap: 4, marginTop: 18, flexWrap: 'nowrap' }}>
          {Array.from({ length: 30 }).map((_, i) => {
            const done = i < 23;
            return (
              <div key={i} style={{
                flex: 1, height: 28, borderRadius: 6,
                background: done ? fg : 'transparent',
                border: done ? 'none' : `1px dashed ${hair}`,
              }} />
            );
          })}
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8, fontSize: 11, color: fgMuted, letterSpacing: 0.3 }}>
          <span>Mar 28</span>
          <span>Today · Apr 19</span>
          <span>next: 30</span>
        </div>
      </Section>

      <Hair />

      {/* Heatmap */}
      <Section kicker="Consistency · last 18 weeks">
        <div style={{ display: 'grid', gridTemplateColumns: `repeat(${weeks}, 1fr)`, gap: 3 }}>
          {Array.from({ length: 7 }).map((_, row) => (
            Array.from({ length: weeks }).map((__, col) => {
              const lvl = heat[col * 7 + row];
              return (
                <div key={`${row}-${col}`} style={{
                  gridColumn: col + 1, gridRow: row + 1,
                  aspectRatio: '1 / 1',
                  borderRadius: 3,
                  background: heatColor(lvl),
                }} />
              );
            })
          ))}
        </div>
        <div style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          marginTop: 12, fontSize: 11, color: fgMuted,
        }}>
          <span>Less</span>
          <div style={{ display: 'flex', gap: 3 }}>
            {[0,1,2,3,4].map(l => (
              <div key={l} style={{ width: 12, height: 12, borderRadius: 3, background: heatColor(l) }}/>
            ))}
          </div>
          <span>More</span>
        </div>
      </Section>

      <Hair />

      {/* Time of day */}
      <Section kicker="When you get things done">
        <div style={{ display: 'flex', alignItems: 'flex-end', height: 120, gap: 6 }}>
          {todBars.map((v, i) => {
            const peak = v > 0.8;
            return (
              <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                <div style={{
                  width: '100%',
                  height: `${v * 100}%`,
                  background: peak ? fg : chipBg,
                  borderRadius: 4,
                  borderTopLeftRadius: peak ? 999 : 4,
                  borderTopRightRadius: peak ? 999 : 4,
                }} />
              </div>
            );
          })}
        </div>
        <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
          {todLabels.map((t, i) => (
            <div key={i} style={{ flex: 1, textAlign: 'center', fontSize: 10, color: fgMuted, fontVariantNumeric: 'tabular-nums' }}>{t}</div>
          ))}
        </div>
        <div style={{
          marginTop: 14, padding: '12px 14px',
          borderRadius: 14, background: accent, color: '#000',
          fontSize: 13, lineHeight: '18px', fontWeight: 500,
        }}>
          You're sharpest between <b>11 AM and 1 PM</b>. 3 of your top weeks started with a morning block.
        </div>
      </Section>

      <Hair />

      {/* Tags leaderboard */}
      <Section kicker="Where the time went">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {tags.map((t, i) => (
            <div key={t.name}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                  <span style={{ fontSize: 11, color: fgMuted, fontVariantNumeric: 'tabular-nums', width: 14 }}>0{i+1}</span>
                  <span style={{ fontSize: 15, fontWeight: 600 }}>{t.name}</span>
                </div>
                <div style={{ fontSize: 13, color: fgMuted, fontVariantNumeric: 'tabular-nums' }}>
                  {t.count} tasks · {t.pct}%
                </div>
              </div>
              <div style={{ height: 6, borderRadius: 3, background: chipBg, overflow: 'hidden' }}>
                <div style={{
                  width: `${t.pct * 2.5}%`, height: '100%',
                  background: i === 0 ? fg : accent,
                  borderRadius: 3,
                }}/>
              </div>
            </div>
          ))}
        </div>
      </Section>

      <Hair />

      {/* Focus time + Weekly + Milestones combined */}
      <Section kicker="Focus time">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          <div style={{ padding: 16, borderRadius: 18, border: `1px solid ${hair}` }}>
            <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 1.4, color: fgMuted, textTransform: 'uppercase' }}>Pomodoros</div>
            <div style={{ fontSize: 34, fontWeight: 800, letterSpacing: -1, marginTop: 6 }}>47</div>
            <div style={{ fontSize: 12, color: fgMuted, marginTop: 2 }}>19h 35m focused</div>
          </div>
          <div style={{ padding: 16, borderRadius: 18, background: accent, color: '#000' }}>
            <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 1.4, textTransform: 'uppercase', opacity: 0.7 }}>Longest session</div>
            <div style={{ fontSize: 34, fontWeight: 800, letterSpacing: -1, marginTop: 6 }}>2h 05m</div>
            <div style={{ fontSize: 12, opacity: 0.7, marginTop: 2 }}>Tuesday morning</div>
          </div>
        </div>
      </Section>

      <Hair />

      {/* Personal bests */}
      <Section kicker="Personal bests">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
          {[
            { k: 'Longest streak',   v: '34 days',  when: 'Jan \u201926' },
            { k: 'Most tasks in a day', v: '17 tasks', when: 'Mar 4' },
            { k: 'Longest focus',    v: '3h 12m',   when: 'Feb 21' },
          ].map((m, i, arr) => (
            <div key={m.k} style={{
              display: 'flex', justifyContent: 'space-between', alignItems: 'baseline',
              padding: '14px 0',
              borderBottom: i < arr.length - 1 ? `1px solid ${hair}` : 'none',
            }}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 500 }}>{m.k}</div>
                <div style={{ fontSize: 11, color: fgMuted, marginTop: 2 }}>{m.when}</div>
              </div>
              <div style={{ fontSize: 18, fontWeight: 700, letterSpacing: -0.3 }}>{m.v}</div>
            </div>
          ))}
        </div>
      </Section>

      {/* Weekly summary footer */}
      <Section kicker="This week in one line">
        <div style={{
          fontSize: 22, lineHeight: '30px', fontWeight: 500, letterSpacing: -0.4,
          textWrap: 'pretty',
        }}>
          You shipped <span style={{ background: accent, padding: '0 4px', borderRadius: 4, color: '#000' }}>42 tasks</span>, kept a <b>23-day streak</b>, and focused for <b>19h 35m</b> — mostly before noon.
        </div>
        <div style={{ height: 40 }} />
      </Section>
    </div>
  );
};

Object.assign(window, { V1Editorial });
