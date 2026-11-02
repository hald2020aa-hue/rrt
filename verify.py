#!/usr/bin/env python3
"""Static validation only. This does not substitute Android build/tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
import json
p=Path(__file__).resolve().parent
root=p/'app/src/main'
assert (p/'settings.gradle.kts').is_file()
assert (p/'.github/workflows/build-apk.yml').is_file()
assert 'com.rrt.neonreader' in (p/'app/build.gradle.kts').read_text()
for f in root.rglob('*.xml'):
    ET.parse(f)
    print('XML OK',f.relative_to(p))
for f in (p/'examples').rglob('*.json'):
    json.loads(f.read_text())
    print('JSON OK',f.relative_to(p))
files=list((root/'java').rglob('*.kt'))
assert len(files)>=5
for f in files:
    txt=f.read_text()
    assert txt.startswith('package com.rrt.neonreader')
    assert 'fillViewport = true' not in txt
    assert 'package com.pairip' not in txt
print('Static file checks passed;',len(files),'Kotlin files.')
print('WARNING: No Android SDK build was performed by this script.')
