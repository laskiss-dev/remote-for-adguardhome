package hu.laskiss.remoteforadguardhome;

import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/** Információ: a program neve, verziója, készítője, a szerver adatai és a változásnapló. */
public class InfoActivity extends BaseActivity {

    /** Változásnapló, a legújabb elöl: verzió, dátum, változások. */
    static final String[][] CHANGES = {
            {"1.9.3", "2026. 10. 31.",
                    "Új név: Remote for AdGuard Home (korábban AdguardHome-remote)",
                    "Új alkalmazásazonosító és új aláírókulcs; a korábbi változat mellé külön appként települ"},
            {"1.9.2", "2026. 10. 31.",
                    "Licenc: GNU GPL 3. verzió, a teljes szöveg az Információ oldalon olvasható",
                    "Az Információ oldalon jelölve, hogy ez független, nem hivatalos alkalmazás"},
            {"1.9.1", "2026. 10. 31.",
                    "Irányítópult: kisebb számok az Általános statisztikákban",
                    "A toplisták szövege nem lóg bele a görgetősávba"},
            {"1.9", "2026. 10. 31.",
                    "Új, lapos, vektoros alkalmazásikon: ház (otthoni hálózat), szerver és védőpajzs",
                    "Kisebb APK, az ikon minden méretben éles"},
            {"1.8", "2026. 10. 31.",
                    "Irányítópult a webes felület szerint: grafikonok, Általános statisztikák, majd a toplisták",
                    "A toplisták fix magasságú, görgethető dobozokban, így az oldal rövidebb",
                    "A grafikonokra és a statisztikákra koppintva a napló az adott szűrővel nyílik"},
            {"1.7", "2026. 10. 31.",
                    "Lekérdezési napló: görgetéskor a cím, a kereső és a szűrő felcsúszik, mint a webes felületen",
                    "Lejjebb görgetve a jobb alsó sarokban megjelenik egy gomb, amivel egyből a lista tetejére ugorhatsz"},
            {"1.6", "2026. 10. 29.",
                    "Új alkalmazásikon",
                    "Az APK fájl neve: AdguardHome-remote"},
            {"1.5", "2026. 10. 29.",
                    "Új Alkalmazás beállítások oldal: sötét, világos vagy a rendszer szerinti téma",
                    "Képernyő ébren tartása, amíg az app előtérben van",
                    "Javítva: a menüből megnyitott lekérdezési napló nem tartja meg az Irányítópultról indított keresést"},
            {"1.4", "2026. 10. 29.",
                    "A lekérdezési naplóban a második sorral együtt a dátum is megjelenik az idő alatt",
                    "Blokkolási lista hozzáadása a webes felülethez hasonlóan: választás a listából vagy egyedi lista",
                    "A Szűrők és a Beállítások menüpontjai a webes felület elnevezéseivel és sorrendjében"},
            {"1.3", "2026. 10. 29.",
                    "Új név: AdguardHome-remote",
                    "Az Irányítópult lett a kezdőképernyő",
                    "A lekérdezési napló szűrője új sorrendben és új elnevezésekkel",
                    "Új Információ oldal változásnaplóval"},
            {"1.2", "2026. 10. 29.",
                    "A lekérdezési naplóban a legutóbbi frissítéssel érkezett sorok zölddel látszanak",
                    "A kiemelés a menüből kikapcsolható"},
            {"1.1", "2026. 10. 28.",
                    "Irányítópult statisztikákkal és toplistákkal",
                    "Szűrők: blokkolási és engedélyezési listák, egyéni szabályok, DNS átírások, blokkolt szolgáltatások",
                    "Kliens beállítások és hozzáférési listák",
                    "Általános, DNS és DHCP beállítások",
                    "Közös menü a webes felület oldalsávja szerint"},
            {"1.0", "2026. 10. 28.",
                    "Lekérdezési napló álló és fekvő nézettel",
                    "Automatikus frissítés, amely csak az új sorokat tölti le",
                    "Keresés és szűrés a szerveren, blokkolás és feloldás egy koppintással",
                    "Védelem be- és kikapcsolása, szüneteltetése"},
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage("Információ");
        load();
    }

