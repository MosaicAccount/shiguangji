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
- `mvn -pl shiguangji-admin -am test`: run backend tests and required modules.
- `java -jar shiguangji-admin/target/shiguangji-admin.jar`: run the packaged API (default port `18080`).

## Coding Style & Naming Conventions

Follow nearby code; no repository-wide formatter or linter is configured. Use two-space indentation in Vue/TypeScript and four spaces in Java, with Java braces on new lines. Name Vue components and Java types in PascalCase, TypeScript identifiers in camelCase, and constants in `UPPER_SNAKE_CASE`. Keep API types in `src/types/api/` aligned with clients in `src/api/`.

## Testing Guidelines

Backend tests use JUnit 5, Spring Boot Test, MockMvc, and AssertJ. Place tests under `src/test/java` and name classes `*Test`. The existing smoke test uses the `dev` profile and expects MySQL on `localhost:13306` and Redis on `localhost:16379`; initialize schemas from `shiguangji-server/sql/`. There is no frontend test runner or coverage threshold yet, so at minimum run type-checking and a production build for UI changes.

Every change must be tested before it is considered complete. Run the checks relevant to the affected application and record the results. If a change touches existing behavior or shared code, also run regression tests for the previously supported flows; do not accept regressions in existing functionality.

## Commit & Pull Request Guidelines

Use prefixes such as `feat:`, `fix:`, `docs:`, `test:`, and `chore:`. Keep subjects concise and include task IDs when applicable. Pull requests should describe behavior changes, list verification commands, link the issue/task, note SQL or configuration changes, and include screenshots for visible UI work.

Create a Git commit for every completed change. Do not leave an implemented change only in the working tree; commit it after the required tests pass.

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
