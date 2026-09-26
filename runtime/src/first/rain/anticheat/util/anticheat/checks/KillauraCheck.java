package first.rain.anticheat.util.anticheat.checks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import first.rain.anticheat.Rain;
import first.rain.anticheat.config.cfg;
import first.rain.anticheat.util.anticheat.AlertManager;
import first.rain.anticheat.util.anticheat.PlayerEligibility;

/**
 * Unified killaura detection. Merges the old "Killaura B" (attacking while
 * consuming) and "Killaura Aim" (MX-ported rotation heuristics) checks and
 * adds silent-aim components, all feeding one violation pool and one alert
 * type.
 *
 * Silent aim background: the cheat leaves the cheater's own camera untouched
 * and only swaps the yaw/pitch inside outgoing C03/C05/C06 packets. The
 * server believes those packets and rebroadcasts them in entity look packets,
 * so from our observer position the cheater's head visibly snaps onto
 * enemies even though their screen never moved. That sent-rotation stream is
 * what we sample here (byte-quantized to 1.40625 deg, interpolated over up
 * to 3 ticks by the client).
 *
 * Components, all sharing one VL pool (MX flag economy: limit 400, alert,
 * drop to 360, fade ~0.5/combat tick):
 *  - heuristic(aim|constant|sync): AimBasicCheck's windowed robotized
 *    rotation counts, thresholds rescaled to quantization multiples.
 *  - pattern(snap): AimBasicCheck's both-direction big-jump oscillation
 *    streak (multi-target switching, snap-and-return spam).
 *  - silent(snap): a large yaw burst that starts >20 deg off every nearby
 *    player and settles inside someone's hitbox bearing within ~1 quantum.
 *    Ported idea from MX AimFactorCheck's [quiet, huge, quiet] factor
 *    analysis, made target-aware because client-side we know where every
 *    player is. Promoted to alert DIRECTLY on the 3rd qualifying hit
 *    (field-confirmed accurate) instead of waiting for the shared pool.
 *  - silent(return): the mirror burst off the target right after a
 *    silent(snap) hit — the attack-tick-only silent aim signature
 *    (MX AimStatisticsCheck's symmetric zFactor outliers).
 *  - silent(track): yaw stays inside a target's hitbox bearing on almost
 *    every tick where the line of sight to that target is rotating fast
 *    (strafing fights). Humans drift off; lock-on auras don't.
 *  - movement(fix)/movement(lock)/movement(sprint): the body/head desync
 *    that "movement correction" silent aim introduces — the body keeps moving
 *    along the real screen yaw while the packet yaw is snapped to a target.
 *    Velocity must sit a multiple of 45 deg off the broadcast yaw (vanilla
 *    strafe inputs); we test the window-MEAN bucket residual (a fixed cheat's
 *    residual is uniform 0-22.5, mean ~11; legit ~2-4), residual while the
 *    head is pinned inside a target's span, and sprint speed beyond the
 *    +-45 deg sprintable cone (catches bucket-quantized fixes and yaw-locked
 *    orbiting). Same idea as MX pairing rotations and MoveEvent from one
 *    packet stream (RawMovementListener), reframed as a physics invariant.
 *  - consume: swinging at entities mid eat/drink (the old Killaura B).
 *
 * Not portable from MX (needs exact packet floats / known sensitivity, all
 * destroyed by byte quantization + interpolation): GCD/sensitivity checks
 * (AimConstantCheck), exact 0.1/0.01 deltas, exponentially-small pitch
 * (AimInvalidCheck), 1e-4 accel patterns (AimPatternCheck), Shannon entropy
 * comparisons (AimComplexCheck), jolt duplicate statistics
 * (AimInconsistentCheck), and the ML modules.
 */
public class KillauraCheck {
   /** MX gates aim analysis to 3500ms after an attack; 70 ticks ~ 3.5s. */
   private static final long COMBAT_WINDOW_TICKS = 70L;
   /** Out of combat this long -> per-fight counters reset. */
   private static final long SESSION_RESET_TICKS = 140L;
   /** AimBasicCheck analyzes windows of 10 non-zero rotations. */
   private static final int WINDOW_SIZE = 10;
   /** Quantization step of observed remote rotations (360 / 256). */
   private static final float QUANTUM = 1.40625F;
   /** Shared flag economy (AimBasicCheck localVl_limit / fade per window ~ 0.5 per tick). */
   private static final float VL_LIMIT = 400.0F;
   private static final float VL_FADE_PER_TICK = 0.5F;

