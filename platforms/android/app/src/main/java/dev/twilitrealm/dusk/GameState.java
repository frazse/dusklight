package dev.twilitrealm.dusk;

public class GameState {
    public final int health;
    public final int maxHealth;
    public final int magic;
    public final int maxMagic;
    public final int oil;
    public final int maxOil;
    public final int oxygen;
    public final int maxOxygen;
    public final int rupees;
    public final int keys;
    public final int arrows;
    public final int bombs;
    public final int transform;
    public final float mapX;
    public final float mapY;
    public final float mapAngle;
    public final String stageName;
    public final int roomNo;
    public final float[] mapLines;
    public final float[] mapIcons;
    
    public final float mapMinX;
    public final float mapMinZ;
    public final float mapMaxX;
    public final float mapMaxZ;

    public final String buttonAText;
    public final String buttonBText;
    public final String buttonZText;
    public final boolean midnaCalling;
    public final String buttonLText;
    public final String buttonXText;
    public final String buttonYText;
    
    public final int itemXResId;
    public final int itemYResId;
    public final int itemXCount;
    public final int itemYCount;

    public final int lightDrops;
    public final int maxLightDrops;
    public final boolean showLightDrops;

    public final String dPadText;
    public final int dPadDirection;
    public final int itemDDownId;
    public final int itemDDownCount;

    public GameState(int health, int maxHealth, int magic, int maxMagic,
                     int oil, int maxOil, int oxygen, int maxOxygen,
                     int rupees, int keys, int arrows, int bombs,
                     int lightDrops, int maxLightDrops, boolean showLightDrops,
                     float mapX, float mapY, int transform, 
                     String stageName, int roomNo, float[] mapLines,
                     float[] mapIcons, float mapAngle,
                     float mapMinX, float mapMinZ, float mapMaxX, float mapMaxZ,
                     String buttonAText, String buttonBText, String buttonZText, boolean midnaCalling,
                     String buttonLText, String buttonXText, String buttonYText,
                     int itemXResId, int itemYResId, int itemXCount, int itemYCount,
                     String dPadText, int dPadDirection, int itemDDownId, int itemDDownCount) {
        this.health = health;
        this.maxHealth = maxHealth;
        this.magic = magic;
        this.maxMagic = maxMagic;
        this.oil = oil;
        this.maxOil = maxOil;
        this.oxygen = oxygen;
        this.maxOxygen = maxOxygen;
        this.rupees = rupees;
        this.keys = keys;
        this.arrows = arrows;
        this.bombs = bombs;
        this.lightDrops = lightDrops;
        this.maxLightDrops = maxLightDrops;
        this.showLightDrops = showLightDrops;
        this.mapX = mapX;
        this.mapY = mapY;
        this.mapAngle = mapAngle;
        this.transform = transform;
        this.stageName = stageName;
        this.roomNo = roomNo;
        this.mapLines = mapLines;
        this.mapIcons = mapIcons;
        this.mapMinX = mapMinX;
        this.mapMinZ = mapMinZ;
        this.mapMaxX = mapMaxX;
        this.mapMaxZ = mapMaxZ;
        this.buttonAText = buttonAText;
        this.buttonBText = buttonBText;
        this.buttonZText = buttonZText;
        this.midnaCalling = midnaCalling;
        this.buttonLText = buttonLText;
        this.buttonXText = buttonXText;
        this.buttonYText = buttonYText;
        this.itemXResId = itemXResId;
        this.itemYResId = itemYResId;
        this.itemXCount = itemXCount;
        this.itemYCount = itemYCount;
        this.dPadText = dPadText;
        this.dPadDirection = dPadDirection;
        this.itemDDownId = itemDDownId;
        this.itemDDownCount = itemDDownCount;
    }
}
