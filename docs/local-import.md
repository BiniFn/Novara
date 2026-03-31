# Local Import Guide

Kototoro supports importing local content directly into the application. Starting from the recent updates, you can explicitly pick whether to import your files as **Manga**, **Novel**, or **Video**. 

Depending on the file type, you can use either "Import File" for single instances or "Import Folder" for structured collections.

## Supported File Formats

When forcing a specific content type during import, the file extensions must match the supported formats for that media type. Otherwise, the import will be blocked to prevent playback or reading errors.

- **Manga / Comics**: `.zip`, `.cbz`, folders with image files (`.jpg`, `.png`, `.webp`, etc.).
- **Novel**: `.epub`, `.txt`.
- **Video / Anime**: `.mp4`, `.mkv`, `.ts`, `.webm`, `.avi`, `.m3u8`.

> [!NOTE]  
> If you do not explicitly select a category and leave it as **Auto-detect**, Kototoro will attempt to guess the content type based on the file extension. However, selecting the correct type explicitly is highly recommended when dealing with `.txt` files or mixed media folders.

## Directory Structures

If you have multiple files, Kototoro can import them as comprehensive chapters/episodes using the "Import Folder" modes.

### 1. Single Folder Import (One Series)
Use **"Import folder (Single item)"** when your folder contains chapters or episodes of exactly one series.
The folder name will become the Title of the Series, and every file inside is treated as a chapter or episode.

**Example for Anime:**
```text
[Folder] Attack on Titan Season 1/
  ├─ Episode 01.mp4
  ├─ Episode 02.mp4
  └─ Episode 03.mkv
```

**Example for Novel:**
```text
[Folder] My Web Novel/
  ├─ Chapter 1.txt
  ├─ Chapter 2.txt
  └─ Chapter 3.txt
```

### 2. Multiple Folders Import (Multiple Series)
Use **"Import folder (Multiple items)"** when you have a large library folder containing many different series.
Each sub-folder will act as an independent series, and the files within them act as chapters/episodes.

**Example:**
```text
[Folder] My Local Anime Library/  (Select this folder during import)
  │
  ├─ [Folder] Naruto/
  │    ├─ Ep01.mp4
  │    └─ Ep02.mp4
  │
  └─ [Folder] Bleach/
       ├─ Ep01.mkv
       └─ Ep02.mkv
```
> [!TIP]
> In "Multiple items" mode, any loose standalone media files (`.mp4`, `.epub`) located at the root of the selected directory will also be imported individually as their own series, adopting the file name as the title.
## Metadata and `index.json`

Kototoro supports reading metadata and custom information through an `index.json` file. 

### Does it recognize `index.json`?
**Yes.** If your folder or `.zip`/`.cbz` file contains a properly formatted `index.json` file at its root, the local parser will automatically read it during the import process. It maps custom titles, authors, descriptions, cover images, tags, and chapter groupings directly to your library.

### Is `index.json` generated automatically?
**No.** The Local Import strictly acts as an importer. It maps the files or copies the folder "as-is" into the internal tracking directory. It will not synthesize or generate a brand new `index.json` for folders that do not have one. Instead, it generates the display data dynamically from the folder structures and file names. (Note: `index.json` *is* automatically generated when you *Download* online content, but not when you locally *Import* it).

### Can users customize information?
**Yes.** You have two ways to customize information for locally imported content:
1. **Pre-Import Editing:** Manually create or modify the `index.json` file inside your folder prior to importing it into Kototoro. The app will strictly respect your custom JSON definitions.
2. **In-App Editing:** After importing, you can often edit the series information (Title, Author, Tags) directly via the app's UI (by tapping the edit button on the detail page). The changes will be safely saved into Kototoro's internal database.
## How to Import
1. Go to **Settings > Content Sources > Local Storage**.
2. Tap the **+** (Plus) or **Import** button.
3. Choose the target **Content Type** (Manga, Novel, or Video) at the top of the dialog.
4. Select whether you are importing a single file, a single folder, or a multi-folder library.
5. The system will copy the files into its internal tracking structure. Once the notification indicates completion, you will find your items in your library.
