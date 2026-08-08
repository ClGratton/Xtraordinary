#ifdef ENABLE_X3_COMPANION

#include "CompanionService.h"

#include <Arduino.h>
#include <Logging.h>
#include <Memory.h>
#include <NimBLEDevice.h>
#include <esp_system.h>
#include <mbedtls/sha256.h>
#include <HalGPIO.h>
#include <HalPowerManager.h>
#include <HalClock.h>

#include <algorithm>
#include <cctype>
#include <cstdio>
#include <cstring>
#include <iterator>

#include "MappedInputManager.h"
#include "ReadingStatsStore.h"
#include "CrossPointSettings.h"
#include "CompanionUiBridge.h"
#include "network/FirmwareFlasher.h"

extern MappedInputManager mappedInputManager;

namespace companion {
namespace {
constexpr char SERVICE_UUID[] = "7e400001-b5a3-f393-e0a9-e50e24dcca9e";
constexpr char CONTROL_UUID[] = "7e400002-b5a3-f393-e0a9-e50e24dcca9e";
constexpr char DATA_UUID[] = "7e400003-b5a3-f393-e0a9-e50e24dcca9e";
constexpr char EVENTS_UUID[] = "7e400004-b5a3-f393-e0a9-e50e24dcca9e";
constexpr char STATUS_UUID[] = "7e400005-b5a3-f393-e0a9-e50e24dcca9e";
constexpr char FIRMWARE_PATH[] = "/.crosspoint/companion/firmware.bin";
constexpr char TICKET_PATH[] = "/.crosspoint/companion/ticket.bin";
constexpr char RADIO_POLICY_PATH[] = "/.crosspoint/companion/radio.bin";
constexpr char BOOK_UPLOAD_TEMP_PATH[] = "/.crosspoint/companion/book-upload.tmp";
constexpr uint32_t TICKET_MAGIC = 0x544b5431;  // TKT1
constexpr uint16_t TICKET_STORAGE_VERSION = 1;
constexpr uint16_t ADVERTISING_INTERVAL = 800;  // 500 ms in 0.625 ms units
constexpr uint32_t FULL_CLOCK_AFTER_BLE_ACTIVITY_MS = 5000;
constexpr uint32_t CONNECTION_PARAMETER_DELAY_MS = 3000;
constexpr uint16_t CONNECTION_SUPERVISION_TIMEOUT = 1000;  // 10 s in 10 ms units.
constexpr uint8_t HELLO_REVISIONED_STATUS = 0x01;
constexpr uint32_t RADIO_RESUME_RETRY_MS = 1000;
constexpr uint32_t POWER_STATUS_INTERVAL_MS = 60u * 1000u;

bool hasBookExtension(const char* path) {
  const char* dot = std::strrchr(path, '.');
  if (!dot) return false;
  char ext[8] = {};
  size_t length = std::min<size_t>(std::strlen(dot), sizeof(ext) - 1);
  for (size_t i = 0; i < length; ++i) ext[i] = static_cast<char>(std::tolower(static_cast<unsigned char>(dot[i])));
  return std::strcmp(ext, ".epub") == 0 || std::strcmp(ext, ".txt") == 0 || std::strcmp(ext, ".xtc") == 0 ||
         std::strcmp(ext, ".pdf") == 0;
}

bool equalsIgnoreCase(const char* left, const char* right) {
  while (*left && *right) {
    if (std::tolower(static_cast<unsigned char>(*left)) !=
        std::tolower(static_cast<unsigned char>(*right))) {
      return false;
    }
    ++left;
    ++right;
  }
  return *left == '\0' && *right == '\0';
}

bool isGeneratedDeviceTextFile(const char* name) {
  if (!name) return false;
  char normalized[96] = {};
  size_t cursor = 0;
  for (const char* value = name; *value && *value != '.' && cursor + 1 < sizeof(normalized); ++value) {
    if (std::isalnum(static_cast<unsigned char>(*value))) {
      normalized[cursor++] = static_cast<char>(std::tolower(static_cast<unsigned char>(*value)));
    }
  }
  static constexpr const char* GENERATED_NAMES[] = {
      "crashreport", "readtime", "readingtime", "readingtimestats", "readingstats", "readstats",
  };
  return std::any_of(std::begin(GENERATED_NAMES), std::end(GENERATED_NAMES),
                     [normalized](const char* generated) { return std::strcmp(normalized, generated) == 0; });
}

bool readBoundedString(const EnvelopeView& envelope, size_t& cursor, char* output, size_t capacity) {
  if (!output || capacity == 0 || cursor + 2 > envelope.payloadLength) return false;
  const uint16_t length = readU16(envelope.payload + cursor);
  cursor += 2;
  if (length >= capacity || cursor + length > envelope.payloadLength) return false;
  std::memcpy(output, envelope.payload + cursor, length);
  output[length] = '\0';
  cursor += length;
  return true;
}

class WriteCallbacks final : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* characteristic, NimBLEConnInfo&) override {
    const NimBLEAttValue value = characteristic->getValue();
    companionService.onWrite(value.data(), value.size());
  }
};
WriteCallbacks writeCallbacks;

struct TicketStorageRecord {
  uint32_t magic = TICKET_MAGIC;
  uint16_t version = TICKET_STORAGE_VERSION;
  uint16_t size = sizeof(TicketState);
  TicketState ticket{};
};

struct RadioPolicyStorageRecord {
  uint32_t magic = 0x52414431;  // RAD1
  uint16_t version = 1;
  uint16_t fastWindowMinutes = 5;
  uint16_t slowIntervalMs = 2000;
  uint16_t sleepAfterMinutes = 10;
};

class ServerCallbacks final : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer*, NimBLEConnInfo& info) override {
    companionService.onClientConnected(info.getConnHandle());
  }

  void onDisconnect(NimBLEServer*, NimBLEConnInfo&, int) override { companionService.onClientDisconnected(); }
};
ServerCallbacks serverCallbacks;
}  // namespace

CompanionService companionService;

