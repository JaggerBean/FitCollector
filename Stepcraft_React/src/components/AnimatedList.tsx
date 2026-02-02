import { useEffect, useMemo, useRef, useState } from "react";

type AnimatedListProps<T> = {
  items: T[];
  renderItem: (item: T, index: number) => React.ReactNode;
  className?: string;
  itemClassName?: string;
  showGradients?: boolean;
  displayScrollbar?: boolean;
  maxHeightClassName?: string;
  onItemSelect?: (item: T, index: number) => void;
};

type AnimatedItemProps = {
  children: React.ReactNode;
  index: number;
  rootRef: React.RefObject<HTMLDivElement | null>;
  itemClassName?: string;
};

const AnimatedItem = ({ children, index, rootRef, itemClassName }: AnimatedItemProps) => {
  const ref = useRef<HTMLDivElement>(null);
  const [inView, setInView] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      ([entry]) => setInView(entry.isIntersecting),
      {
        root: rootRef.current,
        threshold: 0.5,
      },
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, [rootRef]);

  const delay = Math.min(index * 0.04, 0.3);

  return (
    <div
      ref={ref}
      data-index={index}
      className={itemClassName}
      style={{
        transform: inView ? "scale(1)" : "scale(0.7)",
        opacity: inView ? 1 : 0,
        transition: `transform 200ms ease, opacity 200ms ease`,
        transitionDelay: `${delay}s`,
      }}
    >
      {children}
    </div>
  );
};

export default function AnimatedList<T>({
  items,
  renderItem,
  className = "",
  itemClassName = "",
  showGradients = true,
  displayScrollbar = true,
  maxHeightClassName = "max-h-[420px]",
  onItemSelect,
}: AnimatedListProps<T>) {
  const listRef = useRef<HTMLDivElement>(null);
  const [topOpacity, setTopOpacity] = useState(0);
  const [bottomOpacity, setBottomOpacity] = useState(1);

  const handleScroll = () => {
    const container = listRef.current;
    if (!container) return;
    const { scrollTop, scrollHeight, clientHeight } = container;
    setTopOpacity(Math.min(scrollTop / 50, 1));
    const bottomDistance = scrollHeight - (scrollTop + clientHeight);
    setBottomOpacity(scrollHeight <= clientHeight ? 0 : Math.min(bottomDistance / 50, 1));
  };

  const scrollbarClass = useMemo(
    () =>
      displayScrollbar
        ? "[&::-webkit-scrollbar]:w-[8px] [&::-webkit-scrollbar-track]:bg-slate-900/40 [&::-webkit-scrollbar-thumb]:bg-slate-600/60 [&::-webkit-scrollbar-thumb]:rounded-[4px]"
        : "scrollbar-hide",
    [displayScrollbar],
  );

  return (
    <div className={`relative ${className}`}>
      <div
        ref={listRef}
        className={`${maxHeightClassName} overflow-y-auto pr-1 ${scrollbarClass}`}
        onScroll={handleScroll}
        style={{
          scrollbarWidth: displayScrollbar ? "thin" : "none",
          scrollbarColor: displayScrollbar ? "rgba(100,116,139,0.7) rgba(15,23,42,0.4)" : "transparent",
        }}
      >
        <div className="space-y-2">
          {items.map((item, index) => (
            <AnimatedItem key={index} index={index} rootRef={listRef} itemClassName={itemClassName}>
              <div
                role={onItemSelect ? "button" : undefined}
                onClick={onItemSelect ? () => onItemSelect(item, index) : undefined}
              >
                {renderItem(item, index)}
              </div>
            </AnimatedItem>
          ))}
        </div>
      </div>
      {showGradients && (
        <>
          <div
            className="pointer-events-none absolute left-0 right-0 top-0 h-10 bg-gradient-to-b from-slate-950 to-transparent transition-opacity duration-200"
            style={{ opacity: topOpacity }}
          />
          <div
            className="pointer-events-none absolute bottom-0 left-0 right-0 h-16 bg-gradient-to-t from-slate-950 to-transparent transition-opacity duration-200"
            style={{ opacity: bottomOpacity }}
          />
        </>
      )}
    </div>
  );
}
