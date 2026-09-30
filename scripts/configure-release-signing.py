"""Upload an existing release keystore to GitHub secrets without printing it."""

import base64
import getpass
import shutil
import subprocess
import sys
from pathlib import Path


def main():
    gh = shutil.which("gh")
    if not gh:
        raise SystemExit("Install GitHub CLI, then sign in using: gh auth login")
    subprocess.run([gh, "auth", "status"], check=True)
    repo = "Atx85/carbomon-tracker"
    print("Use the existing keystore that signed your previous release.")
    print("This sends four signing secrets to " + repo + ".")
    path = Path(input("Release keystore file: ").strip().strip('"')).expanduser()
    key_bytes = path.read_bytes()
    if not key_bytes:
        raise SystemExit("The keystore file is empty.")
    alias = input("Key alias: ").strip()
    store_password = getpass.getpass("Keystore password: ")
    key_password = getpass.getpass("Key password (Enter to use keystore password): ") or store_password
    if not alias or not store_password:
        raise SystemExit("A key alias and password are required.")
    secrets = {
        "ANDROID_RELEASE_STORE_PASSWORD": store_password.encode(),
        "ANDROID_RELEASE_KEY_ALIAS": alias.encode(),
        "ANDROID_RELEASE_KEY_PASSWORD": key_password.encode(),
        "ANDROID_RELEASE_KEYSTORE_BASE64": base64.b64encode(key_bytes),
    }
    for name, value in secrets.items():
        subprocess.run([gh, "secret", "set", name, "--repo", repo], input=value, check=True)
    print("Release signing secrets saved. Keep a private backup of the original keystore.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, subprocess.CalledProcessError) as error:
        sys.exit("Setup did not complete: " + str(error))
