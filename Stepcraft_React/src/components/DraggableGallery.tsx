import { useRef } from "react";

export function DragGallery({ items }: { items: { label: string }[] }) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const isDown = useRef(false);
  const startX = useRef(0);
  const startScrollLeft = useRef(0);

  return (
    <div className="rounded-3xl border border-slate-800/60 bg-slate-950/70 p-6">
      <div className="flex items-center justify-between gap-4">
        <h3 className="text-lg font-semibold text-white">Screens & moments</h3>
        <div className="rounded-full border border-slate-800 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-400">
          Drag
        </div>
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
          el.scrollLeft = startScrollLeft.current - dx;
        }}
        onPointerUp={() => {
          isDown.current = false;
        }}
        onPointerCancel={() => {
          isDown.current = false;
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
