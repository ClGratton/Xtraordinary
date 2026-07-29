#pragma once

#ifdef ENABLE_X3_COMPANION

#include <HalStorage.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

#include <array>
#include <cstddef>
#include <cstdint>
#include <memory>

#include "CompanionProtocol.h"
#include "SessionEngine.h"

class NimBLEAdvertising;
class NimBLECharacteristic;
class NimBLEServer;

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

  NimBLEServer* server_ = nullptr;
  NimBLEAdvertising* advertising_ = nullptr;
  NimBLECharacteristic* events_ = nullptr;
  std::unique_ptr<LibraryItem[]> library_;
  size_t libraryCount_ = 0;
  size_t librarySendIndex_ = 0;
  uint32_t libraryRevision_ = 0;
  bool librarySendPending_ = false;
  bool initialized_ = false;
  volatile uint16_t connectionHandle_ = 0xffff;
  volatile uint32_t connectedAtMs_ = 0;
  volatile uint32_t lastBleActivityMs_ = 0;
  volatile bool connectionParamsPending_ = false;
  volatile bool connectedOnceSinceBoot_ = false;
  uint32_t bootStartedAtMs_ = 0;
  DeviceActivity deviceActivity_ = DeviceActivity::BOOTING;
  SyncMode syncMode_ = SyncMode::FAST;
  uint32_t statusRevision_ = 1;
  uint32_t confirmedStatusRevision_ = 0;
  bool statusDirty_ = true;
  uint32_t normalPollSeconds_ = 15;
  uint32_t slowPollSeconds_ = 10 * 60;
  uint8_t sleepTimeoutMinutes_ = 5;
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
  uint32_t pendingResponseMessageId_ = 0;
  bool pendingResponse_ = false;
  bool pendingResponseIsNack_ = false;
  StaticQueue_t commandQueueState_{};
  std::array<uint8_t, sizeof(CommandPacket) * COMMAND_QUEUE_DEPTH> commandQueueStorage_{};
  QueueHandle_t commandQueue_ = nullptr;

  void handlePacket(const uint8_t* bytes, size_t length);
  bool sendResponse(uint32_t messageId, bool nack);
  void sendAck(uint32_t messageId);
  void sendNack(uint32_t messageId, const char* reason);
  void sendCapabilities(MessageType type = MessageType::CAPABILITIES);
  void observeDeviceActivity();
  void setDeviceActivity(DeviceActivity activity, SyncMode syncMode);
  void sendDeviceStatus(MessageType type = MessageType::STATUS_CHANGED);
  bool setPowerConfig(const EnvelopeView& envelope);
  bool confirmStatus(const EnvelopeView& envelope);
  bool scanLibrary();
  void scanDirectory(const char* path, uint8_t depth);
  void sendNextLibraryItem();
  bool beginFirmware(const EnvelopeView& envelope);
  bool writeFirmwareChunk(const EnvelopeView& envelope);
  bool commitFirmware();
  bool deleteLibraryEntries(const EnvelopeView& envelope);
  bool notify(MessageType type, const uint8_t* payload, size_t payloadLength);

 public:
  void begin();
  void loop();
  bool connected() const;
  bool requiresBleSafeClock() const { return initialized_ && !connected(); }
  bool requiresFullClock() const;
  bool shouldSleepAfterUnpairedBoot(uint32_t inactiveForMs) const;
  void prepareForSleep();
  SessionEngine& session() { return session_; }
  void onWrite(const uint8_t* bytes, size_t length);
  void onClientConnected(uint16_t connectionHandle);
  void onClientDisconnected();
};

extern CompanionService companionService;
}  // namespace companion

#endif
