#!/usr/bin/env python3
"""Build and start a loopback-only production-mapped Fabric server using verified runtime mods.
Run from the repository root. Requires Java 21, Python 3 and curl; Gradle resolves the build dependencies.
Loom 1.9 cannot remap Cobblemon 1.8.3's Kotlin reflection metadata correctly in runServer.
This launcher tests the actual release JAR, not remapped development classes.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.parse
import uuid
import zipfile

ROOT = Path(__file__).resolve().parent.parent
os.chdir(ROOT)
RUN = Path(os.environ.get('SHIROKAI_VALIDATION_DIR', str(ROOT / 'run'))).resolve()
RUN.mkdir(exist_ok=True)
CACHE = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
JAVA = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else shutil.which('java')
if not JAVA:
    raise SystemExit('Java 21 required')
subprocess.run([str(ROOT / 'gradlew'), 'build', '--console', 'plain'], check=True)


def jar(group, artifact, version):
    folder = CACHE / 'caches/modules-2/files-2.1' / group / artifact / version
    matches = [p for p in folder.glob('*/*.jar') if not p.name.endswith('-sources.jar')]
    if len(matches) != 1:
        raise RuntimeError(f'Expected one artifact: {group}:{artifact}:{version}, found {len(matches)}')
    return matches[0]


loader = jar('net.fabricmc', 'fabric-loader', '0.20.1')
with zipfile.ZipFile(loader) as archive:
    metadata = json.loads(archive.read('fabric-installer.json'))
classpath = [str(loader)]
for library in metadata['libraries']['common']:
    group, artifact, version = library['name'].split(':')
    file = jar(group, artifact, version)
    if hashlib.sha256(file.read_bytes()).hexdigest() != library['sha256']:
        raise RuntimeError(f'Invalid loader library checksum: {file.name}')
    classpath.append(str(file))
classpath.append(str(jar('net.fabricmc', 'intermediary', '1.21.1')))
mods = RUN / 'mods'
mods.mkdir(exist_ok=True)
for group, artifact, version in [
    ('com.cobblemon', 'fabric', '1.8.3+1.21.1'),
    ('net.fabricmc', 'fabric-language-kotlin', '1.13.6+kotlin.2.2.20'),
    ('net.fabricmc.fabric-api', 'fabric-api', '0.116.6+1.21.1'),
]:
    shutil.copy2(jar(group, artifact, version), mods)
for entry in json.loads((ROOT / 'tests/integration/runtime-mods.json').read_text()):
    target = mods / entry['filename']
    if not target.exists():
        subprocess.run(['curl', '-fsSL', '--max-time', '180', entry['url'], '-o', str(target)], check=True)
    if hashlib.sha512(target.read_bytes()).hexdigest() != entry['sha512']:
        raise RuntimeError(f'Invalid runtime checksum: {target.name}')
# Read the actual processed version; select the executable JAR, never a sources archive.
manifest = json.loads((ROOT / 'build/resources/main/fabric.mod.json').read_text())
installable = ROOT / 'build/libs' / f"shirokai-crates-{manifest['version']}.jar"
# Replace only verified prior Shirokai executables. Keep worlds, keys, records and other mods untouched.
for existing in mods.glob('*.jar'):
    if existing.name == installable.name:
        continue
    with zipfile.ZipFile(existing) as archive:
        if 'fabric.mod.json' not in archive.namelist():
            continue
        old = json.loads(archive.read('fabric.mod.json'))
        if old.get('id') == 'shirokaicrates':
            backup = RUN / (existing.name + '.disabled')
            index = 1
            while backup.exists():
                backup = RUN / (existing.name + f'.disabled-{index}')
                index += 1
            shutil.move(str(existing), str(backup))
shutil.copy2(installable, mods)
# Deliberately create local validation settings only if absent. Never overwrite an existing world/config.
files = {
    'eula.txt': 'eula=true\n',
    'server.properties': 'online-mode=false\nserver-ip=127.0.0.1\nserver-port=25565\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\nmax-players=5\n',
}
for filename, content in files.items():
    target = RUN / filename
    if not target.exists():
        target.write_text(content)
name = 'ShirokaiTest'
test_uuid = uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + name).encode()).digest(), version=3)
ops = RUN / 'ops.json'
if not ops.exists():
    ops.write_text(json.dumps([dict(uuid=str(test_uuid), name=name, level=4, bypassesPlayerLimit=True)]))
# Preserve the managed proxy and CA trust for Java runtime dependency downloads.
options = []
proxy = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
if proxy.hostname:
    for scheme in ['http', 'https']:
        options += [f'-D{scheme}.proxyHost={proxy.hostname}', f'-D{scheme}.proxyPort={proxy.port or 80}']
    options += ['-Dhttp.nonProxyHosts=localhost|127.*']
server = CACHE / 'caches/fabric-loom/1.21.1/minecraft-server.jar'
if not server.exists():
    raise RuntimeError('Minecraft server bundle not resolved by Loom')
args = [JAVA, '-Xms1G', '-Xmx4G', *options, f'-Dfabric.gameJarPath={server}', '-cp', ':'.join(classpath),
        'net.fabricmc.loader.impl.launch.server.FabricServerLauncher', 'nogui']
(RUN / "server.pid").write_text(str(os.getpid()))
os.chdir(RUN)
os.execv(JAVA, args)
