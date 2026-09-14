# QuarryGuard Full Runtime Preparation

## Scope and status

`integration/prepare-full-runtime.ps1` prepares one fixed disposable target, `quarryguard-lab/full-runtime`, from the fixed read-only source `C:\Users\avets\Documents\Minecraft\Ascendant-Server`. It never launches Java and has no source or destination path parameter. This remains an isolated prototype test; it is not a production or GitHub workflow.

The reviewed source set is Minecraft 1.21.1, NeoForge 21.1.248, and exactly 145 server mod JARs. The script pins a SHA-256 aggregate for those names and file hashes and pins the NeoForge `win_args.txt` hash. A changed source set fails before any target file is written.

## Included material

- All 145 source `mods/*.jar` files, unchanged, plus the existing lab build `integration/build/ascendant-quarryguard-0.1.0-lab.jar`.
- Complete `config`, `defaultconfigs`, `moonlight-global-datapacks`, and `patchouli_books` trees. Optional `kubejs` and `scripts` trees are copied if they exist.
- Only `world/datapacks` from the source world, placed under the new disposable `quarryguard-lab-world/datapacks`. No other production-world file is read into the plan or copied.
- The existing `eula.txt` only when it already contains `eula=true`. The script does not create new acceptance.
- A generated `launch.args` whose every `libraries/...` reference points absolutely to the source libraries. Libraries are not copied or modified.
- Generated lab-only `server.properties` and `lab-jvm-args.txt`.

The script excludes the production world, production `server.properties`, player caches, whitelist identities, operator identities, bans, server addresses, logs, crash reports, caches, archives, and update/install scripts. It does not print configuration contents, credentials, EULA contents, or log contents. A credential-like nonempty assignment in copied configuration stops preparation without displaying its value.

## Network containment

Update from execution: the original copied `config/neoforge-server.toml`
enabled `advertiseDedicatedServerToLan`. A JVM thread dump identified the
LAN pinger behind the extra wildcard UDP endpoint. The preparer and the lab
copy now disable that option. Runtime verification subsequently found only
the intended Minecraft TCP and Voice Chat UDP loopback endpoints; IPv4-mapped
`::ffff:127.0.0.1` is accepted as the same loopback address, never wildcard.
See [phase 2 execution results](validation-phase2.md).

Minecraft binds to `127.0.0.1:25585`. Query, RCON, status advertising, JMX, and transfers are disabled. The whitelist and whitelist enforcement are enabled, but no identities are copied. Tests are non-concurrent: the preparer probes the ports and owns the named mutex `Local\QuarryGuardFullRuntime25585-25586`; the runner should acquire the same mutex for the whole Java process lifetime.

Simple Voice Chat is retained and changed to UDP `127.0.0.1:25586`, with `voice_host=127.0.0.1:25586` and ping replies disabled. Its own documentation says that voice audio uses a separate UDP port and that `bind_address` controls the bound address: [Simple Voice Chat server configuration](https://modrepo.de/minecraft/voicechat/wiki/server_config).

WebDisplays is retained, while its built-in MiniServ is set to its documented disabled value, `miniserv_port = 0`. Static review of all 145 JAR class files for common Java/Netty listening APIs found listener implementations only in Simple Voice Chat and WebDisplays, apart from test/shaded classes in Ars Nouveau, GuideME, and spark. No mod has been removed to manufacture a full-pack compatibility result. This is static preparation evidence; the runner must still verify actual listeners after startup and fail if any listener is wildcard-bound or outside the two approved endpoints.

FML version checks, the Veinminer updater, and the copied client-default Distant Horizons updater are disabled in the lab copy to avoid unrelated update traffic. Spark remains installed; background profiling is disabled through `lab-jvm-args.txt`. Spark uploads are command-triggered and must not be invoked because they publish data externally; its configurable viewer and upload endpoints are documented in the [spark configuration reference](https://spark.lucko.me/docs/Configuration). No logs or profiler output may be published.

## Generated server policy

- New flat world: `quarryguard-lab-world`
- Fixed seed: `548941320735`
- View distance: `2`; simulation distance: `2`
- JVM recommendation: initial heap 2 GiB, maximum heap 4 GiB
- Maximum one player; online mode and secure profiles remain enabled
- No copied operators, whitelist entries, user cache, bans, IP addresses, or RCON password

The 4 GiB ceiling was selected for startup coverage of the complete 145-mod set. It is a runner input, not an instruction to launch from the preparer.

## Reproducibility and failure behavior

`copy-manifest.json` records, for every copied or generated file, its origin, byte length, source SHA-256 when applicable, and destination SHA-256. Sanitized files intentionally have different source and destination hashes. After each write the script checks the destination and rechecks the source. The completed manifest is written last.

The script rejects reparse points in source and destination ancestors and tree entries, validates every relative path, and verifies containment under the fixed target before writes. Files use create-new semantics. There is no recursive reset, delete, or move. A second run only verifies an exact completed target; extra, missing, or changed files fail. An interrupted partial target is deliberately left intact and subsequent preparation refuses it, preserving evidence instead of erasing it.

Run preparation from PowerShell 7.2 or later:

```powershell
& .\quarryguard-lab\integration\prepare-full-runtime.ps1
```

Verify a completed target without preparing one:

```powershell
& .\quarryguard-lab\integration\prepare-full-runtime.ps1 -CheckOnly
```

The current runner passes its own explicit `-Xms512M`, configurable `-Xmx`
(4 GiB for this profile), and `-Dspark.backgroundProfiler=false`, followed by
`@launch.args` and `nogui`. It does not read `lab-jvm-args.txt`. It holds the
named mutex, verifies actual listeners, and saves logs. `-CheckOnly` above
checks an untouched freshly prepared copy only; after execution has generated
world/config/log files it deliberately no longer certifies an exact match.
