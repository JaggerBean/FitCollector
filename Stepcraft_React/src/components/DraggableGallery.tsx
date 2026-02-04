import { useRef, useState } from "react";
import openHandSvg from "../assets/cursor/hand-svgrepo-com.svg";
import grabHandSvg from "../assets/cursor/grab-svgrepo-com (1).svg";

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
        {dragHintVisible && <span className="sr-only">Drag to explore</span>}
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
        {items.map((it, index) => {
          const showHint = dragHintVisible && index === 0;

          return (
          <div
            key={it.label}
            className="relative min-w-[220px] flex-none rounded-2xl border border-slate-800/70 bg-slate-900/40 p-5 text-[13px] text-slate-300 sm:min-w-[260px] sm:p-6 sm:text-sm"
          >
            {it.label}
            <div className="relative mt-4 rounded-xl border border-slate-800/70 bg-slate-950/40 p-4 text-xs text-slate-500">
              {showHint && (
                <div className="drag-hint-overlay" aria-hidden="true">
                  <div className="drag-hint-rail">
                    <div className="drag-hint-tile" />
                    <div className="drag-hint-hand">
                      <img
                        src={openHandSvg}
                        alt=""
                        className="drag-hint-hand-icon drag-hint-hand-open"
                        loading="lazy"
                      />
                      <img
                        src={grabHandSvg}
                        alt=""
                        className="drag-hint-hand-icon drag-hint-hand-closed"
                        loading="lazy"
                      />
                    </div>
                  </div>
                </div>
              )}
              Image placeholder
            </div>
          </div>
          );
        })}
      </div>
    </div>
  );
}
