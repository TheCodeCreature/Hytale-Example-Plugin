@~/.claude/knowledge/index.md
@knowledge/index.md

## Coding knowledge base

This repo keeps its knowledge base **committed to git**, not in a gitignored `CLAUDE.local.md` — this is a personal fork/learning project (origin `TheCodeCreature/Hytale-Example-Plugin`, upstream `Kaupenjoe/Hytale-Example-Plugin`), so the notes are meant to travel with the code.

Two tiers, both imported above:

- **Global** (`~/.claude/knowledge/`) — cross-project guides and patterns, personal to this machine. The import above only resolves on machines with that directory; that's a known, accepted tradeoff of keeping this file committed.
- **This repo's own** (`knowledge/`, committed at the repo root) — hard, repo-specific rules for this plugin.

- Before writing or reviewing code in this repo, check `knowledge/conventions/` and the global `guides/` for established practice.
- Repo-specific rule → `knowledge/conventions/` (`type: rule`), linked from its `index.md`, logged in `knowledge/log.md`.
- Cross-project guidance → global `~/.claude/knowledge/guides/` (`type: guide`), wikilinked and tagged per `~/.claude/knowledge/meta/graph-conventions.md`. No hub-linking — see that file's rules.
- Don't ask permission to read/write `knowledge/` — it's expected as normal work here, same as editing source or docs.
