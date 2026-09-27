package first.rain.anticheat.util.anticheat.checks;

/** Angular error outside a target-sized box. Zero means yaw and pitch both
 * intersect its approximate visible body. All coordinates are world units. */
public final class AimGeometry {
   private AimGeometry() { }

   public static float outsideBoxDegrees(double eyeX, double eyeY, double eyeZ,
      float yaw, float pitch, double targetX, double targetY, double targetZ,
      double halfWidth, double height) {
      double dx = targetX - eyeX;
      double dz = targetZ - eyeZ;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      if (horizontal < 0.5D || height <= 0.0D) return Float.MAX_VALUE;
      float bearing = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
      float yawSpan = (float)Math.toDegrees(Math.atan2(halfWidth, horizontal));
      float yawError = Math.max(0.0F, Math.abs(wrap(yaw - bearing)) - yawSpan);
      double centerY = targetY + height * 0.55D;
      float targetPitch = (float)-Math.toDegrees(Math.atan2(centerY - eyeY, horizontal));
      float pitchSpan = (float)Math.toDegrees(Math.atan2(height * 0.45D, horizontal));
      float pitchError = Math.max(0.0F, Math.abs(pitch - targetPitch) - pitchSpan);
      return Math.max(yawError, pitchError);
   }

   private static float wrap(float angle) {
      angle %= 360.0F;
      if (angle >= 180.0F) angle -= 360.0F;
      if (angle < -180.0F) angle += 360.0F;
      return angle;
   }
}
