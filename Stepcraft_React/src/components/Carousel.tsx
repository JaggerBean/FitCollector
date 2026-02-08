import React, { useEffect, useMemo, useRef, useState } from "react";
import { motion, useMotionValue, useTransform } from "motion/react";
import type { JSX } from "react";
import type { PanInfo } from "motion/react";
import { FiCircle, FiCode, FiFileText, FiLayers, FiLayout } from "react-icons/fi";

export interface CarouselItem {
  title: string;
  description: string;
  id: number;
  icon: React.ReactNode;
  imageUrl?: string;
}

export interface CarouselProps {
  items?: CarouselItem[];
  baseWidth?: number;
  itemHeight?: number;
  minItemWidth?: number;
  visibleCount?: number;
  autoplay?: boolean;
  autoplayDelay?: number;
  pauseOnHover?: boolean;
  loop?: boolean;
  round?: boolean;
  showArrows?: boolean;
}

const DEFAULT_ITEMS: CarouselItem[] = [
  {
    title: "Text Animations",
    description: "Cool text animations for your projects.",
    id: 1,
    icon: <FiFileText className="h-[16px] w-[16px] text-white" />,
  },
  {
    title: "Animations",
    description: "Smooth animations for your projects.",
    id: 2,
    icon: <FiCircle className="h-[16px] w-[16px] text-white" />,
  },
  {
    title: "Components",
    description: "Reusable components for your projects.",
    id: 3,
    icon: <FiLayers className="h-[16px] w-[16px] text-white" />,
  },
  {
    title: "Backgrounds",
    description: "Beautiful backgrounds and patterns for your projects.",
    id: 4,
    icon: <FiLayout className="h-[16px] w-[16px] text-white" />,
  },
  {
    title: "Common UI",
    description: "Common UI components are coming soon!",
    id: 5,
    icon: <FiCode className="h-[16px] w-[16px] text-white" />,
  },
];

const DRAG_BUFFER = 0;
const VELOCITY_THRESHOLD = 500;
const GAP = 16;
const SPRING_OPTIONS = { type: "spring" as const, stiffness: 300, damping: 30 };

interface CarouselItemProps {
  item: CarouselItem;
  index: number;
  itemWidth: number;
  itemHeight: number;
  round: boolean;
  trackItemOffset: number;
  x: any;
  transition: any;
}

