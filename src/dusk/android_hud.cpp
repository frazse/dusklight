#include "dusk/android_hud.hpp"

#if defined(TARGET_ANDROID) || defined(__ANDROID__) || defined(ANDROID)
#include "d/d_com_inf_game.h"
#include "d/d_map_path_dmap.h"
#include "d/d_meter2_info.h"
#include "d/d_meter2.h"
#include "d/d_tresure.h"
#include "d/actor/d_a_alink.h"
#include "dusk/map_loader_definitions.h"
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

bool should_draw_icon(int type, const dTres_c::data_s* data, int stayNo) {
    if (data == nullptr) return false;

    StageType stage_type = (StageType)dStage_stagInfo_GetSTType(dComIfGp_getStage()->getStagInfo());
    bool is_dungeon = (stage_type == ST_DUNGEON);
    bool has_compass = dMapInfo_n::chkGetCompass();
    bool has_map = dMapInfo_n::chkGetMap();
    bool visited = dComIfGs_isVisitedRoom(data->mRoomNo) || data->mRoomNo == stayNo;

    bool compass_reveal = (is_dungeon && has_compass);

    switch (type) {
    case 0: // Regular Chests
    case 2: // Small Keys
        if (is_dungeon) {
            if (!compass_reveal) return false;
            if (data->mNo != 255 && dComIfGs_isTbox(data->mNo)) return false;
            return true;
        }
        if (data->mNo != 255 && dComIfGs_isTbox(data->mNo)) return false;
        return visited;

    case 1:
    case 8: // Entrances
        if (is_dungeon) {
            if (has_map) return true;
            return visited;
        }
        return visited;

    case 3: // Boss
        if (is_dungeon) {
            if (!compass_reveal) return false;
            if (dComIfGs_isStageBossEnemy()) return false;
            return true;
        }
        return false;

    case 4: // Monkey / Poe Soul / Light Drop
        if (is_dungeon) {
            // Monkeys and Poes show after Compass and disappear when rescued/collected
            if (!compass_reveal) return false;
            if (data->mNo != 255 && dComIfGs_isTbox(data->mNo)) return false;
            if (data->mSwBit != 255 && dComIfGs_isSwitch(data->mSwBit, data->mRoomNo)) return false;
            return true;
        }
        // Field Light Drops
        if (dComIfGp_isLightDropMapVisible()) {
            if (data->mNo != 255 && !dComIfGs_isTbox(data->mNo)) return true;
        }
        return false;

    case 5: // Objective / Statue / Sol
        if (is_dungeon) {
            if (!compass_reveal) return false;
            if (data->mNo != 255 && dComIfGs_isTbox(data->mNo)) return false;
            if (data->mSwBit != 255 && !dComIfGs_isSwitch(data->mSwBit, data->mRoomNo)) return false;
            return true;
        }
        if (data->mSwBit == 255 || dComIfGs_isSwitch(data->mSwBit, data->mRoomNo)) return true;
        return false;

    case 10: // Field Chests
        // Field map does not show chests in the original game
        return false;

    case 13:
    case 14: // Ooccoo / Ooccoo Jr
        if (!is_dungeon) {
            // Only show if available (not yet used/returned)
            if (data->mSwBit == 255 || !dComIfGs_isSwitch(data->mSwBit, data->mRoomNo)) return visited;
        }
        return false;

    case 15: // Ancient Statue
    case 16: // Poe Soul (Arbiter's Grounds)
        if (is_dungeon) {
            if (!compass_reveal) return false;
            if (dComIfGs_isStageBossEnemy()) return false;
            if (data->mNo != 255 && dComIfGs_isTbox(data->mNo)) return false;
            if (data->mSwBit != 255 && dComIfGs_isSwitch(data->mSwBit, data->mRoomNo)) return false;
            return true;
        }
        return false;

    default:
        if (is_dungeon) return compass_reveal;
        return visited;
    }
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

    if (s_onGameStateUpdate == nullptr) {
        jclass cls = env->GetObjectClass(activity);
        if (cls == nullptr || clear_pending_exception(env)) {
            env->DeleteLocalRef(activity);
            return;
        }
        s_onGameStateUpdate = env->GetMethodID(
            cls, "onGameStateUpdate", "(IIIIIIIIIIIIIIIFFILjava/lang/String;I[F[FFFFFFLjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIIII[F)V");
        env->DeleteLocalRef(cls);
        if (s_onGameStateUpdate == nullptr || clear_pending_exception(env)) {
            env->DeleteLocalRef(activity);
            return;
        }
    }

    s_secondScreenActive.store(true, std::memory_order_relaxed);

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
    const int lightDrops = static_cast<int>(dComIfGs_getLightDropNum(dComIfGp_getStartStageDarkArea()));
    const int needLightDrops = static_cast<int>(dComIfGp_getNeedLightDropNum());

    int showLightDrops = 0;
    dMeter2_c* meter = dMeter2Info_getMeterClass();
    if (meter != nullptr && meter->isShowLightDrop()) {
        showLightDrops = 1;
    }

    const char* stageNameStr = dComIfGp_getStartStageName();
    int stayNo = dComIfGp_roomControl_getStayNo();

    std::string friendlyName = (stageNameStr && stayNo != -1) ? stageNameStr : "Loading...";
    if (stageNameStr && stayNo != -1) {
        for (const auto& region : gameRegions) {
            for (const auto& map : region.maps) {
                if (strcmp(map.mapFile, stageNameStr) == 0) {
                    if (map.mapRooms.empty()) {
                        friendlyName = map.mapName;
                    } else {
                        for (const auto& room : map.mapRooms) {
                            if (room.roomNo == stayNo) {
                                friendlyName = map.mapName;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    Vec playerPos = dMapInfo_n::getMapPlayerPos();
    float mapX = playerPos.x;
    float mapY = playerPos.z;
    float mapAngle = (float)dMapInfo_n::getMapPlayerAngleY() * (180.0f / 32768.0f);

    bool has_map = dMapInfo_n::chkGetMap();
    StageType stage_type = (StageType)dStage_stagInfo_GetSTType(dComIfGp_getStage()->getStagInfo());
    bool is_dungeon = (stage_type == ST_DUNGEON);

    s8 stayFloor = 0;
    if (dMapInfo_c::getNowStayFloorNoDecisionFlg()) {
        stayFloor = dMapInfo_c::getNowStayFloorNo();
    } else {
        stayFloor = dMapInfo_c::calcFloorNo(playerPos.y, true, stayNo);
    }

    std::vector<float> mapLines;
    std::vector<float> mapIcons;
    std::vector<float> mapDoors;

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

    update_bounds(mapX, mapY);

    if (dMpath_c::mLayerList != nullptr) {
        for (int l = 0; l < 2; l++) {
            for (int r = 0; r < 64; r++) {
                bool visited = dComIfGs_isVisitedRoom(r);
                bool is_current = (r == stayNo);

                if (is_dungeon) {
                    if (!has_map && !visited && !is_current) continue;
                } else {
                    if (!visited && !is_current) continue;
                }

                dDrawPath_c::room_class* room = dMpath_c::getRoomPointer(l, r);
                if (room != nullptr && room->mpFloatData != nullptr) {
                    BE<f32>* floatData = room->mpFloatData;
                    for (int f = 0; f < room->mFloorNum; f++) {
                        dDrawPath_c::floor_class* floor = &room->mpFloor[f];
                        if (floor->mFloorNo != stayFloor) continue;

                        dDrawPath_c::group_class* group = floor->mpGroup;
                        if (group == nullptr) continue;
                        for (int g = 0; g < floor->mGroupNum; g++) {
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

    auto collect_doors = [&](dStage_KeepDoorInfo* door_info, bool correct) {
        if (door_info == nullptr) return;
        for (int i = 0; i < door_info->mNum; i++) {
            const stage_tgsc_data_class* door = &door_info->mDrTgData[i];
            int prm0 = (door->base.parameters >> 0xD) & 0x3F;
            int prm1 = (door->base.parameters >> 0x13) & 0x3F;

            if (is_dungeon && !has_map) {
                if (!dComIfGs_isVisitedRoom(prm0) && !dComIfGs_isVisitedRoom(prm1) &&
                    prm0 != stayNo && prm1 != stayNo) continue;
            } else if (!is_dungeon) {
                if (!dComIfGs_isVisitedRoom(prm0) && !dComIfGs_isVisitedRoom(prm1) &&
                    prm0 != stayNo && prm1 != stayNo) continue;
            }

            s8 doorFloor = dMapInfo_c::calcFloorNo(door->base.position.y, true, prm0);
            if (doorFloor != stayFloor) continue;

            BE(Vec) pos = (Vec)(cXyz)door->base.position;
            if (correct) dMapInfo_n::correctionOriginPos(prm0, &pos);

            mapDoors.push_back(pos.x);
            mapDoors.push_back(pos.z);
            mapDoors.push_back((float)door->base.angle.y * (180.0f / 32768.0f));
            mapDoors.push_back(0.0f);
            update_bounds(pos.x, pos.z);
        }
    };

    collect_doors(dStage_GetKeepDoorInfo(), true);
    collect_doors(dStage_GetRoomKeepDoorInfo(), false);

    for (int type = 0; type < dTres_c::TYPE_GROUP_ENUM_NUMBER; type++) {
        for (dTres_c::typeGroupData_c* data = dTres_c::getFirstData(type);
             data != nullptr;
             data = dTres_c::getNextData(data)) {
            if (data->mRoomNo != -1 && !dComIfGs_isVisitedRoom(data->mRoomNo) && data->mRoomNo != stayNo) continue;
            if (!should_draw_icon(type, data->getConstDataPointer(), stayNo)) continue;

            BE(Vec) iconPos = data->mPos;
            if (data->mRoomNo != -1) dMapInfo_n::correctionOriginPos(data->mRoomNo, &iconPos);

            mapIcons.push_back((float)type);
            mapIcons.push_back(iconPos.x);
            mapIcons.push_back(iconPos.z);
            mapIcons.push_back((float)data->mStatus);
            update_bounds(iconPos.x, iconPos.z);
        }
    }

    if (minX > maxX) {
        minX = -10000.0f; maxX = 10000.0f;
        minZ = -10000.0f; maxZ = 10000.0f;
    }

    std::string buttonAText = "";
    std::string buttonBText = "";
    std::string buttonZText = "";
    std::string buttonLText = "";
    std::string buttonXText = "";
    std::string buttonYText = "";

    if (dMeter2Info_isUseButton(METER2_USEBUTTON_A)) buttonAText = get_action_text(dComIfGp_getDoStatus());
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_B)) buttonBText = get_action_text(dComIfGp_getAStatus());

    bool midnaCalling = false;
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_Z)) {
        u8 zStatus = dComIfGp_getZStatus();
        if (dComIfGp_isZSetFlag(2) || dComIfGp_isZSetFlag(4)) midnaCalling = true;
        if (zStatus == 0x2F || zStatus == 0x08 || zStatus == 0) {
            buttonZText = "Midna";
        } else {
            buttonZText = get_action_text(zStatus);
            if (buttonZText.empty()) buttonZText = "Midna";
        }
    }

    dAttention_c* attn = dComIfGp_getAttention();
    if (attn != nullptr && attn->GetLockonCount() > 0) buttonLText = "Target";

    if (dMeter2Info_isUseButton(METER2_USEBUTTON_X)) buttonXText = get_action_text(dComIfGp_getXStatus());
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_Y)) buttonYText = get_action_text(dComIfGp_getYStatus());

    int dPadStatus = static_cast<int>(dComIfGp_get3DStatus());
    std::string dPadPromptText = get_action_text(dPadStatus);
    if (dPadPromptText.empty() && dPadStatus == 0x6A) dPadPromptText = "Map";
    int dPadDirection = static_cast<int>(dComIfGp_get3DDirection());

    std::string dPadUpText = (dPadDirection & 8) ? dPadPromptText : "";
    std::string dPadDownText = (dPadDirection & 2) ? dPadPromptText : "";
    std::string dPadLeftText = (dPadDirection & 1) ? dPadPromptText : "";
    std::string dPadRightText = (dPadDirection & 4) ? dPadPromptText : "";

    int itemXId = static_cast<int>(dComIfGp_getSelectItem(0));
    int itemYId = static_cast<int>(dComIfGp_getSelectItem(1));
    int itemDDownId = static_cast<int>(dComIfGp_getSelectItem(2));
    int itemDLeftId = itemXId;
    int itemDRightId = itemYId;
    int itemXCount = static_cast<int>(dComIfGp_getSelectItemNum(0));
    int itemYCount = static_cast<int>(dComIfGp_getSelectItemNum(1));
    int itemDDownCount = static_cast<int>(dComIfGp_getSelectItemNum(2));
    int itemDLeftCount = itemXCount;
    int itemDRightCount = itemYCount;

    jstring jStageName = env->NewStringUTF(friendlyName.c_str());
    jfloatArray jLines = env->NewFloatArray(mapLines.size());
    if (jLines != nullptr && !mapLines.empty()) env->SetFloatArrayRegion(jLines, 0, mapLines.size(), mapLines.data());
    jfloatArray jIcons = env->NewFloatArray(mapIcons.size());
    if (jIcons != nullptr && !mapIcons.empty()) env->SetFloatArrayRegion(jIcons, 0, mapIcons.size(), mapIcons.data());
    jfloatArray jDoors = env->NewFloatArray(mapDoors.size());
    if (jDoors != nullptr && !mapDoors.empty()) env->SetFloatArrayRegion(jDoors, 0, mapDoors.size(), mapDoors.data());

    jstring jButtonA = env->NewStringUTF(buttonAText.c_str());
    jstring jButtonB = env->NewStringUTF(buttonBText.c_str());
    jstring jButtonZ = env->NewStringUTF(buttonZText.c_str());
    jstring jButtonL = env->NewStringUTF(buttonLText.c_str());
    jstring jButtonX = env->NewStringUTF(buttonXText.c_str());
    jstring jButtonY = env->NewStringUTF(buttonYText.c_str());
    jstring jDPadUpText = env->NewStringUTF(dPadUpText.c_str());
    jstring jDPadDownText = env->NewStringUTF(dPadDownText.c_str());
    jstring jDPadLeftText = env->NewStringUTF(dPadLeftText.c_str());
    jstring jDPadRightText = env->NewStringUTF(dPadRightText.c_str());

    env->CallVoidMethod(activity, s_onGameStateUpdate,
        health, maxHealth, magic, maxMagic, oil, maxOil, oxygen, maxOxygen,
        rupees, keys, arrows, bombs, lightDrops, needLightDrops, showLightDrops,
        mapX, mapY, transform, jStageName, stayNo, jLines, jIcons, mapAngle,
        minX, minZ, maxX, maxZ,
        jButtonA, jButtonB, jButtonZ, midnaCalling, jButtonL, jButtonX, jButtonY,
        itemXId, itemYId, itemXCount, itemYCount,
        jDPadUpText, jDPadDownText, jDPadLeftText, jDPadRightText,
        itemDDownId, itemDDownCount, itemDLeftId, itemDLeftCount, itemDRightId, itemDRightCount,
        jDoors);

    if (jStageName) env->DeleteLocalRef(jStageName);
    if (jLines) env->DeleteLocalRef(jLines);
    if (jIcons) env->DeleteLocalRef(jIcons);
    if (jDoors) env->DeleteLocalRef(jDoors);
    if (jButtonA) env->DeleteLocalRef(jButtonA);
    if (jButtonB) env->DeleteLocalRef(jButtonB);
    if (jButtonZ) env->DeleteLocalRef(jButtonZ);
    if (jButtonL) env->DeleteLocalRef(jButtonL);
    if (jButtonX) env->DeleteLocalRef(jButtonX);
    if (jButtonY) env->DeleteLocalRef(jButtonY);
    if (jDPadUpText) env->DeleteLocalRef(jDPadUpText);
    if (jDPadDownText) env->DeleteLocalRef(jDPadDownText);
    if (jDPadLeftText) env->DeleteLocalRef(jDPadLeftText);
    if (jDPadRightText) env->DeleteLocalRef(jDPadRightText);

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
