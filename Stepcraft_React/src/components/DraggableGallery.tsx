import { useRef, useState } from "react";

export function DragGallery({ items }: { items: { label: string }[] }) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const isDown = useRef(false);
  const startX = useRef(0);
  const startScrollLeft = useRef(0);
  const [hasDragged, setHasDragged] = useState(false);
  const dragHintVisible = !hasDragged;

  return (
    <div className="rounded-3xl border border-slate-800/60 bg-slate-950/70 p-4 sm:p-6">
      <div className="flex items-center justify-between gap-4">
        <h3 className="text-base font-semibold text-white sm:text-lg">Screens & moments</h3>
        {dragHintVisible && (
          <div className="drag-hint rounded-full border border-slate-800 px-2.5 py-1.5 text-[10px] text-slate-300 sm:px-3 sm:py-2 sm:text-xs">
            <div className="drag-hint-rail" aria-hidden="true">
              <div className="drag-hint-tile" />
              <div className="drag-hint-hand">
                <svg viewBox="0 0 24 24" className="drag-hint-hand-icon" fill="none">
                  <path
                    d="M7.5 12V6.75a1.5 1.5 0 1 1 3 0v4.5m0-2.25V5.25a1.5 1.5 0 1 1 3 0v5.25m0-1.5V6.75a1.5 1.5 0 1 1 3 0v7.5m0 0-1.2 4.2a3 3 0 0 1-2.88 2.1H9.6a3 3 0 0 1-2.9-2.2l-1.1-4.2a2 2 0 0 1 1.9-2.6H9"
                    stroke="currentColor"
                    strokeWidth="1.6"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </div>
            </div>
            <span className="sr-only">Drag to explore</span>
          </div>
        )}
      </div>

      <div
        ref={scrollerRef}
        className="mt-6 flex gap-4 overflow-x-auto pb-3"
        style={{ scrollbarWidth: "none" as any }}
        onPointerDown={(e) => {
          const el = scrollerRef.current;
          if (!el) return;
          isDown.current = true;
          startX.current = e.clientX;
          startScrollLeft.current = el.scrollLeft;
          (e.currentTarget as HTMLDivElement).setPointerCapture(e.pointerId);
        }}
        onPointerMove={(e) => {
          const el = scrollerRef.current;
          if (!el || !isDown.current) return;
          const dx = e.clientX - startX.current;
          if (!hasDragged && Math.abs(dx) > 6) setHasDragged(true);
          el.scrollLeft = startScrollLeft.current - dx;
        }}
        onPointerUp={() => {
          isDown.current = false;
        }}
        onPointerCancel={() => {
          isDown.current = false;
        }}
        onScroll={(e) => {
          if (hasDragged) return;
          if ((e.currentTarget as HTMLDivElement).scrollLeft !== 0) {
            setHasDragged(true);
          }
        }}
      >
        {items.map((it) => (
          <div
            key={it.label}
            className="min-w-[220px] flex-none rounded-2xl border border-slate-800/70 bg-slate-900/40 p-5 text-[13px] text-slate-300 sm:min-w-[260px] sm:p-6 sm:text-sm"
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
