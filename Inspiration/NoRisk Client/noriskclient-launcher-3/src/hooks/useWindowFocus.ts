import { useEffect, useState } from "react";

/**
 * Hook to track whether the window is currently focused.
 * Used to pause expensive animations/effects when the window is not in focus
 * to save CPU and GPU resources.
 */
export function useWindowFocus(): boolean {
  const [isWindowFocused, setIsWindowFocused] = useState<boolean>(true);

  useEffect(() => {
    // Set initial state
    setIsWindowFocused(document.hasFocus());

    const handleFocus = () => setIsWindowFocused(true);
    const handleBlur = () => setIsWindowFocused(false);
    const handleVisibilityChange = () => {
      setIsWindowFocused(!document.hidden && document.hasFocus());
    };

    // Listen for focus/blur events on the window
    window.addEventListener("focus", handleFocus);
    window.addEventListener("blur", handleBlur);
    // Also listen for visibility changes (tab switching)
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      window.removeEventListener("focus", handleFocus);
      window.removeEventListener("blur", handleBlur);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, []);

  return isWindowFocused;
} 