# CraftplayPlotExtras

Paper/Bukkit-Addon für Craftplay mit PlotSquared-Extras:

- schützt Fly in PlotSquared-Welten und deaktiviert Fly außerhalb von Plotwelten
- verhindert Despawn bei per Nametag benannten Kreaturen
- bietet ein konfigurierbares Plot-GUI für Flags, Deko, Mitglieder, Einstellungen, Infos und Sprache
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
- lädt GUI-Dateien pro Sprache aus `gui/<sprache>/*.yml`
- lädt Wand- und Randdefinitionen getrennt aus `wall.yml` und `border.yml`
- jede Wand- und Randoption kann dort einzeln per `enabled` deaktiviert und per `permission` an eigene Ränge gehängt werden
- begrenzt Entities pro Plot oder Plotmerge über `limits.yml` und zeigt die Werte im Entity-Limit-GUI
- besitzt eine zentrale `features.yml`, über die komplette Bereiche und einzelne Unterfunktionen abschaltbar sind
- sichert vorhandene Standard-Konfigurationen bei Versionswechseln unter `backups/<alte-version>/` und ergänzt neue Standard-Einträge
- unterstützt optionale Items aus HeadDatabase
- unterstützt PlaceholderAPI, inklusive konfigurierbarer Platzhalter für Jobs, CMI-Geld und Quests

Zusätzlich enthält der aktuelle Ausbau Plot-Meldungen, Team-Moderation mit Freeze und Cleanup, Performance-Schnappschüsse, Plot-Wettbewerbe, Config-Validierung und Schematic-Backups als `.schem`-Dateien.

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
/plotextras report <Grund>
/plotextras reports list
/plotextras reports close <id> <Notiz>
/plotextras mod freeze <Grund>
/plotextras mod unfreeze
/plotextras mod cleanup <drops|projectiles|monsters|animals|vehicles|all>
/plotextras performance
/plotextras contest join <Name> <Notiz>
/plotextras contest score <id> <0-100> <Notiz>
/plotextras validate
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
craftplayplotextras.report.create
craftplayplotextras.report.view
craftplayplotextras.report.close
craftplayplotextras.moderation.manage
craftplayplotextras.moderation.bypass
craftplayplotextras.performance.view
craftplayplotextras.config.validate
craftplayplotextras.competition.join
craftplayplotextras.competition.judge
craftplayplotextras.admin
```

## Konfiguration

Beim ersten Start installiert das Plugin:

```text
config.yml
features.yml
wall.yml
border.yml
plot-settings.yml
limits.yml
language/de.yml
language/en.yml
gui/de/*.yml
gui/en/*.yml
```

Neue Sprachdateien werden nach Serverstart oder `/plotextras reload` automatisch erkannt und im Sprach-GUI angezeigt. Eigene GUI-Buttons können über `actions` oder `commands` hinzugefügt werden.

Alle großen Funktionsbereiche werden zusätzlich über `features.yml` gesteuert. Dort können z. B. `player.flags`, `player.decor.wall`, `player.plot-warps.delete`, `team.audit-log`, `redstone.detection`, `limits.enforce` oder externe Integrationen einzeln deaktiviert werden. GUI-Items und dynamische Bereiche können zusätzlich ein `feature: <pfad>` bekommen; dann wird der Button automatisch ausgeblendet, wenn der passende Schalter deaktiviert ist.

Einzelne Auswahloptionen bleiben in ihren jeweiligen Dateien abschaltbar:

```text
wall.yml: options.<id>.enabled
border.yml: options.<id>.enabled
limits.yml: limits.<id>.enabled
plot-settings.yml: <bereich>.options.<id>.enabled
config.yml: plot-presets.flags.options.<id>.enabled
gui/<sprache>/*.yml: items.<id>.enabled oder dynamic.enabled
```

## Build

```bash
mvn package
```

Die fertige Jar liegt danach unter `target/craftplay-plot-extras-1.0.0.jar`.
