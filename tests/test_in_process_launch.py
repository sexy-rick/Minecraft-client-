"""Source-level guardrails for launch paths (Android lifecycle still needs device tests)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/empyreal/client'

class InProcessLaunchTest(unittest.TestCase):
    def test_no_external_minecraft_launch_api(self):
        source = (JAVA / 'core/minecraft/MinecraftLauncher.kt').read_text()
        self.assertNotIn('getLaunchIntentForPackage', source)
        self.assertNotIn('fun openDeepLink', source)
        for path in (JAVA / 'game').glob('*.kt'):
            text = path.read_text()
            self.assertNotIn('MinecraftLauncher.launch(', text, str(path))
            self.assertNotIn('fallbackToExternalMinecraft', text, str(path))

    def test_host_failure_is_published(self):
        host = (JAVA / 'game/GameHost.kt').read_text()
        self.assertIn('fun reportFailure(', host)
        self.assertIn('reportFailure(lastError', host)
        self.assertIn('reportFailure(error)', host)

    def test_activity_failure_is_published(self):
        activity = (JAVA / 'game/GameActivity.kt').read_text()
        self.assertIn('GameHost.reportFailure(GameHost.lastError', activity)
        self.assertIn('GameHost.reportFailure(failure)', activity)
        self.assertNotIn('GameHost.onGameStopped()', activity.split('if (failure != null)', 1)[1].split('gameReady = true', 1)[0])

    def test_game_component_is_resolved_late_and_start_failure_caught(self):
        host = (JAVA / 'game/GameHost.kt').read_text()
        open_game = host.split('    private fun openGame(', 1)[1].split('    fun prepareGameEnvironment', 1)[0]
        self.assertNotIn('GameActivity::class', host)
        self.assertIn('setClassName(context, "dev.empyreal.client.game.GameActivity")', open_game)
        self.assertLess(open_game.index('runCatching {'), open_game.index('setClassName('))
        self.assertLess(open_game.index('runCatching {'), open_game.index('context.startActivity('))
        self.assertIn('reportFailure(error)', open_game)

    def test_home_does_not_claim_external_launch(self):
        home = (JAVA / 'ui/screens/HomeScreen.kt').read_text()
        self.assertNotIn('Opened the installed Minecraft instead', home)
        self.assertIn('Could not start Minecraft inside Empyreal', home)

class NativeSearchPathGuardrailTest(unittest.TestCase):
    def test_cache_priority_and_resolution_diagnostics_are_wired_in(self):
        host = (JAVA / 'game/GameHost.kt').read_text()
        self.assertIn('prioritizeExtractedNativePath(pathList)', host)
        self.assertIn('systemNativeLibraryDirectories', host)
        self.assertIn('findLibrary(name)', host)
        self.assertIn('listOf("c++_shared", "minecraftpe", "pairipcore")', host)

if __name__ == '__main__':
    unittest.main()
