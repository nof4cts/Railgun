package com.yuji;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Modulo Yuji, a sorcerer of the JJK kit. Shared constants and the settings file (config/jjk-yuji.properties),
 * which is created with defaults on first run and re-read whenever a world starts.
 */
public final class Y {
    public static final Logger LOG = LogUtils.getLogger();
    public static final String NS = "yuji";

    private Y() {}

    public static ResourceLocation id(String path) {
        return new ResourceLocation(NS, path);
    }

    // ------------------------------------------------------------------ settings
    // damage (server)
    public static double dmgSignature = 2000, dmgHeavy = 8000, dmgUltimate = 60000, dmgFinal = 1.0e6;
    public static double bossMultiplier = 1.0, damageCap = 0, knockback = 1.0;
    public static boolean resetInvulnerability = true;
    // destruction (server)
    public static String destruction = "PERMANENT";      // PERMANENT, VISUAL (debris only, the world is untouched) or OFF
    public static boolean unrestricted = false;           // also bedrock and other unbreakable blocks
    public static double destructionScale = 1.0;
    public static int maxRadius = 400, worldEnderRadius = 160, blocksPerTick = 4000, restoreLimit = 600000;
    public static int msPerTick = 6;
    public static int splitterLength = 320, dismantleLength = 80;
    // domain, cooldowns, energy (server)
    public static int domainRadius = 48, domainSeconds = 30, overdriveSeconds = 0;
    public static double cooldownScale = 1.0, energyScale = 1.0;
    // presentation (client)
    public static double shake = 1.0, flash = 1.0, motion = 1.0, slashVisibility = 0.6;
    public static boolean impactFrames = true, hitStop = true, cutscenes = true;

    private static final String[][] DOC = {
            {"damage.signature", "Damage of signature moves (one-shots ordinary mobs)."},
            {"damage.heavy", "Damage of heavy moves."},
            {"damage.ultimate", "Damage of ultimates."},
            {"damage.final", "Damage of the final ultimates (World Ender, Domain Apocalypse)."},
            {"damage.bossMultiplier", "Multiplier on damage against bosses (Wither, Ender Dragon, modded bosses)."},
            {"damage.cap", "Largest single hit (0 = no cap)."},
            {"damage.knockback", "Knockback strength multiplier."},
            {"damage.resetInvulnerability", "Ignore the target's invulnerability frames so every scripted hit lands."},
            {"destruction.mode", "PERMANENT (blocks are really destroyed), VISUAL (debris and effects only; the world is untouched) or OFF."},
            {"destruction.unrestricted", "WARNING: also destroys bedrock, command blocks and other unbreakable blocks."},
            {"destruction.scale", "Multiplier on every destruction radius."},
            {"destruction.maxRadius", "Largest radius any single destruction may have."},
            {"destruction.worldEnderRadius", "Radius of the World Ender crater (the Final Judgment)."},
            {"destruction.blocksPerTick", "Most blocks changed per server tick (big craters stream in over a few seconds)."},
            {"destruction.msPerTick", "Most milliseconds of each server tick spent destroying blocks (keeps the game smooth)."},
            {"destruction.restoreLimit", "Blocks remembered for /yuji restore (big World Enders past this can't be undone)."},
            {"destruction.splitterLength", "Length of the World Splitter cut."},
            {"destruction.dismantleLength", "Reach of Dismantle."},
            {"domain.radius", "Radius of the Domain Expansion."},
            {"domain.seconds", "How long the domain lasts."},
            {"overdrive.seconds", "How long Modulo Overdrive lasts (0 = permanent: until he dies or picks another technique)."},
            {"cooldown.scale", "Multiplier on every cooldown (0 = none)."},
            {"energy.scale", "Multiplier on cursed energy costs (0 = free)."},
            {"fx.shake", "Camera shake (0 = off)."},
            {"fx.flash", "Flash brightness (0 = off)."},
            {"fx.motion", "Motion intensity of camera moves and smears (0..1)."},
            {"fx.slashVisibility", "Dismantle visibility: 0 = a faint distortion, 1 = a blazing blade."},
            {"fx.impactFrames", "Impact frames (stark stylised frames on hits). Contains rapid flashing."},
            {"fx.hitStop", "Hit-stop: a few frames of freeze at the moment of contact."},
            {"fx.cutscenes", "Cinematic cameras for the big moves."},
    };

