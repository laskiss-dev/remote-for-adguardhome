package hu.laskiss.remoteforadguardhome;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Beállítási útmutató: a szerver DNS-címei és eszközönkénti beállítási lépések, a webes felület szerint. */
public class GuideActivity extends BaseActivity {

    static final String[] TABS = {"Router", "Windows", "macOS", "Android", "iOS", "DNS adatvédelem"};

    static final String[][] STEPS = {
            {"Nyisd meg a router beállításait. Ez általában böngészőben, egy cím megadásával történik (pl. http://192.168.0.1/ "
                    + "vagy http://192.168.1.1/), és felhasználónevet meg jelszót kér. Ha nem tudod a belépési adatokat, nézd meg "
                    + "a router dobozát, az alján lévő címkét vagy a leírását. Végső esetben visszaállíthatod a routert, de így "
                    + "valószínűleg minden beállítása elvész. Ha a routerhez alkalmazás kell, telepítsd a telefonra vagy a gépre, "
                    + "és azzal érd el a beállításokat.",
                    "Keresd meg a DHCP/DNS beállításokat: a DNS szót egy olyan mező mellett, amely négy, 1–3 számjegyű csoportból "
                            + "álló számsort vár.",
                    "Add meg itt az AdGuard Home szerver címét.",
                    "Egyes routereken nem állítható be egyéni DNS-szerver. Ilyenkor segíthet, ha az AdGuard Home-ot DHCP-szerverként "
                            + "állítod be; egyébként nézd meg a router kézikönyvében, hogyan szabhatók testre a DNS-szerverek."},
            {"Nyisd meg a Vezérlőpultot a Start menüből vagy a Windows keresővel.",
                    "Válaszd a Hálózat és internet kategóriát, majd a Hálózati és megosztási központot.",
                    "A bal oldali panelen kattints az „Adapterbeállítások módosítása” lehetőségre.",
                    "Kattints jobb gombbal az aktív kapcsolatra, és válaszd a Tulajdonságokat.",
                    "Keresd meg a listában az Internet Protocol Version 4 (TCP/IPv4) elemet, jelöld ki, és kattints ismét a Tulajdonságokra.",
                    "Válaszd a „Következő DNS-kiszolgálócímek használata” lehetőséget, és add meg az AdGuard Home szerver címeit."},
            {"Kattints az Apple ikonra, és válaszd a Rendszerbeállításokat.",
                    "Kattints a Hálózat lehetőségre.",
                    "Válaszd ki a listában az első kapcsolatot, és kattints a Haladó beállításokra.",
                    "Válaszd a DNS fület, és add meg az AdGuard Home szerver címeit."},
            {"Az Android kezdőképernyőjén koppints a Beállítások ikonra.",
                    "Koppints a Wi-Fi menüpontra. Megjelenik az összes elérhető hálózat (mobilinternethez nem adható meg egyéni DNS).",
                    "Nyomd hosszan azt a hálózatot, amelyikre csatlakozva vagy, és válaszd a Hálózat módosítása lehetőséget.",
                    "Egyes eszközökön a további beállításokhoz meg kell nyitni a Speciális/haladó beállításokat. A DNS módosításához "
                            + "az IP-beállításokat DHCP-ről statikusra kell váltani.",
                    "Írd át a DNS 1 és DNS 2 értékét az AdGuard Home szerver címeire."},
            {"A kezdőképernyőn koppints a Beállítások ikonra.",
                    "Válaszd a Wi-Fi menüpontot (mobilinternethez nem állítható be DNS).",
                    "Koppints a használt hálózat nevére.",
                    "A DNS mezőbe írd be az AdGuard Home szerver címeit."},
    };

    static final String ROUTER_DESC = "Ez a beállítás automatikusan érvényes minden eszközre, amely az otthoni routeredhez "
            + "csatlakozik, nem kell őket egyenként beállítani.";

