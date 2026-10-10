# Protocol core and dependency decision

Review: 31 August 2026. Effective product: [iPhone → Android TV only](SCOPE.md).
**Decision: no production core has been selected yet. Stage 0 has not passed.** This is a preliminary technical review; it is not a legal opinion, a complete licence audit or a device compatibility report.

## The user's cost preference

On 31 August 2026 the user stated that no paid/commercial SDK licence is available and that the product should be built with open source. Commercial SDK purchase or vendor discussion is therefore outside the project scope. This preference does not eliminate open source licences such as the GPL: those licences may be free of charge, but they impose a distribution obligation together with the source code, the changes and the required notices.

AirServer, receivers on the Store and similar end-user applications provide reference behaviour; their APKs or reverse-engineered protocols/assets will not be brought into AirRelay. The `uxplay-windows` example the user provided is a UxPlay package aimed at Windows and is not an Android TV core. The existing provenance ambiguity within the same UxPlay family is not resolved by this example either.

## Reviewed pinned sources

| Candidate | Reviewed commit | Route / current decision |
|---|---|---|
| [UxPlay](https://github.com/FDH2/UxPlay/tree/fd5192b06f0c2ab2f664b8ab778ac6f772e4d741) | `fd5192b06f0c2ab2f664b8ab778ac6f772e4d741` | A, GPL; awaiting provenance and Android integration review |
| [android-airplay-server](https://github.com/jqssun/android-airplay-server/tree/c8defdd70d7e6a04f4f1b71d353653682d594106) | `c8defdd70d7e6a04f4f1b71d353653682d594106` | A, GPL; first review candidate for Android feasibility, not an approval |
| [PhairPlay](https://github.com/mazer666/PhairPlay/tree/4a3948c51d7f9d050d1f7437c0bd88877ef6db0f) | `4a3948c51d7f9d050d1f7437c0bd88877ef6db0f` | Not accepted as B; the Apache-only/clean room assumption is not supported |

Research lock: [candidates.lock.json](research/candidates.lock.json). File hashes and comparison result: [provenance-evidence.json](research/provenance-evidence.json). The tool reads source files only in memory; candidate code, keys or tables are not copied into the project, compiled or executed.

## Route A

UxPlay declares GPL-3.0 and carries [a GPLv3 licence in the playfair subdirectory](https://github.com/FDH2/UxPlay/blob/fd5192b06f0c2ab2f664b8ab778ac6f772e4d741/lib/playfair/LICENSE.md). The master README that was read also includes experimental development; this HEAD cannot automatically be chosen as a stable P0 dependency. The exact commit of a stable release will be evaluated separately.

The Android adaptation's [CMake file](https://github.com/jqssun/android-airplay-server/blob/c8defdd70d7e6a04f4f1b71d353653682d594106/app/src/main/cpp/CMakeLists.txt) uses UxPlay/playfair, libplist, OpenSSL, FFmpeg and Oboe. The MediaCodec/SurfaceView and audio support in the README is a statement that helps candidate selection; it is not our device test.

[Git submodule records](https://github.com/jqssun/android-airplay-server/tree/c8defdd70d7e6a04f4f1b71d353653682d594106/app/src/main/cpp/third_party):

| Component | Pinned commit / configuration | Missing review |
|---|---|---|
| UxPlay | `462153392f2e30937424922039ff9f0cda5e7b1a` | Different from the master that was researched; file and cryptographic provenance audit |
| FFmpeg | `38b88335f99e76ed89ff3c93f877fdefce736c13` | Active codec/build flags, LGPL/GPL obligations |
| libplist | `f41b1ea67045e0c09339974d83e389972d84f166` | Per-file licences, static linking conditions |
| openssl-cmake | `4edd36a8dab5f85a8f92650b5bdf6e0cab13aab8` | Wrapper licence, downloaded source and hash |
| OpenSSL | `3.4.4` in CMake | This version was not selected for AirRelay; security and provenance review |
| Oboe and Gradle dependencies | Full inventory not produced yet | Transitive graph, version locks, source and licence files |

If Route A is chosen, the exactly corresponding source, change records, licence/NOTICE files and reproduction instructions will be included in the distribution plan. Whether these obligations are actually met will be reviewed before distribution. Commercial competitor code or assets will not be taken. The product's overall licence has not been assigned at this stage.

## Route B

The PhairPlay root [LICENSE](https://github.com/mazer666/PhairPlay/blob/4a3948c51d7f9d050d1f7437c0bd88877ef6db0f/LICENSE) file contains the Apache 2.0 text. In contrast, the [native CMake file](https://github.com/mazer666/PhairPlay/blob/4a3948c51d7f9d050d1f7437c0bd88877ef6db0f/app/src/main/cpp/CMakeLists.txt) identifies playfair as RPiPlay-derived and compiles these sources into `libplayfair.so`. These files were compared against UxPlay's GPLv3 playfair directory by hash. Five of the seven shared source files (`playfair.c`, `hand_garble.c`, `omg_hax.c`, `omg_hax.h`, `playfair.h`) have the same SHA-256 value at byte level; the other two differ. The detailed result is in the evidence JSON.

**Inference:** the root Apache text does not prove that the whole package was produced under a permissive licence and by a clean room method. This is not a court ruling or a finding of infringement against the entire project; the evidence is insufficient for AirRelay to pass specification gate 5.2. The candidate will not be taken into Route B in this state. The keys and tables will not be used unless their legally clean provenance is documented separately.

A clean room alternative does not mean writing the protocol by guessing. It requires an independent source history, a documented protocol and a legally usable cryptographic component. The current research does not provide these. [Additional candidate review](research/ALTERNATIVE_CORES.md) covers two other open repositories and two commercial SDK routes; none of them was approved. The AirPlay audio scope stated in the Apple MFi FAQ does not prove that access to an Android screen mirroring SDK exists. Membership, certification or a contract was not assumed to exist.

## Technical scoring and gate condition

| Criterion | Weight | AirRelay test result for all candidates |
|---|---:|---|
| Real iPhone connection | 25 | Not measured |
| 1080p60 stability | 20 | Not measured |
| A/V synchronisation | 15 | Not measured |
| Current iOS compatibility | 15 | Not measured |
| Licence and code provenance | 15 | No candidate passed the gate |
| Android TV integration cost | 5 | No compiled spike |
| Maintainability | 5 | Preliminary review only |

The scores were not filled in by estimation. A candidate that fails or is pending on the licence/provenance gate cannot be approved by a weighted total. The Android adaptation is only the first candidate in the review order; it is not the core selected for the main application.

For the next core decision: pinned stable source → complete file/submodule licence inventory → cryptographic provenance review → dependency/hash verification → isolated physical iPhone/Android TV spike → measurement → written decision. Before these gates, no functional APK or ready mDNS receiver will be declared on behalf of AirRelay.
