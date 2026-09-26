#!/usr/bin/env python3
"""Fail a release check on missing reports, compatibility problems, or unstable API usage."""
import argparse
import io
import json
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

REJECT_REPORTS = (
    'compatibility-problems.txt', 'compatibility-warnings.txt', 'deprecated-usages.txt',
    'internal-api-usages.txt', 'experimental-api-usages.txt', 'override-only-usages.txt',
    'non-extendable-api-usages.txt', 'plugin-structure-warnings.txt', 'invalid-plugin.txt',
)


def assess_reports(reports, targets, plugin_id, version):
    errors = []
    for target in targets:
        folder = reports / target / 'plugins' / plugin_id / version
        verdict = folder / 'verification-verdict.txt'
        if not verdict.is_file():
            errors.append(f'{target}: verification report missing (not a pass)')
            continue
        text = verdict.read_text().strip()
        if text != 'Compatible':
            errors.append(f'{target}: {text}')
        for name in REJECT_REPORTS:
            report = folder / name
            if report.is_file() and report.read_text().strip():
                errors.append(f'{target}: {name}\n{report.read_text().strip()}')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plugin', required=True, type=Path)
    parser.add_argument('--verifier', required=True, type=Path)
    parser.add_argument('--ide', required=True, action='append', type=Path)
    parser.add_argument('--runtime', required=True, type=Path)
    parser.add_argument('--reports', required=True, type=Path)
    parser.add_argument('--java', default='java')
    args = parser.parse_args()
    with zipfile.ZipFile(args.plugin) as archive:
        jars = [name for name in archive.namelist() if name.endswith('.jar')]
        if len(jars) != 1:
            parser.error('Expected one plugin JAR')
        with zipfile.ZipFile(io.BytesIO(archive.read(jars[0]))) as jar:
            descriptor = ET.fromstring(jar.read('META-INF/plugin.xml'))
    plugin_id, version = descriptor.findtext('id'), descriptor.findtext('version')
    if not plugin_id or not version:
        parser.error('Plugin ID/version missing')
    ides, targets = [], []
    for ide in args.ide:
        if (ide / 'Contents').is_dir(): ide /= 'Contents'
        metadata = ide / 'product-info.json'
        if not metadata.is_file(): metadata = ide / 'Resources/product-info.json'
        info = json.loads(metadata.read_text())
        targets.append(info['productCode'] + '-' + info['buildNumber'])
        ides.append(str(ide.resolve()))
    if len(set(targets)) != len(targets): parser.error('Duplicate IDE builds supplied')
    # A fresh report directory prevents old successful reports from masking a failed run.
    args.reports.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix='run-', dir=args.reports.resolve()))
    reports = run / 'reports'
    command = [args.java, '-Dplugin.verifier.home.dir=' + str(run / 'verifier-cache'),
               '-jar', str(args.verifier.resolve()), 'check-plugin', str(args.plugin.resolve()), *ides,
               '-runtime-dir', str(args.runtime.resolve()), '-offline',
               '-verification-reports-dir', str(reports)]
    with (run / 'verifier.log').open('w') as log:
        result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=900)
    errors = assess_reports(reports, targets, plugin_id, version)
    if result.returncode: errors.insert(0, f'Verifier exited with code {result.returncode}')
    summary = {'plugin': plugin_id, 'version': version, 'targets': targets,
               'passed': not errors, 'errors': errors}
    (run / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print('Reports:', reports)
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print('PASS: all requested builds verified; no deprecated, internal, experimental or incompatible API usage reported.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