void CompanionService::begin() {
  loadRadioPolicy();
  loadTicket();
  commandQueue_ = xQueueCreateStatic(COMMAND_QUEUE_DEPTH, sizeof(CommandPacket), commandQueueStorage_.data(),
                                     &commandQueueState_);
  if (!commandQueue_) {
    LOG_ERR("CMP", "Failed to create command queue");
    return;
  }
  LOG_INF("CMP", "Starting BLE companion, free heap: %u", ESP.getFreeHeap());
  if (!NimBLEDevice::init("XTEINK Companion")) {
    LOG_ERR("CMP", "NimBLE initialization failed");
    return;
  }
  NimBLEDevice::setSecurityAuth(true, false, true);
  NimBLEDevice::setSecurityIOCap(BLE_HS_IO_NO_INPUT_OUTPUT);
  server_ = NimBLEDevice::createServer();
  if (!server_) {
    LOG_ERR("CMP", "Failed to create BLE server");
    return;
  }
  server_->setCallbacks(&serverCallbacks);
  auto* service = server_->createService(SERVICE_UUID);
  if (!service) {
    LOG_ERR("CMP", "Failed to create companion service");
    return;
  }
  auto properties = NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_ENC;
  auto* control = service->createCharacteristic(CONTROL_UUID, properties, MAX_PACKET_BYTES);
  auto* data = service->createCharacteristic(DATA_UUID, properties, MAX_PACKET_BYTES);
  events_ = service->createCharacteristic(EVENTS_UUID, NIMBLE_PROPERTY::NOTIFY, MAX_PACKET_BYTES);
  auto* status =
      service->createCharacteristic(STATUS_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY, MAX_PACKET_BYTES);
  if (!control || !data || !events_ || !status) {
    LOG_ERR("CMP", "Failed to create companion characteristics");
    return;
  }
  control->setCallbacks(&writeCallbacks);
  data->setCallbacks(&writeCallbacks);
  if (!server_->start()) {
    LOG_ERR("CMP", "Failed to start BLE server");
    return;
  }
  server_->advertiseOnDisconnect(true);
  advertising_ = NimBLEDevice::getAdvertising();
  if (!advertising_) {
    LOG_ERR("CMP", "BLE advertising unavailable");
    return;
  }
  advertising_->enableScanResponse(true);
  advertising_->setAdvertisingInterval(ADVERTISING_INTERVAL);
  const bool nameAdded = advertising_->setName("XTEINK Companion");
  const bool uuidAdded = advertising_->addServiceUUID(SERVICE_UUID);
  initialized_ = nameAdded && uuidAdded;
  const bool started = initialized_ && advertising_->start();
  advertisingWindowStartedAtMs_ = millis();
  LOG_INF("CMP", "BLE advertising name=%d UUID=%d started=%d active=%d free heap=%u", nameAdded, uuidAdded, started,
          advertising_->isAdvertising(), ESP.getFreeHeap());
}

bool CompanionService::connected() const { return server_ && server_->getConnectedCount() > 0; }

bool CompanionService::requiresBleSafeClock() const {
  return initialized_ && !readingRadioQuiet_ && !ticketRadioQuiet_;
}

bool CompanionService::requiresFullClock() const {
  // Starting/reconfiguring the controller and processing traffic stay at full
  // speed. Once advertising is running, the BLE-safe 80 MHz floor is enough;
  // keeping 160 MHz for the entire fast-discovery window only wastes battery.
  return radioResumePending_ || bookUploadActive_ ||
         (connected() && static_cast<uint32_t>(millis() - lastBleActivityMs_) < FULL_CLOCK_AFTER_BLE_ACTIVITY_MS);
}

void CompanionService::loop() {
  if (radioResumePending_ && static_cast<int32_t>(millis() - radioResumeRetryAtMs_) >= 0) resumeFastRadio();
  updateAdvertisingPolicy();
  if (connectionParamsPending_ && connected() &&
      static_cast<uint32_t>(millis() - connectedAtMs_) >= CONNECTION_PARAMETER_DELAY_MS) {
    connectionParamsPending_ = false;
    const uint16_t minimum = std::max<uint16_t>(80, slowConnectionIntervalUnits_ - slowConnectionIntervalUnits_ / 8);
    server_->updateConnParams(connectionHandle_, minimum, slowConnectionIntervalUnits_, 0,
                              CONNECTION_SUPERVISION_TIMEOUT);
    LOG_INF("CMP", "Requested %u ms slow BLE interval for handle %u",
            static_cast<unsigned>(slowConnectionIntervalUnits_ * 5u / 4u), connectionHandle_);
  }
  bool transportQueueAvailable = true;
  if (pendingResponse_) {
    if (sendResponse(pendingResponseMessageId_, pendingResponseIsNack_)) {
      pendingResponse_ = false;
    } else {
      transportQueueAvailable = false;
    }
  }
  CommandPacket command;
  if (transportQueueAvailable && commandQueue_ && xQueueReceive(commandQueue_, &command, 0) == pdTRUE) {
    handlePacket(command.bytes, command.length);
  }
  if (transportQueueAvailable && !pendingResponse_ && statusNotifyPending_ && connected()) {
    if (sendDeviceStatus()) {
      statusNotifyPending_ = false;
    } else {
      transportQueueAvailable = false;
    }
  }
  if (revisionedStatusSupported_ && connected() &&
      static_cast<uint32_t>(millis() - lastPowerStatusAtMs_) >= POWER_STATUS_INTERVAL_MS) {
    const uint8_t batteryPercent = static_cast<uint8_t>(std::min<uint16_t>(100, powerManager.getBatteryPercentage()));
    const bool charging = gpio.isUsbConnected();
    lastPowerStatusAtMs_ = millis();
    if (batteryPercent != lastReportedBatteryPercent_ || charging != lastReportedCharging_) {
      statusNotifyPending_ = true;
    }
  }
  session_.update();
  if (transportQueueAvailable && !pendingResponse_ && librarySendPending_ && connected()) sendNextLibraryItem();
  if (transportQueueAvailable && !pendingResponse_ && !librarySendPending_ && statsSendPending_ && connected()) {
    sendNextReadingStatsChunk();
  }
  if (applyPending_ && static_cast<int32_t>(millis() - applyAtMs_) >= 0) {
    applyPending_ = false;
    const auto result = firmware_flash::flashFromSdPath(FIRMWARE_PATH, nullptr, nullptr, true);
    if (result == firmware_flash::Result::OK) {
      delay(100);
      ESP.restart();
    }
  }
  if (ticketShowPending_ && static_cast<int32_t>(millis() - ticketUiAtMs_) >= 0) {
    ticketShowPending_ = false;
    showTicket(ticket_);
  } else if (ticketHidePending_ && static_cast<int32_t>(millis() - ticketUiAtMs_) >= 0) {
    ticketHidePending_ = false;
    hideTicketIfVisible();
  }
}

void CompanionService::setReading(bool reading) {
  if (reading_ == reading) return;

  reading_ = reading;
  ++statusRevision_;
  if (statusRevision_ == 0) ++statusRevision_;
  statusNotifyPending_ = revisionedStatusSupported_ && connected();
  readingSlowConfirmed_ = false;

  if (reading_ && !connected()) {
    // There is no phone transaction to finish. Reading needs neither BLE nor
    // an acknowledgement, so silence the radio immediately instead of burning
    // the remainder of the Home advertising window.
    readingRadioQuiet_ = true;
    server_->advertiseOnDisconnect(false);
    if (advertising_ && advertising_->isAdvertising()) advertising_->stop();
  } else if (!reading_ && !staticTicketPinned_) {
    // Resume the proven awake advertising policy from the main loop at full
    // clock. Pairing/security configuration and stored bonds are untouched.
    readingRadioQuiet_ = false;
    radioResumePending_ = true;
    radioResumeRetryAtMs_ = 0;
  }
}

