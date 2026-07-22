#ifdef ENABLE_X3_COMPANION

#include "CompanionService.h"

#include <NimBLEDevice.h>
#include <esp_system.h>
#include <mbedtls/sha256.h>

#include <algorithm>
#include <cctype>
#include <cstdio>
#include <cstring>

#include "MappedInputManager.h"
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

bool hasBookExtension(const char* path) {
  const char* dot = std::strrchr(path, '.');
  if (!dot) return false;
  char ext[8] = {};
  size_t length = std::min<size_t>(std::strlen(dot), sizeof(ext) - 1);
  for (size_t i = 0; i < length; ++i) ext[i] = static_cast<char>(std::tolower(static_cast<unsigned char>(dot[i])));
  return std::strcmp(ext, ".epub") == 0 || std::strcmp(ext, ".txt") == 0 || std::strcmp(ext, ".xtc") == 0 ||
         std::strcmp(ext, ".pdf") == 0;
}

class WriteCallbacks final : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* characteristic, NimBLEConnInfo&) override {
    const NimBLEAttValue value = characteristic->getValue();
    companionService.onWrite(value.data(), value.size());
  }
};
WriteCallbacks writeCallbacks;
}  // namespace

CompanionService companionService;

void CompanionService::begin() {
  commandQueue_ = xQueueCreateStatic(COMMAND_QUEUE_DEPTH, sizeof(CommandPacket), commandQueueStorage_.data(),
                                     &commandQueueState_);
  NimBLEDevice::init("XTEINK Companion");
  NimBLEDevice::setSecurityAuth(true, false, true);
  NimBLEDevice::setSecurityIOCap(BLE_HS_IO_NO_INPUT_OUTPUT);
  auto* server = NimBLEDevice::createServer();
  auto* service = server->createService(SERVICE_UUID);
  auto properties = NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_ENC;
  auto* control = service->createCharacteristic(CONTROL_UUID, properties, MAX_PACKET_BYTES);
  auto* data = service->createCharacteristic(DATA_UUID, properties, MAX_PACKET_BYTES);
  events_ = service->createCharacteristic(EVENTS_UUID, NIMBLE_PROPERTY::NOTIFY, MAX_PACKET_BYTES);
  service->createCharacteristic(STATUS_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY, MAX_PACKET_BYTES);
  control->setCallbacks(&writeCallbacks);
  data->setCallbacks(&writeCallbacks);
  server->start();
  auto* advertising = NimBLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->enableScanResponse(true);
  advertising->start();
}

bool CompanionService::connected() const { return NimBLEDevice::getServer()->getConnectedCount() > 0; }

void CompanionService::loop() {
  CommandPacket command;
  if (commandQueue_ && xQueueReceive(commandQueue_, &command, 0) == pdTRUE) handlePacket(command.bytes, command.length);
  session_.update();
  if (librarySendPending_ && connected()) sendNextLibraryItem();
  if (applyPending_ && static_cast<int32_t>(millis() - applyAtMs_) >= 0) {
    applyPending_ = false;
    const auto result = firmware_flash::flashFromSdPath(FIRMWARE_PATH, nullptr, nullptr, true);
    if (result == firmware_flash::Result::OK) {
      delay(100);
      ESP.restart();
    }
  }
}

void CompanionService::onWrite(const uint8_t* bytes, size_t length) {
  if (!commandQueue_ || !bytes || length == 0 || length > MAX_PACKET_BYTES) return;
  CommandPacket command;
  command.length = static_cast<uint16_t>(length);
  std::memcpy(command.bytes, bytes, length);
  xQueueSend(commandQueue_, &command, 0);
}

void CompanionService::handlePacket(const uint8_t* bytes, size_t length) {
  EnvelopeView envelope{};
  if (!decodeEnvelope(bytes, length, envelope)) return;
  bool ok = true;
  switch (envelope.type) {
    case MessageType::HELLO:
    case MessageType::GET_STATUS:
      sendCapabilities();
      return;
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
    case MessageType::GET_LIBRARY:
      scanLibrary();
      break;
    case MessageType::DELETE_LIBRARY_ENTRIES:
      ok = deleteLibraryEntries(envelope);
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
        applyAtMs_ = millis() + 400;
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

void CompanionService::sendAck(uint32_t messageId) {
  uint8_t payload[4];
  writeU32(payload, messageId);
  notify(MessageType::ACK, payload, sizeof(payload));
}
void CompanionService::sendNack(uint32_t messageId, const char* reason) {
  uint8_t payload[96];
  writeU32(payload, messageId);
  const size_t length = std::min<size_t>(std::strlen(reason), sizeof(payload) - 4);
  std::memcpy(payload + 4, reason, length);
  notify(MessageType::NACK, payload, length + 4);
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
  notify(type, payload, cursor);
}

void CompanionService::scanLibrary() {
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
}

void CompanionService::scanDirectory(const char* path, uint8_t depth) {
  if (depth > 8 || libraryCount_ >= MAX_LIBRARY_ITEMS) return;
  HalFile directory = Storage.open(path);
  if (!directory || !directory.isDirectory()) return;
  while (libraryCount_ < MAX_LIBRARY_ITEMS) {
    HalFile entry = directory.openNextFile();
    if (!entry) break;
    char name[128] = {};
    entry.getName(name, sizeof(name));
    if (name[0] == '.' || std::strcmp(name, "System Volume Information") == 0) continue;
    char fullPath[181];
    const int written = std::snprintf(fullPath, sizeof(fullPath), std::strcmp(path, "/") == 0 ? "/%s" : "%s/%s", path,
                                      name);
    if (written <= 0 || static_cast<size_t>(written) >= sizeof(fullPath)) continue;
    if (entry.isDirectory()) {
      entry.close();
      scanDirectory(fullPath, depth + 1);
    } else if (hasBookExtension(fullPath)) {
      auto& item = library_[libraryCount_++];
      std::strncpy(item.path, fullPath, sizeof(item.path) - 1);
      item.size = entry.fileSize64();
    }
  }
}

void CompanionService::sendNextLibraryItem() {
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
  notify(MessageType::LIBRARY_PAGE, payload, cursor);
  if (last)
    librarySendPending_ = false;
  else
    ++librarySendIndex_;
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
  scanLibrary();
  return cursor == envelope.payloadLength;
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

void CompanionService::notify(MessageType type, const uint8_t* payload, size_t payloadLength) {
  if (!events_ || !connected()) return;
  uint8_t packet[MAX_PACKET_BYTES];
  const size_t length = encodeEnvelope(type, outgoingMessageId_++, payload, payloadLength, packet, sizeof(packet));
  if (length) events_->notify(packet, length);
}
}  // namespace companion

#endif
