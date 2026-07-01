#include "dusk/android_hud.hpp"

#if defined(TARGET_ANDROID) || defined(__ANDROID__) || defined(ANDROID)
#include "d/d_com_inf_game.h"
#include "d/d_map_path_dmap.h"
#include "d/d_meter2_info.h"
#include "d/d_meter2_draw.h"
#include "d/d_meter2.h"
#include "d/d_menu_dmap.h"
#include "d/d_menu_fmap.h"
#include "d/d_menu_fmap2D.h"
#include "d/d_menu_collect.h"
#include "d/d_menu_window.h"
#include "d/d_menu_option.h"
#include "d/d_menu_save.h"
#include "d/d_menu_ring.h"
#include "d/d_menu_letter.h"
#include "d/d_menu_fishing.h"
#include "d/d_menu_skill.h"
#include "d/d_menu_insect.h"
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
constexpr int kHudUpdateInterval = 1;
int s_frameCounter = 0;

bool should_draw_icon(int type, const dTres_c::data_s* data, int stayNo, s8 sFloor) {
    if (data == nullptr) return false;
    if (data->mNo != 0xFF && dComIfGs_isTbox(data->mNo)) return false;
    if (data->mSwBit != 0xFF && dComIfGs_isSwitch(data->mSwBit, data->mRoomNo)) return false;
    auto* stage = dComIfGp_getStage();
    if (stage == nullptr) return false;
    StageType stype = (StageType)dStage_stagInfo_GetSTType(stage->getStagInfo());
    bool is_d = (stype == ST_DUNGEON);
    s8 iconFloor = dMapInfo_c::calcFloorNo(data->mPos.y, true, data->mRoomNo);
    if (iconFloor != sFloor) return false;
    if (is_d) return dComIfGs_isDungeonItemCompass();
    if (type == 4) {
        int darkArea = dComIfGp_getStartStageDarkArea();
        if (darkArea == 0) return false;
        if (dComIfGs_getLightDropNum(darkArea) >= dComIfGp_getNeedLightDropNum()) return false;
        return true;
    }
    return false;
}
} // namespace

bool hud_is_second_screen_active() { return s_secondScreenActive.load(std::memory_order_relaxed); }

