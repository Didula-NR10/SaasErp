# Phase 04 security CI correction — 2026-11-28

This correction changes CI discovery and verification only. The last authentication/authorization backend result remains 350 passing automated tests; Maven and backend tests were not rerun for this correction. No application code, migrations, backend tests, architecture or production infrastructure changed.

## Exact Gitleaks false positives

The owner manually reviewed the two findings as ordinary security-policy vocabulary, not credentials. Local `git rev-parse HEAD` and `git show --no-patch` resolve the full commit to `f8732921a9b1127064990557c2eb407c9efbc901` (`feat: implement secure authentication and authorization foundation`). Reading each file with `git show COMMIT:PATH` and numbering the historical lines confirms:

| Historical file and line | Owner-reviewed justification |
| --- | --- |
| `docs/security/phase04-authentication-foundation.md:107` | The test-coverage row lists disabled, pending, unverified, MFA and privileged denial scenarios. The slash-separated words describe policy states; they do not authenticate to any service. |
| `docs/security/security-acceptance-criteria.md:191` | AC-S-01 requires denial for pending, disabled and unverified accounts. The slash-separated account states are ordinary acceptance criteria, not an API key. |

The root `.gitleaksignore` contains exactly these two finding fingerprints:

```text
f8732921a9b1127064990557c2eb407c9efbc901:docs/security/phase04-authentication-foundation.md:generic-api-key:107
f8732921a9b1127064990557c2eb407c9efbc901:docs/security/security-acceptance-criteria.md:generic-api-key:191
```

