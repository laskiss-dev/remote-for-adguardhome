# Adatvédelmi nyilatkozat / Privacy policy

**Remote for AdGuard Home** · hu.laskiss.remoteforadguardhome · Fejlesztő / Developer: Kiss László

Utolsó frissítés / Last updated: 2026. 11. 12.

---

## Magyarul

**Röviden:** az app nem gyűjt rólad adatot, és semmit nem küld a fejlesztőnek vagy harmadik félnek. Nincs benne elemzés, hirdetés, hibajelentő szolgáltatás vagy felhasználókövetés.

### Mit csinál az app az adatokkal

- **A saját AdGuard Home szervered.** Az app kizárólag azzal a szerverrel (vagy szerverekkel) kommunikál, amelynek a címét te adod meg. Ide küldi a bejelentkezési adataidat, és innen tölti le a statisztikát, a lekérdezési naplót, a szűrőket, a klienseket és a beállításokat. Ezek az adatok a te szervered és a telefonod között mozognak, a fejlesztőhöz nem jutnak el.
- **Telefonon tárolt adatok.** A szerver címe, felhasználóneve, az app beállításai és (ha nem kapcsolod ki a mentést) a jelszó a telefonon tárolódik. A jelszavakat az Android Keystore által védett kulccsal titkosítva menti. Az app biztonsági mentése (`allowBackup`) ki van kapcsolva, ezért ezek az adatok nem kerülnek a Google-fiók mentésébe. Az app törlésével az összes tárolt adat törlődik.
- **Szűrőlisták jegyzéke.** Amikor megnyitod a szűrőlisták hozzáadását, az app letölti az AdGuard nyilvános listajegyzékét (`adguardteam.github.io`). Ilyenkor az a kiszolgáló látja a telefonod IP-címét, ahogy bármely weboldal-letöltésnél. Az app semmilyen azonosítót nem küld hozzá.
- **Külső böngésző.** Ha a naplóban egy domaint megnyitsz a böngészőben, azt az Android alapértelmezett böngészője nyitja meg, és arra annak saját adatvédelmi szabályai vonatkoznak.

### Engedélyek

- **Internet:** a megadott szerverrel való kommunikációhoz és a fenti listajegyzék letöltéséhez.

Más engedélyt az app nem kér. Nem fér hozzá a névjegyekhez, a helyadatokhoz, a fényképekhez, a mikrofonhoz vagy a kamerához.

### Titkosítatlan (HTTP) kapcsolat

Az app alapértelmezetten HTTPS-t használ. Titkosítatlan HTTP-t csak otthoni (helyi hálózati) címekre enged, nyilvános címre csak a te kifejezett jóváhagyásoddal. HTTP-n a hálózaton lévő mások a forgalmat (a jelszót is) láthatják, ezért nyilvános hálózaton használj HTTPS-t.

### Gyermekek

Az app nem gyerekeknek készült, és nem gyűjt személyes adatot senkitől.

### Változások

Ha ez a nyilatkozat változik, a módosítás ide kerül, az „Utolsó frissítés" dátumával.

### Kapcsolat

Kérdés vagy észrevétel esetén nyiss egy Issue-t ebben a GitHub-tárhelyen.

---

## English

**In short:** the app does not collect any data about you and sends nothing to the developer or to any third party. It contains no analytics, advertising, crash reporting or tracking.

### What the app does with data

- **Your own AdGuard Home server.** The app only communicates with the server(s) whose address you enter. It sends your login details there and downloads statistics, the query log, filters, clients and settings from it. This data travels between your server and your phone and never reaches the developer.
- **Data stored on your phone.** The server address, username, app settings and (unless you turn saving off) the password are stored on your device. Passwords are encrypted with a key protected by the Android Keystore. App backup (`allowBackup`) is disabled, so this data is not included in your Google account backup. Uninstalling the app deletes all stored data.
- **Filter list registry.** When you open the screen for adding filter lists, the app downloads AdGuard's public list registry (`adguardteam.github.io`). That server can see your phone's IP address, as with any web request. The app sends no identifier with it.
- **External browser.** If you open a domain from the log in your browser, it is opened by your default Android browser, which has its own privacy policy.

### Permissions

- **Internet:** to communicate with the server you specify and to download the registry above.

The app requests no other permissions. It has no access to contacts, location, photos, microphone or camera.

### Unencrypted (HTTP) connections

The app uses HTTPS by default. It allows unencrypted HTTP only for home (local network) addresses, and for public addresses only with your explicit confirmation. Over HTTP, others on the network may see the traffic, including your password, so use HTTPS on public networks.

### Children

The app is not directed at children and collects no personal data from anyone.

### Changes

If this policy changes, the update will be published here with a new "Last updated" date.

### Contact

For questions or comments, open an Issue in this GitHub repository.
