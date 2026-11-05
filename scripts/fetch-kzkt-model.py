#!/usr/bin/env python3
"""
Ambil model bubble KZKT dari APK rilis tanpa mengunduh seluruh APK.

Repo kouzen-neo/kzkt hanya berisi dokumentasi; modelnya ada di dalam APK
sebagai `assets/models/kzkt.dat` (deflate + XOR satu byte 0x5A). Skrip ini:

1. membaca ukuran APK lewat HEAD,
2. mengambil EOCD + central directory lewat HTTP Range,
3. menemukan entri model, membaca local header untuk tahu awal data,
4. mengunduh data terkompresi per potongan (bukan 86 MB sekaligus),
5. inflate raw-deflate + XOR, lalu menulis ONNX float32,
6. memverifikasi CRC32, ukuran, dan (opsional) SHA-256.

Offset tidak di-hardcode, jadi versi KZKT baru cukup diganti lewat --tag.
"""

import argparse
import hashlib
import struct
import sys
import urllib.request
import zlib

ENTRY = "assets/models/kzkt.dat"
XOR_KEY = 0x5A
RAW_SIZE = 103_627_861
RAW_CRC = 0x060587A7
RAW_SHA256 = "c646d17e33d3577e5f2333977c4837e6b8110de45d9400fc00ad133dee4d3983"
DEFAULT_TAG = "v2.3.6"
CHUNK = 4 * 1024 * 1024
USER_AGENT = "GrooxTyper-model-fetcher/1.0"


def log(message):
    print(message, flush=True)


def request(url, *, start=None, end=None, method="GET"):
    headers = {"User-Agent": USER_AGENT}
    if start is not None and end is not None:
        headers["Range"] = f"bytes={start}-{end}"
    req = urllib.request.Request(url, headers=headers, method=method)
    return urllib.request.urlopen(req, timeout=180)


def content_size(url):
    with request(url, method="HEAD") as response:
        value = response.headers.get("Content-Length")
    if not value:
        raise SystemExit("APK tidak mengirim Content-Length")
    return int(value)


def read_range(url, start, end):
    with request(url, start=start, end=end) as response:
        data = response.read()
    expected = end - start + 1
    if len(data) != expected:
        raise SystemExit(
            f"Range {start}-{end} mengembalikan {len(data)} byte, harusnya {expected}"
        )
    return data


def find_model_entry(url, apk_size):
    tail_size = min(262_144, apk_size)
    tail_start = apk_size - tail_size
    tail = read_range(url, tail_start, apk_size - 1)
    eocd = tail.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise SystemExit("EOCD ZIP tidak ditemukan")
    cd_size, cd_offset = struct.unpack("<II", tail[eocd + 12:eocd + 20])
    if cd_offset >= tail_start:
        directory = tail[cd_offset - tail_start:cd_offset - tail_start + cd_size]
    else:
        directory = read_range(url, cd_offset, cd_offset + cd_size - 1)

    position = 0
    while position + 46 <= len(directory) and directory[position:position + 4] == b"PK\x01\x02":
        fields = struct.unpack("<IHHHHHHIIIHHHHHII", directory[position:position + 46])
        name_len, extra_len, comment_len = fields[10], fields[11], fields[12]
        name = directory[position + 46:position + 46 + name_len].decode("utf-8", "replace")
        if name == ENTRY:
            return {
                "name": name,
                "crc": fields[7],
                "compressed": fields[8],
                "uncompressed": fields[9],
                "local_offset": fields[16],
            }
        position += 46 + name_len + extra_len + comment_len
    raise SystemExit(f"Entri {ENTRY} tidak ditemukan di APK")


def local_data_offset(url, local_offset):
    header = read_range(url, local_offset, local_offset + 29)
    if header[:4] != b"PK\x03\x04":
        raise SystemExit("Local file header ZIP tidak valid")
    name_len, extra_len = struct.unpack("<HH", header[26:30])
    return local_offset + 30 + name_len + extra_len


def fetch_model(url, out_path, expected_sha256=None):
    apk_size = content_size(url)
    log(f"APK: {url} ({apk_size / 1_048_576:.1f} MB)")
    entry = find_model_entry(url, apk_size)
    log(
        "Entri: {name} crc={crc:08X} compressed={compressed} uncompressed={uncompressed}".format(
            **entry
        )
    )
    if entry["uncompressed"] != RAW_SIZE:
        raise SystemExit(
            f"Ukuran model berubah: {entry['uncompressed']} != {RAW_SIZE}. "
            "Perbarui RAW_SIZE/RAW_CRC setelah memeriksa model baru."
        )
    if entry["crc"] != RAW_CRC:
        raise SystemExit(
            f"CRC model berubah: {entry['crc']:08X} != {RAW_CRC:08X}. "
            "Model baru harus diverifikasi ulang sebelum dipakai."
        )

    data_start = local_data_offset(url, entry["local_offset"])
    data_end = data_start + entry["compressed"] - 1
    log(f"Data: {data_start}-{data_end}")
    decompressor = zlib.decompressobj(-15)
    table = bytes(value ^ XOR_KEY for value in range(256))
    crc = 0
    decoded_bytes = 0
    digest = hashlib.sha256()
    with open(out_path, "wb") as output:
        offset = data_start
        while offset <= data_end:
            end = min(offset + CHUNK - 1, data_end)
            chunk = read_range(url, offset, end)
            offset = end + 1
            try:
                raw = decompressor.decompress(chunk)
            except zlib.error as error:
                raise SystemExit(f"Inflate gagal: {error}")
            if raw:
                crc = zlib.crc32(raw, crc)
                decoded_bytes += len(raw)
                decoded = raw.translate(table)
                digest.update(decoded)
                output.write(decoded)
        tail = decompressor.flush()
        if tail:
            crc = zlib.crc32(tail, crc)
            decoded_bytes += len(tail)
            decoded = tail.translate(table)
            digest.update(decoded)
            output.write(decoded)

    if decoded_bytes != entry["uncompressed"]:
        raise SystemExit(f"Hasil inflate {decoded_bytes} byte, harusnya {entry['uncompressed']}")
    if (crc & 0xFFFFFFFF) != entry["crc"]:
        raise SystemExit(f"CRC hasil inflate {crc & 0xFFFFFFFF:08X} tidak cocok")
    sha256 = digest.hexdigest()
    log(f"ONNX float32: {decoded_bytes} byte, sha256={sha256}")
    expected = expected_sha256 or RAW_SHA256
    if sha256.lower() != expected.lower():
        raise SystemExit(
            f"SHA-256 model tidak cocok: {sha256} != {expected}. "
            "Model berubah; jangan dipakai tanpa verifikasi."
        )
    return sha256


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", default=DEFAULT_TAG, help="tag rilis KZKT, mis. v2.3.6")
    parser.add_argument("--out", required=True, help="path ONNX float32 hasil decode")
    parser.add_argument("--apk-url", default=None, help="override URL APK")
    parser.add_argument("--sha256", default=None, help="SHA-256 yang diharapkan")
    args = parser.parse_args()

    url = args.apk_url or (
        "https://github.com/kouzen-neo/kzkt/releases/download/"
        f"{args.tag}/KZKT-arm64-v8a-{args.tag}.apk"
    )
    fetch_model(url, args.out, args.sha256)
    log("OK")


if __name__ == "__main__":
    main()
