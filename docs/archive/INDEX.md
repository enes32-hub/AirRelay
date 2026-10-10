# Documentation archive index

This index records material that is kept for history but is **not** current guidance. Anything
listed here must not be used to build, ship or review the receiver; use the active documents
instead (`README.md`, `docs/ARCHITECTURE.md`, `docs/BUILD_ENVIRONMENT.md`, `docs/TEST_PLAN.md`,
`docs/DESIGN_REVIEW.md`, `docs/R40_WORKLIST.md`, `legal/NOTICE.md`).

## Archived here

| Item | Why it is archived | Superseded by |
|---|---|---|
| `legal/SBOM-0.0.31-provisional.spdx.json` | The previous provisional SBOM. It named the product "AynaTV Receiver" at version 0.0.31, and its `creationInfo` credited `tools/generate_sbom.py`, which did not exist — the file could not be regenerated or audited. | `legal/SBOM.spdx.json`, now produced by `tools/generate_sbom.py` from `gradle/libs.versions.toml` and `docs/research/native-audit.lock.json` |

## Recommended for archiving (not moved yet)

These are still in `docs/` and contradict the current state. They should be moved here with a
short note each, once their live content has been folded into the active documents.

| Item | Contradiction |
|---|---|
| `docs/VELORA_CAST_USER_GUIDE.md` | Describes the product as **Velora Cast**, an abandoned brand candidate. The product is AirRelay. |
| `docs/BRAND_CANDIDATES_2026-09-10.md` | Lists brand candidates including names that are not the product name; kept only as design history. |
| `docs/ARCHITECTURE.md` | States that the design is not yet implemented, which stopped being true several revisions ago. Needs a rewrite, not only an archive. |
| `docs/CLASSIC_RC13/14/15_2026-09-11.md`, `docs/R28_*`..`docs/R35_*`, `docs/PRODUCT_RC1_2026-09-10.md`, `docs/REVISION2_INTEGRATION.md` | Per-revision working notes whose states were superseded; they describe completed or abandoned intermediate work. |
| `docs/YAPILACAK_GELISTIRMELER.md` | Turkish-named backlog that overlaps `docs/R40_WORKLIST.md`; the worklist is the maintained one. |

## Rules

1. Moving a document here is a reviewed edit: state the reason and the replacing document in the
   table above in the same change.
2. Do not delete archived material. Provenance and review history are part of the licence
   obligations for a GPL-3.0 distribution.
3. The active set is English; archived Turkish notes may stay in their original language, since
   they are history rather than guidance.
