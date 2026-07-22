#!/usr/bin/env python3
"""Create the small, model-explicit manifest consumed by the Android updater."""

import argparse
import hashlib
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--firmware", type=Path, required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    content = args.firmware.read_bytes()
    asset_name = args.firmware.name
    manifest = {
        "schemaVersion": 1,
        "version": args.version,
        "assets": [
            {
                "model": args.model,
                "name": asset_name,
                "url": (
                    f"https://github.com/{args.repository}/releases/download/"
                    f"{args.version}/{asset_name}"
                ),
                "size": len(content),
                "sha256": hashlib.sha256(content).hexdigest(),
            }
        ],
    }
    args.output.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