   // consume component (old Killaura B)
   private static final int EAT_TIMEOUT = 33;
   private static final int MIN_USE_TIME = 6;
   private static final int CONSUME_FAIL_VL = 8;

   // silent(snap) burst machine. A one-packet snap reaches us as up to ~3
   // interpolated steps of >= delta/3 each, then converges exactly (the
   // interpolation divisor counts down 3,2,1), so bursts settle on the true
   // sent yaw within a few ticks.
   private static final float BURST_STEP_MIN = 7.0F;
   private static final float BURST_QUIET = 2.5F;
   private static final int BURST_MAX_TICKS = 7;
   private static final float BURST_SUM_MIN = 20.0F;
   private static final float SNAP_PRE_ERROR_MIN = 20.0F;
   private static final int SNAP_MIN_HITS = 3;
   private static final float SNAP_VL = 90.0F;
   private static final float RETURN_VL = 55.0F;
   private static final long RETURN_PAIR_TICKS = 8L;

   // silent(track)
   private static final int TRACK_WINDOW = 24;
   private static final float TRACK_RATIO = 0.85F;
   private static final float TRACK_LOS_MIN = 2.5F;
   private static final float TRACK_LOS_MAX = 45.0F;
   private static final double TRACK_MIN_DIST = 2.2D;
   private static final float TRACK_VL = 80.0F;

   // movement(fix) desync. In vanilla, ground movement comes from
   // moveFlying(strafe, forward) rotated by the yaw the client is SENDING, so
   // on flat ground with smooth velocity the velocity bearing sits on a
   // multiple of 45 deg off the broadcast yaw (W=0, WA=45, A=90, ...). Silent
   // aim with "movement fix" recomputes strafe inputs against the real screen
   // yaw, so measured against the broadcast yaw the offset drifts arbitrarily.
   // Its residual to the nearest 45-deg bucket is then ~uniform over 0..22.5
   // (mean 11.25), while legit residual stays near 0 — so the separator is the
   // window MEAN, not big single residuals. Sampling is gated hard: flat
   // ground (2 ticks |dy|~0), no hurtTime (knockback), not on ice (momentum
   // ignores yaw there), speed in the run band, and for the residual tests a
   // tight accel bound, because ground momentum lags a fast-turning legit yaw
   // by ~1.5 ticks and fakes residual during real tracking.
   private static final double MOVE_MIN_SPEED = 0.15D;
   private static final double MOVE_MAX_SPEED = 0.45D;
   private static final double MOVE_FLAT_DY = 0.001D;
   private static final double MOVE_SMOOTH_ACCEL = 0.022D;
   private static final int MOVE_WINDOW = 12;
   private static final float MOVE_MEAN_LIMIT = 7.5F;
   private static final float MOVE_DESYNC_RESIDUAL = 8.0F;
   private static final float MOVE_VL = 70.0F;
   // residual while the head is pinned inside a target's hitbox span — the
   // unambiguous lock+fix fingerprint, needs only a few ticks
   private static final float LOCK_RESIDUAL = 13.0F;
   private static final int LOCK_HITS = 3;
   private static final float MOVE_LOCK_VL = 85.0F;
   // sprint-direction leak: no vanilla input sprints beyond +-45 off the yaw,
   // so sprint speed at a wider offset is impossible even for bucket-quantized
   // movement fixes (e.g. orbiting a victim while yaw-locked = offset ~90).
   // 62 = 45 legal bucket + ~15 momentum lag + slack.
   private static final double SPRINT_ACCEL = 0.08D;
   private static final double SPRINT_MIN_SPEED = 0.25D;
   private static final float SPRINT_OFFSET = 62.0F;
   private static final int SPRINT_HITS = 4;
   private static final float MOVE_SPRINT_VL = 85.0F;
   /** Combat ticks between -1 decay of the lock/sprint hit counters. */
   private static final int MOVE_DECAY_TICKS = 40;

   /** Aura target search radius around the attacker (blocks). */
   private static final double TARGET_RANGE_SQ = 36.0D;
   /** Half hitbox width (0.3) plus margin, for bearing-span tests. */
   private static final double HITBOX_HALF_WIDTH = 0.4D;
   /** Ticks of position history kept per player for lag/interpolation slack. */
   private static final int TRAIL_LEN = 5;

   private final Map<UUID, State> states = new HashMap<UUID, State>();
   private final Map<UUID, Trail> trails = new HashMap<UUID, Trail>();
   private boolean failedKillaura = false;

