#include "dusk/android_hud.hpp"

#if defined(TARGET_ANDROID) || defined(__ANDROID__) || defined(ANDROID)
#include "d/d_com_inf_game.h"
#include "d/d_map_path_dmap.h"
#include "d/d_meter2_info.h"
#include "d/d_msg_class.h"
#include "d/d_msg_object.h"
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
#include "d/d_menu_item_explain.h"
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
#include <android/log.h>
#include <vector>
#include <limits>
#include <algorithm>
#include <string>
#include <cmath>

namespace dusk::android {
namespace {

std::string clean_tp_string(const char* input) {
    if (!input || !*input) return "";
    std::string out;
    const unsigned char* p = (const unsigned char*)input;
    int safety = 0;
    while (*p && safety++ < 2048) {
        if (*p == 0x1A) {
            unsigned char size = p[1];
            if (size < 5) { p += (size > 1) ? size : 1; continue; }

            unsigned char group = p[2];
            unsigned char tag = p[3];
            unsigned int number = (p[3] << 8) | p[4];

            if (group == 0xFF && tag == 0x00) { // Color Tags \z{7ff, X00}
                if (size >= 6) {
                    out += "[[C:" + std::to_string(p[4]) + "]]";
                }
            } else if (group == 0x03) { // Wii Buttons
                switch (number) {
                    case 1:  out += "{{A}}"; break;
                    case 2:  out += "{{B}}"; break;
                    case 13: out += "{{L}}"; break;
                    case 14: out += "{{R}}"; break;
                    case 15: out += "{{X}}"; break;
                    case 16: out += "{{Y}}"; break;
                    case 19: out += "{{STICK}}"; break;
                    case 20: out += "{{Z}}"; break;
                    default: break;
                }
            } else if (group == 0x00) { // GC Buttons
                switch (number) {
                    case 0x0A: out += "{{A}}"; break;
                    case 0x0B: out += "{{B}}"; break;
                    case 0x0C: out += "{{STICK}}"; break;
                    case 0x0D: out += "{{L}}"; break;
                    case 0x0E: out += "{{R}}"; break;
                    case 0x0F: out += "{{X}}"; break;
                    case 0x10: out += "{{Y}}"; break;
                    case 0x11: out += "{{Z}}"; break;
                    case 0x13: out += "{{STICK}}"; break;
                    case 0x1C: out += "{{STICK}}"; break;
                    case 0x24: out += "{{RETICLE}}"; break;
                    case 0x2E: out += "{{XORY}}"; break;
                    case 0x37: { // Bomb Capacity (within group 0x00)
                        if (size >= 6) {
                            unsigned char type = p[5];
                            if (type == 0) out += "{{BOMBCAP0}}";
                            else if (type == 1) out += "{{BOMBCAP1}}";
                            else if (type == 2) out += "{{BOMBCAP2}}";
                        }
                        break;
                    }
                    case 0x38: out += "{{ARROWCAP}}"; break;
                    default: break;
                }
            } else if (group == 0x07) { // GC Capacity Tags (Legacy/Wii?)
                switch (number) {
                    case 0x3700: out += "{{BOMBCAP0}}"; break;
                    case 0x3701: out += "{{BOMBCAP1}}"; break;
                    case 0x3702: out += "{{BOMBCAP2}}"; break;
                    default: break;
                }
            } else if (group == 0x06) {
                if (number == 0x0A) out += "- ";
                else if (number == 0x0B) out += "  ";
            } else if (group == 0xFF) { // Special/Color Tags
                if (size >= 6 && number == 0x0000) {
                    char buf[16];
                    sprintf(buf, "[[C:%d]]", p[5]);
                    out += buf;
                }
            }

            p += size;
            continue;
        }
        if (*p == 0x1B) {
            p++;
            if (*p == 'C' && *(p+1) == 'C' && *(p+2) == '[') {
                char hex[9]; memcpy(hex, p + 3, 8); hex[8] = 0;
                out += "[[C:#" + std::string(hex) + "]]";
            }
            while (*p && *p != ']') p++;
            if (*p == ']') p++;
            continue;
        }
        if (*p == 0x0A || *p == 0x0D || *p == 0x1E) { out += '\n'; p++; continue; }
        if (*p >= 32 && *p < 127) { out += (char)*p++; } else { p++; }
    }
    return out;
}

struct BMGParts { u8* entries; u16 count; u16 entrySize; const char* pool; };
BMGParts get_bmg_parts() {
    u8* msgRes = (u8*)JKRGetTypeResource('ROOT', "zel_00.bmg", dComIfGp_getMsgDtArchive(0));
    if (!msgRes || memcmp(msgRes, "MESGbmg1", 8) != 0) return {nullptr, 0, 0, nullptr};
    u32 nSections = be32(*(u32*)(msgRes + 12));
    u8* section = msgRes + 32;
    JMSMesgInfo_c* inf1 = nullptr; u8* dat1 = nullptr;
    for (u32 i = 0; i < nSections; i++) {
        u32 magic = be32(*(u32*)section);
        if (magic == 0x494E4631) inf1 = (JMSMesgInfo_c*)section;
        else if (magic == 0x44415431) dat1 = section;
        section += be32(*(u32*)(section + 4));
    }
    if (!inf1 || !dat1) return {nullptr, 0, 0, nullptr};
    return {(u8*)inf1 + 16, be16(*(u16*)((u8*)inf1 + 8)), be16(*(u16*)((u8*)inf1 + 10)), (const char*)(dat1 + 8)};
}

u16 find_item_long_desc_id(u8 itemNo) {
    BMGParts bmg = get_bmg_parts();
    if (!bmg.entries) return 0xFFFF;
    u16 bestID = 0xFFFF;
    for (u16 i = 0; i < bmg.count; i++) {
        u8* entry = bmg.entries + (i * bmg.entrySize);
        if (entry[9] == 0x0B && entry[10] == 0x04 && entry[12] == itemNo) {
            u16 mid = be16(*(u16*)(entry + 4));
            // IDs < 0x200 are usually short names. Target GC long-descs (0x200-0x400 range).
            if (mid >= 0x0200 && mid < 0x0800) {
                if (bestID == 0xFFFF || mid < bestID) bestID = mid;
            }
        }
    }
    return bestID;
}

std::string get_full_multi_line_desc(u16 baseMsgID, u8 itemNo) {
    BMGParts bmg = get_bmg_parts();
    if (!bmg.entries) return "";
    std::string combined = "";
    for (int p = 0; p < 8; p++) {
        u16 targetID = baseMsgID + p;
        bool found = false;
        for (u16 i = 0; i < bmg.count; i++) {
            u8* entry = bmg.entries + (i * bmg.entrySize);
            if (be16(*(u16*)(entry + 4)) == targetID) {
                if (entry[12] == itemNo || (p == 0)) {
                    u32 off = be32(*(u32*)entry);
                    std::string cleaned = clean_tp_string(bmg.pool + off);
                    if (!cleaned.empty()) { if (!combined.empty()) combined += "\n"; combined += cleaned; }
                    found = true;
                }
                break;
            }
        }
        if (!found) break;
    }
    return combined;
}

bool clear_pending_exception(JNIEnv* env) { if (env == nullptr || !env->ExceptionCheck()) return false; env->ExceptionClear(); return true; }
jmethodID s_onGameStateUpdate = nullptr;
std::atomic<bool> s_secondScreenActive{false};
int s_frameCounter = 0;

bool is_room_visible(int r, const char* sName, int stayNo, bool hasMapItem) { if (sName && sName[0] == 'R') return r == stayNo; return r == stayNo || dComIfGs_isVisitedRoom(r) || hasMapItem; }
bool switch_reveals(u8 swBit, int roomNo) { return swBit == 0xFF || dComIfGs_isSwitch(swBit, roomNo); }
bool group_is_floor_independent(int typeGroupNo) { switch (typeGroupNo) { case 1: case 5: case 6: case 8: case 13: case 14: return true; default: return false; } }

bool should_draw_geometry_group(const dDrawPath_c::group_class& grp, int roomNo) {
    if (grp.mSwbit == 0xFF) return true;
    const char* stageName = dComIfGp_getStartStageName();
    bool specialOff = stageName && strcmp(stageName, "F_SP121") == 0 && grp.mSwbit == 0xb2;
    if (grp.field_0x1 == 0) return specialOff || !dComIfGs_isSwitch(grp.mSwbit, roomNo);
    else return !specialOff && dComIfGs_isSwitch(grp.mSwbit, roomNo);
}

bool should_draw_icon(int typeGroupNo, const dTres_c::data_s* data, int stayNo, s8 sFloor, const char* sName) {
    if (data == nullptr) return false;
    auto* stage = dComIfGp_getStage(); if (!stage) return false;
    StageType stype = (StageType)dStage_stagInfo_GetSTType(stage->getStagInfo());
    bool is_d = (stype == ST_DUNGEON);
    if (!group_is_floor_independent(typeGroupNo)) {
        s8 iconFloor = dMapInfo_c::calcFloorNo(data->mPos.y, true, data->mRoomNo);
        if (iconFloor != sFloor) return false;
    }
    if (is_d) {
        if (!dComIfGs_isDungeonItemCompass()) return false;
        if (data->mNo != 0xFF && dComIfGs_isTbox(data->mNo)) return false;
        return switch_reveals(data->mSwBit, data->mRoomNo);
    }
    switch (typeGroupNo) {
        case 1: case 8: if (!is_room_visible(data->mRoomNo, sName, stayNo, dComIfGs_isDungeonItemMap())) return false; return switch_reveals(data->mSwBit, data->mRoomNo);
        case 13: case 14: return switch_reveals(data->mSwBit, data->mRoomNo);
        case 5: if (data->mNo != 0xFF && dComIfGs_isTbox(data->mNo)) return false; return switch_reveals(data->mSwBit, data->mRoomNo);
        case 6: return data->mSwBit != 0xFF && dComIfGs_isSwitch(data->mSwBit, data->mRoomNo);
        case 4: {
            if (!dComIfGp_isLightDropMapVisible()) return false;
            int darkArea = dComIfGp_getStartStageDarkArea();
            if (darkArea == 0) return false;
            if (dComIfGs_getLightDropNum(darkArea) >= dComIfGp_getNeedLightDropNum()) return false;
            if (data->mNo != 0xFF && dComIfGs_isTbox(data->mNo)) return false;
            return true;
        }
        case 10: return data->mNo != 0xFF && dComIfGs_isTbox(data->mNo);
        default: return false;
    }
}
} // namespace

bool hud_is_second_screen_active() { return s_secondScreenActive.load(std::memory_order_relaxed); }

void hud_update() {
    if (++s_frameCounter < 1) return;
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
        s_onGameStateUpdate = env->GetMethodID(cls, "onGameStateUpdate", "([I[FLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[F[F[F)V");
        env->DeleteLocalRef(cls);
        if (s_onGameStateUpdate == nullptr || clear_pending_exception(env)) return;
    }
    s_secondScreenActive.store(true, std::memory_order_relaxed);
    int iData[120] = {0};
    int winStatus = dMeter2Info_getWindowStatus();
    int mapStatus = dMeter2Info_getMapStatus();
    iData[57] = winStatus; iData[58] = mapStatus;
    std::string itemTitle = ""; std::string itemDesc = ""; std::string dialogText = "";

