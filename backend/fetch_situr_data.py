"""
Fetch and process restaurant/food data from SITUR Open Data (Romanian Government).
Downloads the official Excel file with all classified food establishments in Romania.
Processes the data into a structured format and generates chatbot intents.
"""

import os
import json
import random
import requests
import sys

# Try to import openpyxl, install if not available
try:
    import openpyxl
except ImportError:
    print("📦 Installing openpyxl...")
    os.system(f"{sys.executable} -m pip install openpyxl")
    import openpyxl

# ============================================================
# 1. Download and parse the SITUR Excel data
# ============================================================

EXCEL_URL = "https://se.situr.gov.ro/OpenData/ExportToExcel?type=listaAlimentatie"
EXCEL_FILE = "data/situr_alimentatie.xlsx"
JSON_FILE = "data/situr_places.json"

def download_excel():
    """Download the SITUR Excel file."""
    print(f"📥 Downloading SITUR data from: {EXCEL_URL}")
    os.makedirs("data", exist_ok=True)
    
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
    }
    
    response = requests.get(EXCEL_URL, headers=headers, timeout=120)
    
    if response.status_code == 200:
        with open(EXCEL_FILE, 'wb') as f:
            f.write(response.content)
        print(f"   ✅ Downloaded: {os.path.getsize(EXCEL_FILE) / 1024:.0f} KB")
        return True
    else:
        print(f"   ❌ Download failed: HTTP {response.status_code}")
        return False


def parse_excel():
    """Parse the downloaded Excel file into structured data."""
    print(f"\n📊 Parsing Excel file: {EXCEL_FILE}")
    
    wb = openpyxl.load_workbook(EXCEL_FILE, read_only=True)
    ws = wb.active
    
    rows = list(ws.iter_rows(values_only=True))
    
    # Find header row
    header_row = None
    for i, row in enumerate(rows):
        row_str = str(row).lower()
        if 'denumire' in row_str or 'tip' in row_str or 'judet' in row_str or 'categori' in row_str:
            header_row = i
            break
    
    if header_row is None:
        # Try first row as header
        header_row = 0
    
    headers = [str(h).strip() if h else f"col_{i}" for i, h in enumerate(rows[header_row])]
    print(f"   Headers found at row {header_row}: {headers[:8]}...")
    
    places = []
    seen = set()
    
    for row in rows[header_row + 1:]:
        if not row or all(cell is None for cell in row):
            continue
        
        entry = {}
        for i, val in enumerate(row):
            if i < len(headers):
                entry[headers[i]] = str(val).strip() if val else ""
        
        # Try to extract key fields with various possible header names
        name = ""
        place_type = ""
        category = ""
        county = ""
        locality = ""
        address = ""
        seats = ""
        
        for key, val in entry.items():
            key_lower = key.lower()
            if 'denumire' in key_lower and 'operator' not in key_lower:
                name = val
            elif 'tip' in key_lower and ('structur' in key_lower or 'unitate' in key_lower):
                place_type = val
            elif 'categori' in key_lower:
                category = val
            elif 'judet' in key_lower or 'jud' in key_lower:
                county = val
            elif 'localitat' in key_lower or 'oras' in key_lower or 'comuna' in key_lower:
                locality = val
            elif 'adres' in key_lower or 'strad' in key_lower:
                address = val
            elif 'locuri' in key_lower or 'nr.' in key_lower and 'locuri' in key_lower:
                seats = val
        
        # Only keep entries with a name
        if name and name != "None" and name not in seen:
            seen.add(name)
            place = {
                "name": name,
                "type": place_type if place_type != "None" else "",
                "category": category if category != "None" else "",
                "county": county if county != "None" else "",
                "locality": locality if locality != "None" else "",
                "address": address if address != "None" else "",
                "seats": seats if seats != "None" else ""
            }
            places.append(place)
    
    wb.close()
    print(f"   ✅ Parsed {len(places)} unique establishments")
    return places