    @Override
    void load() {
        content.removeAllViews();
        setPageStatus("", false);

        LinearLayout app = section("Remote for AdGuard Home", null);
        kv(app, "Verzió", versionName());
        kv(app, "Készítette", "Kiss László");
        TextView about = text("A saját AdGuard Home szervered kezelése telefonról: lekérdezési napló, statisztika, "
                + "szűrők, kliensek és beállítások.", 13, MUTED);
        about.setPadding(0, dp(8), 0, 0);
        app.addView(about);
        TextView indep = text("Nem hivatalos alkalmazás. Független fejlesztés: nem áll kapcsolatban az AdGuard Home "
                + "fejlesztőivel, az AdGuard csapatával, és nem ők készítették vagy támogatják. "
                + "Az „AdGuard” név az AdGuard védjegye, itt csak annak jelzésére szerepel, hogy az app mihez készült.", 13, MUTED);
        indep.setPadding(0, dp(8), 0, 0);
        app.addView(indep);

        final LinearLayout srv = section("Szerver", null);
        kv(srv, "Cím", Net.api == null ? "nincs megadva" : Net.api.host);
        final TextView ver = text("…", 14, TEXT);
        LinearLayout line = row();
        line.setPadding(0, dp(4), 0, dp(4));
        line.addView(text("AdGuard Home", 14, LABEL), new LinearLayout.LayoutParams(dp(130), -2));
        line.addView(ver, weight(1));
        srv.addView(line, matchWrap());
        if (Net.api != null) {
            task(() -> Net.api.getJson("status"), (JSONObject r) -> ver.setText(r.optString("version", "ismeretlen")));
        } else {
            ver.setText("–");
        }

        LinearLayout lic = section("Licenc", null);
        kv(lic, "Licenc", "GNU GPL 3. verzió (GPLv3)");
        kv(lic, "Szerzői jog", "© 2026 Kiss László");
        TextView notice = text("Ez a program szabad szoftver: a Free Software Foundation által kiadott GNU General Public "
                + "License 3. verziója szerint terjesztheted és módosíthatod. A programot abban a reményben adom közre, "
                + "hogy hasznos lesz, de minden garancia nélkül, beleértve az eladhatóságra vagy egy adott célra való "
                + "alkalmasságra vonatkozó hallgatólagos garanciát is. A részleteket a licenc teljes szövege tartalmazza.", 13, MUTED);
        notice.setPadding(0, dp(8), 0, 0);
        lic.addView(notice);
        button(buttonRow(lic), "Teljes licencszöveg (angol)", 0, v -> showLicense());

        LinearLayout log = section("Változásnapló", null);
        for (int i = 0; i < CHANGES.length; i++) {
            String[] v = CHANGES[i];
            if (i > 0) divider(log);
            LinearLayout c = col();
            c.setPadding(0, dp(10), 0, dp(8));
            TextView h = text(v[0] + "  ·  " + v[1], 15, TEXT);
            h.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(h);
            for (int j = 2; j < v.length; j++) {
                TextView t = text("•  " + v[j], 14, SUB);
                t.setPadding(dp(4), dp(3), 0, 0);
                c.addView(t);
            }
            log.addView(c, matchWrap());
        }
    }

    /** A GPLv3 teljes, eredeti angol szövege az app csomagjából (assets/LICENSE.txt). */
    private void showLicense() {
        String text;
        try {
            java.io.InputStream in = getAssets().open("LICENSE.txt");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            text = bo.toString("UTF-8");
        } catch (Exception e) {
            text = "A licenc szövege nem olvasható. Elérhető itt: https://www.gnu.org/licenses/gpl-3.0.html";
        }
        TextView t = text(text, 11, TEXT);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(dp(18), dp(8), dp(18), dp(8));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(t);
        new android.app.AlertDialog.Builder(this)
                .setTitle("GNU General Public License v3")
                .setView(sv)
                .setPositiveButton("Bezárás", null)
                .show();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return CHANGES[0][0];
        }
    }
}
