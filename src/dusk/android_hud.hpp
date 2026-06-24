#pragma once

#if defined(TARGET_ANDROID) || defined(__ANDROID__) || defined(ANDROID)
namespace dusk::android {
void hud_update();
bool hud_is_second_screen_active();
}  // namespace dusk::android
#else
namespace dusk::android {
inline void hud_update() {}
inline bool hud_is_second_screen_active() { return false; }
}  // namespace dusk::android
#endif
