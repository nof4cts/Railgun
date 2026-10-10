package com.firestar.santoryu.common;

/**
 * Every technique, with the beat sheet both sides play from. Ticks are counted from the cast; the server lands damage
 * on these ticks and the client lands the blade, sound, camera cut and impact frames on the same ones.
 */
public enum Move {
    ONI_GIRI("oni_giri", "Oni Giri", "鬼斬り", "THREE SWORD STYLE", Need.THREE, 6, false, 26, 0xFF4040),
    TATSU_MAKI("tatsu_maki", "Tatsu Maki", "竜巻き", "THREE SWORD STYLE", Need.THREE, 10, false, 46, 0xD8F0FF),
    POUND_HO("pound_ho", "Sanjuroku Pound Ho", "三十六煩悩鳳", "TWO SWORD STYLE", Need.TWO, 5, false, 34, 0xBFE8FF),
    SHISHI_SONSON("shishi_sonson", "Shishi Sonson", "獅子歌歌", "ONE SWORD STYLE  ·  IAI", Need.ONE, 14, true, 74, 0xFFFFFF),
    HYAKUHACHI("hyakuhachi", "Hyakuhachi Pound Ho", "百八煩悩鳳", "THREE SWORD STYLE", Need.THREE, 20, true, 84, 0xA8DCFF),
    KOKUJO("kokujo", "Kokujo: O Tatsu Maki", "黒縄・大竜巻", "THREE SWORD STYLE  ·  HAKI", Need.THREE, 30, true, 104, 0x7A2CFF),
    SANZEN_SEKAI("sanzen_sekai", "Sanzen Sekai", "三千世界", "THREE SWORD STYLE  ·  SECRET ART", Need.THREE, 40, true, 134, 0xFFE2A0),
    ASURA("asura", "Asura: Ichibugin", "阿修羅 弌霧銀", "NINE SWORD STYLE", Need.THREE, 60, true, 214, 0xFF2A1A),
    RENGOKU("rengoku", "Rengoku Oni Giri", "煉獄鬼斬り", "KING OF HELL THREE SWORD STYLE", Need.ENMA, 45, true, 174, 0xFF3010),
    DRAGON("dragon", "103 Mercies Dragon Damnation", "閻王三刀流", "KING OF HELL  ·  THREE SWORD SERPENT", Need.ENMA, 120, true, 274, 0x9B3BFF);

    public final String key;
    public final String title;
    public final String kanji;
    public final String style;
    public final Need need;
    public final int cooldownSeconds;
    public final boolean cinematic;
    /** Total length in ticks; the cinematic hands the camera back over the last {@link Beats#BLEND_OUT} of them. */
    public final int length;
    public final int color;

    Move(String key, String title, String kanji, String style, Need need, int cooldownSeconds, boolean cinematic, int length, int color) {
        this.key = key;
        this.title = title;
        this.kanji = kanji;
        this.style = style;
        this.need = need;
        this.cooldownSeconds = cooldownSeconds;
        this.cinematic = cinematic;
        this.length = length;
        this.color = color;
    }

    public static Move byId(int id) {
        Move[] all = values();
        return id >= 0 && id < all.length ? all[id] : ONI_GIRI;
    }

    /** Ticks during which the swordsman is held in place for the cinematic (the strike frees him). */
    public int anchorUntil() {
        return switch (this) {
            case ONI_GIRI -> Beats.ONI_DASH;
            case TATSU_MAKI, POUND_HO -> 0;
            case SHISHI_SONSON -> Beats.SHISHI_VANISH;
            case HYAKUHACHI -> Beats.HYAKU_FIRE;
            case KOKUJO -> Beats.KOKUJO_FORM;
            case SANZEN_SEKAI -> Beats.SANZEN_DASH;
            case ASURA -> Beats.ASURA_DASH;
            case RENGOKU -> Beats.RENGOKU_DASH;
            case DRAGON -> Beats.DRAGON_LEAP;
        };
    }
}
