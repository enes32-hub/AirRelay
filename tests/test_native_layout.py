"""Synthetic malformed ELF/ZIP regression corpus; never physical-device evidence."""

import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from scripts.verify_native import inspect_apk, inspect_elf


def elf32(alignment=4096, address=0, machine=40):
    data = bytearray(128)
    data[:7] = b"\x7fELF\x01\x01\x01"
    struct.pack_into("<HHI", data, 16, 3, machine, 1)
    struct.pack_into("<I", data, 28, 52)
    struct.pack_into("<HHH", data, 40, 52, 32, 1)
    struct.pack_into("<IIIIIIII", data, 52, 1, 0, address, 0, 128, 128, 5, alignment)
    return bytes(data)


def elf64(alignment=16384, address=0, machine=183, file_size=128, memory_size=128):
    data = bytearray(128)
    data[:7] = b"\x7fELF\x02\x01\x01"
    struct.pack_into("<HHI", data, 16, 3, machine, 1)
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<HHH", data, 52, 64, 56, 1)
    struct.pack_into("<IIQQQQQQ", data, 64, 1, 5, 0, address, 0, file_size, memory_size, alignment)
    return bytes(data)


def write_aligned_library(archive, name, data):
    """Build a stored ZIP entry with a valid extra field and 16 KB data offset."""
    info = zipfile.ZipInfo(name)
    padding = -(archive.fp.tell() + 30 + len(name.encode("utf-8"))) % 16384
    if padding < 4:
        padding += 16384
    info.extra = struct.pack("<HH", 0xFFFF, padding - 4) + bytes(padding - 4)
    archive.writestr(info, data)


class NativeLayoutTests(unittest.TestCase):
    def test_arm32_4kb_accepted_but_not_reported_as_16kb(self):
        result = inspect_elf(elf32(), "armeabi-v7a")
        self.assertEqual(result["elf_class"], 32)
        self.assertEqual(result["machine"], 40)
        self.assertEqual(result["required_elf_alignment"], 4096)
        self.assertFalse(result["elf_16kb_alignment"])

    def test_arm32_16kb_reported_truthfully(self):
        result = inspect_elf(elf32(alignment=16384), "armeabi-v7a")
        self.assertEqual(result["required_elf_alignment"], 4096)
        self.assertTrue(result["elf_16kb_alignment"])

    def test_arm32_below_4kb_rejected(self):
        for alignment in (0, 1, 2048):
            with self.subTest(alignment=alignment):
                with self.assertRaisesRegex(ValueError, "less than 4 KB"):
                    inspect_elf(elf32(alignment=alignment), "armeabi-v7a")

    def test_arm32_non_power_of_two_rejected(self):
        with self.assertRaisesRegex(ValueError, "power of two"):
            inspect_elf(elf32(alignment=6144), "armeabi-v7a")

    def test_arm32_wrong_machine_rejected(self):
        with self.assertRaisesRegex(ValueError, "matching ABI"):
            inspect_elf(elf32(machine=3), "armeabi-v7a")

    def test_arm32_cannot_satisfy_arm64_abi(self):
        with self.assertRaisesRegex(ValueError, "class"):
            inspect_elf(elf32(alignment=16384), "arm64-v8a")

    def test_arm32_offset_address_mismatch_rejected(self):
        with self.assertRaisesRegex(ValueError, "congruence"):
            inspect_elf(elf32(address=1), "armeabi-v7a")

    def test_valid_aligned_elf64(self):
        result = inspect_elf(elf64(), "arm64-v8a")
        self.assertEqual(result["required_elf_alignment"], 16384)
        self.assertTrue(result["elf_16kb_alignment"])

    def test_4kb_segment_rejected(self):
        with self.assertRaisesRegex(ValueError, "less than 16 KB"):
            inspect_elf(elf64(alignment=4096), "arm64-v8a")

    def test_x86_64_4kb_still_rejected(self):
        with self.assertRaisesRegex(ValueError, "less than 16 KB"):
            inspect_elf(elf64(alignment=4096, machine=62), "x86_64")

    def test_x86_64_16kb_accepted(self):
        self.assertTrue(inspect_elf(elf64(machine=62), "x86_64")["elf_16kb_alignment"])

    def test_64bit_non_power_of_two_rejected(self):
        with self.assertRaisesRegex(ValueError, "power of two"):
            inspect_elf(elf64(alignment=24576), "arm64-v8a")

    def test_wrong_machine_rejected(self):
        with self.assertRaisesRegex(ValueError, "matching ABI"):
            inspect_elf(elf64(machine=62), "arm64-v8a")

    def test_wrong_class_rejected(self):
        with self.assertRaisesRegex(ValueError, "class"):
            inspect_elf(elf64(), "armeabi-v7a")

    def test_offset_address_mismatch_rejected(self):
        with self.assertRaisesRegex(ValueError, "congruence"):
            inspect_elf(elf64(address=1), "arm64-v8a")

    def test_segment_past_eof_rejected(self):
        with self.assertRaisesRegex(ValueError, "bounds"):
            inspect_elf(elf64(file_size=256, memory_size=256), "arm64-v8a")

    def test_filesz_greater_than_memsz_rejected(self):
        with self.assertRaisesRegex(ValueError, "bounds"):
            inspect_elf(elf64(memory_size=64), "arm64-v8a")

    def test_header_table_past_eof_rejected(self):
        with self.assertRaises(ValueError):
            inspect_elf(elf64()[:70], "arm64-v8a")

    def test_not_an_elf_rejected(self):
        with self.assertRaises(ValueError):
            inspect_elf(b"not-an-elf", "arm64-v8a")

    def test_apk_with_no_libraries_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"fixture")
            with self.assertRaisesRegex(ValueError, "missing a required ARM ABI"):
                inspect_apk(path)

    def test_unaligned_library_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("lib/arm64-v8a/libfixture.so", elf64())
            with self.assertRaisesRegex(ValueError, "ZIP data offset"):
                inspect_apk(path)

    def test_apk_mixed_arm_alignments_accepted_and_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.apk"
            with zipfile.ZipFile(path, "w") as archive:
                write_aligned_library(archive, "lib/armeabi-v7a/libfixture.so", elf32())
                write_aligned_library(archive, "lib/arm64-v8a/libfixture.so", elf64())
            result = inspect_apk(path)
            self.assertTrue(result["zip_16kb_alignment"])
            libraries = {item["abi"]: item for item in result["libraries"]}
            self.assertFalse(libraries["armeabi-v7a"]["elf_16kb_alignment"])
            self.assertTrue(libraries["arm64-v8a"]["elf_16kb_alignment"])
            self.assertTrue(all(item["zip_offset"] % 16384 == 0 for item in libraries.values()))

    def test_apk_aligned_zip_does_not_hide_arm64_4kb_elf(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.apk"
            with zipfile.ZipFile(path, "w") as archive:
                write_aligned_library(archive, "lib/armeabi-v7a/libfixture.so", elf32())
                write_aligned_library(archive, "lib/arm64-v8a/libfixture.so", elf64(alignment=4096))
            with self.assertRaisesRegex(ValueError, "less than 16 KB"):
                inspect_apk(path)

    def test_apk_arm32_still_requires_aligned_zip(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("lib/armeabi-v7a/libfixture.so", elf32())
                write_aligned_library(archive, "lib/arm64-v8a/libfixture.so", elf64())
            with self.assertRaisesRegex(ValueError, "ZIP data offset"):
                inspect_apk(path)


if __name__ == "__main__":
    unittest.main()
