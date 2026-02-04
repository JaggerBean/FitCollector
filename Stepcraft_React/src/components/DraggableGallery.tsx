import { useEffect, useRef } from "react";

export function DragGallery({ items }: { items: { label: string }[] }) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const isDown = useRef(false);
  const hasDraggedRef = useRef(false);
  const startX = useRef(0);
  const startScrollLeft = useRef(0);
  const autoScrollActiveRef = useRef(false);
  const autoScrollDirRef = useRef(1);
  const intervalRef = useRef<number | null>(null);
  const autoScrollStoppedRef = useRef(false);
  const lastTimeRef = useRef(0);

  const stopAutoScroll = () => {
    if (!autoScrollActiveRef.current && autoScrollStoppedRef.current) return;
    autoScrollActiveRef.current = false;
    autoScrollStoppedRef.current = true;
    if (intervalRef.current != null) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  };

  useEffect(() => {
    const el = scrollerRef.current;
    if (!el) return;

    const speedPxPerSec = 48;

    const tick = (now: number) => {
      if (autoScrollStoppedRef.current) return;

      const rect = el.getBoundingClientRect();
      const vh = window.innerHeight || 1;
      const isVisible = rect.bottom > 0 && rect.top < vh;
      const maxScroll = el.scrollWidth - el.clientWidth;
      const canScroll = maxScroll > 1;

      autoScrollActiveRef.current = isVisible && canScroll;

      if (autoScrollActiveRef.current) {
        const last = lastTimeRef.current || now;
        const dt = Math.min(0.05, Math.max(0, (now - last) / 1000));
        const next = el.scrollLeft + speedPxPerSec * dt * autoScrollDirRef.current;
        if (next >= maxScroll) {
          el.scrollLeft = maxScroll;
          autoScrollDirRef.current = -1;
        } else if (next <= 0) {
          el.scrollLeft = 0;
          autoScrollDirRef.current = 1;
        } else {
          el.scrollLeft = next;
        }
      }

      lastTimeRef.current = now;
    };

    lastTimeRef.current = performance.now();
    intervalRef.current = window.setInterval(() => {
      tick(performance.now());
    }, 16);

    return () => {
      if (intervalRef.current != null) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
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
          isDown.current = true;
          hasDraggedRef.current = false;
          startX.current = e.clientX;
          startScrollLeft.current = el.scrollLeft;
          (e.currentTarget as HTMLDivElement).setPointerCapture(e.pointerId);
        }}
        onPointerMove={(e) => {
          const el = scrollerRef.current;
          if (!el || !isDown.current) return;
          e.preventDefault();
          const dx = e.clientX - startX.current;
          if (!hasDraggedRef.current && Math.abs(dx) > 4) {
            hasDraggedRef.current = true;
            stopAutoScroll();
          }
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
