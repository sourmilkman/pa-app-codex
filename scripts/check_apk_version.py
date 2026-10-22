"""Fail release publishing if UI build constants and tag could disagree."""
import re
import sys
from pathlib import Path
text = Path(sys.argv[1]).read_text()
name = re.search(r"versionName='([^']+)'", text).group(1)
code = re.search(r"versionCode='([^']+)'", text).group(1)
assert sys.argv[2] == f"v{name}+{code}", "APK version does not match release tag"
print(f"Verified APK version: {name} ({code})")
