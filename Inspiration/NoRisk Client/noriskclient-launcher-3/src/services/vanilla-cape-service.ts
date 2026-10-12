import { invoke } from '@tauri-apps/api/core';
import type { VanillaCape, VanillaCapeInfo } from '../types/vanillaCapes';

export class VanillaCapeService {
  static async getOwnedVanillaCapes(): Promise<VanillaCape[]> {
    return invoke<VanillaCape[]>('get_owned_vanilla_capes');
  }

  static async getCurrentlyEquippedVanillaCape(): Promise<VanillaCape | null> {
    return invoke<VanillaCape | null>('get_currently_equipped_vanilla_cape');
  }

  static async equipVanillaCape(capeId: string | null): Promise<void> {
    return invoke('equip_vanilla_cape', { capeId });
  }

  static async getVanillaCapeInfo(): Promise<VanillaCapeInfo[]> {
    return invoke<VanillaCapeInfo[]>('get_vanilla_cape_info');
  }

  static async refreshVanillaCapeData(): Promise<void> {
    return invoke('refresh_vanilla_cape_data');
  }
}

export const getOwnedVanillaCapes = VanillaCapeService.getOwnedVanillaCapes;
export const getCurrentlyEquippedVanillaCape = VanillaCapeService.getCurrentlyEquippedVanillaCape;
export const equipVanillaCape = VanillaCapeService.equipVanillaCape;
export const getVanillaCapeInfo = VanillaCapeService.getVanillaCapeInfo;
export const refreshVanillaCapeData = VanillaCapeService.refreshVanillaCapeData;