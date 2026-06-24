#include "dusk/android_hud.hpp"

#if defined(TARGET_ANDROID) || defined(__ANDROID__) || defined(ANDROID)
#include "d/d_com_inf_game.h"
#include "d/d_map_path_dmap.h"
#include "d/d_meter2_info.h"
#include "d/d_meter2.h"
#include "d/actor/d_a_alink.h"
#include "SSystem/SComponent/c_xyz.h"

#include <SDL3/SDL_system.h>
#include <jni.h>
#include <atomic>
#include <vector>
#include <limits>
#include <algorithm>
#include <string>

namespace dusk::android {
namespace {

bool clear_pending_exception(JNIEnv* env) {
    if (env == nullptr || !env->ExceptionCheck()) {
        return false;
    }
    env->ExceptionClear();
    return true;
}

// Cached — looked up once on first call, reused every frame
jmethodID s_onGameStateUpdate = nullptr;

// Set to true once the second screen Presentation is confirmed active
std::atomic<bool> s_secondScreenActive{false};

// Throttle: only push HUD update every N frames (~10 Hz at 60 fps)
constexpr int kHudUpdateInterval = 6;
int s_frameCounter = 0;

const u32 s_action_num[] = {
    0,    1001, 1002, 1003, 1004, 1005, 1006, 1007, 1387, 1009, 1010, 1011, 1012, 1013, 1014,
    1015, 1033, 1016, 1017, 1018, 1019, 1020, 1045, 1022, 1026, 1027, 1028, 1029, 1030, 1024,
    1031, 1025, 1023, 1045, 1036, 1032, 93,   0,    0,    0,    1035, 1034, 1037, 1038, 1039,
    1040, 1041, 998,  1042, 1043, 1044, 1046, 1047, 1048, 1049, 1050, 1051, 1052, 1053, 1054,
    1055, 1056, 1057, 1058, 1059, 1070, 1060, 1061, 1062, 1063, 1064, 1067, 1065, 1066, 1211,
    1069, 1021, 1072, 1073, 0,    1074, 1075, 1076, 1077, 1078, 1079, 1080, 1081, 1082, 1083,
    1084, 1085, 1086, 1087, 1088, 1089, 1090, 1092, 1093, 1094, 1095, 1096, 1904, 1097, 1098,
    1099, 1100, 1150, 1149, 1148, 1377, 1147, 1145, 1146, 1161, 1162, 1163, 1164, 1165, 1166,
    1144, 982,  983,  1143, 1160, 1319, 1314, 1399, 1008,
};

std::string get_action_text(u8 action_id) {
    if (action_id == 0 || action_id >= (sizeof(s_action_num) / sizeof(s_action_num[0]))) {
        return "";
    }
    u32 msg_id = s_action_num[action_id];
    if (msg_id == 0) return "";

    char text_buf[64];
    text_buf[0] = '\0';
    dMeter2Info_getStringKanji(msg_id, text_buf, nullptr);
    return std::string(text_buf);
}

} // namespace

bool hud_is_second_screen_active() {
    return s_secondScreenActive.load(std::memory_order_relaxed);
}

void hud_update() {
    if (++s_frameCounter < kHudUpdateInterval) {
        return;
    }
    s_frameCounter = 0;

    auto* env = static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
    if (env == nullptr) return;

    jobject activity = static_cast<jobject>(SDL_GetAndroidActivity());
    if (activity == nullptr || clear_pending_exception(env)) {
        if (activity != nullptr) env->DeleteLocalRef(activity);
        return;
    }

    // Look up and cache the Java method ID on first call
    if (s_onGameStateUpdate == nullptr) {
        jclass cls = env->GetObjectClass(activity);
        if (cls == nullptr || clear_pending_exception(env)) {
            env->DeleteLocalRef(activity);
            return;
        }
        // Updated signature for X/Y buttons and items
        s_onGameStateUpdate = env->GetMethodID(
            cls, "onGameStateUpdate", "(IIIIIIIIIIIIFFILjava/lang/String;I[F[FFFFFFLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIII)V");
        env->DeleteLocalRef(cls);
        if (s_onGameStateUpdate == nullptr || clear_pending_exception(env)) {
            env->DeleteLocalRef(activity);
            return;
        }
    }

    // Second screen confirmed reachable — mark active for d_meter2.cpp
    s_secondScreenActive.store(true, std::memory_order_relaxed);

    // Read game state
    const int health    = static_cast<int>(dComIfGs_getLife());
    const int maxHealth = static_cast<int>(dComIfGs_getMaxLife());
    const int magic     = static_cast<int>(dComIfGs_getMagic());
    const int maxMagic  = static_cast<int>(dComIfGs_getMaxMagic());
    const int oil       = static_cast<int>(dComIfGs_getOil());
    const int maxOil    = static_cast<int>(dComIfGs_getMaxOil());
    const int oxygen    = static_cast<int>(dComIfGp_getNowOxygen());
    const int maxOxygen = static_cast<int>(dComIfGp_getMaxOxygen());
    const int rupees    = static_cast<int>(dComIfGs_getRupee());
    const int arrows    = static_cast<int>(dComIfGs_getArrowNum());
    const int bombs     = static_cast<int>(dComIfGs_getBombNum(0));
    const int keys      = static_cast<int>(dComIfGs_getKeyNum());
    const int transform = static_cast<int>(dComIfGs_getTransformStatus());

    // Map Info
    const char* stageNameStr = dComIfGp_getStartStageName();
    int stayNo = dComIfGp_roomControl_getStayNo();

    std::vector<float> mapLines;
    std::vector<float> mapIcons; // [type, x, y, status]

    float minX = std::numeric_limits<float>::max();
    float minZ = std::numeric_limits<float>::max();
    float maxX = std::numeric_limits<float>::lowest();
    float maxZ = std::numeric_limits<float>::lowest();

    auto update_bounds = [&](float x, float z) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (z < minZ) minZ = z;
        if (z > maxZ) maxZ = z;
    };

