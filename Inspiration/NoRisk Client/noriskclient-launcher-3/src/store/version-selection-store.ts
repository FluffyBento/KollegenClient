import { create } from "zustand";
import { persist } from "zustand/middleware";

interface VersionSelectionState {
  selectedVersion: string;
  setSelectedVersion: (version: string) => void;
  isModalOpen: boolean;
  openModal: () => void;
  closeModal: () => void;
}

export const useVersionSelectionStore = create<VersionSelectionState>()(
  persist(
    (set) => ({
      selectedVersion: "",
      setSelectedVersion: (version) => set({ selectedVersion: version }),
      isModalOpen: false,
      openModal: () => set({ isModalOpen: true }),
      closeModal: () => set({ isModalOpen: false }),
    }),
    {
      name: "version-selection-storage",
    },
  ),
);
