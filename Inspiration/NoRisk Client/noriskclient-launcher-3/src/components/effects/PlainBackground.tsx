import React, { useMemo } from "react";

interface PlainBackgroundProps {
  accentColorValue: string;
}

const PlainBackground: React.FC<PlainBackgroundProps> = ({
  accentColorValue,
}) => {
  const getDarkerShade = (hexColor: string): string => {
    const color = hexColor.startsWith("#") ? hexColor.slice(1) : hexColor;
    let r = Number.parseInt(color.substring(0, 2), 16);
    let g = Number.parseInt(color.substring(2, 4), 16);
    let b = Number.parseInt(color.substring(4, 6), 16);
    r = Math.max(0, Math.floor(r * 0.1));
    g = Math.max(0, Math.floor(g * 0.1));
    b = Math.max(0, Math.floor(b * 0.1));
    return `#${r.toString(16).padStart(2, "0")}${g.toString(16).padStart(2, "0")}${b.toString(16).padStart(2, "0")}`;
  };

  const backgroundColor = useMemo(
    () => getDarkerShade(accentColorValue),
    [accentColorValue],
  );

  return (
    <div
      className="absolute inset-0 w-full h-full transition-colors duration-500"
      style={{ backgroundColor }}
    />
  );
};

export default PlainBackground;
