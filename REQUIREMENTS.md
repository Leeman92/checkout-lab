# Checkout Lab – Anforderungsdokument

## 1. Zweck des Projekts

Dieses Projekt dient dazu, vorhandene Java-Erfahrung zu reaktivieren und den heutigen Stand professioneller Backend-Entwicklung mit Java und Spring Boot praktisch zu erarbeiten.

Es soll bewusst **kein vollständiger Online-Shop** entstehen. Stattdessen wird ein klar abgegrenzter, aber fachlich anspruchsvoller Ausschnitt umgesetzt: das Anlegen, Bezahlen, Stornieren und Abfragen von Bestellungen unter realistischen Fehler- und Konkurrenzbedingungen.

Das Projekt soll klein genug bleiben, um vollständig abgeschlossen zu werden, aber schwierig genug sein, um mehr als reine CRUD-Entwicklung zu verlangen.

---

## 2. Lernziele

Nach Abschluss des Projekts soll der Entwickler in der Lage sein:

- modernen Java-Code sicher zu lesen, zu schreiben und zu erklären,
- eine Spring-Boot-Anwendung selbstständig zu strukturieren,
- fachliche Regeln klar von Transport-, Speicher- und Infrastrukturbelangen zu trennen,
- konsistente Zustandsänderungen bei Fehlern und parallelen Zugriffen sicherzustellen,
- eine nachvollziehbare HTTP-API zu entwerfen,
- automatisierte Tests auf mehreren Ebenen zu schreiben,
- die Anwendung lokal reproduzierbar zu starten und zu betreiben,
- eigene technische Entscheidungen mit Vor- und Nachteilen zu begründen,
- fremdes Feedback kritisch zu prüfen, statt es ungefiltert zu übernehmen.

Dieses Dokument beschreibt **erwartetes Verhalten**, aber keine konkrete technische Lösung.

---

## 3. Verbindlicher technischer Rahmen

Der technische Rahmen gehört zur Übung und ist daher vorgegeben:

- Java 25
- Spring Boot 4.x
- Maven
- relationale Datenbank
- HTTP-API mit JSON
- Docker-basierter lokaler Start der benötigten Infrastruktur
- automatisierte Tests
- Versionsverwaltung mit Git

Die Wahl konkreter Bibliotheken, Projektstruktur, Klassen, Sprachfeatures, Persistenzabbildung und interner Architektur ist Teil der Aufgabe.

---

## 4. Produktvision

Das System verwaltet einen kleinen Produktbestand und ermöglicht das Anlegen von Bestellungen.

Eine Bestellung enthält ein oder mehrere Produkte mit einer jeweils gewünschten Menge. Beim Anlegen einer Bestellung werden Preise und Verfügbarkeit geprüft. Für die Bestellung wird Bestand reserviert. Anschließend kann eine simulierte Zahlung durchgeführt werden.

Das System muss auch bei wiederholten Anfragen, parallelen Bestellungen, verzögerten Zahlungsergebnissen und technischen Fehlern ein fachlich korrektes Ergebnis liefern.

---

## 5. Abgrenzung

### 5.1 Im Projekt enthalten

- Abfrage vorhandener Produkte
- Abfrage verfügbarer Bestände
- Anlegen einer Bestellung
- Reservierung von Bestand
- Berechnung und Speicherung des Bestellwerts
- Simulation unterschiedlicher Zahlungsergebnisse
- Verarbeitung verzögerter Zahlungsergebnisse
- Abfrage einer Bestellung
- Stornierung einer Bestellung
- Freigabe reservierter Bestände
- Schutz gegen doppelte Verarbeitung
- nachvollziehbare Fehlerantworten
- nachvollziehbare Zustandsänderungen
- automatisierte Tests
- grundlegende Betriebsinformationen

### 5.2 Ausdrücklich nicht enthalten

