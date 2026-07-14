#!/usr/bin/env python3
"""Build the DawaNow product seed from Egyptian prices and image catalogs.

Primary data: https://github.com/karem505/egyptian-drug-database
Image indexes: https://demov2.egypt.dawatech.com/sitemap.xml,
https://www.bloompharmacy.com/sitemap.xml,
https://alfouadpharmacies.com/sitemap.xml, and
https://www.kaggle.com/datasets/drowsyng/medicines-dataset
"""

import argparse
import csv
import hashlib
import re
import unicodedata
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from difflib import SequenceMatcher
from pathlib import Path


NON_MEDICINE_CLASS = re.compile(
    r"(?:SKIN\s*CARE|HAIR\s*CARE|BODY\s*CARE|PERSONAL\s*CARE|COSMETIC|"
    r"MULTI\s*VITAMIN|VITAMINS?|MINERAL|SUPPLEMENT|NUTRITION|"
    r"BABY\s*(?:FOOD|MILK|FORMULA|CARE)|INFANT\s*FORMULA|"
    r"SHAMPOO|CONDITIONER|SOAP|CLEANSER|MOISTURI[ZS]|SUN\s*(?:SCRE+N|BLOCK)|"
    r"MASSAGE|TOOTH|MOUTH\s*WASH|DEODORANT|PERFUME|MAKEUP|DIAPER|"
    r"WEIGHT\s*(?:GAIN|LOSS)|SEXUAL\s*LUBRICANT|EYE\s*CONTOUR|ANTI\s*AGING|"
    r"ANTI\s*WRINKLE|WHITEN|LIGHTEN|BEAUTY|INTIMATE|FACIAL|BATH|SHOWER|"
    r"LIP\s*CARE|NAIL\s*CARE|HAND\s*CARE|FOOT\s*CARE|ORAL\s*CARE|"
    r"CONTACT\s*LENS|BREAST\s*FEED|MOTHER\s*CARE)",
    re.IGNORECASE,
)

UNAVAILABLE_NAME = re.compile(r"\((?:CANCELLED|N/A)\)", re.IGNORECASE)
NON_MEDICINE_NAME = re.compile(
    r"(?:SUN\s*SCRE+N|SUN\s*BLOCK|EYE\s*CONTOUR|INFANT\s*FORMULA|"
    r"BABY.*\bMILK\b|INTIMATE.*(?:WASH|GEL)|VAG(?:INAL|\.)?\s*(?:WASH|DOU?CH(?:E)?)|"
    r"WHITENING|LIGHTENING|\bSUPPLEMENT\b)",
    re.IGNORECASE,
)
NON_MEDICINE_MANUFACTURER = re.compile(
    r"(?:SUPPLEMENTS?|NUTRITIONAL|COSMETIC|PERFUME|\bORGANIX\b|\bORGANICS\b)",
    re.IGNORECASE,
)

TOKEN_ALIASES = {
    "TABLET": "TAB",
    "TABLETS": "TAB",
    "TABS": "TAB",
    "CAPSULE": "CAP",
    "CAPSULES": "CAP",
    "CAPS": "CAP",
    "SUSPENSION": "SUSP",
    "SYR": "SYRUP",
    "SYPUP": "SYRUP",
    "MILLIGRAM": "MG",
    "MILLIGRAMS": "MG",
    "GRAM": "GM",
    "GRAMS": "GM",
}

NOISE_TOKENS = {
    "C",
    "F",
    "FC",
    "IMP",
    "IMPORTED",
    "NEW",
    "NEWW",
    "ORIGINAL",
    "PACK",
    "SAUDI",
}

INGREDIENT_NOISE = {
    "ACID",
    "ANHYDROUS",
    "GENERIC",
    "HYDROCHLORIDE",
    "MONOHYDRATE",
    "NAME",
    "SODIUM",
}

DOSAGE_PATTERN = re.compile(r"\b(\d+(?:\.\d+)?)\s*(MCG|MG|GM|G|ML|IU)\b", re.IGNORECASE)
ODOO_PLACEHOLDER_SHA256 = "4e5ac0ca1d07f42ec2b4a85170601a9e4d6188d39cc06db851eb06a5f5880579"


