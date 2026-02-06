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

function applyStepHold(progress: number, steps: number, hold = 0.35) {
  const clamped = clamp01(progress);
  if (steps <= 1) return clamped;

  const step = 1 / steps;
  const idx = Math.min(steps - 1, Math.floor(clamped / step));
  const local = (clamped - idx * step) / step;

  const holdClamped = Math.min(0.45, Math.max(0, hold));
  if (holdClamped <= 0) return clamped;

  let u = 0;
  if (local <= holdClamped) {
    u = 0;
  } else if (local >= 1 - holdClamped) {
    u = 1;
  } else {
    const span = 1 - holdClamped * 2;
    u = (local - holdClamped) / span;
  }

  return (idx + u) / steps;
}

export function PitchScrollScene({
  scenes,
}: {
  scenes: Scene[];
}) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const copyViewportRef = useRef<HTMLDivElement>(null);
  const copyListRef = useRef<HTMLDivElement>(null);
  const sceneRefs = useRef<Array<HTMLDivElement | null>>([]);

  const safeScenes = useMemo(() => scenes.slice(0, Math.max(1, scenes.length)), [scenes]);

  const progressRef = useRef(0);
  const lastPushedPRef = useRef(-1);
  const lastActiveRef = useRef(-1);
  const lastPinnedRef = useRef(false);

  const [p, setP] = useState(0); // 0..1 (smoothed)
  const [active, setActive] = useState(0);
  const [isPinned, setIsPinned] = useState(false);
  const [sceneOffsets, setSceneOffsets] = useState<number[]>([]);

  useEffect(() => {
    const wrap = wrapRef.current;
    if (!wrap) return;

    let rafId = 0;

    const tick = () => {
      const rect = wrap.getBoundingClientRect();
      const vh = window.innerHeight || 1;
      const isMobile = window.innerWidth < 768;

      const total = rect.height - vh;
      const raw = clamp01(total > 0 ? -rect.top / total : 0);
      const hold = isMobile ? 0.16 : 0.45;
      const held = applyStepHold(raw, safeScenes.length, hold);
      const pinnedNow = rect.top <= 0 && rect.bottom >= vh;

      // smooth progress (fluid feel)
      progressRef.current = progressRef.current + (held - progressRef.current) * 0.12;

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

  useEffect(() => {
    const viewport = copyViewportRef.current;
    const list = copyListRef.current;
    if (!viewport || !list) return;

    const measure = () => {
      const viewportHeight = viewport.clientHeight;
      const rawOffsets = safeScenes.map((_, i) => {
        const el = sceneRefs.current[i];
        if (!el) return 0;
        const top = el.offsetTop;
        const height = el.offsetHeight;
        const bottom = top + height;
        return Math.max(0, bottom - viewportHeight);
      });

      const maxOffset = rawOffsets.reduce((max, value) => Math.max(max, value), 0);
      const span = Math.max(1, safeScenes.length - 1);
      const distributed = rawOffsets.map((value, i) => {
        if (maxOffset <= 0) return 0;
        const ramp = (maxOffset * i) / span;
        return Math.max(value, ramp);
      });

      setSceneOffsets(distributed);
    };

    measure();

    const ro = new ResizeObserver(measure);
    ro.observe(viewport);
    sceneRefs.current.forEach((el) => {
      if (el) ro.observe(el);
    });

    return () => ro.disconnect();
  }, [safeScenes.length]);



  const sceneCount = safeScenes.length;

  // Used to drive crossfades + motion between scenes
  const scaled = p * sceneCount;
  const visualScaled = Math.min(sceneCount - 1, scaled);
  const baseIndex = Math.min(sceneCount - 1, Math.floor(visualScaled));
  const t = clamp01(visualScaled - baseIndex); // 0..1 within current step
  const nextIndex = Math.min(sceneCount - 1, baseIndex + 1);
  const currentOffset = sceneOffsets[baseIndex] ?? 0;
  const nextOffset = sceneOffsets[nextIndex] ?? currentOffset;
  const scrollOffset = currentOffset + (nextOffset - currentOffset) * t;
  const mobileSceneA = safeScenes[baseIndex];
  const mobileSceneB = safeScenes[nextIndex];

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
    <div ref={wrapRef} className="relative min-h-[300vh] sm:min-h-[320vh]">
      <div className="sticky top-0 h-screen w-full overflow-hidden rounded-2xl border border-slate-800/60 bg-slate-950/95 sm:rounded-3xl">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(16,185,129,0.14),transparent_55%)]" />

        <div className="relative mx-auto h-full max-w-6xl px-4 py-8 sm:px-6 sm:py-10 md:px-10">
          {/* Desktop layout */}
          <div className="hidden h-full grid-cols-1 gap-8 sm:gap-10 md:grid md:grid-cols-2">
            {/* Left: copy */}
            <div className="relative flex min-h-0 flex-col justify-center">
              <div className="relative z-20 text-[10px] uppercase tracking-[0.3em] text-slate-400 sm:text-xs">
                StepCraft in action
              </div>

              <div ref={copyViewportRef} className="relative z-0 min-h-0 flex-1 overflow-hidden">
                <div
                  ref={copyListRef}
                  className="relative"
                  style={{
                    transform: `translate3d(0, ${-scrollOffset}px, 0)`,
                    transition: "transform 200ms ease",
                  }}
                >
                {safeScenes.map((s, i) => {
                  const dist = Math.abs(i - visualScaled);
                  const fade = clamp01(1 - dist);

                  const baseOpacity = 0.08 + 0.92 * fade;

                  // motion: current slides up slightly as you progress to next scene
                  // and next slides in a bit
                  let y = 0;
                  if (i === baseIndex) y = -10 * t;
                  if (i === baseIndex + 1) y = 14 * (1 - t);

                  const scale = 0.98 + 0.02 * fade;

                  return (
                    <div
                      key={s.title}
                      className="mt-8"
                      ref={(el) => {
                        sceneRefs.current[i] = el;
                      }}
                      style={{
                        opacity: baseOpacity,
                        transform: `translate3d(0, ${y}px, 0) scale(${scale})`,
                        transition: "opacity 280ms ease, transform 280ms ease",
                      }}
                    >
                      <div className="text-xs uppercase tracking-[0.2em] text-emerald-300/70">{s.eyebrow}</div>
                      <h3 className="mt-3 text-xl font-semibold text-white sm:text-2xl">{s.title}</h3>
                      <p className="mt-3 text-sm leading-relaxed text-slate-300">{s.body}</p>
                    </div>
                  );
                })}
                </div>
              </div>
            </div>

            {/* Right: imagery */}
            <div className="relative flex items-center justify-center">
              <div
                className="relative w-full max-w-[260px] aspect-[9/16] overflow-hidden rounded-2xl border border-slate-800/70 bg-slate-900/40 sm:max-w-sm md:max-w-md"
                style={{
                  transform: `translate3d(0, ${-24 * p}px, 0) scale(${0.98 + 0.03 * p})`,
                  transition: "transform 60ms linear",
                }}
              >
                {safeScenes.map((s, i) => {
                  // crossfade based on proximity to scaled position
                  const dist = Math.abs(i - visualScaled);
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

          {/* Mobile layout */}
          <div className="flex h-full flex-col justify-start gap-4 pt-2 md:hidden">
            <div className="text-[10px] uppercase tracking-[0.3em] text-slate-400">StepCraft in action</div>

            <div className="relative min-h-[260px] sm:min-h-[300px]">
              <div
                className="absolute inset-0"
                style={{
                  opacity: 1 - t,
                  transform: `translate3d(0, ${-10 * t}px, 0) scale(${1 - 0.02 * t})`,
                  willChange: "opacity, transform",
                }}
              >
                <div className="text-xs uppercase tracking-[0.2em] text-emerald-300/70">
                  {mobileSceneA.eyebrow}
                </div>
                <h3 className="mt-3 text-xl font-semibold text-white">{mobileSceneA.title}</h3>
                <p className="mt-3 text-sm leading-relaxed text-slate-300">{mobileSceneA.body}</p>
              </div>
              {nextIndex !== baseIndex && (
                <div
                  className="absolute inset-0"
                  style={{
                    opacity: t,
                    transform: `translate3d(0, ${12 * (1 - t)}px, 0) scale(${0.98 + 0.02 * t})`,
                    willChange: "opacity, transform",
                  }}
                >
                  <div className="text-xs uppercase tracking-[0.2em] text-emerald-300/70">
                    {mobileSceneB.eyebrow}
                  </div>
                  <h3 className="mt-3 text-xl font-semibold text-white">{mobileSceneB.title}</h3>
                  <p className="mt-3 text-sm leading-relaxed text-slate-300">{mobileSceneB.body}</p>
                </div>
              )}
            </div>

            <div className="flex items-center justify-center">
              <div className="relative h-[42vh] w-auto max-w-[240px] aspect-[9/16] overflow-hidden rounded-2xl border border-slate-800/70 bg-slate-900/40 sm:h-[48vh] sm:max-w-[280px]">
                <div
                  className="absolute inset-0"
                  style={{
                    opacity: 1 - t,
                    transform: `translate3d(0, ${-6 * t}px, 0) scale(${1 - 0.015 * t})`,
                    willChange: "opacity, transform",
                  }}
                >
                  {mobileSceneA.imageUrl ? (
                    <img src={mobileSceneA.imageUrl} alt={mobileSceneA.imageAlt} className="h-full w-full object-cover" />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center text-xs text-slate-400">
                      Image placeholder ({mobileSceneA.imageAlt})
                    </div>
                  )}
                </div>
                {nextIndex !== baseIndex && (
                  <div
                    className="absolute inset-0"
                    style={{
                      opacity: t,
                      transform: `translate3d(0, ${6 * (1 - t)}px, 0) scale(${0.985 + 0.015 * t})`,
                      willChange: "opacity, transform",
                    }}
                  >
                    {mobileSceneB.imageUrl ? (
                      <img src={mobileSceneB.imageUrl} alt={mobileSceneB.imageAlt} className="h-full w-full object-cover" />
                    ) : (
                      <div className="flex h-full w-full items-center justify-center text-xs text-slate-400">
                        Image placeholder ({mobileSceneB.imageAlt})
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        <button
          type="button"
          aria-label="Keep scrolling"
          disabled={!showScrollHint}
          onClick={onScrollHintClick}
          className={[
            "absolute bottom-5 left-1/2 z-20 -translate-x-1/2 transition-all duration-300 sm:bottom-6",
            showScrollHint ? "opacity-100 translate-y-0" : "pointer-events-none opacity-0 translate-y-3",
          ].join(" ")}
        >
          <div className="flex flex-col items-center gap-2">
            <div className="grid h-10 w-10 place-items-center rounded-full border border-slate-700/60 bg-slate-950/35 text-slate-300/80 shadow-[0_10px_30px_rgba(0,0,0,0.35)] backdrop-blur sm:h-11 sm:w-11">
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
            <div className="text-[10px] uppercase tracking-[0.3em] text-slate-400 sm:text-[11px]">
              Keep scrolling
            </div>
          </div>
        </button>
      </div>
    </div>
  );
}