def organize_by_county_and_type(places):
    """Organize places by county/locality and type for chatbot knowledge."""
    # Group by county
    by_county = {}
    for place in places:
        county = place["county"].upper().strip()
        if not county or county == "NONE":
            continue
        if county not in by_county:
            by_county[county] = []
        by_county[county].append(place)
    
    # Group by type
    by_type = {}
    for place in places:
        ptype = place["type"].upper().strip()
        if not ptype or ptype == "NONE":
            continue
        if ptype not in by_type:
            by_type[ptype] = []
        by_type[ptype].append(place)
    
    # Group by locality
    by_locality = {}
    for place in places:
        locality = place["locality"].strip()
        if not locality or locality == "None":
            continue
        if locality not in by_locality:
            by_locality[locality] = []
        by_locality[locality].append(place)
    
    return by_county, by_type, by_locality


def generate_intents(places, by_county, by_type, by_locality):
    """Generate chatbot intents from the SITUR data."""
    intents = []
    
    # ============================================================
    # 1. General food places intent (nationwide)
    # ============================================================
    
    total = len(places)
    counties = sorted(by_county.keys())
    types = sorted(by_type.keys())
    
    type_counts = {t: len(ps) for t, ps in by_type.items()}
    top_types = sorted(type_counts.items(), key=lambda x: x[1], reverse=True)[:6]
    top_types_str = ", ".join([f"{t} ({c})" for t, c in top_types])
    
    intents.append({
        "tag": "situr_general_info",
        "patterns": [
            "Ce restaurante sunt in Romania",
            "Cate restaurante clasificate sunt",
            "Locuri de mancare in Romania",
            "Restaurante clasificate Romania",
            "What restaurants are in Romania",
            "How many restaurants in Romania",
            "Classified food places Romania",
            "Food establishments Romania",
            "Spune-mi despre restaurantele din Romania",
            "Ce locuri de mancat exista in tara",
            "Restaurante din tara",
            "Localuri clasificate",
            "Unde pot manca in Romania",
            "Tell me about Romanian restaurants",
            "Eating places in Romania"
        ],
        "responses": [
            f"România are {total} structuri de alimentație clasificate oficial, în toate județele țării! Cele mai populare tipuri sunt: {top_types_str}. 🍽️ Spune-mi județul sau orașul și îți dau detalii!",
            f"Am date oficiale de la Ministerul Turismului cu {total} restaurante, baruri, cofetării și alte localuri clasificate din toată România! Întreabă-mă despre orice localitate! 🇷🇴",
            f"According to official data, Romania has {total} classified food establishments across all counties! The most common types are: {top_types_str}. Ask me about any city! 🍴",
            f"În baza mea de date am {total} locuri de alimentație clasificate oficial, din {len(counties)} județe! Ce localitate te interesează? 🗺️",
            f"Romania boasts {total} officially classified dining establishments! From restaurants to bars, cafes to pastry shops. Which county or city are you interested in? 🌟"
        ],
        "suggestions": ["🍽️ Restaurante București", "🏔️ Restaurante Brașov", "☕ Cafenele Cluj", "🍕 Ce tipuri există?"]
    })
    
    # ============================================================
    # 2. Type-based intents
    # ============================================================
    
    type_map = {
        "RESTAURANT CLASIC": {
            "tag": "situr_restaurant_clasic",
            "patterns_ro": [
                "Restaurante clasice", "Restaurant clasic", "Unde gasesc un restaurant clasic",
                "Restaurante traditionale", "Restaurant traditional", "Vreau un restaurant clasic",
                "Unde pot manca la restaurant clasic"
            ],
            "patterns_en": [
                "Classic restaurant", "Traditional restaurant", "Where is a classic restaurant",
                "Fine dining restaurant", "I want a classic restaurant"
            ]
        },
        "BAR DE ZI": {
            "tag": "situr_bar_zi",
            "patterns_ro": [
                "Bar de zi", "Baruri de zi", "Unde gasesc un bar de zi",
                "Vreau la un bar", "Bar in zona", "Un bar fain"
            ],
            "patterns_en": [
                "Day bar", "Bars", "Where to find a bar", "I want a bar", "Bar nearby"
            ]
        },
        "COFETĂRIE": {
            "tag": "situr_cofetarie",
            "patterns_ro": [
                "Cofetarie", "Cofetarii", "Unde gasesc o cofetarie",
                "Vreau ceva dulce", "Patiserie", "Prajituri", "Unde mananc prajituri",
                "Cofetarie buna"
            ],
            "patterns_en": [
                "Pastry shop", "Confectionery", "Where to find pastries",
                "Sweet shop", "I want pastries", "Cake shop"
            ]
        },
        "PIZZERIE": {
            "tag": "situr_pizzerie",
            "patterns_ro": [
                "Pizzerie", "Pizza", "Unde gasesc pizza", "Vreau pizza",
                "Pizzerii", "Cel mai bun pizza", "Pizza buna"
            ],
            "patterns_en": [
                "Pizza place", "Pizzeria", "Where to find pizza", "I want pizza",
                "Best pizza", "Pizza restaurant"
            ]
        },
        "FAST-FOOD": {
            "tag": "situr_fast_food",
            "patterns_ro": [
                "Fast food", "Mancare rapida", "Unde gasesc fast food",
                "Vreau ceva rapid", "Fast food in zona", "Mancare pe apucate"
            ],
            "patterns_en": [
                "Fast food", "Quick food", "Where to find fast food",
                "I want something quick", "Fast food nearby"
            ]
        },
        "RESTAURANT FAMILIAL/PENSIUNE": {
            "tag": "situr_restaurant_pensiune",
            "patterns_ro": [
                "Restaurant de pensiune", "Restaurant familial", "Mancare la pensiune",
                "Pensiune cu restaurant", "Mancare la sat", "Restaurant rural"
            ],
            "patterns_en": [
                "Guesthouse restaurant", "Family restaurant at guesthouse",
                "Rural restaurant", "Countryside dining"
            ]
        }
    }
    
    for type_name, type_info in type_map.items():
        if type_name in by_type:
            type_places = by_type[type_name]
            count = len(type_places)
            
            # Get sample places from different counties
            samples_by_county = {}
            for p in type_places:
                c = p["county"]
                if c and c not in samples_by_county:
                    samples_by_county[c] = p
                if len(samples_by_county) >= 5:
                    break
            
            sample_list = list(samples_by_county.values())
            sample_str_ro = "; ".join([
                f"'{p['name']}' ({p['locality'] or p['county']})"
                for p in sample_list[:4]
            ])
            sample_str_en = sample_str_ro  # Same format works
            
            county_list = list(set(p["county"] for p in type_places if p["county"]))[:8]
            counties_str = ", ".join(sorted(county_list))
            
            intents.append({
                "tag": type_info["tag"],
                "patterns": type_info["patterns_ro"] + type_info["patterns_en"],
                "responses": [
                    f"În România sunt {count} {type_name.lower()} clasificate oficial! Le găsești în județele: {counties_str} și altele. Exemple: {sample_str_ro}. 🍽️ Spune-mi localitatea ta!",
                    f"Am {count} {type_name.lower()} în baza de date! Unele dintre ele: {sample_str_ro}. Vrei să caut în județul sau orașul tău? 📍",
                    f"Romania has {count} classified {type_name.lower()} establishments! Some examples: {sample_str_en}. Tell me your city for recommendations! 🏙️",
                    f"Sunt {count} unități de tip {type_name.lower()} clasificate. Sunt în {len(county_list)}+ județe, inclusiv {counties_str}. Unde te afli?"
                ],
                "suggestions": [f"📍 {c}" for c in sorted(county_list)[:4]]
            })
    
    # ============================================================
    # 3. County-based intents (top 15 counties by number of places)
    # ============================================================
    
    top_counties = sorted(by_county.items(), key=lambda x: len(x[1]), reverse=True)[:20]
    
    county_nice_names = {
        "BUCURESTI": "București", "BRASOV": "Brașov", "CLUJ": "Cluj",
        "CONSTANTA": "Constanța", "TIMIS": "Timiș", "SIBIU": "Sibiu",
        "MARAMURES": "Maramureș", "PRAHOVA": "Prahova", "MURES": "Mureș",
        "SUCEAVA": "Suceava", "IASI": "Iași", "BIHOR": "Bihor",
        "ALBA": "Alba", "HUNEDOARA": "Hunedoara", "ARGES": "Argeș",
        "VRANCEA": "Vrancea", "HARGHITA": "Harghita", "COVASNA": "Covasna",
        "NEAMT": "Neamț", "VALCEA": "Vâlcea", "GORJ": "Gorj",
        "DAMBOVITA": "Dâmbovița", "TULCEA": "Tulcea", "CARAS-SEVERIN": "Caraș-Severin",
        "DOLJ": "Dolj", "GALATI": "Galați", "BACAU": "Bacău",
        "ARAD": "Arad", "BUZAU": "Buzău", "OLT": "Olt",
        "SATU MARE": "Satu Mare", "SALAJ": "Sălaj", "BISTRITA-NASAUD": "Bistrița-Năsăud",
        "BOTOSANI": "Botoșani", "CALARASI": "Călărași", "GIURGIU": "Giurgiu",
        "IALOMITA": "Ialomița", "ILFOV": "Ilfov", "MEHEDINTI": "Mehedinți",
        "TELEORMAN": "Teleorman", "VASLUI": "Vaslui"
    }
    
    for county, county_places in top_counties:
        nice_name = county_nice_names.get(county, county.title())
        count = len(county_places)
        
        # Get type distribution for this county
        county_types = {}
        for p in county_places:
            t = p["type"]
            if t:
                county_types[t] = county_types.get(t, 0) + 1
        top_county_types = sorted(county_types.items(), key=lambda x: x[1], reverse=True)[:4]
        
        # Get sample places
        samples = random.sample(county_places, min(5, len(county_places)))
        sample_names = [f"'{s['name']}'" for s in samples[:4]]
        
        # Get distinct localities
        localities = list(set(p["locality"] for p in county_places if p["locality"] and p["locality"] != "None"))[:6]
        
        tag = f"situr_county_{county.lower().replace(' ', '_').replace('-', '_')}"
        
        patterns_ro = [
            f"Restaurante in {nice_name}",
            f"Unde mananc in {nice_name}",
            f"Localuri in {nice_name}",
            f"Ce restaurante sunt in {nice_name}",
            f"Mancare in {nice_name}",
            f"Recomandari {nice_name}",
            f"Locuri de mancat {nice_name}",
        ]
        
        patterns_en = [
            f"Restaurants in {nice_name}",
            f"Where to eat in {nice_name}",
            f"Food places in {nice_name}",
            f"What restaurants are in {nice_name}",
            f"Dining in {nice_name}",
        ]
        
        types_str = ", ".join([f"{t.lower()} ({c})" for t, c in top_county_types])
        locs_str = ", ".join(localities[:5]) if localities else nice_name
        
        responses = [
            f"În {nice_name} sunt {count} locuri de alimentație clasificate! Tipuri: {types_str}. Câteva exemple: {', '.join(sample_names)}. 🍽️",
            f"Județul {nice_name} are {count} structuri clasificate, în localități ca: {locs_str}. Vrei detalii despre o localitate anume? 📍",
            f"{nice_name} county has {count} classified food establishments! Types include: {types_str}. Examples: {', '.join(sample_names)}. 🌟",
            f"Am {count} restaurante/baruri/cafenele clasificate în {nice_name}! Întreabă-mă despre o localitate specifică din {nice_name}! 🗺️"
        ]
        
        suggestions = [f"📍 {loc}" for loc in localities[:4]] if localities else []
        
        intents.append({
            "tag": tag,
            "patterns": patterns_ro + patterns_en,
            "responses": responses,
            "suggestions": suggestions
        })
    
    # ============================================================
    # 4. Major city intents (top localities by place count)
    # ============================================================
    
    # Only generate for localities with 10+ places
    big_localities = {k: v for k, v in by_locality.items() if len(v) >= 10 and k != "None"}
    top_localities = sorted(big_localities.items(), key=lambda x: len(x[1]), reverse=True)[:25]
    
    for locality, loc_places in top_localities:
        count = len(loc_places)
        
        # Type distribution
        loc_types = {}
        for p in loc_places:
            t = p["type"]
            if t:
                loc_types[t] = loc_types.get(t, 0) + 1
        
        top_loc_types = sorted(loc_types.items(), key=lambda x: x[1], reverse=True)[:4]
        
        # Samples
        samples = random.sample(loc_places, min(5, len(loc_places)))
        sample_names = [f"'{s['name']}' ({s['type'].lower()})" for s in samples[:4] if s['type']]
        
        county = loc_places[0]["county"] if loc_places else ""
        
        # Sanitize tag
        tag_name = locality.lower().replace(' ', '_').replace('-', '_').replace('.', '')
        tag_name = ''.join(c for c in tag_name if c.isalnum() or c == '_')
        tag = f"situr_loc_{tag_name}"
        
        patterns_ro = [
            f"Restaurante in {locality}",
            f"Unde mananc in {locality}",
            f"Ce locuri de mancat sunt in {locality}",
            f"Recomandari mancare {locality}",
            f"Baruri in {locality}",
            f"Unde iesim in {locality}",
            f"Localuri {locality}"
        ]
        
        patterns_en = [
            f"Restaurants in {locality}",
            f"Where to eat in {locality}",
            f"Food in {locality}",
            f"Dining in {locality}",
            f"Bars in {locality}"
        ]
        
        types_str = ", ".join([f"{t.lower()} ({c})" for t, c in top_loc_types])
        
        responses = [
            f"În {locality} ({county}) sunt {count} locuri clasificate! Tipuri: {types_str}. Exemple: {', '.join(sample_names)}. 🍽️",
            f"{locality} are {count} unități de alimentație! Cele mai multe sunt de tip: {types_str}. Vrei să afli mai multe? 📍",
            f"{locality} ({county}) has {count} classified food establishments! Types: {types_str}. Some popular ones: {', '.join(sample_names)}. 🌟",
            f"Am găsit {count} locuri de mâncare în {locality}! Exemple: {', '.join(sample_names)}. Cu ce te pot ajuta? 😊"
        ]
        
        intents.append({
            "tag": tag,
            "patterns": patterns_ro + patterns_en,
            "responses": responses
        })
    
    # ============================================================
    # 5. "Recommend in any city" intent
    # ============================================================
    
    intents.append({
        "tag": "situr_recommend_any_city",
        "patterns": [
            "Recomanda-mi un restaurant in orasul meu",
            "Unde mananc in orasul meu",
            "Restaurante in zona mea",
            "Ce restaurante sunt pe langa mine",
            "Vreau sa mananc in alt oras",
            "Restaurante in alte orase",
            "Nu sunt in Bucuresti",
            "Sunt intr-un alt oras",
            "Locations outside Bucharest",
            "Recommend restaurants in another city",
            "I'm not in Bucharest",
            "Restaurants in my city",
            "Restaurante in provincia",
            "Mancare buna in provincie",
            "Ce restaurante sunt la tara",
            "Locuri de mancare pe litoral",
            "Restaurante la mare",
            "Restaurante la munte"
        ],
        "responses": [
            "Pot să te ajut cu restaurante din orice localitate din România! 🇷🇴 Am date oficiale pentru toate cele 41 de județe. Spune-mi orașul sau județul și îți dau recomandări!",
            "Nu doar București — am informații despre restaurante, baruri, cofetării și alte locuri din TOATĂ România! Scrie-mi numele localității tale! 🗺️",
            "I have data for food establishments across ALL of Romania, not just Bucharest! Tell me your city or county, and I'll help! 🌟",
            "Am informații oficiale despre mii de restaurante din toată țara! De la București la Brașov, de la Cluj la Constanța. Unde ești? 📍",
            "Fie că ești la mare, la munte sau în oraș — am date despre localuri din toată România! Spune-mi localitatea! 🏔️🏖️"
        ],
        "suggestions": ["🏙️ București", "🏔️ Brașov", "🌊 Constanța", "🏰 Sibiu", "📍 Alt oraș"]
    })
    
    return intents