- grafische Benutzeroberfläche
- Benutzerkonten und Login
- Rollen- und Rechtesystem
- Warenkorb über mehrere Sitzungen
- Gutscheine und Rabatte
- unterschiedliche Steuersätze
- Versandkosten
- Versanddienstleister
- Retouren
- Rechnungsstellung
- Produktsuche
- Produktbilder
- Kategorien
- CMS-Funktionen
- echtes Payment-Gateway
- E-Mail-Versand
- Mandantenfähigkeit
- Internationalisierung
- mehrere Währungen

Zusätzliche Funktionen dürfen erst begonnen werden, wenn alle verbindlichen Anforderungen erfüllt sind.

---

## 6. Fachliche Begriffe

### 6.1 Produkt

Ein Produkt besitzt mindestens:

- eine eindeutige SKU,
- einen Namen,
- einen Preis,
- einen Aktivitätsstatus,
- einen verfügbaren oder reservierbaren Bestand.

### 6.2 Bestellung

Eine Bestellung besitzt mindestens:

- eine eindeutige Kennung,
- einen fachlichen Status,
- eine oder mehrere Bestellpositionen,
- den zum Bestellzeitpunkt gültigen Einzelpreis jeder Position,
- einen Gesamtbetrag,
- einen Erstellungszeitpunkt,
- einen Schlüssel zur Erkennung wiederholter Anlegeversuche.

### 6.3 Bestellposition

Eine Bestellposition bezieht sich auf genau eine SKU und enthält mindestens:

- die bestellte Menge,
- den verwendeten Einzelpreis,
- den daraus berechneten Positionswert.

### 6.4 Bestandsreservierung

Eine Reservierung stellt sicher, dass dieselbe verfügbare Einheit nicht mehreren gleichzeitig gültigen Bestellungen zugesprochen wird.

### 6.5 Zahlungsversuch

Ein Zahlungsversuch gehört zu genau einer Bestellung und endet mit einem der folgenden fachlichen Ergebnisse:

- erfolgreich,
- abgelehnt,
- noch nicht abschließend entschieden.

Mehrere Zahlungsversuche für dieselbe Bestellung können unter den in diesem Dokument beschriebenen Bedingungen zulässig sein.

---

## 7. Fachliche Zustände einer Bestellung

Das System muss mindestens folgende fachliche Situationen unterscheiden können:

1. **Bestand reserviert, Zahlung noch nicht abgeschlossen**
2. **Zahlung erfolgreich abgeschlossen**
3. **Zahlung fehlgeschlagen oder abgelehnt**
4. **Bestellung storniert**

Die konkrete Benennung und interne Darstellung der Zustände ist Teil der technischen Lösung.

### 7.1 Erlaubte Zustandsänderungen

- Eine neu angelegte, gültige Bestellung beginnt mit reserviertem Bestand und noch nicht erfolgreich abgeschlossener Zahlung.
- Eine noch nicht bezahlte Bestellung kann erfolgreich bezahlt werden.
- Eine noch nicht bezahlte Bestellung kann ein fehlgeschlagenes Zahlungsergebnis erhalten.
- Nach einem fehlgeschlagenen Zahlungsversuch darf ein weiterer Zahlungsversuch möglich sein, solange die Bestellung noch gültig ist.
- Eine noch nicht erfolgreich bezahlte Bestellung kann storniert werden.
- Eine erfolgreich bezahlte Bestellung darf im Rahmen dieses Projekts nicht über den normalen Stornierungsweg storniert werden.
- Eine bereits stornierte Bestellung darf nicht erneut bezahlt werden.
- Wiederholte identische Meldungen dürfen keine zusätzliche fachliche Wirkung erzeugen.

### 7.2 Unzulässige Zustandsänderungen

Unzulässige Änderungen müssen abgelehnt werden, ohne bestehende Daten oder Bestände inkonsistent zu verändern.

Beispiele:

- Zahlung einer stornierten Bestellung
- erneute Reservierung für dieselbe Bestellung
- Stornierung einer erfolgreich bezahlten Bestellung
- nachträgliche Änderung einer abgeschlossenen Bestellung

---

## 8. Funktionale Anforderungen

### FR-001 – Produkte abfragen

Das System muss vorhandene Produkte über die HTTP-API abfragbar machen.