    dMsgObject_c* msgObj = dComIfGp_getMsgObjectClass();
    if (msgObj) {
        u16 status = msgObj->getStatus();
        iData[107] = (int)status;
        if (status != 1 && status != 0 && status != 14) {
            jmessage_tReference* pRef = (jmessage_tReference*)msgObj->getSequenceProcessor()->getReference();
            if (pRef) {
                dialogText = clean_tp_string(pRef->getTextPtr());
                iData[105] = pRef->getSelectPos();
                iData[106] = pRef->getSelectNum();

                // Append choices with a special marker that Java will handle
                for (int i = 0; i < 3; i++) {
                    std::string selText = clean_tp_string(pRef->getSelTextPtr(i));
                    if (!selText.empty()) {
                        dialogText += "\n[[SEL:" + std::to_string(i) + "]]" + selText;
                    }
                }
            }
        }
    }

    iData[4] = dComIfGs_getOil(); iData[5] = dComIfGs_getMaxOil();
    iData[6] = dComIfGp_getNowOxygen(); iData[7] = dComIfGp_getMaxOxygen();

    iData[28] = meter->getDoStatus(); iData[29] = meter->getAStatus(); iData[30] = meter->getZStatus();
    iData[32] = meter->getRStatus(); iData[33] = meter->getItemStatus(1); iData[34] = meter->getItemStatus(3);

