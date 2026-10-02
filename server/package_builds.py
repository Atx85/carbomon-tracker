"""Package the already-built executables; no extra Python packages required."""
from pathlib import Path
import hashlib
import io
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parent
DIST = ROOT / 'dist'

WINDOWS_HELP = '''CarboMon catalogue server - Windows (64-bit)

1. Extract all files into a folder.
2. Double-click carbomon-catalog-windows-amd64.exe. Keep its window open.
3. Open http://localhost:8765 in your browser.
4. In the app use Setup > Shared food and recipe catalogue > Find server.
5. Tap Sync foods and recipes. No access key is needed.

Allow this program on your private network if Windows Firewall asks.
Other CarboMon servers on the same trusted LAN automatically collect missing
foods and recipes. Existing entries are preserved. See README.md for details.

To migrate or back up: stop the server with Ctrl+C and copy the entire data folder.
Run only one copy of a migrated data folder at a time.
'''

PI_HELP = '''CarboMon catalogue server - Raspberry Pi 4B

1. Extract this archive into a folder on the Pi.
2. Open a terminal in that folder and run: ./start-catalog.sh
3. Open http://localhost:8765 on the Pi, or use the Pi's LAN address on another device.
4. In the app use Setup > Shared food and recipe catalogue > Find server.
5. Tap Sync foods and recipes. No access key is needed.

The launcher selects the included 64-bit or 32-bit executable for your system.
No Apache, Node, Go, Python, or separate database installation is needed to run it.
Keep the terminal open while using the server. Stop it with Ctrl+C.

Other CarboMon servers on the same trusted LAN automatically collect missing
foods and recipes. Existing entries are preserved. See README.md for details.
If a firewall is enabled, allow TCP 8765 and UDP 5353 on your private network.

To migrate or back up: stop the server and copy the entire data folder.
Run only one copy of a migrated data folder at a time.
'''

LAUNCHER = '''#!/bin/sh
set -eu
cd -- "$(dirname -- "$0")"
case "$(uname -m)" in
    aarch64|arm64) executable=./carbomon-catalog-linux-arm64 ;;
    armv7l|armv8l) executable=./carbomon-catalog-linux-armv7 ;;
    *) echo "This package is for a Raspberry Pi running 32-bit or 64-bit ARM Linux." >&2; exit 1 ;;
esac
exec "$executable" "$@"
'''

common = {name: (ROOT / name).read_bytes() for name in ['README.md', 'THIRD_PARTY_NOTICES.txt']}
windows = dict(common, **{'START-HERE.txt': WINDOWS_HELP.encode(),
    'carbomon-catalog-windows-amd64.exe': (DIST / 'carbomon-catalog-windows-amd64.exe').read_bytes()})
with zipfile.ZipFile(DIST / 'carbomon-catalog-windows.zip', 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    for name, content in windows.items():
        archive.writestr('carbomon-catalog-windows/' + name, content)

pi = dict(common, **{'START-HERE.txt': PI_HELP.encode(), 'start-catalog.sh': LAUNCHER.encode(),
    'carbomon-catalog-linux-arm64': (DIST / 'carbomon-catalog-linux-arm64').read_bytes(),
    'carbomon-catalog-linux-armv7': (DIST / 'carbomon-catalog-linux-armv7').read_bytes()})
with tarfile.open(DIST / 'carbomon-catalog-raspberry-pi.tar.gz', 'w:gz') as archive:
    for name, content in pi.items():
        info = tarfile.TarInfo('carbomon-catalog-raspberry-pi/' + name)
        info.size = len(content)
        info.mode = 0o755 if name.endswith('.sh') or name.startswith('carbomon-catalog-linux-') else 0o644
        archive.addfile(info, io.BytesIO(content))

packages = ['carbomon-catalog-windows.zip', 'carbomon-catalog-raspberry-pi.tar.gz']
(DIST / 'DOWNLOAD-SHA256SUMS.txt').write_text(''.join(
    hashlib.sha256((DIST / name).read_bytes()).hexdigest() + '  ' + name + '\n' for name in packages))
for name in packages:
    print(f'{name}: {(DIST / name).stat().st_size:,} bytes')
