#include "ReadingStatsStore.h"

#include <Arduino.h>
#include <HalClock.h>
#include <Logging.h>
#include <Serialization.h>

#include <algorithm>
#include <cstdio>
#include <cstring>

namespace {
constexpr char STATS_DIR[] = "/.crosspoint/reading-stats";
constexpr char ACTIVE_PATH[] = "/.crosspoint/reading-stats/active.bin";
constexpr char INDEX_PATH[] = "/.crosspoint/reading-stats/index.bin";
constexpr char INDEX_TEMP_PATH[] = "/.crosspoint/reading-stats/index.tmp";
constexpr char COUNTER_PATH[] = "/.crosspoint/reading-stats/counter.bin";
constexpr uint32_t SESSION_MAGIC = 0x31545352;  // RST1
constexpr uint32_t INDEX_MAGIC = 0x31495352;    // RSI1
constexpr uint16_t FORMAT_VERSION = 1;

struct SessionHeader {
  uint32_t magic = SESSION_MAGIC;
  uint16_t version = FORMAT_VERSION;
  uint16_t headerSize = 0;
  uint32_t sessionId = 0;
  uint64_t startedEpochSeconds = 0;
  char title[97] = {};
};

struct SampleRecord {
  uint32_t elapsedMs = 0;
  uint16_t words = 0;
  uint16_t pageNumber = 0;
  uint8_t wordCountKnown = 1;
  uint8_t reserved[3] = {};
};

struct SessionFooter {
  uint32_t marker = 0x21444e45;  // END!
  uint64_t endedEpochSeconds = 0;
  uint16_t pageCount = 0;
  uint8_t hasWordCounts = 0;
  uint8_t reserved = 0;
};

uint64_t epochNow() {
  uint64_t epoch = 0;
  return halClock.getEpochSeconds(epoch) ? epoch : 0;
}
}  // namespace

ReadingStatsStore& ReadingStatsStore::getInstance() {
  static ReadingStatsStore instance;
  return instance;
}

std::string ReadingStatsStore::sessionPath(const uint32_t sessionId) const {
  char path[64];
  snprintf(path, sizeof(path), "%s/session-%08lx.bin", STATS_DIR, static_cast<unsigned long>(sessionId));
  return path;
}

uint32_t ReadingStatsStore::allocateSessionId() {
  uint32_t next = 1;
  HalFile input;
  if (Storage.openFileForRead("RST", COUNTER_PATH, input)) {
    serialization::readPod(input, next);
    input.close();
    if (next == 0) next = 1;
  }
  const uint32_t allocated = next++;
  HalFile output;
  if (Storage.openFileForWrite("RST", COUNTER_PATH, output)) {
    serialization::writePod(output, next);
    output.flush();
  }
  return allocated;
}

bool ReadingStatsStore::beginSession(const std::string& title) {
  if (active_) return true;
  Storage.mkdir("/.crosspoint");
  Storage.mkdir(STATS_DIR);
  // An interrupted, uncommitted journal is deliberately discarded. Completed
  // sessions have already been renamed and indexed before reader teardown ends.
  if (Storage.exists(ACTIVE_PATH)) Storage.remove(ACTIVE_PATH);

  activeSessionId_ = allocateSessionId();
  activeStartedEpoch_ = epochNow();
  std::memset(activeTitle_, 0, sizeof(activeTitle_));
  std::strncpy(activeTitle_, title.c_str(), sizeof(activeTitle_) - 1);

  SessionHeader header;
  header.headerSize = sizeof(SessionHeader);
  header.sessionId = activeSessionId_;
  header.startedEpochSeconds = activeStartedEpoch_;
  std::strncpy(header.title, activeTitle_, sizeof(header.title) - 1);
  HalFile file;
  if (!Storage.openFileForWrite("RST", ACTIVE_PATH, file) || file.write(&header, sizeof(header)) != sizeof(header)) {
    LOG_ERR("RST", "Could not create reading journal");
    resetActive();
    return false;
  }
  file.flush();
  active_ = true;
  return true;
}

