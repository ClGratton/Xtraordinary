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
#include "TicketState.h"

class NimBLEAdvertising;
class NimBLECharacteristic;
class NimBLEServer;

namespace companion {

enum class DeepSleepFinalizeResult : uint8_t {
  Ready,
  WakeCancelled,
  WakeRequiresRestart,
  TimedOut,
};

using WakeRequestProbe = bool (*)();

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
  uint16_t statsSessionIndex_ = 0;
  uint16_t statsSampleIndex_ = 0;
  bool statsSendPending_ = false;
  bool initialized_ = false;
  volatile uint16_t connectionHandle_ = 0xffff;
  volatile uint32_t connectedAtMs_ = 0;
  volatile uint32_t lastBleActivityMs_ = 0;
  volatile uint32_t interactiveLeaseUntilMs_ = 0;
  volatile bool connectionParamsPending_ = false;
  volatile bool deepSleepShutdownFinished_ = false;
  volatile bool deepSleepShutdownStopped_ = false;
  volatile bool deepSleepShutdownStarted_ = false;
  volatile bool deepSleepWakeRequested_ = false;
  volatile bool deepSleepPreparing_ = false;
  uint32_t deepSleepSyncWindowMs_ = 0;
  SessionEngine session_;
  TicketState ticket_;
  HalFile ticketBarcodeFile_;
  uint32_t ticketBarcodeExpectedSize_ = 0;
  uint32_t ticketBarcodeReceived_ = 0;
  bool ticketBarcodeUploadActive_ = false;
  bool ticketBarcodeCommitted_ = false;
  HalFile firmwareFile_;
  uint64_t firmwareExpectedSize_ = 0;
  uint64_t firmwareReceived_ = 0;
  uint8_t firmwareExpectedSha_[32] = {};
  char firmwareModel_[25] = {};
  bool firmwareValidated_ = false;
  HalFile bookUploadFile_;
  uint64_t bookUploadExpectedSize_ = 0;
  uint64_t bookUploadReceived_ = 0;
  uint8_t bookUploadExpectedSha_[32] = {};
  char bookUploadFinalPath_[181] = {};
  char bookUploadBackupPath_[181] = {};
  bool bookUploadActive_ = false;
  enum class BookUploadOwner : uint8_t { None, Ble, Usb };
  BookUploadOwner bookUploadOwner_ = BookUploadOwner::None;
  uint32_t bookUploadLastActivityMs_ = 0;
  bool applyPending_ = false;
  uint32_t applyAtMs_ = 0;
  uint32_t outgoingMessageId_ = 1;
  uint32_t pendingResponseMessageId_ = 0;
  bool pendingResponse_ = false;
  bool pendingResponseIsNack_ = false;
  bool revisionedStatusSupported_ = false;
  bool reading_ = false;
  bool readingSlowConfirmed_ = false;
  bool readingRadioQuiet_ = false;
  bool ticketPresent_ = false;
  bool staticTicketPinned_ = false;
  bool liveTicketDisplayed_ = false;
  bool ticketRadioQuiet_ = false;
  bool ticketShowPending_ = false;
  bool ticketHidePending_ = false;
  bool slowAdvertising_ = false;
  bool advertisingWindowExpired_ = false;
  bool standbyPulseActive_ = false;
  bool statusNotifyPending_ = false;
  bool radioResumePending_ = false;
  uint32_t statusRevision_ = 1;
  uint32_t lastPowerStatusAtMs_ = 0;
  uint8_t lastReportedBatteryPercent_ = 0xff;
  bool lastReportedCharging_ = false;
  uint32_t radioResumeRetryAtMs_ = 0;
  uint32_t advertisingWindowStartedAtMs_ = 0;
  uint32_t standbyPulseStartedAtMs_ = 0;
  uint32_t ticketUiAtMs_ = 0;
  uint32_t fastAdvertisingWindowMs_ = 5u * 60u * 1000u;
  uint32_t companionSleepAfterMs_ = 10u * 60u * 1000u;
  uint32_t standbyAdvertisingIntervalMs_ = 30u * 1000u;
  uint16_t slowConnectionIntervalUnits_ = 1600;  // 2 s in 1.25 ms units.
  StaticQueue_t commandQueueState_{};
  std::array<uint8_t, sizeof(CommandPacket) * COMMAND_QUEUE_DEPTH> commandQueueStorage_{};
  QueueHandle_t commandQueue_ = nullptr;

