# 1.2.1 Diagrame ale Cazurilor de Utilizare - CityScape

## Actori Principali
- **Utilizator Neautentificat** - vizitator care nu s-a logat
- **Utilizator Autentificat** - utilizator înregistrat și logat
- **Sistem AI** - componenta de inteligență artificială
- **Administrator** - moderator al locațiilor propuse

---

## UC1: Autentificare și Profil

```mermaid
flowchart LR
    subgraph Actors
        U[("👤 Utilizator")]
    end
    
    subgraph "Modul Autentificare"
        UC1["Înregistrare cont"]
        UC2["Autentificare"]
        UC3["Autentificare Google/Facebook"]
        UC4["Configurare preferințe"]
        UC5["Editare profil"]
        UC6["Delogare"]
    end
    
    U --> UC1
    U --> UC2
    U --> UC3
    U --> UC4
    U --> UC5
    U --> UC6
    
    UC1 -.->|include| UC4
    UC2 -.->|extend| UC3
```

---

## UC2: Explorare și Căutare Locații

```mermaid
flowchart LR
    subgraph Actors
        U[("👤 Utilizator")]
        AI[("🤖 Sistem AI")]
    end
    
    subgraph "Modul Explorare"
        UC1["Vizualizare hartă"]
        UC2["Căutare locații"]
        UC3["Filtrare după categorie"]
        UC4["Filtrare după preț"]
        UC5["Vizualizare detalii loc"]
        UC6["Sortare după rating"]
        UC7["Locații în apropiere"]
    end
    
    U --> UC1
    U --> UC2
    U --> UC3
    U --> UC4
    U --> UC5
    U --> UC6
    U --> UC7
    
    AI --> UC7
    
    UC2 -.->|extend| UC3
    UC2 -.->|extend| UC4
    UC7 -.->|include| UC1
```

---

## UC3: Recomandări AI

```mermaid
flowchart LR
    subgraph Actors
        U[("👤 Utilizator")]
        AI[("🤖 Sistem AI")]
    end
    
    subgraph "Modul Recomandări"
        UC1["Recomandări personalizate"]
        UC2["Locații trending"]
        UC3["Compatibilitate loc-utilizator"]
        UC4["Generare itinerariu"]
        UC5["Chatbot recomandări"]
        UC6["Loc aleatoriu"]
    end
    
    U --> UC1
    U --> UC2
    U --> UC4
    U --> UC5
    U --> UC6
    
    AI --> UC1
    AI --> UC2
    AI --> UC3
    AI --> UC4
    AI --> UC5
    
    UC5 -.->|include| UC1
    UC4 -.->|include| UC3
```

---

## UC4: Gestionare Favorite și Vizite

```mermaid
flowchart LR
    subgraph Actors
        U[("👤 Utilizator")]
    end
    
    subgraph "Modul Favorite/Vizite"
        UC1["Adăugare la favorite"]
        UC2["Eliminare din favorite"]
        UC3["Vizualizare favorite"]
        UC4["Marcare loc vizitat"]
        UC5["Istoric vizite"]
        UC6["Adăugare review"]
        UC7["Încărcare poze"]
    end
    
    U --> UC1
    U --> UC2
    U --> UC3
    U --> UC4
    U --> UC5
    U --> UC6
    U --> UC7
    
    UC6 -.->|extend| UC7
    UC4 -.->|extend| UC6
```

---

## UC5: Calendar și Planificare

```mermaid
flowchart LR
    subgraph Actors
        U[("👤 Utilizator")]
        U2[("👥 Alt Utilizator")]
    end
    
    subgraph "Modul Calendar"
        UC1["Planificare activitate"]
        UC2["Vizualizare calendar"]
        UC3["Ștergere activitate"]
        UC4["Invitare utilizator"]
        UC5["Acceptare invitație"]
        UC6["Refuzare invitație"]
        UC7["Setare reminder"]
    end
    
    U --> UC1
    U --> UC2
    U --> UC3
    U --> UC4
    U --> UC7
    
    U2 --> UC5
    U2 --> UC6
    
    UC1 -.->|extend| UC4
    UC1 -.->|extend| UC7
    UC4 -.->|include| UC5
    UC4 -.->|include| UC6
```

