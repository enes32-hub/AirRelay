"""Create an initial, explicitly untested P0 matrix without overwriting results."""

import csv
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC_NAME = "Android_TV_iPhone_Ekran_Yansitma_Alicisi_AI_Ajan_Ana_Sartnamesi.md"

# Each entry maps the corresponding section 3.1 bullet to a concrete acceptance test.
P0_TESTS = [
    ("app-tv", "physical", "Android TV'ye kur; yalnız TV launcher, yatay ekran ve kumandayla başlatmayı doğrula."),
    ("receiver-discovery", "physical", "Gerçek iPhone sistem Ekran Yansıtma listesinde alıcıyı bul; keşif p95 ölç."),
    ("receiver-security", "physical", "Aynı alt ağda bağlan; WAN, VPN ve yetkisiz oturum denemelerini reddettiğini paket yakalamayla doğrula."),
    ("media-video", "physical", "iPhone canlı H.264 akışında kullanılan MediaCodec donanım decoder adını ve Surface yolunu kaydet."),
    ("media-audio", "physical", "Canlı iPhone ekran akışıyla eşzamanlı sistem sesi ve korumasız video sesini doğrula."),
    ("media-audio", "physical", "AAC-ELD ve AAC-LC için ayrı gerçek oturumlarda codec, ses ve underrun sonuçlarını kaydet."),
    ("media-audio", "physical", "ALAC gerektiren iPhone oturumunu kur; decoder kökeni, ses ve kaynak kapanışını doğrula."),
    ("media-video", "physical", "720p30, 1080p30 ve 1080p60 için ayrı 30 dakika oturum; bölüm 19 FPS/drop/donma eşiklerini ölç."),
    ("media-video", "physical", "20 yön değişimi ve çözünürlük değişimi uygula; doğru yeni kareye geçiş süresini ölç."),
    ("media-video", "physical", "Dikey/yatay canlı içerikte test ızgarasının oranını doğrula; esneme ve yeşil kare olmamalı."),
    ("receiver-security", "physical", "Varsayılan PIN açık; doğru/yanlış/süresi dolmuş PIN, brute force ve oturum sonrası iptali dene."),
    ("receiver-security", "physical", "Cihazı açık TV onayıyla güvenilir yap; unut; sonraki oturumun yeniden PIN istediğini doğrula."),
    ("receiver-service", "physical", "Açılış, manuel durdurma/başlatma, tekrar açılış ve izin/ağ yokluğunda davranışı doğrula."),
    ("app-tv", "physical", "Tüm ana işlevleri D-pad/OK/Back/Home ile kullan; TalkBack ve odak sırasını kaydet."),
    ("app-tv", "physical", "Gerçek yayın tam ekran; çubuk/cursor yok; OK ve Back katman davranışını doğrula."),
    ("media-video", "physical", "Sığdır, Doldur ve 1:1 ölçekleri canlı kaynak ızgarasıyla kontrol et; kırpma ve oranı ölç."),
    ("media-audio", "physical", "TV kumandasıyla sesi kapat/aç; video sürsün, ses geri gelsin ve patlama olmasın."),
    ("media-sync", "physical", "-250..500 ms, 10 ms adım, sıfırlama ve kalıcılığı clap ölçümüyle doğrula."),
    ("diagnostics", "physical", "FPS/drop/jitter/A-V sayaçlarını bağımsız ölçümle karşılaştır; uydurma istatistik olmasın."),
    ("diagnostics", "physical", "İzin reddi, multicast engeli, port doluluğu, decoder hatası ve DRM durumlarında doğru yardım göster."),
    ("receiver-service", "physical", "API 37'de izin ver/reddet/iptal et; izin yokken ilan ve soket yok; izin sonrası yeniden başlatmadan çalış."),
    ("receiver-service", "physical", "Kullanıcı etkinleştirmesi, FGS türü/bildirimi, arka plan/boot kısıtları ve varsayılan kapalı ayarı doğrula."),
    ("receiver-service", "physical", "20 bağlan/ayrıl sonrası fd/thread/codec/AudioTrack/wake ve multicast kilitlerinde sızıntı ölç."),
    ("receiver-service", "physical", "Process death, Home/dönüş, uyku/uyanma ve DHCP değişimi sonrası temiz toparlanmayı doğrula."),
    ("benchmark", "physical", "E2E-001..015, iPhone sürüm matrisi ve altı Android TV donanım sınıfı raporlarını incele."),
    ("app-tv", "build", "İmzalı AAB üret; paket kimliği, TV manifest, sürüm meta verisi ve bundletool doğrulamasını kaydet."),
    ("app-tv", "build", "İmzalı evrensel APK üret; apksigner doğrula; debug kapalı; kaynak commit ve SHA-256 kaydet."),
    ("receiver-native", "build", "AAB/APK ve her native bağımlılıkta armeabi-v7a ve arm64-v8a kapsamını kontrol et."),
    ("receiver-native", "physical", "Her .so için ELF ve APK ZIP hizalaması; 16 KB sayfa cihazında yükleme/çalışma testi."),
    ("app-tv", "review", "Gizlilik metnini fiili depolama/ağ davranışı ve E2E-015 raporuyla karşılaştır."),
    ("receiver-native", "review", "Dağıtılan tüm transitif bağımlılıkların lisans/NOTICE ve kaynak sunum bağlantılarını doğrula."),
    ("app-tv", "build", "Gerçek release grafiğinden SPDX/CycloneDX SBOM üret; APK/AAB bileşenleriyle karşılaştır."),
    ("receiver-native", "review", "Sabit çekirdek ve tüm dosya/altmodüllerin köken/lisans raporunu, kripto materyali ve dağıtım yükümlülüklerini incele."),
]


