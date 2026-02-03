import { useRef, useEffect, useMemo } from "react";

type Scene = {
  eyebrow: string;
  title: string;
  body: string;
  imageAlt: string;
  imageUrl?: string; // optional (use placeholders for now)
};

function clamp01(n: number) {
  return Math.min(1, Math.max(0, n));
}

export function PitchScrollScene({ scenes }: { scenes: Scene[] }) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const stickyRef = useRef<HTMLDivElement>(null);
  const progressRef = useRef(0);
  const activeRef = useRef(0);

  const safeScenes = useMemo(() => scenes.slice(0, Math.max(1, scenes.length)), [scenes]);

  useEffect(() => {
    const wrap = wrapRef.current;
    const sticky = stickyRef.current;
    if (!wrap || !sticky) return;

    let rafId: number | null = null;

    const tick = () => {
      const rect = wrap.getBoundingClientRect();
      const vh = window.innerHeight;

      const total = rect.height - vh;
      const scrolled = clamp01(total > 0 ? (-rect.top / total) : 0);

      // smooth the progress a bit (this is the “fluid” part)
      progressRef.current = progressRef.current + (scrolled - progressRef.current) * 0.12;

      const p = progressRef.current;
      const nextActive = Math.min(safeScenes.length - 1, Math.floor(p * safeScenes.length));

      // only touch the DOM when something actually changes
      if (nextActive !== activeRef.current) {
        activeRef.current = nextActive;
        sticky.setAttribute("data-active", String(nextActive));
      }

      // expose progress for CSS transforms
      sticky.style.setProperty("--p", String(p));

      rafId = requestAnimationFrame(tick);
    };

    rafId = requestAnimationFrame(tick);
    return () => {
      if (rafId) cancelAnimationFrame(rafId);
    };
  }, [safeScenes.length]);

  return (
    <div ref={wrapRef} className="relative min-h-[320vh]">
      <div
        ref={stickyRef}
        className="sticky top-0 h-screen w-full overflow-hidden rounded-3xl border border-slate-800/60 bg-slate-950/95"
      >
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(16,185,129,0.14),transparent_55%)]" />

        <div className="relative mx-auto grid h-full max-w-6xl grid-cols-1 gap-10 px-6 py-10 md:grid-cols-2 md:px-10">
          {/* Left: copy */}
          <div className="flex flex-col justify-center">
            <div className="text-xs uppercase tracking-[0.3em] text-slate-400">Scroll story</div>

            {safeScenes.map((s, i) => (
              <div
                key={s.title}
                className="mt-8 transition-opacity duration-500"
                style={{
                  opacity: i === Number(stickyRef.current?.getAttribute("data-active") ?? 0) ? 1 : 0.18,
                }}
              >
                <div className="text-xs uppercase tracking-[0.2em] text-emerald-300/70">{s.eyebrow}</div>
                <h3 className="mt-3 text-2xl font-semibold text-white">{s.title}</h3>
                <p className="mt-3 text-sm leading-relaxed text-slate-300">{s.body}</p>
              </div>
            ))}
          </div>

          {/* Right: imagery */}
          <div className="relative flex items-center justify-center">
            <div
              className="relative h-[70vh] w-full max-w-md overflow-hidden rounded-2xl border border-slate-800/70 bg-slate-900/40"
              style={{
                // subtle parallax/scale tied to --p
                transform: "translate3d(0, calc(var(--p) * -24px), 0) scale(calc(0.98 + var(--p) * 0.03))",
                transition: "transform 120ms linear",
              }}
            >
              {/* Crossfade images by active index (simple + effective) */}
              {safeScenes.map((s, i) => (
                <div
                  key={s.imageAlt}
                  className="absolute inset-0 transition-opacity duration-500"
                  style={{
                    opacity: i === Number(stickyRef.current?.getAttribute("data-active") ?? 0) ? 1 : 0,
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
              ))}
            </div>

            <div className="pointer-events-none absolute -bottom-6 left-6 rounded-full border border-slate-700/70 bg-slate-950/60 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-400">
              Keep scrolling
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
