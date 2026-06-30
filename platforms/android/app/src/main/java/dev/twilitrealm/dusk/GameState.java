package dev.twilitrealm.dusk;

public class GameState {
    public final int health, maxHealth, magic, maxMagic, oil, maxOil, oxygen, maxOxygen;
    public final int rupees, keys, arrows, bombs, transform, roomNo;
    public final int lightDrops, maxLightDrops, horseSpurs;
    public final boolean showLightDrops, midnaCalling, isRiding, isSwimming, showOxygen, isDungeon;
    
    public final float mapX, mapY, mapAngle;
    public final float mapMinX, mapMinZ, mapMaxX, mapMaxZ;
    public final float restartX, restartY, restartAngle;
    public final float[] mapLines, mapIcons, mapDoors;
    public final boolean showRestart;

    public final String buttonAText, buttonBText, buttonZText, buttonLText, buttonRText, buttonXText, buttonYText;
    public final String labelA, labelB, labelX, labelY, labelZ, labelL, labelR;
    public final String dPadUpText, dPadDownText, dPadLeftText, dPadRightText;
    public final String stageName;

    public final int itemXResId, itemYResId, itemXCount, itemYCount;
    public final int itemDDownId, itemDDownCount, itemDLeftId, itemDLeftCount, itemDRightId, itemDRightCount;

    public GameState(int[] i, float[] f, String stageName, float[] lines, float[] icons, float[] doors) {
        this.health = i[0];     this.maxHealth = i[1];
        this.magic  = i[2];     this.maxMagic  = i[3];
        this.oil    = i[4];     this.maxOil    = i[5];
        this.oxygen = i[6];     this.maxOxygen = i[7];
        this.rupees = i[8];     this.keys      = i[9];
        this.arrows = i[10];    this.bombs     = i[11];
        this.transform = i[12]; this.roomNo    = i[13];
        this.lightDrops = i[14]; this.maxLightDrops = i[15];
        this.showLightDrops = i[16] != 0;
        
        this.itemXResId = i[17];    this.itemYResId = i[18];
        this.itemXCount = i[19];    this.itemYCount = i[20];
        this.itemDDownId = i[21];   this.itemDDownCount = i[22];
        this.itemDLeftId = i[25];   this.itemDLeftCount = i[26];
        this.itemDRightId = i[40];  this.itemDRightCount = i[41];
        
        this.horseSpurs = i[42];
        this.showOxygen = i[43] != 0;
        this.midnaCalling = i[27] != 0;
        this.isDungeon = i[47] != 0;

        int stateFlags = i[31];
        boolean targeting = (stateFlags & 1) != 0;
        this.isSwimming = (stateFlags & 2) != 0;
        this.isRiding = (stateFlags & 4) != 0;

        int vis = i[39];
        this.buttonAText = ((vis & 1) != 0) ? getActionLabel(i[28], isSwimming, isRiding, transform) : "";
        this.buttonBText = ((vis & 2) != 0) ? getActionLabel(i[29], isSwimming, isRiding, transform) : "";
        
        String zText = getActionLabel(i[30], isSwimming, isRiding, transform);
        if (zText.isEmpty()) zText = "Midna";
        this.buttonZText = ((vis & 4) != 0) ? zText : "";

        this.buttonLText = targeting ? "Target" : "";
        this.buttonRText = ((vis & 8) != 0) ? getActionLabel(i[32], isSwimming, isRiding, transform) : "";
        this.buttonXText = ((vis & 16) != 0) ? getActionLabel(i[33], isSwimming, isRiding, transform) : "";
        this.buttonYText = ((vis & 32) != 0) ? getActionLabel(i[34], isSwimming, isRiding, transform) : "";

        this.dPadUpText    = getActionLabel(i[35], isSwimming, isRiding, transform);
        this.dPadDownText  = getActionLabel(i[36], isSwimming, isRiding, transform);
        this.dPadLeftText  = getActionLabel(i[37], isSwimming, isRiding, transform);
        this.dPadRightText = getActionLabel(i[38], isSwimming, isRiding, transform);

        this.mapX = f[0]; this.mapY = f[1]; this.mapAngle = f[2];
        this.mapMinX = f[3]; this.mapMinZ = f[4]; this.mapMaxX = f[5]; this.mapMaxZ = f[6];
        this.restartX = f[7]; this.restartY = f[8]; this.restartAngle = f[9];
        this.showRestart = i[46] != 0;

        // Physical Label Resolution
        this.labelA = getPhysicalName(i[50], "A");
        this.labelB = getPhysicalName(i[51], "B");
        this.labelX = getPhysicalName(i[52], "X");
        this.labelY = getPhysicalName(i[53], "Y");
        this.labelZ = getPhysicalName(i[54], "Z");
        this.labelL = getPhysicalName(i[55], "L");
        this.labelR = getPhysicalName(i[56], "R");

        this.stageName = stageName;
        this.mapLines = lines;
        this.mapIcons = icons;
        this.mapDoors = doors;
    }

