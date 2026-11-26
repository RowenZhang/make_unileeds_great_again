"""Windows regression: starting the desktop app twice must reuse the live site."""
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import unittest
import urllib.request


@unittest.skipUnless(os.name == "nt", "Windows desktop launcher")
class LauncherTests(unittest.TestCase):
    def test_second_launch_reuses_the_first(self):
        root = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as tmp, socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
            probe.close()
            env = dict(os.environ, PORT=str(port), LOST_FOUND_DATA=tmp)
            jar = Path(os.environ.get("LOST_FOUND_JAR", root / "app/lost-found.jar"))
            java = os.environ.get("JAVA_EXE", "java")
            server = subprocess.Popen([java, "-jar", str(jar)], cwd=root, env=env,
                                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            url = f"http://127.0.0.1:{port}/lost-found"
            try:
                for _ in range(80):
                    try:
                        with urllib.request.urlopen(url, timeout=1) as response:
                            self.assertIn(b"/lost-found/assets/main.css", response.read())
                        break
                    except OSError:
                        time.sleep(.1)
                else:
                    self.fail("First instance never served the board")
                result = subprocess.run(
                    [os.environ["ComSpec"], "/d", "/c", "Start.cmd", "--no-browser"],
                    cwd=root, env=env, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT, timeout=12, encoding="utf-8", errors="replace",
                )
                self.assertIn("Already running", result.stdout, result.stdout[:800])
                self.assertNotIn("BindException", result.stdout)
                self.assertEqual(result.returncode, 0)
                self.assertIsNone(server.poll())
                with urllib.request.urlopen(url, timeout=2) as response:
                    self.assertEqual(response.status, 200)
            finally:
                server.terminate()
                server.wait(timeout=10)


if __name__ == "__main__":
    unittest.main(verbosity=2)
