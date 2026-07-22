#pragma once

#ifdef ENABLE_X3_COMPANION

#include <HalStorage.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

#include <array>
#include <cstddef>
#include <cstdint>

#include "CompanionProtocol.h"
#include "SessionEngine.h"

class NimBLECharacteristic;

namespace companion {

class CompanionService {
  struct CommandPacket {
    uint16_t length = 0;
    uint8_t bytes[MAX_PACKET_BYTES] = {};
  };
  static constexpr size_t COMMAND_QUEUE_DEPTH = 8;
  struct LibraryItem {
    char path[181] = {};
    uint64_t size = 0;
  };
  static constexpr size_t MAX_LIBRARY_ITEMS = 128;

  NimBLECharacteristic* events_ = nullptr;
  std::array<LibraryItem, MAX_LIBRARY_ITEMS> library_{};
  size_t libraryCount_ = 0;
  size_t librarySendIndex_ = 0;
  uint32_t libraryRevision_ = 0;
  bool librarySendPending_ = false;
  SessionEngine session_;
  HalFile firmwareFile_;
  uint64_t firmwareExpectedSize_ = 0;
  uint64_t firmwareReceived_ = 0;
  uint8_t firmwareExpectedSha_[32] = {};
  char firmwareModel_[25] = {};
  bool firmwareValidated_ = false;
  bool applyPending_ = false;
  uint32_t applyAtMs_ = 0;
  uint32_t outgoingMessageId_ = 1;
  StaticQueue_t commandQueueState_{};
  std::array<uint8_t, sizeof(CommandPacket) * COMMAND_QUEUE_DEPTH> commandQueueStorage_{};
  QueueHandle_t commandQueue_ = nullptr;

  void handlePacket(const uint8_t* bytes, size_t length);
  void sendAck(uint32_t messageId);
  void sendNack(uint32_t messageId, const char* reason);
  void sendCapabilities(MessageType type = MessageType::CAPABILITIES);
  void scanLibrary();
  void scanDirectory(const char* path, uint8_t depth);
  void sendNextLibraryItem();
  bool beginFirmware(const EnvelopeView& envelope);
  bool writeFirmwareChunk(const EnvelopeView& envelope);
  bool commitFirmware();
  bool deleteLibraryEntries(const EnvelopeView& envelope);
  void notify(MessageType type, const uint8_t* payload, size_t payloadLength);

 public:
  void begin();
  void loop();
  bool connected() const;
  SessionEngine& session() { return session_; }
  void onWrite(const uint8_t* bytes, size_t length);
};

extern CompanionService companionService;
}  // namespace companion

#endif