@dataclass(frozen=True)
class ImageCandidate:
    name: str
    image_url: str
    source: str
    ingredients: frozenset[str] = frozenset()


def normalize_ascii(value: str) -> str:
    value = unicodedata.normalize("NFKD", value or "")
    value = value.encode("ascii", "ignore").decode("ascii")
    value = value.upper().replace("&", " AND ")
    return re.sub(r"[^A-Z0-9.]+", " ", value).strip()


def name_tokens(value: str) -> tuple[str, ...]:
    tokens = []
    for token in normalize_ascii(value).replace(".", " ").split():
        token = TOKEN_ALIASES.get(token, token)
        if token not in NOISE_TOKENS:
            tokens.append(token)
    return tuple(tokens)


def ingredient_tokens(value: str) -> frozenset[str]:
    tokens = set()
    for token in name_tokens(value):
        if token.isdigit() or token in INGREDIENT_NOISE or token in {"MG", "GM", "G", "ML", "MCG", "IU"}:
            continue
        tokens.add(token)
    return frozenset(tokens)


def brand_key(value: str) -> str:
    return next((token for token in name_tokens(value) if token.isalpha()), "")


def dosage_pairs(value: str) -> frozenset[tuple[str, str]]:
    normalized = normalize_ascii(value)
    return frozenset((amount.rstrip("0").rstrip("."), unit.upper()) for amount, unit in DOSAGE_PATTERN.findall(normalized))


def match_score(source_name: str, candidate_name: str) -> float:
    source = name_tokens(source_name)
    candidate = name_tokens(candidate_name)
    if not source or not candidate or source[0] != candidate[0]:
        return 0.0

    source_dosages = dosage_pairs(source_name)
    candidate_dosages = dosage_pairs(candidate_name)
    if source_dosages and candidate_dosages and not (source_dosages & candidate_dosages):
        return 0.0

    source_set = set(source)
    candidate_set = set(candidate)
    shared = source_set & candidate_set
    containment = min(len(shared) / len(source_set), len(shared) / len(candidate_set))
    jaccard = len(shared) / len(source_set | candidate_set)
    sequence = SequenceMatcher(None, " ".join(source), " ".join(candidate)).ratio()
    return (containment * 0.45) + (jaccard * 0.25) + (sequence * 0.30)


def build_dawatech_index(sitemap_path: Path) -> dict[str, list[ImageCandidate]]:
    root = ET.parse(sitemap_path).getroot()
    index: dict[str, list[ImageCandidate]] = defaultdict(list)

    for element in root.iter():
        if not element.tag.endswith("loc") or not element.text:
            continue

        url = element.text.strip()
        path = urllib.parse.urlparse(url).path
        if not path.startswith("/shop/") or "/shop/category/" in path:
            continue

        slug = path.removeprefix("/shop/")
        parts = slug.split("-")
        if len(parts) < 3 or not parts[-1].isdigit():
            continue

        template_id = parts[-1]
        name_parts = parts[1:-1] if parts[0].isdigit() else parts[:-1]
        if name_parts and name_parts[-1].isdigit() and len(name_parts[-1]) >= 5:
            name_parts.pop()

        name = " ".join(name_parts)
        key = brand_key(name)
        if key:
            image_url = f"https://demov2.egypt.dawatech.com/web/image/product.template/{template_id}/image_1920"
            index[key].append(ImageCandidate(name=name, image_url=image_url, source="dawatech"))

    return index


def build_shopify_index(sitemap_paths: list[Path]) -> dict[str, list[ImageCandidate]]:
    index: dict[str, list[ImageCandidate]] = defaultdict(list)
    for sitemap_path in sitemap_paths:
        root = ET.parse(sitemap_path).getroot()
        for url_element in root:
            title = next((element.text for element in url_element.iter() if element.tag.endswith("title")), None)
            image_url = next(
                (
                    element.text
                    for element in url_element.iter()
                    if element.tag.endswith("loc") and "cdn.shopify.com" in (element.text or "")
                ),
                None,
            )
            if not title or not image_url:
                continue

            key = brand_key(title)
            if key:
                domain = urllib.parse.urlparse(image_url).netloc
                index[key].append(
                    ImageCandidate(name=title, image_url=image_url, source=f"shopify:{domain}")
                )

    return index


