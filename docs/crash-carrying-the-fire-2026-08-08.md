# Carrying the Fire page crash — 2026-08-08

## Evidence

Persistent X3 crash report from `xtraordinary-v0.2.6-dev7`:

- panic: `abort()` / `std::terminate` on core 0;
- immediately preceding logs: optional CSS cache skipped at about 56 KiB free heap, inflate-reader initialization failed, then embedded-image extraction failed;
- exact ELF symbolization reaches `ParsedText::addWord`, called from `ChapterHtmlSlimParser::flushPartWordBuffer` during `Section::createSectionFile`.

The failing allocation was the set of parallel token vectors. `words.push_back()` could grow successfully and the following `wordStyles.push_back()` could not obtain another contiguous block, so the no-exceptions firmware terminated. The earlier image extraction also attempted a 32 KiB inflate dictionary plus buffers when the largest contiguous block was insufficient.

## Correction

- Embedded image extraction now checks both total free heap and maximum contiguous allocation before initializing the streaming inflater, uses 1 KiB chunks, and removes a partial image cache on failure.
- Parsed text now reserves every parallel token vector together before mutating sizes. Because the firmware disables C++ exceptions, it checks both total and maximum allocatable heap before every `reserve()` and skips the remaining token when safe growth is impossible.
- The exact release firmware target compiles and links with these guards. `xtraordinary-v0.2.6-dev8-local` was flashed application-only on 2026-08-08 and esptool verified the written data hash. No claim of page-level success or image rendering is made until the same page is opened on hardware.