   private static final class State {
      // rotation stream
      float lastYaw;
      float lastPitch;
      boolean hasRotation;
      // combat gate
      long lastSwingTick = Long.MIN_VALUE;
      // shared flag economy
      float aimVl;
      // heuristic window
      final List<Float> yawChangeWindow = new ArrayList<Float>();
      int snapStreak;
      // silent(snap) burst machine
      int burstTicks;       // 0 = idle, -1 = invalidated (sustained turn), >0 = in burst
      float burstSum;
      float burstDir;
      float preBurstYaw;
      int quietTicks;
      int snapHits;
      int snapMisses;
      long lastSnapHitTick = Long.MIN_VALUE;
      // silent(track)
      UUID lastTargetId;
      float lastBearing = Float.NaN;
      int trackSamples;
      int trackTicks;
      // movement(fix) desync
      double lastVelX;
      double lastVelZ;
      double lastMoveY;
      boolean hasVel;
      int moveSamples;
      int moveDesyncTicks;
      float residualSum;
      int lockDesync;
      int sprintDesync;
      int moveTickCounter;
      // consume component
      int useItemTicks;
      long lastEatTick;
      int consumeVl;
   }

   /** Short per-player position history, newest entry at index 0. */
   private static final class Trail {
      final double[] x = new double[TRAIL_LEN];
      final double[] z = new double[TRAIL_LEN];
      long lastTick = Long.MIN_VALUE;
      int size;

      void push(double px, double pz, long tick) {
         if (tick == this.lastTick && this.size > 0) {
            return;
         }
         System.arraycopy(this.x, 0, this.x, 1, TRAIL_LEN - 1);
         System.arraycopy(this.z, 0, this.z, 1, TRAIL_LEN - 1);
         this.x[0] = px;
         this.z[0] = pz;
         this.lastTick = tick;
         if (this.size < TRAIL_LEN) {
            ++this.size;
         }
      }
   }

   public void anticheatCheck(EntityPlayer player) {
      if (!cfg.v.detectKillaura) {
         return;
      }
      Minecraft mc = Minecraft.func_71410_x();
      if (mc.field_71441_e == null || !PlayerEligibility.shouldCheckPlayer(player)) {
         this.forgetPlayer(player == null ? null : player.func_110124_au());
         return;
      }

      UUID uuid = player.func_110124_au();
      long tick = mc.field_71441_e.func_82737_E();
      State st = this.states.computeIfAbsent(uuid, (k) -> new State());

      // Keep position history fresh for this player and the observer — both
      // are bearing candidates when someone else is the attacker.
      this.trail(uuid).push(player.field_70165_t, player.field_70161_v, tick);
      this.trail(mc.field_71439_g.func_110124_au())
         .push(mc.field_71439_g.field_70165_t, mc.field_71439_g.field_70161_v, tick);

      if (player.field_70154_o != null) { // riding: vehicle rotations/consume timing are unreliable
         return;
      }

      this.consumeComponent(player, st, tick);

      if (player.field_82175_bq) { // isSwingInProgress — the combat signal we can observe
         st.lastSwingTick = tick;
      }

      float yaw = player.field_70177_z;
      float pitch = player.field_70125_A;
      if (!st.hasRotation) {
         st.lastYaw = yaw;
         st.lastPitch = pitch;
         st.hasRotation = true;
         return;
      }
      float prevYaw = st.lastYaw;
      float yawChange = wrapDegrees(yaw - st.lastYaw);
      float pitchChange = wrapDegrees(pitch - st.lastPitch);
      st.lastYaw = yaw;
      st.lastPitch = pitch;

      // Teleport/lag guard: a large position step also snaps observed rotation,
      // which would poison every rotation component with a false "snap".
      double moveX = player.field_70165_t - player.field_70142_S;
      double moveY = player.field_70163_u - player.field_70137_T;
      double moveZ = player.field_70161_v - player.field_70136_U;
      if (moveX * moveX + moveZ * moveZ > 25.0D) {
         st.yawChangeWindow.clear();
         this.resetBurst(st);
         st.lastBearing = Float.NaN;
         st.lastTargetId = null;
         st.hasVel = false;
         st.moveSamples = 0;
         st.moveDesyncTicks = 0;
         st.residualSum = 0.0F;
         return;
      }

      // AimHeuristicCheck.event(): rotations outside the attack window are ignored
      if (st.lastSwingTick == Long.MIN_VALUE || tick < st.lastSwingTick
         || tick - st.lastSwingTick > COMBAT_WINDOW_TICKS) {
         if (st.lastSwingTick != Long.MIN_VALUE && tick - st.lastSwingTick > SESSION_RESET_TICKS) {
            this.resetSession(st);
         }
         return;
      }

      // component: windowed rotation heuristics (AimBasicCheck port)
      float absYawChange = Math.abs(yawChange);
      float absPitchChange = Math.abs(pitchChange);
      if (absYawChange != 0.0F || absPitchChange != 0.0F) { // MX components skip zero-delta events
         st.yawChangeWindow.add(absYawChange);
         if (st.yawChangeWindow.size() >= WINDOW_SIZE) {
            this.analyzeWindow(player, st, st.yawChangeWindow);
            st.yawChangeWindow.clear();
         }
      }

      // geometry components share one candidate scan per tick
      List<EntityPlayer> targets = this.targetsNear(mc, player, tick);
      this.burstMachine(player, st, tick, yawChange, prevYaw, targets);
      this.trackComponent(player, st, yaw, targets);
      this.movementComponent(mc, player, st, moveX, moveY, moveZ, yaw, targets);

      // shared flag economy
      if (st.aimVl > VL_LIMIT) {
         this.failedKillaura = true;
         AlertManager.flag(player, AlertManager.CheckType.KILLAURA, (int)(st.aimVl / 10.0F));
         st.aimVl = 360.0F;
      }
      if (st.aimVl > 0.0F) {
         st.aimVl = Math.max(0.0F, st.aimVl - VL_FADE_PER_TICK);
      }
   }

