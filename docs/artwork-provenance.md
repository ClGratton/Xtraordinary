# Artwork provenance register

Status: **inventory complete; rights evidence incomplete**. Last verified 20 August 2026.

The machine-readable register is `docs/artwork-provenance.tsv`. It binds every production and retained concept PNG to its dimensions, SHA-256, introducing commit, timestamp, and Git author. `scripts/check-artwork-provenance.ps1` rejects an added, removed, or changed tracked artwork file until this register is deliberately updated.

Git history proves when and by whom the files entered this repository. It does **not** prove who owned the source image, which generation account or model was used, the prompt/reference chain, or which commercial-output terms applied. Those fields remain explicitly `MISSING`; no public release may describe the art as rights-cleared until they are replaced with dated evidence and a human approval record.

For derived X3 crops, the register names the full-size production parent. Clearing a crop requires clearing its parent plus confirming that the transformation did not introduce another source.
