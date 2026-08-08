#pragma once

#include <cstddef>
#include <cstdint>

namespace companion {

constexpr uint8_t PROTOCOL_VERSION = 1;
constexpr size_t HEADER_BYTES = 20;
constexpr size_t MAX_PACKET_BYTES = 512;
constexpr size_t MAX_PAYLOAD_BYTES = MAX_PACKET_BYTES - HEADER_BYTES;

enum class MessageType : uint8_t {
  HELLO = 0x01,
  CAPABILITIES = 0x02,
  GET_STATUS = 0x03,
  ACK_STATUS = 0x04,
  SET_CLOCK = 0x05,
  START_SESSION = 0x20,
  PAUSE_SESSION = 0x21,
  RESUME_SESSION = 0x22,
  STOP_SESSION = 0x23,
  SHOW_TICKET = 0x31,
  CLEAR_TICKET = 0x32,
  SET_RADIO_POLICY = 0x33,
  SET_READER_POLICY = 0x34,
  GET_LIBRARY = 0x40,
  LIBRARY_PAGE = 0x41,
  DELETE_LIBRARY_ENTRIES = 0x42,
  GET_READING_STATS = 0x43,
  READING_STATS_CHUNK = 0x44,
  ACK_READING_STATS = 0x45,
  BEGIN_BOOK_UPLOAD = 0x46,
  BOOK_UPLOAD_CHUNK = 0x47,
  COMMIT_BOOK_UPLOAD = 0x48,
  ABORT_BOOK_UPLOAD = 0x49,
  BEGIN_FIRMWARE = 0x50,
  FIRMWARE_CHUNK = 0x51,
  COMMIT_FIRMWARE = 0x52,
  APPLY_FIRMWARE = 0x53,
  ACK = 0x80,
  NACK = 0x81,
  STATUS_CHANGED = 0x83,
  LIBRARY_CHANGED = 0x84,
  FIRMWARE_PROGRESS = 0x85,
  ERROR = 0xff,
};

enum class DeviceActivity : uint8_t {
  AWAKE = 0x01,
  READING = 0x02,
};

enum class DeviceSyncMode : uint8_t {
  FAST = 0x01,
  SLOW = 0x02,
};

struct EnvelopeView {
  MessageType type;
  uint16_t flags;
  uint32_t messageId;
  const uint8_t* payload;
  uint32_t payloadLength;
};

uint32_t crc32(const uint8_t* data, size_t length);
bool decodeEnvelope(const uint8_t* bytes, size_t length, EnvelopeView& output);
size_t encodeEnvelope(MessageType type, uint32_t messageId, const uint8_t* payload, size_t payloadLength, uint8_t* output,
                      size_t outputCapacity);

uint16_t readU16(const uint8_t* bytes);
uint32_t readU32(const uint8_t* bytes);
uint64_t readU64(const uint8_t* bytes);
void writeU16(uint8_t* bytes, uint16_t value);
void writeU32(uint8_t* bytes, uint32_t value);
void writeU64(uint8_t* bytes, uint64_t value);

}  // namespace companion
