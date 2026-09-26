# Contributing

Start with a reproducible issue or a small pull request. Describe the user-visible problem, the resulting behavior, and how you verified it. Keep changes focused.

## Development

Use a Java 21-capable JDK, Python 3.9+, and an IntelliJ IDEA SDK on macOS or Linux. Follow the [README development walkthrough](README.md#build-and-test-locally) to clone, select your SDK, build, test and install locally. Build and run the offline checks:

```sh
python3 scripts/build.py --ide /path/to/idea --test
python3 -m unittest discover -s scripts/tests -v
```

The test suite uses fake provider CLIs and does not make paid model requests. Live checks are explicit opt-ins. Python 3.12+ is required by the optional SDK download helper. See [release checks](docs/RELEASE-CHECKS.md) for minimum-SDK and cross-version verification.

## Pull requests

Fork the repository, branch from `main`, and open a pull request with the problem, resulting behavior, and validation. Add screenshots for UI changes and update usage docs. Real provider tests are optional unless the change requires them; disclose the CLI/model versions and quota usage, and use synthetic code.

## Standards

- Use supported IntelliJ APIs. Deprecated, removal-marked, internal and experimental APIs fail release checks.
- Keep file discovery and provider work off the UI thread. Preserve cancellation, bounded context, and process cleanup.
- Let provider CLIs own authentication. Never add credential files, tokens or real login URLs to code, tests, screenshots or issues.
- Preserve current editor buffers and explicit diff review. Do not enable autonomous writes as an incidental change.
- Document changed behavior and validate it at the appropriate level. Run native IDE smoke checks for UI changes.
- Keep generated ZIPs, classes, logs and SDK downloads out of Git. Publish installable ZIPs as release assets.

Do not change the public plugin ID after publication. Coordinate compatibility-range and protocol changes with the maintainer.
