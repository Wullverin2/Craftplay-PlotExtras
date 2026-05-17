# CraftplayPlotExtras Permissions

Diese Datei dokumentiert die statischen und dynamischen Rechte des Plugins. Rechte aus `plugin.yml` sind direkt sichtbar, dynamische Rechte werden aus `config.yml`, `wall.yml`, `border.yml` und `limits.yml` gelesen.

## Grundrechte

- `craftplayplotextras.use`: Spieler-GUI öffnen.
- `craftplayplotextras.admin`: Admin-Bypass für Plot- und Pluginprüfungen.
- `craftplayplotextras.cooldown.bypass`: Cooldowns umgehen.

## Spielerfunktionen

- `craftplayplotextras.flags`: Flags über die GUI ändern.
- `craftplayplotextras.flags.<flag>`: einzelnes Flag erlauben, wenn `config.yml` `flags.require-permission` aktiv ist. Beispiel: `craftplayplotextras.flags.mob-spawning`.
- `craftplayplotextras.presets`: Flag-Presets nutzen.
- `craftplayplotextras.decor`: Wand und Rand über die GUI ändern.
- `craftplayplotextras.decor.wall.1` bis `.5`: Wand-Wertgruppen aus `wall.yml`.
- `craftplayplotextras.decor.wall.*`: Dokumentations-Wildcard für alle Wandgruppen.
- `craftplayplotextras.decor.border.*`: Dokumentations-Wildcard für optionale Randrechte aus `border.yml`.
- `craftplayplotextras.settings`: Wetter, Zeit, Biom und Home über die GUI ändern.
- `craftplayplotextras.report.create`: Plot melden.
- `craftplayplotextras.competition.join`: Plot bei Wettbewerben anmelden.
- `craftplayplotextras.visit.bypass`: Besuchsmodus umgehen.

## Teamfunktionen

- `craftplayplotextras.teamtools`: Team-GUI öffnen.
- `craftplayplotextras.audit.view`: Auditlog und Team-Inspektor ansehen.
- `craftplayplotextras.backup.admin`: Backups ansehen.
- `craftplayplotextras.backup.create`: aktuelles Plot sichern.
- `craftplayplotextras.backup.restore`: Backup auf aktuellem Plot wiederherstellen.
- `craftplayplotextras.report.view`: Plot-Meldungen ansehen.
- `craftplayplotextras.report.close`: Plot-Meldungen schließen.
- `craftplayplotextras.requests.manage`: Spieleranfragen bearbeiten.
- `craftplayplotextras.guestbook.manage`: Gästebuch-Einträge löschen.
- `craftplayplotextras.moderation.manage`: Plots einfrieren, freigeben und bereinigen.
- `craftplayplotextras.moderation.bypass`: Plot-Freeze umgehen.
- `craftplayplotextras.performance.view`: Performance-Schnappschuss ansehen.
- `craftplayplotextras.config.validate`: Config-Check öffnen.
- `craftplayplotextras.features.manage`: Feature-Toggles ändern.
- `craftplayplotextras.statistics`: Statistik-GUI öffnen.
- `craftplayplotextras.permissioncheck`: Rechtecheck für Online-Spieler nutzen.
- `craftplayplotextras.plotmeta.team`: Teamnotizen lesen und ändern.
- `craftplayplotextras.plotmeta.status`: Plotstatus ändern.
- `craftplayplotextras.redstone.notify`: Redstone-Lagwarnungen erhalten.
- `craftplayplotextras.redstone.admin`: Redstone-Alarme teleportieren und Redstone reaktivieren.
- `craftplayplotextras.builder.mode`: Builder-Modus setzen.
- `craftplayplotextras.builder.tasks`: Builder-Aufgaben erstellen und abschließen.
- `craftplayplotextras.competition.judge`: Wettbewerbe bewerten.

## Entity-Limits

- `craftplayplotextras.entitylimit.bypass`: alle Entity-Limits umgehen.
- `craftplayplotextras.entitylimit.<entity>.<limit>`: höheres Limit aus `limits.yml`.
- `craftplayplotextras.entitylimit.<entity>.unlimited`: einzelnes Entity-Limit deaktivieren.
- `craftplayplotextras.entitylimit.total.<limit>`: höheres Gesamtlimit.
- `craftplayplotextras.entitylimit.total.unlimited`: Gesamtlimit deaktivieren.

Beispiele:

- `craftplayplotextras.entitylimit.villager.64`
- `craftplayplotextras.entitylimit.armor_stand.80`
- `craftplayplotextras.entitylimit.total.400`

## Plotlimit

Das harte Plotlimit wird absichtlich über die externe Permission aus `config.yml` gelesen:

- Standard: `plots.plot.<anzahl>`
- Beispiel: `plots.plot.84`

Das Format kann in `config.yml` unter `plot-limits.permission-pattern` und `plot-limits.permission-check-format` angepasst werden.