def save_data(places, intents):
    """Save processed data and update intents.json."""
    # Save raw places data
    with open(JSON_FILE, 'w', encoding='utf-8') as f:
        json.dump(places, f, ensure_ascii=False, indent=2)
    print(f"\n💾 Saved {len(places)} places to: {JSON_FILE}")
    
    # Load existing intents
    with open('data/intents.json', 'r', encoding='utf-8') as f:
        existing = json.load(f)
    
    # Remove old SITUR intents (if re-running)
    existing_intents = [i for i in existing['intents'] if not i['tag'].startswith('situr_')]
    
    # Add new SITUR intents
    existing_intents.extend(intents)
    existing['intents'] = existing_intents
    
    with open('data/intents.json', 'w', encoding='utf-8') as f:
        json.dump(existing, f, ensure_ascii=False, indent=4)
    
    situr_count = len(intents)
    total_count = len(existing_intents)
    print(f"   ✅ Added {situr_count} SITUR intents (total: {total_count} intents)")
    
    # Count patterns
    total_patterns = sum(len(i['patterns']) for i in existing_intents)
    situr_patterns = sum(len(i['patterns']) for i in intents)
    print(f"   📊 Total patterns: {total_patterns} ({situr_patterns} from SITUR)")


def print_summary(places, by_county, by_type, by_locality):
    """Print a comprehensive summary."""
    print(f"\n{'='*60}")
    print(f"📊 SITUR Data Summary")
    print(f"{'='*60}")
    print(f"   Total establishments: {len(places)}")
    print(f"   Counties: {len(by_county)}")
    print(f"   Localities: {len(by_locality)}")
    print(f"   Types: {len(by_type)}")
    
    print(f"\n   Top 10 counties by count:")
    for county, ps in sorted(by_county.items(), key=lambda x: len(x[1]), reverse=True)[:10]:
        print(f"   {county:20s}: {len(ps):5d} establishments")
    
    print(f"\n   Types breakdown:")
    for t, ps in sorted(by_type.items(), key=lambda x: len(x[1]), reverse=True):
        print(f"   {t:35s}: {len(ps):5d}")
    
    print(f"\n   Top 10 localities by count:")
    for loc, ps in sorted(by_locality.items(), key=lambda x: len(x[1]), reverse=True)[:10]:
        if loc and loc != "None":
            county = ps[0]["county"] if ps else ""
            print(f"   {loc:25s} ({county:15s}): {len(ps):5d}")


