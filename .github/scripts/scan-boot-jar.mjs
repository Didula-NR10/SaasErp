import { accessSync, constants as fsConstants, lstatSync, mkdirSync, readFileSync, realpathSync, mkdtempSync, statSync } from 'node:fs';
import { dirname, isAbsolute, join, normalize, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { constants } from 'node:os';

// Verified against Trivy v0.76.2's versioned CLI, jar parser, language analyzer
// and JSON structs; see docs/security/phase04-ci-security-correction.md.
export const SCAN_FLAGS = [
  // v0.76.2 filesystem mode disables individual package analyzers, including JARs.
  'rootfs', '--config', '', '--ignorefile', '', '--ignore-unfixed=false',
  '--scanners', 'vuln', '--pkg-types', 'library', '--severity', 'HIGH,CRITICAL', '--exit-code', '1',
  '--list-all-pkgs', '--format', 'json',
];
const libraryPath = /^BOOT-INF\/lib\/[A-Za-z0-9][A-Za-z0-9._+\-]*\.jar$/;
const coordinate = /^[A-Za-z0-9_][A-Za-z0-9_.-]*$/;
const versionPattern = /^[A-Za-z0-9][A-Za-z0-9._+~\-]*$/;
function requireValue(condition, message) {
  if (!condition) throw new Error(message);
}
function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }

