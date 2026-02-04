import { useRef, useState } from "react";

export function DragGallery({ items }: { items: { label: string }[] }) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const isDown = useRef(false);
  const startX = useRef(0);
  const startScrollLeft = useRef(0);
  const [hasDragged, setHasDragged] = useState(false);
  const dragHintVisible = !hasDragged;

  return (
    <div className="rounded-3xl border border-slate-800/60 bg-slate-950/70 p-6">
      <div className="flex items-center justify-between gap-4">
        <h3 className="text-lg font-semibold text-white">Screens & moments</h3>
        {dragHintVisible && (
          <div className="drag-hint flex items-center gap-2 rounded-full border border-slate-800 px-3 py-2 text-slate-400">
            <span className="drag-hint-icon" aria-hidden="true">
              <span className="drag-hint-mouse">🖱️</span>
              <span className="drag-hint-hand">🤏</span>
            </span>
            <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" aria-hidden="true">
              <path d="M15 6 9 12l6 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            <div className="h-[2px] w-6 rounded-full bg-slate-700/70" />
            <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" aria-hidden="true">
              <path d="m9 6 6 6-6 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
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
            className="min-w-[260px] flex-none rounded-2xl border border-slate-800/70 bg-slate-900/40 p-6 text-sm text-slate-300"
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
