#!/usr/bin/env python3
"""Build against an installed IDE, without downloading a second IDE or build plugins."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--ide', default=os.environ.get('IDEA_HOME', str(Path.home() / 'Applications/IntelliJ IDEA.app/Contents')))
parser.add_argument('--test', action='store_true')
parser.add_argument('--output', default='build', help='Build output directory (relative to repository or absolute)')
args = parser.parse_args()
ide = Path(args.ide).expanduser().resolve()
if (ide / 'Contents').is_dir():
    ide /= 'Contents'
java_home = ide / 'jbr/Contents/Home'
if not (java_home / 'bin/javac').exists():
    java_home = Path(os.environ.get('JAVA_HOME', str(ide / 'jbr')))
if not (java_home / 'bin/javac').exists():
    raise SystemExit('Set JAVA_HOME to a JDK 21+ installation, and --ide to a supported IntelliJ installation.')
build = ROOT / args.output
classes = build / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir(parents=True)
classpath = os.pathsep.join([str(ide / 'lib' / '*'), str(ide / 'plugins/terminal/lib' / '*')])
sources = sorted((ROOT / 'src/main/java').rglob('*.java'))
# Production code may not introduce a deprecated or removal-marked SDK call.
subprocess.run([str(java_home / 'bin/javac'), '--release', '21', '-encoding', 'UTF-8',
                '-Xlint:deprecation,removal', '-Werror', '-cp', classpath,
                '-d', str(classes), *map(str, sources)], check=True)
if args.test:
    tests = sorted((ROOT / 'src/test/java').rglob('*.java'))
    subprocess.run([str(java_home / 'bin/javac'), '--release', '21', '-encoding', 'UTF-8',
                    '-Xlint:deprecation,removal', '-Werror', '-cp', str(classes) + os.pathsep + classpath,
                    '-d', str(classes), *map(str, tests)], check=True)
if args.test:
    subprocess.run([str(java_home / 'bin/java'), '-ea', '-Djava.awt.headless=true', '-cp', str(classes) + os.pathsep + classpath,
                    'dev.agentbridge.MarkdownTextTest'], check=True)
    subprocess.run([str(java_home / 'bin/java'), '-ea', '-cp', str(classes) + os.pathsep + classpath,
                    'dev.agentbridge.ConversationHistoryTest'], check=True)
    subprocess.run([str(java_home / 'bin/java'), '-ea', '-Djava.awt.headless=true', '-cp', str(classes) + os.pathsep + classpath,
                    'dev.agentbridge.IdeReadTest'], check=True, timeout=60)
    subprocess.run([str(java_home / 'bin/java'), '-ea', '-cp', str(classes) + os.pathsep + classpath,
                    'dev.agentbridge.ModelCatalogTest', str(ROOT / 'scripts/fake_agent.py')], check=True)
    subprocess.run([str(java_home / 'bin/java'), '-ea', '-Djava.awt.headless=true', '-cp', str(classes) + os.pathsep + classpath,
                    'dev.agentbridge.SelectionContextTest'], check=True)
    subprocess.run([str(java_home / 'bin/java'), '-ea', '-cp', str(classes) + os.pathsep + classpath,
                    'dev.agentbridge.ProtocolTest', str(ROOT / 'scripts/fake_agent.py')], check=True)
    subprocess.run([str(java_home / 'bin/java'), '-ea', '-Djava.awt.headless=true', '-cp', str(classes) + os.pathsep + classpath,
                    'dev.agentbridge.ContextTest', str(build)], check=True)
    subprocess.run([str(java_home / 'bin/java'), '-ea', '-cp', str(classes) + os.pathsep + classpath,
                    'dev.agentbridge.AuthTest'], check=True)
    subprocess.run([str(java_home / 'bin/java'), '-ea', '-cp', str(classes) + os.pathsep + classpath,
                    'dev.agentbridge.CodexAuthTest'], check=True)
    subprocess.run([str(java_home / 'bin/java'), '-ea', '-cp', str(classes) + os.pathsep + classpath,
                    'dev.agentbridge.ProjectContextTest'], check=True)
jar = build / 'agent-bridge.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'):
        if 'Test' not in p.name:
            z.write(p, p.relative_to(classes))
    resources = ROOT / 'src/main/resources'
    for p in resources.rglob('*'):
        if p.is_file():
            z.write(p, p.relative_to(resources))
    z.write(ROOT / 'LICENSE', 'META-INF/LICENSE')
version = ET.parse(ROOT / 'src/main/resources/META-INF/plugin.xml').getroot().findtext('version')
archive = build / ('agent-bridge-' + version + '.zip')
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    z.write(jar, 'agent-bridge/lib/agent-bridge.jar')
print(f'Plugin: {archive}')
