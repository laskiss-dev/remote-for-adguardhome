# Remote for AdGuard Home

Android-alkalmazás a saját AdGuard Home szervered kezeléséhez: lekérdezési napló, statisztika, szűrők, kliensek és beállítások, a webes felülethez hasonló kinézettel.

*An Android app for managing your own AdGuard Home server: query log, statistics, filters, clients and settings, with a look close to the web interface.*

Nyelvek / Languages: magyar, English (a telefon nyelvét követi / follows the phone's language).

## Nem hivatalos alkalmazás / Unofficial app

Független fejlesztés: nem áll kapcsolatban az AdGuard Home fejlesztőivel, az AdGuard csapatával, és nem ők készítették vagy támogatják. Az „AdGuard” név az AdGuard védjegye, itt csak annak jelzésére szerepel, hogy az app mihez készült.

*Independent project, not affiliated with, endorsed or supported by AdGuard or the AdGuard Home developers. "AdGuard" is a trademark of AdGuard and is used here only to describe what the app works with.*

## Letöltés / Download

A kész APK a [Releases](../../releases) oldalon található.

## Fordítás / Building

Java 17, Gradle 8.14, Android SDK 35. Külső könyvtárat nem használ.

```
gradle assembleRelease
```

A kiadott APK aláírókulcsa nincs a repóban. A GitHub Actions a repó titkos beállításaiból (`RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`) állítja elő a fordítás idejére. Helyi fordításnál a `RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` és `RELEASE_KEY_PASSWORD` környezeti változókkal adható meg; ezek nélkül aláíratlan APK készül.

## Adatvédelem / Privacy

Az app nem gyűjt adatot és semmit nem küld a fejlesztőnek. Részletek: [PRIVACY.md](PRIVACY.md).

*The app collects no data and sends nothing to the developer. Details: [PRIVACY.md](PRIVACY.md).*

## Licenc / License

© 2026 Kiss László

Ez a program szabad szoftver: a GNU General Public License 3. verziója szerint terjesztheted és módosíthatod. Lásd: [LICENSE](LICENSE).

*This program is free software, licensed under the GNU General Public License v3. See [LICENSE](LICENSE).*
