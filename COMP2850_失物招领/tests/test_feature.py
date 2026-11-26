"""Black-box checks for the packaged app. Uses Python's standard library and a temporary H2 database."""
import base64
import html
import mimetypes
import os
from pathlib import Path
import re
import socket
import struct
import subprocess
import tempfile
import time
import unittest
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib

ROOT = Path(__file__).resolve().parents[1]
JAR = Path(os.environ.get("LOST_FOUND_JAR", ROOT / "app" / "lost-found.jar"))


def png(width=1, height=1):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"\0\xff\0\0")) + chunk(b"IEND", b""))


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None


class FeatureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temp.cleanup)
        cls.logs = open(Path(cls.temp.name) / "server.log", "w+b")
        cls.addClassCleanup(cls.logs.close)
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            cls.port = sock.getsockname()[1]
        cls.base = f"http://127.0.0.1:{cls.port}"
        cls.client = urllib.request.build_opener(NoRedirect)
        cls.start()
        cls.addClassCleanup(cls.stop)

    @classmethod
    def start(cls):
        env = dict(os.environ, PORT=str(cls.port), LOST_FOUND_DATA=cls.temp.name)
        cls.proc = subprocess.Popen([os.environ.get("JAVA_EXE", "java"), "-jar", str(JAR)], env=env,
                                    stdout=cls.logs, stderr=subprocess.STDOUT, cwd=cls.temp.name)
        for _ in range(110):
            try:
                response = cls.fetch("/lost-found")[0]
                if response >= 500:
                    break
                if response == 200:
                    return
            except OSError:
                pass
            if cls.proc.poll() is not None:
                break
            time.sleep(.1)
        cls.stop()
        cls.logs.seek(0)
        raise RuntimeError(cls.logs.read(12000).decode(errors="replace"))

    @classmethod
    def stop(cls):
        cls.proc.terminate()
        cls.proc.wait(timeout=15)

    @classmethod
    def fetch(cls, path, data=None, headers=None):
        req = urllib.request.Request(cls.base + path, data=data, headers=headers or {})
        try:
            response = cls.client.open(req, timeout=8)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, response.read(), response.headers

    def new_key(self):
        code, body, _ = self.fetch("/lost-found/new")
        self.assertEqual(code, 200)
        return re.search(rb'name="key" value="([^"]+)"', body)[1].decode()

    def report(self, key=None, photo=None, **values):
        fields = dict(key=key or self.new_key(), name="Case", date="2026-01-05", tags="", place="")
        fields.update(values)
        boundary = uuid.uuid4().hex
        segments = []
        for name, value in fields.items():
            segments.extend([f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n".encode(),
                             value.encode(), b"\r\n"])
        if photo:
            filename, payload = photo
            mime = mimetypes.guess_type(filename)[0] or "application/octet-stream"
            segments.extend([f'--{boundary}\r\nContent-Disposition: form-data; name="photo"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n'.encode(), payload, b"\r\n"])
        segments.append(f"--{boundary}--\r\n".encode())
        return self.fetch("/lost-found/items", b"".join(segments), {"Content-Type": f"multipart/form-data; boundary={boundary}"})

    def test_01_upload_search_detail(self):
        key = self.new_key()
        response = self.report(key, ("item.png", png()), name="Red skirt", tags="clothing, RED", place="Library desk")
        self.assertEqual(response[0], 303, response[1][:600])
        dest = response[2]["Location"]
        self.assertEqual(self.report(key, name="Red skirt")[2]["Location"], dest)
        for name, tags in [("Red pen", "stationery"), ("Canvas bag", "red, textile"), ("Blue bottle", "water")]:
            self.assertEqual(self.report(name=name, tags=tags)[0], 303)
        code, page, _ = self.fetch("/lost-found?q=rEd")
        self.assertEqual(code, 200)
        for name in (b"Red skirt", b"Red pen", b"Canvas bag"):
            self.assertIn(name, page)
        self.assertNotIn(b"Blue bottle", page)
        self.assertIn(b'data-count="3"', page)
        self.assertIn(dest.encode(), page)
        partial = self.fetch("/lost-found/results?q=reD+clo")[1]
        self.assertIn(b"Red skirt", partial)
        self.assertNotIn(b"Red pen", partial)
        self.assertNotIn(b"<html", partial)
        detail = self.fetch(dest)[1]
        for field in (b"Library desk", b"2026-01-05", b"clothing", dest.encode()+b"/photo"):
            self.assertIn(field, detail)
        code, data, headers = self.fetch(dest+"/photo")
        self.assertEqual((code,headers.get_content_type()),(200,"image/png"))
        self.assertTrue(data.startswith(b"\x89PNG"))
        self.stop()
        self.start()
        self.assertIn(b"Red skirt", self.fetch(dest)[1])
        self.assertEqual(data, self.fetch(dest+"/photo")[1])

    def test_02_invalid_forms_and_no_result(self):
        for fields, status, code in [
            ({"name":"  "},422,"LF01"), ({"date":"2026-02-30"},422,"LF02"), ({"date":"9999-01-01"},422,"LF03"),
            ({"tags":",".join(str(n) for n in range(9))},422,"LF04"), ({"place":"x"*121},422,"LF05"),
            ({"photo":("fake.png",b"not a picture")},415,"LF06"),
            ({"photo":("large.png",b"0"*(5*1024*1024+1))},413,"LF07"),
            ({"photo":("short.png",png()[:24])},422,"LF08"),
            ({"photo":("large-dimensions.png",png(8000,8000))},422,"LF09"),
        ]:
            with self.subTest(code=code):
                got, body, _ = self.report(**fields)
                self.assertEqual(got,status)
                self.assertIn(code.encode(),body)
        self.assertIn(b'data-count="0"', self.fetch("/lost-found/results?q=zzznomatch")[1])
        self.assertIn(b'LF10',self.fetch("/lost-found?q="+"x"*101)[1])
        self.assertIn(b'LF11',self.fetch("/lost-found/items/9999999")[1])
        no_photo = self.report(name="No photo record")[2]["Location"]
        self.assertIn(b'LF12',self.fetch(no_photo+"/photo")[1])
        self.assertIn(b'LF18',self.fetch("/lost-found?page=0")[1])

    def test_03_literals_and_escaping(self):
        name='100%_green <example>'
        dest=self.report(name=name)[2]["Location"]
        self.assertIn(html.escape(name).encode(),self.fetch(dest)[1])
        for query in ["%", "_"]:
            page=self.fetch("/lost-found/results?q="+urllib.parse.quote(query))[1]
            self.assertIn(b'data-count="1"',page)
        code, body, _=self.fetch("/lost-found/items",b"x",{"Origin":"https://example.org"})
        self.assertEqual(code,403)
        self.assertIn(b"LF17",body)

    def test_04_jpeg_and_pagination(self):
        jpeg=base64.b64decode("/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAACAAIDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAb/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAABAb/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCNAEUj/9k=")
        dest=self.report(name="First notebook",tags="pagination",photo=("notebook.jpg",jpeg))[2]["Location"]
        self.assertEqual(self.fetch(dest+"/photo")[2].get_content_type(),"image/jpeg")
        for i in range(24):
            self.assertEqual(self.report(name=f"Notebook {i}",tags="pagination")[0],303)
        first=self.fetch("/lost-found?q=pagination")[1]
        second=self.fetch("/lost-found?q=pagination&page=2")[1]
        self.assertIn(b"Next page",first)
        self.assertNotIn(b"First notebook",first)
        self.assertIn(b"First notebook",second)
        self.assertIn(b"Previous page",second)
        self.assertIn(b"LF18",self.fetch("/lost-found?q=pagination&page=3")[1])

    def delete_form_data(self, item_path):
        code, page, _ = self.fetch(item_path + "/delete")
        self.assertEqual(code, 200)
        self.assertIn(b"Confirm deletion", page)
        self.assertIn(f'href="{item_path}">Cancel</a>'.encode(), page)
        token = re.search(rb'name="token" value="([^"]+)"', page)[1]
        return b"token=" + token

    def test_05_delete_with_confirmation(self):
        item = self.report(name="Violet test wallet", photo=("wallet.png", png()))[2]["Location"]
        other = self.report(name="Keep this item")[2]["Location"]
        self.assertIn((item + "/delete").encode(), self.fetch(item)[1])
        token = self.delete_form_data(item)
        self.assertEqual(self.fetch(item + "/photo")[0], 200)
        for path, data, headers, error in [
            (item, b"", {}, b"LF20"),
            (item, b"token=invalid", {}, b"LF20"),
            (other, token, {}, b"LF20"),
            (item, token, {"Origin": "https://example.org"}, b"LF17"),
        ]:
            status, body, _ = self.fetch(path + "/delete", data, headers)
            self.assertEqual(status, 403)
            self.assertIn(error, body)
        status, _, headers = self.fetch(item + "/delete", token)
        self.assertEqual((status, headers["Location"]), (303, "/lost-found"))
        self.assertEqual(self.fetch(item + "/delete", token)[0], 404)
        self.stop()
        self.start()
        for suffix in ("", "/photo", "/delete"):
            self.assertEqual(self.fetch(item + suffix)[0], 404)
        self.assertIn(b'data-count="0"', self.fetch("/lost-found/results?q=violet")[1])
        self.assertEqual(self.fetch(other)[0], 200)

    def import_examples(self):
        self.stop()
        try:
            subprocess.run([os.environ.get("JAVA_EXE", "java"), "-jar", str(JAR), "--load-examples"],
                           env=dict(os.environ, LOST_FOUND_DATA=self.temp.name), cwd=self.temp.name,
                           check=True, capture_output=True, timeout=20)
        finally:
            self.start()

    def test_06_examples_import_once(self):
        existing = self.report(name="Existing user entry")[2]["Location"]
        self.import_examples()
        self.assertEqual(self.fetch("/lost-found")[1].count(b'class="sample-label"'), 6)
        self.assertEqual(self.fetch("/lost-found/results?q=red")[1].count(b'class="sample-label"'), 3)
        matches = self.fetch("/lost-found/results?q=knitted")[1]
        item = re.search(rb'class="card" href="([^"]+)"', matches)[1].decode()
        self.assertEqual(self.fetch(item + "/delete", self.delete_form_data(item))[0], 303)
        self.import_examples()
        self.assertEqual(self.fetch("/lost-found")[1].count(b'class="sample-label"'), 5)
        self.assertIn(b'data-count="0"', self.fetch("/lost-found/results?q=knitted")[1])
        self.assertIn(b"Existing user entry", self.fetch(existing)[1])

if __name__ == "__main__":
    unittest.main(verbosity=2)

