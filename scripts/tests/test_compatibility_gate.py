import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('gate', Path(__file__).resolve().parents[1] / 'verify_compatibility.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

class CompatibilityGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.folder = self.root / 'IC-243.1/plugins/plugin/1.0'
        self.folder.mkdir(parents=True)

    def check_reports(self, targets=None):
        return gate.assess_reports(self.root, targets or ['IC-243.1'], 'plugin', '1.0')

    def verdict(self, text='Compatible'):
        (self.folder / 'verification-verdict.txt').write_text(text)

    def test_clean_report_passes(self):
        self.verdict()
        self.assertEqual([], self.check_reports())

    def test_missing_report_fails_even_if_other_build_passed(self):
        self.verdict()
        self.assertIn('missing', self.check_reports(['IC-243.1', 'IU-262.1'])[0])

    def test_zero_exit_with_deprecation_report_is_not_success(self):
        self.verdict()
        (self.folder / 'deprecated-usages.txt').write_text('Method is deprecated')
        self.assertTrue(self.check_reports())

    def test_every_unstable_api_category_blocks_release(self):
        self.verdict()
        for name in gate.REJECT_REPORTS:
            with self.subTest(name=name):
                report = self.folder / name
                report.write_text('Detected problem')
                self.assertTrue(self.check_reports())
                report.unlink()

    def test_non_clean_verdict_fails(self):
        self.verdict('Compatible. 1 usage of deprecated API')
        self.assertTrue(self.check_reports())

if __name__ == '__main__': unittest.main()
