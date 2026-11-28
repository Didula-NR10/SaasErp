import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, readdirSync, rmSync, symlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { deflateRawSync } from 'node:zlib';
import { SCAN_FLAGS, validateInventory } from './scan-boot-jar.mjs';

const root = mkdtempSync(join(tmpdir(), 'security-ci-test-'));
const classes = join(root, 'helper-classes');
const extractor = resolve('.github/scripts/PrepareBootScan.java');
const launcher = 'org/springframework/boot/loader/launch/JarLauncher.class';
const application = 'BOOT-INF/classes/com/saaserp/erp/ErpApplication.class';
const libA = 'BOOT-INF/lib/alpha-1.0.jar', libB = 'BOOT-INF/lib/beta-2.0.jar';
const classBytes = Buffer.from('cafebabe00000041', 'hex');
const manifest = 'Manifest-Version: 1.0\r\nMain-Class: org.springframework.boot.loader.launch.JarLauncher\r\nStart-Class: com.saaserp.erp.ErpApplication\r\n\r\n';

// Tiny synthetic ZIP fixture writer; production extraction uses JDK ZipFile.
function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function zip(entries) {
  const local = [], central = [];
  let offset = 0;
  for (const [name, value, method = 0] of entries) {
    const data = Buffer.isBuffer(value) ? value : Buffer.from(value);
    const packed = method === 8 ? deflateRawSync(data) : data;
    const path = Buffer.from(name), header = Buffer.alloc(30), directory = Buffer.alloc(46);
    header.writeUInt32LE(0x04034b50); header.writeUInt16LE(20, 4); header.writeUInt16LE(method, 8);
    header.writeUInt32LE(crc32(data), 14); header.writeUInt32LE(packed.length, 18);
    header.writeUInt32LE(data.length, 22); header.writeUInt16LE(path.length, 26);
    directory.writeUInt32LE(0x02014b50); directory.writeUInt16LE(20, 4); directory.writeUInt16LE(20, 6);
    directory.writeUInt16LE(method, 10); directory.writeUInt32LE(crc32(data), 16);
    directory.writeUInt32LE(packed.length, 20); directory.writeUInt32LE(data.length, 24);
    directory.writeUInt16LE(path.length, 28); directory.writeUInt32LE(offset, 42);
    local.push(header, path, packed); central.push(directory, path);
    offset += header.length + path.length + packed.length;
  }
  const directory = Buffer.concat(central), end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50); end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(directory.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, directory, end]);
}
const dependency = zip([['META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\n\r\n']]);
function bootEntries() {
  return [['META-INF/MANIFEST.MF', manifest, 8], [launcher, classBytes, 8], [application, classBytes, 8],
    ['BOOT-INF/lib/', ''], [libA, dependency], [libB, dependency, 8]];
}
function fixture(entries = bootEntries()) {
  const work = mkdtempSync(join(root, 'case-')), target = join(work, 'target');
  mkdirSync(target);
  const jar = join(target, 'erp.jar');
  writeFileSync(jar, zip(entries));
  return { work, target, jar, scan: join(work, 'scan'), inventory: join(work, 'expected.txt') };
}
function prepare(f) {
  return spawnSync('java', ['-cp', classes, 'PrepareBootScan', f.target, f.scan, f.inventory], { encoding: 'utf8' });
}
function pkg(path, group, name, version) {
  return { FilePath: path, Name: `${group}:${name}`, Version: version,
    Identifier: { PURL: `pkg:maven/${group}/${name}@${encodeURIComponent(version)}` } };
}
function complete() {
  return { SchemaVersion: 2, Results: [{ Target: 'Java', Class: 'lang-pkgs', Type: 'jar',
    Packages: [pkg(libA, 'org.example', 'alpha', '1.0'), pkg(libB, 'org.example', 'beta', '2.0')] }] };
}

before(() => {
  mkdirSync(classes);
  const compiled = spawnSync('javac', ['-d', classes, extractor], { encoding: 'utf8' });
  assert.equal(compiled.status, 0, compiled.stderr);
});
after(() => rmSync(root, { recursive: true, force: true }));

