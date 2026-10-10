# Pinned native source review

31 August 2026. **The production core was not approved.** The review covers source inventory and provenance signals; it is not a legal opinion, a security scan or a compliance test.

## Scope and evidence

The [research lock](native-audit.lock.json), the [JSON report](native-source-audit.json) and the [file inventory](native-source-inventory.csv) must be read together. `scripts/audit_native_sources.py` compares each file with its Git blob SHA-1 in the commit tree and also produces SHA-256. The sources are read only in memory; third-party code, tables and keys are not copied into the working tree or executed.

| Source | Files reviewed |
|---|---:|
| Native files and configuration of the Android adaptation | 21 |
| UxPlay submodule used by the adaptation | 72 |
| RPiPlay provenance reference | 11 |
| Initial playfair reference | 10 |
| Sources/headers/licences under libplist | 57 |
| openssl-cmake configuration | 7 |
| Sources/licences selected for FFmpeg ALAC | 7 |
| **Total** | **185** |

The prefix scope also includes some unused sources; the full transitive link graph of FFmpeg has not been reviewed. A licence header signal is not licence approval, and its absence is not by itself a finding that there is no licence. All of the files are in the `MANUAL_REVIEW_REQUIRED` state.

## Main blocker: upstream's own statement of uncertainty

The Android adaptation uses `UxPlay@462153392f2e30937424922039ff9f0cda5e7b1a`. [README lines 2486–2488 of this commit](https://github.com/FDH2/UxPlay/blob/462153392f2e30937424922039ff9f0cda5e7b1a/README.md#L2486) say that the legal status of the FairPlay component is uncertain. The [RPiPlay README](https://github.com/FD-/RPiPlay/blob/64d0341ed3bef098c940c9ed0675948870a271f9/README.md#L124) contains the same uncertainty.

UxPlay's attribution chain runs RPiPlay → AirplayServer/ShairPlay/playfair. The [README of the initial playfair repository](https://github.com/EstebanKubata/playfair/blob/96c232ed7e65cccfbd1eae8e0d70e7179799290d/README.md) contains only a project title; it does not explain the independent production of the cryptographic constants or the source of the rights. Having access to the Git history and the GPL text does not by itself close this gap. Session encryption is not the same thing as content DRM; even so, the specification separately prohibits the use of uncertain material.

**Decision:** there is not enough evidence to add this core to AirRelay by accepting the GPL licence alone. This gate does not pass until the [review questions](../../legal/REVIEW_REQUEST.md) are resolved or a different core that meets the specification is provided. This result is not a finding that the component is unlawful.

## Reasons for not taking the adaptation as it is

- The [version catalog](https://github.com/jqssun/android-airplay-server/blob/c8defdd70d7e6a04f4f1b71d353653682d594106/gradle/libs.versions.toml) contains `media3 = 1.11.0-beta01`. It does not comply with the stable dependency rule in the specification; it must not be confused with our `1.11.0` research baseline.
- The [CMake](https://github.com/jqssun/android-airplay-server/blob/c8defdd70d7e6a04f4f1b71d353653682d594106/app/src/main/cpp/CMakeLists.txt) selects OpenSSL `3.4.4`. [openssl-cmake BuildOpenSSL](https://github.com/viaduck/openssl-cmake/blob/4edd36a8dab5f85a8f92650b5bdf6e0cab13aab8/cmake/BuildOpenSSL.cmake) makes hash verification depend on `OPENSSL_BUILD_HASH` being supplied and uses a mirror URL. In the parent CMake that was reviewed there is no such hash assignment. This dependency cannot be taken without the full configuration and the verified hash of the downloaded source.
- The [FFmpeg configuration](https://github.com/jqssun/android-airplay-server/blob/c8defdd70d7e6a04f4f1b71d353653682d594106/app/src/main/cpp/cmake/BuildFFmpeg.cmake) aims at a static ALAC decoder with network support turned off. The exactly corresponding output of the flags and licences, the real build graph and the source provision obligation must be reviewed separately.

These findings are not defects transferred into AirRelay: the candidate dependencies have not been added yet. `contains_private_key_pem_marker=false` is only a narrow PEM marker check; it does not prove the absence of a cryptographic table or an embedded secret.

## Reproduction

```powershell
python scripts/audit_native_sources.py
```

The pinned commits do not change; a network error or a Git blob mismatch stops result generation. The JSON and the CSV are produced from the same run. The research downloads are not the traffic of the future Android application, which is limited to the local network.
