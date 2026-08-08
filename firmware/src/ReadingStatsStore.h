#pragma once

#include <HalStorage.h>

#include <cstddef>
#include <cstdint>
#include <string>

struct ReadingPageSample {
  uint32_t elapsedMs = 0;
  uint16_t words = 0;
  uint16_t pageNumber = 0;
};

struct ReadingSessionInfo {
  uint32_t sessionId = 0;
  uint64_t startedEpochSeconds = 0;
  uint64_t endedEpochSeconds = 0;
  uint16_t pageCount = 0;
  bool hasWordCounts = false;
  char title[97] = {};
};

// Offline-first reading journal. Page samples are flushed to the SD card as
// they are completed; completed sessions remain queued until Android ACKs the
// exact session id.
class ReadingStatsStore {
 public:
  static constexpr uint16_t MIN_SESSION_PAGES = 3;
  static constexpr uint16_t MAX_SESSION_PAGES = 2048;
  static constexpr uint16_t MAX_PENDING_SESSIONS = 128;

  static ReadingStatsStore& getInstance();

  bool beginSession(const std::string& title);
  void showPage(uint32_t pageKey, uint16_t pageNumber, uint16_t words, bool wordCountKnown = true);
  void completeDisplayedPage();
  void finishSession();

  uint16_t pendingCount();
  bool sessionAt(uint16_t index, ReadingSessionInfo& info);
  bool readSamples(uint32_t sessionId, uint16_t startIndex, ReadingPageSample* samples, uint16_t capacity,
                   uint16_t& readCount);
  bool acknowledge(uint32_t sessionId);

 private:
  ReadingStatsStore() = default;
  bool loadIndex();
  bool saveIndex() const;
  bool readSessionHeader(uint32_t sessionId, ReadingSessionInfo& info);
  bool appendSample(const ReadingPageSample& sample);
  std::string sessionPath(uint32_t sessionId) const;
  uint32_t allocateSessionId();
  void resetActive();

  uint32_t pendingIds_[MAX_PENDING_SESSIONS] = {};
  uint16_t pendingCount_ = 0;
  bool indexLoaded_ = false;

  bool active_ = false;
  uint32_t activeSessionId_ = 0;
  uint64_t activeStartedEpoch_ = 0;
  uint16_t activePageCount_ = 0;
  bool activeHasWordCounts_ = true;
  bool pageDisplayed_ = false;
  uint32_t displayedPageKey_ = 0;
  uint16_t displayedPageNumber_ = 0;
  uint16_t displayedWords_ = 0;
  bool displayedWordCountKnown_ = true;
  uint32_t displayedAtMs_ = 0;
  char activeTitle_[97] = {};
};

#define READING_STATS ReadingStatsStore::getInstance()
