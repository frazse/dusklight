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

struct TempLine {
    std::vector<float> coords;
    float area = 0.0f;
    bool isClosed = false;
};

float calculate_polygon_area(const std::vector<float>& pts) {
    if (pts.size() < 6) return 0.0f;
    float area = 0.0f;
    for (size_t i = 0; i < pts.size() - 2; i += 2) {
        area += pts[i] * pts[i+3];
        area -= pts[i+2] * pts[i+1];
    }
    return std::abs(area) / 2.0f;
}

bool should_draw_icon(int type, const dTres_c::data_s* data, int stayNo) {
    if (data == nullptr) return false;
    auto* stage = dComIfGp_getStage();
    if (stage == nullptr) return false;
    StageType stype = (StageType)dStage_stagInfo_GetSTType(stage->getStagInfo());
    bool is_d = (stype == ST_DUNGEON);
    bool visited = dComIfGs_isVisitedRoom(data->mRoomNo) || data->mRoomNo == stayNo;
    if (data->mNo != 0xFF && dComIfGs_isTbox(data->mNo)) return false;
    if (data->mSwBit != 0xFF && dComIfGs_isSwitch(data->mSwBit, data->mRoomNo)) return false;
    if (is_d) {
        if (!dMapInfo_n::chkGetCompass()) return false;
        return true;
    } else {
        if (type == 4) return dComIfGp_getStartStageDarkArea() != 0;
        if (type == 0 || type == 10 || type == 2 || type == 3 || type == 5 || type == 16) return false;
        return visited;
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
    iData[19] = dComIfGp_getSelectItemNum(0); iData[20] = dComIfGp_getSelectItemNum(1);
    iData[21] = dComIfGp_getSelectItem(2); iData[22] = dComIfGp_getSelectItemNum(2);
    iData[23] = dComIfGp_getSelectItem(3); iData[24] = dComIfGp_getSelectItemNum(3);
    iData[25] = dComIfGp_getSelectItem(4); iData[26] = dComIfGp_getSelectItemNum(4);
    iData[27] = (dComIfGp_isZSetFlag(2) || dComIfGp_isZSetFlag(4)) ? 1 : 0;
    iData[40] = dComIfGp_getSelectItem(5); iData[41] = dComIfGp_getSelectItemNum(5);
    iData[42] = dMeter2Info_getHorseLifeCount();
    iData[43] = (meter && meter->isShowFlag(11)) ? 1 : 0;

    dAttention_c* attn = dComIfGp_getAttention();
    daPy_py_c* player = dComIfGp_getLinkPlayer();
    int stateFlags = 0;
    if (attn && attn->GetLockonCount() > 0) stateFlags |= 1;
    if (player && (player->checkWaterInMove() || player->checkSwimUp())) stateFlags |= 2;
    if (player && player->checkHorseRide()) stateFlags |= 4;
    iData[31] = stateFlags;
    iData[28] = dComIfGp_getDoStatusForce() ? dComIfGp_getDoStatusForce() : dComIfGp_getDoStatus();
    iData[29] = dComIfGp_getAStatusForce() ? dComIfGp_getAStatusForce() : dComIfGp_getAStatus();
    iData[30] = dComIfGp_getZStatus(); iData[32] = dComIfGp_getRStatus();
    iData[33] = dComIfGp_getXStatus(); iData[34] = dComIfGp_getYStatus();
    int dPadS = dComIfGp_get3DStatus(), dPadD = dComIfGp_get3DDirection();
    iData[35] = (dPadD & 8) ? dPadS : 0; iData[36] = (dPadD & 2) ? dPadS : 0;
    iData[37] = (dPadD & 1) ? dPadS : 0; iData[38] = (dPadD & 4) ? dPadS : 0;
    iData[39] = dMeter2Info_isUseButton(0xFFFF) ? 0xFFFF : 0;

    Vec playerPos = dMapInfo_n::getMapPlayerPos();
    float fData[7] = { playerPos.x, playerPos.z, (float)dMapInfo_n::getMapPlayerAngleY() * (180.0f / 32768.0f) };

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

    bool has_map = dMapInfo_n::chkGetMap();
    auto* stage = dComIfGp_getStage();
    StageType stype = (StageType)dStage_stagInfo_GetSTType(stage->getStagInfo());
    bool is_d = (stype == ST_DUNGEON);
    s8 sFloor = dMapInfo_c::getNowStayFloorNoDecisionFlg() ? dMapInfo_c::getNowStayFloorNo() : dMapInfo_c::calcFloorNo(playerPos.y, true, stayNo);

    float minX = 1e10f, minZ = 1e10f, maxX = -1e10f, maxZ = -1e10f;
    std::vector<float> finalLines, icons, doors;

    if (dMpath_c::mLayerList) for (int r = 0; r < 64; r++) {
        // --- ROOM VISIBILITY LOGIC ---
        // 1. Interior/Boss stages: strictly show ONLY the current room to prevent overlap.
        if (stype == ST_ROOM || stype == ST_BOSS_ROOM) {
            if (r != stayNo) continue;
        }
        // 2. Dungeon stages: show all rooms if map is held, otherwise only visited.
        else if (stype == ST_DUNGEON) {
            if (!has_map && !dComIfGs_isVisitedRoom(r) && r != stayNo) continue;
        }
        // 3. Field/Castle Town: show all visited rooms (geographically separate).
        else {
            if (!dComIfGs_isVisitedRoom(r) && r != stayNo) continue;
        }

        std::vector<TempLine> roomLines;
        float maxRoomArea = 0; int boundaryIdx = -1;

        for (int l = 0; l < 2; l++) {
            dDrawPath_c::room_class* room = dMpath_c::getRoomPointer(l, r);
            if (room && room->mpFloatData) for (int f = 0; f < room->mFloorNum; f++) {
                if (room->mpFloor[f].mFloorNo != sFloor) continue;
                for (int g = 0; g < room->mpFloor[f].mGroupNum; g++) {
                    dDrawPath_c::line_class* line = room->mpFloor[f].mpGroup[g].mpLine;
                    for (int ln = 0; ln < room->mpFloor[f].mpGroup[g].mLineNum; ln++) {
                        TempLine tl;
                        for (int i = 0; i < line[ln].mDataNum; i++) {
                            u16 idx = line[ln].mpData[i];
                            BE<Vec> p = {room->mpFloatData[idx*2], 0.0f, room->mpFloatData[idx*2+1]};
                            dMapInfo_n::correctionOriginPos(r, &p);
                            tl.coords.push_back(p.x); tl.coords.push_back(p.z);
                        }
                        if (tl.coords.size() >= 6) {
                            float dist = std::hypot(tl.coords[0] - tl.coords[tl.coords.size()-2], tl.coords[1] - tl.coords[tl.coords.size()-1]);
                            tl.isClosed = (dist < 5.0f);
                            if (tl.isClosed) {
                                tl.area = calculate_polygon_area(tl.coords);
                                if (tl.area > maxRoomArea) { maxRoomArea = tl.area; boundaryIdx = (int)roomLines.size(); }
                            }
                        }
                        roomLines.push_back(tl);
                    }
                }
            }
        }

        for (int i = 0; i < (int)roomLines.size(); i++) {
            // DUNGEON/INTERIOR BYPASS: Never filter boundaries in temples or houses.
            if (stype == ST_FIELD && i == boundaryIdx && maxRoomArea > 200000.0f) continue;

            for (size_t j = 0; j < roomLines[i].coords.size(); j += 2) {
                finalLines.push_back(roomLines[i].coords[j]); finalLines.push_back(roomLines[i].coords[j+1]);
                minX = std::min(minX, roomLines[i].coords[j]); maxX = std::max(maxX, roomLines[i].coords[j]);
                minZ = std::min(minZ, roomLines[i].coords[j+1]); maxZ = std::max(maxZ, roomLines[i].coords[j+1]);
            }
            finalLines.push_back(std::numeric_limits<float>::quiet_NaN()); finalLines.push_back(0.0f);
        }
    }
    fData[3] = minX; fData[4] = minZ; fData[5] = maxX; fData[6] = maxZ;

    // Original Dungeon Transitions
    auto collect_orig_doors = [&](dStage_KeepDoorInfo* di, bool correct) {
        if (!di) return;
        for (int i = 0; i < di->mNum; i++) {
            const stage_tgsc_data_class* d = &di->mDrTgData[i];
            int p0 = (d->base.parameters >> 0xD) & 0x3F;
            if (is_d && !has_map && !dComIfGs_isVisitedRoom(p0) && p0 != stayNo) continue;
            if (dMapInfo_c::calcFloorNo(d->base.position.y, true, p0) != sFloor) continue;
            BE(Vec) p = (Vec)(cXyz)d->base.position; if (correct) dMapInfo_n::correctionOriginPos(p0, &p);
            doors.push_back(p.x); doors.push_back(p.z); doors.push_back((float)d->base.angle.y * (180.0f / 32768.0f)); doors.push_back(0.0f);
        }
    };
    collect_orig_doors(dStage_GetKeepDoorInfo(), true); collect_orig_doors(dStage_GetRoomKeepDoorInfo(), false);

    for (int t = 0; t < dTres_c::TYPE_GROUP_ENUM_NUMBER; t++) {
        for (dTres_c::typeGroupData_c* data = dTres_c::getFirstData(t); data; data = dTres_c::getNextData(data)) {
            if (!is_d && stayNo != 0 && data->mRoomNo != -1 && data->mRoomNo != stayNo) continue;
            if (t != 4 && data->mRoomNo != -1 && !dComIfGs_isVisitedRoom(data->mRoomNo) && data->mRoomNo != stayNo && !is_d) continue;
            if (should_draw_icon(t, data->getConstDataPointer(), stayNo)) {
                BE(Vec) p = data->mPos; if (data->mRoomNo != -1) dMapInfo_n::correctionOriginPos(data->mRoomNo, &p);
                icons.push_back((float)t); icons.push_back(p.x); icons.push_back(p.z); icons.push_back((float)data->mStatus);
            }
        }
    }

    jstring jStage = env->NewStringUTF(friendlyName.c_str());
    jintArray jInts = env->NewIntArray(60); env->SetIntArrayRegion(jInts, 0, 60, iData);
    jfloatArray jFloats = env->NewFloatArray(7); env->SetFloatArrayRegion(jFloats, 0, 7, fData);
    jfloatArray jLines = env->NewFloatArray(finalLines.size()); env->SetFloatArrayRegion(jLines, 0, finalLines.size(), finalLines.data());
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
