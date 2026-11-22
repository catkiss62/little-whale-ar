"""Recover and verify the 31 reference clips from the author's local Quest APK."""
import argparse
import csv
import hashlib
import zipfile
from pathlib import Path

APK_HASH = "dc79c7775471d0e8f8092f2ae37f4dfb312deb95798804c5ef1855939f73fb39"


def collect(apk, inventory, destination):
    with apk.open("rb") as stream:
        digest = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    if digest.hexdigest() != APK_HASH:
        raise ValueError("需要原作者 v0.5.1-quest-test APK，当前文件指纹不符")
    with inventory.open(encoding="utf-8", newline="") as stream:
        clips = [r for r in csv.DictReader(stream, delimiter="\t") if r["来源"] == "Quest v0.5.1"]
    if len(clips) != 31:
        raise ValueError("清单缺少动作条目")
    with zipfile.ZipFile(apk) as archive:
        for clip in clips:
            entry = clip["文件或容器与片段"]
            data = archive.read(entry)
            expected = clip["SHA-256"]
            if hashlib.sha256(data).hexdigest() != expected:
                raise ValueError(f"动作校验失败 {entry}")
            target = destination / Path(entry).name
            if target.exists():
                if hashlib.sha256(target.read_bytes()).hexdigest() != expected:
                    raise FileExistsError(f"已有同名不同文件 {target}")
                continue
            destination.mkdir(parents=True, exist_ok=True)
            with target.open("xb") as out:
                out.write(data)
    print(f"已核对 {len(clips)} 个动作，目录 {destination}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("destination", type=Path)
    root = Path(__file__).resolve().parent.parent
    parser.add_argument("--inventory", type=Path, default=root / "docs/yuchat-motion-files.tsv")
    args = parser.parse_args()
    collect(args.apk, args.inventory, args.destination)