test('complete Java inventory uses exact package archive paths even with aggregate Java Target', () => {
  assert.equal(validateInventory([libA, libB], complete()), false);
});
test('missing dependency inventory fails', () => {
  const report = complete(); report.Results[0].Packages.pop();
  assert.throws(() => validateInventory([libA, libB], report), /beta-2.0.jar/);
});
test('empty reports and malformed JSON structures fail closed', () => {
  for (const report of [null, [], {}, { Results: [] }, { SchemaVersion: 2, Results: null },
    { SchemaVersion: 2, Results: [] }, { SchemaVersion: 2, Results: [null] },
    { SchemaVersion: 3, Results: complete().Results }]) {
    assert.throws(() => validateInventory([libA, libB], report));
  }
  const report = complete(); report.Results[0].Packages = {};
  assert.throws(() => validateInventory([libA, libB], report), /package list/);
});
test('missing and mismatched Maven identities fail', () => {
  for (const mutate of [p => delete p.Name, p => p.Name = 'alpha', p => p.Version = '', p => p.Version = 'unknown',
    p => delete p.Identifier, p => p.Identifier.PURL = 'pkg:npm/alpha@1.0',
    p => p.Identifier.PURL = 'pkg:maven/org.example/alpha',
    p => p.Identifier.PURL = 'pkg:maven/org.example/alpha@9.9',
    p => p.Identifier.PURL = 'pkg:maven/org.other/alpha@1.0']) {
    const report = complete(); mutate(report.Results[0].Packages[0]);
    assert.throws(() => validateInventory([libA, libB], report));
  }
});
test('malformed vulnerability structures fail closed', () => {
  for (const findings of [{}, [null], [{}], [{ VulnerabilityID: 'CVE-2099-0001', Severity: 'HIGH ' }]]) {
    const report = complete(); report.Results[0].Vulnerabilities = findings;
    assert.throws(() => validateInventory([libA, libB], report), /Malformed vulnerability/);
  }
});
test('filename, substring, result Target and nested archive substitutes cannot satisfy coverage', () => {
  for (const path of ['alpha-1.0.jar', `elsewhere/${libA}`, `/tmp/${libA}`, `${libA}/nested.jar`, undefined]) {
    const report = complete(); report.Results[0].Target = libA; report.Results[0].Packages[0].FilePath = path;
    assert.throws(() => validateInventory([libA, libB], report));
  }
});
test('a complete report still blocks HIGH/CRITICAL including unfixed findings', () => {
  for (const severity of ['HIGH', 'CRITICAL']) {
    const report = complete();
    report.Results[0].Vulnerabilities = [{ VulnerabilityID: 'CVE-2099-0001', Severity: severity,
      PkgName: 'org.example:alpha', InstalledVersion: '1.0', FixedVersion: '' }];
    assert.equal(validateInventory([libA, libB], report), true);
  }
});
test('extracts the actual stored and deflated nested JAR bytes, with compiled Boot classes', () => {
  const f = fixture(), result = prepare(f);
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(readFileSync(join(f.scan, libA)), dependency);
  assert.deepEqual(readFileSync(join(f.scan, libB)), dependency);
  assert.equal(readFileSync(f.inventory, 'utf8'), `${libA}\n${libB}\n`);
  assert.deepEqual(readdirSync(join(f.scan, 'BOOT-INF')), ['lib']);
});
test('requires one executable application JAR, classes and nonempty libraries', () => {
  for (const entries of [bootEntries().filter(([name]) => name !== application),
    bootEntries().filter(([name]) => name !== launcher),
    bootEntries().filter(([name]) => !name.startsWith('BOOT-INF/lib')),
    bootEntries().map(e => e[0] === 'META-INF/MANIFEST.MF' ? [e[0], 'Manifest-Version: 1.0\r\n\r\n'] : e)]) {
    const result = prepare(fixture(entries)); assert.notEqual(result.status, 0, result.stdout);
  }
  const f = fixture(); writeFileSync(join(f.target, 'another.jar'), zip(bootEntries()));
  assert.notEqual(prepare(f).status, 0);
  rmSync(f.jar); rmSync(join(f.target, 'another.jar'));
  assert.notEqual(prepare(f).status, 0);
});
test('rejects duplicate, traversal, absolute, ambiguous and unexpected archive entries', () => {
  for (const [name, bytes] of [[libA, dependency], ['../escape.jar', dependency], ['/escape.jar', dependency],
    ['BOOT-INF/lib/../escape.jar', dependency], ['BOOT-INF/lib/nested/escape.jar', dependency],
    ['BOOT-INF/lib/policy.txt', 'unexpected'], ['BOOT-INF/lib/link.jar', '../outside.jar'],
    ['BOOT-INF/lib/alpha\\escape.jar', dependency], ['BOOT-INF//lib/odd.jar', dependency],
    ['BOOT-INF/lib/empty.jar', Buffer.alloc(0)], ['BOOT-INF/lib', 'file']]) {
    const result = prepare(fixture([...bootEntries(), [name, bytes]]));
    assert.notEqual(result.status, 0, name);
  }
});
test('rejects preexisting destinations and symlink application artifacts', () => {
  const f = fixture(); mkdirSync(f.scan);
  assert.notEqual(prepare(f).status, 0);
  const other = fixture(); rmSync(other.jar); symlinkSync(f.jar, other.jar);
  assert.notEqual(prepare(other).status, 0);
});

