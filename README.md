# CraftplayPlotExtras

Kleines Paper/Bukkit-Addon für Craftplay mit Extras für PlotSquared, Multiverse-Core und CMI. Es schützt Fly in PlotSquared-Plotwelten, deaktiviert CMI-Fly in Nicht-Plotwelten und verhindert Despawn bei per Nametag benannten Kreaturen.

## Verhalten

- Deaktiviert Fliegen nicht beim Betreten oder Verlassen eines Plots.
- Stellt Fly in Plotwelten automatisch wieder her, falls PlotSquared oder CMI es beim Plot-Wechsel ausschaltet.
- Deaktiviert Fliegen beim Wechsel in andere Welten, z. B. Farmwelt oder Nether.
- Erkennt Plotwelten automatisch über PlotSquared.
- Nutzt automatisch `cmi fly <spieler> false -s`, wenn CMI installiert ist.
- Funktioniert mit Multiverse-Core-Weltwechseln über den normalen Bukkit-World-Change-Event.
- Creative- und Spectator-Spieler werden standardmäßig nicht verändert.
- Monster, Tiere und Villager despawnen nicht mehr, wenn sie mit einem Nametag benannt wurden.
- PlotSquared muss auf dem Server installiert sein.

## Config

Beim ersten Start wird automatisch eine `config.yml` im Plugin-Ordner erstellt. Welten müssen dort nicht eingetragen werden; Plotwelten kommen weiterhin automatisch aus PlotSquared.

## Build

```bash
mvn package
```

Die fertige Jar liegt danach unter `target/craftplay-plot-extras-1.0.0.jar`.
