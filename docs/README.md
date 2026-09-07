# HighAudio documentation index

**Status:** canonical navigation document  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last architecture review:** 2026-09-07  
**Search tags:** `DOC-INDEX`, `CANONICAL`, `SEARCH-CONVENTIONS`, `UPDATE-RULES`

## Why the documentation is split this way

This repository uses a lightweight docs-as-code structure inspired by arc42's separation of goals/constraints, building blocks, runtime behavior, decisions, risks, and glossary, plus small Architecture Decision Records (ADRs). The objective is not process ceremony. It is to make it difficult for an old assumption to be rediscovered later and repeated as fact.

The authoritative information is intentionally split by **kind of knowledge**:

| Need | Canonical file |
|---|---|
| What is proven about exact versions? | [`VERIFIED-FACTS.md`](VERIFIED-FACTS.md) |
| What architecture are we currently building? | [`ARCHITECTURE.md`](ARCHITECTURE.md) |
| Why did we make an important choice? | [`decisions/`](decisions/) |
| What has not been proven and needs code/runtime evidence? | [`PROTOTYPES.md`](PROTOTYPES.md) |
| What do we build next and what gate must pass? | [`ROADMAP.md`](ROADMAP.md) |
| What can still hurt the project? | [`RISKS.md`](RISKS.md) |
| Where did a claim/library/license come from? | [`SOURCES.md`](SOURCES.md) |
| What does a term mean? | [`GLOSSARY.md`](GLOSSARY.md) |

## Authority order

When documents disagree, use this order:

1. **Completed prototype/runtime result** for the exact target stack.
2. **Pinned exact-version source fact** in `VERIFIED-FACTS.md`.
3. **Accepted ADR**, provided its assumptions have not been invalidated by newer facts/results.
4. Current `ARCHITECTURE.md`.
5. `ROADMAP.md` and design notes.
6. Old research prose, chat history, CC:HQ behavior, or assumptions.

A result which invalidates an ADR does not get hidden. Mark the ADR `Superseded` or `Rejected`, link the replacement, and update the affected fact/risk/roadmap entries.

## Search vocabulary

Use these exact prefixes in code reviews, commits, issues, and docs:

- `FACT-CCT-*` — CC:Tweaked behavior.
- `FACT-NF-*` — NeoForge behavior.
- `FACT-MC-*` — Minecraft sound-engine behavior.
- `FACT-AL-*` — OpenAL/OpenAL Soft behavior.
- `FACT-SPR-*` — Sound Physics Remastered behavior.
- `FACT-CODEC-*` — codec/library behavior.
- `ADR-*` — architecture decisions.
- `EXP-*` — runtime/build experiments.
- `RISK-*` — architecture/performance/compatibility risks.
- `MILESTONE-*` — roadmap stages.

Status vocabulary:

- `[VERIFIED]`
- `[INFERENCE]`
- `[ACCEPTED]`
- `[PROPOSED]`
- `[EXPERIMENT]`
- `[DEFERRED]`
- `[REJECTED]`
- `[SUPERSEDED]`

Do not use words such as “confirmed”, “supported”, or “safe” for behavior still tagged `[EXPERIMENT]`.

## Version discipline

A source claim must state the version/ref it proves.

For CC:Tweaked, architecture claims use the exact tag:

```text
v1.21.1-1.120.0
```

Current `main`, CC:T 1.120.2, or another Minecraft line can be cited only as comparative information and must be labelled as such.

NeoForge implementation work initially targets 21.1.247 and must be tested on both 21.1.247 and 21.1.248 before both are advertised. A nearby NeoForge Javadoc/doc page can establish an API concept, but patch-level compatibility still requires compile/runtime proof when it matters.

## How to add a fact

A fact entry should answer:

1. What exactly is true?
2. Which version/ref proves it?
3. Where is the source?
4. What does it imply for HighAudio?
5. Is there any important caveat?

Example ID:

```text
FACT-CCT-006
```

Do not put preferences in `VERIFIED-FACTS.md`; use an ADR.

## How to add an ADR

Use the Nygard-style shape:

```text
Title
Status
Date
Context
Decision
Alternatives considered
Consequences
Validation / evidence
Supersedes / superseded by
```

Only create ADRs for choices that materially affect structure, external dependencies, interfaces, lifecycle, compatibility, or important quality characteristics.

A proposed design which depends on an unrun prototype remains `Proposed`.

## How to record an experiment

Every `EXP-*` must include:

- question being answered;
- smallest implementation needed;
- exact versions;
- procedure;
- observable pass/fail criteria;
- artifacts/logging to keep;
- decisions that the result may accept/reject;
- result section, initially `NOT RUN`.

Do not turn a prototype into production code by accident. If it passes, extract the validated mechanism into the real architecture in the next milestone.

## Documentation update rule

Any change which alters one of these must update docs in the same change or immediately adjacent change:

- supported version;
- CC:T integration mechanism;
- client playback backend;
- session authority/timeline;
- network payload format/version;
- identity semantics;
- cache/content format;
- synchronization mechanism;
- lifecycle semantics;
- security/resource limit;
- codec dependency;
- SPR integration mechanism.

## Known unresolved product choices

These are intentionally **not** decisions yet:

- whether a session stops on speaker chunk unload or keeps advancing logically;
- whether a singleplayer pause pauses HighAudio's semantic clock;
- whether turtle/pocket speaker HighAudio methods are supported immediately or initially return unsupported;
- final synced-group Lua API shape;
- final mod id / Java package root / project license;
- exact audible/prefetch range;
- exact source/cache/upload limits.

Do not silently choose these while implementing unrelated code.
