import os
import sys
import json
import zipfile

def generate_plugins_json(builds_dir, repo_slug="ulgenzade/ulgencs3"):
    plugins = []
    
    metadata = {
        "TurkAnimeProvider": {
            "name": "Türk Anime TV",
            "description": "Türkiye'nin en büyük anime arşivi. Tüm fandom/çeviri grupları (AniSekai, Benihime, PuzzleSubs vb.) ve tüm medya oynatıcıları (Sibnet, OK.ru, Mail.ru, Doodstream, VOE, Vudea, M3U8 vb.) desteklenir.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://www.turkanime.tv/imajlar/favicon.ico"
        },
        "AnimeCixProvider": {
            "name": "AnimeCiX",
            "description": "AnimeciX resmi REST API ve TauVideo oynatıcı desteği ile yüksek hızlı anime akışı.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://animecix.tv/favicon.ico"
        },
        "AnizmProvider": {
            "name": "Anizm",
            "description": "Anizm anime arşivi. Cloudflare Turnstile koruması aşımı ve çoklu gömülü oynatıcı desteği.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://anizm.net/favicon.ico"
        },
        "AniziumProvider": {
            "name": "Anizium",
            "description": "Anizium anime platformu. 4K Ultra HD (2160p) video ve zengin alternatif oynatıcı desteği.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://anizium.co/favicon.ico"
        },
        "OpenAnimeProvider": {
            "name": "OpenAnime",
            "description": "OpenAnime Next.js modern anime platformu. Doğrudan HLS m3u8 akışları ve alternatif oynatıcılar.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://openani.me/favicon.ico"
        },
        "TrAnimeIzleProvider": {
            "name": "TrAnimeİzle",
            "description": "TrAnimeİzle arşivi. Çoklu alternatif sunucu ve harici oynatıcı desteği.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://www.tranimeizle.io/favicon.ico"
        }
    }

    for fname in sorted(os.listdir(builds_dir)):
        if not fname.endswith(".cs3"):
            continue

        fpath = os.path.join(builds_dir, fname)
        internal_name = fname[:-4]
        file_size = os.path.getsize(fpath)

        manifest = {}
        try:
            with zipfile.ZipFile(fpath, 'r') as z:
                if 'manifest.json' in z.namelist():
                    manifest = json.loads(z.read('manifest.json').decode('utf-8'))
        except Exception as e:
            print(f"Warning: Could not read manifest from {fname}: {e}")

        meta = metadata.get(internal_name, {})
        entry = {
            "name": meta.get("name", manifest.get("name", internal_name)),
            "internalName": internal_name,
            "pluginClassName": manifest.get("pluginClassName", f"com.ulgencs3.{internal_name.lower()}.{internal_name}"),
            "version": manifest.get("version", 1),
            "url": f"https://raw.githubusercontent.com/{repo_slug}/builds/{fname}",
            "apiVersion": 1,
            "fileSize": file_size,
            "status": 1,
            "language": "tr",
            "tvTypes": meta.get("tvTypes", ["Anime"]),
            "authors": ["ulgenzade"],
            "description": meta.get("description", f"{internal_name} Cloudstream eklentisi."),
            "iconUrl": meta.get("iconUrl", "")
        }
        plugins.append(entry)

    out_path = os.path.join(builds_dir, "plugins.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(plugins, f, indent=2, ensure_ascii=False)
    
    print(f"Successfully generated {out_path} with {len(plugins)} plugins.")

if __name__ == "__main__":
    b_dir = sys.argv[1] if len(sys.argv) > 1 else "builds"
    r_slug = sys.argv[2] if len(sys.argv) > 2 else os.getenv("GITHUB_REPOSITORY", "ulgenzade/ulgencs3")
    generate_plugins_json(b_dir, r_slug)
