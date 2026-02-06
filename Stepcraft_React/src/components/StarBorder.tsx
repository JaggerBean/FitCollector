import React from "react";

type StarBorderProps<T extends React.ElementType> = React.ComponentPropsWithoutRef<T> & {
  as?: T;
  className?: string;
  children?: React.ReactNode;
  color?: string;
  speed?: React.CSSProperties["animationDuration"];
  thickness?: number;
};

const StarBorder = <T extends React.ElementType = "button">({
  as,
  className = "",
  color = "white",
  speed = "1s",
  thickness = 4,
  children,
  ...rest
}: StarBorderProps<T>) => {
  const Component = as || "button";

  return (
    <Component
      className={`group relative inline-block overflow-hidden rounded-[20px] ${className}`}
      {...(rest as any)}
      style={{
        padding: `${thickness}px 0`,
        ...(rest as any).style,
      }}
    >
      <div
        className="absolute w-[220%] h-[60%] opacity-0 bottom-[-8px] right-[-120%] rounded-full z-0 transition-opacity duration-0 group-hover:opacity-70 group-hover:animate-star-movement-bottom"
        style={{
          background: `radial-gradient(circle, ${color}, transparent 10%)`,
          animationDuration: speed,
          animationDelay: "0s",
          animationTimingFunction: "linear",
        }}
      ></div>
      <div
        className="absolute w-[220%] h-[60%] opacity-0 top-[-8px] left-[-120%] rounded-full z-0 transition-opacity duration-0 group-hover:opacity-70 group-hover:animate-star-movement-top"
        style={{
          background: `radial-gradient(circle, ${color}, transparent 10%)`,
          animationDuration: speed,
          animationDelay: "0s",
          animationTimingFunction: "linear",
        }}
      ></div>
      <div className="relative z-[1] bg-gradient-to-b from-emerald-950/90 via-slate-950 to-slate-950 border border-emerald-900/70 text-white text-center text-[16px] py-[16px] px-[26px] rounded-[20px]">
        {children}
      </div>
    </Component>
  );
};

export default StarBorder;
