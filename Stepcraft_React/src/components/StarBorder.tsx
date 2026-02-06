import React from "react";

type StarBorderProps<T extends React.ElementType> = React.ComponentPropsWithoutRef<T> & {
  as?: T;
  className?: string;
  contentClassName?: string;
  children?: React.ReactNode;
  color?: string;
  speed?: React.CSSProperties["animationDuration"];
  thickness?: number;
};

const StarBorder = <T extends React.ElementType = "button">({
  as,
  className = "",
  contentClassName,
  color = "#10b981",
  speed = "6s",
  thickness = 1,
  children,
  ...rest
}: StarBorderProps<T>) => {
  const Component = as || "button";
  const defaultContentClass =
    "bg-gradient-to-b from-black to-gray-900 border border-gray-800 text-white text-center text-[16px] py-[16px] px-[26px] rounded-[20px]";
  const resolvedContentClass = contentClassName
    ? `${defaultContentClass} ${contentClassName}`
    : defaultContentClass;

  return (
    <Component
      className={`relative inline-block overflow-hidden rounded-[inherit] ${className}`}
      {...(rest as any)}
      style={{
        padding: `${thickness}px 0`,
        ...(rest as any).style,
      }}
    >
      <div
        className="pointer-events-none absolute inset-0 rounded-[inherit] border border-transparent animate-star-rotate z-0"
        style={{
          background: `linear-gradient(#000, #000) padding-box, conic-gradient(from 0deg, transparent 0deg, ${color} 60deg, transparent 120deg, transparent 360deg) border-box`,
          backgroundClip: "padding-box, border-box",
          animationDuration: speed,
        }}
      ></div>
      <div className={`relative z-10 rounded-[inherit] ${resolvedContentClass}`}>
        {children}
      </div>
    </Component>
  );
};

export default StarBorder;
