#pragma once

#ifdef ENABLE_X3_COMPANION

#include <Arduino.h>

#include <cstdint>

namespace companion {

enum class SessionPhase : uint8_t { STOPPED, RUNNING, PAUSED, COMPLETE };

class SessionEngine {
  SessionPhase phase_ = SessionPhase::STOPPED;
  uint32_t totalSeconds_ = 0;
  uint32_t remainingAtPause_ = 0;
  uint32_t deadlineMs_ = 0;
  char title_[161] = {};

 public:
  bool start(uint32_t durationSeconds, const char* title, size_t titleLength);
  void pause();
  void resume();
  void stop();
  void update();
  SessionPhase phase() const { return phase_; }
  uint32_t totalSeconds() const { return totalSeconds_; }
  uint32_t remainingSeconds() const;
  const char* title() const { return title_; }
  bool active() const { return phase_ != SessionPhase::STOPPED; }
};

}  // namespace companion
#endif
