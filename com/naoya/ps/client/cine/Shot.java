package com.naoya.ps.client.cine;

public record Shot(int start, int end, int blend, Shot.Path path) {
   public static Shot cut(int start, int end, Shot.Path path) {
      return new Shot(start, end, 0, path);
   }

   public static Shot sweep(int start, int end, int blend, Shot.Path path) {
      return new Shot(start, end, blend, path);
   }

   @FunctionalInterface
   public interface Path {
      CameraPose pose(float var1, float var2);
   }
}