Für jedes Produkt müssen mindestens SKU, Name, Preis, Aktivitätsstatus und aktuell bestellbare Menge erkennbar sein.

### FR-002 – Einzelnes Produkt abfragen

Ein Produkt muss anhand seiner SKU eindeutig abfragbar sein.

Eine unbekannte SKU muss als fachlich nachvollziehbarer Fehler beantwortet werden.

### FR-003 – Bestellung anlegen

Ein Client muss eine Bestellung mit einer oder mehreren Positionen anlegen können.

Jede Position enthält mindestens:

- eine SKU,
- eine positive ganzzahlige Menge.

Bei erfolgreicher Anlage liefert das System mindestens:

- die Bestellkennung,
- den aktuellen Bestellstatus,
- die Positionen,
- die verwendeten Einzelpreise,
- den Gesamtbetrag,
- den Erstellungszeitpunkt.

### FR-004 – Eingabevalidierung

Eine Bestellung darf nicht angelegt werden, wenn:

- keine Position enthalten ist,
- eine Menge kleiner oder gleich null ist,
- eine SKU leer ist,
- eine SKU unbekannt ist,
- ein Produkt inaktiv ist,
- eine angeforderte Menge nicht verfügbar ist.

Fehlerhafte Eingaben dürfen keine teilweise angelegte Bestellung und keine teilweise veränderten Bestände hinterlassen.

### FR-005 – Doppelte SKUs innerhalb einer Anfrage

Enthält eine Bestellanfrage dieselbe SKU mehrfach, muss das System ein eindeutig dokumentiertes Verhalten besitzen.

Zulässige Varianten sind:

- Mengen derselben SKU werden fachlich zusammengeführt,
- oder die Anfrage wird als ungültig abgelehnt.

Die gewählte Regel muss dokumentiert und getestet sein.

### FR-006 – Preis zum Bestellzeitpunkt

Für jede Bestellposition muss der zum Zeitpunkt der Bestellung verwendete Einzelpreis gespeichert werden.

Eine spätere Preisänderung am Produkt darf den Gesamtbetrag einer bestehenden Bestellung nicht verändern.

### FR-007 – Gesamtbetrag

Der Gesamtbetrag einer Bestellung ergibt sich aus der Summe aller Positionswerte.

Berechnungen müssen fachlich korrekt und reproduzierbar sein. Rundungsregeln müssen dokumentiert und getestet werden.

Im Projekt wird ausschließlich EUR verwendet.

### FR-008 – Bestand reservieren

Bei erfolgreicher Bestellanlage muss die bestellte Menge reserviert werden.

Nach erfolgreicher Reservierung darf sie nicht gleichzeitig für eine andere Bestellung als verfügbar gelten.

### FR-009 – Kein negativer Bestand

Der verfügbare Bestand eines Produkts darf unter keinen Umständen negativ werden.

Das gilt auch bei nahezu gleichzeitig eintreffenden Bestellungen.

### FR-010 – Parallele Bestellungen

Wenn mehrere Clients gleichzeitig denselben knappen Bestand bestellen, dürfen insgesamt nur so viele Einheiten erfolgreich reserviert werden, wie tatsächlich vorhanden sind.

Alle weiteren Bestellungen müssen kontrolliert und nachvollziehbar scheitern.

### FR-011 – Wiederholte Bestellanfrage

Beim Anlegen einer Bestellung übermittelt der Client einen eindeutigen Wiederholungsschlüssel.

Wird dieselbe fachliche Anfrage mit demselben Schlüssel erneut gesendet, darf keine zweite Bestellung entstehen und kein weiterer Bestand reserviert werden.

Das System muss das bereits vorhandene Ergebnis wiederauffindbar machen.

### FR-012 – Konflikt beim Wiederholungsschlüssel

Wird derselbe Wiederholungsschlüssel für inhaltlich unterschiedliche Bestellanfragen verwendet, muss das System den Konflikt ablehnen.

Es darf weder eine weitere Bestellung entstehen noch eine bestehende Bestellung unbemerkt verändert werden.

