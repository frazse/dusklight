#pragma once
#include <stdint.h>

// Authoritative mapping of Item IDs to their verified BMG Long Description indices
// Derived from zel_00.txt analysis for Twilight Princess.
static inline uint32_t dusk_getItemLongDescMsgID(uint8_t itemNo) {
    switch (itemNo) {
        // Quest Items (0x123B base range)
        case 0x43: return 0x123B; // Hero's Bow
        case 0x44: return 0x123C; // Clawshot
        case 0x45: return 0x123D; // Iron Boots
        case 0x46: return 0x123E; // Dominion Rod
        case 0x47: return 0x123F; // Double Clawshots
        case 0x48: return 0x1240; // Lantern
        case 0x4B: return 0x1243; // Slingshot
        case 0x4C: return 0x1244; // Dominion Rod (Powered)

        // Items with multiple pages (Sequential ID logic will fetch the rest)
        // Slingshot: 1243
        // Bow: 123B

        default: return 0xFFFF; // Fallback to engine-provided ID
    }
}