function validateIdentity(pkg) {
  requireValue(typeof pkg.Name === 'string' && typeof pkg.Version === 'string', 'Missing Maven name/version');
  const [group, artifact, extra] = pkg.Name.split(':');
  requireValue(extra === undefined && coordinate.test(group ?? '') && coordinate.test(artifact ?? ''), 'Invalid Maven group:artifact identity');
  requireValue(versionPattern.test(pkg.Version) && !/^(unknown|unspecified|undefined|null)$/i.test(pkg.Version), 'Invalid Maven version');
  const purl = pkg.Identifier?.PURL;
  requireValue(typeof purl === 'string', 'Missing Maven PURL');
  // Trivy emits a Maven PackageURL; compare decoded namespace, name and version
  // to Name/Version instead of accepting an arbitrary pkg:maven/ prefix.
  const match = /^pkg:maven\/([^/@?#]+)\/([^/@?#]+)@([^/?#]+)(?:\?[^#]+)?(?:#.+)?$/.exec(purl);
  requireValue(match !== null, 'Invalid versioned Maven PURL');
  requireValue(decodeURIComponent(match[1]) === group && decodeURIComponent(match[2]) === artifact
    && decodeURIComponent(match[3]) === pkg.Version, 'Maven PURL/name/version mismatch');
}

export function validateInventory(expected, report) {
  requireValue(Array.isArray(expected) && expected.length > 0 && expected.every(p => libraryPath.test(p))
    && new Set(expected).size === expected.length, 'Invalid or empty expected embedded inventory');
  requireValue(object(report) && report.SchemaVersion === 2 && Array.isArray(report.Results)
    && report.Results.length > 0, 'Expected nonempty Trivy schema-2 Results');
  const needed = new Set(expected), identified = new Set();
  let blocked = false;
  for (const result of report.Results) {
    requireValue(object(result), 'Malformed Trivy result');
    if (result.Vulnerabilities !== undefined) {
      requireValue(Array.isArray(result.Vulnerabilities), 'Malformed vulnerability list');
      for (const finding of result.Vulnerabilities) {
        requireValue(object(finding) && typeof finding.VulnerabilityID === 'string'
          && finding.VulnerabilityID.trim().length > 0
          && ['UNKNOWN', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(finding.Severity), 'Malformed vulnerability finding');
        if (['HIGH', 'CRITICAL'].includes(finding.Severity)) blocked = true;
        console.log(`${finding.Severity}: ${finding.VulnerabilityID} ${finding.PkgName ?? ''} ${finding.InstalledVersion ?? ''}`);
      }
    }
    if (result.Type !== 'jar' || result.Class !== 'lang-pkgs') continue;
    requireValue(Array.isArray(result.Packages), 'Missing Java package list');
    for (const pkg of result.Packages) {
      requireValue(object(pkg) && typeof pkg.FilePath === 'string' && pkg.FilePath.length > 0, 'Missing Java package archive path');
      // No substring, basename or result.Target fallback. The directory walker
      // supplies scan-root-relative paths, retained in Packages[].FilePath.
      if (!needed.has(pkg.FilePath)) continue;
      validateIdentity(pkg);
      identified.add(pkg.FilePath);
    }
  }
  const missing = expected.filter(path => !identified.has(path));
  requireValue(missing.length === 0, `Trivy did not identify every embedded dependency:\n${missing.join('\n')}`);
  console.log(`Trivy identified all ${expected.length} embedded dependency JARs`);
  return blocked;
}

function statusOf(child) {
  if (child.error) throw child.error;
  return child.status ?? (128 + (constants.signals[child.signal] ?? 0));
}

const repositoryRoot = fileURLToPath(new URL('../../', import.meta.url));
const installedTools = Object.freeze({ javaCache: '/opt/hostedtoolcache', trivy: '/usr/local/bin/trivy' });

function canonicalPath(value) {
  requireValue(typeof value === 'string' && /^\/[A-Za-z0-9_./+\-]+$/.test(value)
    && isAbsolute(value) && normalize(value) === value
    && !value.split('/').some(part => part === '.' || part === '..'), 'Unsafe or noncanonical CI path');
  requireValue(realpathSync(value) === value, 'Symbolic links are not permitted in CI paths');
  return value;
}

function requireContained(root, path) {
  const suffix = relative(root, path);
  requireValue(suffix !== '' && !suffix.startsWith('..') && !isAbsolute(suffix), 'CI path is outside its trusted root');
  return path;
}

function directory(path) {
  canonicalPath(path);
  const info = lstatSync(path);
  requireValue(info.isDirectory(), 'Expected a real CI directory');
  return path;
}

function ownedPath(path) {
  const info = lstatSync(path);
  requireValue(info.uid === 0 || info.uid === process.getuid(), 'CI path has unsafe ownership');
  return path;
}

function protectedPath(path) {
  ownedPath(path);
  requireValue((lstatSync(path).mode & 0o022) === 0, 'CI path has unsafe ownership or write permissions');
  return path;
}

function protectedDirectoryTree(root, path) {
  directory(root);
  requireContained(root, path);
  let current = root;
  for (const part of relative(root, path).split('/')) {
    current = join(current, part);
    protectedPath(directory(current));
  }
  return current;
}

export function validateExecutable(path, root) {
  protectedPath(directory(root));
  requireContained(root, canonicalPath(path));
  const parent = dirname(path);
  if (parent !== root) protectedDirectoryTree(root, parent);
  const info = lstatSync(path);
  requireValue(info.isFile() && info.size > 0, 'Expected a nonempty regular CI executable');
  protectedPath(path);
  accessSync(path, fsConstants.X_OK);
  return path;
}

// Provisioning overrides are used only by in-process tests. The CLI accepts no
// paths or tool overrides; production always uses the fixed installations above.
export function createScanContext(repo, ci, provisioning = installedTools) {
  repo = directory(repo);
  const target = directory(requireContained(repo, join(repo, 'Backend', 'target')));
  const extractor = canonicalPath(requireContained(repo, join(repo, '.github', 'scripts', 'PrepareBootScan.java')));
  requireValue(lstatSync(extractor).isFile(), 'Expected the checked-in archive extractor');

  // actions/checkout's default layout on this ubuntu-latest job:
  // <work>/<repository>/<repository>, with runner.temp at <work>/_temp.
  const runnerTemp = protectedPath(directory(join(dirname(dirname(repo)), '_temp')));
  requireValue(ci.RUNNER_TEMP === runnerTemp, 'Unexpected RUNNER_TEMP for the checked-out repository');
  const cache = protectedPath(directory(requireContained(runnerTemp, join(runnerTemp, 'trivy-cache'))));
  requireValue(ci.TRIVY_CACHE_DIR === cache, 'Unexpected Trivy cache directory');

  // The hosted image intentionally makes the tool-cache container writable.
  // Trust its fixed CI location/owner, but require protected JDK descendants.
  const javaCache = ownedPath(directory(provisioning.javaCache));
  const javaHome = protectedDirectoryTree(javaCache, canonicalPath(ci.JAVA_HOME));
  requireValue(/^Java_Temurin-Hotspot_jdk\/21\.[A-Za-z0-9.+_-]+\/x64$/.test(relative(javaCache, javaHome)),
    'Expected the CI-provisioned Temurin 21 x64 JDK');
  const java = validateExecutable(join(javaHome, 'bin', 'java'), javaHome);
  validateExecutable(provisioning.trivy, protectedPath(directory(dirname(provisioning.trivy))));
  return Object.freeze({ repo, target, extractor, runnerTemp, cache, java, javaHome });
}

export function scanBootJar(context, runProcess = spawnSync) {
  const work = mkdtempSync(join(context.runnerTemp, 'trivy-backend-'));
  protectedPath(directory(requireContained(context.runnerTemp, work)));
  const scan = join(work, 'scan'), inventory = join(work, 'expected.txt'), report = join(work, 'report.json');
  const home = join(work, 'home'), temp = join(work, 'tmp');
  mkdirSync(home, { mode: 0o700 });
  mkdirSync(temp, { mode: 0o700 });
  // Deliberate allowlist: no PATH, loader/JVM injection, credentials, or inherited
  // TRIVY_* options that could suppress findings. Retain the validated DB cache.
  const baseEnv = { HOME: home, TMPDIR: temp, LANG: 'C.UTF-8', LC_ALL: 'C.UTF-8' };
  const prepareStatus = statusOf(runProcess(context.java, [context.extractor, context.target, scan, inventory], {
    cwd: context.repo, shell: false, stdio: 'inherit', env: { ...baseEnv, JAVA_HOME: context.javaHome },
  }));
  if (prepareStatus !== 0) return prepareStatus;
  const scanStatus = statusOf(runProcess('/usr/local/bin/trivy', [...SCAN_FLAGS, '--output', report, scan], {
    cwd: context.repo, shell: false, stdio: 'inherit', env: { ...baseEnv, TRIVY_CACHE_DIR: context.cache },
  }));
  let validationStatus = 0;
  try {
    requireValue(statSync(report).size > 0 && statSync(report).size <= 64 * 1024 * 1024, 'Empty or oversized Trivy report');
    const expected = readFileSync(inventory, 'utf8').trimEnd().split('\n');
    const blocked = validateInventory(expected, JSON.parse(readFileSync(report, 'utf8')));
    validationStatus = blocked ? 1 : 0;
  } catch (error) {
    console.error(`Inventory validation failed: ${error.message}`);
    validationStatus = 1;
  }
  // A bad/missing report must never mask Trivy's original vulnerability/runtime
  // failure. A successful scanner still fails if its inventory is incomplete.
  return scanStatus !== 0 ? scanStatus : validationStatus;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    requireValue(process.argv.length === 2, 'The artifact scanner accepts no command-line arguments');
    process.exitCode = scanBootJar(createScanContext(repositoryRoot.replace(/\/$/, ''), process.env));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
