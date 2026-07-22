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
  START_SESSION = 0x20,
  PAUSE_SESSION = 0x21,
  RESUME_SESSION = 0x22,
  STOP_SESSION = 0x23,
  GET_LIBRARY = 0x40,
  LIBRARY_PAGE = 0x41,
  DELETE_LIBRARY_ENTRIES = 0x42,
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