void CompanionService::leaveTicket() {
  staticTicketPinned_ = false;
  ticketRadioQuiet_ = false;
  radioResumePending_ = true;
  radioResumeRetryAtMs_ = 0;
}

void CompanionService::wakeFastAdvertising() {
  if (!initialized_ || reading_ || staticTicketPinned_) return;
  // User input is an explicit request to make Home reachable again. Restart
  // from the main loop at full clock so both a stopped radio and a currently
  // slow advertiser return to the proven fast discovery path.
  radioResumePending_ = true;
  radioResumeRetryAtMs_ = 0;
}

void CompanionService::notifyPowerChanged() {
  if (!initialized_ || !revisionedStatusSupported_ || !connected()) return;
  statusNotifyPending_ = true;
}

void CompanionService::syncBeforeSleep(uint32_t windowMs) {
  if (!initialized_ || windowMs == 0) return;

  // Reading and Static intentionally keep their radios silent. A direct power
  // off would otherwise skip Home and give queued phone work no discovery
  // window at all. Offer one short fast-advertising window before the power
  // latch is released; stored bonds and the displayed e-ink frame are unchanged.
  setReading(false);
  readingRadioQuiet_ = false;
  ticketRadioQuiet_ = false;
  radioResumePending_ = true;
  radioResumeRetryAtMs_ = 0;
  resumeFastRadio();

  const uint32_t startedAt = millis();
  uint32_t connectedAt = 0;
  while (static_cast<uint32_t>(millis() - startedAt) < windowMs) {
    loop();
    if (connected() && connectedAt == 0) connectedAt = millis();
    if (connectedAt != 0 && static_cast<uint32_t>(millis() - connectedAt) >= 600 &&
        static_cast<uint32_t>(millis() - lastBleActivityMs_) >= 200 &&
        uxQueueMessagesWaiting(commandQueue_) == 0 && !pendingResponse_) {
      break;
    }
    delay(10);
  }
}

void CompanionService::onWrite(const uint8_t* bytes, size_t length) {
  lastBleActivityMs_ = millis();
  if (!commandQueue_ || !bytes || length == 0 || length > MAX_PACKET_BYTES) return;
  CommandPacket command;
  command.length = static_cast<uint16_t>(length);
  std::memcpy(command.bytes, bytes, length);
  xQueueSend(commandQueue_, &command, 0);
}

void CompanionService::onClientConnected(uint16_t connectionHandle) {
  connectionHandle_ = connectionHandle;
  connectedAtMs_ = millis();
  lastBleActivityMs_ = connectedAtMs_;
  connectionParamsPending_ = true;
  revisionedStatusSupported_ = false;
  statusNotifyPending_ = false;
  readingRadioQuiet_ = false;
  ticketRadioQuiet_ = false;
  LOG_INF("CMP", "BLE client connected handle=%u", connectionHandle);
}

void CompanionService::onClientDisconnected() {
  if (bookUploadActive_) abortBookUpload(false);
  connectionHandle_ = 0xffff;
  connectionParamsPending_ = false;
  revisionedStatusSupported_ = false;
  statusNotifyPending_ = false;
  // advertiseOnDisconnect is disabled only after Android has acknowledged the
  // current Reading / Slow revision. No bond data is changed.
  readingRadioQuiet_ = reading_ && readingSlowConfirmed_;
  ticketRadioQuiet_ = staticTicketPinned_;
  LOG_INF("CMP", "BLE client disconnected readingQuiet=%d ticketQuiet=%d", readingRadioQuiet_, ticketRadioQuiet_);
}