    private List<String> addresses = new ArrayList<>();
    private LinearLayout tabBody;
    private int tab;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage("Beállítási útmutató");
        load();
    }

    @Override
    void load() {
        loading();
        task(() -> Net.api.getJson("status"), this::render);
    }

    private void render(JSONObject st) {
        loaded();
        addresses = addresses(st);

        LinearLayout s = section("Állítsd be az eszközeidet",
                "Az AdGuard Home használatához be kell állítanod az eszközeidet, hogy ezt a DNS-szervert használják.");
        TextView l = text("Az AdGuard DNS-szerver a következő címeken figyel (koppints a másoláshoz):", 13, MUTED);
        l.setPadding(0, dp(10), 0, dp(4));
        s.addView(l);
        if (addresses.isEmpty()) emptyNote(s, "A szerver nem adott vissza címet.");
        for (final String a : addresses) {
            TextView t = text(a, 15, TEXT);
            t.setTypeface(Typeface.MONOSPACE);
            t.setPadding(0, dp(6), 0, dp(6));
            t.setClickable(true);
            t.setFocusable(true);
            t.setOnClickListener(v -> copy(a));
            s.addView(t);
        }

        LinearLayout g = section("Útmutató", null);
        final Choice c = new Choice(g, "Eszköz", TABS, tab);
        tabBody = col();
        g.addView(tabBody, matchWrap());
        c.onChange = () -> {
            tab = c.sel;
            showTab();
        };
        showTab();
    }

    /** A szerver címei a status válaszból (dns_addresses). */
    static List<String> addresses(JSONObject st) {
        ArrayList<String> out = new ArrayList<>();
        JSONArray a = st.optJSONArray("dns_addresses");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    static String first(List<String> l, String prefix) {
        for (String s : l) if (s.startsWith(prefix)) return s;
        return null;
    }

    private void showTab() {
        tabBody.removeAllViews();
        if (tab < STEPS.length) {
            if (tab == 0) para(ROUTER_DESC, MUTED);
            String[] st = STEPS[tab];
            for (int i = 0; i < st.length; i++) step(i + 1, st[i]);
            return;
        }
        String tls = first(addresses, "tls://");
        String https = first(addresses, "https://");
        if (tls != null) para("DNS-over-TLS: használd ezt a címet: " + tls, TEXT);
        if (https != null) para("DNS-over-HTTPS: használd ezt a címet: " + https, TEXT);
        if (tls == null && https == null) {
            para("A DNS-over-HTTPS vagy a DNS-over-TLS használatához be kell állítanod a titkosítást az AdGuard Home "
                    + "beállításaiban.", TEXT);
            button(buttonRow(tabBody), "Titkosítási beállítások", 0, v -> open(EncryptionActivity.class, 0));
            return;
        }
        para("Programok, amelyeket használhatsz:", MUTED);
        head("Android");
        bullet("Az Android 9 és újabb beépítetten támogatja a DNS-over-TLS-t: Beállítások → Hálózat és internet → "
                + "Speciális → Privát DNS, és itt add meg a domaint.");
        bullet("Az AdGuard for Android támogatja a DNS-over-HTTPS-t és a DNS-over-TLS-t.");
        bullet("Az Intra DNS-over-HTTPS támogatást ad az Androidhoz.");
        head("iOS");
        bullet("Az AdGuard for iOS támogatja a DNS-over-HTTPS és a DNS-over-TLS beállításokat.");
        bullet("A DNSCloak támogatja a DNS-over-HTTPS-t, de a saját szerveredhez DNS-bélyeget (DNS stamp) kell készítened "
                + "(dnscrypt.info/stamps).");
        head("Egyéb megvalósítások");
        bullet("Maga az AdGuard Home bármilyen platformon lehet biztonságos DNS-kliens.");
        bullet("A dnsproxy támogatja az összes ismert biztonságos DNS-protokollt.");
        bullet("A dnscrypt-proxy támogatja a DNS-over-HTTPS-t.");
        bullet("A Mozilla Firefox támogatja a DNS-over-HTTPS-t.");
        bullet("További megvalósítások: dnscrypt.info/implementations és dnsprivacy.org (DNS Privacy Clients).");
        head("iOS és macOS konfiguráció");
        para("iOS 14 és macOS Big Sur óta egy „.mobileconfig” fájllal hozzáadható a DNS-over-HTTPS vagy DNS-over-TLS szerver "
                + "a DNS-beállításokhoz. A fájlt az AdGuard Home webes felületén, a Beállítási útmutató oldalon töltheted le.", TEXT);
    }

    private void para(String s, int color) {
        TextView t = text(s, 14, color);
        t.setPadding(0, dp(10), 0, 0);
        tabBody.addView(t);
    }

    private void head(String s) {
        TextView t = text(s, 15, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, 0);
        tabBody.addView(t);
    }

    private void bullet(String s) {
        TextView t = text("•  " + s, 14, TEXT);
        t.setPadding(dp(4), dp(6), 0, 0);
        tabBody.addView(t);
    }

    private void step(int n, String s) {
        LinearLayout r = row();
        r.setGravity(android.view.Gravity.TOP);
        r.setPadding(0, dp(10), 0, 0);
        TextView num = text(n + ".", 14, GREEN);
        num.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(num, new LinearLayout.LayoutParams(dp(26), LinearLayout.LayoutParams.WRAP_CONTENT));
        r.addView(text(s, 14, TEXT), weight(1));
        tabBody.addView(r, matchWrap());
    }

    private void copy(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("dns", s));
        Toast.makeText(this, "Másolva: " + s, Toast.LENGTH_SHORT).show();
    }
}
