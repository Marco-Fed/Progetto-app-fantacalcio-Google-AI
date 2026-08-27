import csv, re, unicodedata, json

def normalize_adv(text):
    if not text: return ''
    replacements = {
        'ð': 'd', 'Ð': 'D',
        'đ': 'd', 'Đ': 'D',
        'ø': 'o', 'Ø': 'O',
        'æ': 'ae', 'Æ': 'AE',
        'œ': 'oe', 'Œ': 'OE',
        'ß': 'ss',
        'ł': 'l', 'Ł': 'L',
        '’': '', '\'': '', '`': '', '-': ' '
    }
    t = text
    for k, v in replacements.items():
        t = t.replace(k, v)
    nfkd = unicodedata.normalize('NFKD', t)
    clean = ''.join([c for c in nfkd if not unicodedata.combining(c)])
    clean = re.sub(r'[^a-zA-Z0-9\s]', ' ', clean).lower()
    return re.sub(r'\s+', ' ', clean).strip()

def role_matches_pos(role_str, pos_str):
    if not role_str or not pos_str: return True
    r = role_str.upper().strip()
    p = pos_str.upper().strip()
    if r == 'P': return 'GK' in p
    if r == 'D': return 'DF' in p or 'MF' in p
    if r == 'C': return 'MF' in p or 'DF' in p or 'FW' in p
    if r == 'A': return 'FW' in p or 'MF' in p
    return True

with open('data_kaggle_2024-2025/players_data-2024_2025.csv', encoding='utf-8') as f:
    k24 = list(csv.DictReader(f))
with open('data_kaggle_2025-2026/players_data-2025_2026.csv', encoding='utf-8') as f:
    k25 = list(csv.DictReader(f))

for r in k24:
    r['norm'] = normalize_adv(r['Player'])
    r['norm_tokens'] = r['norm'].split()
    r['norm_team'] = normalize_adv(r['Squad'])
for r in k25:
    r['norm'] = normalize_adv(r['Player'])
    r['norm_tokens'] = r['norm'].split()
    r['norm_team'] = normalize_adv(r['Squad'])