void CompanionService::handlePacket(const uint8_t* bytes, size_t length) {
  EnvelopeView envelope{};
  if (!decodeEnvelope(bytes, length, envelope)) return;
  bool ok = true;
  switch (envelope.type) {
    case MessageType::HELLO:
      revisionedStatusSupported_ =
          envelope.payloadLength >= 1 && (envelope.payload[0] & HELLO_REVISIONED_STATUS) != 0;
      statusNotifyPending_ = revisionedStatusSupported_;
      sendCapabilities();
      return;
    case MessageType::GET_STATUS:
      if (revisionedStatusSupported_) {
        statusNotifyPending_ = true;
      } else {
        sendCapabilities();
      }
      return;
    case MessageType::SET_CLOCK:
      ok = envelope.payloadLength == 8 && halClock.setEpochSeconds(readU64(envelope.payload));
      break;
    case MessageType::ACK_STATUS:
      if (envelope.payloadLength != 4) {
        ok = false;
        break;
      }
      if (readU32(envelope.payload) == statusRevision_ && reading_) {
        readingSlowConfirmed_ = true;
        // Android has stored the exact Reading / Slow revision. When it
        // intentionally releases GATT, leave advertising off so the idle
        // reader can return to the 10 MHz floor without altering the bond.
        server_->advertiseOnDisconnect(false);
      }
      break;
    case MessageType::START_SESSION: {
      if (envelope.payloadLength < 14) {
        ok = false;
        break;
      }
      const uint32_t duration = readU32(envelope.payload + 8);
      const uint16_t titleLength = readU16(envelope.payload + 12);
      ok = titleLength <= 160 && envelope.payloadLength == 14u + titleLength &&
           session_.start(duration, reinterpret_cast<const char*>(envelope.payload + 14), titleLength);
      if (ok) showFocus(session_);
      break;
    }
    case MessageType::PAUSE_SESSION:
      session_.pause();
      refreshFocus();
      break;
    case MessageType::RESUME_SESSION:
      session_.resume();
      refreshFocus();
      break;
    case MessageType::STOP_SESSION:
      session_.stop();
      showHome();
      break;
    case MessageType::SHOW_TICKET:
      ok = decodeTicket(envelope);
      if (ok) {
        ok = persistTicket();
      }
      if (ok) {
        ticketPresent_ = true;
        staticTicketPinned_ = ticket_.mode == TicketMode::STATIC;
        ticketRadioQuiet_ = false;
        server_->advertiseOnDisconnect(!staticTicketPinned_);
        // A full e-ink refresh can keep the main task busy for several seconds.
        // Queue the acknowledgement first, then defer the activity transition
        // by at least one configured connection interval so the notification
        // actually reaches Android before display rendering begins.
        sendAck(envelope.messageId);
        ticketHidePending_ = false;
        ticketShowPending_ = true;
        ticketUiAtMs_ = millis() + static_cast<uint32_t>(slowConnectionIntervalUnits_) * 5u / 4u + 250u;
        return;
      }
      break;
    case MessageType::CLEAR_TICKET:
      ok = envelope.payloadLength == 0 && clearTicket();
      if (ok) {
        // As above, let the acknowledgement leave over BLE before a possible
        // full Home refresh.
        sendAck(envelope.messageId);
        ticketShowPending_ = false;
        ticketHidePending_ = true;
        ticketUiAtMs_ = millis() + static_cast<uint32_t>(slowConnectionIntervalUnits_) * 5u / 4u + 250u;
        return;
      }
      break;
    case MessageType::SET_RADIO_POLICY:
      ok = applyRadioPolicy(envelope);
      break;
    case MessageType::SET_READER_POLICY:
      ok = applyReaderPolicy(envelope);
      break;
    case MessageType::GET_LIBRARY:
      ok = scanLibrary();
      break;
    case MessageType::DELETE_LIBRARY_ENTRIES:
      ok = deleteLibraryEntries(envelope);
      break;
    case MessageType::GET_READING_STATS:
      statsSessionIndex_ = 0;
      statsSampleIndex_ = 0;
      statsSendPending_ = true;
      break;
    case MessageType::ACK_READING_STATS:
      if (envelope.payloadLength != 4) {
        ok = false;
      } else {
        ok = READING_STATS.acknowledge(readU32(envelope.payload));
        if (ok) {
          statsSessionIndex_ = 0;
          statsSampleIndex_ = 0;
          statsSendPending_ = true;
        }
      }
      break;
    case MessageType::BEGIN_BOOK_UPLOAD:
      ok = beginBookUpload(envelope);
      break;
    case MessageType::BOOK_UPLOAD_CHUNK:
      ok = writeBookUploadChunk(envelope);
      break;
    case MessageType::COMMIT_BOOK_UPLOAD:
      ok = envelope.payloadLength == 0 && commitBookUpload();
      break;
    case MessageType::ABORT_BOOK_UPLOAD:
      ok = envelope.payloadLength == 0 && bookUploadActive_;
      if (ok) abortBookUpload();
      break;
    case MessageType::BEGIN_FIRMWARE:
      ok = beginFirmware(envelope);
      break;
    case MessageType::FIRMWARE_CHUNK:
      ok = writeFirmwareChunk(envelope);
      break;
    case MessageType::COMMIT_FIRMWARE:
      ok = commitFirmware();
      break;
    case MessageType::APPLY_FIRMWARE:
      ok = firmwareValidated_;
      if (ok) {
        applyPending_ = true;
        // The phone waits for this command's ACK, requests a clean GATT
        // disconnect, closes its native client, and lets Android settle before
        // the X3 disappears. Resetting immediately can wedge Android's GATT
        // state until the whole Bluetooth adapter is restarted.
        applyAtMs_ = millis() + 4000;
      }
      break;
    default:
      ok = false;
      break;
  }
  if (ok)
    sendAck(envelope.messageId);
  else
    sendNack(envelope.messageId, "Command rejected");
}

bool CompanionService::decodeTicket(const EnvelopeView& envelope) {
  if (envelope.payloadLength < 1) return false;
  const uint8_t mode = envelope.payload[0];
  if (mode > static_cast<uint8_t>(TicketMode::LIVE)) return false;

  size_t cursor = 1;
  TicketState decodedTicket{};
  decodedTicket.mode = static_cast<TicketMode>(mode);
  const bool decoded =
      readBoundedString(envelope, cursor, decodedTicket.origin, sizeof(decodedTicket.origin)) &&
      readBoundedString(envelope, cursor, decodedTicket.destination, sizeof(decodedTicket.destination)) &&
      readBoundedString(envelope, cursor, decodedTicket.flight, sizeof(decodedTicket.flight)) &&
      readBoundedString(envelope, cursor, decodedTicket.status, sizeof(decodedTicket.status)) &&
      readBoundedString(envelope, cursor, decodedTicket.departureTime, sizeof(decodedTicket.departureTime)) &&
      readBoundedString(envelope, cursor, decodedTicket.gate, sizeof(decodedTicket.gate)) &&
      readBoundedString(envelope, cursor, decodedTicket.terminal, sizeof(decodedTicket.terminal)) &&
      readBoundedString(envelope, cursor, decodedTicket.seat, sizeof(decodedTicket.seat)) &&
      readBoundedString(envelope, cursor, decodedTicket.passenger, sizeof(decodedTicket.passenger)) &&
      readBoundedString(envelope, cursor, decodedTicket.boardingGroup, sizeof(decodedTicket.boardingGroup)) &&
      readBoundedString(envelope, cursor, decodedTicket.barcodePayload, sizeof(decodedTicket.barcodePayload));
  if (!decoded || cursor != envelope.payloadLength || decodedTicket.barcodePayload[0] == '\0') return false;
  ticket_ = decodedTicket;
  return true;
}

bool CompanionService::loadTicket() {
  HalFile input = Storage.open(TICKET_PATH, O_RDONLY);
  if (!input || input.fileSize() != sizeof(TicketStorageRecord)) return false;
  TicketStorageRecord record{};
  if (input.read(&record, sizeof(record)) != sizeof(record) || record.magic != TICKET_MAGIC ||
      record.version != TICKET_STORAGE_VERSION || record.size != sizeof(TicketState) ||
      record.ticket.barcodePayload[0] == '\0') {
    input.close();
    Storage.remove(TICKET_PATH);
    return false;
  }
  ticket_ = record.ticket;
  ticketPresent_ = true;
  LOG_INF("CMP", "Restored saved ticket %s", ticket_.flight);
  return true;
}

bool CompanionService::persistTicket() {
  if (!Storage.ready()) {
    LOG_ERR("CMP", "SD unavailable; ticket retained in RAM only");
    return true;
  }
  if (!Storage.ensureDirectoryExists("/.crosspoint/companion")) {
    LOG_ERR("CMP", "Could not create ticket storage; retaining in RAM only");
    return true;
  }
  TicketStorageRecord record{};
  record.ticket = ticket_;
  HalFile output = Storage.open(TICKET_PATH, O_WRITE | O_CREAT | O_TRUNC);
  if (!output || output.write(&record, sizeof(record)) != sizeof(record)) {
    LOG_ERR("CMP", "Could not persist ticket; retaining in RAM only");
    return true;
  }
  output.flush();
  if (!output.close()) LOG_ERR("CMP", "Ticket file close failed; retaining in RAM only");
  return true;
}

