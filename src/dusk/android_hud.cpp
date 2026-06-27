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
    if (env == nullptr || !env->ExceptionCheck()) return false;
    env->ExceptionClear();
    return true;
}

jmethodID s_onGameStateUpdate = nullptr;
std::atomic<bool> s_secondScreenActive{false};
constexpr int kHudUpdateInterval = 6;
int s_frameCounter = 0;

/**
 * Refined visibility logic based on Dungeon Map and Compass progression.
 */
bool should_draw_icon(int type, const dTres_c::data_s* data, int stayNo) {
    if (data == nullptr) return false;
    auto* stage = dComIfGp_getStage();
    if (stage == nullptr) return false;

    StageType stage_type = (StageType)dStage_stagInfo_GetSTType(stage->getStagInfo());
    bool is_dungeon = (stage_type == ST_DUNGEON);
    bool has_compass = dMapInfo_n::chkGetCompass();
    bool visited = dComIfGs_isVisitedRoom(data->mRoomNo) || data->mRoomNo == stayNo;

    // 1. Global "Completion" Checks
    // If it's a treasure box and it's already open, hide it.
    if (data->mNo != 0xFF && dComIfGs_isTbox(data->mNo)) return false;

    // If it's associated with a switch (Monkeys, Poes, etc.) and switch is ON, hide it.
    if (data->mSwBit != 0xFF && dComIfGs_isSwitch(data->mSwBit, data->mRoomNo)) return false;

    // 2. Type-Specific Logic
    if (is_dungeon) {
        // Dungeon Rule: Most markers (Chests, Keys, Boss, Objectives) require the Compass.
        if (!has_compass) return false;

        if (type == 3) { // Boss Icon
            if (dComIfGs_isStageBossEnemy()) return false;
        }

        // Return true if we reached here (Compass is found and not "completed")
        return true;
    } else {
        // Overworld Rule:
        // Show Light Drops (4) if we are in a Dark Area.
        if (type == 4) {
             return dComIfGp_getStartStageDarkArea() != 0;
        }

        // NEVER show: Chests (0, 10), Heart Pieces (2), Boss (3), or generic quest markers (5, 16).
        if (type == 0 || type == 10 || type == 2 || type == 3 || type == 5 || type == 16) {
            return false;
        }

        // DO show: Landmarks, Warp Portals, etc., but only if the area has been visited.
        return visited;
    }
}

} // namespace

bool hud_is_second_screen_active() {
    return s_secondScreenActive.load(std::memory_order_relaxed);
}

