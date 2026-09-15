#!/usr/bin/env python3
"""
UlgenCS3 - Domain Health Check & Auto-Resolver
Her 12 saatte GitHub Actions tarafından çalıştırılır.
HTTP 301/302 yönlendirmelerini takip ederek güncel domaini bulur.
"""

import json
import sys
import requests

DOMAINS_FILE = "domains.json"
TIMEOUT = 15
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                  "AppleWebKit/537.36 (KHTML, like Gecko) "
                  "Chrome/120.0.0.0 Safari/537.36"
}


def get_final_url(url: str) -> str | None:
    """HTTP 301/302 yönlendirmelerini takip ederek nihai URL'yi döndürür."""
    try:
        resp = requests.head(
            url,
            allow_redirects=True,
            timeout=TIMEOUT,
            headers=HEADERS
        )
        final = resp.url.rstrip("/")
        # Sadece protokol + host kısmını al
        from urllib.parse import urlparse
        parsed = urlparse(final)
        return f"{parsed.scheme}://{parsed.netloc}"
    except Exception as e:
        print(f"  [HATA] {url} → {e}")
        return None


def check_site_alive(url: str) -> bool:
    """Sitenin erişilebilir olup olmadığını kontrol eder."""
    try:
        resp = requests.get(url, timeout=TIMEOUT, headers=HEADERS)
        return resp.status_code < 500
    except Exception:
        return False


def main():
    try:
        with open(DOMAINS_FILE, "r", encoding="utf-8") as f:
            domains: dict = json.load(f)
    except FileNotFoundError:
        print(f"[HATA] {DOMAINS_FILE} bulunamadı!")
        sys.exit(1)

    updated = False
    errors = []

    for provider, current_url in domains.items():
        print(f"\n[?] {provider} kontrol ediliyor: {current_url}")

        latest_url = get_final_url(current_url)

        if latest_url is None:
            print(f"  [UYARI] {provider} erişilemiyor, mevcut URL korunuyor.")
            errors.append(provider)
            continue

        if latest_url != current_url:
            print(f"  [!] Domain değişti: {current_url} → {latest_url}")
            domains[provider] = latest_url
            updated = True
        else:
            # Yine de canlılık kontrolü yap
            alive = check_site_alive(current_url)
            status = "✓ Canlı" if alive else "✗ Yanıt vermiyor"
            print(f"  [{status}] {current_url}")
            if not alive:
                errors.append(provider)

    if updated:
        with open(DOMAINS_FILE, "w", encoding="utf-8") as f:
            json.dump(domains, f, indent=2, ensure_ascii=False)
        print(f"\n[OK] domains.json güncellendi.")
    else:
        print(f"\n[OK] Tüm domainler stabil, güncelleme yok.")

    if errors:
        print(f"\n[UYARI] Erişilemeyen sağlayıcılar: {', '.join(errors)}")
        # Kritik hata sayısı eşiği aşılmadıkça başarılı çık
        if len(errors) >= len(domains):
            sys.exit(1)


if __name__ == "__main__":
    main()