### FR-013 – Bestellung abfragen

Eine Bestellung muss anhand ihrer Kennung vollständig abfragbar sein.

Die Antwort muss mindestens enthalten:

- Kennung,
- fachlichen Status,
- Positionen,
- Preise,
- Gesamtbetrag,
- Erstellungszeitpunkt,
- bekannte Zahlungsergebnisse.

### FR-014 – Unbekannte Bestellung

Die Abfrage einer unbekannten Bestellkennung muss mit einem nachvollziehbaren Fehler beantwortet werden.

### FR-015 – Zahlung starten

Für eine bestehende, noch nicht erfolgreich bezahlte und nicht stornierte Bestellung muss ein Zahlungsversuch ausgelöst werden können.

### FR-016 – Simulierte Zahlungsergebnisse

Die Zahlungssimulation muss mindestens folgende Ergebnisse erzeugen können:

- erfolgreich,
- abgelehnt,
- ausstehend.

Die Auswahl des Simulationsergebnisses muss für Tests kontrollierbar sein.

### FR-017 – Erfolgreiche Zahlung

Bei erfolgreicher Zahlung wird die Bestellung dauerhaft als erfolgreich bezahlt behandelt.

Eine erneute Meldung desselben Zahlungserfolgs darf keine zusätzliche Wirkung auslösen.

### FR-018 – Abgelehnte Zahlung

Bei einer abgelehnten Zahlung bleibt die Bestellung unbezahlt.

Der reservierte Bestand bleibt zunächst bestehen, damit ein weiterer Zahlungsversuch möglich ist.

### FR-019 – Ausstehende Zahlung

Bei einem ausstehenden Zahlungsergebnis bleibt die Bestellung in einem noch nicht abgeschlossenen Zahlungszustand.

Das endgültige Ergebnis kann später eintreffen.

### FR-020 – Verzögertes endgültiges Zahlungsergebnis

Ein ausstehender Zahlungsversuch muss später in ein erfolgreiches oder abgelehntes Ergebnis überführt werden können.

Die spätere Meldung muss eindeutig einem vorhandenen Zahlungsversuch zugeordnet werden können.

### FR-021 – Doppelte Zahlungsnachricht

Wird dasselbe Zahlungsergebnis mehrfach übermittelt, darf es nur einmal fachlich wirksam werden.

### FR-022 – Widersprüchliche Zahlungsnachricht

Treffen für denselben Zahlungsversuch widersprüchliche endgültige Ergebnisse ein, darf das System den bestehenden Abschluss nicht stillschweigend überschreiben.

Der Konflikt muss nachvollziehbar erkennbar sein.

### FR-023 – Erneuter Zahlungsversuch

Nach einer abgelehnten Zahlung darf ein neuer Zahlungsversuch gestartet werden.

Ein neuer Versuch muss von früheren Versuchen unterscheidbar sein.

### FR-024 – Parallele Zahlungsversuche

Das System muss ein dokumentiertes Verhalten besitzen, wenn nahezu gleichzeitig mehrere Zahlungsversuche für dieselbe Bestellung gestartet werden.

Die gewählte Regel muss verhindern, dass eine Bestellung mehrfach bezahlt oder in widersprüchliche Zustände versetzt wird.

### FR-025 – Bestellung stornieren

Eine noch nicht erfolgreich bezahlte Bestellung kann storniert werden.

Die Stornierung muss dauerhaft gespeichert werden.

### FR-026 – Bestand bei Stornierung freigeben

Bei erfolgreicher Stornierung muss der reservierte Bestand wieder für andere Bestellungen verfügbar werden.

Die Freigabe darf bei wiederholter Stornierungsanfrage nicht mehrfach erfolgen.

### FR-027 – Wiederholte Stornierung

Eine wiederholte Stornierungsanfrage für eine bereits stornierte Bestellung darf keine zusätzliche fachliche Wirkung haben.

Das System muss ein konsistentes und dokumentiertes Ergebnis liefern.

### FR-028 – Stornierung bezahlter Bestellungen

