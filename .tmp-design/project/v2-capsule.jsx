// Variant 2 — CAPSULE GRID
// Bento-style grid of pill/capsule tiles. Everything is a rounded rect.
// Dense, playful, still monochrome-dominant.

const V2Capsule = ({ accent = '#E8EDDF', theme = 'light', density = 'comfortable' }) => {
  const dark = theme === 'dark';
  const fg = dark ? '#FFFFFF' : '#000000';
  const fgMuted = dark ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.52)';
  const hair = dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.08)';
  const tile = dark ? '#1A1A1A' : '#F4F4F3';
  const surface = dark ? '#0E0E0E' : '#FFFFFF';
  const pad = density === 'compact' ? 14 : density === 'spacious' ? 22 : 18;
  const gap = density === 'compact' ? 8 : density === 'spacious' ? 14 : 10;

  const family = "'SF Pro Display','SF Pro Text',-apple-system,BlinkMacSystemFont,Inter,sans-serif";

  const Tile = ({ children, span = 1, rowSpan = 1, bg, color, style, noPad }) => (
    <div style={{
      gridColumn: `span ${span}`,
      gridRow: `span ${rowSpan}`,
      background: bg || tile,
      color: color || fg,
      borderRadius: 22,
      padding: noPad ? 0 : 16,
      boxSizing: 'border-box',
      display: 'flex', flexDirection: 'column',
      position: 'relative',
      overflow: 'hidden',
      ...style,
    }}>{children}</div>
  );

  const Kicker = ({ children, color }) => (
    <div style={{
      fontSize: 10, fontWeight: 700, letterSpacing: 1.4,
      color: color || fgMuted, textTransform: 'uppercase',
    }}>{children}</div>
  );

  // Week bars
  const wk = [0.5, 0.7, 0.9, 0.4, 0.85, 0.65, 0.3];
  const wkLabels = ['M','T','W','T','F','S','S'];

  // Sparkline path
  const spark = [20, 35, 28, 48, 42, 60, 55, 70, 62, 82, 78, 88];
  const sparkPath = (() => {
    const w = 180, h = 44;
    const min = Math.min(...spark), max = Math.max(...spark);
    const pts = spark.map((v, i) => {
      const x = (i / (spark.length - 1)) * w;
      const y = h - ((v - min) / (max - min)) * h;
      return [x, y];
    });
    return pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ');
  })();

  // Donut
  const Donut = ({ pct, size = 96, stroke = 12, color = fg, trackColor = hair }) => {
    const r = (size - stroke) / 2;
    const c = 2 * Math.PI * r;
    return (
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={size/2} cy={size/2} r={r} fill="none" stroke={trackColor} strokeWidth={stroke} />
        <circle cx={size/2} cy={size/2} r={r} fill="none" stroke={color} strokeWidth={stroke}
          strokeLinecap="round" strokeDasharray={`${c * pct} ${c}`} />
      </svg>
    );
  };

  return (
    <div style={{ fontFamily: family, background: surface, color: fg, minHeight: '100%' }}>
      {/* top bar */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: `14px ${pad}px 4px`,
      }}>
        <div>
          <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 1.4, color: fgMuted, textTransform: 'uppercase' }}>April 2026</div>
          <div style={{ fontSize: 26, fontWeight: 800, letterSpacing: -0.8, marginTop: 2 }}>Your month</div>
        </div>
        <div style={{
          display: 'flex', padding: 3, background: tile, borderRadius: 999,
        }}>
          {['W','M','Y'].map((r, i) => {
            const active = i === 1;
            return (
              <div key={r} style={{
                width: 28, height: 28, borderRadius: 999,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                background: active ? fg : 'transparent',
                color: active ? surface : fgMuted,
                fontSize: 12, fontWeight: 700,
              }}>{r}</div>
            );
          })}
        </div>
      </div>

      {/* grid */}
      <div style={{
        padding: `${pad - 4}px ${pad}px 24px`,
        display: 'grid',
        gridTemplateColumns: 'repeat(6, 1fr)',
        gap,
      }}>

        {/* HERO SCORE — 6 cols */}
        <Tile span={6} style={{ padding: 20 }}>
          <Kicker>Productivity index</Kicker>
          <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', marginTop: 6 }}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
              <div style={{ fontSize: 96, lineHeight: '84px', fontWeight: 800, letterSpacing: -4 }}>87</div>
              <div style={{ fontSize: 18, color: fgMuted, fontWeight: 500 }}>/100</div>
            </div>
            <svg width="180" height="44" viewBox="0 0 180 44" style={{ overflow: 'visible', marginBottom: 8 }}>
              <path d={sparkPath} fill="none" stroke={fg} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <circle cx="180" cy={44 - ((88-20)/(88-20))*44} r="4" fill={fg}/>
            </svg>
          </div>
          <div style={{ display: 'flex', gap: 6, marginTop: 10 }}>
            <span style={{
              padding: '4px 10px', borderRadius: 999,
              background: accent, color: '#000', fontSize: 11, fontWeight: 700, letterSpacing: 0.3,
            }}>↑ +12 pts</span>
            <span style={{
              padding: '4px 10px', borderRadius: 999,
              border: `1px solid ${hair}`, fontSize: 11, fontWeight: 600, color: fgMuted,
            }}>Best this year</span>
          </div>
        </Tile>

        {/* Streak — 3 cols */}
        <Tile span={3} bg={accent} color="#000">
          <Kicker color="rgba(0,0,0,0.55)">Streak</Kicker>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 4, marginTop: 'auto' }}>
            <div style={{ fontSize: 54, lineHeight: '48px', fontWeight: 800, letterSpacing: -2 }}>23</div>
            <div style={{ fontSize: 14, opacity: 0.7 }}>days</div>
          </div>
          <div style={{ fontSize: 11, opacity: 0.7, marginTop: 6 }}>Next milestone · 30</div>
        </Tile>

        {/* Tasks done — 3 cols */}
        <Tile span={3} bg={fg} color={surface}>
          <Kicker color={dark ? 'rgba(0,0,0,0.55)' : 'rgba(255,255,255,0.55)'}>Completed</Kicker>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 4, marginTop: 'auto' }}>
            <div style={{ fontSize: 54, lineHeight: '48px', fontWeight: 800, letterSpacing: -2 }}>128</div>
          </div>
          <div style={{ fontSize: 11, opacity: 0.6, marginTop: 6 }}>of 151 · 85% rate</div>
        </Tile>

        {/* Week bars — 6 cols */}
        <Tile span={6}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Kicker>This week</Kicker>
            <span style={{ fontSize: 12, fontWeight: 600 }}>42 tasks</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-end', height: 90, gap: 6, marginTop: 14 }}>
            {wk.map((v, i) => {
              const today = i === 4;
              return (
                <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                  <div style={{
                    width: '100%',
                    height: `${v * 100}%`,
                    minHeight: 6,
                    background: today ? fg : (dark ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.10)'),
                    borderRadius: 999,
                  }} />
                  <div style={{ fontSize: 10, fontWeight: 600, color: today ? fg : fgMuted }}>{wkLabels[i]}</div>
                </div>
              );
            })}
          </div>
        </Tile>

        {/* Focus donut — 3 cols */}
        <Tile span={3}>
          <Kicker>Focus</Kicker>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 6 }}>
            <div style={{ position: 'relative' }}>
              <Donut pct={0.78} size={80} stroke={10} color={fg}/>
              <div style={{
                position: 'absolute', inset: 0,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 14, fontWeight: 800, letterSpacing: -0.3,
              }}>78%</div>
            </div>
            <div>
              <div style={{ fontSize: 18, fontWeight: 800, letterSpacing: -0.3 }}>19h</div>
              <div style={{ fontSize: 10, color: fgMuted }}>47 pomodoros</div>
            </div>
          </div>
        </Tile>

        {/* Longest session — 3 cols */}
        <Tile span={3}>
          <Kicker>Longest focus</Kicker>
          <div style={{ fontSize: 36, fontWeight: 800, letterSpacing: -1.4, marginTop: 'auto' }}>2h 05</div>
          <div style={{ fontSize: 11, color: fgMuted, marginTop: 4 }}>Tue, 09:15 → 11:20</div>
        </Tile>

        {/* Heatmap — 6 cols */}
        <Tile span={6}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Kicker>Consistency</Kicker>
            <span style={{ fontSize: 11, color: fgMuted }}>18 weeks</span>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(18, 1fr)', gap: 2, marginTop: 12 }}>
            {Array.from({ length: 7 * 18 }).map((_, i) => {
              const row = i % 7, col = Math.floor(i / 7);
              const v = Math.sin(col * 0.41 + row * 0.3) * 0.5 + Math.cos(col * 0.17) * 0.5;
              const n = Math.max(0, Math.min(1, (v + 1) / 2));
              const lvl = n > 0.78 ? 4 : n > 0.58 ? 3 : n > 0.35 ? 2 : n > 0.15 ? 1 : 0;
              const alphas = [0, 0.3, 0.55, 0.78, 1];
              const bg = lvl === 0
                ? (dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)')
                : `color-mix(in oklab, ${accent} ${alphas[lvl] * 100}%, ${surface})`;
              return (
                <div key={i} style={{
                  gridColumn: col + 1, gridRow: row + 1,
                  aspectRatio: '1 / 1',
                  borderRadius: 4,
                  background: bg,
                }} />
              );
            })}
          </div>
        </Tile>

        {/* Time of day — 6 cols */}
        <Tile span={6}>
          <Kicker>Peak hours</Kicker>
          <div style={{ display: 'flex', alignItems: 'flex-end', height: 70, gap: 4, marginTop: 10 }}>
            {[0.15, 0.3, 0.55, 0.85, 0.92, 0.7, 0.55, 0.65, 0.82, 0.6, 0.3, 0.18].map((v, i) => (
              <div key={i} style={{
                flex: 1, height: `${v * 100}%`, minHeight: 4,
                background: v > 0.8 ? fg : (dark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.09)'),
                borderRadius: 999,
              }}/>
            ))}
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 6, fontSize: 10, color: fgMuted }}>
            <span>6 AM</span><span>Noon</span><span>6 PM</span><span>12 AM</span>
          </div>
          <div style={{
            marginTop: 10, padding: '8px 12px', borderRadius: 14,
            background: dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.04)',
            fontSize: 12,
          }}>
            Morning bird — 62% done before noon.
          </div>
        </Tile>

        {/* Tags — 6 cols, capsule grid */}
        <Tile span={6}>
          <Kicker>Top tags</Kicker>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 10 }}>
            {[
              { n: 'Deep work', c: 42, big: true },
              { n: 'Errands', c: 24 },
              { n: 'Writing', c: 19 },
              { n: 'Health', c: 15 },
              { n: 'Reading', c: 11 },
              { n: 'Calls', c: 8 },
              { n: 'Other', c: 9 },
            ].map(t => (
              <div key={t.n} style={{
                padding: t.big ? '8px 14px' : '6px 12px',
                borderRadius: 999,
                background: t.big ? fg : (dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.05)'),
                color: t.big ? surface : fg,
                fontSize: t.big ? 14 : 12,
                fontWeight: 600,
                display: 'inline-flex', alignItems: 'center', gap: 6,
              }}>
                {t.n}
                <span style={{
                  fontSize: 10, fontWeight: 700,
                  opacity: t.big ? 0.6 : 0.5,
                  fontVariantNumeric: 'tabular-nums',
                }}>{t.c}</span>
              </div>
            ))}
          </div>
        </Tile>

        {/* Milestones — 6 cols, horizontal list */}
        <Tile span={6}>
          <Kicker>Personal bests · unlocked</Kicker>
          <div style={{ display: 'flex', gap: 8, marginTop: 10, overflowX: 'auto' }}>
            {[
              { e: '30', l: 'day streak', sub: 'Feb 14', hot: true },
              { e: '17', l: 'tasks/day', sub: 'Mar 4' },
              { e: '3h', l: 'deep focus', sub: 'Feb 21' },
              { e: '100%', l: 'week rate', sub: 'Apr 7' },
            ].map((m, i) => (
              <div key={i} style={{
                flexShrink: 0,
                padding: 12,
                borderRadius: 16,
                background: m.hot ? accent : (dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.04)'),
                color: m.hot ? '#000' : fg,
                minWidth: 104,
              }}>
                <div style={{ fontSize: 24, fontWeight: 800, letterSpacing: -1 }}>{m.e}</div>
                <div style={{ fontSize: 11, marginTop: 2, opacity: 0.7 }}>{m.l}</div>
                <div style={{ fontSize: 10, marginTop: 6, opacity: 0.5 }}>{m.sub}</div>
              </div>
            ))}
          </div>
        </Tile>

        {/* Weekly summary — 6 cols */}
        <Tile span={6} style={{ background: 'transparent', border: `1px solid ${hair}`, padding: 16 }}>
          <Kicker>Week in review</Kicker>
          <div style={{ fontSize: 16, lineHeight: '22px', fontWeight: 500, letterSpacing: -0.2, marginTop: 8, textWrap: 'pretty' }}>
            42 tasks shipped. Friday was your strongest. Deep-work mornings pulled your score up 12 points.
          </div>
        </Tile>

      </div>
    </div>
  );
};

Object.assign(window, { V2Capsule });
