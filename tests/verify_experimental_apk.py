"""Validate the CI debug APK contains the experimental arm64 provider and notices."""
import hashlib
import subprocess
import sys
import tempfile
from pathlib import Path
from zipfile import ZipFile

apk = Path(sys.argv[1])
with ZipFile(apk) as archive:
    bad = archive.testzip()
    if bad:
        raise SystemExit(f'APK contains corrupt entry: {bad}')
    entries = set(archive.namelist())
    provider = 'lib/arm64-v8a/libpairipcore.so'
    notices = {
        'assets/third_party_notices/UnpairCore-LICENSE.txt',
        'assets/third_party_notices/GamePwnage-LICENSE.txt',
    }
    missing = ({provider} | notices) - entries
    if missing:
        raise SystemExit(f'APK missing required entries: {sorted(missing)}')
    data = archive.read(provider)
    with tempfile.NamedTemporaryFile() as shared:
        shared.write(data)
        shared.flush()
        symbols = subprocess.check_output(['readelf', '--dyn-syms', '--wide', shared.name], text=True)
        for name in ('JNI_OnLoad', 'ExecuteProgram'):
            if not any(line.split()[-1] == name and 'GLOBAL' in line and 'UND' not in line
                       for line in symbols.splitlines() if line.split()):
                raise SystemExit(f'Replacement provider is missing export {name}')
print(f'APK verification passed: {provider}; licenses and required exports present')
print(f'APK SHA-256: {hashlib.sha256(apk.read_bytes()).hexdigest()}')
