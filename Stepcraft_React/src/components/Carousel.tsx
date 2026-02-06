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
  const range = [-(index + 1) * trackItemOffset, -index * trackItemOffset, -(index - 1) * trackItemOffset];
  const outputRange = [90, 0, -90];
  const rotateY = useTransform(x, range, outputRange, { clamp: false });

  return (
    <motion.div
      key={`${item?.id ?? index}-${index}`}
      className={`relative shrink-0 flex flex-col ${
        round
          ? "items-center justify-center text-center bg-[#060010] border-0"
          : "items-start justify-between bg-[#222] border border-[#222] rounded-[12px]"
      } overflow-hidden cursor-grab active:cursor-grabbing`}
      style={{
        width: itemWidth,
        height: round ? itemWidth : itemHeight,
        rotateY: rotateY,
        ...(round && { borderRadius: "50%" }),
      }}
      transition={transition}
    >
      {!round && item.imageUrl && (
        <div
          className="absolute inset-0 bg-center bg-cover"
          style={{ backgroundImage: `url(${item.imageUrl})` }}
        />
      )}
      {!round && <div className="absolute inset-0 bg-[#0b0f1a]/70" />}
      <div className="relative z-10 w-full">
        <div className={`${round ? "p-0 m-0" : "mb-4 p-5"}`}>
          <span className="flex h-[44px] w-[44px] items-center justify-center rounded-full bg-[#060010]">
            {item.icon}
          </span>
        </div>
        <div className="p-5">
          <div className="mb-1 font-black text-lg text-white">{item.title}</div>
          <p className="text-sm text-white">{item.description}</p>
        </div>
      </div>
      <div className="p-5">
        <div className="mb-1 font-black text-lg text-white">{item.title}</div>
        <p className="text-sm text-white">{item.description}</p>
      </div>
    </motion.div>
  );
}

export default function Carousel({
  items = DEFAULT_ITEMS,
  baseWidth = 300,
  itemHeight = 200,
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
  const itemWidth = resolvedBaseWidth ? resolvedBaseWidth - containerPadding * 2 : 0;
  const trackItemOffset = itemWidth + GAP;
  const itemsForRender = useMemo(() => {
    if (!loop) return items;
    if (items.length === 0) return [];
    return [items[items.length - 1], ...items, items[0]];
  }, [items, loop]);

  const [position, setPosition] = useState<number>(loop ? 1 : 0);
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
    if (!autoplay || itemsForRender.length <= 1) return undefined;
    if (pauseOnHover && isHovered) return undefined;

    const timer = setInterval(() => {
      setPosition((prev) => Math.min(prev + 1, itemsForRender.length - 1));
    }, autoplayDelay);

    return () => clearInterval(timer);
  }, [autoplay, autoplayDelay, isHovered, pauseOnHover, itemsForRender.length]);

  useEffect(() => {
    const startingPosition = loop ? 1 : 0;
    setPosition(startingPosition);
    if (trackItemOffset) {
      x.set(-startingPosition * trackItemOffset);
    }
  }, [items.length, loop, trackItemOffset, x]);

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
    const lastCloneIndex = itemsForRender.length - 1;

    if (position === lastCloneIndex) {
      setIsJumping(true);
      const target = 1;
      setPosition(target);
      x.set(-target * trackItemOffset);
      requestAnimationFrame(() => {
        setIsJumping(false);
        setIsAnimating(false);
      });
      return;
    }

    if (position === 0) {
      setIsJumping(true);
      const target = items.length;
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
      const max = itemsForRender.length - 1;
      return Math.max(0, Math.min(next, max));
    });
  };

  const goTo = (direction: 1 | -1) => {
    setPosition((prev) => {
      const next = prev + direction;
      const max = itemsForRender.length - 1;
      return Math.max(0, Math.min(next, max));
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
        ? (position - 1 + items.length) % items.length
        : Math.min(position, items.length - 1);

  return (
    <div
      ref={containerRef}
      className={`relative overflow-hidden p-4 ${
        round ? "rounded-full border border-white" : "rounded-[24px] border border-[#222]"
      }`}
      style={{
        width: baseWidth === 0 ? "100%" : `${baseWidth}px`,
        ...(round && { height: `${baseWidth}px` }),
      }}
    >
      {showArrows && !round && (
        <>
          <button
            type="button"
            aria-label="Previous"
            onClick={() => goTo(-1)}
            className="absolute left-3 top-1/2 z-20 -translate-y-1/2 grid h-9 w-9 place-items-center rounded-full border border-white/10 bg-[#0b0f1a]/70 text-white hover:bg-[#111827]"
          >
            ‹
          </button>
          <button
            type="button"
            aria-label="Next"
            onClick={() => goTo(1)}
            className="absolute right-3 top-1/2 z-20 -translate-y-1/2 grid h-9 w-9 place-items-center rounded-full border border-white/10 bg-[#0b0f1a]/70 text-white hover:bg-[#111827]"
          >
            ›
          </button>
        </>
      )}
      {itemWidth > 0 && (
      <motion.div
        className="flex"
        drag={isAnimating ? false : "x"}
        {...dragProps}
        style={{
          width: itemWidth || "100%",
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
      <div className={`flex w-full justify-center ${round ? "absolute z-20 bottom-12 left-1/2 -translate-x-1/2" : ""}`}>
        <div className="mt-4 flex w-[150px] justify-between px-8">
          {items.map((_, index) => (
            <motion.div
              key={index}
              className={`h-2 w-2 rounded-full cursor-pointer transition-colors duration-150 ${
                activeIndex === index
                  ? round
                    ? "bg-white"
                    : "bg-[#333333]"
                  : round
                    ? "bg-[#555]"
                    : "bg-[rgba(51,51,51,0.4)]"
              }`}
              animate={{
                scale: activeIndex === index ? 1.2 : 1,
              }}
              onClick={() => setPosition(loop ? index + 1 : index)}
              transition={{ duration: 0.15 }}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

                        {!round && <div className="absolute inset-0 bg-[#0b0f1a]/60" />}
                        <div className="relative z-10 flex h-full w-full flex-col">
                          {!round && item.imageUrl ? (
                            <div className="flex flex-1 items-center justify-center px-6 py-6">
                              <img src={item.imageUrl} alt={item.title} className="max-h-full w-full object-contain" />
                            </div>
                          ) : (
                            <div className={`${round ? "p-0 m-0" : "mb-4 p-5"}`}>
                              <span className="flex h-[44px] w-[44px] items-center justify-center rounded-full bg-[#060010]">
                                {item.icon}
                              </span>
                            </div>
                          )}
                          <div className="px-5 pb-5">
                            <div className="mb-1 font-black text-lg text-white">{item.title}</div>
                            <p className="text-sm text-white/90">{item.description}</p>
                          </div>
                        </div>