def match_player_smart(raw_name, raw_team, raw_role, dataset):
    cleaned = raw_name.replace('.', ' ').strip()
    norm = normalize_adv(cleaned)
    tokens = norm.split()
    if not tokens: return None
    norm_team = normalize_adv(raw_team)

    # 1. Exact match on normalized name
    exact_candidates = [r for r in dataset if r['norm'] == norm and role_matches_pos(raw_role, r['Pos'])]
    if len(exact_candidates) == 1:
        return exact_candidates[0]
    elif len(exact_candidates) > 1:
        team_m = [r for r in exact_candidates if norm_team in r['norm_team'] or r['norm_team'] in norm_team]
        return team_m[0] if team_m else exact_candidates[0]

    # 2. Inverted name match (e.g. 'Thuram Marcus' vs 'Marcus Thuram')
    if len(tokens) >= 2 and all(len(t) > 2 for t in tokens):
        inv = tokens[-1] + ' ' + ' '.join(tokens[:-1])
        inv_matches = [r for r in dataset if r['norm'] == inv and role_matches_pos(raw_role, r['Pos'])]
        if len(inv_matches) == 1:
            return inv_matches[0]
        elif len(inv_matches) > 1:
            team_m = [r for r in inv_matches if norm_team in r['norm_team'] or r['norm_team'] in norm_team]
            return team_m[0] if team_m else inv_matches[0]

    # Check for abbreviation (initial or short prefix like 'Lo', 'Lu', 'Jo', 'L', 'M', 'K')
    var_abbr = None
    var_surname_tokens = []
    
    if len(tokens) >= 2:
        if len(tokens[-1]) <= 2: # 'martinez l', 'pellegrini lo', 'esposito se', 'esposito p'
            var_abbr = tokens[-1]
            var_surname_tokens = tokens[:-1]
        elif len(tokens[0]) <= 2: # 'l martinez'
            var_abbr = tokens[0]
            var_surname_tokens = tokens[1:]

    # 3. If abbreviation is present, STRICTLY require surname AND initial match
    if var_abbr and var_surname_tokens:
        surname_str = ' '.join(var_surname_tokens)
        candidates = []
        for r in dataset:
            has_surname = all(st in r['norm_tokens'] for st in var_surname_tokens) or surname_str in r['norm']
            if has_surname and role_matches_pos(raw_role, r['Pos']):
                first_name_tokens = [t for t in r['norm_tokens'] if t not in var_surname_tokens]
                if any(t.startswith(var_abbr) for t in first_name_tokens):
                    candidates.append(r)
        
        if len(candidates) == 1:
            return candidates[0]
        elif len(candidates) > 1:
            team_m = [r for r in candidates if norm_team and (norm_team in r['norm_team'] or r['norm_team'] in norm_team)]
            if team_m: return team_m[0]
            return candidates[0]
            
        # Strict: Do NOT fall back to general surname if initial was explicitly specified!
        return None

    # 4. Multi-token full surname check (e.g. 'Milinkovic-Savic', 'Di Francesco', 'De Ketelaere')
    if len(tokens) >= 2:
        cand = [r for r in dataset if all(st in r['norm_tokens'] for st in tokens) and role_matches_pos(raw_role, r['Pos'])]
        if len(cand) == 1:
            return cand[0]
        elif len(cand) > 1:
            team_m = [r for r in cand if norm_team and (norm_team in r['norm_team'] or r['norm_team'] in norm_team)]
            if team_m: return team_m[0]

    # 5. Single surname match with role + team check
    main_surname = tokens[0] if len(tokens[0]) > 3 else tokens[-1]
    if len(main_surname) >= 3:
        candidates_with_team = [r for r in dataset if (main_surname in r['norm_tokens'] or main_surname in r['norm']) 
                                and norm_team and (norm_team in r['norm_team'] or r['norm_team'] in norm_team)
                                and role_matches_pos(raw_role, r['Pos'])]
        if len(candidates_with_team) == 1:
            return candidates_with_team[0]
        elif len(candidates_with_team) > 1:
            return candidates_with_team[0]
            
        if len(tokens) == 1:
            candidates_unique = [r for r in dataset if (main_surname == r['norm'] or main_surname in r['norm_tokens']) and role_matches_pos(raw_role, r['Pos'])]
            if len(candidates_unique) == 1:
                return candidates_unique[0]

    return None

with open('app/src/main/java/com/example/data/model/PreloadedPlayersData.kt', encoding='utf-8') as f:
    content = f.read()

players = re.findall(r'name\s*=\s*\"([^\"]+)\",\s*team\s*=\s*\"([^\"]+)\",\s*role\s*=\s*Role\.([PDCA])', content)

matched_count = 0
for name, team, role in players:
    m24 = match_player_smart(name, team, role, k24)
    m25 = match_player_smart(name, team, role, k25)
    if m24 or m25:
        matched_count += 1

print(f"Total Matched: {matched_count}/{len(players)} ({matched_count/len(players)*100:.1f}%)")

test_list = [
    ('Martinez L.', 'Inter', 'A'),
    ('Martinez Jo.', 'Inter', 'P'),
    ('Gudmundsson A.', 'Fiorentina', 'A'),
    ('Pellegrini Lo.', 'Roma', 'C'),
    ('Pellegrini Lu.', 'Lazio', 'D'),
    ('Thuram M.', 'Inter', 'A'),
    ('Thuram K.', 'Juventus', 'C'),
    ('Esposito Se.', 'Empoli', 'A'),
    ('Esposito P.', 'Spezia', 'A'),
    ('Gonzalez N.', 'Juventus', 'A'),
    ('Zapata D.', 'Torino', 'A'),
    ('Milinkovic-Savic V.', 'Torino', 'P'),
    ('Vicario', 'Juventus', 'P'),
    ('Provedel', 'Inter', 'P')
]
for name, team, role in test_list:
    m24 = match_player_smart(name, team, role, k24)
    m25 = match_player_smart(name, team, role, k25)
    n24 = f"{m24['Player']} ({m24['Squad']})" if m24 else "None"
    n25 = f"{m25['Player']} ({m25['Squad']})" if m25 else "None"
    print(f"{name} ({team}, {role}) -> 24: {n24} | 25: {n25}")
