import { useEffect, useRef } from "react";

export function DragGallery({ items }: { items: { label: string }[] }) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const isDown = useRef(false);
  const startX = useRef(0);
  const startScrollLeft = useRef(0);
  const autoScrollActiveRef = useRef(false);
  const autoScrollDirRef = useRef(1);
  const rafRef = useRef(0);
  const visibilityRafRef = useRef(0);
  const autoScrollStoppedRef = useRef(false);

  const stopAutoScroll = () => {
    if (!autoScrollActiveRef.current && autoScrollStoppedRef.current) return;
    autoScrollActiveRef.current = false;
    autoScrollStoppedRef.current = true;
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
  };

  useEffect(() => {
    const el = scrollerRef.current;
    if (!el) return;

    const speed = 0.35;

    const startAutoScroll = () => {
      if (autoScrollStoppedRef.current || autoScrollActiveRef.current) return;
      autoScrollActiveRef.current = true;
      rafRef.current = requestAnimationFrame(tick);
    };

    const pauseAutoScroll = () => {
      if (!autoScrollActiveRef.current) return;
      autoScrollActiveRef.current = false;
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };

    const tick = () => {
      if (!autoScrollActiveRef.current) return;
      const maxScroll = el.scrollWidth - el.clientWidth;
      if (maxScroll <= 0) {
        rafRef.current = requestAnimationFrame(tick);
        return;
      }

      const next = el.scrollLeft + speed * autoScrollDirRef.current;
      if (next >= maxScroll) {
        el.scrollLeft = maxScroll;
        autoScrollDirRef.current = -1;
      } else if (next <= 0) {
        el.scrollLeft = 0;
        autoScrollDirRef.current = 1;
      } else {
        el.scrollLeft = next;
      }

      rafRef.current = requestAnimationFrame(tick);
    };

    let lastCheck = 0;
    const visibilityThreshold = 0.01;
    const checkVisibility = (now: number) => {
      if (autoScrollStoppedRef.current) return;
      if (now - lastCheck >= 150) {
        lastCheck = now;
        const rect = el.getBoundingClientRect();
        const vh = window.innerHeight || 1;
        const visibleHeight = Math.max(0, Math.min(rect.bottom, vh) - Math.max(rect.top, 0));
        const ratio = rect.height > 0 ? visibleHeight / rect.height : 0;
        if (ratio >= visibilityThreshold) {
          startAutoScroll();
        } else {
          pauseAutoScroll();
        }
      }
      visibilityRafRef.current = requestAnimationFrame(checkVisibility);
    };

    visibilityRafRef.current = requestAnimationFrame(checkVisibility);

    return () => {
      if (visibilityRafRef.current) cancelAnimationFrame(visibilityRafRef.current);
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, []);

  return (
    <div className="rounded-3xl border border-slate-800/60 bg-slate-950/70 p-4 sm:p-6">
      <div className="flex items-center justify-between gap-4">
        <h3 className="text-base font-semibold text-white sm:text-lg">Screens & moments</h3>
      </div>

      <div
        ref={scrollerRef}
        className="drag-gallery-scroller mt-6 flex gap-4 overflow-x-auto pb-3"
        style={{ scrollbarWidth: "none" as any, touchAction: "pan-y" }}
        onPointerDown={(e) => {
          const el = scrollerRef.current;
          if (!el) return;
          e.preventDefault();
          stopAutoScroll();
          isDown.current = true;
          startX.current = e.clientX;
          startScrollLeft.current = el.scrollLeft;
          (e.currentTarget as HTMLDivElement).setPointerCapture(e.pointerId);
        }}
        onPointerMove={(e) => {
          const el = scrollerRef.current;
          if (!el || !isDown.current) return;
          e.preventDefault();
          const dx = e.clientX - startX.current;
          el.scrollLeft = startScrollLeft.current - dx;
        }}
        onPointerUp={() => {
          isDown.current = false;
        }}
        onPointerCancel={() => {
          isDown.current = false;
        }}
        onTouchStart={() => {
          stopAutoScroll();
        }}
        onDragStart={(e) => {
          e.preventDefault();
        }}
      >
        {items.map((it) => (
          <div
            key={it.label}
            className="drag-gallery-item min-w-[220px] flex-none rounded-2xl border border-slate-800/70 bg-slate-900/40 p-5 text-[13px] text-slate-300 sm:min-w-[260px] sm:p-6 sm:text-sm"
          >
            {it.label}
            <div className="mt-4 rounded-xl border border-slate-800/70 bg-slate-950/40 p-4 text-xs text-slate-500">
              Image placeholder
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