def build(spec: str) -> tuple[list, dict]:
    """Preserve source lines and bind every section 3.1 item to an acceptance ID."""
    rows, tests = [], {}
    section, heading, p0_index = "", "", 0
    e2e_id, e2e_lines = None, []
    for number, line in enumerate(spec.splitlines(), 1):
        if line.startswith("## E2E-"):
            if e2e_id:
                tests[e2e_id] = {"kind": "physical", "procedure": "\n".join(e2e_lines).strip()}
            e2e_id = re.search(r"E2E-\d{3}", line).group()
            e2e_lines = [line.removeprefix("## ")]
        elif e2e_id and line.startswith("# "):
            tests[e2e_id] = {"kind": "physical", "procedure": "\n".join(e2e_lines).strip()}
            e2e_id = None
        elif e2e_id:
            e2e_lines.append(line)
        match = re.match(r"^## (\d+\.\d+)\.", line)
        if match:
            section, heading = match.group(1), line.removeprefix("## ")
        elif line.startswith("# "):
            section = ""
        if section == "3.1" and line.startswith("- "):
            module, kind, procedure = P0_TESTS[p0_index]
            p0_index += 1
            test_id = f"P0-{p0_index:03}"
            tests[test_id] = {"kind": kind, "procedure": procedure}
            description = line[2:].replace("Android TV/Google TV", "Android TV").replace("iPhone/iPad", "iPhone")
            rows.append(row(f"REQ-P0-{p0_index:03}", description, module, test_id, number, line, section))
        elif section.startswith("19."):
            is_bullet = line.startswith("- ")
            is_value = line.startswith("|") and any(x in line for x in ("≤", "±"))
            if not (is_bullet or is_value):
                continue
            test_id = "PERF-" + section.replace(".", "-")
            tests.setdefault(test_id, {"kind": "physical", "procedure": f"Şartname {heading}; docs/PERFORMANCE_METHOD.md yöntemiyle tüm eşikleri ölç."})
            rows.append(row(f"REQ-PERF-{len([r for r in rows if r['REQ-ID'].startswith('REQ-PERF')]) + 1:03}",
                            f"{heading}: {line.removeprefix('- ')}", "benchmark", test_id, number, line, section))
    if p0_index != len(P0_TESTS):
        raise ValueError("Section 3.1 changed: manually review test mapping before seeding")
    return rows, tests


def row(req_id: str, description: str, module: str, test: str, line: int, text: str, section: str) -> dict:
    """Build an untested requirement record with exact source linkage."""
    return {"REQ-ID": req_id, "Açıklama": description, "Öncelik": "P0", "Uygulama modülü": module,
            "Test-ID": test, "Test sonucu": "NOT_RUN", "Kanıt dosyası": "",
            "Kaynak bölüm": section, "Kaynak satır": str(line), "Kaynak metin": text}


def main() -> None:
    matrix = ROOT / "REQUIREMENTS_TRACEABILITY.csv"
    catalog = ROOT / "docs/TEST_CATALOG.json"
    if matrix.exists() or catalog.exists():
        raise SystemExit("Refusing to overwrite existing results. Maintain the matrix and catalog by reviewed edits.")
    raw = (ROOT / SPEC_NAME).read_bytes()
    rows, tests = build(raw.decode("utf-8"))
    with matrix.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=rows[0].keys(), lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    catalog.write_text(json.dumps({"spec_file": SPEC_NAME, "spec_sha256": hashlib.sha256(raw).hexdigest(),
                                  "coverage": "Section 3.1 and selected section 19 acceptance thresholds; detailed coverage is incomplete.",
                                  "tests": tests}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Created {len(rows)} NOT_RUN requirements and {len(tests)} acceptance test definitions.")


if __name__ == "__main__":
    main()