function runMockScan(report, scannerStatus, options = {}) {
  const f = fixture(), bin = join(f.work, 'bin'); mkdirSync(bin);
  // Reuse the compiled CI-only extractor in these subprocess tests.
  writeFileSync(join(bin, 'java'), '#!/bin/sh\nshift\nexec "$REAL_TEST_JAVA" -cp "$TEST_HELPER_CLASSES" PrepareBootScan "$@"\n', { mode: 0o755 });
  writeFileSync(join(bin, 'trivy'), `#!/usr/bin/env node
const fs = require('node:fs');
const args = process.argv.slice(2);
fs.writeFileSync(process.env.TEST_ARGS, JSON.stringify(args));
if (process.env.TEST_NO_REPORT !== 'true') {
  fs.writeFileSync(args[args.indexOf('--output') + 1], process.env.TEST_REPORT);
}
process.exit(Number(process.env.TEST_SCAN_STATUS));
`, { mode: 0o755 });
  const realJava = spawnSync('which', ['java'], { encoding: 'utf8' }).stdout.trim();
  const argsFile = join(f.work, 'arguments.json');
  const result = spawnSync(process.execPath, [resolve('.github/scripts/scan-boot-jar.mjs'), f.target, f.work], {
    encoding: 'utf8', env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, REAL_TEST_JAVA: realJava,
      TEST_HELPER_CLASSES: classes, TEST_ARGS: argsFile, TEST_REPORT: report,
      TEST_SCAN_STATUS: String(scannerStatus), TEST_NO_REPORT: String(Boolean(options.noReport)) },
  });
  return { ...result, args: JSON.parse(readFileSync(argsFile, 'utf8')) };
}
test('pipeline scans extracted files with mandatory unfiltered vulnerability flags', () => {
  const result = runMockScan(JSON.stringify(complete()), 0);
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(result.args.slice(0, -3), SCAN_FLAGS);
  for (const [flag, value] of [['--config', ''], ['--ignorefile', ''], ['--scanners', 'vuln'],
    ['--severity', 'HIGH,CRITICAL'], ['--exit-code', '1'], ['--format', 'json']]) {
    assert.equal(result.args[result.args.indexOf(flag) + 1], value, flag);
  }
  assert.ok(result.args.includes('--ignore-unfixed=false'));
  assert.ok(result.args.includes('--list-all-pkgs'));
  assert.equal(result.args.at(-3), '--output');
  assert.deepEqual(readFileSync(join(result.args.at(-1), libA)), dependency);
});
test('pipeline rejects missing, empty, malformed and incomplete reports even on scanner exit zero', () => {
  const missing = complete(); missing.Results[0].Packages.pop();
  for (const report of ['', '{bad json', '{}', JSON.stringify({ SchemaVersion: 2, Results: [] }), JSON.stringify(missing)]) {
    const result = runMockScan(report, 0);
    assert.equal(result.status, 1, result.stdout);
  }
  assert.equal(runMockScan('', 0, { noReport: true }).status, 1);
});
test('scanner vulnerability and operational failure exit codes survive inventory validation failures', () => {
  for (const status of [1, 2, 17]) {
    assert.equal(runMockScan(JSON.stringify(complete()), status).status, status);
    assert.equal(runMockScan('{malformed', status).status, status);
    assert.equal(runMockScan('', status, { noReport: true }).status, status);
  }
});
test('pipeline also rejects a HIGH finding if the scanner incorrectly exits zero', () => {
  const report = complete(); report.Results[0].Vulnerabilities = [{ VulnerabilityID: 'CVE-2099-0001', Severity: 'HIGH' }];
  assert.equal(runMockScan(JSON.stringify(report), 0).status, 1);
});
test('Gitleaks exceptions contain only the two owner-reviewed historical fingerprints', () => {
  assert.deepEqual(readFileSync('.gitleaksignore', 'utf8').trim().split('\n'), [
    'f8732921a9b1127064990557c2eb407c9efbc901:docs/security/phase04-authentication-foundation.md:generic-api-key:107',
    'f8732921a9b1127064990557c2eb407c9efbc901:docs/security/security-acceptance-criteria.md:generic-api-key:191',
  ]);
});
