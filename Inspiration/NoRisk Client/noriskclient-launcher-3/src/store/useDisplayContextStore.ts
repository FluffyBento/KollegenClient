import { create } from "zustand";

type DisplayContext = "detail" | "standalone";

interface DisplayContextState {
  context: DisplayContext;
  setContext: (context: DisplayContext) => void;
}

export const useDisplayContextStore = create<DisplayContextState>((set) => ({
  context: "standalone",
  setContext: (context) => set({ context }),
}));