   /**
    * Port of AimBasicCheck.checkDefaultAim(): counts how often yaw changes in
    * the window are simultaneously large and nearly identical to the first
    * change ("robotized"), plus the both-direction big-jump snap pattern.
    * Tolerances rescaled from MX's raw-packet floats to quantization steps.
    */
   private void analyzeWindow(EntityPlayer player, State st, List<Float> window) {
      float yawChangeFirst = window.get(0);
      float oldYawChange = yawChangeFirst;
      int machineKnownMovement = 0;
      int constantRotations = 0;
      int robotizedAmount = 0;
      int bigSwingUp = 0;
      int bigSwingDown = 0;

      for (float yawChange : window) {
         float robotized = Math.abs(yawChange - yawChangeFirst);
         float diffBetweenYawChanges = yawChange - oldYawChange;
         if (robotized < QUANTUM * 1.5F && yawChange > QUANTUM * 2.0F) {
            ++robotizedAmount;
         }
         if (robotized < QUANTUM && yawChange > QUANTUM * 3.0F) {
            ++machineKnownMovement;
         }
         if (robotized < QUANTUM * 0.5F && yawChange > QUANTUM * 2.5F) {
            ++constantRotations;
         }
         // AimBasicCheck aggressivePatternI2/D2 used +-2 on raw floats; observed
         // data needs real snaps, so +-12 (a 35deg+ flick spread over interpolation).
         if (diffBetweenYawChanges > 12.0F) {
            ++bigSwingUp;
         }
         if (diffBetweenYawChanges < -12.0F) {
            ++bigSwingDown;
         }
         oldYawChange = yawChange;
      }

      // MX addLocalVl weights: aim=100, constant=65, sync=50.
      if (machineKnownMovement > 8) {
         this.addVl(player, st, 100.0F, "heuristic(aim)");
      }
      if (constantRotations > 6) {
         this.addVl(player, st, 65.0F, "heuristic(constant)");
      }
      if (robotizedAmount > 8) {
         this.addVl(player, st, 50.0F, "heuristic(sync)");
      }

      // pattern(snap): both-direction big jumps with a persistence streak (>2 windows)
      if (bigSwingUp > 1 && bigSwingDown > 1 && bigSwingUp + bigSwingDown > 4) {
         ++st.snapStreak;
         if (st.snapStreak > 2) {
            this.addVl(player, st, 55.0F, "pattern(snap)");
         }
      } else {
         st.snapStreak = 0;
      }
   }