void ReadingStatsStore::showPage(const uint32_t pageKey, const uint16_t pageNumber, const uint16_t words,
                                 const bool wordCountKnown) {
  if (!active_) return;
  if (pageDisplayed_ && displayedPageKey_ == pageKey) return;
  if (pageDisplayed_) completeDisplayedPage();
  pageDisplayed_ = true;
  displayedPageKey_ = pageKey;
  displayedPageNumber_ = pageNumber;
  displayedWords_ = words;
  displayedWordCountKnown_ = wordCountKnown;
  displayedAtMs_ = millis();
}

bool ReadingStatsStore::appendSample(const ReadingPageSample& sample) {
  HalFile file = Storage.open(ACTIVE_PATH, O_WRITE | O_APPEND);
  if (!file) return false;
  SampleRecord record;
  record.elapsedMs = sample.elapsedMs;
  record.words = sample.words;
  record.pageNumber = sample.pageNumber;
  record.wordCountKnown = displayedWordCountKnown_ ? 1 : 0;
  const bool ok = file.write(&record, sizeof(record)) == sizeof(record);
  file.flush();
  return ok;
}

void ReadingStatsStore::completeDisplayedPage() {
  if (!active_ || !pageDisplayed_ || activePageCount_ >= MAX_SESSION_PAGES) return;
  const uint32_t elapsed = millis() - displayedAtMs_;
  ReadingPageSample sample{elapsed, displayedWords_, displayedPageNumber_};
  if (appendSample(sample)) {
    activePageCount_++;
    activeHasWordCounts_ = activeHasWordCounts_ && displayedWordCountKnown_;
  } else {
    LOG_ERR("RST", "Could not append page sample");
  }
  pageDisplayed_ = false;
}

void ReadingStatsStore::finishSession() {
  if (!active_) return;
  completeDisplayedPage();
  if (activePageCount_ < MIN_SESSION_PAGES) {
    Storage.remove(ACTIVE_PATH);
    resetActive();
    return;
  }

  SessionFooter footer;
  footer.endedEpochSeconds = epochNow();
  footer.pageCount = activePageCount_;
  footer.hasWordCounts = activeHasWordCounts_ ? 1 : 0;
  HalFile file = Storage.open(ACTIVE_PATH, O_WRITE | O_APPEND);
  const bool footerWritten = file && file.write(&footer, sizeof(footer)) == sizeof(footer);
  if (file) file.flush();
  file.close();
  if (!footerWritten) {
    LOG_ERR("RST", "Could not finalize reading journal");
    resetActive();
    return;
  }

  loadIndex();
  if (pendingCount_ >= MAX_PENDING_SESSIONS) {
    Storage.remove(sessionPath(pendingIds_[0]).c_str());
    std::move(pendingIds_ + 1, pendingIds_ + pendingCount_, pendingIds_);
    pendingCount_--;
  }
  const std::string destination = sessionPath(activeSessionId_);
  Storage.remove(destination.c_str());
  if (Storage.rename(ACTIVE_PATH, destination.c_str())) {
    pendingIds_[pendingCount_++] = activeSessionId_;
    saveIndex();
    LOG_INF("RST", "Queued reading session %lu (%u pages)", static_cast<unsigned long>(activeSessionId_),
            activePageCount_);
  }
  resetActive();
}

void ReadingStatsStore::resetActive() {
  active_ = false;
  pageDisplayed_ = false;
  activeSessionId_ = 0;
  activeStartedEpoch_ = 0;
  activePageCount_ = 0;
  activeHasWordCounts_ = true;
  activeTitle_[0] = '\0';
}

bool ReadingStatsStore::loadIndex() {
  if (indexLoaded_) return true;
  indexLoaded_ = true;
  pendingCount_ = 0;
  HalFile file;
  if (!Storage.openFileForRead("RST", INDEX_PATH, file)) return true;
  uint32_t magic = 0;
  uint16_t version = 0;
  uint16_t count = 0;
  serialization::readPod(file, magic);
  serialization::readPod(file, version);
  serialization::readPod(file, count);
  if (magic != INDEX_MAGIC || version != FORMAT_VERSION || count > MAX_PENDING_SESSIONS) return false;
  for (uint16_t i = 0; i < count; ++i) {
    uint32_t id = 0;
    serialization::readPod(file, id);
    if (id && Storage.exists(sessionPath(id).c_str())) pendingIds_[pendingCount_++] = id;
  }
  return true;
}

