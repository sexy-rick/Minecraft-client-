"""Basic wiring guardrails; these do not replace Android runtime tests."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class ExperimentalRuntimeTest(unittest.TestCase):
    def test_no_original_provider_extraction_and_engine_requires_known_dependencies(self):
        host = (ROOT / 'app/src/main/java/dev/empyreal/client/game/GameHost.kt').read_text()
        self.assertIn('name == NativeLoadPlan.REPLACEMENT_PROVIDER) continue', host)
        self.assertIn('check(NativeLoadPlan.REPLACEMENT_PROVIDER in bundledAvailable)', host)
        self.assertIn('check(NativeLoadPlan.REPLACEMENT_PROVIDER in engineDependencies)', host)
        self.assertNotIn('PairipApkBinding.bind(', host)

    def test_experimental_mode_is_disclosed(self):
        home = (ROOT / 'app/src/main/java/dev/empyreal/client/ui/screens/HomeScreen.kt').read_text()
        report = (ROOT / 'app/src/main/java/dev/empyreal/client/core/log/DiagnosticReportCollector.kt').read_text()
        self.assertIn('Experimental: replaces PairIP integrity checks', home)
        self.assertIn('original integrity checks are not run', report)

if __name__ == '__main__':
    unittest.main()