bool CompanionService::clearTicket() {
  const bool removed = !Storage.ready() || !Storage.exists(TICKET_PATH) || Storage.remove(TICKET_PATH);
  if (!removed) return false;
  ticket_ = TicketState{};
  ticketPresent_ = false;
  leaveTicket();
  return true;
}

bool CompanionService::applyRadioPolicy(const EnvelopeView& envelope) {
  if (envelope.payloadLength != 6) return false;
  const uint16_t fastMinutes = readU16(envelope.payload);
  const uint16_t slowIntervalMs = readU16(envelope.payload + 2);
  const uint16_t sleepMinutes = readU16(envelope.payload + 4);
  if (fastMinutes < 1 || fastMinutes > 30 || slowIntervalMs < 500 || slowIntervalMs > 4000 ||
      sleepMinutes < 2 || sleepMinutes > 60 || fastMinutes >= sleepMinutes) {
    return false;
  }
  fastAdvertisingWindowMs_ = static_cast<uint32_t>(fastMinutes) * 60u * 1000u;
  companionSleepAfterMs_ = static_cast<uint32_t>(sleepMinutes) * 60u * 1000u;
  slowAdvertisingIntervalUnits_ = static_cast<uint16_t>((static_cast<uint32_t>(slowIntervalMs) * 8u) / 5u);
  slowConnectionIntervalUnits_ = static_cast<uint16_t>((static_cast<uint32_t>(slowIntervalMs) * 4u) / 5u);
  connectionParamsPending_ = connected();
  connectedAtMs_ = millis();
  return persistRadioPolicy();
}

bool CompanionService::applyReaderPolicy(const EnvelopeView& envelope) {
  if (envelope.payloadLength != 2) return false;
  switch (readU16(envelope.payload)) {
    case 1:
      SETTINGS.refreshFrequency = CrossPointSettings::REFRESH_1;
      break;
    case 5:
      SETTINGS.refreshFrequency = CrossPointSettings::REFRESH_5;
      break;
    case 10:
      SETTINGS.refreshFrequency = CrossPointSettings::REFRESH_10;
      break;
    case 15:
      SETTINGS.refreshFrequency = CrossPointSettings::REFRESH_15;
      break;
    case 30:
      SETTINGS.refreshFrequency = CrossPointSettings::REFRESH_30;
      break;
    default:
      return false;
  }
  return SETTINGS.saveToFile();
}

bool CompanionService::loadRadioPolicy() {
  if (!Storage.ready()) return false;
  HalFile input = Storage.open(RADIO_POLICY_PATH, O_RDONLY);
  if (!input || input.fileSize() != sizeof(RadioPolicyStorageRecord)) return false;
  RadioPolicyStorageRecord record{};
  const bool valid = input.read(&record, sizeof(record)) == sizeof(record) && record.magic == 0x52414431 &&
                     record.version == 1 && record.fastWindowMinutes >= 1 && record.fastWindowMinutes <= 30 &&
                     record.slowIntervalMs >= 500 && record.slowIntervalMs <= 4000 &&
                     record.sleepAfterMinutes >= 2 && record.sleepAfterMinutes <= 60 &&
                     record.fastWindowMinutes < record.sleepAfterMinutes;
  input.close();
  if (!valid) return false;
  fastAdvertisingWindowMs_ = static_cast<uint32_t>(record.fastWindowMinutes) * 60u * 1000u;
  companionSleepAfterMs_ = static_cast<uint32_t>(record.sleepAfterMinutes) * 60u * 1000u;
  slowAdvertisingIntervalUnits_ = static_cast<uint16_t>((static_cast<uint32_t>(record.slowIntervalMs) * 8u) / 5u);
  slowConnectionIntervalUnits_ = static_cast<uint16_t>((static_cast<uint32_t>(record.slowIntervalMs) * 4u) / 5u);
  return true;
}

bool CompanionService::persistRadioPolicy() {
  if (!Storage.ready() || !Storage.ensureDirectoryExists("/.crosspoint/companion")) return true;
  RadioPolicyStorageRecord record{};
  record.fastWindowMinutes = static_cast<uint16_t>(fastAdvertisingWindowMs_ / (60u * 1000u));
  record.slowIntervalMs = static_cast<uint16_t>(slowConnectionIntervalUnits_ * 5u / 4u);
  record.sleepAfterMinutes = static_cast<uint16_t>(companionSleepAfterMs_ / (60u * 1000u));
  HalFile output = Storage.open(RADIO_POLICY_PATH, O_WRITE | O_CREAT | O_TRUNC);
  if (!output || output.write(&record, sizeof(record)) != sizeof(record)) return false;
  output.flush();
  return output.close();
}

void CompanionService::armFastAdvertising() {
  if (!advertising_) return;
  advertisingWindowStartedAtMs_ = millis();
  slowAdvertising_ = false;
  advertisingWindowExpired_ = false;
  advertising_->setAdvertisingInterval(ADVERTISING_INTERVAL);
}

void CompanionService::updateAdvertisingPolicy() {
  if (!initialized_ || !advertising_ || connected() || readingRadioQuiet_ || ticketRadioQuiet_) {
    return;
  }
  const bool persistentSlowMode = session_.active() || (ticketPresent_ && ticket_.mode == TicketMode::LIVE);
  if (persistentSlowMode) {
    server_->advertiseOnDisconnect(true);
    if (!slowAdvertising_ || advertisingWindowExpired_) {
      advertising_->stop();
      advertising_->setAdvertisingInterval(slowAdvertisingIntervalUnits_);
      advertisingWindowExpired_ = false;
      slowAdvertising_ = true;
      advertising_->start();
      LOG_INF("CMP", "Active Focus / Live advertising at %u ms",
              static_cast<unsigned>(slowAdvertisingIntervalUnits_ * 5u / 8u));
    }
    return;
  }
  if (advertisingWindowExpired_) return;
  const uint32_t elapsed = millis() - advertisingWindowStartedAtMs_;
  if (elapsed >= companionSleepAfterMs_) {
    advertising_->stop();
    server_->advertiseOnDisconnect(false);
    advertisingWindowExpired_ = true;
    LOG_INF("CMP", "Companion advertising stopped at sleep deadline");
    return;
  }
  if (!slowAdvertising_ && elapsed >= fastAdvertisingWindowMs_) {
    advertising_->stop();
    advertising_->setAdvertisingInterval(slowAdvertisingIntervalUnits_);
    advertising_->start();
    slowAdvertising_ = true;
    LOG_INF("CMP", "Companion advertising slowed to %u ms",
            static_cast<unsigned>(slowAdvertisingIntervalUnits_ * 5u / 8u));
  }
}