bool ReadingStatsStore::saveIndex() const {
  HalFile file;
  if (!Storage.openFileForWrite("RST", INDEX_TEMP_PATH, file)) return false;
  serialization::writePod(file, INDEX_MAGIC);
  serialization::writePod(file, FORMAT_VERSION);
  serialization::writePod(file, pendingCount_);
  for (uint16_t i = 0; i < pendingCount_; ++i) serialization::writePod(file, pendingIds_[i]);
  file.flush();
  file.close();
  Storage.remove(INDEX_PATH);
  return Storage.rename(INDEX_TEMP_PATH, INDEX_PATH);
}

uint16_t ReadingStatsStore::pendingCount() {
  loadIndex();
  return pendingCount_;
}

bool ReadingStatsStore::readSessionHeader(const uint32_t sessionId, ReadingSessionInfo& info) {
  HalFile file;
  if (!Storage.openFileForRead("RST", sessionPath(sessionId), file)) return false;
  SessionHeader header;
  if (file.read(&header, sizeof(header)) != sizeof(header) || header.magic != SESSION_MAGIC ||
      header.version != FORMAT_VERSION || header.headerSize != sizeof(header)) {
    return false;
  }
  const size_t size = file.fileSize();
  if (size < sizeof(SessionHeader) + sizeof(SessionFooter)) return false;
  if (!file.seek(size - sizeof(SessionFooter))) return false;
  SessionFooter footer;
  if (file.read(&footer, sizeof(footer)) != sizeof(footer) || footer.marker != 0x21444e45) return false;
  info.sessionId = header.sessionId;
  info.startedEpochSeconds = header.startedEpochSeconds;
  info.endedEpochSeconds = footer.endedEpochSeconds;
  info.pageCount = footer.pageCount;
  info.hasWordCounts = footer.hasWordCounts != 0;
  std::strncpy(info.title, header.title, sizeof(info.title) - 1);
  return true;
}

bool ReadingStatsStore::sessionAt(const uint16_t index, ReadingSessionInfo& info) {
  loadIndex();
  return index < pendingCount_ && readSessionHeader(pendingIds_[index], info);
}

bool ReadingStatsStore::readSamples(const uint32_t sessionId, const uint16_t startIndex, ReadingPageSample* samples,
                                    const uint16_t capacity, uint16_t& readCount) {
  readCount = 0;
  if (!samples || capacity == 0) return false;
  ReadingSessionInfo info;
  if (!readSessionHeader(sessionId, info) || startIndex >= info.pageCount) return startIndex == info.pageCount;
  HalFile file;
  if (!Storage.openFileForRead("RST", sessionPath(sessionId), file) ||
      !file.seek(sizeof(SessionHeader) + static_cast<size_t>(startIndex) * sizeof(SampleRecord))) {
    return false;
  }
  const uint16_t wanted = std::min<uint16_t>(capacity, info.pageCount - startIndex);
  for (; readCount < wanted; ++readCount) {
    SampleRecord record;
    if (file.read(&record, sizeof(record)) != sizeof(record)) return false;
    samples[readCount] = {record.elapsedMs, record.words, record.pageNumber};
  }
  return true;
}

bool ReadingStatsStore::acknowledge(const uint32_t sessionId) {
  loadIndex();
  for (uint16_t i = 0; i < pendingCount_; ++i) {
    if (pendingIds_[i] != sessionId) continue;
    if (!Storage.remove(sessionPath(sessionId).c_str())) return false;
    std::move(pendingIds_ + i + 1, pendingIds_ + pendingCount_, pendingIds_ + i);
    pendingCount_--;
    return saveIndex();
  }
  return true;  // idempotent ACK
}