   /**
    * silent(snap)/silent(return): detect a yaw burst (1-7 ticks, same
    * direction, >=20 deg total) and judge where it settled. Landing inside a
    * nearby player's hitbox bearing after starting >20 deg off is a snap hit;
    * the mirror burst off the target right after a hit is the return leg.
    */
   private void burstMachine(EntityPlayer player, State st, long tick,
      float yawChange, float prevYaw, List<EntityPlayer> targets) {
      float absYaw = Math.abs(yawChange);

      if (st.burstTicks > 0) {
         boolean sameDir = yawChange * st.burstDir >= 0.0F;
         if (absYaw < BURST_QUIET) {
            if (st.burstSum >= BURST_SUM_MIN) {
               this.evaluateBurst(player, st, tick, targets);
            }
            this.resetBurst(st);
            st.quietTicks = 1;
         } else if (sameDir) { // hard step or interpolation tail, still converging
            ++st.burstTicks;
            st.burstSum += absYaw;
            if (st.burstTicks > BURST_MAX_TICKS) {
               st.burstTicks = -1; // sustained turn (mouse swipe), not a snap
            }
         } else if (absYaw > BURST_STEP_MIN) { // hard direction flip: new burst
            st.burstTicks = 1;
            st.burstSum = absYaw;
            st.burstDir = yawChange;
            st.preBurstYaw = prevYaw;
            st.quietTicks = 0;
         } else {
            this.resetBurst(st); // weak counter-step, ambiguous
            st.quietTicks = 0;
         }
      } else if (st.burstTicks == -1) {
         if (absYaw < BURST_QUIET) {
            this.resetBurst(st);
            st.quietTicks = 1;
         }
      } else {
         if (absYaw > BURST_STEP_MIN && st.quietTicks >= 2) {
            st.burstTicks = 1;
            st.burstSum = absYaw;
            st.burstDir = yawChange;
            st.preBurstYaw = prevYaw;
            st.quietTicks = 0;
         } else if (absYaw < BURST_QUIET) {
            ++st.quietTicks;
         } else {
            st.quietTicks = 0;
         }
      }
   }

   private void evaluateBurst(EntityPlayer player, State st, long tick, List<EntityPlayer> targets) {
      if (targets.isEmpty()) {
         return; // nobody near — flick is meaningless either way
      }
      float bestErr = Float.MAX_VALUE;
      float bestPre = 0.0F;
      float bestPreInside = Float.MAX_VALUE;
      for (EntityPlayer target : targets) {
         Trail trail = this.trail(target.func_110124_au());
         float err = this.minInsideError(player, trail, st.lastYaw);
         if (err < bestErr) {
            bestErr = err;
            float bearingNow = bearingTo(player, trail.x[0], trail.z[0]);
            bestPre = Math.abs(wrapDegrees(st.preBurstYaw - bearingNow));
         }
         bestPreInside = Math.min(bestPreInside, this.minInsideError(player, trail, st.preBurstYaw));
      }

      if (bestErr <= QUANTUM && bestPre > SNAP_PRE_ERROR_MIN) {
         ++st.snapHits;
         st.lastSnapHitTick = tick;
         this.debug(player, "silent(snap) hit " + st.snapHits + "/" + (st.snapHits + st.snapMisses)
            + " land=" + String.format("%.1f", bestErr) + (char)176 + " pre=" + (int)bestPre + (char)176);
         if (st.snapHits >= SNAP_MIN_HITS && st.snapHits > st.snapMisses) {
            this.addVl(player, st, SNAP_VL, "silent(snap)");
            // silent(snap) is promoted to alert directly: repeated packet-precision
            // landings are confirmed silent aim, no need to wait out the shared pool
            // (AlertManager still debounces to one chat line per 200 ticks).
            this.failedKillaura = true;
            AlertManager.flag(player, AlertManager.CheckType.KILLAURA, (int)(st.aimVl / 10.0F));
         }
      } else if (bestPreInside <= QUANTUM && bestErr > SNAP_PRE_ERROR_MIN * 0.75F) {
         // burst started on a target and left it — the return leg of a
         // snap-attack-return silent aim cycle
         if (st.lastSnapHitTick != Long.MIN_VALUE && tick - st.lastSnapHitTick <= RETURN_PAIR_TICKS) {
            this.addVl(player, st, RETURN_VL, "silent(return)");
         }
      } else if (bestPre > SNAP_PRE_ERROR_MIN && bestErr > QUANTUM * 2.0F) {
         ++st.snapMisses; // genuine flick that landed past/short of everyone
      }
   }