bool CompanionService::sendResponse(uint32_t messageId, bool nack) {
  uint8_t payload[96];
  writeU32(payload, messageId);
  size_t length = 4;
  if (nack) {
    constexpr char reason[] = "Command rejected";
    std::memcpy(payload + length, reason, sizeof(reason) - 1);
    length += sizeof(reason) - 1;
  }
  return notify(nack ? MessageType::NACK : MessageType::ACK, payload, length);
}

void CompanionService::sendAck(uint32_t messageId) {
  if (sendResponse(messageId, false)) return;
  pendingResponseMessageId_ = messageId;
  pendingResponseIsNack_ = false;
  pendingResponse_ = true;
}

void CompanionService::sendNack(uint32_t messageId, const char* reason) {
  (void)reason;
  if (sendResponse(messageId, true)) return;
  pendingResponseMessageId_ = messageId;
  pendingResponseIsNack_ = true;
  pendingResponse_ = true;
}

void CompanionService::sendCapabilities(MessageType type) {
  uint8_t payload[128];
  size_t cursor = 0;
  const char* model = "X3";
  const uint16_t modelLength = static_cast<uint16_t>(std::strlen(model));
  writeU16(payload + cursor, modelLength);
  cursor += 2;
  std::memcpy(payload + cursor, model, modelLength);
  cursor += modelLength;
  const char* version = CROSSPOINT_VERSION;
  const uint16_t versionLength = static_cast<uint16_t>(std::strlen(version));
  writeU16(payload + cursor, versionLength);
  cursor += 2;
  std::memcpy(payload + cursor, version, versionLength);
  cursor += versionLength;
  writeU32(payload + cursor, libraryRevision_);
  cursor += 4;
  payload[cursor++] = 1;
  payload[cursor++] = ticketPresent_ ? 1 : 0;
  payload[cursor++] = 1;  // Supports SET_READER_POLICY.
  notify(type, payload, cursor);
}

bool CompanionService::sendDeviceStatus() {
  uint8_t payload[8];
  const uint8_t batteryPercent = static_cast<uint8_t>(std::min<uint16_t>(100, powerManager.getBatteryPercentage()));
  const bool charging = gpio.isUsbConnected();
  writeU32(payload, statusRevision_);
  payload[4] = static_cast<uint8_t>(reading_ ? DeviceActivity::READING : DeviceActivity::AWAKE);
  payload[5] = static_cast<uint8_t>(reading_ ? DeviceSyncMode::SLOW : DeviceSyncMode::FAST);
  payload[6] = batteryPercent;
  payload[7] = charging ? 1 : 0;
  lastReportedBatteryPercent_ = batteryPercent;
  lastReportedCharging_ = charging;
  lastPowerStatusAtMs_ = millis();
  return notify(MessageType::STATUS_CHANGED, payload, sizeof(payload));
}

void CompanionService::resumeFastRadio() {
  if (!server_ || !advertising_) {
    radioResumeRetryAtMs_ = millis() + RADIO_RESUME_RETRY_MS;
    return;
  }

  armFastAdvertising();
  server_->advertiseOnDisconnect(true);
  if (!connected() && advertising_->isAdvertising()) advertising_->stop();
  if (connected() || advertising_->start()) {
    radioResumePending_ = false;
    readingRadioQuiet_ = false;
    ticketRadioQuiet_ = false;
    LOG_INF("CMP", "Awake / Fast radio policy restored");
    return;
  }

  LOG_ERR("CMP", "Failed to restore Awake / Fast advertising; retrying");
  radioResumeRetryAtMs_ = millis() + RADIO_RESUME_RETRY_MS;
}

bool CompanionService::scanLibrary() {
  librarySendPending_ = false;
  library_.reset();
  library_ = makeUniqueNoThrow<LibraryItem[]>(MAX_LIBRARY_ITEMS);
  if (!library_) {
    LOG_ERR("CMP", "OOM allocating library snapshot: %u bytes", sizeof(LibraryItem) * MAX_LIBRARY_ITEMS);
    return false;
  }
  libraryCount_ = 0;
  scanDirectory("/", 0);
  uint32_t revision = 0xffffffffu;
  for (size_t i = 0; i < libraryCount_; ++i) {
    revision ^= crc32(reinterpret_cast<const uint8_t*>(library_[i].path), std::strlen(library_[i].path));
    revision ^= static_cast<uint32_t>(library_[i].size);
  }
  libraryRevision_ = revision;
  librarySendIndex_ = 0;
  librarySendPending_ = true;
  LOG_INF("CMP", "Library snapshot: %u items, free heap: %u", libraryCount_, ESP.getFreeHeap());
  return true;
}

void CompanionService::scanDirectory(const char* path, uint8_t depth) {
  if (!library_ || depth > 8 || libraryCount_ >= MAX_LIBRARY_ITEMS) return;
  HalFile directory = Storage.open(path);
  if (!directory || !directory.isDirectory()) return;
  directory.rewindDirectory();
  while (libraryCount_ < MAX_LIBRARY_ITEMS) {
    HalFile entry = directory.openNextFile();
    if (!entry) break;
    char rawName[181] = {};
    if (entry.getName(rawName, sizeof(rawName)) == 0) {
      entry.close();
      continue;
    }
    const char* name = rawName;
    while (*name == '/') ++name;
    if (const char* slash = std::strrchr(name, '/')) name = slash + 1;
    if (name[0] == '\0' || name[0] == '.') {
      entry.close();
      continue;
    }
    if (entry.isDirectory() &&
        (equalsIgnoreCase(name, "XTCache") || equalsIgnoreCase(name, "System Volume Information") ||
         equalsIgnoreCase(name, "LOST.DIR"))) {
      entry.close();
      continue;
    }
    char fullPath[181];
    const int written = std::strcmp(path, "/") == 0
                            ? std::snprintf(fullPath, sizeof(fullPath), "/%s", name)
                            : std::snprintf(fullPath, sizeof(fullPath), "%s/%s", path, name);
    if (written <= 0 || static_cast<size_t>(written) >= sizeof(fullPath)) {
      entry.close();
      continue;
    }
    const bool directoryEntry = entry.isDirectory();
    const bool bookExtension = hasBookExtension(fullPath);
    const bool generatedText = isGeneratedDeviceTextFile(name);
    if (directoryEntry) {
      entry.close();
      scanDirectory(fullPath, depth + 1);
    } else if (bookExtension && !generatedText) {
      auto& item = library_[libraryCount_++];
      std::strncpy(item.path, fullPath, sizeof(item.path) - 1);
      item.size = entry.fileSize64();
    }
  }
}