The format is `commit:file:rule:line`, verified against the [Gitleaks v8.30.1 example and fingerprint documentation](https://github.com/gitleaks/gitleaks/blob/v8.30.1/README.md#gitleaksignore). These exceptions apply only to the historical findings. Full checkout history (`fetch-depth: 0`), redaction, all detection rules, `gitleaks git` and `--exit-code 1` remain unchanged. No Markdown exclusion, rule disablement, broad allowlist or history rewrite is introduced.

## Trivy discovery root cause and pinned-source review

The failed command supplied the executable application JAR itself as the filesystem scan root. In v0.76.2 the [filesystem walker](https://github.com/aquasecurity/trivy/blob/v0.76.2/pkg/fanal/walker/fs.go) computes paths relative to that root. For the root file this is `.`. The [Java JAR analyzer](https://github.com/aquasecurity/trivy/blob/v0.76.2/pkg/fanal/analyzer/language/java/jar/jar.go) selects files by extension, so that input does not select the JAR analyzer. This explains the reported zero language-specific files; it is not evidence of an empty dependency set. The previous inventory gate correctly failed, but its substring-based path matching also needed replacement.

The exact supported flags were checked in the [v0.76.2 filesystem CLI reference](https://github.com/aquasecurity/trivy/blob/v0.76.2/docs/guide/references/configuration/cli/trivy_filesystem.md), [report flags](https://github.com/aquasecurity/trivy/blob/v0.76.2/pkg/flag/report_flags.go) and [vulnerability flags](https://github.com/aquasecurity/trivy/blob/v0.76.2/pkg/flag/vulnerability_flags.go). Empty config/ignore-file arguments disable repository configuration/ignore loading; JSON package listing includes packages without vulnerabilities. Unfixed findings remain included.

The [report structs](https://github.com/aquasecurity/trivy/blob/v0.76.2/pkg/types/report.go) expose `Results[].Class`, `Type`, `Packages` and `Vulnerabilities`. The [language scanner](https://github.com/aquasecurity/trivy/blob/v0.76.2/pkg/scan/langpkg/scan.go) emits `Class: lang-pkgs`, `Type: jar`; `Target` can be an application path or an aggregate `Java` label. The [application aggregator](https://github.com/aquasecurity/trivy/blob/v0.76.2/pkg/fanal/applier/docker.go) merges JAR package lists without replacing their package paths. Therefore `Target` is not used as a substitute for package paths. The [JAR properties conversion](https://github.com/aquasecurity/trivy/blob/v0.76.2/pkg/dependency/parser/java/jar/types.go), [JAR parser](https://github.com/aquasecurity/trivy/blob/v0.76.2/pkg/dependency/parser/java/jar/parse.go) and [language analyzer](https://github.com/aquasecurity/trivy/blob/v0.76.2/pkg/fanal/analyzer/language/analyze.go) preserve each archive's path in `Packages[].FilePath`, with Maven `Name` in `group:artifact` form and `Version`. [Package identifier serialization](https://github.com/aquasecurity/trivy/blob/v0.76.2/pkg/fanal/types/package.go) exposes `Identifier.PURL`. Schema version 2 is also demonstrated by the pinned [official Java report fixture](https://github.com/aquasecurity/trivy/blob/v0.76.2/integration/testdata/spring4shell-jre11.json.golden).

## Corrected artifact scan and inventory enforcement

The workflow invokes:

```sh
node .github/scripts/scan-boot-jar.mjs
```

The helper creates a unique directory under `RUNNER_TEMP`. `PrepareBootScan.java` runs with the existing JDK 21 and standard ZIP/manifest APIs, without Maven or new dependencies. It requires exactly one regular `.jar` candidate, a Spring Boot `JarLauncher` manifest with `Start-Class`, compiled launcher/application class headers, and a nonempty embedded dependency inventory. Multiple JAR candidates are rejected rather than guessed.

Before writing files, it rejects duplicate normalized paths, absolute paths, traversal, backslashes, control characters, file/directory collisions, nested or non-JAR library entries and oversized archives/entries. Only direct allowlisted `BOOT-INF/lib/*.jar` byte streams are copied into a fresh directory using `CREATE_NEW`. No archive paths outside that allowlist are extracted, and no archive permissions, symlinks or other filesystem metadata are restored. Each copied dependency must match its declared size/CRC and open as a nonempty ZIP/JAR. Bounds are 100,000 entries, 128 MiB per entry and 1 GiB aggregate archive/expanded size. The expected inventory is recorded from these actual entries, outside the scan directory.

The actual scanner invocation is equivalent to:

```sh
/usr/local/bin/trivy rootfs \
  --config '' \
  --ignorefile '' \
  --ignore-unfixed=false \
  --scanners vuln \
  --pkg-types library \
  --severity HIGH,CRITICAL \
  --exit-code 1 \
  --list-all-pkgs \
  --format json \
  --output "$work/report.json" \
  "$work/scan"
```

The scan root contains the actual embedded JAR bytes at `BOOT-INF/lib/<filename>.jar`; it does not substitute POM scanning. The validator requires nonempty schema-2 JSON results and exact scan-root-relative `Packages[].FilePath` equality for every expected library. Basename, substring, absolute-path, nested-JAR and `Target` substitutes do not count. Every matching package must have a valid nonempty Maven group/artifact/version and a versioned Maven PURL whose decoded identity agrees with `Name` and `Version`. Missing coverage, incomplete identity, empty/missing/malformed reports and malformed vulnerability structures fail closed. HIGH/CRITICAL findings also fail validation if the scanner unexpectedly returns zero.

The original scanner status is retained even if inventory validation fails, so a vulnerability/runtime error cannot be replaced with success or masked by a validator exit code. Existing dependency and infrastructure scans, scanner pins, Maven caching, vulnerability/Java database caches and job/check names (`Gitleaks Secret Scan`, `Trivy Vulnerability Scan`) remain unchanged. No continue-on-error, vulnerability suppression or fabricated production inventory is added.

## Local verification and remaining Actions evidence

- `node --test .github/scripts/security-ci.test.mjs`: **16 tests passed, zero failures, cancellations or skips**. Fixtures exercise complete/missing inventory, empty/malformed reports and identities, unsafe/duplicate archive entries, application artifact requirements, actual byte extraction and scanner failure status preservation (including statuses 1, 2 and 17 with valid, malformed or missing reports). Report/scanner mocks are test fixtures only.
- `node --check` passed for both CI JavaScript files. The regression suite compiles only the standalone CI extractor with `javac`; it does not compile or test application code.
- The extractor processed the already existing `Backend/target/erp-0.0.1-SNAPSHOT.jar`: **114 dependency JARs**, each independently compared byte-for-byte against `unzip -p` of the original artifact. This is extraction evidence, not a Trivy identification/vulnerability result.
- Workflow YAML parsed with duplicate-key rejection using the already cached SnakeYAML library. A structural comparison against the original workflow confirmed that all prior settings remain identical except the intended artifact scan command and added regression step; all **11 workflow run blocks passed `bash -n`**. `git diff --check` also passed.

Neither scanner is installed locally. No local Trivy Java database was downloaded, Docker image rebuilt, Maven command run, or commit/push made. GitHub Actions must still execute the corrected workflow with the pinned scanners and refreshed databases, confirm full-history Gitleaks behavior with exactly these two exceptions, identify every packaged dependency, enforce HIGH/CRITICAL findings including unfixed findings, and retain both required check results. No actual scanner success is claimed here.


## SonarCloud PR #4 security and immutable-migration correction

The public project settings for `Didula-NR10_SaasErp` returned `sonar.autoscan.enabled=true`. Repository inspection found no SonarScanner workflow, Maven analysis configuration, `sonar-project.properties` or prior `.sonarcloud.properties`. The PR check is published by the SonarQube Cloud GitHub application. This correction preserves automatic analysis; it does not add a second CI analysis.

### Artifact scanner trust boundaries

| Reported issue | Correction |
| --- | --- |
| `jssecurity:S8707`, CLI path traversal | The production entrypoint accepts no arguments. Repository root comes from `import.meta.url`; the only target is its fixed `Backend/target` directory and the only extractor is the adjacent checked-in helper. Temp storage is derived from the default hosted checkout layout and must equal `RUNNER_TEMP`. Cache storage must be the canonical `trivy-cache` child and must equal `TRIVY_CACHE_DIR`. Relative, option-like, malformed, unexpected and symlink-resolved paths and invalid directory types deny before tools run. |
| `jssecurity:S8705`, command argument injection | CLI paths are removed. Process arguments are arrays of fixed options and validated absolute paths; `shell: false` is explicit. No shell command concatenation or interpolation is used. |
| `javascript:S4036`, Java PATH resolution | Java is the validated absolute `JAVA_HOME/bin/java` from the fixed `/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/21.<version>/x64` installation provisioned by `actions/setup-java@v5`. The canonical installation, JDK descendant directories, executable type, ownership, execute permission and nonwritable group/other modes are checked. There is no fallback to PATH. |
| `javascript:S4036`, Trivy PATH resolution | The scanner validates and invokes `/usr/local/bin/trivy`, matching the workflow's installation destination. A missing, empty, nonexecutable, symlinked or group/other-writable executable fails closed. There is no CLI/environment override for this executable. |

The JDK naming and `JAVA_HOME` provisioning were checked against the official [setup-java base installer](https://github.com/actions/setup-java/blob/v5/src/distributions/base-installer.ts) and [Temurin installer](https://github.com/actions/setup-java/blob/v5/src/distributions/temurin/installer.ts). GitHub's [hosted-image environment setup](https://github.com/actions/runner-images/blob/main/images/ubuntu/scripts/build/configure-environment.sh) deliberately makes the top-level tool-cache container writable. That fixed provider-owned/current-user-owned container is accepted; protected JDK descendant directories and executable checks still apply. No host permissions are changed by this correction.

Child processes receive private HOME/TMPDIR directories and fixed locale values. Java receives only its validated JAVA_HOME in addition to those base values. Trivy receives only the validated database cache in addition to those base values. PATH, inherited JVM/loader options, credentials and arbitrary TRIVY_* overrides are not forwarded. The workflow's vulnerability/Java database restore/update/save paths are retained, as are rootfs mode, explicit library scanning, every-dependency path/identity validation, HIGH/CRITICAL enforcement including unfixed findings, and scanner status propagation.

`PrepareBootScan.java` is unchanged. Its duplicate/traversal/unexpected-entry, fresh-directory, regular-file, manifest/class, size/CRC and embedded-archive checks remain covered by the original regression cases. The test harness now invokes its compiler/JVM by absolute paths and substitutes Trivy only through an in-process process-runner test seam; production does not accept test configuration through arguments or environment variables.

This entrypoint deliberately supports the current `ubuntu-latest`, default checkout, x64 Temurin 21 tool-cache layout. A changed runner layout or tool distribution fails closed and needs an explicit code/test review. Filesystem ownership and tool provenance still rely on the trusted ephemeral runner; this does not sandbox arbitrary malicious code already executing as the runner user or root.

### Duplication-only exclusion for immutable Flyway history

V5–V9 deliberately preserve and replace PostgreSQL security function definitions. Refactoring prior migration bodies would change Flyway checksums and the upgrade path for already migrated databases. Moving historical definitions into mutable shared includes would also change their meaning. Applied migrations are historical records, so repeated replacement definitions are justified duplication; normal application duplication remains subject to the existing 3% gate.

The official [automatic-analysis configuration](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/automatic-analysis#additional-analysis-configuration) explicitly supports `sonar.cpd.exclusions` in `.sonarcloud.properties` on the default branch, while warning that wildcard patterns are not allowed there. The added root file therefore contains only `sonar.cpd.exclusions`, with the ten exact existing migration paths: billing V1 and platform V1–V9. It uses no wildcard and no source/security exclusion. It leaves all applicable static/security issue analysis of the migration files enabled. Java, JavaScript, infrastructure, repeatable SQL and other application sources retain normal duplication checks. No migration, security requirement ID, security rule, rating requirement or quality-gate threshold is changed.

The exact repository property value is in [`.sonarcloud.properties`](../../.sonarcloud.properties). New migrations are not silently added to that exact list; any extension requires review.

For PR #4 before that file reaches the default branch, the project administrator can configure the same exact comma-separated list in **Administration → General Settings → Analysis Scope → Duplication → Duplication Exclusions**. Alternatively, the UI supports this narrowly scoped versioned-Flyway path pattern (UI only):

```properties
sonar.cpd.exclusions=Backend/src/main/resources/db/migration/**/V*__*.sql
```

The [official duplication-exclusion documentation](https://docs.sonarsource.com/sonarqube-cloud/managing-your-projects/project-analysis/setting-analysis-scope/exclude-from-coverage-duplication) documents the property and UI path, and [path-pattern semantics](https://docs.sonarsource.com/sonarqube-cloud/appendices/defining-matching-patterns) document the wildcard form. This is a duplication-only project setting, not `sonar.exclusions`. Do not add `sonar-project.properties` for automatic analysis; it is ignored in this mode. Do not disable automatic analysis or introduce a parallel analysis job for this change.

### Verification and owner actions

The existing lightweight Node.js suite plus new negative security cases passed: **29 tests, zero failures, cancellations or skips**. This includes real standalone-extractor execution against synthetic stored/deflated JAR fixtures; malformed/missing reports; every-dependency Maven identity checks; the explicit 114-library completeness case; rootfs/library/severity flags; scanner statuses 1, 2, 17 and signal exit 143; unexpected CLI arguments; unsafe CI paths; symlink escapes; invalid/writable executable and JDK paths; and the restricted child environment/cache behavior. Script syntax checks passed. No Maven build, Trivy database download, Docker rebuild, application change, migration change, commit or push was performed.

Owner actions remain: apply the duplication-only project setting for the current PR if needed, review/land the default-branch configuration through the normal process, and trigger fresh SonarCloud and GitHub Actions runs. Confirm both the Security A rating and duplication ≤3%, review whether any remaining duplication is outside immutable migrations, and confirm the hardened runner paths/cache with live rootfs scanning of all 114 packaged libraries. Local mocked scanner tests do not establish live scanner success or Sonar issue closure. No new remote success is claimed.
