# KruXx-Changelogs

Die Datei `<versionName>.txt` enthält genau die Hinweise, die für diese KruXx-Version im
Changelog-Dialog der App erscheinen. Veröffentlichte ältere Dateien sind historische Belege und
werden nicht nachträglich um Funktionen späterer Versionen ergänzt.

Format:

```text
Abschnitt:
- Erster Eintrag
- Zweiter Eintrag
```

Für den aktuellen Build kopiert Gradles Task `copyKruxxReleaseNote` die passende Datei nach
`composeApp/src/androidKruxx/res/raw/release_notes.txt`. Vor Build und Release müssen Quelle und
eingebettete Kopie bytegleich sein. Kreate-Dateien unter `fastlane/metadata/android/` sowie die
Release-Note-Ressourcen anderer Product Flavors gehören nicht zur KruXx-Historie.