def build_netmeds_index(csv_path: Path | None) -> dict[str, list[ImageCandidate]]:
    index: dict[str, list[ImageCandidate]] = defaultdict(list)
    if csv_path is None:
        return index

    with csv_path.open("r", encoding="utf-8", newline="") as source:
        for row in csv.DictReader(source):
            name = (row.get("med_name") or "").strip()
            image_url = (row.get("img_urls") or "").split(",", 1)[0].strip()
            key = brand_key(name)
            if not key or not image_url.startswith("http") or "/formulation_based/" in image_url:
                continue

            index[key].append(
                ImageCandidate(
                    name=name,
                    image_url=image_url,
                    source="netmeds",
                    ingredients=ingredient_tokens(row.get("generic_name") or ""),
                )
            )

    return index


def best_image(
    name: str,
    scientific_name: str,
    egypt_image_index: dict[str, list[ImageCandidate]],
    netmeds_index: dict[str, list[ImageCandidate]],
) -> tuple[str, str]:
    key = brand_key(name)
    if not key:
        return "", ""

    scored = sorted(
        ((match_score(name, candidate.name), candidate) for candidate in egypt_image_index.get(key, [])),
        key=lambda item: item[0],
        reverse=True,
    )
    if scored and scored[0][0] >= 0.82:
        same_product_tie = len(scored) > 1 and name_tokens(scored[0][1].name) == name_tokens(scored[1][1].name)
        is_unambiguous = len(scored) == 1 or same_product_tie or scored[0][0] - scored[1][0] >= 0.025
        if is_unambiguous:
            return scored[0][1].image_url, scored[0][1].source

    source_ingredients = ingredient_tokens(scientific_name)
    scored = []
    for candidate in netmeds_index.get(key, []):
        score = match_score(name, candidate.name)
        if score < 0.80:
            continue

        if source_ingredients and candidate.ingredients:
            ingredient_overlap = len(source_ingredients & candidate.ingredients) / len(source_ingredients | candidate.ingredients)
            if ingredient_overlap < 0.50:
                continue

        scored.append((score, candidate))

    scored.sort(key=lambda item: item[0], reverse=True)
    if scored and scored[0][0] >= 0.80 and (len(scored) == 1 or scored[0][0] - scored[1][0] >= 0.025):
        return scored[0][1].image_url, scored[0][1].source

    return "", ""


def clean(value: str | None, max_length: int) -> str:
    value = re.sub(r"[\t\r\n]+", " ", (value or "").strip())
    return value[:max_length]


def load_products(
    egypt_csv: Path,
    egypt_image_index: dict[str, list[ImageCandidate]],
    netmeds_index: dict[str, list[ImageCandidate]],
) -> tuple[list[dict[str, str]], dict[str, int]]:
    products = []
    stats = defaultdict(int)
    seen = set()

    with egypt_csv.open("r", encoding="utf-8-sig", newline="") as source:
        for row in csv.DictReader(source):
            stats["source_rows"] += 1
            category = re.sub(r"\s+", " ", clean(row.get("drug_class"), 255)).upper()
            if not category or category == "." or NON_MEDICINE_CLASS.search(category):
                stats["filtered_non_medicine"] += 1
                continue

            name = clean(row.get("commercial_name_en"), 500)
            price = clean(row.get("price_egp"), 32)
            company = clean(row.get("manufacturer"), 500)
            if not name or not price or not company:
                stats["filtered_incomplete"] += 1
                continue
            if UNAVAILABLE_NAME.search(name):
                stats["filtered_unavailable"] += 1
                continue
            if NON_MEDICINE_NAME.search(name):
                stats["filtered_non_medicine_name"] += 1
                continue
            if NON_MEDICINE_MANUFACTURER.search(company):
                stats["filtered_non_medicine_manufacturer"] += 1
                continue

            scientific_name = clean(row.get("scientific_name"), 1000)
            if not scientific_name:
                stats["filtered_missing_scientific_name"] += 1
                continue

            duplicate_key = (normalize_ascii(name), price)
            if duplicate_key in seen:
                stats["filtered_duplicate"] += 1
                continue
            seen.add(duplicate_key)

            image_url, image_source = best_image(name, scientific_name, egypt_image_index, netmeds_index)
            if image_source:
                stats[f"images_{image_source}"] += 1

            products.append(
                {
                    "name": name,
                    "arabicName": clean(row.get("commercial_name_ar"), 500),
                    "scientificName": scientific_name,
                    "price": price,
                    "imageUrl": clean(image_url, 1000),
                    "categoryName": category,
                    "company": company,
                    "route": clean(row.get("route"), 100),
                }
            )

    stats["eligible_rows"] = len(products)
    return products, stats


