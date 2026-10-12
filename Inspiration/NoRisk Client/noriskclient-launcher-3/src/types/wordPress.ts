/**
 * Represents the structure of an Open Graph image object from Yoast SEO data.
 */
export interface OgImage {
  url: string | null;
  type: string | null; // Field name in JSON is 'type', corresponds to 'image_type' in Rust struct
  // Width and height are currently removed in the Rust struct,
  // uncomment if they are re-added and handled.
  // width: number | null;
  // height: number | null;
}

/**
 * Represents the relevant SEO details extracted from the Yoast Head JSON.
 */
export interface YoastHeadJson {
  title: string | null;
  description: string | null;
  og_description: string | null;
  og_url: string | null;
  og_image: OgImage[] | null;
}

/**
 * Represents a blog post fetched from the WordPress API.
 * Note: Many fields were simplified or removed in the Rust struct.
 * This reflects the latest known Rust structure.
 */
export interface BlogPost {
  id: number; // Corresponds to i64
  date: string;
  // The 'yoast_head_json' field from the API response, potentially null.
  yoast_head_json: YoastHeadJson | null;

  // Other fields from the original JSON structure are currently not mapped in the Rust struct:
  // date_gmt?: string;
  // modified?: string;
  // modified_gmt?: string;
  // slug?: string;
  // status?: string;
  // link?: string;
  // title?: { rendered: string }; // Simplified representation if needed
  // content?: { rendered: string; protected: boolean }; // Simplified representation if needed
  // excerpt?: { rendered: string; protected: boolean }; // Simplified representation if needed
  // author?: number;
  // featured_media?: number;
  // categories?: number[];
  // tags?: number[];
} 