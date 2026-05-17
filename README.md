# CraftplayPlotExtras

Paper/Bukkit-Addon für Craftplay mit PlotSquared-Extras:

- schützt Fly in PlotSquared-Welten und deaktiviert Fly außerhalb von Plotwelten
- verhindert Despawn bei per Nametag benannten Kreaturen
- bietet ein konfigurierbares Plot-GUI fuer Flags, Deko, Mitglieder, Einstellungen, Infos und Sprache
- zeigt deutsche Flag-Namen und Beschreibungen aus `language/de.yml`
- nutzt Permission-Nodes wie `plots.plot.84` als hartes Plot-Limit und für die Plot-Info-Anzeige
- trennt Einstellungen in Tabs für Home, Wetter, Zeit und Plotbiom
- trennt Deko in Tabs für Wand und Rand, danach in konfigurierbare Kategorien und Block-Untermenüs
- teilt Wandblöcke in 5 konfigurierbare Permission-Wertgruppen
- liefert zusätzliche Wand- und Randblöcke inklusive Halbstufen und einer Rand-Kategorie `Spezial`
- verhindert in Plotwelten, dass Enderdrachenei-Ränder beim Anklicken wegteleportieren
- erlaubt Änderungen nur dem Besitzer des aktuellen Plots oder des verbundenen Plotmerges
- bietet pro Plot/Plotmerge eigene Rollen mit einzelnen Rechten für Flags, Wand, Rand, Home, Wetter, Zeit und Biom
- Rollen, Rechte und Mitglieder-Zuweisungen sind im GUI verwaltbar; neue Namen werden nach Klick per Chat eingegeben
- Rollen können zusätzlich `members.promote` und `members.demote` erhalten, um Mitglieder per GUI zu befördern oder zu degradieren
- zeigt Spielern nur Flags, für die sie eine konfigurierte Flag-Permission besitzen
- liest Sprachen automatisch aus `language/*.yml`
- laedt GUI-Dateien pro Sprache aus `gui/<sprache>/*.yml`
- laedt Wand- und Randdefinitionen getrennt aus `wall.yml` und `border.yml`
- jede Wand- und Randoption kann dort einzeln per `enabled` deaktiviert und per `permission` an eigene Ränge gehängt werden
- begrenzt Entities pro Plot oder Plotmerge ueber `limits.yml` und zeigt die Werte im Entity-Limit-GUI
- sichert vorhandene Standard-Konfigurationen bei Versionswechseln unter `backups/<alte-version>/` und ergaenzt neue Standard-Eintraege
- unterstuetzt optionale Items aus HeadDatabase
- unterstuetzt PlaceholderAPI, inklusive konfigurierbarer Platzhalter fuer Jobs, CMI-Geld und Quests

## Befehle

```text
/plotextras
/plotgui
/pe
/plotextras language
/plotextras role list
/plotextras role create <id> <Name>
/plotextras role rename <id> <Name>
/plotextras role delete <id>
/plotextras role permission <id> <Recht> <on|off>
/plotextras role assign <Spieler> <id>
/plotextras reload
```

## Rechte

```text
craftplayplotextras.use
craftplayplotextras.flags
craftplayplotextras.decor
craftplayplotextras.settings
craftplayplotextras.decor.wall.1
craftplayplotextras.decor.wall.2
craftplayplotextras.decor.wall.3
craftplayplotextras.decor.wall.4
craftplayplotextras.decor.wall.5
craftplayplotextras.entitylimit.bypass
craftplayplotextras.admin
```

## Konfiguration

Beim ersten Start installiert das Plugin:

```text
config.yml
wall.yml
border.yml
limits.yml
language/de.yml
language/en.yml
gui/de/*.yml
gui/en/*.yml
```

Neue Sprachdateien werden nach Serverstart oder `/plotextras reload` automatisch erkannt und im Sprach-GUI angezeigt. Eigene GUI-Buttons können über `actions` oder `commands` hinzugefügt werden.

## Build

```bash
mvn package
```

Die fertige Jar liegt danach unter `target/craftplay-plot-extras-1.0.0.jar`.