Eine erfolgreich bezahlte Bestellung darf nicht über den Stornierungsendpunkt dieses Projekts storniert werden.

Der Versuch muss ohne Veränderung von Bestellung und Bestand abgelehnt werden.

### FR-029 – Zahlung nach Stornierung

Nach einer erfolgreichen Stornierung eintreffende Zahlungsergebnisse dürfen die Bestellung nicht unbemerkt wieder in einen bezahlten Zustand versetzen.

Der Konflikt muss nachvollziehbar behandelt werden.

### FR-030 – Dauerhafte Speicherung

Produkte, Bestände, Bestellungen, Positionen, Reservierungen und Zahlungsergebnisse müssen einen Neustart der Anwendung überstehen.

---

## 9. Fehlerverhalten

### ER-001 – Einheitliches Fehlerformat

Fehlerantworten der HTTP-API müssen einem einheitlichen Format folgen.

Eine Fehlerantwort muss mindestens enthalten:

- einen stabilen maschinenlesbaren Fehlertyp oder Fehlercode,
- eine verständliche Beschreibung,
- den Zeitpunkt,
- eine Möglichkeit, die Anfrage in Logs oder Diagnosedaten wiederzufinden.

### ER-002 – Unterscheidbare Fehler

Mindestens folgende Fehlerarten müssen voneinander unterscheidbar sein:

- syntaktisch ungültige Anfrage,
- fachlich ungültige Anfrage,
- unbekannte Ressource,
- nicht ausreichender Bestand,
- unzulässiger Zustandswechsel,
- Konflikt durch Wiederholungsschlüssel,
- Konflikt durch parallele oder widersprüchliche Verarbeitung,
- unerwarteter interner Fehler.

### ER-003 – Keine internen Details

Fehlerantworten dürfen keine Stacktraces, Datenbankdetails, Zugangsdaten oder sonstige interne Implementierungsdetails offenlegen.

### ER-004 – Keine Teilwirkungen

Eine fehlgeschlagene Operation darf keine fachlich unvollständigen Teilwirkungen hinterlassen.

Beispiele:

- Bestellung existiert, aber Bestand wurde nicht reserviert,
- Bestand wurde reduziert, aber Bestellung fehlt,
- Stornierung wurde gespeichert, aber Bestand nicht freigegeben,
- Zahlung wurde als erfolgreich erfasst, aber Bestellstatus blieb unverändert.

---

## 10. Nichtfunktionale Anforderungen

### NFR-001 – Nachvollziehbarkeit

Wichtige fachliche Operationen müssen in den Anwendungslogs nachvollziehbar sein.

Dazu gehören mindestens:

- Bestellanlage,
- abgelehnte Bestellanlage,
- Zahlungsversuch,
- Zahlungsergebnis,
- Stornierung,
- Konflikte bei doppelter oder widersprüchlicher Verarbeitung.

Personenbezogene Daten sind in diesem Projekt nicht erforderlich und sollen nicht eingeführt werden.

### NFR-002 – Diagnosefähigkeit

Es muss erkennbar sein, ob:

- die Anwendung läuft,
- die Anwendung Anfragen verarbeiten kann,
- die benötigte Datenbank erreichbar ist.

### NFR-003 – Reproduzierbarer lokaler Start

Ein neuer Entwickler muss das Projekt anhand der Projektdokumentation lokal starten können.

Notwendige externe Infrastruktur muss reproduzierbar bereitgestellt werden können.

### NFR-004 – Konfiguration

Umgebungsabhängige Werte dürfen nicht fest im Quellcode verankert sein.

Das Projekt muss mindestens unterschiedliche Konfigurationen für automatisierte Tests und lokalen Betrieb unterstützen.

### NFR-005 – Datenbankänderungen

Änderungen an der Datenbankstruktur müssen versioniert und reproduzierbar sein.

Eine leere Datenbank muss sich anhand des Projekts in den erwarteten Zustand bringen lassen.

### NFR-006 – Testbarkeit

Fachliche Kernregeln müssen automatisiert prüfbar sein, ohne dass alle externen Bestandteile real betrieben werden müssen.