    if (dMpath_c::mLayerList != nullptr) {
        for (int l = 0; l < 2; l++) {
            for (int r = 0; r < 64; r++) {
                if (r != stayNo && !dComIfGs_isVisitedRoom(r)) {
                    continue;
                }

                dDrawPath_c::room_class* room = dMpath_c::getRoomPointer(l, r);
                if (room != nullptr && room->mpFloatData != nullptr) {
                    BE<f32>* floatData = room->mpFloatData;
                    dDrawPath_c::floor_class* floor = room->mpFloor;
                    for (int f = 0; f < room->mFloorNum; f++) {
                        dDrawPath_c::group_class* group = floor[f].mpGroup;
                        if (group == nullptr) continue;
                        for (int g = 0; g < floor[f].mGroupNum; g++) {
                            dDrawPath_c::line_class* line = group[g].mpLine;
                            if (line == nullptr) continue;
                            for (int ln = 0; ln < group[g].mLineNum; ln++) {
                                if (line[ln].mDataNum >= 2 && line[ln].mpData != nullptr) {
                                    BE(u16)* indices = line[ln].mpData;
                                    for (int i = 0; i < line[ln].mDataNum; i++) {
                                        u16 idx = indices[i];
                                        BE<Vec> pos;
                                        pos.x = floatData[idx * 2];
                                        pos.y = 0.0f;
                                        pos.z = floatData[idx * 2 + 1];
                                        dMapInfo_n::correctionOriginPos(r, &pos);
                                        mapLines.push_back((float)pos.x);
                                        mapLines.push_back((float)pos.z);
                                        update_bounds(pos.x, pos.z);
                                    }
                                    mapLines.push_back(std::numeric_limits<float>::quiet_NaN());
                                    mapLines.push_back(std::numeric_limits<float>::quiet_NaN());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    for (int type = 0; type < dTres_c::TYPE_GROUP_ENUM_NUMBER; type++) {
        for (dTres_c::typeGroupData_c* data = dTres_c::getFirstData(type);
             data != nullptr;
             data = dTres_c::getNextData(data)) {
            if (data->mRoomNo != -1 && !dComIfGs_isVisitedRoom(data->mRoomNo) && data->mRoomNo != stayNo) {
                continue;
            }
            BE(Vec) iconPos = data->mPos;
            if (data->mRoomNo != -1) {
                dMapInfo_n::correctionOriginPos(data->mRoomNo, &iconPos);
            }
            mapIcons.push_back((float)type);
            mapIcons.push_back(iconPos.x);
            mapIcons.push_back(iconPos.z);
            mapIcons.push_back((float)data->mStatus);
            update_bounds(iconPos.x, iconPos.z);
        }
    }

    Vec playerPos = dMapInfo_n::getMapPlayerPos();
    float mapX = playerPos.x;
    float mapY = playerPos.z;
    float mapAngle = (float)dMapInfo_n::getMapPlayerAngleY() * (180.0f / 32768.0f);
    update_bounds(mapX, mapY);

    if (minX > maxX) {
        minX = -10000.0f; maxX = 10000.0f;
        minZ = -10000.0f; maxZ = 10000.0f;
    }

    // Button Labels
    std::string buttonAText = "";
    std::string buttonBText = "";
    std::string buttonZText = "";
    std::string buttonXText = "";
    std::string buttonYText = "";

    if (dMeter2Info_isUseButton(METER2_USEBUTTON_A)) {
        buttonAText = get_action_text(dComIfGp_getDoStatus());
    }
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_B)) {
        buttonBText = get_action_text(dComIfGp_getAStatus());
    }
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_Z)) {
        buttonZText = get_action_text(dComIfGp_getZStatus());
    }
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_X)) {
        buttonXText = get_action_text(dComIfGp_getXStatus());
    }
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_Y)) {
        buttonYText = get_action_text(dComIfGp_getYStatus());
    }

