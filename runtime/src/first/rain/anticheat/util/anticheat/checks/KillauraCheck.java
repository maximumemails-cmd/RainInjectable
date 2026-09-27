package first.rain.anticheat.util.anticheat.checks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import first.rain.anticheat.Rain;
import first.rain.anticheat.config.cfg;
import first.rain.anticheat.util.anticheat.AlertManager;
import first.rain.anticheat.util.anticheat.PlayerEligibility;

/** Remote aim evidence is evaluated only during hurt-correlated combat.
 * Repeated target-aware snap and tracking signals must both contribute. */
public class KillauraCheck {
   /** Keep recent target hurt observations in one short fight window. */
   private static final long COMBAT_WINDOW_TICKS = 20L;
   /** Out of combat this long -> per-fight counters reset. */
   private static final long SESSION_RESET_TICKS = 140L;
   /** Quantization step of observed remote rotations (360 / 256). */
   private static final float QUANTUM = 1.40625F;

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

   /** Aura target search radius around the attacker (blocks). */
   private static final double TARGET_RANGE_SQ = 36.0D;
   /** Half hitbox width (0.3) plus margin, for bearing-span tests. */
   private static final double HITBOX_HALF_WIDTH = 0.4D;
   /** Ticks of position history kept per player for lag/interpolation slack. */
   private static final int TRAIL_LEN = 5;

   private final Map<UUID, State> states = new HashMap<UUID, State>();
   private final Map<UUID, Trail> trails = new HashMap<UUID, Trail>();

   private static final class State {
      // rotation stream
      float lastYaw;
      boolean hasRotation;
      // combat gate
      long lastSwingTick = Long.MIN_VALUE;
      boolean wasSwinging;
      long lastCombatTick = Long.MIN_VALUE;
      long lastObservationTick = Long.MIN_VALUE;
      UUID combatTarget;
      final AimEvidence evidence = new AimEvidence();
      final Map<UUID, Integer> targetHurt = new HashMap<UUID, Integer>();
      final List<EntityPlayer> targets = new ArrayList<EntityPlayer>();
      // silent(snap) burst machine
      int burstTicks;       // 0 = idle, -1 = invalidated (sustained turn), >0 = in burst
      float burstSum;
      float burstDir;
      float preBurstYaw;
      int quietTicks;
      int snapHits;
      int snapMisses;
      float lastSnapMagnitude;
      long lastSnapHitTick = Long.MIN_VALUE;
      // silent(track)
      UUID lastTargetId;
      float lastBearing = Float.NaN;
      int trackSamples;
      int trackTicks;
   }

   /** Short per-player position history, newest entry at index 0. */
   private static final class Trail {
      final double[] x = new double[TRAIL_LEN];
      final double[] y = new double[TRAIL_LEN];
      final double[] z = new double[TRAIL_LEN];
      long lastTick = Long.MIN_VALUE;
      int size;

      void push(double px, double py, double pz, long tick) {
         if (tick == this.lastTick && this.size > 0) {
            return;
         }
         if (this.lastTick != Long.MIN_VALUE && (tick < this.lastTick || tick - this.lastTick > 2L)) {
            this.size = 0;
         }
         System.arraycopy(this.x, 0, this.x, 1, TRAIL_LEN - 1);
         System.arraycopy(this.y, 0, this.y, 1, TRAIL_LEN - 1);
         System.arraycopy(this.z, 0, this.z, 1, TRAIL_LEN - 1);
         this.x[0] = px;
         this.y[0] = py;
         this.z[0] = pz;
         this.lastTick = tick;
         if (this.size < TRAIL_LEN) {
            ++this.size;
         }
      }
   }