    dMw_c* mw = dMeter2Info_getMenuWindowClass();
    if (mw) {
        if (winStatus > 0) {
            // Clear prompts when any menu is open to prevent gameplay buttons leaking in
            iData[28] = 0; iData[29] = 0; iData[30] = 0; iData[32] = 0; iData[33] = 0; iData[34] = 0; iData[59] = 0;
        }

        if (winStatus == 3) {
            dMenu_Collect_c* collect = mw->getMenuCollect();
            if (collect) {
                u8 sub = collect->getSubWindowOpenCheck();
                dMenu_save_c* save = mw->getMenuSave();
                dMenu_Option_c* opt = mw->getMenuOption();
                dMenu_Letter_c* l = mw->getMenuLetter();
                dMenu_Fishing_c* f = mw->getMenuFishing();
                dMenu_Skill_c* sk = mw->getMenuSkill();
                dMenu_Insect_c* ns = mw->getMenuInsect();

                if (sub == 1 && save) { iData[28] = (int)save->getAButtonString(); iData[29] = (int)save->getBButtonString(); }
                else if (sub == 2 && opt) { iData[28] = (int)opt->getAButtonString(); iData[29] = (int)opt->getBButtonString(); iData[30] = (int)opt->getZButtonString(); }
                else if (sub == 3 && l) { iData[28] = (int)l->getAButtonString(); iData[29] = (int)l->getBButtonString(); }
                else if (sub == 4 && f) { iData[28] = (int)f->getAButtonString(); iData[29] = (int)f->getBButtonString(); }
                else if (sub == 5 && sk) { iData[28] = (int)sk->getAButtonString(); iData[29] = (int)sk->getBButtonString(); }
                else if (sub == 6 && ns) { iData[28] = (int)ns->getAButtonString(); iData[29] = (int)ns->getBButtonString(); }
                else { dMenu_Collect2D_c* collect2d = collect->getCollect2D(); if (collect2d) { iData[28] = (int)collect2d->getCurrentAString(); iData[29] = (int)collect2d->getCurrentBString(); } }
            }
        } else if (winStatus == 4) {
            dMenu_Fmap_c* fmap = mw->getMenuFmap();
            if (fmap) { dMenu_Fmap2DTop_c* top = fmap->getDraw2DTop(); if (top) { iData[28] = (int)top->getAButtonString(); iData[29] = (int)top->getBButtonString(); iData[30] = (int)top->getZButtonString(); } }
        } else if (winStatus == 5) {
            dMenu_Dmap_c* dmap = mw->getMenuDmap();
            if (dmap) { dMenu_DmapBg_c* bg = dmap->getDrawBg(); if (bg) { iData[28] = (int)bg->getAButtonString(); iData[29] = (int)bg->getBButtonString(); iData[32] = (int)bg->getCButtonString(); iData[59] = (int)bg->getCButtonString(); } }
        } else if (winStatus == 10) {
            dMenu_save_c* save = mw->getMenuSave();
            dMenu_Option_c* opt = mw->getMenuOption();
            dMenu_Letter_c* l = mw->getMenuLetter();
            dMenu_Fishing_c* f = mw->getMenuFishing();
            dMenu_Skill_c* sk = mw->getMenuSkill();
            dMenu_Insect_c* ns = mw->getMenuInsect();

            if (save) { iData[28] = (int)save->getAButtonString(); iData[29] = (int)save->getBButtonString(); }
            else if (opt) { iData[28] = (int)opt->getAButtonString(); iData[29] = (int)opt->getBButtonString(); iData[30] = (int)opt->getZButtonString(); }
            else if (l) { iData[28] = (int)l->getAButtonString(); iData[29] = (int)l->getBButtonString(); iData[32] = 0x4D8; iData[59] = 0x4D7; }
            else if (f) { iData[28] = (int)f->getAButtonString(); iData[29] = (int)f->getBButtonString(); }
            else if (sk) { iData[28] = (int)sk->getAButtonString(); iData[29] = (int)sk->getBButtonString(); }
            else if (ns) { iData[28] = (int)ns->getAButtonString(); iData[29] = (int)ns->getBButtonString(); }
        }
else if (winStatus == 1 || winStatus == 2) {
            dMenu_Ring_c* ring = mw->getMenuRing();
            if (ring) {
                if (ring->isMixItemOff()) iData[32] = 0x90; // "Combine"
                else if (ring->isMixItemOn()) iData[32] = 0x91; // "Separate"
                iData[28] = (int)ring->getDoStatus();
                iData[60] = (int)ring->getStatus() + 1; iData[61] = (int)ring->getCurrentSlot(); iData[62] = (int)ring->getItemsTotal();
                for (int s = 0; s < 24; s++) {
                    u8 slotIdx = ring->getItem(s, 0);
                    if (slotIdx != 0xFF && slotIdx < 24) {
                        u8 comboItem = dComIfGs_getItem(slotIdx, true);
                        u8 baseItem = dComIfGs_getItem(slotIdx, false);

                        if (comboItem == 0x59 && baseItem != 0x43) {
                            iData[63 + s] = 0x59; // Combined Bomb slot -> show "Bow & Arrow Combo"
                        } else {
                            iData[63 + s] = baseItem; // Use base item (Bow) to keep wheel entry consistent
                        }

                        int count = ring->getMenuRingItemNum(slotIdx);
                        if (comboItem == 0x59) { // But calculate ammo for the combo
                            int arrows = (int)dComIfGs_getArrowNum();
                            count = std::min(arrows, count);
                        }
                        iData[87 + s] = count;
                    } else {
                        iData[63 + s] = 0xFF;
                        iData[87 + s] = 0;
                    }
                }
                dMenu_ItemExplain_c* explain = ring->getItemExplain();
                if (explain && explain->getStatus() != 0) {
                    u8 slotIdx = ring->getItem(ring->getCurrentSlot(), 0);
                    u8 currentItemNo = (slotIdx != 0xFF) ? dComIfGs_getItem(slotIdx, false) : 0xFF;

                    uint16_t baseDescID = find_item_long_desc_id(currentItemNo);
                    if (baseDescID == 0xFFFF) baseDescID = explain->getDescMsgID();

                    char tBuf[256]; dMeter2Info_getString(explain->getNameMsgID(), tBuf, NULL);
                    itemTitle = clean_tp_string(tBuf);
                    itemDesc = get_full_multi_line_desc(baseDescID, currentItemNo);
                }
            }
        }
    }