    // Equipped Items
    int itemXId = static_cast<int>(dComIfGp_getSelectItem(0));
    int itemYId = static_cast<int>(dComIfGp_getSelectItem(1));
    int itemXCount = static_cast<int>(dComIfGp_getSelectItemNum(0));
    int itemYCount = static_cast<int>(dComIfGp_getSelectItemNum(1));

    jstring jStageName = env->NewStringUTF(stageNameStr ? stayNo != -1 ? stageNameStr : "Title" : "Loading...");
    jfloatArray jLines = env->NewFloatArray(mapLines.size());
    if (jLines != nullptr && !mapLines.empty()) {
        env->SetFloatArrayRegion(jLines, 0, mapLines.size(), mapLines.data());
    }
    jfloatArray jIcons = env->NewFloatArray(mapIcons.size());
    if (jIcons != nullptr && !mapIcons.empty()) {
        env->SetFloatArrayRegion(jIcons, 0, mapIcons.size(), mapIcons.data());
    }
    jstring jButtonA = env->NewStringUTF(buttonAText.c_str());
    jstring jButtonB = env->NewStringUTF(buttonBText.c_str());
    jstring jButtonZ = env->NewStringUTF(buttonZText.c_str());
    jstring jButtonX = env->NewStringUTF(buttonXText.c_str());
    jstring jButtonY = env->NewStringUTF(buttonYText.c_str());

    env->CallVoidMethod(activity, s_onGameStateUpdate,
        health, maxHealth,
        magic, maxMagic,
        oil, maxOil,
        oxygen, maxOxygen,
        rupees, keys, arrows, bombs,
        mapX, mapY,
        transform,
        jStageName, stayNo, jLines, jIcons, mapAngle,
        minX, minZ, maxX, maxZ,
        jButtonA, jButtonB, jButtonZ, jButtonX, jButtonY,
        itemXId, itemYId, itemXCount, itemYCount);

    if (jStageName) env->DeleteLocalRef(jStageName);
    if (jLines) env->DeleteLocalRef(jLines);
    if (jIcons) env->DeleteLocalRef(jIcons);
    if (jButtonA) env->DeleteLocalRef(jButtonA);
    if (jButtonB) env->DeleteLocalRef(jButtonB);
    if (jButtonZ) env->DeleteLocalRef(jButtonZ);
    if (jButtonX) env->DeleteLocalRef(jButtonX);
    if (jButtonY) env->DeleteLocalRef(jButtonY);

    clear_pending_exception(env);
    env->DeleteLocalRef(activity);
}

} // namespace dusk::android

#else

namespace dusk::android {
void hud_update() {}
bool hud_is_second_screen_active() { return false; }
} // namespace dusk::android

#endif