   public void anticheatCheck(EntityPlayer player) {
      if (!cfg.v.detectKillaura) {
         reset();
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
      if (st.lastObservationTick != Long.MIN_VALUE
         && (tick <= st.lastObservationTick || tick - st.lastObservationTick > 2L)) {
         this.resetSession(st);
         st.evidence.reset();
         st.hasRotation = false;
         st.targetHurt.clear();
      }
      st.lastObservationTick = tick;

      // Keep position history fresh for this player and the observer — both
      // are bearing candidates when someone else is the attacker.
      this.trail(uuid).push(player.field_70165_t, player.field_70163_u, player.field_70161_v, tick);
      this.trail(mc.field_71439_g.func_110124_au())
         .push(mc.field_71439_g.field_70165_t, mc.field_71439_g.field_70163_u,
            mc.field_71439_g.field_70161_v, tick);

      if (player.field_70154_o != null) { // riding: vehicle rotations are unreliable
         this.resetSession(st);
         st.evidence.reset();
         st.hasRotation = false;
         return;
      }

      if (player.field_82175_bq && !st.wasSwinging) {
         st.lastSwingTick = tick;
      }
      st.wasSwinging = player.field_82175_bq;

      List<EntityPlayer> targets = st.targets;
      boolean recentSwing = st.lastSwingTick != Long.MIN_VALUE
         && tick >= st.lastSwingTick && tick - st.lastSwingTick <= 5L;
      boolean recentFight = st.lastCombatTick != Long.MIN_VALUE
         && tick >= st.lastCombatTick && tick - st.lastCombatTick <= COMBAT_WINDOW_TICKS;
      if (recentSwing || recentFight) {
         this.targetsNear(mc, player, tick, targets);
         if (recentSwing) this.updateCombat(player, st, tick, targets);
      } else {
         targets.clear();
      }

      float yaw = player.field_70177_z;
      if (!st.hasRotation) {
         st.lastYaw = yaw;
         st.hasRotation = true;
         return;
      }
      float prevYaw = st.lastYaw;
      float yawChange = wrapDegrees(yaw - st.lastYaw);
      st.lastYaw = yaw;

      // Teleport/lag guard: a large position step also snaps observed rotation,
      // which would poison every rotation component with a false "snap".
      double moveX = player.field_70165_t - player.field_70142_S;
      double moveZ = player.field_70161_v - player.field_70136_U;
      if (moveX * moveX + moveZ * moveZ > 25.0D) {         this.resetBurst(st);
         st.lastBearing = Float.NaN;
         st.lastTargetId = null;         return;
      }

      // Rotations outside a hurt-correlated fight cannot add aim evidence.
      if (st.lastCombatTick == Long.MIN_VALUE || tick < st.lastCombatTick
         || tick - st.lastCombatTick > COMBAT_WINDOW_TICKS) {
         if (st.lastCombatTick != Long.MIN_VALUE && tick - st.lastCombatTick > SESSION_RESET_TICKS) {
            this.resetSession(st);
            st.evidence.reset();
         }
         st.evidence.tick(false);
         return;
      }

      // geometry components share one candidate scan per tick
      this.burstMachine(player, st, tick, yawChange, prevYaw, targets);
      this.trackComponent(player, st, yaw, targets);
      if (st.evidence.shouldAlert()) {
         double baseline = Rain.ANTICHEAT.observations.calmTurnBaseline(uuid);
         double baselineSupport = Double.isFinite(baseline)
            && st.lastSnapMagnitude >= Math.max(25.0D, baseline * 2.0D) ? 0.06D : 0.0D;
         AlertManager.recordLegacy(player, AlertManager.CheckType.KILLAURA,
            (int)(st.evidence.score() / 10.0F), "snapHits=" + st.snapHits
               + ", trackSamples=" + st.trackSamples + ", trackInside=" + st.trackTicks
               + ", inferredHurtEpisodes=" + st.evidence.combatHits()
               + ", calmTurnBaseline=" + (Double.isFinite(baseline)
                  ? String.format(java.util.Locale.ROOT, "%.1f", baseline) : "unknown"),
            Math.min(0.65D, 0.25D + 0.06D * st.snapHits
               + 0.04D * st.evidence.combatHits() + baselineSupport));
         st.evidence.afterAlert();
         st.snapHits = 0;
         st.snapMisses = 0;
         st.trackSamples = 0;
         st.trackTicks = 0;
      }
      st.evidence.tick(true);
   }

   /** A swing is only a candidate attack. A fresh hurt animation on a nearby,
    * plausible target supplies the stronger (still imperfect) combat context. */
   private void updateCombat(EntityPlayer player, State st, long tick, List<EntityPlayer> targets) {
      // Crowded fights make a victim's hurt onset impossible to attribute from
      // a relayed swing. Do not feed that ambiguity into aim or AutoBlock.
      if (targets.size() != 1) return;
      for (EntityPlayer target : targets) {
         UUID id = target.func_110124_au();
         int hurt = target.field_70737_aN;
         Integer previous = st.targetHurt.put(id, hurt);
         if (previous == null || hurt <= previous || hurt < 7
            || st.lastSwingTick == Long.MIN_VALUE || tick - st.lastSwingTick > 4L) {
            continue;
         }
         double dx = target.field_70165_t - player.field_70165_t;
         double dy = target.field_70163_u - player.field_70163_u;
         double dz = target.field_70161_v - player.field_70161_v;
         if (dx * dx + dy * dy + dz * dz > 20.25D) {
            continue;
         }
         if (!Rain.ANTICHEAT.observations.unambiguousSwing(player.func_110124_au(), id, tick)) {
            continue;
         }
         if (this.minAimError(player, target, this.trail(id),
            player.field_70177_z, player.field_70125_A) > 18.0F) {
            continue;
         }
         if (!player.func_70685_l(target)) continue;
         st.lastCombatTick = tick;
         st.combatTarget = id;
         st.evidence.combatHit(tick);
      }
   }

   public boolean hasRecentCombat(UUID uuid, long tick) {
      State st = this.states.get(uuid);
      return st != null && st.lastCombatTick != Long.MIN_VALUE
         && tick >= st.lastCombatTick && tick - st.lastCombatTick <= 12L;
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
      EntityPlayer bestTarget = null;
      float bestPre = 0.0F;
      float bestPreInside = Float.MAX_VALUE;
      for (EntityPlayer target : targets) {
         if (!target.func_110124_au().equals(st.combatTarget)) {
            continue;
         }
         Trail trail = this.trail(target.func_110124_au());
         float err = this.minAimError(player, target, trail, st.lastYaw, player.field_70125_A);
         if (err < bestErr) {
            bestErr = err;
            bestTarget = target;
            float bearingNow = bearingTo(player, trail.x[0], trail.z[0]);
            bestPre = Math.abs(wrapDegrees(st.preBurstYaw - bearingNow));
         }
         bestPreInside = Math.min(bestPreInside, this.minInsideError(player, trail, st.preBurstYaw));
      }

      if (bestErr <= QUANTUM && bestPre > SNAP_PRE_ERROR_MIN
         && tick - st.lastCombatTick <= 12L && bestTarget != null
         && player.func_70685_l(bestTarget)) {
         ++st.snapHits;
         st.lastSnapMagnitude = st.burstSum;
         st.lastSnapHitTick = tick;
         this.debug(player, "silent(snap) hit " + st.snapHits + "/" + (st.snapHits + st.snapMisses)
            + " land=" + String.format("%.1f", bestErr) + (char)176 + " pre=" + (int)bestPre + (char)176);
         if (st.snapHits >= SNAP_MIN_HITS && st.snapHits > st.snapMisses) {
            this.addEvidence(player, st, AimEvidence.SNAP, SNAP_VL, "silent(snap)");
         }
      } else if (bestPreInside <= QUANTUM && bestErr > SNAP_PRE_ERROR_MIN * 0.75F) {
         // burst started on a target and left it — the return leg of a
         // snap-attack-return silent aim cycle
         if (st.lastSnapHitTick != Long.MIN_VALUE && tick - st.lastSnapHitTick <= RETURN_PAIR_TICKS) {
            this.addEvidence(player, st, AimEvidence.RETURN, RETURN_VL, "silent(return)");
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
         if (!candidate.func_110124_au().equals(st.combatTarget)) {
            continue;
         }
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
            if (this.minAimError(player, target, trail, yaw, player.field_70125_A) <= QUANTUM
               && player.func_70685_l(target)) {
               ++st.trackTicks;
            }
            if (st.trackSamples >= TRACK_WINDOW) {
               if ((float)st.trackTicks >= TRACK_RATIO * (float)st.trackSamples) {
                  this.addEvidence(player, st, AimEvidence.TRACK, TRACK_VL,
                     "silent(track) " + st.trackTicks + "/" + st.trackSamples);
               }
               st.trackSamples = 0;
               st.trackTicks = 0;
            }
         }
      }
      st.lastTargetId = targetId;
      st.lastBearing = bearingNow;
   }

   /** Players within aura range of the attacker that could be aim targets. */
   private void targetsNear(Minecraft mc, EntityPlayer attacker, long tick, List<EntityPlayer> out) {
      out.clear();
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
         this.trail(p.func_110124_au()).push(p.field_70165_t, p.field_70163_u, p.field_70161_v, tick);
         out.add(p);
      }
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

   private float minAimError(EntityPlayer attacker, EntityPlayer target, Trail trail,
      float yaw, float pitch) {
      float best = Float.MAX_VALUE;
      double eyeY = attacker.field_70163_u + attacker.func_70047_e();
      for (int i = 0; i < trail.size; ++i) {
         best = Math.min(best, AimGeometry.outsideBoxDegrees(attacker.field_70165_t,
            eyeY, attacker.field_70161_v, yaw, pitch, trail.x[i], trail.y[i], trail.z[i],
            HITBOX_HALF_WIDTH, target.field_70131_O));
      }
      return best;
   }

   /** Yaw bearing from the attacker to a world position (vanilla faceEntity formula). */
   private static float bearingTo(EntityPlayer attacker, double x, double z) {
      double dx = x - attacker.field_70165_t;
      double dz = z - attacker.field_70161_v;
      return (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
   }

   private void addEvidence(EntityPlayer player, State st, int source, float vl, String reason) {
      st.evidence.add(source, vl);
      this.debug(player, "aim component: " + reason + " | score=" + (int)st.evidence.score()
         + " hits=" + st.evidence.combatHits() + " sources=" + st.evidence.sourceMask());
   }

   private void debug(EntityPlayer player, String message) {
      if (cfg.v.debugMessages) {
         System.out.println("[Rain] " + player.func_70005_c_() + " " + message);
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
      st.lastSnapMagnitude = 0.0F;
      st.lastSnapHitTick = Long.MIN_VALUE;
      st.trackSamples = 0;
      st.trackTicks = 0;
      st.lastTargetId = null;
      st.lastBearing = Float.NaN;
      st.combatTarget = null;
      st.lastCombatTick = Long.MIN_VALUE;
   }

   public void forgetPlayer(UUID uuid) {
      if (uuid == null) {
         return;
      }
      this.states.remove(uuid);
      this.trails.remove(uuid);
   }

   public void retainPlayers(Set<UUID> checkablePlayerIds, Set<UUID> realPlayerIds) {
      this.states.keySet().retainAll(checkablePlayerIds);
      this.trails.keySet().retainAll(realPlayerIds);
      for (State st : this.states.values()) st.targetHurt.keySet().retainAll(realPlayerIds);
   }

   public void reset() {
      this.states.clear();
      this.trails.clear();
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