def verify_dawatech_images(products: list[dict[str, str]], stats: dict[str, int]) -> None:
    targets = [product for product in products if "demov2.egypt.dawatech.com" in product["imageUrl"]]

    def inspect(product: dict[str, str]) -> tuple[dict[str, str], str]:
        request = urllib.request.Request(
            product["imageUrl"],
            headers={"User-Agent": "DawaNow product dataset validator"},
        )
        for _ in range(3):
            try:
                with urllib.request.urlopen(request, timeout=20) as response:
                    body = response.read()
                if hashlib.sha256(body).hexdigest() == ODOO_PLACEHOLDER_SHA256:
                    return product, "placeholder"
                if not body:
                    return product, "unreachable"
                return product, "valid"
            except Exception:
                continue
        return product, "unreachable"

    with ThreadPoolExecutor(max_workers=12) as executor:
        futures = [executor.submit(inspect, product) for product in targets]
        for future in as_completed(futures):
            product, result = future.result()
            if result == "valid":
                stats["verified_dawatech_images"] += 1
                continue

            product["imageUrl"] = ""
            stats[f"removed_dawatech_{result}"] += 1


def write_tsv(products: list[dict[str, str]], output: Path, limit: int) -> int:
    products.sort(key=lambda product: (not bool(product["imageUrl"]), product["name"]))
    selected = products[:limit] if limit > 0 else products
    output.parent.mkdir(parents=True, exist_ok=True)

    with output.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(destination, fieldnames=list(selected[0]), delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(selected)

    return len(selected)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the DawaNow Egyptian product seed dataset.")
    parser.add_argument("--egypt-csv", required=True, type=Path)
    parser.add_argument("--dawatech-sitemap", required=True, type=Path)
    parser.add_argument("--shopify-sitemap", action="append", default=[], type=Path)
    parser.add_argument("--netmeds-csv", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--limit", type=int, default=10_000)
    parser.add_argument("--verify-dawatech-images", action="store_true")
    args = parser.parse_args()

    dawatech_index = build_dawatech_index(args.dawatech_sitemap)
    shopify_index = build_shopify_index(args.shopify_sitemap)
    egypt_image_index: dict[str, list[ImageCandidate]] = defaultdict(list)
    for source_index in (shopify_index, dawatech_index):
        for key, candidates in source_index.items():
            egypt_image_index[key].extend(candidates)
    netmeds_index = build_netmeds_index(args.netmeds_csv)
    products, stats = load_products(args.egypt_csv, egypt_image_index, netmeds_index)
    if args.verify_dawatech_images:
        verify_dawatech_images(products, stats)
    written = write_tsv(products, args.output, args.limit)

    print(f"Dawatech image candidates: {sum(map(len, dawatech_index.values()))}")
    print(f"Shopify image candidates: {sum(map(len, shopify_index.values()))}")
    print(f"Netmeds image candidates: {sum(map(len, netmeds_index.values()))}")
    for key in sorted(stats):
        print(f"{key}: {stats[key]}")
    print(f"written_rows: {written}")
    print(f"written_with_images: {sum(bool(product['imageUrl']) for product in products[:written])}")


if __name__ == "__main__":
    main()
