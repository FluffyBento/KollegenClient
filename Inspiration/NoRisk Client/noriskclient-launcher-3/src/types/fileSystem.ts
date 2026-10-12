// Types for file system operations

/**
 * Represents a node in a file system tree
 * Maps to the Rust FileNode struct in path_utils.rs
 */
export interface FileNode {
    /** Name of the file or directory (just the filename, not the full path) */
    name: string;
    
    /** Full path to the file or directory */
    path: string;
    
    /** Whether this node is a directory */
    is_dir: boolean;
    
    /** Child nodes (empty for files) */
    children?: FileNode[];
    
    /** File size in bytes (0 for directories) */
    size: number;
    
    /** Last modified timestamp as seconds since UNIX epoch */
    last_modified: number | null;
} 

// --- NEW TYPES START HERE ---

/**
 * Payload for requesting an image preview.
 * Maps to the Rust ImagePreviewPayload struct.
 */
export interface ImagePreviewPayload {
  path: string;
  width?: number;  // Target width for the preview
  height?: number; // Target height for the preview
  quality?: number; // Target quality (e.g., 1-100 for JPEG)
}

/**
 * Response containing the image preview data.
 * Maps to the Rust ImagePreviewResponse struct.
 */
export interface ImagePreviewResponse {
  base64_image: string;
  original_width: number;
  original_height: number;
  preview_width: number;
  preview_height: number;
}

// --- NEW TYPES END HERE --- 