Das Gesamtsystem muss zusätzlich mit realitätsnaher Datenbankanbindung getestet werden.

### NFR-007 – Verständlichkeit

Der Code muss für einen erfahrenen Java-Entwickler ohne mündliche Einführung nachvollziehbar sein.

Ungewöhnliche Entscheidungen müssen dokumentiert werden.

### NFR-008 – Deterministisches Verhalten

Automatisierte Tests dürfen nicht zufällig erfolgreich oder fehlschlagen.

Zeit, Zufall und simulierte Zahlungsergebnisse müssen in Tests kontrollierbar sein.

### NFR-009 – Datengenauigkeit

Geldbeträge dürfen nicht durch ungeeignete numerische Darstellung oder Rundung verfälscht werden.

### NFR-010 – Sicherheit im Basisscope

Auch ohne Benutzerverwaltung gelten mindestens folgende Anforderungen:

- Eingaben werden geprüft,
- interne Fehlerdetails werden nicht offengelegt,
- Geheimnisse werden nicht im Repository gespeichert,
- die Anwendung läuft lokal nicht unnötig mit privilegierten Rechten,
- Abhängigkeiten müssen nachvollziehbar verwaltet werden.

### NFR-011 – Build

Das Projekt muss über Maven ohne IDE-spezifische Schritte gebaut und getestet werden können.

### NFR-012 – Dokumentation technischer Entscheidungen

Mindestens drei relevante technische Entscheidungen müssen jeweils kurz dokumentiert werden.

Jede Dokumentation enthält:

- Ausgangssituation,
- betrachtete Optionen,
- getroffene Entscheidung,
- Vorteile,
- Nachteile,
- mögliche spätere Neubewertung.

Die Entscheidungen selbst werden in diesem Dokument nicht vorgegeben.

---

## 11. Verbindliche Akzeptanzszenarien

Die folgenden Szenarien müssen automatisiert oder nachvollziehbar reproduzierbar geprüft werden.

### AC-001 – Erfolgreiche Einzelbestellung

**Gegeben:** Ein aktives Produkt besitzt ausreichenden Bestand.  
**Wenn:** Eine Bestellung mit gültiger SKU und Menge angelegt wird.  
**Dann:** Entsteht genau eine Bestellung, der korrekte Betrag wird gespeichert und der Bestand wird entsprechend reserviert.

### AC-002 – Unbekannte SKU

**Gegeben:** Eine SKU existiert nicht.  
**Wenn:** Sie bestellt wird.  
**Dann:** Wird die Anfrage abgelehnt und weder Bestellung noch Reservierung entstehen.

### AC-003 – Inaktives Produkt

**Gegeben:** Ein Produkt existiert, ist aber inaktiv.  
**Wenn:** Es bestellt wird.  
**Dann:** Wird die Anfrage ohne Bestandsänderung abgelehnt.

### AC-004 – Nicht ausreichender Bestand

**Gegeben:** Ein Produkt besitzt weniger verfügbare Einheiten als angefordert.  
**Wenn:** Die Bestellung angelegt wird.  
**Dann:** Wird sie vollständig abgelehnt; es entsteht keine Teilreservierung.

### AC-005 – Mehrere Positionen, eine ungültig

**Gegeben:** Eine Anfrage enthält mehrere Positionen und mindestens eine davon ist ungültig oder nicht verfügbar.  
**Wenn:** Die Bestellung angelegt wird.  
**Dann:** Wird die gesamte Bestellung abgelehnt und keine Position reserviert.

### AC-006 – Preis bleibt stabil

**Gegeben:** Eine Bestellung wurde erfolgreich angelegt.  
**Wenn:** Der aktuelle Produktpreis danach geändert wird.  
**Dann:** Bleiben Einzelpreise und Gesamtbetrag der bestehenden Bestellung unverändert.

### AC-007 – Identische Wiederholung

