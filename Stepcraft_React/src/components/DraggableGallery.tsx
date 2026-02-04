import { useEffect, useMemo, useRef } from "react";

type DragGalleryItem = {
  label: string;
  imageUrl?: string;
  imageAlt?: string;
};

export function DragGallery({ items }: { items: DragGalleryItem[] }) {
  const loopCount = 5;
  const scrollerRef = useRef<HTMLDivElement>(null);
  const isDown = useRef(false);
  const hasDraggedRef = useRef(false);
  const startX = useRef(0);
  const startScrollLeft = useRef(0);
  const autoScrollActiveRef = useRef(false);
  const autoScrollDirRef = useRef(1);
  const intervalRef = useRef<number | null>(null);
  const lastTimeRef = useRef(0);
  const copyWidthRef = useRef(0);
  const pauseUntilRef = useRef(0);
  const bodyOverflowRef = useRef<string | null>(null);

  const loopedItems = useMemo(() => {
    if (!items.length) return [];
    const result: Array<DragGalleryItem & { key: string }> = [];
    for (let copyIndex = 0; copyIndex < loopCount; copyIndex += 1) {
      items.forEach((item, index) => {
        result.push({ ...item, key: `${copyIndex}-${index}-${item.label}` });
      });
    }
    return result;
  }, [items, loopCount]);

  const pauseAutoScroll = (delayMs = 5000) => {
    const now = performance.now();
    pauseUntilRef.current = Math.max(pauseUntilRef.current, now + delayMs);
    autoScrollActiveRef.current = false;
  };

  const lockBodyScroll = () => {
    if (bodyOverflowRef.current !== null) return;
    bodyOverflowRef.current = document.body.style.overflow;
    document.body.style.overflow = "hidden";
  };

  const unlockBodyScroll = () => {
    if (bodyOverflowRef.current === null) return;
    document.body.style.overflow = bodyOverflowRef.current;
    bodyOverflowRef.current = null;
  };

  useEffect(() => {
    const el = scrollerRef.current;
    if (!el) return;

    const speedPxPerSec = 48;

    const wrapScroll = () => {
      const copyWidth = copyWidthRef.current;
      if (!copyWidth) return;
      const lower = copyWidth;
      const upper = copyWidth * 3;
      if (el.scrollLeft <= lower) {
        el.scrollLeft += copyWidth * 2;
      } else if (el.scrollLeft >= upper) {
        el.scrollLeft -= copyWidth * 2;
      }
    };

    const tick = (now: number) => {
      const rect = el.getBoundingClientRect();
      const vh = window.innerHeight || 1;
      const isVisible = rect.bottom > 0 && rect.top < vh;
      const maxScroll = el.scrollWidth - el.clientWidth;
      const canScroll = maxScroll > 1;
      const isUserPaused = now < pauseUntilRef.current;

      autoScrollActiveRef.current = !isUserPaused && isVisible && canScroll;

      if (autoScrollActiveRef.current) {
        const last = lastTimeRef.current || now;
        const dt = Math.min(0.05, Math.max(0, (now - last) / 1000));
        const next = el.scrollLeft + speedPxPerSec * dt * autoScrollDirRef.current;
        el.scrollLeft = next;
        wrapScroll();
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
      unlockBodyScroll();
    };
  }, []);

  useEffect(() => {
    const el = scrollerRef.current;
    if (!el) return;

    const updateCopyWidth = () => {
      const total = el.scrollWidth;
      if (!total) return;
      const copyWidth = total / loopCount;
      if (copyWidth > 0) {
        copyWidthRef.current = copyWidth;
        const lower = copyWidth;
        const upper = copyWidth * 3;
        if (el.scrollLeft <= lower) {
          el.scrollLeft += copyWidth * 2;
        } else if (el.scrollLeft >= upper) {
          el.scrollLeft -= copyWidth * 2;
        }
      }
    };

    updateCopyWidth();

    let resizeObserver: ResizeObserver | null = null;
    if (typeof ResizeObserver !== "undefined") {
      resizeObserver = new ResizeObserver(() => updateCopyWidth());
      resizeObserver.observe(el);
    } else {
      window.addEventListener("resize", updateCopyWidth);
    }

    return () => {
      if (resizeObserver) {
        resizeObserver.disconnect();
      } else {
        window.removeEventListener("resize", updateCopyWidth);
      }
    };
  }, [loopCount, items.length]);

  return (
    <div className="rounded-3xl border border-slate-800/60 bg-slate-950/70 p-4 sm:p-6">
      <div className="flex items-center justify-between gap-4">
        <h3 className="text-base font-semibold text-white sm:text-lg">Screens & moments</h3>
      </div>

      <div
        ref={scrollerRef}
        className="drag-gallery-scroller mt-6 flex gap-4 overflow-x-auto pb-3"
        style={{ scrollbarWidth: "none" as any, touchAction: "pan-x" }}
        onPointerDown={(e) => {
          const el = scrollerRef.current;
          if (!el) return;
          e.preventDefault();
          isDown.current = true;
          hasDraggedRef.current = false;
          lockBodyScroll();
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
            pauseAutoScroll();
          }
          el.scrollLeft = startScrollLeft.current - dx;
          const copyWidth = copyWidthRef.current;
          if (copyWidth) {
            const lower = copyWidth;
            const upper = copyWidth * 3;
            if (el.scrollLeft <= lower) {
              el.scrollLeft += copyWidth * 2;
              startScrollLeft.current += copyWidth * 2;
            } else if (el.scrollLeft >= upper) {
              el.scrollLeft -= copyWidth * 2;
              startScrollLeft.current -= copyWidth * 2;
            }
          }
        }}
        onPointerUp={() => {
          isDown.current = false;
          unlockBodyScroll();
        }}
        onPointerCancel={() => {
          isDown.current = false;
          unlockBodyScroll();
        }}
        onTouchStart={() => {
          pauseAutoScroll();
          lockBodyScroll();
        }}
        onTouchMove={(e) => {
          if (!isDown.current) return;
          e.preventDefault();
        }}
        onTouchEnd={() => {
          unlockBodyScroll();
        }}
        onTouchCancel={() => {
          unlockBodyScroll();
        }}
        onDragStart={(e) => {
          e.preventDefault();
        }}
      >
        {loopedItems.map((it) => (
          <div
            key={it.key}
            className="drag-gallery-item min-w-[190px] flex-none rounded-2xl border border-slate-800/70 bg-slate-900/40 p-4 text-[13px] text-slate-300 sm:min-w-[260px] sm:p-6 sm:text-sm"
          >
            {it.label}
            <div className="mt-3 h-[140px] overflow-hidden rounded-xl border border-slate-800/70 bg-slate-950/40 p-2 text-xs text-slate-500 sm:mt-4 sm:h-[200px]">
              {it.imageUrl ? (
                <img
                  src={it.imageUrl}
                  alt={it.imageAlt ?? it.label}
                  className="h-full w-full rounded-lg object-contain sm:object-cover"
                  loading="lazy"
                />
              ) : (
                <div className="p-3 text-xs text-slate-500">Image placeholder</div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