---

## UC6: Gamificare

```mermaid
flowchart LR
    subgraph Actors
        U[("👤 Utilizator")]
        S[("⚙️ Sistem")]
    end
    
    subgraph "Modul Gamificare"
        UC1["Vizualizare puncte"]
        UC2["Vizualizare nivel"]
        UC3["Colectare badge-uri"]
        UC4["Vizualizare badge-uri"]
        UC5["Primire puncte"]
    end
    
    U --> UC1
    U --> UC2
    U --> UC4
    
    S --> UC3
    S --> UC5
    
    UC3 -.->|include| UC5
```

---

## UC7: Selectare Oraș și Setări

```mermaid
flowchart LR
    subgraph Actors
        U[("👤 Utilizator")]
    end
    
    subgraph "Modul Setări"
        UC1["Selectare oraș"]
        UC2["Căutare oraș"]
        UC3["Schimbare temă"]
        UC4["Setări notificări"]
        UC5["Schimbare limbă"]
        UC6["Vizualizare privacy policy"]
    end
    
    U --> UC1
    U --> UC2
    U --> UC3
    U --> UC4
    U --> UC5
    U --> UC6
    
    UC1 -.->|extend| UC2
```

---

## Diagramă Generală - Toate Modulele

```mermaid
flowchart TB
    subgraph Actors
        UN[("👤 Utilizator Neautentificat")]
        UA[("👤 Utilizator Autentificat")]
        AI[("🤖 Sistem AI")]
        S[("⚙️ Sistem")]
    end
    
    subgraph "CityScape Application"
        subgraph "Autentificare"
            A1["Login/Register"]
            A2["Social Login"]
        end
        
        subgraph "Explorare"
            E1["Hartă interactivă"]
            E2["Căutare locații"]
            E3["Detalii loc"]
        end
        
        subgraph "AI Features"
            AI1["Recomandări personalizate"]
            AI2["Itinerariu automat"]
            AI3["Chatbot"]
        end
        
        subgraph "Interacțiune"
            I1["Favorite"]
            I2["Reviews"]
            I3["Calendar/Invitații"]
        end
        
        subgraph "Gamificare"
            G1["Puncte & Nivele"]
            G2["Badge-uri"]
        end
    end
    
    UN --> A1
    UN --> A2
    UN --> E1
    UN --> E2
    
    UA --> E1
    UA --> E2
    UA --> E3
    UA --> AI1
    UA --> AI2
    UA --> AI3
    UA --> I1
    UA --> I2
    UA --> I3
    UA --> G1
    UA --> G2
    
    AI --> AI1
    AI --> AI2
    AI --> AI3
    
    S --> G1
    S --> G2
```

---

## Legendă

| Simbol | Semnificație |
|--------|-------------|
| `-->` | Asociere actor-caz de utilizare |
| `-.->|include|` | Relație de includere (obligatorie) |
| `-.->|extend|` | Relație de extindere (opțională) |
| `👤` | Actor uman |
| `🤖` | Actor sistem (AI) |
| `⚙️` | Actor sistem (backend) |

---

## Descrieri Cazuri de Utilizare Principale

### UC-AUTH-01: Autentificare Utilizator
- **Actor principal:** Utilizator neautentificat
- **Precondiție:** Utilizatorul are cont creat
- **Flux principal:** Email + Parolă → Validare → Acces aplicație
- **Flux alternativ:** Login social (Google/Facebook)

### UC-REC-01: Recomandări Personalizate
- **Actor principal:** Utilizator autentificat, Sistem AI
- **Precondiție:** Preferințe utilizator configurate
- **Flux principal:** AI analizează preferințe → Calculare compatibilitate → Afișare top locații

### UC-CAL-01: Planificare Activitate
- **Actor principal:** Utilizator autentificat
- **Precondiție:** Utilizator logat
- **Flux principal:** Selectare loc → Alegere dată/oră → Opțional invitare prieteni → Salvare