    public static synchronized void load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("jjk-yuji.properties");
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                LOG.warn("[yuji] couldn't read {}", file, e);
            }
        }
        dmgSignature = d(p, "damage.signature", dmgSignature);
        dmgHeavy = d(p, "damage.heavy", dmgHeavy);
        dmgUltimate = d(p, "damage.ultimate", dmgUltimate);
        dmgFinal = d(p, "damage.final", dmgFinal);
        bossMultiplier = d(p, "damage.bossMultiplier", bossMultiplier);
        damageCap = d(p, "damage.cap", damageCap);
        knockback = d(p, "damage.knockback", knockback);
        resetInvulnerability = b(p, "damage.resetInvulnerability", resetInvulnerability);
        destruction = p.getProperty("destruction.mode", destruction).trim().toUpperCase();
        unrestricted = b(p, "destruction.unrestricted", unrestricted);
        destructionScale = d(p, "destruction.scale", destructionScale);
        maxRadius = i(p, "destruction.maxRadius", maxRadius);
        worldEnderRadius = i(p, "destruction.worldEnderRadius", worldEnderRadius);
        blocksPerTick = i(p, "destruction.blocksPerTick", blocksPerTick);
        restoreLimit = i(p, "destruction.restoreLimit", restoreLimit);
        msPerTick = i(p, "destruction.msPerTick", msPerTick);
        // settings files from the first release asked for 24000 blocks a tick, far too many on real terrain
        String ver = p.getProperty("config.version", "1");
        if (ver.equals("1")) blocksPerTick = Math.min(blocksPerTick, 4000);
        if (!ver.equals("3")) overdriveSeconds = 0;   // Overdrive became permanent in settings version 3
        splitterLength = i(p, "destruction.splitterLength", splitterLength);
        dismantleLength = i(p, "destruction.dismantleLength", dismantleLength);
        domainRadius = i(p, "domain.radius", domainRadius);
        domainSeconds = i(p, "domain.seconds", domainSeconds);
        overdriveSeconds = i(p, "overdrive.seconds", overdriveSeconds);
        cooldownScale = d(p, "cooldown.scale", cooldownScale);
        energyScale = d(p, "energy.scale", energyScale);
        shake = d(p, "fx.shake", shake);
        flash = d(p, "fx.flash", flash);
        motion = d(p, "fx.motion", motion);
        slashVisibility = d(p, "fx.slashVisibility", slashVisibility);
        impactFrames = b(p, "fx.impactFrames", impactFrames);
        hitStop = b(p, "fx.hitStop", hitStop);
        cutscenes = b(p, "fx.cutscenes", cutscenes);
        if (!Files.exists(file) || !"3".equals(p.getProperty("config.version"))) save(file);
    }

    private static void save(Path file) {
        StringBuilder sb = new StringBuilder("# Modulo Yuji (JJK kit) settings. Delete this file to restore the defaults.\nconfig.version=3\n");
        Object[] vals = {dmgSignature, dmgHeavy, dmgUltimate, dmgFinal, bossMultiplier, damageCap, knockback, resetInvulnerability,
                destruction, unrestricted, destructionScale, maxRadius, worldEnderRadius, blocksPerTick, msPerTick, restoreLimit,
                splitterLength, dismantleLength, domainRadius, domainSeconds, overdriveSeconds, cooldownScale, energyScale,
                shake, flash, motion, slashVisibility, impactFrames, hitStop, cutscenes};
        for (int k = 0; k < DOC.length; k++) {
            sb.append("\n# ").append(DOC[k][1]).append('\n').append(DOC[k][0]).append('=').append(vals[k]).append('\n');
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warn("[yuji] couldn't write {}", file, e);
        }
    }

    private static double d(Properties p, String k, double def) {
        try {
            return Double.parseDouble(p.getProperty(k, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int i(Properties p, String k, int def) {
        return (int) Math.round(d(p, k, def));
    }

    private static boolean b(Properties p, String k, boolean def) {
        return Boolean.parseBoolean(p.getProperty(k, String.valueOf(def)).trim());
    }

    public static boolean destroys() {
        return "PERMANENT".equals(destruction);
    }

    public static boolean destructionOff() {
        return "OFF".equals(destruction);
    }
}
