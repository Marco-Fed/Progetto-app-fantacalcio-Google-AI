import re
import json

# Read current build_preloaded_database.py to get players
with open("build_preloaded_database.py", "r") as f:
    code = f.read()

# Exact Goal.com indisponibili map: name -> (status, notes, return_date)
goal_indisponibili = {
    # Atalanta
    "Scalvini": ("Infortunato", "Rottura legamento crociato anteriore sinistro", "Febbraio 2027"),
    "Scamacca": ("Infortunato", "Rottura legamento crociato anteriore", "Febbraio 2027"),
    "Ahanor": ("Infortunato", "Distrazione di basso grado agli adduttori", "In valutazione"),
    "Kristensen T.": ("Infortunato", "Problema alla caviglia", "In valutazione"),
    "Sulemana I.": ("Infortunato", "Risentimento muscolare", "1-2 settimane"),
    
    # Bologna
    "Ferguson": ("Infortunato", "Lesione legamento crociato e menisco", "Ottobre 2026"),
    "Holm": ("Infortunato", "Risentimento al flessore", "In valutazione"),
    "Casale": ("In dubbio", "Affaticamento muscolare", "In dubbio"),
    
    # Cagliari
    "Mina": ("In dubbio", "Fastidio al polpaccio", "In dubbio"),
    "Prati": ("Infortunato", "Trauma distorsivo alla caviglia", "Metà Ottobre 2026"),
    
    # Como
    "Varane": ("Infortunato", "Problema muscolare al ginocchio", "Da definire"),
    "Baselli": ("In dubbio", "Affaticamento muscolare", "In dubbio"),
    
    # Fiorentina
    "Gudmundsson A.": ("Infortunato", "Risentimento muscolare coscia destra", "Settembre 2026 (3ª giornata)"),
    "Mandragora": ("Infortunato", "Lesione al menisco mediale", "Novembre 2026"),
    "Pongracic": ("Infortunato", "Affaticamento ai flessori", "In valutazione"),
    
    # Genoa
    "Messias": ("Infortunato", "Edema muscolare all'adduttore", "Fine Settembre 2026"),
    "Bani": ("Infortunato", "Distrazione muscolare al gluteo", "In valutazione"),
    
    # Inter
    "Buchanan": ("Infortunato", "Frattura della tibia", "Novembre 2026"),
    "Barella": ("Infortunato", "Distrazione al retto femorale coscia destra", "Inizio Ottobre 2026"),
    
    # Juventus
    "Bremer": ("Infortunato", "Rottura legamento crociato anteriore", "Aprile 2027"),
    "Cabal": ("Infortunato", "Lesione legamento crociato", "Maggio 2027"),
    "Milik": ("Infortunato", "Lesione menisco mediale e artroscopia", "Fine Ottobre 2026"),
    "Douglas Luiz": ("In dubbio", "Affaticamento muscolare", "In dubbio"),
    
    # Lazio
    "Cataldi": ("Infortunato", "Recupero da ernia inguinale bilaterale", "Inizio Ottobre 2026"),
    "Marusic": ("Infortunato", "Problema muscolare alla coscia destra", "In valutazione"),
    "Dele-Bashiru": ("Infortunato", "Problema muscolare alla gamba", "In valutazione"),
    "Lazzari": ("Infortunato", "Lesione muscolare al retto femorale", "Fine Ottobre 2026"),
    "Patric": ("Infortunato", "Problema muscolare", "In valutazione"),
    
    # Lecce
    "Berisha M.": ("Infortunato", "Elongazione del retto femorale", "Metà Ottobre 2026"),
    "Kaba": ("Infortunato", "Recupero post rottura legamento crociato", "Ottobre 2026"),
    
    # Milan
    "Florenzi": ("Infortunato", "Rottura legamento crociato e menisco", "Marzo 2027"),
    "Sportiello": ("Infortunato", "Lesione tendinea alla mano sinistra", "Metà Ottobre 2026"),
    "Bennacer": ("Infortunato", "Lesione severa muscolo gemello mediale", "Gennaio 2027"),
    
    # Monza
    "Pessina": ("Infortunato", "Lussazione rotula ginocchio destro", "Inizio Novembre 2026"),
    "Colombo L.": ("In dubbio", "Problema fisico", "In dubbio"),
    "Ciurria": ("Infortunato", "Recupero post operazione al ginocchio", "Fine Ottobre 2026"),
    "Touré I.": ("Infortunato", "Fastidio articolare", "In valutazione"),
    
    # Napoli
    "Lobotka": ("Infortunato", "Distrazione primo grado semitendinoso", "Fine Ottobre 2026"),
    "Olivera": ("In dubbio", "Affaticamento muscolare", "In dubbio"),
    
    # Parma
    "Circati": ("Infortunato", "Rottura legamento crociato anteriore", "Aprile 2027"),
    "Kowalski": ("Infortunato", "Rottura legamento crociato anteriore", "Aprile 2027"),
    
    # Roma
    "Saelemaekers": ("Infortunato", "Frattura composta malleolo mediale", "Novembre 2026"),
    "El Shaarawy": ("In dubbio", "Lieve stiramento al polpaccio", "In dubbio"),
    
    # Torino
    "Zapata D.": ("Infortunato", "Rottura legamento crociato e menisco", "Maggio 2027"),
    "Schuurs": ("Infortunato", "Rieducazione e nuova artroscopia ginocchio", "Novembre 2026"),
    "Ilic": ("Infortunato", "Lesione tendine bicipite femorale", "Fine Ottobre 2026"),
    
    # Udinese
    "Sanchez": ("Infortunato", "Lesione distrattiva miofasciale gemello mediale", "Fine Ottobre 2026"),
    "Palma": ("Infortunato", "Lesione muscolare all'adduttore lungo", "3-4 settimane"),
    "Zarraga": ("Infortunato", "Risentimento muscolare", "In valutazione"),
    "Chakvetadze": ("Infortunato", "Frattura terzo metatarsale piede destro", "Settembre 2026"),
    "Zanoli": ("Infortunato", "Lesione legamento crociato ginocchio destro", "Ottobre 2026"),
    
    # Venezia
    "Bjarkason": ("Infortunato", "Ernia del disco ed operazione", "Fine Ottobre 2026"),
    "Sverko": ("In dubbio", "Affaticamento muscolare", "In dubbio"),
    
    # Sassuolo
    "Berardi": ("Infortunato", "Fase finale recupero rottura tendine d'Achille", "Novembre 2026")
}

print(f"Total Goal.com infortunati tracked: {len(goal_indisponibili)}")
