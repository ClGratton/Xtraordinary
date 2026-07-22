#include "SessionEngine.h"

#ifdef ENABLE_X3_COMPANION

#include <algorithm>
#include <cstring>

namespace companion {
bool SessionEngine::start(uint32_t durationSeconds, const char* title, size_t titleLength) {
  if (durationSeconds == 0 || durationSeconds > 24 * 60 * 60 || !title || titleLength > 160) return false;
  totalSeconds_ = durationSeconds;
  remainingAtPause_ = durationSeconds;
  deadlineMs_ = millis() + durationSeconds * 1000u;
  std::memcpy(title_, title, titleLength);
  title_[titleLength] = '\0';
  phase_ = SessionPhase::RUNNING;
  return true;
}
void SessionEngine::pause() {
  if (phase_ != SessionPhase::RUNNING) return;
  remainingAtPause_ = remainingSeconds();
  phase_ = SessionPhase::PAUSED;
}
void SessionEngine::resume() {
  if (phase_ != SessionPhase::PAUSED) return;
  deadlineMs_ = millis() + remainingAtPause_ * 1000u;
  phase_ = SessionPhase::RUNNING;
}
void SessionEngine::stop() {
  phase_ = SessionPhase::STOPPED;
  remainingAtPause_ = 0;
}
void SessionEngine::update() {
  if (phase_ == SessionPhase::RUNNING && remainingSeconds() == 0) phase_ = SessionPhase::COMPLETE;
}
uint32_t SessionEngine::remainingSeconds() const {
  if (phase_ == SessionPhase::PAUSED) return remainingAtPause_;
  if (phase_ != SessionPhase::RUNNING) return phase_ == SessionPhase::COMPLETE ? 0 : remainingAtPause_;
  const int32_t delta = static_cast<int32_t>(deadlineMs_ - millis());
  return delta <= 0 ? 0 : static_cast<uint32_t>((delta + 999) / 1000);
}
}  // namespace companion
#endif
