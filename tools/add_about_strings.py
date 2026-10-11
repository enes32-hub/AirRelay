"""Add the About-section notices to every locale set.

Adds the privacy-policy entry, its failure message, the developer contact line and the source-code
line, and extends `p_version` so the About screen shows the version code as well as the version
name. The copyright/licence line is language-independent and lives only in the default `values/`
set; Android falls back to it for every locale.
"""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"

CONTACT = "alquariz@yandex.com"
SOURCE_URL = "https://github.com/enes32-hub/AirRelay"

VERSION_SUFFIX = " (%2$d)"
COPYRIGHT = "© 2026 Mahmut Enes Bozdoğan — GPL-3.0"

T = {
    "values": ("Privacy policy", "Privacy policy could not be opened. Please contact the app provider.", "Contact", "Source code", "Version"),
    "values-tr": ("Gizlilik politikası", "Gizlilik politikası açılamadı. Lütfen uygulama sağlayıcısına başvurun.", "İletişim", "Kaynak kodu", "Sürüm"),
    "values-de": ("Datenschutzerklärung", "Die Datenschutzerklärung konnte nicht geöffnet werden. Bitte wenden Sie sich an den App-Anbieter.", "Kontakt", "Quellcode", "Version"),
    "values-fr": ("Politique de confidentialité", "La politique de confidentialité n'a pas pu être ouverte. Veuillez contacter le fournisseur de l'application.", "Contact", "Code source", "Version"),
    "values-es": ("Política de privacidad", "No se pudo abrir la política de privacidad. Póngase en contacto con el proveedor de la aplicación.", "Contacto", "Código fuente", "Versión"),
    "values-it": ("Informativa sulla privacy", "Impossibile aprire l'informativa sulla privacy. Contatta il fornitore dell'app.", "Contatti", "Codice sorgente", "Versione"),
    "values-pt-rBR": ("Política de privacidade", "Não foi possível abrir a política de privacidade. Entre em contato com o fornecedor do aplicativo.", "Contato", "Código-fonte", "Versão"),
    "values-ru": ("Политика конфиденциальности", "Не удалось открыть политику конфиденциальности. Обратитесь к поставщику приложения.", "Контакты", "Исходный код", "Версия"),
    "values-ar": ("سياسة الخصوصية", "تعذّر فتح سياسة الخصوصية. يُرجى التواصل مع مزوّد التطبيق.", "التواصل", "الشيفرة المصدرية", "الإصدار"),
    "values-hi": ("गोपनीयता नीति", "गोपनीयता नीति खोली नहीं जा सकी। कृपया ऐप प्रदाता से संपर्क करें।", "संपर्क", "स्रोत कोड", "संस्करण"),
    "values-ja": ("プライバシーポリシー", "プライバシーポリシーを開けませんでした。アプリ提供者にお問い合わせください。", "お問い合わせ", "ソースコード", "バージョン"),
    "values-ko": ("개인정보 처리방침", "개인정보 처리방침을 열 수 없습니다. 앱 제공자에게 문의하세요.", "문의", "소스 코드", "버전"),
    "values-zh-rCN": ("隐私政策", "无法打开隐私政策。请联系应用提供方。", "联系", "源代码", "版本"),
    "values-th": ("นโยบายความเป็นส่วนตัว", "ไม่สามารถเปิดนโยบายความเป็นส่วนตัวได้ โปรดติดต่อผู้ให้บริการแอป", "ติดต่อ", "ซอร์สโค้ด", "เวอร์ชัน"),
    "values-vi": ("Chính sách bảo mật", "Không thể mở chính sách bảo mật. Vui lòng liên hệ nhà cung cấp ứng dụng.", "Liên hệ", "Mã nguồn", "Phiên bản"),
    "values-in": ("Kebijakan privasi", "Kebijakan privasi tidak dapat dibuka. Silakan hubungi penyedia aplikasi.", "Kontak", "Kode sumber", "Versi"),
}


def escape(value: str) -> str:
    return (value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("'", "\\'").replace('"', '\\"'))


def target_file(folder: Path) -> Path | None:
    for candidate in sorted(folder.glob("*.xml")):
        try:
            root = ET.parse(candidate).getroot()
        except ET.ParseError:
            continue
        if any(s.get("name") == "p_version" for s in root.iter("string")):
            return candidate
    return None


def main() -> int:
    changed = []
    for folder_name, (privacy, unavailable, contact, source, version_word) in T.items():
        folder = RES / folder_name
        if not folder.is_dir():
            print(f"  ! {folder_name} missing")
            continue
        path = target_file(folder)
        if path is None:
            print(f"  ! {folder_name}: no file defines p_version")
            continue
        text = path.read_text(encoding="utf-8")

        text, n = re.subn(
            r'<string name="p_version">[^<]*</string>',
            f'<string name="p_version">{escape(version_word)} %1$s{escape(VERSION_SUFFIX)}</string>',
            text, count=1,
        )
        block = [
            f'    <string name="p_privacy_policy">{escape(privacy)}</string>',
            f'    <string name="p_privacy_unavailable">{escape(unavailable)}</string>',
            f'    <string name="p_contact_line">{escape(contact)}: {CONTACT}</string>',
            f'    <string name="p_source_line">{escape(source)}: {SOURCE_URL}</string>',
            # Language-independent legal notice: the same text in every locale, but it must exist
            # everywhere because the source check compares key sets across locales.
            f'    <string name="p_copyright_notice">{escape(COPYRIGHT)}</string>',
        ]
        for key in ("p_privacy_policy", "p_privacy_unavailable", "p_contact_line", "p_source_line", "p_copyright_notice"):
            text = re.sub(rf'\n\s*<string name="{key}">[^<]*</string>', "", text)
        text = text.replace("</resources>", "\n".join(block) + "\n</resources>")
        path.write_text(text, encoding="utf-8", newline="\n")
        changed.append(f"{folder_name} ({path.name}, p_version x{n})")

    print(f"  guncellenen dil dosyasi: {len(changed)}")
    for c in changed:
        print("    " + c)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
