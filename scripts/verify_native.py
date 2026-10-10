"""Verify ABI-specific ELF load alignment and uncompressed APK 16 KB ZIP alignment."""

import argparse
import json
import struct
import zipfile
from pathlib import Path

PAGE = 16384
ABI = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183), "x86_64": (2, 62)}
# Android's 16 KB page-size requirement applies to 64-bit native libraries.
# Keep ARM32 compatible with its 4 KB target without reporting it as 16 KB aligned.
ELF_MIN_ALIGNMENT = {"armeabi-v7a": 4096, "arm64-v8a": PAGE, "x86_64": PAGE}
MAX_LIBRARY_BYTES = 128 * 1024 * 1024


def inspect_elf(data: bytes, abi: str) -> dict:
    """Validate ELF program headers, not decoder performance or runtime compatibility."""
    if abi not in ABI:
        raise ValueError("Unsupported ABI")
    if len(data) < 52 or data[:4] != b"\x7fELF" or data[5] != 1 or data[6] != 1:
        raise ValueError("Expected little-endian ELF version 1")
    elf_class, machine = ABI[abi]
    required_alignment = ELF_MIN_ALIGNMENT[abi]
    if data[4] != elf_class:
        raise ValueError("ELF class does not match ABI")
    kind, actual_machine = struct.unpack_from("<HH", data, 16)
    if kind != 3 or actual_machine != machine:
        raise ValueError("Expected ET_DYN shared library matching ABI")
    if elf_class == 1:
        offset = struct.unpack_from("<I", data, 28)[0]
        entry_size, count = struct.unpack_from("<HH", data, 42)
        layout = "<IIIIIIII"
    else:
        if len(data) < 64:
            raise ValueError("Truncated ELF64 header")
        offset = struct.unpack_from("<Q", data, 32)[0]
        entry_size, count = struct.unpack_from("<HH", data, 54)
        layout = "<IIQQQQQQ"
    if not 1 <= count <= 1024 or entry_size != struct.calcsize(layout):
        raise ValueError("Invalid program header count or entry size")
    if offset < (52 if elf_class == 1 else 64) or offset + count * entry_size > len(data):
        raise ValueError("Program header table exceeds file bounds")
    segments = []
    for index in range(count):
        header = struct.unpack_from(layout, data, offset + index * entry_size)
        if header[0] != 1:  # PT_LOAD only
            continue
        if elf_class == 1:
            _, file_offset, address, _, file_size, memory_size, _, alignment = header
        else:
            _, _, file_offset, address, _, file_size, memory_size, alignment = header
        if file_size > memory_size or file_offset + file_size > len(data):
            raise ValueError("Load segment exceeds file or memory bounds")
        if alignment < required_alignment or alignment & (alignment - 1):
            raise ValueError(
                f"PT_LOAD alignment is less than {required_alignment // 1024} KB "
                f"for {abi} or not a power of two"
            )
        if file_offset % alignment != address % alignment:
            raise ValueError("PT_LOAD offset/address congruence is invalid")
        segments.append({"offset": file_offset, "vaddr": address, "filesz": file_size,
                         "memsz": memory_size, "align": alignment})
    if not segments:
        raise ValueError("No loadable segments")
    return {"abi": abi, "elf_class": 32 if elf_class == 1 else 64, "machine": machine,
            "segments": segments, "required_elf_alignment": required_alignment,
            "elf_16kb_alignment": all(segment["align"] >= PAGE for segment in segments)}


def inspect_apk(path: Path) -> dict:
    """Require both ARM ABIs and direct-load ZIP alignment; do not extract any entry."""
    records, seen = [], set()
    with zipfile.ZipFile(path) as archive, path.open("rb") as raw:
        entries = archive.infolist()
        if len(entries) > 50000:
            raise ValueError("APK entry count exceeds inspection limit")
        for info in entries:
            if info.filename in seen:
                raise ValueError("Duplicate ZIP entry")
            seen.add(info.filename)
            if not info.filename.endswith(".so"):
                continue
            parts = info.filename.split("/")
            if len(parts) != 3 or parts[0] != "lib" or parts[1] not in ABI or not parts[2].startswith("lib"):
                raise ValueError("Unexpected native library path")
            if info.file_size > MAX_LIBRARY_BYTES:
                raise ValueError("Native library exceeds inspection size limit")
            if info.compress_type != zipfile.ZIP_STORED or info.flag_bits & 1:
                raise ValueError("This check requires uncompressed, unencrypted directly loaded libraries")
            raw.seek(info.header_offset)
            local = raw.read(30)
            if len(local) != 30 or local[:4] != b"PK\x03\x04":
                raise ValueError("Invalid local ZIP header")
            name_length, extra_length = struct.unpack_from("<HH", local, 26)
            data_offset = info.header_offset + 30 + name_length + extra_length
            if data_offset % PAGE:
                raise ValueError("Native library ZIP data offset is not 16 KB aligned")
            records.append({"path": info.filename, "zip_offset": data_offset,
                            **inspect_elf(archive.read(info), parts[1])})
    present = {record["abi"] for record in records}
    if not {"armeabi-v7a", "arm64-v8a"}.issubset(present):
        raise ValueError("APK is missing a required ARM ABI")
    names = {abi: {r["path"].split("/")[-1] for r in records if r["abi"] == abi} for abi in present}
    if names["armeabi-v7a"] != names["arm64-v8a"]:
        raise ValueError("Required ARM ABIs have different native library sets")
    return {"libraries": records, "required_arm_abis": True, "zip_16kb_alignment": True}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--elf", type=Path)
    source.add_argument("--apk", type=Path)
    parser.add_argument("--abi", choices=ABI)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        if args.elf:
            if not args.abi:
                raise ValueError("--elf requires --abi")
            if args.elf.stat().st_size > MAX_LIBRARY_BYTES:
                raise ValueError("Native library exceeds inspection size limit")
            result = inspect_elf(args.elf.read_bytes(), args.abi)
        else:
            result = inspect_apk(args.apk)
        report = {"ok": True, "scope": "artifact-layout-only", "runtime_verified": False, "result": result}
    except (ValueError, OSError, zipfile.BadZipFile, struct.error) as error:
        report = {"ok": False, "error": str(error), "runtime_verified": False}
    text = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    print(text)
    raise SystemExit(0 if report["ok"] else 2)


if __name__ == "__main__":
    main()
