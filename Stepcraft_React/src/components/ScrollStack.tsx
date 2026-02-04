import { useEffect, useMemo, useRef, useState } from "react";

type Scene = {
  eyebrow: string;
  title: string;
  body: string;
  imageAlt: string;
  imageUrl?: string;
};

function clamp01(n: number) {
  return Math.min(1, Math.max(0, n));
}

export function PitchScrollScene({ scenes }: { scenes: Scene[] }) {
  const wrapRef = useRef<HTMLDivElement>(null);

  const safeScenes = useMemo(() => scenes.slice(0, Math.max(1, scenes.length)), [scenes]);

  const progressRef = useRef(0);
  const lastPushedPRef = useRef(-1);
  const lastActiveRef = useRef(-1);
  const lastPinnedRef = useRef(false);

  const [p, setP] = useState(0); // 0..1 (smoothed)
  const [active, setActive] = useState(0);
  const [isPinned, setIsPinned] = useState(false);

  useEffect(() => {
    const wrap = wrapRef.current;
    if (!wrap) return;

    let rafId = 0;

    const tick = () => {
      const rect = wrap.getBoundingClientRect();
      const vh = window.innerHeight || 1;

      const total = rect.height - vh;
      const raw = clamp01(total > 0 ? -rect.top / total : 0);
      const pinnedNow = rect.top <= 0 && rect.bottom >= vh;

      // smooth progress (fluid feel)
      progressRef.current = progressRef.current + (raw - progressRef.current) * 0.12;

      const nextP = progressRef.current;
      const nextActive = Math.min(safeScenes.length - 1, Math.floor(nextP * safeScenes.length));

      // Throttle state updates: update p only if it moved enough
      if (Math.abs(nextP - lastPushedPRef.current) > 0.002) {
        lastPushedPRef.current = nextP;
        setP(nextP);
      }

      if (nextActive !== lastActiveRef.current) {
        lastActiveRef.current = nextActive;
        setActive(nextActive);
      }

      if (pinnedNow !== lastPinnedRef.current) {
        lastPinnedRef.current = pinnedNow;
        setIsPinned(pinnedNow);
      }

      rafId = requestAnimationFrame(tick);
    };

    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [safeScenes.length]);

  const sceneCount = safeScenes.length;

  // Used to drive crossfades + motion between scenes
  const scaled = p * sceneCount;
  const baseIndex = Math.min(sceneCount - 1, Math.floor(scaled));
  const t = clamp01(scaled - baseIndex); // 0..1 within current step

  const showScrollHint = isPinned && p < 0.985;

  const onScrollHintClick = () => {
    if (!sceneCount) return;
    const wrap = wrapRef.current;
    if (!wrap) return;

    const rect = wrap.getBoundingClientRect();
    const vh = window.innerHeight || 1;
    const total = rect.height - vh;
    if (total <= 0) return;

    const nextIndex = active >= sceneCount - 1 ? sceneCount : active + 1;
    const targetP = clamp01((nextIndex + 0.02) / sceneCount);
    const targetTop = window.scrollY + rect.top;
    const targetY = targetTop + targetP * total;

    window.scrollTo({ top: targetY, behavior: "smooth" });
  };

  return (
    <div ref={wrapRef} className="relative min-h-[320vh]">
      <div className="sticky top-0 h-screen w-full overflow-hidden rounded-3xl border border-slate-800/60 bg-slate-950/95">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(16,185,129,0.14),transparent_55%)]" />

        <div className="relative mx-auto grid h-full max-w-6xl grid-cols-1 gap-10 px-6 py-10 md:grid-cols-2 md:px-10">
          {/* Left: copy */}
          <div className="flex flex-col justify-center">
            <div className="text-xs uppercase tracking-[0.3em] text-slate-400">StepCraft in action</div>

            {safeScenes.map((s, i) => {
              // distance from active scene
              const d = i - active; // -2, -1, 0, +1, ...
              const isCurrent = i === active;

              // make adjacent scenes more visible than far scenes
              const baseOpacity = isCurrent ? 1 : Math.max(0.08, 0.22 - Math.abs(d) * 0.07);

              // motion: current slides up slightly as you progress to next scene
              // and next slides in a bit
              let y = 0;
              if (i === baseIndex) y = -10 * t;
              if (i === baseIndex + 1) y = 14 * (1 - t);

              const scale = isCurrent ? 1 : 0.98;

              return (
                <div
                  key={s.title}
                  className="mt-8"
                  style={{
                    opacity: baseOpacity,
                    transform: `translate3d(0, ${y}px, 0) scale(${scale})`,
                    transition: "opacity 280ms ease, transform 280ms ease",
                  }}
                >
                  <div className="text-xs uppercase tracking-[0.2em] text-emerald-300/70">{s.eyebrow}</div>
                  <h3 className="mt-3 text-2xl font-semibold text-white">{s.title}</h3>
                  <p className="mt-3 text-sm leading-relaxed text-slate-300">{s.body}</p>
                </div>
              );
            })}
          </div>

          {/* Right: imagery */}
          <div className="relative flex items-center justify-center">
            <div
              className="relative w-full max-w-md aspect-[9/16] overflow-hidden rounded-2xl border border-slate-800/70 bg-slate-900/40"
              style={{
                transform: `translate3d(0, ${-24 * p}px, 0) scale(${0.98 + 0.03 * p})`,
                transition: "transform 60ms linear",
              }}
            >
              {safeScenes.map((s, i) => {
                // crossfade based on proximity to scaled position
                const dist = Math.abs(i - scaled);
                const opacity = clamp01(1 - dist); // 1 when centered, 0 when >=1 away

                // subtle depth per card
                const z = i === active ? 1 : 0.995;
                const blur = i === active ? 0 : Math.min(6, dist * 6);

                return (
                  <div
                    key={s.imageAlt}
                    className="absolute inset-0"
                    style={{
                      opacity,
                      transform: `scale(${z})`,
                      filter: blur ? `blur(${blur}px)` : undefined,
                      transition: "opacity 320ms ease, transform 320ms ease, filter 320ms ease",
                    }}
                  >
                    {s.imageUrl ? (
                      <img src={s.imageUrl} alt={s.imageAlt} className="h-full w-full object-cover" />
                    ) : (
                      <div className="flex h-full w-full items-center justify-center text-xs text-slate-400">
                        Image placeholder ({s.imageAlt})
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        <button
          type="button"
          aria-label="Keep scrolling"
          disabled={!showScrollHint}
          onClick={onScrollHintClick}
          className={[
            "absolute bottom-6 left-1/2 z-20 -translate-x-1/2 transition-all duration-300",
            showScrollHint ? "opacity-100 translate-y-0" : "pointer-events-none opacity-0 translate-y-3",
          ].join(" ")}
        >
          <div className="flex flex-col items-center gap-2">
            <div className="grid h-11 w-11 place-items-center rounded-full border border-slate-700/60 bg-slate-950/35 text-slate-300/80 shadow-[0_10px_30px_rgba(0,0,0,0.35)] backdrop-blur">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                className="h-5 w-5 motion-safe:animate-bounce"
                style={{ animationDuration: "1.9s" }}
              >
                <path
                  d="M12 5v11m0 0 6-6m-6 6-6-6"
                  stroke="currentColor"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </div>
            <div className="text-[11px] uppercase tracking-[0.3em] text-slate-400">Keep scrolling</div>
          </div>
        </button>
      </div>
    </div>
  );
}