    iData[0] = dComIfGs_getLife(); iData[1] = dComIfGs_getMaxLife(); iData[8] = dComIfGs_getRupee(); iData[9] = dComIfGs_getKeyNum() + dComIfGp_getItemKeyNumCount();
    iData[10] = dComIfGs_getArrowNum(); iData[11] = dComIfGs_getBombNum(0); iData[13] = stayNo;
    iData[17] = dComIfGp_getSelectItem(0); iData[18] = dComIfGp_getSelectItem(1);
    iData[19] = dComIfGp_getSelectItemNum(0); iData[20] = dComIfGp_getSelectItemNum(1);
    iData[41] = dComIfGs_isDungeonItemBossKey() ? 1 : 0;
    iData[47] = dStage_stagInfo_GetSTType(dComIfGp_getStage()->getStagInfo()) == ST_DUNGEON;
    iData[48] = dComIfGs_isDungeonItemMap() ? 1 : 0; iData[49] = dComIfGs_isDungeonItemCompass() ? 1 : 0;
    iData[100] = dComIfGs_getArrowMax();
    iData[101] = dComIfGs_getBombMax(0x70); // dItemNo_NORMAL_BOMB_e
    iData[102] = dComIfGs_getBombMax(0x71); // dItemNo_WATER_BOMB_e
    iData[103] = dComIfGs_getBombMax(0x72); // dItemNo_POKE_BOMB_e
    iData[104] = getSettings().game.dialogOnSecondScreen.getValue() ? 1 : 0;