void CompanionService::sendNextLibraryItem() {
  if (!library_) {
    librarySendPending_ = false;
    return;
  }
  uint8_t payload[MAX_PAYLOAD_BYTES];
  size_t cursor = 0;
  writeU32(payload + cursor, libraryRevision_);
  cursor += 4;
  writeU16(payload + cursor, static_cast<uint16_t>(librarySendIndex_));
  cursor += 2;
  const bool empty = libraryCount_ == 0;
  const bool last = empty || librarySendIndex_ + 1 >= libraryCount_;
  payload[cursor++] = last ? 1 : 0;
  writeU16(payload + cursor, empty ? 0 : 1);
  cursor += 2;
  if (!empty) {
    const auto& item = library_[librarySendIndex_];
    const uint16_t pathLength = static_cast<uint16_t>(std::strlen(item.path));
    writeU16(payload + cursor, pathLength);
    cursor += 2;
    std::memcpy(payload + cursor, item.path, pathLength);
    cursor += pathLength;
    writeU64(payload + cursor, item.size);
    cursor += 8;
    writeU64(payload + cursor, 0);
    cursor += 8;
  }
  // NimBLE can reject a notification while its transmit queue is busy. Keep
  // the same page alive and retry on the next main-loop pass instead of
  // silently advancing to an incomplete (or entirely missing) snapshot.
  if (!notify(MessageType::LIBRARY_PAGE, payload, cursor)) return;
  if (last) {
    librarySendPending_ = false;
    library_.reset();
    LOG_INF("CMP", "Library snapshot sent, free heap: %u", ESP.getFreeHeap());
  } else {
    ++librarySendIndex_;
  }
}

void CompanionService::sendNextReadingStatsChunk() {
  if (!statsSendPending_ || !connected()) return;
  ReadingSessionInfo info;
  if (!READING_STATS.sessionAt(statsSessionIndex_, info)) {
    statsSendPending_ = false;
    return;
  }

  constexpr uint16_t MAX_SAMPLES_PER_CHUNK = 32;
  ReadingPageSample samples[MAX_SAMPLES_PER_CHUNK];
  uint16_t sampleCount = 0;
  if (!READING_STATS.readSamples(info.sessionId, statsSampleIndex_, samples, MAX_SAMPLES_PER_CHUNK, sampleCount)) {
    statsSendPending_ = false;
    return;
  }
  const size_t titleLength = strnlen(info.title, sizeof(info.title));
  uint8_t payload[MAX_PAYLOAD_BYTES] = {};
  size_t cursor = 0;
  writeU32(payload + cursor, info.sessionId);
  cursor += 4;
  writeU64(payload + cursor, info.startedEpochSeconds);
  cursor += 8;
  writeU64(payload + cursor, info.endedEpochSeconds);
  cursor += 8;
  writeU16(payload + cursor, info.pageCount);
  cursor += 2;
  writeU16(payload + cursor, statsSampleIndex_);
  cursor += 2;
  payload[cursor++] = statsSampleIndex_ + sampleCount >= info.pageCount ? 1 : 0;
  payload[cursor++] = info.hasWordCounts ? 1 : 0;
  writeU16(payload + cursor, sampleCount);
  cursor += 2;
  writeU16(payload + cursor, static_cast<uint16_t>(titleLength));
  cursor += 2;
  memcpy(payload + cursor, info.title, titleLength);
  cursor += titleLength;
  for (uint16_t i = 0; i < sampleCount; ++i) {
    writeU32(payload + cursor, samples[i].elapsedMs);
    cursor += 4;
    writeU16(payload + cursor, samples[i].words);
    cursor += 2;
    writeU16(payload + cursor, samples[i].pageNumber);
    cursor += 2;
  }
  if (!notify(MessageType::READING_STATS_CHUNK, payload, cursor)) return;
  statsSampleIndex_ += sampleCount;
  if (statsSampleIndex_ >= info.pageCount) {
    // Wait for Android's persisted-session ACK before advancing/deleting.
    statsSendPending_ = false;
  }
}

bool CompanionService::deleteLibraryEntries(const EnvelopeView& envelope) {
  if (envelope.payloadLength < 6) return false;
  const uint32_t expectedRevision = readU32(envelope.payload);
  if (expectedRevision != 0 && expectedRevision != libraryRevision_) return false;
  const uint16_t count = readU16(envelope.payload + 4);
  size_t cursor = 6;
  for (uint16_t i = 0; i < count; ++i) {
    if (cursor + 2 > envelope.payloadLength) return false;
    const uint16_t pathLength = readU16(envelope.payload + cursor);
    cursor += 2;
    if (pathLength == 0 || pathLength > 180 || cursor + pathLength > envelope.payloadLength) return false;
    char path[181] = {};
    std::memcpy(path, envelope.payload + cursor, pathLength);
    cursor += pathLength;
    if (path[0] != '/' || std::strstr(path, "..") || !hasBookExtension(path) || !Storage.remove(path)) return false;
  }
  return cursor == envelope.payloadLength && scanLibrary();
}

bool CompanionService::beginBookUpload(const EnvelopeView& envelope) {
  if (reading_ || bookUploadActive_ || !Storage.ready() || envelope.payloadLength < 2 + 8 + 32) return false;
  const uint16_t nameLength = readU16(envelope.payload);
  if (nameLength == 0 || nameLength > 160 || envelope.payloadLength != 2u + nameLength + 8u + 32u) return false;

  char fileName[161] = {};
  std::memcpy(fileName, envelope.payload + 2, nameLength);
  for (uint16_t i = 0; i < nameLength; ++i) {
    const unsigned char value = static_cast<unsigned char>(fileName[i]);
    if (value < 0x20 || fileName[i] == '/' || fileName[i] == '\\') return false;
  }
  if (fileName[0] == '.' || std::strstr(fileName, "..") || !hasBookExtension(fileName)) return false;

  bookUploadExpectedSize_ = readU64(envelope.payload + 2 + nameLength);
  if (bookUploadExpectedSize_ == 0 || bookUploadExpectedSize_ > 128u * 1024u * 1024u) return false;
  std::memcpy(bookUploadExpectedSha_, envelope.payload + 2 + nameLength + 8, sizeof(bookUploadExpectedSha_));
  if (std::snprintf(bookUploadFinalPath_, sizeof(bookUploadFinalPath_), "/Books/%s", fileName) <= 0 ||
      Storage.exists(bookUploadFinalPath_)) {
    return false;
  }
  if (!Storage.ensureDirectoryExists("/.crosspoint/companion") || !Storage.ensureDirectoryExists("/Books")) {
    return false;
  }
  Storage.remove(BOOK_UPLOAD_TEMP_PATH);
  bookUploadFile_ = Storage.open(BOOK_UPLOAD_TEMP_PATH, O_WRITE | O_CREAT | O_TRUNC);
  if (!bookUploadFile_) return false;

  bookUploadReceived_ = 0;
  bookUploadActive_ = true;
  connectionParamsPending_ = false;
  if (connected()) {
    server_->updateConnParams(connectionHandle_, 12, 24, 0, CONNECTION_SUPERVISION_TIMEOUT);
  }
  LOG_INF("CMP", "Book upload started path=%s bytes=%llu", bookUploadFinalPath_, bookUploadExpectedSize_);
  return true;
}

