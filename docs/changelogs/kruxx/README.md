# KruXx-Changelogs

Die Datei `<versionName>.txt` enthält genau die Hinweise, die für diese KruXx-Version im
Changelog-Dialog der Release-App erscheinen. Veröffentlichte Dateien sind historische Belege und
werden nicht nachträglich um Funktionen späterer Versionen ergänzt.

Format:

```text
Abschnitt:
- Erster Eintrag
- Zweiter Eintrag
```

Für den aktuellen Build kopiert Gradles Task `copyKruxxReleaseNote` die passende Datei nach
`composeApp/src/androidKruxx/res/raw/release_notes.txt`. Nach dem Kopiertask müssen Quelle und
Flavor-Ressource bytegleich sein; vor Veröffentlichung zusätzlich die eingebettete Datei in der
Release-APK prüfen. Der Kopiertask läuft auch für KruXx-Debug;
dort überlagert jedoch `composeApp/src/androidDebug/res/raw/release_notes.txt` die Flavor-Ressource.
Der Debug-Dialog ist deshalb kein Nachweis für die veröffentlichten Versionshinweise.
Kreate-Dateien unter `fastlane/metadata/android/` sowie die Release-Note-Ressourcen anderer
Product Flavors gehören nicht zur KruXx-Historie.