void hud_update() {
    if (++s_frameCounter < kHudUpdateInterval) return;
    s_frameCounter = 0;

    dMeter2_c* meter = dMeter2Info_getMeterClass();
    if (!meter) return;

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

    // 1. Diagnostics (W:57, M:58)
    int winStatus = dMeter2Info_getWindowStatus();
    int mapStatus = dMeter2Info_getMapStatus();
    iData[57] = winStatus;
    iData[58] = mapStatus;

    // 2. Base IDs
    iData[28] = meter->getDoStatus(); // A
    iData[29] = meter->getAStatus();  // B
    iData[30] = meter->getZStatus();
    iData[32] = meter->getRStatus();
    iData[33] = meter->getItemStatus(1); // X
    iData[34] = meter->getItemStatus(3); // Y
    iData[59] = 0; // L Prompt

    // 3. AUTHORITATIVE MENU OVERRIDES (Truth Mirror)
    dMw_c* mw = dMeter2Info_getMenuWindowClass();
    if (mw) {
        if (winStatus == 3) { // Pause Menu (Collection)
            dMenu_Collect_c* collect = mw->getMenuCollect();
            if (collect) {
                u8 sub = collect->getSubWindowOpenCheck();
                if (sub == 1) { // Save Submenu
                    dMenu_save_c* s = mw->getMenuSave();
                    if (s) { iData[28] = (int)s->getAButtonString(); iData[29] = (int)s->getBButtonString(); }
                } else if (sub == 2) { // Option Submenu
                    dMenu_Option_c* o = mw->getMenuOption();
                    if (o) { iData[28] = (int)o->getAButtonString(); iData[29] = (int)o->getBButtonString(); iData[30] = (int)o->getZButtonString(); }
                } else {
                    dMenu_Collect2D_c* collect2d = collect->getCollect2D();
                    if (collect2d) { iData[28] = (int)collect2d->getCurrentAString(); iData[29] = (int)collect2d->getCurrentBString(); }
                }
            }
        } else if (winStatus == 4) { // Field Map
            dMenu_Fmap_c* fmap = mw->getMenuFmap();
            if (fmap) {
                dMenu_Fmap2DTop_c* top = fmap->getDraw2DTop();
                if (top) { iData[28] = (int)top->getAButtonString(); iData[29] = (int)top->getBButtonString(); iData[30] = (int)top->getZButtonString(); }
            }
        } else if (winStatus == 5) { // Dungeon Map
            dMenu_Dmap_c* dmap = mw->getMenuDmap();
            if (dmap) {
                dMenu_DmapBg_c* bg = dmap->getDrawBg();
                if (bg) { iData[28] = (int)bg->getAButtonString(); iData[29] = (int)bg->getBButtonString(); iData[32] = (int)bg->getCButtonString(); iData[59] = (int)bg->getCButtonString(); }
            }
        } else if (winStatus == 10) { // Submenus (Standalone Save, Options, Letters, etc)
            dMenu_save_c* save = mw->getMenuSave();
            if (save) { iData[28] = (int)save->getAButtonString(); iData[29] = (int)save->getBButtonString(); }

            dMenu_Option_c* opt = mw->getMenuOption();
            if (opt && iData[28] == 0) { iData[28] = (int)opt->getAButtonString(); iData[29] = (int)opt->getBButtonString(); iData[30] = (int)opt->getZButtonString(); }

            dMenu_Letter_c* l = mw->getMenuLetter();
            if (l && iData[28] == 0) { iData[28] = (int)l->getAButtonString(); iData[29] = (int)l->getBButtonString(); iData[32] = 0x4D8; iData[59] = 0x4D7; }

            dMenu_Fishing_c* f = mw->getMenuFishing();
            if (f && iData[28] == 0) { iData[28] = (int)f->getAButtonString(); iData[29] = (int)f->getBButtonString(); }

            dMenu_Skill_c* sk = mw->getMenuSkill();
            if (sk && iData[28] == 0) { iData[28] = (int)sk->getAButtonString(); iData[29] = (int)sk->getBButtonString(); }

            dMenu_Insect_c* ns = mw->getMenuInsect();
            if (ns && iData[28] == 0) { iData[28] = (int)ns->getAButtonString(); iData[29] = (int)ns->getBButtonString(); }
        } else if (winStatus == 1 || winStatus == 2) { // Item Wheel
            dMenu_Ring_c* ring = mw->getMenuRing();
            if (ring) iData[28] = (int)ring->getDoStatus();
        }
    }

    // 4. Force Visibility
    int vis = 0;
    if (iData[28] > 0) vis |= 1; // A
    if (iData[29] > 0) vis |= 2; // B
    if (iData[30] > 0 || winStatus == 0) vis |= 4; // Z (Always visible during gameplay)
    if (iData[32] > 0) vis |= 8; // R
    if (iData[33] > 0) vis |= 16; // X
    if (iData[34] > 0) vis |= 32; // Y
    if (iData[59] > 0) vis |= 64; // L
    iData[39] = vis;

    // 5. Basic Stats
    iData[0] = dComIfGs_getLife(); iData[1] = dComIfGs_getMaxLife();
    iData[2] = dComIfGs_getMagic(); iData[3] = dComIfGs_getMaxMagic();
    iData[4] = dComIfGs_getOil(); iData[5] = dComIfGs_getMaxOil();
    iData[6] = dComIfGp_getNowOxygen(); iData[7] = dComIfGp_getMaxOxygen();
    iData[8] = dComIfGs_getRupee();
    iData[9] = dComIfGs_getKeyNum();
    iData[10] = dComIfGs_getArrowNum(); iData[11] = dComIfGs_getBombNum(0);
    iData[12] = (int)dComIfGs_getTransformStatus();
    iData[13] = stayNo;
    iData[14] = dComIfGs_getLightDropNum(dComIfGp_getStartStageDarkArea());
    iData[15] = dComIfGp_getNeedLightDropNum();
    iData[16] = meter->isShowLightDrop() ? 1 : 0;
    iData[27] = (dComIfGp_isZSetFlag(2) || dComIfGp_isZSetFlag(4)) ? 1 : 0;
    iData[17] = dComIfGp_getSelectItem(0); iData[18] = dComIfGp_getSelectItem(1);

    auto get_ammo = [](u8 item, u8 slotNum) {
        if (item == 0x43) return (int)dComIfGs_getArrowNum();
        if (item == 0x4B) return (int)dComIfGs_getPachinkoNum();
        if (item >= 0x70 && item <= 0x72) return (int)dComIfGs_getBombNum(item - 0x70);
        return (int)dComIfGp_getSelectItemNum(slotNum);
    };
    iData[19] = get_ammo(iData[17], 0);
    iData[20] = get_ammo(iData[18], 1);
    iData[21] = dComIfGp_getSelectItem(2); iData[22] = dComIfGp_getSelectItemNum(2);
    iData[23] = dComIfGp_getSelectItem(3); iData[24] = dComIfGp_getSelectItemNum(3);
    iData[25] = dComIfGp_getSelectItem(4); iData[26] = dComIfGp_getSelectItemNum(4);
    iData[42] = dMeter2Info_getHorseLifeCount();
    iData[43] = dComIfGp_getOxygenShowFlag() ? 1 : 0;
    iData[44] = dComIfGs_getSelectEquipClothes();
    iData[46] = (dMapInfo_c::calcFloorNo(dMapInfo_n::getMapRestartPos().y, true, dComIfGs_getRestartRoomNo()) == (dMapInfo_c::getNowStayFloorNoDecisionFlg() ? dMapInfo_c::getNowStayFloorNo() : dMapInfo_c::calcFloorNo(dMapInfo_n::getMapPlayerPos().y, true, stayNo))) ? 1 : 0;
    iData[47] = dStage_stagInfo_GetSTType(dComIfGp_getStage()->getStagInfo()) == ST_DUNGEON;
    iData[48] = dComIfGs_isDungeonItemMap() ? 1 : 0;
    iData[49] = dComIfGs_isDungeonItemCompass() ? 1 : 0;
    iData[41] = dComIfGs_isDungeonItemBossKey() ? 1 : 0;

    dAttention_c* attn = dComIfGp_getAttention();
    daPy_py_c* player = dComIfGp_getLinkPlayer();
    int stateFlags = 0;
    if (attn && attn->GetLockonCount() > 0) stateFlags |= 1;
    if (player) {
        if (player->checkWaterInMove() || player->checkSwimUp() || iData[43]) stateFlags |= 2;
        if (player->checkHorseRide()) stateFlags |= 4;
        if (player->current.pos.y < player->getGroundY() - 20.0f) stateFlags |= 8;
        if (iData[44] == 0x40) stateFlags |= 16;
    }
    iData[31] = stateFlags;

    for (int k = 50; k <= 56; k++) iData[k] = -1;
    u32 bCount = 0;
    PADButtonMapping* pbm = PADGetButtonMappings(0, &bCount);
    if (pbm) {
        for (u32 j = 0; j < bCount; j++) {
            switch (pbm[j].padButton) {
                case PAD_BUTTON_A: iData[50] = pbm[j].nativeButton; break;
                case PAD_BUTTON_B: iData[51] = pbm[j].nativeButton; break;
                case PAD_BUTTON_X: iData[52] = pbm[j].nativeButton; break;
                case PAD_BUTTON_Y: iData[53] = pbm[j].nativeButton; break;
                case PAD_TRIGGER_Z: iData[54] = pbm[j].nativeButton; break;
                case PAD_TRIGGER_L: iData[55] = pbm[j].nativeButton; break;
                case PAD_TRIGGER_R: iData[56] = pbm[j].nativeButton; break;
            }
        }
    }

    // 6. Map Geometry
    Vec pPos = dMapInfo_n::getMapPlayerPos();
    const char* sName = dComIfGp_getStartStageName();
    std::string fName = sName ? sName : "Unknown";
    if (sName) {
        for (const auto& reg : gameRegions) {
            for (const auto& ma : reg.maps) {
                if (strcmp(ma.mapFile, sName) == 0) { fName = ma.mapName; goto f_ok; }
            }
        }
    }
    f_ok:;
    s8 floor = dMapInfo_c::getNowStayFloorNoDecisionFlg() ? dMapInfo_c::getNowStayFloorNo() : dMapInfo_c::calcFloorNo(pPos.y, true, stayNo);
    float roomMinX, roomMinZ, roomMaxX, roomMaxZ;
    dMapInfo_n::getRoomMinMaxXZ(stayNo, &roomMinX, &roomMinZ, &roomMaxX, &roomMaxZ);
    float fData[14] = { pPos.x, pPos.z, (float)dMapInfo_n::getMapPlayerAngleY() * (180.0f / 32768.0f), 0,0,0,0, dMapInfo_n::getMapRestartPos().x, dMapInfo_n::getMapRestartPos().z, (float)dMapInfo_n::getMapRestartAngleY() * (180.0f / 32768.0f), roomMinX, roomMinZ, roomMaxX, roomMaxZ };
    std::vector<float> lines, icons, doors;
    float miX=1e10f, miZ=1e10f, maX=-1e10f, maZ=-1e10f;
    if (dMpath_c::mLayerList) for (int r = 0; r < 64; r++) {
        if (r != stayNo && !dComIfGs_isVisitedRoom(r) && !iData[48]) continue;
        for (int l = 0; l < 2; l++) {
            auto* rm = dMpath_c::getRoomPointer(l, r);
            if (!rm || !rm->mpFloatData) continue;
            for (int f = 0; f < rm->mFloorNum; f++) {
                if (sName[0] != 'F' && rm->mpFloor[f].mFloorNo != floor) continue;
                for (int g = 0; g < rm->mpFloor[f].mGroupNum; g++) {
                    auto& grp = rm->mpFloor[f].mpGroup[g];
                    for (int ln = 0; ln < grp.mLineNum; ln++) {
                        for (int i = 0; i < grp.mpLine[ln].mDataNum; i++) {
                            float px = rm->mpFloatData[grp.mpLine[ln].mpData[i]*2], pz = rm->mpFloatData[grp.mpLine[ln].mpData[i]*2+1];
                            lines.push_back(px); lines.push_back(pz);
                            miX=std::min(miX,px); maX=std::max(maX,px); miZ=std::min(miZ,pz); maZ=std::max(maZ,pz);
                        }
                        lines.push_back(std::numeric_limits<float>::quiet_NaN()); lines.push_back((float)grp.mpLine[ln].field_0x0); lines.push_back((float)grp.mpLine[ln].field_0x1); lines.push_back(0);
                    }
                    for (int pn = 0; pn < grp.mPolyNum; pn++) {
                        for (int i = 0; i < grp.mpPoly[pn].mDataNum; i++) {
                            float px = rm->mpFloatData[grp.mpPoly[pn].mpData[i]*2], pz = rm->mpFloatData[grp.mpPoly[pn].mpData[i]*2+1];
                            lines.push_back(px); lines.push_back(pz);
                        }
                        lines.push_back(std::numeric_limits<float>::quiet_NaN()); lines.push_back((float)grp.mpPoly[pn].field_0x0); lines.push_back(1001.0f); lines.push_back(0);
                    }
                }
            }
        }
    }
    fData[3]=miX; fData[4]=miZ; fData[5]=maX; fData[6]=maZ;
    for (int g = 0; g < 17; g++) {
        for (auto* d = dTres_c::getFirstData(g); d; d = dTres_c::getNextData(d)) {
            if (should_draw_icon(d->mType, d, stayNo, floor)) {
                icons.push_back((float)g); icons.push_back(d->mPos.x); icons.push_back(d->mPos.z); icons.push_back((float)d->mRoomNo);
            }
        }
    }
    auto ad = [&](dStage_KeepDoorInfo* in) {
        if (!in) return;
        for (int i = 0; i < in->mNum; i++) {
            auto& dr = in->mDrTgData[i]; int r = (dr.base.parameters >> 24) & 0x3F;
            if (dMapInfo_c::calcFloorNo(dr.base.position.y, true, r) != floor) continue;
            if (r == stayNo || dComIfGs_isVisitedRoom(r) || iData[48]) {
                doors.push_back(dr.base.position.x); doors.push_back(dr.base.position.z); doors.push_back((float)dr.base.angle.y * (180.0f / 32768.0f)); doors.push_back(0);
            }
        }
    };
    ad(dStage_GetKeepDoorInfo()); ad(dStage_GetRoomKeepDoorInfo());

    // 7. JNI Authoritative Mirror
    jstring jS = env->NewStringUTF(fName.c_str());
    jintArray jInts = env->NewIntArray(60); env->SetIntArrayRegion(jInts, 0, 60, iData);
    jfloatArray jF = env->NewFloatArray(14); env->SetFloatArrayRegion(jF, 0, 14, fData);
    jfloatArray jL = env->NewFloatArray(lines.size()); env->SetFloatArrayRegion(jL, 0, lines.size(), lines.data());
    jfloatArray jI = env->NewFloatArray(icons.size()); env->SetFloatArrayRegion(jI, 0, icons.size(), icons.data());
    jfloatArray jD = env->NewFloatArray(doors.size()); env->SetFloatArrayRegion(jD, 0, doors.size(), doors.data());
    env->CallVoidMethod(activity, s_onGameStateUpdate, jInts, jF, jS, jL, jI, jD);
    env->DeleteLocalRef(jInts); env->DeleteLocalRef(jF); env->DeleteLocalRef(jS); env->DeleteLocalRef(jL); env->DeleteLocalRef(jI); env->DeleteLocalRef(jD); env->DeleteLocalRef(activity);
}
} // namespace dusk::android
#else
namespace dusk::android { void hud_update() {} bool hud_is_second_screen_active() { return false; } }
#endif