   /**
    * silent(track): while the bearing to the same target rotates faster than
    * 2.5 deg/tick (strafing fight), count how often the observed yaw stays
    * inside that target's hitbox span. Lock-on aim holds ~100%; humans drift.
    */
   private void trackComponent(EntityPlayer player, State st, float yaw, List<EntityPlayer> targets) {
      EntityPlayer target = null;
      double bestDistSq = Double.MAX_VALUE;
      for (EntityPlayer candidate : targets) {
         double dx = candidate.field_70165_t - player.field_70165_t;
         double dy = candidate.field_70163_u - player.field_70163_u;
         double dz = candidate.field_70161_v - player.field_70161_v;
         double distSq = dx * dx + dy * dy + dz * dz;
         if (distSq < bestDistSq) {
            bestDistSq = distSq;
            target = candidate;
         }
      }
      if (target == null) {
         st.lastTargetId = null;
         st.lastBearing = Float.NaN;
         return;
      }

      UUID targetId = target.func_110124_au();
      Trail trail = this.trail(targetId);
      float bearingNow = bearingTo(player, trail.x[0], trail.z[0]);
      if (targetId.equals(st.lastTargetId) && !Float.isNaN(st.lastBearing)) {
         float losDelta = Math.abs(wrapDegrees(bearingNow - st.lastBearing));
         double dx = target.field_70165_t - player.field_70165_t;
         double dz = target.field_70161_v - player.field_70161_v;
         double horizDist = Math.sqrt(dx * dx + dz * dz);
         // close range makes the hitbox span huge — inside-span is only
         // meaningful from ~2.2 blocks out
         if (losDelta > TRACK_LOS_MIN && losDelta < TRACK_LOS_MAX && horizDist >= TRACK_MIN_DIST) {
            ++st.trackSamples;
            if (this.minInsideError(player, trail, yaw) <= QUANTUM * 0.5F) {
               ++st.trackTicks;
            }
            if (st.trackSamples >= TRACK_WINDOW) {
               if ((float)st.trackTicks >= TRACK_RATIO * (float)st.trackSamples) {
                  this.addVl(player, st, TRACK_VL, "silent(track) " + st.trackTicks + "/" + st.trackSamples);
               }
               st.trackSamples = 0;
               st.trackTicks = 0;
            }
         }
      }
      st.lastTargetId = targetId;
      st.lastBearing = bearingNow;
   }

   /**
    * movement(fix): detect the body/head desync that "movement fix" silent aim
    * introduces. Three leaks, in rising order of cheat sophistication:
    * window-mean bucket residual (float-input fixes), residual while the head
    * is pinned inside a target's hitbox span (lock + fix), and sprint speed at
    * an offset no sprintable input can produce (proper bucket-quantized fixes,
    * or orbiting a victim while yaw-locked).
    */
   private void movementComponent(Minecraft mc, EntityPlayer player, State st,
      double moveX, double moveY, double moveZ, float yaw, List<EntityPlayer> targets) {
      // slow decay so isolated lock/sprint ticks don't pool up over a long fight
      if (++st.moveTickCounter >= MOVE_DECAY_TICKS) {
         st.moveTickCounter = 0;
         st.lockDesync = Math.max(0, st.lockDesync - 1);
         st.sprintDesync = Math.max(0, st.sprintDesync - 1);
      }

      boolean flat = st.hasVel && Math.abs(moveY) < MOVE_FLAT_DY && Math.abs(st.lastMoveY) < MOVE_FLAT_DY;
      boolean haveAccel = st.hasVel;
      double ax = moveX - st.lastVelX;
      double az = moveZ - st.lastVelZ;
      st.lastVelX = moveX;
      st.lastVelZ = moveZ;
      st.lastMoveY = moveY;
      st.hasVel = true;
      if (!haveAccel) {
         return;
      }
      double accel = Math.sqrt(ax * ax + az * az);
      double speed = Math.sqrt(moveX * moveX + moveZ * moveZ);

      // Only flat-ground running is informative: air gliding and knockback
      // carry momentum in directions the yaw never chose, jumps spike |dy|.
      if (!flat || player.field_70737_aN > 0 || speed < MOVE_MIN_SPEED || speed > MOVE_MAX_SPEED) {
         return;
      }
      // Ice keeps momentum pointing away from the yaw for many low-accel ticks
      // even for legit players — useless terrain for every sub-test.
      Block ground = mc.field_71441_e.func_180495_p(
         new BlockPos(player.field_70165_t, player.field_70163_u - 0.5D, player.field_70161_v)).func_177230_c();
      if (ground == Blocks.field_150432_aD || ground == Blocks.field_150403_cj) {
         return;
      }

      // Yaw you would have to hold to walk this velocity vector forward, vs the
      // yaw actually broadcast; residual = distance to the nearest legal
      // 45-deg strafe offset.
      float moveBearing = (float)Math.toDegrees(Math.atan2(-moveX, moveZ));
      float offset = wrapDegrees(moveBearing - yaw);
      float residual = bucketResidual(offset);

      // sprint-direction leak (tolerant accel gate: orbiting curves gently)
      if (player.func_70051_ag() && speed > SPRINT_MIN_SPEED && accel < SPRINT_ACCEL
         && Math.abs(offset) > SPRINT_OFFSET) {
         ++st.sprintDesync;
         this.debug(player, "movement(sprint) tick " + st.sprintDesync + "/" + SPRINT_HITS
            + " off=" + (int)offset + (char)176 + " spd=" + String.format("%.2f", speed));
         if (st.sprintDesync >= SPRINT_HITS) {
            this.addVl(player, st, MOVE_SPRINT_VL, "movement(sprint) " + (int)offset + (char)176);
            st.sprintDesync -= SPRINT_HITS;
         }
      }

      if (accel > MOVE_SMOOTH_ACCEL) {
         return; // turning momentum lags the yaw by ~1.5 ticks; residual unreliable
      }

      // head pinned inside someone's hitbox while the body walks its own line
      if (residual > LOCK_RESIDUAL) {
         for (EntityPlayer target : targets) {
            if (this.minInsideError(player, this.trail(target.func_110124_au()), yaw) <= QUANTUM) {
               ++st.lockDesync;
               this.debug(player, "movement(lock) tick " + st.lockDesync + "/" + LOCK_HITS
                  + " res=" + (int)residual + (char)176);
               if (st.lockDesync >= LOCK_HITS) {
                  this.addVl(player, st, MOVE_LOCK_VL, "movement(lock) " + (int)residual + (char)176);
                  st.lockDesync -= LOCK_HITS;
               }
               break;
            }
         }
      }

      ++st.moveSamples;
      st.residualSum += residual;
      if (residual > MOVE_DESYNC_RESIDUAL) {
         ++st.moveDesyncTicks;
      }
      if (st.moveSamples >= MOVE_WINDOW) {
         float mean = st.residualSum / (float)st.moveSamples;
         if (mean > MOVE_MEAN_LIMIT) {
            this.addVl(player, st, MOVE_VL, "movement(fix) mean=" + String.format("%.1f", mean)
               + (char)176 + " hard=" + st.moveDesyncTicks + "/" + st.moveSamples);
         }
         st.moveSamples = 0;
         st.moveDesyncTicks = 0;
         st.residualSum = 0.0F;
      }
   }

