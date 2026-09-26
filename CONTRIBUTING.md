# Contributing

Start with a reproducible issue or a small pull request. Describe the user-visible problem, the resulting behavior, and how you verified it. Keep changes focused.

## Development

Use JDK 21+, Python 3, and an IntelliJ IDEA SDK. Build and run the offline checks:

```sh
python3 scripts/build.py --ide /path/to/idea --test
python3 -m unittest discover -s scripts/tests -v
```

The test suite uses fake provider CLIs and does not make paid model requests. Live checks are explicit opt-ins. Python 3.12+ is required by the optional SDK download helper. See [release checks](docs/RELEASE-CHECKS.md) for minimum-SDK and cross-version verification.

## Standards

- Use supported IntelliJ APIs. Deprecated, removal-marked, internal and experimental APIs fail release checks.
- Keep file discovery and provider work off the UI thread. Preserve cancellation, bounded context, and process cleanup.
- Let provider CLIs own authentication. Never add credential files, tokens or real login URLs to code, tests, screenshots or issues.
- Preserve current editor buffers and explicit diff review. Do not enable autonomous writes as an incidental change.
- Document changed behavior and validate it at the appropriate level. Run native IDE smoke checks for UI changes.
- Keep generated ZIPs, classes, logs and SDK downloads out of Git. Publish installable ZIPs as release assets.

Do not change the public plugin ID after publication. Coordinate compatibility-range and protocol changes with the maintainer.
