import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, readdirSync, rmSync, symlinkSync, realpathSync, chmodSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { deflateRawSync } from 'node:zlib';
import { SCAN_FLAGS, createScanContext, scanBootJar, validateExecutable, validateInventory } from './scan-boot-jar.mjs';

const root = mkdtempSync(join(tmpdir(), 'security-ci-test-'));
const classes = join(root, 'helper-classes');
const extractor = resolve('.github/scripts/PrepareBootScan.java');
const testJdk = realpathSync(process.env.JAVA_HOME);
const testJava = join(testJdk, 'bin', 'java');
const testJavac = join(testJdk, 'bin', 'javac');
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
  const work = mkdtempSync(join(root, 'case-')), repo = join(work, 'work', 'project', 'project');
  const target = join(repo, 'Backend', 'target'), runnerTemp = join(work, 'work', '_temp');
  const cache = join(runnerTemp, 'trivy-cache'), javaCache = join(work, 'toolcache');
  const javaHome = join(javaCache, 'Java_Temurin-Hotspot_jdk', '21.0.13-11', 'x64');
  const java = join(javaHome, 'bin', 'java'), trivy = join(work, 'install', 'bin', 'trivy');
  for (const dir of [target, cache, join(repo, '.github', 'scripts'), join(javaHome, 'bin'), join(work, 'install', 'bin')]) {
    mkdirSync(dir, { recursive: true });
  }
  writeFileSync(join(repo, '.github', 'scripts', 'PrepareBootScan.java'), readFileSync(extractor));
  for (const file of [java, trivy]) writeFileSync(file, 'fixture executable; never executed', { mode: 0o755 });
  const jar = join(target, 'erp.jar');
  writeFileSync(jar, zip(entries));
  return { work, repo, runnerTemp, cache, javaCache, javaHome, java, trivy, target, jar,
    scan: join(work, 'scan'), inventory: join(work, 'expected.txt') };
}
function prepare(f) {
  return spawnSync(testJava, ['-cp', classes, 'PrepareBootScan', f.target, f.scan, f.inventory], { encoding: 'utf8' });
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
  const compiled = spawnSync(testJavac, ['-d', classes, extractor], { encoding: 'utf8' });
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

function contextFor(f, overrides = {}) {
  return createScanContext(f.repo, { RUNNER_TEMP: f.runnerTemp, TRIVY_CACHE_DIR: f.cache,
    JAVA_HOME: f.javaHome, ...overrides }, { javaCache: f.javaCache, trivy: f.trivy });
}

function runMockScan(report, scannerStatus, options = {}) {
  const f = fixture(), context = contextFor(f, options.ci);
  const calls = [];
  const status = scanBootJar(context, (executable, args, processOptions) => {
    calls.push({ executable, args, processOptions });
    if (calls.length === 1) {
      assert.equal(executable, f.java);
      // Execute the real CI extractor, compiled once for the test suite; no PATH
      // wrappers or production environment overrides are needed to mock Trivy.
      return spawnSync(testJava, ['-cp', classes, 'PrepareBootScan', ...args.slice(1)], {
        ...processOptions, stdio: 'pipe', encoding: 'utf8',
      });
    }
    assert.equal(executable, '/usr/local/bin/trivy');
    if (!options.noReport) writeFileSync(args[args.indexOf('--output') + 1], report);
    return { status: scannerStatus, signal: options.signal ?? null };
  });
  return { status, args: calls[1].args, calls, fixture: f };
}

test('pipeline uses rootfs rather than fs for extracted JARs with library vulnerability scanning', () => {
  const result = runMockScan(JSON.stringify(complete()), 0);
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.args[0], 'rootfs');
  assert.ok(!result.args.includes('fs'));
  assert.deepEqual(result.args.slice(0, -3), SCAN_FLAGS);
  for (const [flag, value] of [['--config', ''], ['--ignorefile', ''], ['--scanners', 'vuln'],
    ['--pkg-types', 'library'], ['--severity', 'HIGH,CRITICAL'], ['--exit-code', '1'], ['--format', 'json']]) {
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
test('production entrypoint rejects every unexpected CLI argument before invoking tools', () => {
  for (const args of [['../../outside'], ['/tmp'], ['--target=/tmp'], ['--config', '/tmp/override']]) {
    const result = spawnSync(process.execPath, [resolve('.github/scripts/scan-boot-jar.mjs'), ...args], {
      encoding: 'utf8', env: {},
    });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /accepts no command-line arguments/);
  }
});
test('target and extractor are derived from the repository rather than the current directory or CI overrides', () => {
  const f = fixture();
  const context = contextFor(f, { GITHUB_WORKSPACE: '/tmp/other-repository', TARGET_DIRECTORY: '/tmp' });
  assert.equal(context.target, join(f.repo, 'Backend', 'target'));
  assert.equal(context.extractor, join(f.repo, '.github', 'scripts', 'PrepareBootScan.java'));
  assert.equal(context.java, join(f.javaHome, 'bin', 'java'));
});
test('unexpected, relative, traversal and option-like CI paths are rejected', () => {
  const f = fixture();
  for (const path of ['/tmp', '../outside', `${f.runnerTemp}/../_temp`, `${f.runnerTemp}//`, '--output=/tmp',
    `${f.runnerTemp}\n`, `${f.runnerTemp};echo-unsafe`, '']) {
    assert.throws(() => contextFor(f, { RUNNER_TEMP: path }));
    assert.throws(() => contextFor(f, { TRIVY_CACHE_DIR: path }));
    assert.throws(() => contextFor(f, { JAVA_HOME: path }));
  }
});
test('symlink escapes in backend target, extractor, runner temp and cache are rejected', () => {
  for (const key of ['target', 'runnerTemp', 'cache']) {
    const f = fixture(), outside = join(f.work, 'outside'); mkdirSync(outside);
    rmSync(f[key], { recursive: true }); symlinkSync(outside, f[key]);
    assert.throws(() => contextFor(f), /Symbolic links/);
  }
  const f = fixture(), file = join(f.repo, '.github', 'scripts', 'PrepareBootScan.java');
  rmSync(file); symlinkSync(extractor, file);
  assert.throws(() => contextFor(f), /Symbolic links/);
});
test('invalid directory types and writable CI configuration directories are rejected', () => {
  for (const key of ['target', 'runnerTemp', 'cache', 'javaHome']) {
    const f = fixture(); rmSync(f[key], { recursive: true }); writeFileSync(f[key], 'not a directory');
    assert.throws(() => contextFor(f));
  }
  for (const key of ['runnerTemp', 'cache', 'javaHome']) {
    const f = fixture(); chmodSync(f[key], 0o777);
    assert.throws(() => contextFor(f), /unsafe ownership or write permissions/);
  }
});
test('Java must come from the provisioned Temurin 21 JDK and not arbitrary executables', () => {
  const f = fixture();
  assert.throws(() => contextFor(f, { JAVA_HOME: testJdk }), /outside its trusted root/);
  const wrongJdk = join(f.javaCache, 'other-jdk'); mkdirSync(wrongJdk);
  assert.throws(() => contextFor(f, { JAVA_HOME: wrongJdk }), /Temurin 21/);
  rmSync(f.javaHome, { recursive: true }); symlinkSync(testJdk, f.javaHome);
  assert.throws(() => contextFor(f), /Symbolic links/);
});
test('Java and Trivy validation rejects nonexecutables, empty files, directories and symlinks', () => {
  for (const key of ['java', 'trivy']) {
    for (const mutation of ['not-executable', 'empty', 'directory', 'symlink', 'writable']) {
      const f = fixture(), path = f[key];
      if (mutation === 'not-executable') chmodSync(path, 0o644);
      if (mutation === 'empty') writeFileSync(path, '');
      if (mutation === 'directory') { rmSync(path); mkdirSync(path); }
      if (mutation === 'symlink') { rmSync(path); symlinkSync(testJava, path); }
      if (mutation === 'writable') chmodSync(path, 0o777);
      assert.throws(() => contextFor(f), `${key}: ${mutation}`);
    }
  }
  const f = fixture();
  assert.throws(() => validateExecutable(testJava, f.javaHome), /outside its trusted root/);
});
test('child execution uses absolute executables, argument arrays and a restricted environment with the real DB cache', () => {
  const result = runMockScan(JSON.stringify(complete()), 0, { ci: {
    PATH: '/attacker/bin', LD_PRELOAD: '/tmp/untrusted.so', NODE_OPTIONS: '--inspect',
    JAVA_TOOL_OPTIONS: '-javaagent:/tmp/agent.jar', TRIVY_SKIP_FILES: '*', TRIVY_EXIT_CODE: '0',
  } });
  assert.equal(result.status, 0);
  const [java, trivy] = result.calls;
  assert.equal(java.executable, result.fixture.java);
  assert.equal(trivy.executable, '/usr/local/bin/trivy');
  assert.equal(trivy.processOptions.env.TRIVY_CACHE_DIR, result.fixture.cache);
  for (const call of result.calls) {
    assert.ok(Array.isArray(call.args)); assert.equal(call.processOptions.shell, false);
    assert.equal(call.processOptions.cwd, result.fixture.repo);
    assert.deepEqual(Object.keys(call.processOptions.env).sort(),
      ['HOME', 'TMPDIR', 'LANG', 'LC_ALL', call === java ? 'JAVA_HOME' : 'TRIVY_CACHE_DIR'].sort());
    assert.ok(call.processOptions.env.HOME.startsWith(`${result.fixture.runnerTemp}/trivy-backend-`));
    assert.ok(call.processOptions.env.TMPDIR.startsWith(`${result.fixture.runnerTemp}/trivy-backend-`));
  }
});
test('signal termination and extractor failures remain failures and cannot reach a success path', () => {
  assert.equal(runMockScan(JSON.stringify(complete()), null, { signal: 'SIGTERM' }).status, 143);
  const f = fixture(); let calls = 0;
  assert.equal(scanBootJar(contextFor(f), () => { calls++; return { status: 9 }; }), 9);
  assert.equal(calls, 1);
  assert.throws(() => scanBootJar(contextFor(f), () => ({ error: new Error('executable unavailable') })),
    /executable unavailable/);
});

test('writable JDK ancestors and executable parent directories are rejected', () => {
  for (const path of [f => join(f.javaCache, 'Java_Temurin-Hotspot_jdk'),
    f => join(f.javaCache, 'Java_Temurin-Hotspot_jdk', '21.0.13-11'),
    f => join(f.javaHome, 'bin'), f => join(f.work, 'install', 'bin')]) {
    const f = fixture(); chmodSync(path(f), 0o777);
    assert.throws(() => contextFor(f), /unsafe ownership or write permissions/);
  }
});
test('the provider shared tool-cache container can be writable while JDK descendants remain protected', () => {
  const f = fixture(); chmodSync(f.javaCache, 0o777);
  assert.equal(contextFor(f).java, f.java);
  chmodSync(join(f.javaHome, 'bin'), 0o777);
  assert.throws(() => contextFor(f), /unsafe ownership or write permissions/);
});
test('all 114 embedded dependencies must be identified, including the final archive', () => {
  const paths = Array.from({ length: 114 }, (_, i) => `BOOT-INF/lib/dependency-${i}-1.0.jar`);
  const report = { SchemaVersion: 2, Results: [{ Target: 'Java', Class: 'lang-pkgs', Type: 'jar',
    Packages: paths.map((path, i) => pkg(path, 'org.example', `dependency-${i}`, '1.0')) }] };
  assert.equal(validateInventory(paths, report), false);
  report.Results[0].Packages.pop();
  assert.throws(() => validateInventory(paths, report), /dependency-113-1.0.jar/);
});
test('automatic-analysis configuration excludes only exact immutable migrations from duplication', () => {
  const settings = readFileSync('.sonarcloud.properties', 'utf8').split('\n')
    .filter(line => line.trim() && !line.startsWith('#'));
  assert.equal(settings.length, 1);
  assert.ok(settings[0].startsWith('sonar.cpd.exclusions='));
  const paths = settings[0].slice('sonar.cpd.exclusions='.length).split(',');
  const migrationRoot = 'Backend/src/main/resources/db/migration/';
  const expected = readdirSync(migrationRoot, { recursive: true })
    .filter(path => /(^|\/)V[1-9]__[^/]+\.sql$/.test(path)).map(path => migrationRoot + path).sort();
  assert.equal(paths.length, 10);
  assert.deepEqual(paths, expected);
  assert.ok(paths.every(path => /^Backend\/src\/main\/resources\/db\/migration\/(platform|billing)\/V[1-9]__[^/]+\.sql$/.test(path)));
  assert.ok(paths.every(path => !path.includes('*')));
});

test('Gitleaks exceptions contain only the two owner-reviewed historical fingerprints', () => {
  assert.deepEqual(readFileSync('.gitleaksignore', 'utf8').trim().split('\n'), [
    'f8732921a9b1127064990557c2eb407c9efbc901:docs/security/phase04-authentication-foundation.md:generic-api-key:107',
    'f8732921a9b1127064990557c2eb407c9efbc901:docs/security/security-acceptance-criteria.md:generic-api-key:191',
  ]);
});
