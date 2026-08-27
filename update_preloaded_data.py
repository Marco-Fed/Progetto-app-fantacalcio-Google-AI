import re
import json

with open("build_preloaded_database.py", "r") as f:
    orig = f.read()

# Exact dataset from User's text
default_user_injuries = {
    # Atalanta
    "Ahanor": ("Infortunato", "Distrazione agli adduttori", "Rientro fine agosto"),
    "Hien": ("Infortunato", "Operato dopo lesione muscolare", "Rientro inizio ottobre"),
    "Kristensen T.": ("Infortunato", "Problema a una caviglia", "Da valutare"),
    "Kristensen": ("Infortunato", "Problema a una caviglia", "Da valutare"),
    "Sulemana I.": ("Infortunato", "Lesione al collaterale del ginocchio", "Rientro metà ottobre"),
    "Sulemana": ("Infortunato", "Lesione al collaterale del ginocchio", "Rientro metà ottobre"),
    
    # Cagliari
    "Idrissi R.": ("Infortunato", "Lesione del legamento crociato anteriore", "Rientro settembre/ottobre"),
    "Idrissi": ("Infortunato", "Lesione del legamento crociato anteriore", "Rientro settembre/ottobre"),
    
    # Como
    "Addai": ("Infortunato", "Rottura del tendine d'Achille", "Rientro settembre/ottobre"),
    
    # Fiorentina
    "Parisi": ("Infortunato", "Lesione legamento crociato anteriore", "Rientro novembre/dicembre"),
    
    # Genoa
    "Venturino": ("Infortunato", "Operato al tendine rotuleo", "Rientro inizio settembre"),
    
    # Juventus
    "Ekhator": ("Infortunato", "Lesione al bicipite femorale", "Rientro fine agosto"),
    "Gatti": ("Infortunato", "Problema alla caviglia", "Da valutare"),
    "Vicario": ("Infortunato", "Problema muscolare", "Da valutare"),
    "Yildiz": ("Infortunato", "Problema al piede", "Da valutare"),
    
    # Lazio
    "Cataldi": ("Infortunato", "Pubalgia", "Rientro inizio settembre"),
    "Dele-Bashiru": ("Infortunato", "Problema fisico", "Da valutare"),
    "Marusic": ("Infortunato", "Problema muscolare", "Da valutare"),
    
    # Lecce
    "Gallo": ("Infortunato", "Problema allo zigomo", "Da valutare"),
    
    # Milan
    "Gimenez": ("Infortunato", "Distorsione alla caviglia", "Rientro fine agosto/inizio settembre"),
    "Leao": ("Infortunato", "Risentimento muscolare", "Da valutare"),
    
    # Monza
    "Pessina": ("Infortunato", "Lesione alla rotula", "Rientro fine ottobre-inizio novembre"),
    
    # Napoli
    "Buongiorno": ("Infortunato", "Infortunio al menisco", "Rientro novembre"),
    "Marianucci": ("Infortunato", "Lesione di alto grado del collaterale mediale del ginocchio sinistro", "Rientro metà ottobre"),
    "Marin R.": ("Infortunato", "Problema fisico", "Da valutare"),
    "Marin": ("Infortunato", "Problema fisico", "Da valutare"),
    
    # Parma
    "Nicolussi Caviglia": ("Infortunato", "Lesione di medio grado alla coscia", "Rientro metà settembre"),
    
    # Roma
    "Rensch": ("Infortunato", "Affaticamento muscolare", "Da valutare"),
    "Vaz": ("Infortunato", "Lesione al collaterale", "Rientro metà settembre"),
    
    # Sassuolo
    "Berardi": ("Infortunato", "Sovraccarico alla caviglia", "Rientro fine agosto-inizio settembre"),
    "Candé": ("Infortunato", "Rottura del crociato", "Rientro metà settembre"),
    "Koné I.": ("Infortunato", "Frattura di tibia e perone", "Rientro gennaio 2027"),
    "Koné": ("Infortunato", "Frattura di tibia e perone", "Rientro gennaio 2027"),
    "Pinamonti": ("Infortunato", "Problema fisico", "Da valutare"),
    
    # Torino
    "Comuzzo": ("Infortunato", "Infortunio muscolare", "Da valutare"),
    "Israel": ("Infortunato", "Infortunio alla spalla", "Rientro novembre-dicembre"),
    
    # Udinese
    "Kabasele": ("Squalificato", "1 giornata - Salta Monza (2ª)", "Prossima giornata"),
    "Chakvetadze": ("Infortunato", "Frattura al piede", "Rientro fine agosto/inizio settembre"),
    "Gueye": ("Infortunato", "Problema muscolare", "Da valutare"),
    "Zanoli": ("Infortunato", "Recupero dall'infortunio al crociato", "Rientro metà-fine settembre"),
    
    # Venezia
    "Adorante": ("Infortunato", "Operato alla schiena", "Rientro metà-fine ottobre"),
    "Sverko": ("Infortunato", "Infortunio alle anche", "Rientro metà-fine ottobre")
}

# Replace goal_infortuni_serie_a in build_preloaded_database.py
with open("build_preloaded_database.py", "w") as f:
    # Rewrite script to use default_user_injuries
    code = re.sub(r"goal_infortuni_serie_a\s*=\s*\{.*?\n\}", f"goal_infortuni_serie_a = {json.dumps(default_user_injuries, indent=4)}", orig, flags=re.DOTALL)
    f.write(code)

print("Updated build_preloaded_database.py")
