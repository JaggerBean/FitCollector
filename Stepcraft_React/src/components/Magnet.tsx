import React, { useEffect, useRef, useState } from "react";

type MagnetProps = React.HTMLAttributes<HTMLDivElement> & {
  children: React.ReactNode;
  padding?: number;
  disabled?: boolean;
  magnetStrength?: number;
  collisionPadding?: number;
  activeTransition?: string;
  inactiveTransition?: string;
  wrapperClassName?: string;
  innerClassName?: string;
};

const Magnet: React.FC<MagnetProps> = ({
  children,
  padding = 25,
  disabled = false,
  magnetStrength = 5,
  collisionPadding = 6,
  activeTransition = "transform 0.3s ease-out",
  inactiveTransition = "transform 0.5s ease-in-out",
  wrapperClassName = "",
  innerClassName = "",
  ...props
}) => {
  const [isActive, setIsActive] = useState<boolean>(false);
  const [position, setPosition] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const magnetRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (disabled) {
      setPosition({ x: 0, y: 0 });
      return;
    }

    const handleMouseMove = (e: MouseEvent) => {
      if (!magnetRef.current) return;

      const { left, top, width, height } = magnetRef.current.getBoundingClientRect();
      const centerX = left + width / 2;
      const centerY = top + height / 2;

      const distX = Math.abs(centerX - e.clientX);
      const distY = Math.abs(centerY - e.clientY);

      if (distX < width / 2 + padding && distY < height / 2 + padding) {
        setIsActive(true);
        let offsetX = (e.clientX - centerX) / magnetStrength;
        let offsetY = (e.clientY - centerY) / magnetStrength;

        const parent = magnetRef.current.parentElement;
        if (parent) {
          const siblings = Array.from(parent.children).filter(
            (el) => el !== magnetRef.current && el.getAttribute("data-magnet") === "true",
          ) as HTMLDivElement[];

          const rect = magnetRef.current.getBoundingClientRect();
          let minX = -Infinity;
          let maxX = Infinity;

          siblings.forEach((sib) => {
            const s = sib.getBoundingClientRect();
            const verticalOverlap = rect.top + offsetY < s.bottom && rect.bottom + offsetY > s.top;
            if (!verticalOverlap) return;

            if (s.left >= rect.right) {
              const gap = s.left - rect.right;
              const maxRight = (gap - collisionPadding) / 2;
              if (maxRight <= 0) {
                maxX = Math.min(maxX, 0);
              } else {
                maxX = Math.min(maxX, maxRight);
              }
            } else if (s.right <= rect.left) {
              const gap = rect.left - s.right;
              const maxLeft = -(gap - collisionPadding) / 2;
              if (maxLeft >= 0) {
                minX = Math.max(minX, 0);
              } else {
                minX = Math.max(minX, maxLeft);
              }
            }
          });

          if (minX <= maxX) {
            offsetX = Math.min(maxX, Math.max(minX, offsetX));
          }
        }

        setPosition({ x: offsetX, y: offsetY });
      } else {
        setIsActive(false);
        setPosition({ x: 0, y: 0 });
      }
    };

    window.addEventListener("mousemove", handleMouseMove);
    return () => {
      window.removeEventListener("mousemove", handleMouseMove);
    };
  }, [padding, disabled, magnetStrength]);

  const transitionStyle = isActive ? activeTransition : inactiveTransition;

  return (
    <div
      ref={magnetRef}
      className={wrapperClassName}
      data-magnet="true"
      style={{ position: "relative", display: "inline-block" }}
      {...props}
    >
      <div
        className={innerClassName}
        style={{
          transform: `translate3d(${position.x}px, ${position.y}px, 0)`,
          transition: transitionStyle,
          willChange: "transform",
        }}
      >
        {children}
      </div>
    </div>
  );
};

export default Magnet;
