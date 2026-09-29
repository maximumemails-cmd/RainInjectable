package first.rain.anticheat.gui;

/** In-game descriptions kept with the modules they explain. */
public final class ModuleGuide {
   private ModuleGuide() {}
   public static final String[] TITLES = {
      "Rain / how alerts work", "AutoBlock", "Legit Scaffold", "KillAura / aim",
      "KillAura / movement fix", "KillAura / consuming", "Temporal Analysis", "Reach Review"
   };
   public static final String[][] DETAILS = {
      {"The master switch enables detection. Turning it off clears tracked evidence. Individual switches control each module.",
       "Alerts report repeated suspicious observations for review. Confidence measures evidence strength, not the probability of cheating. Lag and ambiguous fights can suppress checks."},
      {"Looks for AutoBlock: repeatedly swinging while the player appears to block with a sword during combat.",
       "Counts separate overlap episodes, not every animation tick. Requires recent hurt-correlated combat and usable observations. Normal block-hitting can look similar; an alert is not proof."},
      {"Looks for Legit Scaffold, sneak automation and bridge-assist patterns: repeated, unusually regular short crouches while bridging.",
       "Requires a held block, downward aim, ground movement and a visible edge. Placement is inferred from swings. Skilled speed-bridging may resemble automation."},
      {"Looks for KillAura, aim assist and silent-aim patterns: target snaps, snap-and-return bursts, and sustained target tracking.",
       "Also restores the original constant, synchronized and robot-like rotation windows and repeated snap oscillations. Checks use hurt-correlated combat; remote rotations cannot reveal exact mouse input."},
      {"Part of KillAura. Looks for movement correction associated with silent aim: movement direction repeatedly misaligned with the visible head rotation.",
       "Checks average strafe-angle residual, target lock with sideways movement, and unusual sprint direction. Skips ice, knockback, airborne movement and other uncertain conditions."},
      {"Part of KillAura. Restores the original attack-during-eating/drinking check for food, potions and milk.",
       "Looks for repeated attack/use overlap after an earlier consume action, with use-duration and timing thresholds. Requires combat context; delayed item and attack animations can still be ambiguous."},
      {"Keeps about four seconds of player history to compare behavior over time. Supports the reach observer and adds longer bridging-rhythm evidence.",
       "Looks for repeated crouch/edge timing consistent with bridge assistance. These bridge signals also require Legit Scaffold enabled. This is supporting analysis, not a separate cheat verdict."},
      {"Looks for gross Reach: repeated combat candidates with an unusually large minimum attacker-to-target distance, even after generous observer slack.",
       "Requires usable ping and unambiguous swing/hurt correlation across episodes. Crowds, lag and uncertainty suppress evidence. Small reach changes cannot be reliably resolved from this observer."}
   };
}