   /** Distance (deg) from an angle to the nearest multiple of 45 — the legal strafe offsets. */
   private static float bucketResidual(float offset) {
      float nearest = 45.0F * Math.round(offset / 45.0F);
      return Math.abs(wrapDegrees(offset - nearest));
   }

   /** consume: the old Killaura B — swinging at entities mid eat/drink. */
   private void consumeComponent(EntityPlayer player, State st, long tick) {
      ItemStack heldItem = player.func_70694_bm();
      boolean isUsingItem = player.func_71039_bw();
      boolean isConsumable = heldItem != null && isConsumable(heldItem.func_77973_b());
      boolean isAttacking = player.field_110158_av > 0;

      if (isUsingItem && isConsumable) {
         ++st.useItemTicks;
      } else {
         if (st.useItemTicks > 0) {
            st.lastEatTick = tick;
         }
         st.useItemTicks = 0;
      }

      long sinceLastEat = tick - st.lastEatTick;
      if (isAttacking && st.useItemTicks > MIN_USE_TIME && sinceLastEat < EAT_TIMEOUT && isConsumable) {
         ++st.consumeVl;
         if (cfg.v.debugMessages) {
            // itemRegistry.getNameForObject — vanilla equivalent of Forge's getRegistryName(), which only exists after runtime binpatching
            Rain.addMessage(EnumChatFormatting.YELLOW + "[AntiCheat]: " + EnumChatFormatting.WHITE
               + player.func_70005_c_() + " consume: swinging while using item | Use Time=" + st.useItemTicks
               + " | Last Ate=" + sinceLastEat + " | vl=" + st.consumeVl
               + " | Item=" + String.valueOf(Item.field_150901_e.func_177774_c(heldItem.func_77973_b())));
         }
         if (st.consumeVl >= CONSUME_FAIL_VL) {
            this.failedKillaura = true;
            AlertManager.flag(player, AlertManager.CheckType.KILLAURA, st.consumeVl);
         }
      } else if (st.consumeVl > 0) {
         --st.consumeVl;
      }
   }

