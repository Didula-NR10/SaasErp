import { readFileSync, mkdtempSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { constants } from 'node:os';

// Verified against Trivy v0.76.2's versioned CLI, jar parser, language analyzer
// and JSON structs; see docs/security/phase04-ci-security-correction.md.
export const SCAN_FLAGS = [
  'fs', '--config', '', '--ignorefile', '', '--ignore-unfixed=false',
  '--scanners', 'vuln', '--severity', 'HIGH,CRITICAL', '--exit-code', '1',
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

export function scanBootJar(target, runnerTemp) {
  requireValue(target && runnerTemp, 'Expected target directory and RUNNER_TEMP');
  const work = mkdtempSync(join(resolve(runnerTemp), 'trivy-backend-'));
  const scan = join(work, 'scan'), inventory = join(work, 'expected.txt'), report = join(work, 'report.json');
  const extractor = fileURLToPath(new URL('./PrepareBootScan.java', import.meta.url));
  const prepareStatus = statusOf(spawnSync('java', [extractor, resolve(target), scan, inventory], { stdio: 'inherit' }));
  if (prepareStatus !== 0) return prepareStatus;
  const scanStatus = statusOf(spawnSync('trivy', [...SCAN_FLAGS, '--output', report, scan], { stdio: 'inherit' }));
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
    requireValue(process.argv.length === 4, 'Usage: node scan-boot-jar.mjs TARGET_DIRECTORY RUNNER_TEMP');
    process.exitCode = scanBootJar(process.argv[2], process.argv[3]);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