# ============================================================
# Main execution
# ============================================================

if __name__ == "__main__":
    print("🏗️  SITUR Open Data Processor")
    print("   Source: Ministry of Tourism - Romania")
    print(f"{'='*60}\n")
    
    # Step 1: Download
    if not os.path.exists(EXCEL_FILE):
        success = download_excel()
        if not success:
            print("\n⚠️  Could not download the Excel file.")
            print("   Trying to parse from HTML data instead...")
            # We'll handle this case by generating from the HTML data we already have
    else:
        print(f"📄 Using cached Excel file: {EXCEL_FILE}")
    
    # Step 2: Parse
    if os.path.exists(EXCEL_FILE):
        places = parse_excel()
    else:
        print("❌ No data file available. Please download manually from:")
        print(f"   {EXCEL_URL}")
        sys.exit(1)
    
    if not places:
        print("❌ No places found in the data file!")
        sys.exit(1)
    
    # Step 3: Organize
    by_county, by_type, by_locality = organize_by_county_and_type(places)
    
    # Step 4: Summary
    print_summary(places, by_county, by_type, by_locality)
    
    # Step 5: Generate intents
    print(f"\n🤖 Generating chatbot intents...")
    new_intents = generate_intents(places, by_county, by_type, by_locality)
    print(f"   Generated {len(new_intents)} new intents")
    
    # Step 6: Save
    save_data(places, new_intents)
    
    print(f"\n{'='*60}")
    print(f"✅ SITUR data processing complete!")
    print(f"   Now run 'python train.py' to retrain the chatbot model.")
    print(f"{'='*60}")