  void handlePacket(const uint8_t* bytes, size_t length);
  bool sendResponse(uint32_t messageId, bool nack);
  void sendAck(uint32_t messageId);
  void sendNack(uint32_t messageId, const char* reason);
  void sendCapabilities(MessageType type = MessageType::CAPABILITIES);
  bool sendDeviceStatus();
  bool shouldUseSlowConnection() const;
  bool interactiveLeaseActive() const;
  void acquireInteractiveLease(uint16_t seconds);
  void requestFastConnection();
  void scheduleSlowConnection();
  bool decodeTicket(const EnvelopeView& envelope);
  bool loadTicket();
  bool persistTicket();
  bool clearTicket();
  bool beginTicketBarcode(const EnvelopeView& envelope);
  bool writeTicketBarcodeChunk(const EnvelopeView& envelope);
  bool commitTicketBarcode();
  bool promoteTicketBarcode();
  void abortTicketBarcode();
  bool applyRadioPolicy(const EnvelopeView& envelope);
  bool applyReaderPolicy(const EnvelopeView& envelope);
  bool loadRadioPolicy();
  bool persistRadioPolicy();
  void updateAdvertisingPolicy();
  void armFastAdvertising();
  void enterStandbyAdvertising();
  void updateStandbyAdvertising();
  bool standbyPulseStartDue() const;
  void resumeFastRadio();
  bool scanLibrary();
  void scanDirectory(const char* path, uint8_t depth);
  void sendNextLibraryItem();
  void sendNextReadingStatsChunk();
  bool beginFirmware(const EnvelopeView& envelope);
  bool writeFirmwareChunk(const EnvelopeView& envelope);
  bool commitFirmware();
  bool beginBookUpload(const EnvelopeView& envelope, BookUploadOwner owner);
  bool writeBookUploadChunk(const EnvelopeView& envelope, BookUploadOwner owner);
  bool commitBookUpload(BookUploadOwner owner);
  void abortBookUpload(bool restoreSlowConnection = true);
  bool deleteLibraryEntries(const EnvelopeView& envelope);
  bool notify(MessageType type, const uint8_t* payload, size_t payloadLength);

 public:
  void begin();
  void loop();
  bool connected() const;
  bool requiresBleSafeClock() const;
  bool requiresFullClock() const;
  bool isSlowAdvertising() const {
    return initialized_ && slowAdvertising_ && !advertisingWindowExpired_ && !connected() && !readingRadioQuiet_ &&
           !ticketRadioQuiet_;
  }
  uint32_t standbyAdvertisingIntervalMs() const { return standbyAdvertisingIntervalMs_; }
  uint32_t companionSleepAfterMs() const { return companionSleepAfterMs_; }
  bool staticTicketDisplayed() const { return staticTicketPinned_; }
  SessionEngine& session() { return session_; }
  bool hasTicket() const { return ticketPresent_; }
  TicketState& ticket() { return ticket_; }
  void setReading(bool reading);
  void enterTicket();
  void leaveTicket();
  void wakeFastAdvertising();
  void notifyPowerChanged();
  DeepSleepFinalizeResult finalizeForDeepSleep(uint32_t syncWindowMs, uint32_t timeoutMs,
                                               WakeRequestProbe wakeRequested);
  void onWrite(const uint8_t* bytes, size_t length);
  bool handleUsbBookPacket(const uint8_t* bytes, size_t length, uint32_t& messageId);
  void onClientConnected(uint16_t connectionHandle);
  void onClientDisconnected();
};

extern CompanionService companionService;
}  // namespace companion

#endif