**Gegeben:** Eine Bestellanfrage wurde erfolgreich mit einem Wiederholungsschlüssel verarbeitet.  
**Wenn:** Dieselbe Anfrage mit demselben Schlüssel erneut gesendet wird.  
**Dann:** Existiert weiterhin genau eine Bestellung und der Bestand wird nicht erneut reserviert.

### AC-008 – Wiederholungsschlüssel mit anderem Inhalt

**Gegeben:** Ein Wiederholungsschlüssel wurde bereits verwendet.  
**Wenn:** Eine andere Bestellung mit demselben Schlüssel gesendet wird.  
**Dann:** Wird die Anfrage als Konflikt abgelehnt.

### AC-009 – Konkurrenz um letzten Bestand

**Gegeben:** Von einem Produkt ist genau eine Einheit verfügbar.  
**Wenn:** Zwei Bestellungen nahezu gleichzeitig jeweils eine Einheit anfordern.  
**Dann:** Ist genau eine Bestellung erfolgreich und genau eine wird wegen fehlender Verfügbarkeit abgelehnt.

### AC-010 – Erfolgreiche Zahlung

**Gegeben:** Eine gültige, unbezahlte und nicht stornierte Bestellung.  
**Wenn:** Ein erfolgreicher Zahlungsabschluss verarbeitet wird.  
**Dann:** Gilt die Bestellung dauerhaft als bezahlt.

### AC-011 – Doppelte erfolgreiche Zahlungsnachricht

**Gegeben:** Eine Zahlung wurde bereits erfolgreich verarbeitet.  
**Wenn:** Dasselbe erfolgreiche Ergebnis erneut eintrifft.  
**Dann:** Entsteht keine zusätzliche fachliche Wirkung.

### AC-012 – Abgelehnte Zahlung und Wiederholung

**Gegeben:** Eine gültige Bestellung mit reserviertem Bestand.  
**Wenn:** Ein Zahlungsversuch abgelehnt wird und anschließend ein neuer Versuch erfolgreich ist.  
**Dann:** Bleibt die Reservierung zwischen den Versuchen erhalten und die Bestellung endet genau einmal bezahlt.

### AC-013 – Ausstehende Zahlung

**Gegeben:** Eine gültige Bestellung.  
**Wenn:** Der Zahlungsversuch zunächst als ausstehend und später als erfolgreich gemeldet wird.  
**Dann:** Wechselt die Bestellung erst mit dem endgültigen Ergebnis in den bezahlten Zustand.

### AC-014 – Widersprüchliche Zahlungsergebnisse

**Gegeben:** Ein Zahlungsversuch wurde endgültig erfolgreich abgeschlossen.  
**Wenn:** Später für denselben Versuch eine Ablehnung eintrifft.  
**Dann:** Wird der erfolgreiche Abschluss nicht überschrieben und der Konflikt ist nachvollziehbar.

### AC-015 – Erfolgreiche Stornierung

**Gegeben:** Eine unbezahlte Bestellung mit reserviertem Bestand.  
**Wenn:** Sie storniert wird.  
**Dann:** Gilt sie dauerhaft als storniert und der Bestand ist wieder verfügbar.

### AC-016 – Doppelte Stornierung

**Gegeben:** Eine Bestellung wurde bereits storniert und der Bestand freigegeben.  
**Wenn:** Die Stornierung erneut angefordert wird.  
**Dann:** Wird der Bestand nicht ein zweites Mal erhöht.

### AC-017 – Stornierung nach Zahlung

**Gegeben:** Eine erfolgreich bezahlte Bestellung.  
**Wenn:** Eine Stornierung angefordert wird.  
**Dann:** Wird sie abgelehnt und weder Bestellung noch Bestand verändern sich.

### AC-018 – Zahlung nach Stornierung

**Gegeben:** Eine Bestellung wurde erfolgreich storniert.  
**Wenn:** Danach ein erfolgreiches Zahlungsergebnis eintrifft.  
**Dann:** Wird die Bestellung nicht stillschweigend wieder aktiviert oder bezahlt; der Konflikt wird nachvollziehbar behandelt.

### AC-019 – Neustart

