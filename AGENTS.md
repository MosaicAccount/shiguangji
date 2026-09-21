# Repository Guidelines

## Project Structure & Module Organization

This is a single Git monorepo containing two applications. `shiguangji-web/` is a Vue 3, TypeScript, Vite, Pinia, and Element Plus client. In `src/`, pages are under `views/`, reusable UI under `components/`, HTTP clients under `api/`, and Pinia modules under `store/`; static resources use `assets/` and `public/`.

`shiguangji-server/` is a Java 17, Spring Boot multi-module Maven project. `shiguangji-admin` is the executable entry point; `business`, `system`, `framework`, `common`, `quartz`, and `generator` separate domain and infrastructure concerns. MyBatis XML belongs in each module's `src/main/resources/mapper/`. Database scripts live in `sql/`.

## Build, Test, and Development Commands

- `cd shiguangji-web && npm ci`: install the locked frontend dependencies.
- `npm run dev`: start Vite's local development server.
- `npm run build:prod`: create the production bundle in `dist/`.
- `npx vue-tsc --noEmit`: type-check Vue and TypeScript sources.
- `cd shiguangji-server && mvn clean package -DskipTests`: compile and package all backend modules.
- `mvn -pl shiguangji-admin -am test`: run backend tests and required modules. The smoke tests use the `dev` profile against MySQL on `localhost:13306` and Redis on `localhost:16379`, so start those containers first.
- The backend targets **Java 17**, so `mvn` itself must run on a JDK 17+. Check with `mvn -version`: a shell whose `JAVA_HOME` points at an older JDK (a jenv / asdf default, or macOS' bundled 1.8) dies with `无效的目标发行版: 17` before compiling anything. On macOS: `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn ...`.
- `java -jar shiguangji-admin/target/shiguangji-admin.jar`: run the packaged API (default port `18080`).

## Coding Style & Naming Conventions

Follow nearby code; no repository-wide formatter or linter is configured. Use two-space indentation in Vue/TypeScript and four spaces in Java, with Java braces on new lines. Name Vue components and Java types in PascalCase, TypeScript identifiers in camelCase, and constants in `UPPER_SNAKE_CASE`. Keep API types in `src/types/api/` aligned with clients in `src/api/`.

## Testing Guidelines

Backend tests use JUnit 5, Spring Boot Test, MockMvc, and AssertJ. Place tests under `src/test/java` and name classes `*Test`. The existing smoke test uses the `dev` profile and expects MySQL on `localhost:13306` and Redis on `localhost:16379`; initialize schemas from `shiguangji-server/sql/`. There is no frontend test runner or coverage threshold yet, so at minimum run type-checking and a production build for UI changes.

Every change must be tested before it is considered complete. Run the checks relevant to the affected application and record the results. If a change touches existing behavior or shared code, also run regression tests for the previously supported flows; do not accept regressions in existing functionality.

## Commit & Pull Request Guidelines

Use prefixes such as `feat:`, `fix:`, `docs:`, `test:`, and `chore:`. Keep subjects concise and include task IDs when applicable. Pull requests should describe behavior changes, list verification commands, link the issue/task, note SQL or configuration changes, and include screenshots for visible UI work.

Create a Git commit for every completed change. Do not leave an implemented change only in the working tree; commit it after the required tests pass.

**Commit in small steps.** One commit = one reviewable change, not one issue. An issue that lands as a single 500-line diff across backend + frontend + docs cannot be reviewed, tested or reverted as a unit. Split by concern — a DDL change, one endpoint, one page's entry point, a doc alignment, the tests for one of them — and commit each once its own check passes. The issue's commit list should read as the outline of the work; if a commit message needs "and" to describe it, it is two commits.

Every commit must leave the tree in a working state: the type-check, the tests and the build relevant to what it touched still pass at that commit.

### Branch workflow: `develop` is receive-only

`develop` accepts direct commits for exactly three things:

1. **global documents that no single feature owns** — finalizing the version requirement docs, product planning, overall design diagrams, project architecture, and similar cross-cutting docs. Judging by scope, not by file path: `/docs/design/*` may belong here (整体设计图) or to the worktree branch described below (某个功能的详细设计); the test is whether the document is about the product as a whole or about one feature;
2. version bumps / version changes;
3. **global-impact files that no single feature owns** — `AGENTS.md`, `CLAUDE.md`, `.gitignore`, and similar repo-wide config. These are not tied to one workstream, so routing them through a feature branch only splits them across branches.

Everything else — **a single feature's** design docs, code, tests, feature config — goes through a worktree branch named after that feature, cut from the latest `develop`, never a direct edit in the `develop` working tree. The feature's docs and code live in the same branch (e.g. 草稿箱: its design doc and its implementation are both in `feat/note-draft`):

```bash
git worktree add -b <type>/<topic> .worktrees/<topic> develop
# ...work and commit inside .worktrees/<topic>...
```

Merge back with a PR. If a stray change lands in the `develop` working tree, move it onto a worktree branch before doing anything else. The worktree directory lives under `.worktrees/`, excluded locally via `.git/info/exclude` so it never shows up as untracked and `.gitignore` stays untouched.

A worktree has no `node_modules` of its own: symlink the main checkout's in (`ln -s ../../../shiguangji-web/node_modules node_modules`) so the checks can run. But `npm install` **replaces that symlink with a real directory** — the package then looks installed in this worktree while every other worktree silently loses it. To add something the shared install is missing, run `npm install --no-save <pkg>` in the main checkout instead.

**A finished branch becomes a pull request.** Commit → push the branch → open the PR against `develop`. A branch that only exists locally is not finished work. The PR body carries what the title cannot: the behavior change, the verification commands you actually ran with their results, the linked issue(s), any SQL / configuration change, and for visible UI work the design mockup it implements plus the manual steps nobody-but-a-human can run (say plainly when you could not run them).

## Security & Configuration

Use environment overrides documented by `application*.yml` and `.env.*`. Never commit production database, Redis, or JWT credentials; provide secrets through environment variables such as `MYSQL_URL`, `REDIS_HOST`, and `JWT_SECRET`. 

# Ponytail, lazy senior dev mode

[](https://github.com/DietrichGebert/ponytail/blob/main/AGENTS.md#ponytail-lazy-senior-dev-mode)

You are a lazy senior developer. Lazy means efficient, not careless. The best code is the code never written.

Before writing any code, stop at the first rung that holds:

2. Does this need to be built at all? (YAGNI)
3. Does it already exist in this codebase? Reuse the helper, util, or pattern that's already here, don't re-write it.
4. Does the standard library already do this? Use it.
5. Does a native platform feature cover it? Use it.
6. Does an already-installed dependency solve it? Use it.
7. Can this be one line? Make it one line.
8. Only then: write the minimum code that works.

The ladder runs after you understand the problem, not 
instead of it: read the task and the code it touches, trace the real 
flow end to end, then climb.

Bug fix = root cause, not symptom: a report names a 
symptom. Grep every caller of the function you touch and fix the shared 
function once — one guard there is a smaller diff than one per caller, 
and patching only the path the ticket names leaves a sibling caller 
still broken.

Rules:

- No abstractions that weren't explicitly requested.
- No new dependency if it can be avoided.
- No boilerplate nobody asked for.
- Deletion over addition. Boring over clever. Fewest files possible.
- Shortest working diff wins, but only once you understand the 
  problem. The smallest change in the wrong place isn't lazy, it's a 
  second bug.
- Question complex requests: "Do you actually need X, or does Y cover it?"
- Pick the edge-case-correct option when two stdlib approaches are the
   same size, lazy means less code, not the flimsier algorithm.
- Mark deliberate simplifications that cut a real corner with a known ceiling (global lock, O(n²) scan, naive heuristic) with a `ponytail:` comment naming the ceiling and upgrade path.

Not lazy about: understanding the problem (read it fully 
and trace the real flow before picking a rung, a small diff you don't 
understand is just laziness dressed up as efficiency), input validation 
at trust boundaries, error handling that prevents data loss, security, 
accessibility, the calibration real hardware needs (the platform is 
never the spec ideal, a clock drifts, a sensor reads off), anything 
explicitly requested. Lazy code without its check is unfinished: 
non-trivial logic leaves ONE runnable check behind, the smallest thing 
that fails if the logic breaks (an assert-based demo/self-check or one 
small test file; no frameworks, no fixtures). Trivial one-liners need no
 test.

(Yes, this file also applies to agents working on the ponytail repo itself. Especially to them.)

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **shiguangji** (8253 symbols, 19362 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> Index stale? Run `node .gitnexus/run.cjs analyze` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? `npx gitnexus analyze` (npm 11 crash → `npm i -g gitnexus`; #1939).

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows. For regression review, compare against the default branch: `detect_changes({scope: "compare", base_ref: "main"})`.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit changes without running `detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/shiguangji/context` | Codebase overview, check index freshness |
| `gitnexus://repo/shiguangji/clusters` | All functional areas |
| `gitnexus://repo/shiguangji/processes` | All execution flows |
| `gitnexus://repo/shiguangji/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
