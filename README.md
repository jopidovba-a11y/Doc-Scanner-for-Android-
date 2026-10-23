# Doc Scanner

A small Android app to scan documents (ID cards, licenses, etc.) with your phone and export them to PDF with full control over each image's size and position on the page.

I built it because most scanner apps make it hard to put several scans on a single page, or to resize them. This one is made for cases like: scan both sides of two ID cards in one go, then get one page per card with front and back at the size you want.

<img width="1220" height="2712" alt="Screenshot_2026-10-23-16-26-26-280_com example docscanner" src="https://github.com/user-attachments/assets/db19f7ff-c8f8-48e9-9f9c-20adbfb28c74" />


## Features

- Scan multiple documents in a single pass (auto edge detection, crop and perspective correction via Google's ML Kit Document Scanner)
- Import images from the gallery
- Add scans to as many A4 pages as you need
- Drag to move, pinch to resize, rotate, center, or use a slider (size shown in mm)
- "ID size" button to set the real size of an ID card (85.6 × 54 mm)
- Export a single PDF with all pages, or one PDF per page
- PDFs are saved to the Downloads folder and can be shared right away


## Requirements

- Android 10 (API 29) or higher
- Google Play services (required by the ML Kit scanner)

## Notes

- Images are downscaled to about 200 DPI when exporting to keep PDF files small
- The page size is fixed to A4 for now

## License

Personal project. Use it however you like.
