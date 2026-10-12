import { create } from 'zustand';
import type { Profile } from '../types/profile';

interface ProfileSettingsState {
  isModalOpen: boolean;
  profile: Profile | null;
  openModal: (profile: Profile) => void;
  closeModal: () => void;
}

export const useProfileSettingsStore = create<ProfileSettingsState>((set) => ({
  isModalOpen: false,
  profile: null,
  openModal: (profile: Profile) => set({ isModalOpen: true, profile }),
  closeModal: () => set({ isModalOpen: false, profile: null }),
}));
