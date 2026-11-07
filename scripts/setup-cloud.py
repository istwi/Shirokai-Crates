#!/usr/bin/env python3
"""Install the pinned verified JDK into the cloud workspace; preserve repository files."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile

root=Path(__file__).resolve().parent.parent
metadata=json.loads((root/'scripts/toolchain.json').read_text())
tools=Path('/workspace/.tools')
target=tools/metadata['version']
# Temurin's archive top-level directory uses '+' rather than the tag's optional other notation.
if (target/'bin/javac').exists():
    print('JDK 21 already installed:',target)
else:
    tools.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir='/tmp',prefix='shirokai-jdk-') as directory:
        archive=Path(directory)/'jdk.tar.gz'
        subprocess.run(['curl','-fsSL','--max-time','180',metadata['url'],'-o',str(archive)],check=True)
        if hashlib.sha256(archive.read_bytes()).hexdigest()!=metadata['sha256']:
            raise RuntimeError('JDK checksum mismatch')
        with tarfile.open(archive) as package:
            package.extractall(tools,filter='data')
    if not (target/'bin/javac').exists():
        raise RuntimeError('Unexpected JDK archive layout')
# Keep the cloud's trusted CA roots, including its managed proxy CA. Verification remains enabled.
trust=Path('/etc/ssl/certs/java/cacerts')
if trust.exists():
    shutil.copy2(trust,target/'lib/security/cacerts')
print('Ready:',target)
