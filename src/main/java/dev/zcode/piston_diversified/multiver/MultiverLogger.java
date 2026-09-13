package dev.zcode.piston_diversified.multiver;

/** Shared logger for the BE tag capture helpers (mirrors the vanilla save/load path). */
public final class MultiverLogger {
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private MultiverLogger() {
    }
}