    private String getPhysicalName(int id, String fallback) {
        if (id == -1) return fallback;
        if (id >= 0x1000) { // Axis/Trigger logic
             int axis = id - 0x1000;
             if (axis == 4) return "L2";
             if (axis == 5) return "R2";
             return fallback;
        }
        // Thor (Nintendo) Layout Button Mapping
        switch(id) {
            case 0: return "B";  // SOUTH (Bottom)
            case 1: return "A";  // EAST (Right)
            case 2: return "Y";  // WEST (Left)
            case 3: return "X";  // NORTH (Top)
            case 4: return "Back";
            case 6: return "Start";
            case 7: return "L3";
            case 8: return "R3";
            case 9: return "L1";
            case 10: return "R1";
            default: return fallback;
        }
    }

    private String getActionLabel(int id, boolean isSwimming, boolean isRiding, int wolfForm) {
        if (id == 0) return "";
        if (wolfForm == 0 && (id == 0x05 || id == 0x0D || id == 0x1F || id == 0x45 || id == 0x46 || id == 0x4E)) return "";
        switch (id) {
            case 0x01: return "Action";
            case 0x02: return "Peek";
            case 0x03: return "Attack";
            case 0x04: return "Put Away";
            case 0x05: return "Howl";
            case 0x06: return "Open";
            case 0x07: return "Enter";
            case 0x08: return "Check";
            case 0x09: // Generic Dash/Roll
                if (isSwimming) return "Dash";
                if (isRiding) return "Hurry";
                if (wolfForm == 1) return "Dash";
                return "Roll";
            case 0x0A: return "Crouch";
            case 0x0B: return "Defend";
            case 0x0C: return "Pick Up";
            case 0x0D: return "Dig";
            case 0x0E: return "Eat";
            case 0x0F: return "Select";
            case 0x10: return "Lock";
            case 0x11: return "Switch";
            case 0x12: return "Back";
            case 0x13: return "Throw";
            case 0x14: return "Place";
            case 0x15: return "Grab";
            case 0x16: return "Get Off";
            case 0x17: return "Get On";
            case 0x18: return "Row";
            case 0x19: return "Jump";
            case 0x1A: return "Read";
            case 0x1B: return "Look";
            case 0x1C: return "Speak";
            case 0x1D: return "Lift";
            case 0x1E: return "Swing";
            case 0x1F: return "Dig";
            case 0x20: return "Jump";
            case 0x22: return "Confirm";
            case 0x23: return "Next";
            case 0x24: return "Info";
            case 0x26: return "Attack";
            case 0x28: return "Whoop";
            case 0x29: return "Zoom";
            case 0x2A: return "Quit";
            case 0x2B: return "Pick";
            case 0x2C: return "Blow";
            case 0x2D: return "Dodge";
            case 0x2E: return "Cut";
            case 0x2F: return "Hint";
            case 0x30: return "Finish";
            case 0x31: return "Set Free";
            case 0x32: return "Dismount";
            case 0x33: return "Let Go";
            case 0x35: return "Take";
            case 0x36: return "Hurry";
            case 0x37: return "Pull Down";
            case 0x38: return "Pet";
            case 0x3A: return "Shield Attack";
            case 0x3B: return "Listen";
            case 0x3C: return "Drink";
            case 0x3E: return "Cover";
            case 0x3F: return "Push";
            case 0x40: return "Resist";
            case 0x41: return "Dive";
            case 0x43: return "Skip";
            case 0x44: return "Slap";
            case 0x45: return "Sniff";
            case 0x46: return "Bite";
            case 0x47: return "Roll";
            case 0x4C: return "Swim";
            case 0x4D: return "Can't Skip";
            case 0x4E: return "Sense";
            case 0x51: return "Land";
            case 0x52: return "Hook";
            case 0x53: return "Change Locks";
            case 0x54: return "Equip";
            case 0x55: return "Pull";
            case 0x56: return "Reel";
            case 0x57: return "Extract";
            case 0x58: return "Spin";
            case 0x5A: return "Spin Attack";
            case 0x5B: return "Reel Fast";
            case 0x5C: return "Rise"; // Fixed typo (was Raise)
            case 0x5D: return "Release";
            case 0x5F: return "Map";
            case 0x60: return "Items";
            case 0x61: return "Insert";
            case 0x62: return "Draw";
            case 0x63: return "Strike";
            case 0x68: return "Change View";
            case 0x6B: return "Chance";
            case 0x6C: return "Scoop";
            case 0x6D: return "Survey";
            case 0x6E: return "Cancel";
            case 0x6F: return "Seize";
            case 0x70: return "Collection";
            case 0x71: return "Area Map";
            case 0x72: return "Action";
            case 0x73: return "Set Hook";
            case 0x74: return "Tilt";
            case 0x75: return "Thrust";
            case 0x76: return "Rotate";
            case 0x77: return "Helm Splitter";
            case 0x78: return "Move";
            case 0x79: return "Roll";
            case 0x7C: return "Help";
            case 0x7D: return "Zoom In";
            case 0x7E: return "Zoom Out";
            default: return "ID: 0x" + Integer.toHexString(id).toUpperCase();
        }
    }
}