   /** Players within aura range of the attacker that could be aim targets. */
   private List<EntityPlayer> targetsNear(Minecraft mc, EntityPlayer attacker, long tick) {
      List<EntityPlayer> out = new ArrayList<EntityPlayer>();
      for (EntityPlayer p : mc.field_71441_e.field_73010_i) {
         if (!PlayerEligibility.shouldUseAsTarget(p, attacker)) {
            continue;
         }
         double dx = p.field_70165_t - attacker.field_70165_t;
         double dy = p.field_70163_u - attacker.field_70163_u;
         double dz = p.field_70161_v - attacker.field_70161_v;
         if (dx * dx + dy * dy + dz * dz > TARGET_RANGE_SQ) {
            continue;
         }
         this.trail(p.func_110124_au()).push(p.field_70165_t, p.field_70161_v, tick);
         out.add(p);
      }
      return out;
   }

   /**
    * Angular distance (deg) from the given yaw to the OUTSIDE of the target's
    * horizontal hitbox span — 0 means the yaw points inside the hitbox.
    * Minimized over the target's recent positions so network latency and the
    * ~1-tick rotation interpolation lag can't manufacture error.
    */
   private float minInsideError(EntityPlayer attacker, Trail trail, float yaw) {
      float best = Float.MAX_VALUE;
      for (int i = 0; i < trail.size; ++i) {
         double dx = trail.x[i] - attacker.field_70165_t;
         double dz = trail.z[i] - attacker.field_70161_v;
         double horizDist = Math.sqrt(dx * dx + dz * dz);
         if (horizDist < 0.5D) { // overlapping — bearing is meaningless
            continue;
         }
         float bearing = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
         float err = Math.abs(wrapDegrees(yaw - bearing));
         float halfWidth = (float)Math.toDegrees(Math.atan2(HITBOX_HALF_WIDTH, horizDist));
         best = Math.min(best, Math.max(0.0F, err - halfWidth));
      }
      return best;
   }

   /** Yaw bearing from the attacker to a world position (vanilla faceEntity formula). */
   private static float bearingTo(EntityPlayer attacker, double x, double z) {
      double dx = x - attacker.field_70165_t;
      double dz = z - attacker.field_70161_v;
      return (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
   }

   private void addVl(EntityPlayer player, State st, float vl, String reason) {
      st.aimVl += vl;
      this.debug(player, "aim component: " + reason + " | vl=" + (int)st.aimVl + " (+" + (int)vl + ")");
   }

   private void debug(EntityPlayer player, String message) {
      if (cfg.v.debugMessages) {
         Rain.addMessage(EnumChatFormatting.YELLOW + "[AntiCheat]: " + EnumChatFormatting.WHITE
            + player.func_70005_c_() + " " + message);
      }
   }

   private Trail trail(UUID uuid) {
      return this.trails.computeIfAbsent(uuid, (k) -> new Trail());
   }

   private void resetBurst(State st) {
      st.burstTicks = 0;
      st.burstSum = 0.0F;
      st.burstDir = 0.0F;
   }

   private void resetSession(State st) {
      this.resetBurst(st);
      st.quietTicks = 0;
      st.snapHits = 0;
      st.snapMisses = 0;
      st.lastSnapHitTick = Long.MIN_VALUE;
      st.trackSamples = 0;
      st.trackTicks = 0;
      st.lastTargetId = null;
      st.lastBearing = Float.NaN;
      st.hasVel = false;
      st.moveSamples = 0;
      st.moveDesyncTicks = 0;
      st.residualSum = 0.0F;
      st.lockDesync = 0;
      st.sprintDesync = 0;
      st.moveTickCounter = 0;
   }

   public boolean failedKillaura() {
      return this.failedKillaura;
   }

   public void forgetPlayer(UUID uuid) {
      if (uuid == null) {
         return;
      }
      this.states.remove(uuid);
      this.trails.remove(uuid);
      if (this.states.isEmpty()) {
         this.failedKillaura = false;
      }
   }

   public void retainPlayers(Set<UUID> checkablePlayerIds, Set<UUID> realPlayerIds) {
      this.states.keySet().retainAll(checkablePlayerIds);
      this.trails.keySet().retainAll(realPlayerIds);
      if (this.states.isEmpty()) {
         this.failedKillaura = false;
      }
   }

   public void reset() {
      this.failedKillaura = false;
      this.states.clear();
      this.trails.clear();
   }

   private boolean isConsumable(Item item) {
      return item instanceof ItemFood || item instanceof ItemPotion || item instanceof ItemBucketMilk;
   }

   private static float wrapDegrees(float angle) {
      angle %= 360.0F;
      if (angle >= 180.0F) {
         angle -= 360.0F;
      }
      if (angle < -180.0F) {
         angle += 360.0F;
      }
      return angle;
   }
}
