#!/usr/bin/env python3
"""Download and checksum-verify an official Linux IDE SDK for CI. Requires Python 3.12+."""
import argparse
import hashlib
import json
import shutil
import sys
import tarfile
import tempfile
import urllib.parse
import urllib.request
from pathlib import Path


def fetch(url, destination):
    request = urllib.request.Request(url, headers={'User-Agent': 'AgentBridge-Compatibility-Check'})
    with urllib.request.urlopen(request, timeout=120) as response, destination.open('wb') as output:
        shutil.copyfileobj(response, output, 1024 * 1024)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--code', default='IIU', choices=['IIU', 'IIC'])
    parser.add_argument('--channel', default='release', choices=['release', 'eap'])
    parser.add_argument('--version')
    parser.add_argument('--directory', required=True, type=Path)
    args = parser.parse_args()
    if sys.version_info < (3, 12): parser.error('Python 3.12+ required for safe tar extraction')
    query = {'code': args.code, 'type': args.channel}
    if not args.version: query['latest'] = 'true'
    args.directory.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='download-', dir=args.directory) as temporary:
        temporary = Path(temporary)
        metadata = temporary / 'releases.json'
        fetch('https://data.services.jetbrains.com/products/releases?' + urllib.parse.urlencode(query), metadata)
        releases = json.loads(metadata.read_text()).get(args.code, [])
        if args.version: releases = [r for r in releases if r.get('version') == args.version]
        if not releases: raise RuntimeError('Requested SDK not published in the official release feed')
        release = releases[0]
        package = release['downloads']['linux']
        for key in ['link', 'checksumLink']:
            url = urllib.parse.urlparse(package[key])
            if url.scheme != 'https' or url.hostname != 'download.jetbrains.com':
                raise RuntimeError('Unexpected SDK download host')
        archive, checksum = temporary / 'ide.tar.gz', temporary / 'checksum.txt'
        print('Downloading SDK ' + release['build'], file=sys.stderr)
        fetch(package['link'], archive)
        fetch(package['checksumLink'], checksum)
        digest = hashlib.sha256()
        with archive.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b''): digest.update(chunk)
        if digest.hexdigest() != checksum.read_text().split()[0]: raise RuntimeError('SDK checksum mismatch')
        extracted = temporary / 'extracted'
        extracted.mkdir()
        with tarfile.open(archive) as tar: tar.extractall(extracted, filter='data')
        roots = [p.parent for p in extracted.glob('*/product-info.json')]
        if len(roots) != 1: raise RuntimeError('Expected one IDE directory')
        info = json.loads((roots[0] / 'product-info.json').read_text())
        if info['buildNumber'] != release['build']: raise RuntimeError('Extracted SDK build mismatch')
        destination = args.directory.resolve() / (info['productCode'] + '-' + info['buildNumber'])
        if destination.exists(): raise RuntimeError('Output SDK directory already exists: ' + str(destination))
        shutil.move(str(roots[0]), destination)
        (destination / 'agent-bridge-sdk-source.json').write_text(json.dumps({
            'url': package['link'], 'sha256': digest.hexdigest(), 'build': release['build'], 'channel': args.channel,
        }, indent=2) + '\n')
        print(destination)


if __name__ == '__main__': main()
