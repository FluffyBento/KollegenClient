// CurseForge API Types for get_mods_by_ids functionality

export interface CurseForgeLinks {
    websiteUrl: string;
    wikiUrl?: string;
    issuesUrl?: string;
    sourceUrl?: string;
}

export interface CurseForgeCategory {
    id: number;
    gameId: number;
    name: string;
    slug: string;
    url: string;
    iconUrl: string;
    dateModified: string;
    isClass?: boolean;
    classId?: number;
    parentCategoryId?: number;
    displayIndex?: number;
}

export interface CurseForgeAuthor {
    id: number;
    name: string;
    url: string;
}

export interface CurseForgeAttachment {
    id: number;
    modId: number;
    title: string;
    description: string;
    thumbnailUrl: string;
    url: string;
}

export interface CurseForgeFileHash {
    value: string;
    algo: number;
}

export interface CurseForgeSortableGameVersion {
    gameVersionName: string;
    gameVersionPadded: string;
    gameVersion: string;
    gameVersionReleaseDate: string;
    gameVersionTypeId?: number;
}

export interface CurseForgeDependency {
    modId: number;
    relationType: number;
}

export interface CurseForgeModule {
    name: string;
    fingerprint: number;
}

export interface CurseForgeFile {
    id: number;
    gameId: number;
    modId: number;
    isAvailable: boolean;
    displayName: string;
    fileName: string;
    releaseType: number;
    fileStatus: number;
    hashes: CurseForgeFileHash[];
    fileDate: string;
    fileLength: number;
    downloadCount: number;
    fileSizeOnDisk?: number;
    downloadUrl: string;
    gameVersions: string[];
    sortableGameVersions: CurseForgeSortableGameVersion[];
    dependencies: CurseForgeDependency[];
    exposeAsAlternative?: boolean;
    parentProjectFileId?: number;
    alternateFileId?: number;
    isServerPack?: boolean;
    serverPackFileId?: number;
    isEarlyAccessContent?: boolean;
    earlyAccessEndDate?: string;
    fileFingerprint: number;
    modules: CurseForgeModule[];
}

export interface CurseForgeFileIndex {
    gameVersion: string;
    fileId: number;
    filename: string;
    releaseType: number;
    gameVersionTypeId?: number;
    modLoader?: number;
}

export interface CurseForgeMod {
    id: number;
    gameId: number;
    name: string;
    slug: string;
    links: CurseForgeLinks;
    summary: string;
    status: number;
    downloadCount: number;
    isFeatured: boolean;
    primaryCategoryId: number;
    categories: CurseForgeCategory[];
    classId?: number;
    authors: CurseForgeAuthor[];
    logo?: CurseForgeAttachment;
    screenshots: CurseForgeAttachment[];
    mainFileId: number;
    latestFiles: CurseForgeFile[];
    latestFilesIndexes: CurseForgeFileIndex[];
    latestEarlyAccessFilesIndexes: CurseForgeFileIndex[];
    dateCreated: string;
    dateModified: string;
    dateReleased: string;
    allowModDistribution?: boolean;
    gamePopularityRank: number;
    isAvailable: boolean;
    thumbsUpCount: number;
    rating?: number;
}

export interface CurseForgeModsResponse {
    data: CurseForgeMod[];
}

export interface GetModsByIdsRequestBody {
    modIds: number[];
    filterPcOnly?: boolean;
}
