# CraftplayPlotExtras

CraftplayPlotExtras ist ein leichtes Menü-Plugin, das Spielern die Bedienung von PlotSquared 5.4.0 erleichtert.

Aktueller Stand:

- `/plotextras` öffnet das Hauptmenü
- `/plotextras reload` lädt die Konfiguration neu
- Buttons, Slots, Materialien, Namen, Lore, Permissions und Befehle liegen unter `gui/<sprache>/main.yml`
- Sprachdateien liegen unter `language/`
- Deutsch und Englisch werden direkt mitgeliefert
- PlotSquared wird als Pflichtabhängigkeit geladen
- Die Menübuttons führen aktuell konfigurierbare PlotSquared-Befehle aus
- Jede verwaltete YAML-Datei hat eine `file-version`
- Vor automatischen Ergänzungen wird die alte Datei unter `backup/` mit Datum im Dateinamen gesichert
- Neue Standardwerte werden ergänzt, vorhandene Einstellungen werden nicht überschrieben

Die neue Codebasis ist bewusst schlank gehalten, damit die nächsten Funktionen sauber darauf aufgebaut werden können.
