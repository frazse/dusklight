#pragma once

#if defined(TARGET_ANDROID) || defined(__ANDROID__) || defined(ANDROID)
#include "dusk/settings.h"

namespace dusk::android {
void hud_update();
bool hud_is_second_screen_active();

inline bool dusk_shouldWheelShowOnMainScreen() {
    if (!hud_is_second_screen_active()) return true;
    return !dusk::getSettings().game.itemWheelOnSecondScreen;
}
}  // namespace dusk::android
#else
namespace dusk::android {
inline void hud_update() {}
inline bool hud_is_second_screen_active() { return false; }
inline bool dusk_shouldWheelShowOnMainScreen() { return true; }
}  // namespace dusk::android
#endif
