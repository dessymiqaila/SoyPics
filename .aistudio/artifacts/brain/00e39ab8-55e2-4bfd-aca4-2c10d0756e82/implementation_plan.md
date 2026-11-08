# SoyPics - Secure Encrypted Image Archive & Viewer

SoyPics is a high-security, discreet personal media vault that obfuscates and stores images inside a single encrypted archive file (`gamelog.logz`). It features background batch packing with Android WorkManager, complete multi-playlist organization via Room Database, and an immersive, gesture-driven media viewer.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Key Architecture Decisions:**
> - **Cryptographic Standard:** AES-256 GCM with PBKDF2 (SHA-256, 65,536 iterations, 128-bit random salt) for authenticated stream encryption and decryption. This guarantees both confidentiality and tamper detection when reading/writing entries within `gamelog.logz`.
> - **Discreet Archive Packaging:** The `gamelog.logz` container is formatted as a custom ZIP stream where each image file is stored under an obfuscated randomized ID (e.g. `img_7f8a9b.dat`) containing encrypted bytes and an internal metadata header.
> - **Safe File Lifecycle:** Source files selected via `ACTION_OPEN_DOCUMENT_TREE` or Photo Picker are verified byte-for-byte in the archive before deletion, ensuring zero data loss.
> - **Multi-Playlist Tagging:** Backed by Room M:N cross-reference relation (`ImagePlaylistCrossRef`), allowing any image to belong to zero, one, or multiple playlists simultaneously, plus an isolated `isDeleted` soft-delete state.

---

## 1. Overview & Core Concept

- **What It Does:** Transforms arbitrary folders of private photos into a single, innocent-looking binary file (`gamelog.logz`). Once imported, source files are scrubbed. The user unlocks the vault with their secret password, browsing playlists or viewing images in an edge-to-edge immersive viewer with rapid TikTok-style playlist tagging.
- **Target Audience:** Users requiring strong local privacy, stealth storage for sensitive photos, and rapid fluid categorization without cloud dependencies.
- **Key Value:** True zero-cloud privacy, resilient background processing via WorkManager, military-grade AES-256 authenticated encryption, and a fluid, gesture-first viewing UX.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Vault Unlock & Setup:**
   - On initial launch, the user defines or enters their master encryption password.
   - The Home Dashboard displays storage telemetry: total encrypted images count, size of `gamelog.logz` in MB, and timestamp of last archive modification.
2. **Importing & Background Packing:**
   - Tapping **"Load Folder"** launches Android's Document Tree / Storage Access Framework.
   - WorkManager starts `ArchivePackWorker` in the background with progress notifications. Files are encrypted on-the-fly and appended to `gamelog.logz`. Successful entries delete the source original.
3. **Browsing Playlists & Deleted Bin:**
   - 2-Column responsive grid of custom playlists displaying name, item count, and cover preview.
   - Dedicated full-width **"Deleted"** card at the bottom.
   - **"Empty Trash"** button triggers an alert dialog confirming permanent purge, which executes a non-blocking background compaction (rewriting the archive excluding deleted entries to reclaim storage).
4. **Gesture-Driven Immersive Viewer:**
   - Fullscreen immersive mode (Status Bar & Navigation Bar hidden).
   - **Swipe Up / Left**: Previous image.
   - **Swipe Down / Right** or **Center Tap**: Next image.
   - Smooth depth/fade transition animation.
   - **Exclusion Zone**: The right 64dp strip is reserved for the TikTok-style vertical playlist pills (1, 2, 3...) so fast taps never accidentally advance photos.
   - **Top Floating Bar**: Back navigation, dynamic Delete / Restore button, and Download button (decrypts and exports active photo directly to public `Pictures/SoyPics` or `Download`).

### Visual Identity & Theme

- **Vibe:** Sleek, stealth metallic aesthetic with modern dark glassmorphism.
- **Palette:**
  - Background: Deep obsidian (`#0D1117`) with subtle gunmetal surface cards (`#161B22` / `#21262D`).
  - Accents: Titanium silver (`#C9D1D9`), metallic steel blue (`#58A6FF`), and amber warning tones for trash purge (`#F85149`).
  - App Icon: Two stylized metallic mountain peaks with polished chrome gradient.
- **Typography:** Modern clean sans-serif with monospace accents for cryptographic info and file statistics.

---

## 3. Key Product Decisions & Trade-Offs

