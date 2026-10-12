import { create } from 'zustand';

interface SocialsModalState {
  isModalOpen: boolean;
  openModal: () => void;
  closeModal: () => void;
}

export const useSocialsModalStore = create<SocialsModalState>((set) => ({
  isModalOpen: false,
  openModal: () => set({ isModalOpen: true }),
  closeModal: () => set({ isModalOpen: false }),
})); 