    // Horse & Riding State (Spur Fix)
    iData[42] = dMeter2Info_getHorseLifeCount();
    int stateFlags = 0;
    auto* player = dComIfGp_getLinkPlayer();
    if (player) {
        if (player->checkHorseRide()) stateFlags |= 4;
        if (player->checkPlayerFly()) stateFlags |= 32;
    }
    iData[31] = stateFlags;

    for (int k = 50; k <= 56; k++) iData[k] = -1;
    u32 bCount = 0; PADButtonMapping* pbm = PADGetButtonMappings(0, &bCount);
    if (pbm) for (u32 j = 0; j < bCount; j++) {
        switch (pbm[j].padButton) { case PAD_BUTTON_A: iData[50] = pbm[j].nativeButton; break; case PAD_BUTTON_B: iData[51] = pbm[j].nativeButton; break; case PAD_BUTTON_X: iData[52] = pbm[j].nativeButton; break; case PAD_BUTTON_Y: iData[53] = pbm[j].nativeButton; break; case PAD_TRIGGER_Z: iData[54] = pbm[j].nativeButton; break; }
    }

    Vec pPos = dMapInfo_n::getMapPlayerPos(); const char* sName = dComIfGp_getStartStageName(); std::string fName = sName ? sName : "Unknown";
    if (sName) { for (const auto& reg : gameRegions) { for (const auto& ma : reg.maps) { if (strcmp(ma.mapFile, sName) == 0) { fName = ma.mapName; goto f_ok; } } } }
    f_ok:; s8 floor = dMapInfo_c::getNowStayFloorNoDecisionFlg() ? dMapInfo_c::getNowStayFloorNo() : dMapInfo_c::calcFloorNo(pPos.y, true, stayNo);
    float roomMinX, roomMinZ, roomMaxX, roomMaxZ; dMapInfo_n::getRoomMinMaxXZ(stayNo, &roomMinX, &roomMinZ, &roomMaxX, &roomMaxZ);
    float fData[14] = { pPos.x, pPos.z, (float)dMapInfo_n::getMapPlayerAngleY() * (180.0f / 32768.0f), 0,0,0,0, dMapInfo_n::getMapRestartPos().x, dMapInfo_n::getMapRestartPos().z, (float)dMapInfo_n::getMapRestartAngleY() * (180.0f / 32768.0f), roomMinX, roomMinZ, roomMaxX, roomMaxZ };
    std::vector<float> lines, icons, doors; float miX=1e10f, miZ=1e10f, maX=-1e10f, maZ=-1e10f;
    if (dMpath_c::mLayerList) for (int r = 0; r < 64; r++) {
        if (!is_room_visible(r, sName, stayNo, iData[48])) continue;
        for (int l = 0; l < 2; l++) {
            auto* rm = dMpath_c::getRoomPointer(l, r); if (!rm || !rm->mpFloatData) continue;
            for (int f = 0; f < rm->mFloorNum; f++) {
                if (sName && sName[0] != 'F' && rm->mpFloor[f].mFloorNo != floor) continue;
                for (int g = 0; g < rm->mpFloor[f].mGroupNum; g++) {
                    auto& grp = rm->mpFloor[f].mpGroup[g]; if (!should_draw_geometry_group(grp, r)) continue;
                    for (int ln = 0; ln < grp.mLineNum; ln++) {
                        if (grp.mpLine[ln].field_0x0 & 0x40) continue;
                        for (int i = 0; i < grp.mpLine[ln].mDataNum; i++) {
                            float px = rm->mpFloatData[grp.mpLine[ln].mpData[i]*2], pz = rm->mpFloatData[grp.mpLine[ln].mpData[i]*2+1];
                            lines.push_back(px); lines.push_back(pz); miX=std::min(miX,px); maX=std::max(maX,px); miZ=std::min(miZ,pz); maZ=std::max(maZ,pz);
                        }
                        lines.push_back(std::numeric_limits<float>::quiet_NaN()); lines.push_back((float)grp.mpLine[ln].field_0x0); lines.push_back((float)grp.mpLine[ln].field_0x1); lines.push_back(0);
                    }
                    for (int pn = 0; pn < grp.mPolyNum; pn++) {
                        if (grp.mpPoly[pn].field_0x0 & 0x40) continue;
                        for (int i = 0; i < grp.mpPoly[pn].mDataNum; i++) {
                            float px = rm->mpFloatData[grp.mpPoly[pn].mpData[i]*2], pz = rm->mpFloatData[grp.mpPoly[pn].mpData[i]*2+1];
                            lines.push_back(px); lines.push_back(pz); miX=std::min(miX,px); maX=std::max(maX,px); miZ=std::min(miZ,pz); maZ=std::max(maZ,pz);
                        }
                        lines.push_back(std::numeric_limits<float>::quiet_NaN()); lines.push_back((float)grp.mpPoly[pn].field_0x0); lines.push_back(1001.0f); lines.push_back(0);
                    }
                }
            }
        }
    }
    fData[3]=miX; fData[4]=miZ; fData[5]=maX; fData[6]=maZ;
    for (int g = 0; g < 17; g++) { for (auto* d = dTres_c::getFirstData(g); d; d = dTres_c::getNextData(d)) { if (should_draw_icon(g, d, stayNo, floor, sName)) { icons.push_back((float)g); icons.push_back(d->mPos.x); icons.push_back(d->mPos.z); icons.push_back((float)d->mRoomNo); } } }
    auto ad = [&](dStage_KeepDoorInfo* in) { if (!in) return; for (int i = 0; i < in->mNum; i++) { auto& dr = in->mDrTgData[i]; int r = (dr.base.parameters >> 24) & 0x3F; if (dMapInfo_c::calcFloorNo(dr.base.position.y, true, r) != floor) continue; if (is_room_visible(r, sName, stayNo, iData[48])) { doors.push_back(dr.base.position.x); doors.push_back(dr.base.position.z); doors.push_back((float)dr.base.angle.y * (180.0f / 32768.0f)); doors.push_back(0); } } };
    ad(dStage_GetKeepDoorInfo()); ad(dStage_GetRoomKeepDoorInfo());