bool CompanionService::writeBookUploadChunk(const EnvelopeView& envelope) {
  if (!bookUploadActive_ || !bookUploadFile_ || envelope.payloadLength < 5) return false;
  const uint32_t offset = readU32(envelope.payload);
  const size_t count = envelope.payloadLength - 4;
  if (offset != bookUploadReceived_ || bookUploadReceived_ + count > bookUploadExpectedSize_) return false;
  if (bookUploadFile_.write(envelope.payload + 4, count) != count) return false;
  bookUploadReceived_ += count;
  return true;
}

bool CompanionService::commitBookUpload() {
  if (!bookUploadActive_ || !bookUploadFile_ || bookUploadReceived_ != bookUploadExpectedSize_) return false;
  bookUploadFile_.flush();
  if (!bookUploadFile_.close()) {
    abortBookUpload();
    return false;
  }

  HalFile input = Storage.open(BOOK_UPLOAD_TEMP_PATH, O_RDONLY);
  if (!input) {
    abortBookUpload();
    return false;
  }
  mbedtls_sha256_context context;
  mbedtls_sha256_init(&context);
  bool hashOk = mbedtls_sha256_starts(&context, 0) == 0;
  uint8_t buffer[1024];
  while (hashOk && input.available()) {
    const int count = input.read(buffer, sizeof(buffer));
    hashOk = count > 0 && mbedtls_sha256_update(&context, buffer, count) == 0;
  }
  uint8_t actual[32] = {};
  hashOk = hashOk && mbedtls_sha256_finish(&context, actual) == 0 &&
           std::memcmp(actual, bookUploadExpectedSha_, sizeof(actual)) == 0;
  mbedtls_sha256_free(&context);
  input.close();
  if (!hashOk || !Storage.rename(BOOK_UPLOAD_TEMP_PATH, bookUploadFinalPath_)) {
    abortBookUpload();
    return false;
  }

  LOG_INF("CMP", "Book upload committed path=%s", bookUploadFinalPath_);
  bookUploadActive_ = false;
  bookUploadFinalPath_[0] = '\0';
  connectedAtMs_ = millis();
  connectionParamsPending_ = connected();
  return scanLibrary();
}

void CompanionService::abortBookUpload(bool restoreSlowConnection) {
  if (bookUploadFile_) bookUploadFile_.close();
  Storage.remove(BOOK_UPLOAD_TEMP_PATH);
  bookUploadActive_ = false;
  bookUploadReceived_ = 0;
  bookUploadExpectedSize_ = 0;
  bookUploadFinalPath_[0] = '\0';
  if (restoreSlowConnection) {
    connectedAtMs_ = millis();
    connectionParamsPending_ = connected();
  }
}

bool CompanionService::beginFirmware(const EnvelopeView& envelope) {
  // A nearby bonded phone still needs a physical gesture before it can replace
  // the running image. This keeps firmware writes impossible while the device
  // is unattended.
  if (!mappedInputManager.isPressed(MappedInputManager::Button::Confirm)) return false;
  size_t cursor = 0;
  if (envelope.payloadLength < 2) return false;
  const uint16_t modelLength = readU16(envelope.payload);
  cursor = 2;
  if (modelLength == 0 || modelLength > 24 || cursor + modelLength + 2 > envelope.payloadLength) return false;
  std::memcpy(firmwareModel_, envelope.payload + cursor, modelLength);
  firmwareModel_[modelLength] = '\0';
  cursor += modelLength;
  const uint16_t versionLength = readU16(envelope.payload + cursor);
  cursor += 2;
  if (versionLength > 48 || cursor + versionLength + 8 + 32 != envelope.payloadLength) return false;
  cursor += versionLength;
  firmwareExpectedSize_ = readU64(envelope.payload + cursor);
  cursor += 8;
  std::memcpy(firmwareExpectedSha_, envelope.payload + cursor, 32);
  if (std::strcmp(firmwareModel_, "X3") != 0 || firmwareExpectedSize_ < 1024 || firmwareExpectedSize_ > 0x640000)
    return false;
  Storage.ensureDirectoryExists("/.crosspoint/companion");
  firmwareFile_ = Storage.open(FIRMWARE_PATH, O_WRITE | O_CREAT | O_TRUNC);
  firmwareReceived_ = 0;
  firmwareValidated_ = false;
  return static_cast<bool>(firmwareFile_);
}

bool CompanionService::writeFirmwareChunk(const EnvelopeView& envelope) {
  if (!firmwareFile_ || envelope.payloadLength < 5) return false;
  const uint32_t offset = readU32(envelope.payload);
  const size_t count = envelope.payloadLength - 4;
  if (offset != firmwareReceived_ || firmwareReceived_ + count > firmwareExpectedSize_) return false;
  if (firmwareFile_.write(envelope.payload + 4, count) != count) return false;
  firmwareReceived_ += count;
  return true;
}

bool CompanionService::commitFirmware() {
  if (!firmwareFile_ || firmwareReceived_ != firmwareExpectedSize_) return false;
  firmwareFile_.flush();
  firmwareFile_.close();
  HalFile input = Storage.open(FIRMWARE_PATH, O_RDONLY);
  if (!input) return false;
  mbedtls_sha256_context context;
  mbedtls_sha256_init(&context);
  if (mbedtls_sha256_starts(&context, 0) != 0) return false;
  uint8_t buffer[1024];
  while (input.available()) {
    const int count = input.read(buffer, sizeof(buffer));
    if (count <= 0 || mbedtls_sha256_update(&context, buffer, count) != 0) {
      mbedtls_sha256_free(&context);
      return false;
    }
  }
  uint8_t actual[32];
  const bool hashOk = mbedtls_sha256_finish(&context, actual) == 0 &&
                      std::memcmp(actual, firmwareExpectedSha_, sizeof(actual)) == 0;
  mbedtls_sha256_free(&context);
  if (!hashOk) return false;
  firmwareValidated_ = firmware_flash::validateImageFile(FIRMWARE_PATH, 0x640000) == firmware_flash::Result::OK;
  return firmwareValidated_;
}

bool CompanionService::notify(MessageType type, const uint8_t* payload, size_t payloadLength) {
  if (!events_ || !connected()) return false;
  uint8_t packet[MAX_PACKET_BYTES];
  const size_t length = encodeEnvelope(type, outgoingMessageId_++, payload, payloadLength, packet, sizeof(packet));
  return length && events_->notify(packet, length);
}
}  // namespace companion

#endif
