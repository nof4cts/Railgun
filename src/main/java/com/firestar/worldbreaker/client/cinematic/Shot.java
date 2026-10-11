package com.firestar.worldbreaker.client.cinematic;

/**
 * One shot of a film: active from {@code start} to {@code end} ticks; {@code blend} > 0 sweeps in from the previous
 * shot over that many ticks, 0 is a hard cut. The path gets k = 0..1 across the shot, eased in and out.
 */
public record Shot(int start, int end, int blend, Path path) {
    @FunctionalInterface
    public interface Path {
        CameraPose pose(float k, float t);
    }

    public static Shot cut(int start, int end, Path path) {
        return new Shot(start, end, 0, path);
    }

    public static Shot sweep(int start, int end, int blend, Path path) {
        return new Shot(start, end, blend, path);
    }
}