    jstring jS = env->NewStringUTF(fName.c_str()); jstring jT = env->NewStringUTF(itemTitle.c_str()); jstring jDe = env->NewStringUTF(itemDesc.c_str()); jstring jDT = env->NewStringUTF(dialogText.c_str());
    jintArray jInts = env->NewIntArray(120); env->SetIntArrayRegion(jInts, 0, 120, iData); jfloatArray jF = env->NewFloatArray(14); env->SetFloatArrayRegion(jF, 0, 14, fData);
    jfloatArray jL = env->NewFloatArray(lines.size()); env->SetFloatArrayRegion(jL, 0, lines.size(), lines.data());
    jfloatArray jI = env->NewFloatArray(icons.size()); env->SetFloatArrayRegion(jI, 0, icons.size(), icons.data());
    jfloatArray jD = env->NewFloatArray(doors.size()); env->SetFloatArrayRegion(jD, 0, doors.size(), doors.data());
    env->CallVoidMethod(activity, s_onGameStateUpdate, jInts, jF, jS, jT, jDe, jDT, jL, jI, jD);
    env->DeleteLocalRef(jInts); env->DeleteLocalRef(jF); env->DeleteLocalRef(jS); env->DeleteLocalRef(jT); env->DeleteLocalRef(jDe); env->DeleteLocalRef(jDT); env->DeleteLocalRef(jL); env->DeleteLocalRef(jI); env->DeleteLocalRef(jD); env->DeleteLocalRef(activity);
}
} // namespace dusk::android
#else
namespace dusk::android { void hud_update() {} bool hud_is_second_screen_active() { return false; } }
#endif