**Gegeben:** Produkte, Bestellungen und Zahlungsergebnisse wurden gespeichert.  
**Wenn:** Anwendung und Infrastruktur neu gestartet werden.  
**Dann:** Sind die gespeicherten fachlichen Daten weiterhin vorhanden und konsistent.

### AC-020 – Unerwarteter Fehler

**Gegeben:** Während einer zustandsändernden Operation tritt ein unerwarteter technischer Fehler auf.  
**Wenn:** Die Operation abbricht.  
**Dann:** Bleibt das System fachlich konsistent und liefert keine internen Details an den Client.

---

## 12. Projektetappen

Die Etappen beschreiben fachliche Lieferstände, nicht die technische Reihenfolge innerhalb der Implementierung.

### Etappe A – Lesbares Grundsystem

Enthalten:

- Maven-Build
- Start der Anwendung
- reproduzierbare lokale Infrastruktur
- Produktabfrage
- einheitliches Fehlerformat
- erste automatisierte Tests

### Etappe B – Bestellung und Bestand

Enthalten:

- Bestellung anlegen
- Preise und Gesamtbetrag
- Bestandsreservierung
- Bestellabfrage
- vollständige Validierung
- Wiederholungsschutz für Bestellanlage

### Etappe C – Zahlung und Stornierung

Enthalten:

- Zahlungsversuche
- alle drei Zahlungsergebnisse
- verzögerte Ergebnisse
- Wiederholungs- und Konfliktbehandlung
- Stornierung
- Bestandsfreigabe

### Etappe D – Konkurrenz und Robustheit

Enthalten:

- parallele Bestellungen
- parallele Zahlungsoperationen
- Fehler während zustandsändernder Abläufe
- Neustartverhalten
- realitätsnahe Integrationstests

### Etappe E – Betriebsreife und Abschluss

Enthalten:

- Diagnoseinformationen
- strukturierte und nachvollziehbare Logs
- vollständige Projektdokumentation
- dokumentierte technische Entscheidungen
- Review aller Akzeptanzszenarien
- Aufräumen provisorischer Lösungen

---

## 13. Definition of Done

Das Projekt gilt als abgeschlossen, wenn:

- alle verbindlichen funktionalen Anforderungen umgesetzt sind,
- alle Akzeptanzszenarien erfolgreich geprüft wurden,
- Build und Tests ohne IDE über Maven ausführbar sind,
- ein neuer Entwickler das Projekt anhand der Dokumentation starten kann,
- keine Geheimnisse oder lokale Zugangsdaten im Repository enthalten sind,
- keine bekannten inkonsistenten Zustände bestehen,
- die Anwendung auch nach Neustart korrekt weiterarbeitet,
- mindestens drei technische Entscheidungen dokumentiert sind,
- der Entwickler jede wesentliche technische Entscheidung selbst erklären kann,
- der Entwickler bekannte Schwächen und mögliche nächste Schritte benennen kann,
- der finale Review gemäß `AGENTS.md` keine offenen kritischen Befunde enthält.

---

## 14. Selbstreflexion nach jeder Etappe

Nach jeder Etappe beantwortet der Entwickler schriftlich:

1. Welche Entscheidung war am schwierigsten?
2. Welche Alternativen wurden betrachtet?
3. Welche Annahmen wurden getroffen?
4. Was kann unter Parallelität oder bei einem Fehler schiefgehen?
5. Welche Teile sind fachliche Regeln und welche technische Umsetzung?
6. Welche Tests geben tatsächlich Vertrauen und welche testen nur Implementierungsdetails?
7. Welche Stelle würde ein erfahrener Reviewer vermutlich zuerst hinterfragen?
8. Was wurde durch KI-Unterstützung beeinflusst und wie wurde das Ergebnis selbst überprüft?
9. Welche verwendete Java- oder Spring-Funktion könnte der Entwickler ohne Hilfsmittel erklären?
10. Was würde bei zehnfacher Last oder einem zweiten Prozess der Anwendung problematisch werden?

Diese Antworten sind Bestandteil des Lernprojekts und werden bei Reviews berücksichtigt.
