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
#include "dusk/endian.h"

#include <SDL3/SDL_system.h>
#include <jni.h>
#include <atomic>
#include <vector>
#include <limits>
#include <algorithm>
#include <string>
#include <cmath>

namespace dusk::android {
namespace {

bool clear_pending_exception(JNIEnv* env) {
    if (env == nullptr || !env->ExceptionCheck()) return false;
    env->ExceptionClear();
    return true;
}

jmethodID s_onGameStateUpdate = nullptr;
std::atomic<bool> s_secondScreenActive{false};
constexpr int kHudUpdateInterval = 6;
int s_frameCounter = 0;

bool should_draw_icon(int type, const dTres_c::data_s* data, int stayNo, s8 sFloor) {
    if (data == nullptr) return false;

    // GLOBAL FILTER: If it's already collected or completed, never draw it.
    if (data->mNo != 0xFF && dComIfGs_isTbox(data->mNo)) return false;
    if (data->mSwBit != 0xFF && dComIfGs_isSwitch(data->mSwBit, data->mRoomNo)) return false;

    auto* stage = dComIfGp_getStage();
    if (stage == nullptr) return false;
    StageType stype = (StageType)dStage_stagInfo_GetSTType(stage->getStagInfo());
    bool is_d = (stype == ST_DUNGEON);

    // FLOOR FILTER: Only draw icons on the current floor
    s8 iconFloor = dMapInfo_c::calcFloorNo(data->mPos.y, true, data->mRoomNo);
    if (iconFloor != sFloor) return false;

    if (is_d) {
        // In dungeons, the Compass reveals all remaining icons on this floor.
        return dComIfGs_isDungeonItemCompass();
    } else {
        // Field logic (Mist areas, etc)
        if (type == 4) return dComIfGp_getStartStageDarkArea() != 0;
        // Never show chests/bosses on field maps per request
        return false;
    }
}
} // namespace

bool hud_is_second_screen_active() { return s_secondScreenActive.load(std::memory_order_relaxed); }

void hud_update() {
    if (++s_frameCounter < kHudUpdateInterval) return;
    s_frameCounter = 0;
    int stayNo = dComIfGp_roomControl_getStayNo();
    if (stayNo < 0) return;
    auto* env = static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
    if (!env) return;
    jobject activity = static_cast<jobject>(SDL_GetAndroidActivity());
    if (!activity || clear_pending_exception(env)) return;
    if (s_onGameStateUpdate == nullptr) {
        jclass cls = env->GetObjectClass(activity);
        s_onGameStateUpdate = env->GetMethodID(cls, "onGameStateUpdate", "([I[FLjava/lang/String;[F[F[F)V");
        env->DeleteLocalRef(cls);
        if (s_onGameStateUpdate == nullptr || clear_pending_exception(env)) return;
    }
    s_secondScreenActive.store(true, std::memory_order_relaxed);

    int iData[60] = {0};
    iData[0] = dComIfGs_getLife(); iData[1] = dComIfGs_getMaxLife();
    iData[2] = dComIfGs_getMagic(); iData[3] = dComIfGs_getMaxMagic();
    iData[4] = dComIfGs_getOil(); iData[5] = dComIfGs_getMaxOil();
    iData[6] = dComIfGp_getNowOxygen(); iData[7] = dComIfGp_getMaxOxygen();
    iData[8] = dComIfGs_getRupee(); iData[9] = dComIfGs_getKeyNum();
    iData[10] = dComIfGs_getArrowNum(); iData[11] = dComIfGs_getBombNum(0);
    iData[12] = dComIfGs_getTransformStatus(); iData[13] = stayNo;
    iData[14] = dComIfGs_getLightDropNum(dComIfGp_getStartStageDarkArea());
    iData[15] = dComIfGp_getNeedLightDropNum();
    dMeter2_c* meter = dMeter2Info_getMeterClass();
    iData[16] = (meter && meter->isShowLightDrop()) ? 1 : 0;
    iData[17] = dComIfGp_getSelectItem(0); iData[18] = dComIfGp_getSelectItem(1);

    // Explicit Ammo Sync for Consumables
    auto get_ammo = [](u8 item, u8 slotNum) {
        if (item == 0x43) return (int)dComIfGs_getArrowNum(); // Bow
        if (item == 0x4B) return (int)dComIfGs_getPachinkoNum(); // Slingshot
        if (item >= 0x70 && item <= 0x72) return (int)dComIfGs_getBombNum(item - 0x70); // Bombs
        return (int)dComIfGp_getSelectItemNum(slotNum);
    };

    iData[19] = get_ammo(iData[17], 0);
    iData[20] = get_ammo(iData[18], 1);
    iData[21] = dComIfGp_getSelectItem(2); iData[22] = dComIfGp_getSelectItemNum(2);
    iData[23] = dComIfGp_getSelectItem(3); iData[24] = dComIfGp_getSelectItemNum(3);
    iData[25] = dComIfGp_getSelectItem(4); iData[26] = dComIfGp_getSelectItemNum(4);
    iData[27] = (dComIfGp_isZSetFlag(2) || dComIfGp_isZSetFlag(4)) ? 1 : 0;
    iData[40] = dComIfGp_getSelectItem(5); iData[41] = dComIfGp_getSelectItemNum(5);
    iData[42] = dMeter2Info_getHorseLifeCount();
    iData[43] = dComIfGp_getOxygenShowFlag() ? 1 : 0;
    iData[45] = dComIfGs_getSelectEquipClothes();

    // Dungeon Progress Tracking (Global Save Hooks for instant update)
    iData[48] = dComIfGs_isDungeonItemMap() ? 1 : 0;
    iData[49] = dComIfGs_isDungeonItemCompass() ? 1 : 0;
    iData[58] = dComIfGs_isDungeonItemBossKey() ? 1 : 0;

    dAttention_c* attn = dComIfGp_getAttention();
    daPy_py_c* player = dComIfGp_getLinkPlayer();
    int stateFlags = 0;
    if (attn && attn->GetLockonCount() > 0) stateFlags |= 1; // Targeting
    if (player) {
        if (player->checkWaterInMove() || player->checkSwimUp() || iData[43]) stateFlags |= 2; // Swimming
        if (player->checkHorseRide()) stateFlags |= 4; // Riding
        if (player->current.pos.y < player->getGroundY() - 20.0f) stateFlags |= 8; // Submerged
        if (iData[45] == 0x40) stateFlags |= 16; // Zora Armor
    }
    iData[31] = stateFlags;

    iData[28] = dComIfGp_getDoStatusForce() ? dComIfGp_getDoStatusForce() : dComIfGp_getDoStatus();
    iData[29] = dComIfGp_getAStatusForce() ? dComIfGp_getAStatusForce() : dComIfGp_getAStatus();
    iData[30] = dComIfGp_getZStatus(); iData[32] = dComIfGp_getRStatus();
    iData[33] = dComIfGp_getXStatus(); iData[34] = dComIfGp_getYStatus();
    iData[39] = dMeter2Info_isUseButton(0xFFFF) ? 0xFFFF : 0;

    // --- Odin 2 / Thor Physical Mapping ---
    // Initialize to -1 (Invalid)
    for (int k = 50; k <= 57; k++) iData[k] = -1;
    // --- Odin 2 / Thor Physical Mapping ---
    // Initialize to -1 (Invalid)
    for (int k = 50; k <= 57; k++) iData[k] = -1;

    u32 btnCount = 0;
    PADButtonMapping* maps = PADGetButtonMappings(0, &btnCount);
    if (maps) {
        for (u32 m = 0; m < btnCount; m++) {
            switch (maps[m].padButton) {
                case PAD_BUTTON_A:  iData[50] = maps[m].nativeButton; break;
                case PAD_BUTTON_B:  iData[51] = maps[m].nativeButton; break;
                case PAD_BUTTON_X:  iData[52] = maps[m].nativeButton; break;
                case PAD_BUTTON_Y:  iData[53] = maps[m].nativeButton; break;
                case PAD_TRIGGER_Z: iData[54] = maps[m].nativeButton; break;
                case PAD_TRIGGER_L: iData[55] = maps[m].nativeButton; break;
                case PAD_TRIGGER_R: iData[56] = maps[m].nativeButton; break;
            }
        }
    }
    // Axis detection for LT/RT
    PADAxisMapping* axisMaps = PADGetAxisMappings(0, &btnCount);
    if (axisMaps) {
        for (u32 m = 0; m < btnCount; m++) {
            if (axisMaps[m].padAxis == PAD_AXIS_TRIGGER_L && iData[55] == -1) iData[55] = 0x1000 + axisMaps[m].nativeAxis.nativeAxis;
            if (axisMaps[m].padAxis == PAD_AXIS_TRIGGER_R && iData[56] == -1) iData[56] = 0x1000 + axisMaps[m].nativeAxis.nativeAxis;
        }
    }

    Vec playerPos = dMapInfo_n::getMapPlayerPos();
    const char* sName = dComIfGp_getStartStageName();
    std::string friendlyName = sName ? sName : "Unknown Area";
    if (sName) {
        for (const auto& reg : gameRegions) {
            for (const auto& m : reg.maps) {
                if (strcmp(m.mapFile, sName) == 0) {
                    if (m.mapRooms.empty()) { friendlyName = m.mapName; goto found_name; }
                    for (const auto& r : m.mapRooms) {
                        if (r.roomNo == stayNo) { friendlyName = m.mapName; goto found_name; }
                    }
                }
            }
        }
    }
    found_name:;

    auto* stage = dComIfGp_getStage();
    StageType stype = (StageType)dStage_stagInfo_GetSTType(stage->getStagInfo());
    bool is_d = (stype == ST_DUNGEON);
    s8 sFloor = dMapInfo_c::getNowStayFloorNoDecisionFlg() ? dMapInfo_c::getNowStayFloorNo() : dMapInfo_c::calcFloorNo(playerPos.y, true, stayNo);

    // Classification & Context Markers
    bool isFieldStage = sName && sName[0] == 'F';
    bool isRoomStage = sName && sName[0] == 'R';
    iData[47] = (sName && (sName[0] == 'D' || (is_d && !isFieldStage))) ? 1 : 0; // isDungeon

    // Restart Marker (Entrance)
    Vec restartPos = dMapInfo_n::getMapRestartPos();
    s8 restartFloor = dMapInfo_c::calcFloorNo(restartPos.y, true, dComIfGs_getRestartRoomNo());
    iData[46] = (restartFloor == sFloor) ? 1 : 0;

    float fData[10] = {
        playerPos.x, playerPos.z, (float)dMapInfo_n::getMapPlayerAngleY() * (180.0f / 32768.0f),
        0, 0, 0, 0, // minX, minZ, maxX, maxZ (set below)
        restartPos.x, restartPos.z, (float)dMapInfo_n::getMapRestartAngleY() * (180.0f / 32768.0f)
    };

    float minX = 1e10f, minZ = 1e10f, maxX = -1e10f, maxZ = -1e10f;
    std::vector<float> finalLines, icons, doors;

    if (dMpath_c::mLayerList) for (int r = 0; r < 64; r++) {
        // PRECISION FOG-OF-WAR:
        // - Prefix 'R' (Interiors): ONLY render the current room link is standing in.
        // - Others: Render only rooms Link has visited (respects fog-of-war).
        bool showRoom = (r == stayNo);
        if (!showRoom) {
            if (isRoomStage) showRoom = false;
            else showRoom = dComIfGs_isVisitedRoom(r) || (iData[47] && iData[48]);
        }
        if (!showRoom) continue;

        for (int l = 0; l < 2; l++) {
            dDrawPath_c::room_class* room = dMpath_c::getRoomPointer(l, r);
            if (!room || !room->mpFloatData) continue;
            for (int f = 0; f < room->mFloorNum; f++) {
                // FLOOR FILTERING: Enabled for Dungeons/Interiors.
                // Disabled for Fields (prefix 'F') to ensure path connectivity.
                if (!isFieldStage && room->mpFloor[f].mFloorNo != sFloor) continue;

                for (int g = 0; g < room->mpFloor[f].mGroupNum; g++) {
                    auto& group = room->mpFloor[f].mpGroup[g];
                    dDrawPath_c::line_class* lines = group.mpLine;
                    for (int ln = 0; ln < group.mLineNum; ln++) {
                        for (int i = 0; i < lines[ln].mDataNum; i++) {
                            u16 idx = lines[ln].mpData[i];
                            // ALIGNMENT FIX: Geography data is already global.
                            float px = room->mpFloatData[idx*2];
                            float pz = room->mpFloatData[idx*2+1];
                            finalLines.push_back(px); finalLines.push_back(pz);
                            minX = std::min(minX, px); maxX = std::max(maxX, px);
                            minZ = std::min(minZ, pz); maxZ = std::max(maxZ, pz);
                        }
                        finalLines.push_back(std::numeric_limits<float>::quiet_NaN());
                        finalLines.push_back((float)lines[ln].field_0x0);
                        finalLines.push_back((float)lines[ln].field_0x1);
                        finalLines.push_back(0.0f);
                    }
                    dDrawPath_c::poly_class* polys = group.mpPoly;
                    for (int pn = 0; pn < group.mPolyNum; pn++) {
                        for (int i = 0; i < polys[pn].mDataNum; i++) {
                            u16 idx = polys[pn].mpData[i];
                            float px = room->mpFloatData[idx*2];
                            float pz = room->mpFloatData[idx*2+1];
                            finalLines.push_back(px); finalLines.push_back(pz);
                        }
                        finalLines.push_back(std::numeric_limits<float>::quiet_NaN());
                        finalLines.push_back((float)polys[pn].field_0x0);
                        finalLines.push_back(1001.0f);
                        finalLines.push_back(0.0f);
                    }
                }
            }
        }
    }
    fData[3] = minX; fData[4] = minZ; fData[5] = maxX; fData[6] = maxZ;

    // Collect Map Icons (Chests, Bosses, etc.)
    for (int g = 0; g < 17; g++) {
        for (auto* data = dTres_c::getFirstData(g); data != nullptr; data = dTres_c::getNextData(data)) {
            if (should_draw_icon(data->mType, data, stayNo, sFloor)) {
                // Send 'g' (Group Index) as the type for reliable classification
                icons.push_back((float)g);
                icons.push_back(data->mPos.x);
                icons.push_back(data->mPos.z);
                icons.push_back((float)data->mRoomNo);
            }
        }
    }

    // Collect Doors
    auto add_doors = [&](dStage_KeepDoorInfo* info) {
        if (!info) return;
        for (int i = 0; i < info->mNum; i++) {
            stage_tgsc_data_class& door = info->mDrTgData[i];
            int roomNo = (door.base.parameters >> 24) & 0x3F;

            // FLOOR FILTERING for Doors
            s8 doorFloor = dMapInfo_c::calcFloorNo(door.base.position.y, true, roomNo);
            if (doorFloor != sFloor) continue;

            bool showDoor = (roomNo == stayNo) || dComIfGs_isVisitedRoom(roomNo) || (iData[47] && iData[48]);
            if (!showDoor) continue;

            doors.push_back(door.base.position.x);
            doors.push_back(door.base.position.z);
            doors.push_back((float)door.base.angle.y * (180.0f / 32768.0f));
            doors.push_back(0.0f); // Type placeholder
        }
    };
    add_doors(dStage_GetKeepDoorInfo());
    add_doors(dStage_GetRoomKeepDoorInfo());

    jstring jStage = env->NewStringUTF(friendlyName.c_str());
    jintArray jInts = env->NewIntArray(60); env->SetIntArrayRegion(jInts, 0, 60, iData);
    jfloatArray jFloats = env->NewFloatArray(10); env->SetFloatArrayRegion(jFloats, 0, 10, fData);
    jfloatArray jL = env->NewFloatArray(finalLines.size()); env->SetFloatArrayRegion(jL, 0, finalLines.size(), finalLines.data());
    jfloatArray jI = env->NewFloatArray(icons.size()); env->SetFloatArrayRegion(jI, 0, icons.size(), icons.data());
    jfloatArray jD = env->NewFloatArray(doors.size()); env->SetFloatArrayRegion(jD, 0, doors.size(), doors.data());
    env->CallVoidMethod(activity, s_onGameStateUpdate, jInts, jFloats, jStage, jL, jI, jD);
    env->DeleteLocalRef(jInts); env->DeleteLocalRef(jFloats); env->DeleteLocalRef(jStage);
    env->DeleteLocalRef(jL); env->DeleteLocalRef(jI); env->DeleteLocalRef(jD);
    env->DeleteLocalRef(activity);
}
} // namespace dusk::android
#else
namespace dusk::android { void hud_update() {} bool hud_is_second_screen_active() { return false; } }
#endif
