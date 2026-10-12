import { create } from 'zustand';
import type { Profile } from '../types/profile';

interface ProfileDuplicateState {
  isModalOpen: boolean;
  sourceProfile: Profile | null;
  openModal: (profile: Profile) => void;
  closeModal: () => void;
}

export const useProfileDuplicateStore = create<ProfileDuplicateState>((set) => ({
  isModalOpen: false,
  sourceProfile: null,
  openModal: (profile: Profile) => set({ isModalOpen: true, sourceProfile: profile }),
  closeModal: () => set({ isModalOpen: false, sourceProfile: null }),
}));
