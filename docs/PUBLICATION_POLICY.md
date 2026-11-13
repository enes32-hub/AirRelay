# Publication policy — what may enter the public repository

This repository is public, and it is read by people who did not build this project: a Google Play
reviewer, a security researcher, a downstream packager reusing the GPL-3.0 source. Every file that
is committed is therefore treated as a publication, not as a working note.

Three rules apply. They are enforced mechanically by
[`scripts/check_publication.py`](../scripts/check_publication.py), which
[`scripts/run_full_check.ps1`](../scripts/run_full_check.ps1) runs on every check cycle, so a
violation fails the gate instead of reaching the remote.

---

## Rule 1 — English only

Every published document is written in English:

- `README.md`, `ACCEPTANCE.md`
- everything under [`legal/`](../legal/), [`docs/`](../docs/) and [`artifacts/`](../artifacts/)
- the two bundled legal documents shown on the TV:
  [`OPEN_SOURCE_NOTICES.txt`](../app/src/main/assets/OPEN_SOURCE_NOTICES.txt) and
  [`PRIVACY_POLICY.txt`](../app/src/main/assets/PRIVACY_POLICY.txt)
- the published pages [`index.html`](../index.html) and [`privacy.html`](../privacy.html)
- the English string set `app/src/main/res/values/`

**Permitted exceptions, and only these:**

| Exception | Why |
|---|---|
| The author's name, `Mahmut Enes Bozdoğan` | A legal name is written as it is spelled; it is not translated. |
| `app/src/main/res/values-*/` | These are the product's sixteen translations, shown to users. They are user-interface text, not documentation. |
| `app/src/main/cpp/third_party/` | Vendored upstream sources, reproduced verbatim. Their wording is not ours to edit. |
| App language names in code (`AppLanguages.kt`) | A language selector must name languages in their own script. |

## Rule 2 — No private information

None of the following may appear anywhere in the published tree, documentation or code:

- home-directory paths — `C:\Users\<name>\…`, `/home/<name>/…`
- private or link-local network addresses — `10.x`, `172.16–31.x`, `192.168.x`, `100.64–127.x`
  (loopback `127.0.0.1` and `0.0.0.0` are fine; they describe a local test server)
- MAC addresses, device serials, hostnames of the developer's own machines
- credentials of any kind: keystore or key passwords, API tokens (`ghp_…`, `AKIA…`, `xox…`),
  private keys, `storePassword=`/`keyPassword=` with a literal value
- personal e-mail addresses other than the published project contact, **alquariz@yandex.com**

The release keystore, `local.properties` and any signing material are gitignored and must never be
committed. If one is committed by accident, the history — not only the file — has to be cleaned.

## Rule 3 — No unfinished text

A published document may not contain:

- `TODO`, `FIXME`, `TBD`, `TBA`
- placeholder markup: `<FILL THIS IN>`, `<SOURCE-URL-NOT-SET>`, `[insert …]`, "fill this in",
  "buraya yaz", "doldurun"
- an unresolved licence value in a shipped legal document: `"licenseDeclared": "NOASSERTION"`

If a fact is not known yet, the document says so in a finished sentence — the honest-state sections
in [ACCEPTANCE.md](../ACCEPTANCE.md) are the model: they name exactly what has not been measured
instead of leaving a blank.

---

## What stays local and never reaches GitHub

The delivery package on the developer's desktop, the Turkish working notes (the requirement
specification, the Play Console walkthrough, the runbook notes, the R8 mappings, the evidence
status summary) and the acceptance bookkeeping are all gitignored. They are useful to the
developer and irrelevant — sometimes confusing — to a public reader.

`scripts/export_source_zip.py` exports **only Git-tracked files**, so a published
corresponding-source archive cannot accidentally carry one of those local notes.

## Adding a new published document

1. Write it in English.
2. Do not paste a machine path, an address, a token or a personal e-mail into it.
3. Do not leave a placeholder; state the unknown explicitly.
4. Run `python scripts/check_publication.py` (or the full gate) before committing.

## Verifying the rule by hand

```powershell
pwsh -File scripts/run_full_check.ps1     # includes the publication gate
python scripts/check_publication.py       # the same check on its own
```

The checker prints every violation with `file:line`, so a failure names the exact line to fix.
