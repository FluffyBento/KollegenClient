export interface VanillaCape {
  id: string;
  name: string;
  description?: string;
  url: string;
  equipped: boolean;
  obtainedAt?: number;
  category: string;
  active: boolean;
}

export interface VanillaCapeInfo {
  id: string;
  name: string;
  description: string;
  previewUrl: string;
  category: string;
  obtainable: boolean;
  obtainMethod?: string;
}

export interface EquipVanillaCapePayload {
  cape_id: string | null;
}

export interface MojangCapeResponse {
  capes: {
    id: string;
    state: "ACTIVE" | "INACTIVE";
    url: string;
    alias: string;
  }[];
}

export interface ProfileWithCapes {
  id: string;
  name: string;
  capes: VanillaCape[];
  activeCape?: VanillaCape;
}