| Decision | Chosen Solution | Rationale | Alternatives Considered |
|---|---|---|---|
| **Encryption Engine** | AES-256-GCM + PBKDF2 | Authenticated encryption prevents file corruption & tampering. Standard Java `javax.crypto` with zero external dependencies. | Simple XOR (fast but insecure); SQLCipher (overkill for flat archive). |
| **Storage Architecture** | Single file `gamelog.logz` with Room Index | High portability: user can back up a single file. Room caches metadata for instant search and instant playlist queries. | Multi-file encrypted directory (lacks stealth and easily identified). |
| **Import Execution** | Android WorkManager (`CoroutineWorker`) | Prevents ANR and OS process termination when importing hundreds of high-res photos in the background. | Foreground Coroutine in ViewModel (dies if user switches apps). |
| **Deletion Model** | Soft-delete in Room + Physical Compaction on "Empty Trash" | Instant deletion UI responsiveness; user can restore mistakenly deleted photos. Compaction recovers disk space on demand. | Immediate archive rewrite on every delete (very slow for large files). |

---

## 4. Technical Architecture & Data Strategy

### System Component Diagram

```
┌───────────────────────────────────────────────────────────────┐
│                          UI LAYER                             │
│  ┌───────────────────────────┐   ┌──────────────────────────┐ │
│  │      HomeScreen           │   │    ImageViewerScreen     │ │
│  │ - Storage Telemetry Card  │   │ - Immersive Fullscreen   │ │
│  │ - 2-Col Playlist Grid     │   │ - Gesture Detection Hub  │ │
│  │ - Full-width Deleted Card │   │ - Top Action Bar         │ │
│  │ - Empty Trash Dialog      │   │ - TikTok Playlist Rail   │ │
│  └─────────────┬─────────────┘   └────────────┬─────────────┘ │
└────────────────┼──────────────────────────────┼───────────────┘
                 │                              │
┌────────────────▼──────────────────────────────▼───────────────┐
│                     VIEWMODEL & REPOSITORY                    │
│  - HomeViewModel / ViewerViewModel                            │
│  - VaultRepository (Coordinates Room, Cache & Archive)        │
└────────────────┬──────────────────────────────┬───────────────┘
                 │                              │
         ┌───────▼────────┐             ┌───────▼──────────────┐
         │ Room Database  │             │   WorkManager Hub    │
         │ - ImageEntity  │             │ - ArchivePackWorker  │
         │ - Playlist     │             │ - CompactionWorker   │
         │ - CrossRef     │             └───────┬──────────────┘
         └────────────────┘                     │
                                        ┌───────▼──────────────┐
                                        │ ArchiveSecurity &    │
                                        │ CryptoStreamManager  │
                                        │ (AES-256-GCM / PBKDF)│
                                        └───────┬──────────────┘
                                                │
                                        ┌───────▼──────────────┐
                                        │    gamelog.logz      │
                                        │  (Encrypted Archive) │
                                        └──────────────────────┘
```

### Data Layer (Room Schema)

1. **`ImageEntity`:**
   - `id: String` (UUID primary key)
   - `archiveEntryName: String` (e.g. `blob_4e1a.dat`)
   - `originalFileName: String`
   - `mimeType: String`
   - `fileSizeBytes: Long`
   - `importedTimestamp: Long`
   - `isDeleted: Boolean` (default `false`)
   - `deletedTimestamp: Long?`
2. **`PlaylistEntity`:**
   - `id: Long` (Auto-increment primary key)
   - `name: String`
   - `iconIndex: Int`
   - `createdTimestamp: Long`
3. **`ImagePlaylistCrossRef`:**
   - `imageId: String`
   - `playlistId: Long`
   - Composite Primary Key `(imageId, playlistId)`

### Core Security & Cryptography Specification

1. **Key Derivation:**
   - Password converted to `SecretKey` using `PBKDF2WithHmacSHA256` with salt stored in entry header or archive header.
2. **Byte Stream Encryption / Decryption:**
   - `Cipher.getInstance("AES/GCM/NoPadding")`
   - 12-byte IV prepended to the ciphertext.
   - Encrypted stream wrote into/read from standard ZIP container inside `gamelog.logz`.
3. **Image Decryption for Viewer:**
   - In-memory decoding: Entry stream -> Decryption CipherInputStream -> `BitmapFactory.decodeStream` -> Compose `ImageBitmap` (no plain image written to disk cache).
