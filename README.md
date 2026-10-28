# Gauchos Kill Sounds

Fabric-Mod für **Minecraft 1.21.11**. Spielt **deinen eigenen Sound, wenn du einen Kill machst** –
ausgesucht aus Gauchos Sound-Sammlung hier im Repo. Menü mit Taste **G**.

- Sounds liegen im Ordner [`killsounds/`](killsounds), Hintergrundbilder fürs Menü in [`backgrounds/`](backgrounds).
- Nur du (Besitzer des Repos) kannst dort hochladen. Deine Freunde laden nur herunter.
- Neue Sounds brauchen **keine neue Mod-Version** – hochladen, ingame auf **↻** klicken, fertig.
- Der Kill-Sound ist nur für dich hörbar und funktioniert auf jedem Server (nichts auf dem Server nötig).

---

## 1. Einmalig einrichten

1. Auf <https://github.com> oben rechts **+ → New repository**
   - Name: `gauchos-kill-sounds`
   - **Public** auswählen (sonst können deine Freunde die Sounds nicht laden)
   - **Create repository**
2. Auf **„uploading an existing file“** klicken und **alles aus diesem Ordner** hineinziehen –
   auch den versteckten Ordner `.github` (Windows: Explorer → Ansicht → „Ausgeblendete Elemente“).
   Dann **Commit changes**.
3. Tab **Actions** → der Build startet automatisch (2–5 Minuten). Falls nicht: links **build** → **Run workflow**.
4. Wenn er grün ist: rechts unter **Releases** liegt `gauchos-kill-sounds-1.0.X.jar`. **Das ist die Mod.**

> Dein Repo-Name wird beim Bauen automatisch eingetragen – du musst keinen Code anfassen.

## 2. Mod installieren (du und deine Freunde)

1. [Fabric Loader](https://fabricmc.net/use/installer/) für **1.21.11** installieren.
2. **Fabric API** für 1.21.11 und die `gauchos-kill-sounds-….jar` in den `mods`-Ordner legen.
3. Wer noch die alte **Gauchos Texture Gallery** hat: die alte .jar aus `mods` löschen.
   Die Mod räumt die alten Textur-Dateien beim ersten Start selbst auf und übernimmt die Einstellungen.
4. Spiel starten, **G** drücken.

## 3. Sounds hochladen

- Ordner `killsounds/` öffnen → **Add file → Upload files** → Datei reinziehen → **Commit changes**.
- Dateien müssen **.ogg** sein (am besten mono, unter 2 Sekunden). MP3/WAV z. B. mit Audacity
  oder convertio.co in .ogg umwandeln.
- Der Dateiname ist der Name im Menü: `Headshot.ogg` erscheint als „Headshot“.
- Löschen: Datei anklicken → **…** → **Delete file** → **Commit changes**.

## 4. Hintergrundbilder fürs Menü

- Ordner `backgrounds/` – PNG, JPG, GIF oder BMP (am schärfsten mit 1920×1080).
- Jeder Spieler sucht sich unter **⚙ → Hintergrund** eins aus. Ohne Auswahl wird das erste benutzt.

## Das Menü

- **Oben:** der aktive Kill-Sound mit **▶** zum Anhören.
- **Lautstärke:** 25 / 50 / 75 / 100 %.
- **Liste:** alle Sounds – Klick wählt aus (und spielt ihn kurz an), **▶** nur anhören. Alles wird sofort gespeichert.
- Oben rechts: **↻** neu von GitHub laden, **⚙** Aussehen, **✕** schließen.
- **⚙ Aussehen** (jeder für sich): Dunkel/Hell, Akzentfarbe, Hintergrundbild, Abdunkeln.

## So funktioniert's (technisch)

- Beim Start liest die Mod die Dateiliste deines Repos und lädt neue Sounds/Bilder in
  `.minecraft/config/gauchos_killsounds_cache/` (danach geht's auch offline).
- Damit Minecraft sie abspielen kann, legt sie ein kleines Resource Pack `resourcepacks/GauchosKillSounds` an.
  Es ändert **keine** Spiel-Texturen.
- Kill-Erkennung: Spieler, die du geschlagen hast oder die neben einem End-Kristall / Seelenanker standen,
  den du ausgelöst hast, und danach sterben. Zusätzlich wird die Todesnachricht im Chat geprüft.

## Selbst bauen (optional)

`gradle.properties` → `gallery_repo=DEINNAME/gauchos-kill-sounds`, dann `./gradlew build` (Java 21+).
Die Jar liegt in `build/libs/`.
