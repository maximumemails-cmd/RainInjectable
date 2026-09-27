# Testing and compatibility evidence

Run the following after a clean build:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build.ps1
ctest --test-dir build/native -C Release --output-on-failure
build/native/Release/target_diagnostics.exe
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test-bootstrap.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test-detectors.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-detector-static.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1
git diff --check
```

The native compatibility test covers confirmed Forge 1.8.9, a starting JVM,
32-bit Java, another Minecraft version, vanilla, Lunar, Badlion, Prism and
Feather. The scanner discovers processes separately from the compatibility
decision; candidate recognition alone does not imply Rain can run. It also checks
launcher parent attribution, JVM architecture errors, stage results, payload
status parsing, and credential redaction. The UI layout test checks action,
detail, log and footer spacing at 100%, 125%, 150% and 200% DPI.

`target_diagnostics.exe` prints stage evidence without command-line secrets.
Its explicit `--inject PID --dll PATH` option runs the native injector for live
diagnostics. On 2026-09-27 the Badlion child JVM was Java 17.0.13 x64 running
Minecraft 1.8.9 without Forge. Rain's native DLL loaded successfully and JNI
reached Java. The initial Forge bootstrap stopped because it found `ave` (the
obfuscated Minecraft class) and no Forge event bus. The dedicated Badlion JAR
was then compiled against Rain's detector sources, remapped from SRG to Notch
names, and loaded without Lion cheat classes. In a restarted Badlion server
session, PID 27452, the payload reported `Complete`; its local status file
showed `inWorld=true`, 51 eligible players, increasing processed detector
ticks and no runtime error. The JVM remained responsive. This proves the
detector loop ran on that session; it does not establish alert accuracy against
actual cheating. The JDK external Attach API remained disabled; Rain's native
DLL/JNI path did not use it.

In a later live Badlion session, PID 27260, the new GUI build initialized its
GUI classes, recorded two Right Shift opens with a close between them, and
continued processing detector ticks with no reported runtime error.

The Java bootstrap harness uses a synthetic game-thread endpoint. It checks
the system and wrapper classloader paths, a JAR path with spaces and Unicode,
repeat startup, and a startup failure that must leave the loaded flag unset.
It does not start Minecraft or prove Forge event-bus delivery.

The detector harness has 345 assertions over synthetic normal, skilled,
suspicious, noisy and recovery traces. `check-detector-static.ps1` verifies
detector registration and cleanup, master/world resets, key persistence, and
absence of network/process/send behavior in detector sources. Synthetic
traces cannot establish real attack attribution.

`verify-release.ps1` checks both native binaries are x64, compares the GUI and
Java runtime version markers, confirms the EXE's embedded DLL and both JARs match the
built files byte-for-byte, checks the ZIP contains the intended files, and
writes SHA-256 hashes to the local release folder.

## Runtime limits

- Forge uses its event bus and SRG names. Badlion uses a separate Notch-mapped
  detector JAR and a scheduled game-thread loop. Both target Minecraft 1.8.9.
- Lunar and other unsupported runtimes remain disabled. Badlion support was
  live-tested on the specific 1.8.9 Java 17 client described above; another
  Badlion build may need new mappings or integration checks.
- Standard Launcher, Forge 1.8.9 under Prism/MultiMC, and Forge-based Feather
  are eligible based on static markers and classloader tests. Wrapper profiles
  that hide Forge markers show Needs verification and require confirmation before the
  Java bootstrap's definitive Forge check. They have not been live-injected.
- Forge event delivery and visual desktop rendering of the injector remain
  unverified. Badlion's GUI key opened the screen in the live JVM, and the
  master control changed and persisted across a close and reopen. Flash and
  Nametag overlays are not part of the Badlion adapter yet; the final Badlion
  build hides those controls.
- After a partial bootstrap failure, restart Minecraft before retrying; an
  already-loaded native DLL will not rerun its process-attach entry point.
- The injector now clears the prior PID status and waits up to 36 seconds for
  the Java result. Native DLL presence alone does not mean Rain initialized.
- The UI does not offer DLL ejection: Rain's Java event registrations would
  remain active after unloading its native bootstrap DLL.