function CarouselItem({
  item,
  index,
  itemWidth,
  itemHeight,
  round,
  trackItemOffset,
  x,
  transition,
}: CarouselItemProps) {
  const range = round
    ? [-(index + 1) * trackItemOffset, -index * trackItemOffset, -(index - 1) * trackItemOffset]
    : [
        -(index + 2) * trackItemOffset,
        -(index + 1) * trackItemOffset,
        -index * trackItemOffset,
        -(index - 1) * trackItemOffset,
        -(index - 2) * trackItemOffset,
      ];
  const outputRange = round ? [90, 0, -90] : [35, 0, 0, 0, -35];
  const rotateY = useTransform(x, range, outputRange, { clamp: false });
  const scale = useTransform(
    x,
    range,
    round ? [0.88, 1, 0.88] : [1, 1, 1, 1, 1],
    { clamp: false }
  );
  const opacity = useTransform(
    x,
    range,
    round ? [0.55, 1, 0.55] : [0, 0.7, 1, 0.7, 0],
    { clamp: false }
  );
  const xShift = useTransform(
    x,
    range,
    round ? [0, 0, 0] : [0, 0, 0, 0, 0],
    { clamp: false }
  );

  return (
    <motion.div
      key={`${item?.id ?? index}-${index}`}
      className={`relative shrink-0 flex flex-col ${
        round
          ? "items-center justify-center text-center bg-[#060010] border-0"
          : "items-start justify-between bg-[#0b0f1a] border border-white/5 rounded-[20px] shadow-[0_20px_60px_rgba(0,0,0,0.35)]"
      } overflow-hidden cursor-grab active:cursor-grabbing`}
      style={{
        width: itemWidth,
        height: round ? itemWidth : itemHeight,
        rotateY: rotateY,
        scale: scale,
        opacity: opacity,
        x: xShift,
        ...(round && { borderRadius: "50%" }),
      }}
      transition={transition}
    >
      {round ? (
        <div className="flex h-full w-full flex-col items-center justify-center gap-3 px-6">
          <span className="flex h-[56px] w-[56px] items-center justify-center rounded-full bg-[#0f172a]">
            {item.icon}
          </span>
          <div className="font-black text-lg text-white">{item.title}</div>
          <p className="text-sm text-white/80">{item.description}</p>
        </div>
      ) : (
        <>
          {item.imageUrl ? (
            <>
              <img
                src={item.imageUrl}
                alt=""
                aria-hidden="true"
                className="absolute inset-0 h-full w-full object-cover blur-md scale-110 opacity-35"
                loading="eager"
                decoding="async"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-[#0b0f1a]/85 via-[#0b0f1a]/45 to-transparent" />
            </>
          ) : null}
          <div className="relative z-10 flex h-full w-full flex-col p-6">
            {item.imageUrl ? (
              <div className="relative flex-1 rounded-2xl bg-[#0b0f1a]/60 p-4 shadow-[0_10px_30px_rgba(0,0,0,0.35)]">
                <img
                  src={item.imageUrl}
                  alt={item.title}
                  className="h-full w-full object-contain"
                  loading="eager"
                  decoding="async"
                />
                <div className="absolute bottom-4 left-4 right-4 rounded-xl border border-white/10 bg-[#0b0f1a]/80 px-4 py-3 backdrop-blur">
                  <div className="text-base font-semibold text-white">{item.title}</div>
                  <p className="mt-1 text-xs text-white/70">{item.description}</p>
                </div>
              </div>
            ) : (
              <div className="mb-4">
                <span className="flex h-[48px] w-[48px] items-center justify-center rounded-full bg-[#111827]">
                  {item.icon}
                </span>
              </div>
            )}
            {!item.imageUrl && (
              <div className="pt-4">
                <div className="text-xl font-black text-white">{item.title}</div>
                <p className="mt-2 text-sm text-white/85">{item.description}</p>
              </div>
            )}
          </div>
        </>
      )}
    </motion.div>
  );
}

export default function Carousel({
  items = DEFAULT_ITEMS,
  baseWidth = 300,
  itemHeight = 340,
  minItemWidth = 360,
  visibleCount,
  autoplay = false,
  autoplayDelay = 3000,
  pauseOnHover = false,
  loop = false,
  round = false,
  showArrows = true,
}: CarouselProps): JSX.Element {
  const containerPadding = 16;
  const [measuredWidth, setMeasuredWidth] = useState(0);
  const resolvedBaseWidth = baseWidth === 0 ? measuredWidth : baseWidth;
  const availableWidth = resolvedBaseWidth ? resolvedBaseWidth - containerPadding * 2 : 0;
  const autoVisibleCount = availableWidth
    ? Math.max(1, Math.floor((availableWidth + GAP) / (minItemWidth + GAP)))
    : 1;
  const effectiveVisibleCount = round
    ? 1
    : Math.max(1, Math.min(visibleCount ?? Number.POSITIVE_INFINITY, autoVisibleCount));
  const loopClones = loop ? Math.min(items.length, effectiveVisibleCount) : 0;
  const itemWidth = availableWidth
    ? (availableWidth - GAP * (effectiveVisibleCount - 1)) / effectiveVisibleCount
    : 0;
  const trackItemOffset = itemWidth + GAP;
  const itemsForRender = useMemo(() => {
    if (!loop) return items;
    if (items.length === 0) return [];
    const head = items.slice(0, loopClones);
    const tail = items.slice(Math.max(0, items.length - loopClones));
    return [...tail, ...items, ...head];
  }, [items, loop, loopClones]);

  const [position, setPosition] = useState<number>(loop ? loopClones : 0);
  const x = useMotionValue(0);
  const [isHovered, setIsHovered] = useState<boolean>(false);
  const [isJumping, setIsJumping] = useState<boolean>(false);
  const [isAnimating, setIsAnimating] = useState<boolean>(false);

  const containerRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (baseWidth !== 0 || !containerRef.current) return;
    const el = containerRef.current;
    const update = () => setMeasuredWidth(el.clientWidth);
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    return () => ro.disconnect();
  }, [baseWidth]);
  useEffect(() => {
    if (pauseOnHover && containerRef.current) {
      const container = containerRef.current;
      const handleMouseEnter = () => setIsHovered(true);
      const handleMouseLeave = () => setIsHovered(false);
      container.addEventListener("mouseenter", handleMouseEnter);
      container.addEventListener("mouseleave", handleMouseLeave);
      return () => {
        container.removeEventListener("mouseenter", handleMouseEnter);
        container.removeEventListener("mouseleave", handleMouseLeave);
      };
    }
  }, [pauseOnHover]);

  useEffect(() => {
    if (!items.length) return;
    items.forEach((item) => {
      if (!item.imageUrl) return;
      const img = new Image();
      img.src = item.imageUrl;
    });
  }, [items]);

  useEffect(() => {
    if (!autoplay || itemsForRender.length <= 1) return undefined;
    if (pauseOnHover && isHovered) return undefined;

    const timer = setInterval(() => {
      if (isAnimating || isJumping) return;
      setPosition((prev) => {
        const max = loop ? items.length + loopClones : itemsForRender.length - 1;
        return Math.min(prev + 1, max);
      });
    }, autoplayDelay);

    return () => clearInterval(timer);
  }, [autoplay, autoplayDelay, isHovered, pauseOnHover, itemsForRender.length, isAnimating, isJumping]);

  useEffect(() => {
    const startingPosition = loop ? loopClones : 0;
    setPosition(startingPosition);
    if (trackItemOffset) {
      x.set(-startingPosition * trackItemOffset);
    }
  }, [items.length, loop, loopClones, trackItemOffset, x]);

  useEffect(() => {
    if (!loop && position > itemsForRender.length - 1) {
      setPosition(Math.max(0, itemsForRender.length - 1));
    }
  }, [itemsForRender.length, loop, position]);

  const effectiveTransition = isJumping ? { duration: 0 } : SPRING_OPTIONS;

  const handleAnimationStart = () => {
    setIsAnimating(true);
  };

  const handleAnimationComplete = () => {
    if (!loop || itemsForRender.length <= 1) {
      setIsAnimating(false);
      return;
    }
    if (loopClones === 0) {
      setIsAnimating(false);
      return;
    }

    const firstCloneIndex = items.length + loopClones;
    const lastCloneIndex = loopClones - 1;

    if (position >= firstCloneIndex) {
      setIsJumping(true);
      const target = loopClones;
      setPosition(target);
      x.set(-target * trackItemOffset);
      requestAnimationFrame(() => {
        setIsJumping(false);
        setIsAnimating(false);
      });
      return;
    }

    if (position <= lastCloneIndex) {
      setIsJumping(true);
      const target = items.length + loopClones - 1;
      setPosition(target);
      x.set(-target * trackItemOffset);
      requestAnimationFrame(() => {
        setIsJumping(false);
        setIsAnimating(false);
      });
      return;
    }

    setIsAnimating(false);
  };

  const handleDragEnd = (_: MouseEvent | TouchEvent | PointerEvent, info: PanInfo): void => {
    const { offset, velocity } = info;
    const direction =
      offset.x < -DRAG_BUFFER || velocity.x < -VELOCITY_THRESHOLD
        ? 1
        : offset.x > DRAG_BUFFER || velocity.x > VELOCITY_THRESHOLD
          ? -1
          : 0;

    if (direction === 0) return;

    setPosition((prev) => {
      const next = prev + direction;
      const max = loop ? items.length + loopClones : itemsForRender.length - 1;
      const min = loop ? loopClones - 1 : 0;
      return Math.max(min, Math.min(next, max));
    });
  };

  const goTo = (direction: 1 | -1) => {
    setPosition((prev) => {
      const next = prev + direction;
      const max = loop ? items.length + loopClones : itemsForRender.length - 1;
      const min = loop ? loopClones - 1 : 0;
      return Math.max(min, Math.min(next, max));
    });
  };

  const dragProps = loop
    ? {}
    : {
        dragConstraints: {
          left: -trackItemOffset * Math.max(itemsForRender.length - 1, 0),
          right: 0,
        },
      };

  const activeIndex =
    items.length === 0
      ? 0
      : loop
        ? (position - loopClones + items.length) % items.length
        : Math.min(position, items.length - 1);

  return (
    <div className="relative">
      {showArrows && !round && (
        <>
          <button
            type="button"
            aria-label="Previous"
            onClick={() => goTo(-1)}
            className="absolute left-0 top-1/2 z-20 -translate-x-1/2 -translate-y-1/2 grid h-11 w-11 place-items-center rounded-full border border-emerald-300/70 bg-[#0b0f1a] text-emerald-200 shadow-[0_10px_25px_rgba(0,0,0,0.35)] transition hover:bg-[#0f172a] hover:text-emerald-100"
          >
            ‹
          </button>
          <button
            type="button"
            aria-label="Next"
            onClick={() => goTo(1)}
            className="absolute right-0 top-1/2 z-20 translate-x-1/2 -translate-y-1/2 grid h-11 w-11 place-items-center rounded-full border border-emerald-300/70 bg-[#0b0f1a] text-emerald-200 shadow-[0_10px_25px_rgba(0,0,0,0.35)] transition hover:bg-[#0f172a] hover:text-emerald-100"
          >
            ›
          </button>
        </>
      )}
      <div
        ref={containerRef}
        className={`relative overflow-visible p-4 ${
          round ? "rounded-full border border-white" : "rounded-[24px] border border-[#222]"
        }`}
        style={{
          width: baseWidth === 0 ? "100%" : `${baseWidth}px`,
          ...(round && { height: `${baseWidth}px` }),
        }}
      >
        <div className="overflow-hidden rounded-[20px]" style={{ clipPath: "inset(0)" }}>
          {itemWidth > 0 && (
            <motion.div
              className="flex"
              drag={isAnimating ? false : "x"}
              {...dragProps}
              style={{
                width: trackItemOffset * itemsForRender.length,
                gap: `${GAP}px`,
                perspective: 1000,
                perspectiveOrigin: `${position * trackItemOffset + itemWidth / 2}px 50%`,
                x,
              }}
              onDragEnd={handleDragEnd}
              animate={{ x: -(position * trackItemOffset) }}
              transition={effectiveTransition}
              onAnimationStart={handleAnimationStart}
              onAnimationComplete={handleAnimationComplete}
            >
              {itemsForRender.map((item, index) => (
                <CarouselItem
                  key={`${item?.id ?? index}-${index}`}
                  item={item}
                  index={index}
                  itemWidth={itemWidth}
                  itemHeight={itemHeight}
                  round={round}
                  trackItemOffset={trackItemOffset}
                  x={x}
                  transition={effectiveTransition}
                />
              ))}
            </motion.div>
          )}
        </div>
        <div className={`flex w-full justify-center ${round ? "absolute z-20 bottom-12 left-1/2 -translate-x-1/2" : ""}`}>
          <div className="mt-4 flex w-[150px] justify-between px-8">
            {items.map((_, index) => (
              <motion.div
                key={index}
                className={`h-2 w-2 rounded-full cursor-pointer transition-colors duration-150 ${
                  activeIndex === index
                    ? round
                      ? "bg-white"
                      : "bg-emerald-300/100"
                    : round
                      ? "bg-[#555]"
                      : "bg-emerald-300/35"
                }`}
                animate={{
                  scale: activeIndex === index ? 1.2 : 1,
                }}
                onClick={() => setPosition(loop ? index + loopClones : index)}
                transition={{ duration: 0.15 }}
              />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
