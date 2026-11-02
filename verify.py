"""Minimal source/static validation without Android SDK (not a substitute for Gradle)."""
from pathlib import Path
import json, xml.etree.ElementTree as ET
root = Path(__file__).parent
assert (root/'app/src/main/java/com/rrt/neonreader/MainActivity.kt').exists()
assert (root/'app/src/main/java/com/rrt/neonreader/ReaderActivity.kt').exists()
assert (root/'.github/workflows/build-apk.yml').exists()
ET.parse(root/'app/src/main/AndroidManifest.xml')
ET.parse(root/'app/src/main/res/values/styles.xml')
ET.parse(root/'app/src/main/res/drawable/neon_icon.xml')
example = json.loads((root/'examples/madara-example.json').read_text())
assert example['baseUrl'] == 'https://example.org'
print('Static structure, resource XML and source JSON: OK')
