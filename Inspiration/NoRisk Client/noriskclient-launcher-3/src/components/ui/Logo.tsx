import { cn } from "../../lib/utils";

interface LogoProps {
  size?: "sm" | "md" | "lg";
  className?: string;
  onClick?: () => void;
}

export function Logo({ size = "md", className, onClick }: LogoProps) {
  const sizeClasses = {
    sm: "w-10 h-10",
    md: "w-16 h-16",
    lg: "w-24 h-24",
  };

  return (
    <div 
      className={cn(
        "relative", 
        sizeClasses[size], 
        className,
        onClick && "cursor-pointer hover:scale-105 transition-transform duration-200"
      )}
      onClick={onClick}
    >
      <img
        src="/logo.png"
        alt="NoRisk Logo"
        className="w-full h-full object-contain"
      />
    </div>
  );
}
