import type { LocalContentItem } from '../hooks/useLocalContentManager';

/**
 * Determines the update identifier for a content item based on platform detection logic.
 * This function must be kept in sync with the update checking logic in useLocalContentManager.ts
 *
 * Priority order:
 * 1. CurseForge forced if fingerprint exists (overrides all other metadata)
 * 2. CurseForge if platform === 'CurseForge' (uses sha1_hash as fallback)
 * 3. Modrinth for all other cases (uses sha1_hash)
 *
 * @param item The content item to get the identifier for
 * @returns The identifier string, or null if no valid identifier can be determined
 */
export function getUpdateIdentifier(item: LocalContentItem): string | null {
  // Priority 1: CurseForge forced if fingerprint exists
  // This overrides all other metadata
  if (item.curseforge_info?.fingerprint != null) {
    return String(item.curseforge_info.fingerprint);
  }

  // Priority 2: Determine by original source (platform/modsource)
  if (item.platform === 'CurseForge') {
    // CurseForge item without fingerprint - use sha1_hash as identifier
    return item.sha1_hash || null;
  }

  // Default to Modrinth for all other cases
  return item.sha1_hash || null;
}

/**
 * Determines the platform for a content item based on the same logic as getUpdateIdentifier.
 * This is useful for UI elements that need to display platform information.
 *
 * @param item The content item to get the platform for
 * @returns The platform string ('CurseForge' or 'Modrinth'), or null if undetermined
 */
export function getContentPlatform(item: LocalContentItem): 'CurseForge' | 'Modrinth' | null {
  // Priority 1: CurseForge forced if fingerprint exists
  if (item.curseforge_info?.fingerprint != null) {
    return 'CurseForge';
  }

  // Priority 2: Determine by original source (platform/modsource)
  if (item.platform === 'CurseForge') {
    return 'CurseForge';
  }

  // Default to Modrinth for all other cases
  return 'Modrinth';
}
