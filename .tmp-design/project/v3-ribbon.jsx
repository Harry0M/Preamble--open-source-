// Variant 3 — RIBBON / STORY
// A vertical spine runs down the screen. Stats read as chapters.
// Bold numeric type + narrative microcopy. Pale accents as highlights.

const V3Ribbon = ({ accent = '#FCE4E4', theme = 'light', density = 'comfortable' }) => {
  const dark = theme === 'dark';
  const fg = dark ? '#FFFFFF' : '#000000';
  const fgMuted = dark ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.52)';
  const hair = dark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.12)';
  const tile = dark ? '#1A1A1A' : '#F4F4F3';
  const surface = dark ? '#0E0E0E' : '#FFFFFF';
  const pad = density === 'compact' ? 16 : density === 'spacious' ? 24 : 20;
  const gap = density === 'compact' ? 22 : density === 'spacious' ? 40 : 32;

  const family = "'SF Pro Display','SF Pro Text',-apple-system,BlinkMacSystemFont,Inter,sans-serif";
  const mono = "'SF Mono','JetBrains Mono',ui-monospace,'Roboto Mono',monospace";

  const Chapter = ({ num, title, children }) => (
    <div style={{ position: 'relative', paddingLeft: 36, paddingRight: 0, marginBottom: gap }}>
      {/* node */}
      <div style={{
        position: 'absolute', left: 7, top: 2,
        width: 18, height: 18, borderRadius: 999,
        background: surface, border: `2px solid ${fg}`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <div style={{ width: 6, height: 6, borderRadius: 999, background: fg }}/>
      </div>
      <div style={{
        fontFamily: mono, fontSize: 11, fontWeight: 600,
        color: fgMuted, letterSpacing: 1.2, marginBottom: 4,
      }}>CH.{String(num).padStart(2,'0')}</div>
      <div style={{ fontSize: 20, fontWeight: 700, letterSpacing: -0.5, marginBottom: 14 }}>{title}</div>
      {children}
    </div>
  );

  // tiny area chart for score
  const scorePts = [52, 58, 51, 63, 68, 72, 69, 75, 81, 78, 84, 87];
  const areaPath = (() => {
    const w = 280, h = 80;
    const min = 40, max = 95;
    const pts = scorePts.map((v, i) => {
      const x = (i / (scorePts.length - 1)) * w;
      const y = h - ((v - min) / (max - min)) * h;
      return [x, y];
    });
    const line = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ');
    const area = `${line} L${w},${h} L0,${h} Z`;
    return { line, area, pts };
  })();

  return (
    <div style={{ fontFamily: family, background: surface, color: fg, minHeight: '100%' }}>
      {/* top bar */}
      <div style={{
        padding: `14px ${pad}px 8px`,
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      }}>
        <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: -0.2 }}>Stats</div>
        <div style={{
          fontFamily: mono, fontSize: 11, color: fgMuted, letterSpacing: 0.6,
        }}>APR 01 → APR 19</div>
      </div>

      {/* editorial hero */}
      <div style={{ padding: `4px ${pad}px 10px` }}>
        <div style={{
          fontSize: 11, fontWeight: 700, letterSpacing: 1.6, color: fgMuted, textTransform: 'uppercase',
        }}>The story so far</div>
        <div style={{
          fontSize: 32, lineHeight: '36px', fontWeight: 800, letterSpacing: -1.2,
          marginTop: 8, textWrap: 'pretty',
        }}>
          A steady month.<br/>
          <span style={{ color: fgMuted, fontWeight: 500 }}>
            You did the work — in smaller, tighter pieces.
          </span>
        </div>
      </div>

      {/* capsule range */}
      <div style={{ padding: `8px ${pad}px 20px`, display: 'flex', gap: 6 }}>
        {[
          ['7D', false],
          ['30D', true],
          ['90D', false],
          ['YTD', false],
        ].map(([l, active]) => (
          <div key={l} style={{
            padding: '6px 14px',
            borderRadius: 999,
            border: active ? `1px solid ${fg}` : `1px solid ${hair}`,
            background: active ? fg : 'transparent',
            color: active ? surface : fg,
            fontSize: 12, fontWeight: 600, letterSpacing: 0.2,
            fontFamily: mono,
          }}>{l}</div>
        ))}
      </div>

      {/* Spine line */}
      <div style={{ position: 'relative', padding: `0 ${pad}px 20px` }}>
        <div style={{
          position: 'absolute',
          left: pad + 15, top: 0, bottom: 0, width: 2,
          background: `repeating-linear-gradient(to bottom, ${hair} 0 4px, transparent 4px 8px)`,
        }}/>

        {/* Chapter 1 — Score */}
        <Chapter num={1} title="Productivity · 87/100">
          <div style={{
            background: tile, borderRadius: 20, padding: 16,
            position: 'relative', overflow: 'hidden',
          }}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
              <div style={{ fontSize: 72, lineHeight: '64px', fontWeight: 800, letterSpacing: -3 }}>87</div>
              <div style={{
                padding: '3px 8px', borderRadius: 999, background: accent, color: '#000',
                fontSize: 10, fontWeight: 700, letterSpacing: 0.4,
              }}>↑ 12 PTS</div>
            </div>
            <svg width="100%" height="80" viewBox={`0 0 280 80`} preserveAspectRatio="none" style={{ marginTop: 12, display: 'block' }}>
              <defs>
                <linearGradient id="v3grad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={fg} stopOpacity="0.14"/>
                  <stop offset="100%" stopColor={fg} stopOpacity="0"/>
                </linearGradient>
              </defs>
              <path d={areaPath.area} fill="url(#v3grad)"/>
              <path d={areaPath.line} fill="none" stroke={fg} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              {areaPath.pts.map((p, i) => i === areaPath.pts.length - 1 && (
                <g key={i}>
                  <circle cx={p[0]} cy={p[1]} r="5" fill={surface} stroke={fg} strokeWidth="2"/>
                </g>
              ))}
            </svg>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontFamily: mono, fontSize: 10, color: fgMuted, marginTop: 4 }}>
              <span>MAY '25</span><span>OCT '25</span><span>APR '26</span>
            </div>
          </div>
        </Chapter>

        {/* Chapter 2 — Streak */}
        <Chapter num={2} title="23-day streak, unbroken">
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div style={{
              width: 84, height: 84, borderRadius: 999,
              background: accent, color: '#000',
              display: 'flex', flexDirection: 'column',
              alignItems: 'center', justifyContent: 'center',
              flexShrink: 0,
            }}>
              <div style={{ fontSize: 32, fontWeight: 800, letterSpacing: -1, lineHeight: 1 }}>23</div>
              <div style={{ fontSize: 9, fontWeight: 700, letterSpacing: 1, marginTop: 2 }}>DAYS</div>
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, color: fgMuted, lineHeight: '18px' }}>
                Started <b style={{ color: fg }}>Mar 28</b>. Next milestone in <b style={{ color: fg }}>7 days</b>.
              </div>
              <div style={{ display: 'flex', gap: 2, marginTop: 10 }}>
                {Array.from({ length: 30 }).map((_, i) => (
                  <div key={i} style={{
                    flex: 1, height: 20, borderRadius: 2,
                    background: i < 23 ? fg : (i === 23 ? fgMuted : hair),
                  }}/>
                ))}
              </div>
            </div>
          </div>
        </Chapter>

        {/* Chapter 3 — Consistency heatmap as ribbon strip */}
        <Chapter num={3} title="Consistency">
          <div style={{
            display: 'grid', gridTemplateColumns: 'repeat(18, 1fr)', gap: 3,
          }}>
            {Array.from({ length: 7 * 18 }).map((_, i) => {
              const row = i % 7, col = Math.floor(i / 7);
              const v = Math.sin(col * 0.35 + row * 0.28) * 0.5 + Math.cos(col * 0.2) * 0.5;
              const n = Math.max(0, Math.min(1, (v + 1) / 2));
              const lvl = n > 0.8 ? 4 : n > 0.58 ? 3 : n > 0.35 ? 2 : n > 0.15 ? 1 : 0;
              const alphas = [0, 0.3, 0.55, 0.78, 1];
              const bg = lvl === 0
                ? (dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)')
                : `color-mix(in oklab, ${accent} ${alphas[lvl] * 100}%, ${surface})`;
              return (
                <div key={i} style={{
                  gridColumn: col + 1, gridRow: row + 1,
                  aspectRatio: '1 / 1', borderRadius: 2,
                  background: bg,
                }} />
              );
            })}
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontFamily: mono, fontSize: 10, color: fgMuted, marginTop: 8 }}>
            <span>18W AGO</span>
            <span>93% ACTIVE DAYS</span>
            <span>TODAY</span>
          </div>
        </Chapter>

        {/* Chapter 4 — Time of day */}
        <Chapter num={4} title="Your shape of a day">
          <div style={{ position: 'relative' }}>
            {/* radial / arc chart alternative: use bars but arranged like a wave */}
            <svg width="100%" height="110" viewBox="0 0 300 110" preserveAspectRatio="none" style={{ display: 'block' }}>
              {(() => {
                const data = [0.08, 0.12, 0.2, 0.45, 0.82, 0.95, 0.78, 0.6, 0.5, 0.68, 0.82, 0.55, 0.32, 0.18, 0.1, 0.06];
                const w = 300, h = 100;
                const pts = data.map((v, i) => [
                  (i / (data.length - 1)) * w,
                  h - v * h,
                ]);
                const line = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ');
                const area = `${line} L${w},${h} L0,${h} Z`;
                const peakIdx = data.indexOf(Math.max(...data));
                return (
                  <g>
                    <defs>
                      <linearGradient id="v3wave" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor={accent} stopOpacity="1"/>
                        <stop offset="100%" stopColor={accent} stopOpacity="0.2"/>
                      </linearGradient>
                    </defs>
                    <path d={area} fill="url(#v3wave)"/>
                    <path d={line} fill="none" stroke={fg} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                    <circle cx={pts[peakIdx][0]} cy={pts[peakIdx][1]} r="5" fill={fg}/>
                    <line x1={pts[peakIdx][0]} y1={pts[peakIdx][1] - 5} x2={pts[peakIdx][0]} y2="0" stroke={fg} strokeWidth="1" strokeDasharray="2 3"/>
                  </g>
                );
              })()}
            </svg>
            <div style={{
              position: 'absolute', top: 0, left: '33%',
              fontFamily: mono, fontSize: 10, fontWeight: 700,
              background: fg, color: surface, padding: '2px 6px', borderRadius: 4,
              transform: 'translateX(-50%)',
            }}>11 AM</div>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontFamily: mono, fontSize: 10, color: fgMuted, marginTop: 4 }}>
            <span>6A</span><span>10A</span><span>2P</span><span>6P</span><span>10P</span><span>2A</span>
          </div>
          <div style={{
            marginTop: 12, fontSize: 13, lineHeight: '18px',
            color: fgMuted, textWrap: 'pretty',
          }}>
            Your peak is <span style={{ color: fg, fontWeight: 600 }}>late morning</span>, with a second push after dinner. Flat afternoons — consider a reset walk.
          </div>
        </Chapter>

        {/* Chapter 5 — Tags as ranked rows */}
        <Chapter num={5} title="Where the hours went">
          <div>
            {[
              { n: 'Deep work', c: 42, h: '9h 10m' },
              { n: 'Errands',   c: 24, h: '3h 40m' },
              { n: 'Writing',   c: 19, h: '3h 20m' },
              { n: 'Health',    c: 15, h: '2h 05m' },
              { n: 'Other',     c: 28, h: '1h 20m' },
            ].map((t, i, arr) => (
              <div key={t.n} style={{
                display: 'flex', alignItems: 'center', gap: 10,
                padding: '12px 0',
                borderBottom: i < arr.length - 1 ? `1px solid ${hair}` : 'none',
              }}>
                <div style={{
                  fontFamily: mono, fontSize: 11, color: fgMuted,
                  width: 22, fontWeight: 600, letterSpacing: 0.5,
                }}>{String(i+1).padStart(2,'0')}</div>
                <div style={{ flex: 1, fontSize: 15, fontWeight: 600 }}>{t.n}</div>
                <div style={{
                  fontFamily: mono, fontSize: 11, color: fgMuted,
                  width: 60, textAlign: 'right',
                }}>{t.c} tasks</div>
                <div style={{
                  padding: '3px 10px', borderRadius: 999,
                  background: i === 0 ? fg : (dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.05)'),
                  color: i === 0 ? surface : fg,
                  fontSize: 11, fontWeight: 700,
                  fontVariantNumeric: 'tabular-nums',
                  minWidth: 54, textAlign: 'center',
                }}>{t.h}</div>
              </div>
            ))}
          </div>
        </Chapter>

        {/* Chapter 6 — Focus */}
        <Chapter num={6} title="Focus · 19h 35m">
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <div style={{
              padding: 14, borderRadius: 16, background: tile,
            }}>
              <div style={{ fontFamily: mono, fontSize: 10, fontWeight: 700, letterSpacing: 1, color: fgMuted }}>POMODOROS</div>
              <div style={{ fontSize: 34, fontWeight: 800, letterSpacing: -1, marginTop: 8 }}>47</div>
            </div>
            <div style={{
              padding: 14, borderRadius: 16, background: tile,
            }}>
              <div style={{ fontFamily: mono, fontSize: 10, fontWeight: 700, letterSpacing: 1, color: fgMuted }}>LONGEST</div>
              <div style={{ fontSize: 34, fontWeight: 800, letterSpacing: -1, marginTop: 8 }}>2<span style={{ fontSize: 18, color: fgMuted }}>h</span> 05</div>
            </div>
          </div>
        </Chapter>

        {/* Chapter 7 — Milestones */}
        <Chapter num={7} title="Personal bests">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
            {[
              { k: 'Longest streak',   v: '34', u: 'days', when: 'Jan 2026' },
              { k: 'Most in one day',  v: '17', u: 'tasks', when: 'Mar 4' },
              { k: 'Best focus block', v: '3h 12', u: 'min', when: 'Feb 21' },
              { k: 'Perfect weeks',    v: '06', u: 'ever', when: 'lifetime' },
            ].map((m, i, arr) => (
              <div key={m.k} style={{
                display: 'flex', alignItems: 'baseline', justifyContent: 'space-between',
                padding: '12px 0',
                borderTop: i === 0 ? `1px solid ${hair}` : 'none',
                borderBottom: `1px solid ${hair}`,
              }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>{m.k}</div>
                  <div style={{ fontFamily: mono, fontSize: 10, color: fgMuted, marginTop: 2, letterSpacing: 0.5 }}>{m.when.toUpperCase()}</div>
                </div>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
                  <div style={{ fontSize: 22, fontWeight: 800, letterSpacing: -0.6 }}>{m.v}</div>
                  <div style={{ fontSize: 11, color: fgMuted }}>{m.u}</div>
                </div>
              </div>
            ))}
          </div>
        </Chapter>

        {/* Chapter 8 — Weekly closer */}
        <Chapter num={8} title="The week in one line">
          <div style={{
            padding: 16, borderRadius: 20,
            background: accent, color: '#000',
            fontSize: 16, lineHeight: '22px', fontWeight: 500, letterSpacing: -0.2,
            textWrap: 'pretty',
          }}>
            Good week. Forty-two tasks, twenty-three days in a row, nearly twenty hours of focus — and mornings clearly working for you.
          </div>
        </Chapter>

        <div style={{ height: 20 }}/>
      </div>
    </div>
  );
};

Object.assign(window, { V3Ribbon });