void hud_update() {
    if (++s_frameCounter < kHudUpdateInterval) return;
    s_frameCounter = 0;

    int stayNo = dComIfGp_roomControl_getStayNo();
    if (stayNo < 0) return;
    auto* stage = dComIfGp_getStage();
    if (stage == nullptr) return;

    auto* env = static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
    if (env == nullptr) return;

    jobject activity = static_cast<jobject>(SDL_GetAndroidActivity());
    if (activity == nullptr || clear_pending_exception(env)) return;

    if (s_onGameStateUpdate == nullptr) {
        jclass cls = env->GetObjectClass(activity);
        s_onGameStateUpdate = env->GetMethodID(cls, "onGameStateUpdate", "([I[FLjava/lang/String;[F[F[F)V");
        env->DeleteLocalRef(cls);
        if (s_onGameStateUpdate == nullptr || clear_pending_exception(env)) return;
    }

    s_secondScreenActive.store(true, std::memory_order_relaxed);

    int iData[40] = {0};
    iData[0] = dComIfGs_getLife();      iData[1] = dComIfGs_getMaxLife();
    iData[2] = dComIfGs_getMagic();     iData[3] = dComIfGs_getMaxMagic();
    iData[4] = dComIfGs_getOil();       iData[5] = dComIfGs_getMaxOil();
    iData[6] = dComIfGp_getNowOxygen(); iData[7] = dComIfGp_getMaxOxygen();
    iData[8] = dComIfGs_getRupee();     iData[9] = dComIfGs_getKeyNum();
    iData[10] = dComIfGs_getArrowNum(); iData[11] = dComIfGs_getBombNum(0);
    iData[12] = dComIfGs_getTransformStatus(); iData[13] = stayNo;
    iData[14] = dComIfGs_getLightDropNum(dComIfGp_getStartStageDarkArea());
    iData[15] = dComIfGp_getNeedLightDropNum();
    dMeter2_c* meter = dMeter2Info_getMeterClass();
    iData[16] = (meter && meter->isShowLightDrop()) ? 1 : 0;

    // Items
    iData[17] = dComIfGp_getSelectItem(0); iData[18] = dComIfGp_getSelectItem(1);
    iData[19] = dComIfGp_getSelectItemNum(0); iData[20] = dComIfGp_getSelectItemNum(1);
    iData[21] = dComIfGp_getSelectItem(2); iData[22] = dComIfGp_getSelectItemNum(2);
    iData[23] = iData[17]; iData[24] = iData[19];
    iData[25] = iData[18]; iData[26] = iData[20];
    iData[27] = (dComIfGp_isZSetFlag(2) || dComIfGp_isZSetFlag(4)) ? 1 : 0;

    // Player movement state
    dAttention_c* attn = dComIfGp_getAttention();
    daPy_py_c* player = dComIfGp_getLinkPlayer();
    int stateFlags = 0;
    if (attn && attn->GetLockonCount() > 0) stateFlags |= 1; // Targeting
    if (player && (player->checkWaterInMove() || player->checkSwimUp())) stateFlags |= 2; // Swimming
    if (player && player->checkHorseRide()) stateFlags |= 4; // Riding
    iData[31] = stateFlags;

    // Actions
    u8 doStatus = dComIfGp_getDoStatusForce();
    if (doStatus == 0) doStatus = dComIfGp_getDoStatus();
    iData[28] = doStatus;
    u8 aStatus = dComIfGp_getAStatusForce();
    if (aStatus == 0) aStatus = dComIfGp_getAStatus();
    iData[29] = aStatus;
    iData[30] = dComIfGp_getZStatus();
    iData[32] = dComIfGp_getRStatus();
    iData[33] = dComIfGp_getXStatus();
    iData[34] = dComIfGp_getYStatus();

    // DPad
    int dPadS = dComIfGp_get3DStatus(), dPadD = dComIfGp_get3DDirection();
    iData[35] = (dPadD & 8) ? dPadS : 0; iData[36] = (dPadD & 2) ? dPadS : 0;
    iData[37] = (dPadD & 1) ? dPadS : 0; iData[38] = (dPadD & 4) ? dPadS : 0;

    // Visibility Mask
    int visMask = 0;
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_A)) visMask |= 1;
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_B)) visMask |= 2;
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_Z)) visMask |= 4;
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_R)) visMask |= 8;
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_X)) visMask |= 16;
    if (dMeter2Info_isUseButton(METER2_USEBUTTON_Y)) visMask |= 32;
    iData[39] = visMask;

    // Map Floats
    Vec playerPos = dMapInfo_n::getMapPlayerPos();
    float fData[7] = { playerPos.x, playerPos.z, (float)dMapInfo_n::getMapPlayerAngleY() * (180.0f / 32768.0f) };
    float minX = 1e10f, minZ = 1e10f, maxX = -1e10f, maxZ = -1e10f;
    std::vector<float> lines, icons, doors;

    const char* sName = dComIfGp_getStartStageName();
    std::string friendlyName = sName ? sName : "Loading...";
    if (sName) {
        for (const auto& reg : gameRegions) for (const auto& m : reg.maps) if (strcmp(m.mapFile, sName) == 0) {
            if (m.mapRooms.empty()) friendlyName = m.mapName;
            else for (const auto& r : m.mapRooms) if (r.roomNo == stayNo) { friendlyName = m.mapName; break; }
        }
    }

    bool has_map = dMapInfo_n::chkGetMap();
    StageType stype = (StageType)dStage_stagInfo_GetSTType(stage->getStagInfo());
    bool is_d = (stype == ST_DUNGEON);
    s8 sFloor = dMapInfo_c::getNowStayFloorNoDecisionFlg() ? dMapInfo_c::getNowStayFloorNo() : dMapInfo_c::calcFloorNo(playerPos.y, true, stayNo);

    // --- Process Room Layouts ---
    if (dMpath_c::mLayerList) for (int l = 0; l < 2; l++) for (int r = 0; r < 64; r++) {
        // Dungeon Rule: If you don't have the Map, only show visited rooms.
        if (is_d && !has_map && !dComIfGs_isVisitedRoom(r) && r != stayNo) continue;
        // Overworld Rule: Only show visited rooms.
        if (!is_d && !dComIfGs_isVisitedRoom(r) && r != stayNo) continue;

        dDrawPath_c::room_class* room = dMpath_c::getRoomPointer(l, r);
        if (room && room->mpFloatData) for (int f = 0; f < room->mFloorNum; f++) {
            if (room->mpFloor[f].mFloorNo != sFloor) continue;
            for (int g = 0; g < room->mpFloor[f].mGroupNum; g++) {
                dDrawPath_c::line_class* line = room->mpFloor[f].mpGroup[g].mpLine;
                for (int ln = 0; ln < room->mpFloor[f].mpGroup[g].mLineNum; ln++) {
                    for (int i = 0; i < line[ln].mDataNum; i++) {
                        u16 idx = line[ln].mpData[i];
                        BE<Vec> pos = {room->mpFloatData[idx*2], 0.0f, room->mpFloatData[idx*2+1]};
                        dMapInfo_n::correctionOriginPos(r, &pos);
                        lines.push_back(pos.x); lines.push_back(pos.z);
                        minX = std::min(minX, (float)pos.x); maxX = std::max(maxX, (float)pos.x);
                        minZ = std::min(minZ, (float)pos.z); maxZ = std::max(maxZ, (float)pos.z);
                    }
                    lines.push_back(std::numeric_limits<float>::quiet_NaN()); lines.push_back(0.0f);
                }
            }
        }
    }
    fData[3] = minX; fData[4] = minZ; fData[5] = maxX; fData[6] = maxZ;

    // --- Process Doors ---
    auto collect_doors = [&](dStage_KeepDoorInfo* di, bool correct) {
        if (!di) return;
        for (int i = 0; i < di->mNum; i++) {
            const stage_tgsc_data_class* d = &di->mDrTgData[i];
            int p0 = (d->base.parameters >> 0xD) & 0x3F, p1 = (d->base.parameters >> 0x13) & 0x3F;
            if ((is_d && !has_map && !dComIfGs_isVisitedRoom(p0) && !dComIfGs_isVisitedRoom(p1) && p0 != stayNo && p1 != stayNo) || (!is_d && !dComIfGs_isVisitedRoom(p0) && !dComIfGs_isVisitedRoom(p1) && p0 != stayNo && p1 != stayNo)) continue;
            if (dMapInfo_c::calcFloorNo(d->base.position.y, true, p0) != sFloor) continue;
            BE(Vec) p = (Vec)(cXyz)d->base.position; if (correct) dMapInfo_n::correctionOriginPos(p0, &p);
            doors.push_back(p.x); doors.push_back(p.z); doors.push_back((float)d->base.angle.y * (180.0f / 32768.0f)); doors.push_back(0.0f);
        }
    };
    collect_doors(dStage_GetKeepDoorInfo(), true); collect_doors(dStage_GetRoomKeepDoorInfo(), false);

    // --- Process Icons ---
    for (int t = 0; t < dTres_c::TYPE_GROUP_ENUM_NUMBER; t++) {
        for (dTres_c::typeGroupData_c* data = dTres_c::getFirstData(t); data; data = dTres_c::getNextData(data)) {
            // Overworld Interior Filter: If we are in a house/interior room,
            // only show icons that are physically inside that room.
            if (!is_d && stayNo != 0 && data->mRoomNo != -1 && data->mRoomNo != stayNo) continue;

            // General filter: hidden if room hasn't been visited (unless Compass reveals it or it's a Light Drop)
            bool is_light_drop = (t == 4);
            if (!is_light_drop && data->mRoomNo != -1 && !dComIfGs_isVisitedRoom(data->mRoomNo) && data->mRoomNo != stayNo && !is_d) continue;

            if (should_draw_icon(t, data->getConstDataPointer(), stayNo)) {
                BE(Vec) p = data->mPos; if (data->mRoomNo != -1) dMapInfo_n::correctionOriginPos(data->mRoomNo, &p);
                icons.push_back((float)t); icons.push_back(p.x); icons.push_back(p.z); icons.push_back((float)data->mStatus);
            }
        }
    }

    jstring jStage = env->NewStringUTF(friendlyName.c_str());
    jintArray jInts = env->NewIntArray(40); env->SetIntArrayRegion(jInts, 0, 40, iData);
    jfloatArray jFloats = env->NewFloatArray(7); env->SetFloatArrayRegion(jFloats, 0, 7, fData);
    jfloatArray jLines = env->NewFloatArray(lines.size()); env->SetFloatArrayRegion(jLines, 0, lines.size(), lines.data());
    jfloatArray jIcons = env->NewFloatArray(icons.size()); env->SetFloatArrayRegion(jIcons, 0, icons.size(), icons.data());
    jfloatArray jDoors = env->NewFloatArray(doors.size()); env->SetFloatArrayRegion(jDoors, 0, doors.size(), doors.data());

    env->CallVoidMethod(activity, s_onGameStateUpdate, jInts, jFloats, jStage, jLines, jIcons, jDoors);

    env->DeleteLocalRef(jInts); env->DeleteLocalRef(jFloats); env->DeleteLocalRef(jStage);
    env->DeleteLocalRef(jLines); env->DeleteLocalRef(jIcons); env->DeleteLocalRef(jDoors);
    env->DeleteLocalRef(activity);
}
} // namespace dusk::android
#else
namespace dusk::android { void hud_update() {} bool hud_is_second_screen_active() { return false; } }
#endif
