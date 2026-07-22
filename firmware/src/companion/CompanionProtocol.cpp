#include "CompanionProtocol.h"

#include <cstring>

namespace companion {
namespace {
constexpr uint8_t MAGIC[] = {'X', '3', 'C', 'P'};
}

uint16_t readU16(const uint8_t* bytes) { return static_cast<uint16_t>(bytes[0] | (bytes[1] << 8)); }
uint32_t readU32(const uint8_t* bytes) {
  return static_cast<uint32_t>(bytes[0]) | (static_cast<uint32_t>(bytes[1]) << 8) |
         (static_cast<uint32_t>(bytes[2]) << 16) | (static_cast<uint32_t>(bytes[3]) << 24);
}
uint64_t readU64(const uint8_t* bytes) {
  return static_cast<uint64_t>(readU32(bytes)) | (static_cast<uint64_t>(readU32(bytes + 4)) << 32);
}
void writeU16(uint8_t* bytes, uint16_t value) {
  bytes[0] = static_cast<uint8_t>(value);
  bytes[1] = static_cast<uint8_t>(value >> 8);
}
void writeU32(uint8_t* bytes, uint32_t value) {
  for (uint8_t i = 0; i < 4; ++i) bytes[i] = static_cast<uint8_t>(value >> (i * 8));
}
void writeU64(uint8_t* bytes, uint64_t value) {
  writeU32(bytes, static_cast<uint32_t>(value));
  writeU32(bytes + 4, static_cast<uint32_t>(value >> 32));
}

uint32_t crc32(const uint8_t* data, size_t length) {
  uint32_t crc = 0xffffffffu;
  for (size_t i = 0; i < length; ++i) {
    crc ^= data[i];
    for (uint8_t bit = 0; bit < 8; ++bit) crc = (crc >> 1) ^ (0xedb88320u & (0u - (crc & 1u)));
  }
  return ~crc;
}

bool decodeEnvelope(const uint8_t* bytes, size_t length, EnvelopeView& output) {
  if (!bytes || length < HEADER_BYTES || std::memcmp(bytes, MAGIC, sizeof(MAGIC)) != 0 ||
      bytes[4] != PROTOCOL_VERSION)
    return false;
  const uint32_t payloadLength = readU32(bytes + 12);
  if (payloadLength > MAX_PAYLOAD_BYTES || length != HEADER_BYTES + payloadLength) return false;
  if (crc32(bytes + HEADER_BYTES, payloadLength) != readU32(bytes + 16)) return false;
  output.type = static_cast<MessageType>(bytes[5]);
  output.flags = readU16(bytes + 6);
  output.messageId = readU32(bytes + 8);
  output.payload = bytes + HEADER_BYTES;
  output.payloadLength = payloadLength;
  return true;
}

size_t encodeEnvelope(MessageType type, uint32_t messageId, const uint8_t* payload, size_t payloadLength, uint8_t* output,
                      size_t outputCapacity) {
  if (!output || payloadLength > MAX_PAYLOAD_BYTES || outputCapacity < HEADER_BYTES + payloadLength) return 0;
  std::memcpy(output, MAGIC, sizeof(MAGIC));
  output[4] = PROTOCOL_VERSION;
  output[5] = static_cast<uint8_t>(type);
  writeU16(output + 6, 0);
  writeU32(output + 8, messageId);
  writeU32(output + 12, static_cast<uint32_t>(payloadLength));
  writeU32(output + 16, crc32(payload, payloadLength));
  if (payloadLength) std::memcpy(output + HEADER_BYTES, payload, payloadLength);
  return HEADER_BYTES + payloadLength;
}
}  // namespace companion
