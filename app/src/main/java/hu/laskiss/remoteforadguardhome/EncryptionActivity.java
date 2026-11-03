package hu.laskiss.remoteforadguardhome;

import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Titkosítási beállítások: HTTPS, DNS-over-HTTPS/TLS/QUIC, tanúsítvány és privát kulcs,
 * a webes felület Titkosítási beállítások oldalának megfelelően (tls/status, tls/validate, tls/configure).
 */
public class EncryptionActivity extends BaseActivity {

    private JSONObject orig;
    private Switch enabled, plainDns, forceHttps, useSavedKey;
    private EditText serverName, portHttps, portDot, portDoq, certPath, certText, keyPath, keyText;
    private Choice certSource, keySource;
    private LinearLayout certStatus, keyStatus, warning, savedKeyBox;
    private boolean keyWasSaved;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage("Titkosítási beállítások");
        load();
    }

    @Override
    void load() {
        loading();
        task(() -> Net.api.getJson("tls/status"), this::render);
    }

    private void render(JSONObject st) {
        loaded();
        orig = st;

        LinearLayout t = section("Titkosítás",
                "Titkosítás (HTTPS/QUIC/TLS) támogatása a DNS és a webes admin felület számára.");
        enabled = switchRow(t, "Titkosítás engedélyezése (HTTPS, DNS-over-HTTPS és DNS-over-TLS)",
                "Bekapcsolva az admin felület HTTPS-en is működik, a DNS szerver pedig DNS-over-HTTPS-en "
                        + "és DNS-over-TLS-en is fogadja a kéréseket.",
                st.optBoolean("enabled", false));
        if (st.has("serve_plain_dns")) {
            plainDns = switchRow(t, "Egyszerű DNS engedélyezése",
                    "Alapból be van kapcsolva. Kikapcsolva minden eszköz titkosított DNS-t kénytelen használni; "
                            + "ehhez legalább egy titkosított DNS protokollnak engedélyezve kell lennie.",
                    st.optBoolean("serve_plain_dns", true));
        }

        LinearLayout s = section("Szerver és portok", null);
        serverName = field(s, "Szerver neve", "pl. dns.example.org", st.optString("server_name", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        note(s, "Ha meg van adva, az AdGuard Home felismeri a ClientID-ket, válaszol a DDR-lekérdezésekre és további "
                + "kapcsolatellenőrzéseket végez. Egyeznie kell a tanúsítvány egyik DNS-nevével.");
        forceHttps = switchRow(s, "Automatikus átirányítás HTTPS kapcsolatra",
                "Bekapcsolva az AdGuard Home a HTTP kapcsolatokat automatikusan HTTPS-re irányítja.",
                st.optBoolean("force_https", false));
        portHttps = port(s, "HTTPS port", st.optInt("port_https", 443));
        note(s, "Ezen a porton érhető el az admin felület HTTPS-en, és itt működik a DNS-over-HTTPS is (/dns-query). 0 = kikapcsolva.");
        portDot = port(s, "DNS-over-TLS port", st.optInt("port_dns_over_tls", 853));
        note(s, "Ha meg van adva, az AdGuard Home ezen a porton DNS-over-TLS szerverként is fut. 0 = kikapcsolva.");
        portDoq = port(s, "DNS-over-QUIC port", st.optInt("port_dns_over_quic", 853));
        note(s, "Ha meg van adva, az AdGuard Home ezen a porton DNS-over-QUIC szerverként is fut. 0 = kikapcsolva.");

        LinearLayout c = section("Tanúsítványok",
                "A titkosításhoz érvényes SSL-tanúsítványlánc kell a domainedhez. Ingyenes tanúsítványt a "
                        + "letsencrypt.org oldalon kaphatsz, vagy vásárolhatsz egy megbízható kibocsátótól.");
        String chain = decode(st.optString("certificate_chain", ""));
        certSource = new Choice(c, "Forrás", new String[]{"Tanúsítványfájl útvonalának megadása", "Tanúsítvány tartalmának megadása"},
                chain.isEmpty() ? 0 : 1);
        certPath = field(c, "Tanúsítvány útvonala", "pl. /ssl/fullchain.pem", st.optString("certificate_path", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        certText = area(c, null, "Másold be ide a PEM-kódolt tanúsítványt.", chain, 4);
        certStatus = col();
        c.addView(certStatus, matchWrap());
        certSource.onChange = this::syncSources;

        LinearLayout k = section("Privát kulcs", null);
        keyWasSaved = st.optBoolean("private_key_saved", false);
        keySource = new Choice(k, "Forrás", new String[]{"Privát kulcsfájl útvonalának beállítása", "Privát kulcs tartalmának megadása"},
                keyWasSaved || !st.optString("private_key", "").isEmpty() ? 1 : 0);
        keyPath = field(k, "Privát kulcs útvonala", "pl. /ssl/privkey.pem", st.optString("private_key_path", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        savedKeyBox = col();
        k.addView(savedKeyBox, matchWrap());
        useSavedKey = keyWasSaved ? switchRow(savedKeyBox, "Előzőleg mentett kulcs használata",
                "A szerveren tárolt kulcs marad; nem kell újra bemásolnod.", true) : null;
        keyText = area(k, null, "Másold be ide a tanúsítványod PEM-kódolt privát kulcsát.", "", 4);
        keyStatus = col();
        k.addView(keyStatus, matchWrap());
        keySource.onChange = this::syncSources;
        if (useSavedKey != null) useSavedKey.setOnCheckedChangeListener((v, on) -> syncSources());

        LinearLayout a = section("Ellenőrzés és mentés", null);
        warning = col();
        a.addView(warning, matchWrap());
        LinearLayout br = buttonRow(a);
        button(br, "Konfiguráció mentése", 1, v -> saveConfig());
        button(br, "Ellenőrzés", 0, v -> validate());
        button(buttonRow(a), "Beállítások visszaállítása", 2, v -> confirm("Beállítások visszaállítása",
                "Biztosan visszaállítod a titkosítási beállításokat? A titkosítás kikapcsol, a tanúsítvány és a kulcs törlődik.",
                "Visszaállítás", this::resetConfig));

        syncSources();
        showStatus(st);
    }

    private EditText port(LinearLayout parent, String label, int value) {
        return field(parent, label, "0", value > 0 ? String.valueOf(value) : "", InputType.TYPE_CLASS_NUMBER);
    }

    private void note(LinearLayout parent, String s) {
        TextView d = text(s, 12, MUTED);
        d.setPadding(0, dp(2), 0, dp(4));
        parent.addView(d);
    }

    private void syncSources() {
        boolean certContent = certSource.sel == 1;
        certPath.setVisibility(certContent ? View.GONE : View.VISIBLE);
        certText.setVisibility(certContent ? View.VISIBLE : View.GONE);
        boolean keyContent = keySource.sel == 1;
        keyPath.setVisibility(keyContent ? View.GONE : View.VISIBLE);
        savedKeyBox.setVisibility(keyContent && useSavedKey != null ? View.VISIBLE : View.GONE);
        boolean saved = useSavedKey != null && useSavedKey.isChecked();
        keyText.setVisibility(keyContent && !saved ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- kérés összeállítása

    static String decode(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        try {
            return new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    static String encode(String s) {
        return s.isEmpty() ? "" : Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static int portValue(EditText e) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException x) {
            return -1;
        }
    }

    private static final String[] STATUS_FIELDS = {"valid_cert", "valid_chain", "valid_key", "valid_pair", "subject",
            "issuer", "not_before", "not_after", "dns_names", "key_type", "warning_validation"};

    /** A beírt értékekből a tls/validate és tls/configure kérés teste; a nem látható mezők (pl. DNSCrypt) megmaradnak. */
    JSONObject body() throws Exception {
        JSONObject b = copy(orig);
        for (String f : STATUS_FIELDS) b.remove(f);
        b.put("enabled", enabled.isChecked());
        if (plainDns != null) b.put("serve_plain_dns", plainDns.isChecked());
        b.put("server_name", serverName.getText().toString().trim());
        b.put("force_https", forceHttps.isChecked());
        b.put("port_https", Math.max(0, portValue(portHttps)));
        b.put("port_dns_over_tls", Math.max(0, portValue(portDot)));
        b.put("port_dns_over_quic", Math.max(0, portValue(portDoq)));
        if (certSource.sel == 0) {
            b.put("certificate_chain", "").put("certificate_path", certPath.getText().toString().trim());
        } else {
            b.put("certificate_path", "").put("certificate_chain", encode(certText.getText().toString().trim()));
        }
        if (keySource.sel == 0) {
            b.put("private_key", "").put("private_key_saved", false).put("private_key_path", keyPath.getText().toString().trim());
        } else if (useSavedKey != null && useSavedKey.isChecked()) {
            b.put("private_key_path", "").put("private_key", "").put("private_key_saved", true);
        } else {
            b.put("private_key_path", "").put("private_key_saved", false)
                    .put("private_key", encode(keyText.getText().toString().trim()));
        }
        return b;
    }

    /** Helyi ellenőrzés a webes felület szabályai szerint; hiba esetén a hibaüzenet, különben null. */
    String localError() {
        int[] ports = {portValue(portHttps), portValue(portDot), portValue(portDoq)};
        for (int p : ports) if (p < 0 || p > 65535) return "A port 0 és 65535 között lehet.";
        if (ports[0] > 0 && ports[0] == ports[1]) return "A HTTPS és a DNS-over-TLS port nem egyezhet.";
        if (plainDns != null && !plainDns.isChecked() && !enabled.isChecked()) {
            return "Az egyszerű DNS letiltásához engedélyezz legalább egy titkosított DNS protokollt.";
        }
        return null;
    }

    static boolean validPair(JSONObject r) {
        return r.optBoolean("valid_cert", false) && r.optBoolean("valid_key", false) && r.optBoolean("valid_pair", false);
    }

    // ---------------------------------------------------------------- műveletek

    private void validate() {
        final JSONObject b;
        try {
            b = body();
        } catch (Exception e) {
            return;
        }
        setPageStatus("Ellenőrzés…", false);
        task(() -> new JSONObject(Net.api.post("tls/validate", b)), r -> {
            setPageStatus("", false);
            showStatus(r);
        });
    }

    private void saveConfig() {
        String err = localError();
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            return;
        }
        final JSONObject b;
        try {
            b = body();
        } catch (Exception e) {
            return;
        }
        StringBuilder msg = new StringBuilder("Elmented a titkosítási beállításokat?");
        String risk = connectionRisk(b);
        if (risk != null) msg.append("\n\n").append(risk);
        msg.append("\n\nHibás beállítás esetén a webes felület és az app is elérhetetlenné válhat.");
        confirm("Konfiguráció mentése", msg.toString(), "Mentés", () -> {
            setPageStatus("Mentés…", false);
            task(() -> {
                if (b.optBoolean("enabled", false)) {
                    JSONObject v = new JSONObject(Net.api.post("tls/validate", b));
                    if (!validPair(v)) return new Object[]{false, v};
                }
                JSONObject r = new JSONObject(Net.api.post("tls/configure", b));
                return new Object[]{!b.optBoolean("enabled", false) || validPair(r), r};
            }, res -> {
                setPageStatus("", false);
                boolean ok = (Boolean) res[0];
                if (!ok) {
                    showStatus((JSONObject) res[1]);
                    Toast.makeText(this, "A tanúsítvány vagy a kulcs hibás, a beállítás nem mentődött.", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, "Titkosítási beállítások mentve", Toast.LENGTH_SHORT).show();
                load();
            });
        });
    }

    private void resetConfig() {
        final JSONObject b;
        try {
            b = copy(orig);
            for (String f : STATUS_FIELDS) b.remove(f);
            b.put("enabled", false).put("serve_plain_dns", true).put("server_name", "").put("force_https", false)
                    .put("port_https", 443).put("port_dns_over_tls", 853).put("port_dns_over_quic", 853)
                    .put("certificate_chain", "").put("private_key", "").put("certificate_path", "")
                    .put("private_key_path", "").put("private_key_saved", false);
        } catch (Exception e) {
            return;
        }
        save(() -> Net.api.post("tls/configure", b), "Titkosítási beállítások visszaállítva");
    }

    /** Figyelmeztetés, ha a mentés után az app a jelenlegi címen nem érné el a szervert. */
    String connectionRisk(JSONObject b) {
        Address a = Net.api == null ? null : Net.api.address;
        if (a == null) return null;
        boolean en = b.optBoolean("enabled", false);
        int https = b.optInt("port_https", 0);
        if (en && b.optBoolean("force_https", false) && !a.https()) {
            return "Az app most HTTP-n kapcsolódik. Az átirányítás bekapcsolása után a Szerverek menüben át kell állítanod "
                    + "HTTPS-re" + (https > 0 ? " és a(z) " + https + " portra" : "") + ", különben nem éri el a szervert.";
        }
        if (a.https() && (!en || https == 0)) {
            return "Az app most HTTPS-en kapcsolódik. Ha a titkosítás vagy a HTTPS port kikapcsol, a Szerverek menüben "
                    + "HTTP-re kell váltanod a kapcsolatot.";
        }
        return null;
    }

    // ---------------------------------------------------------------- állapot megjelenítése

    void showStatus(JSONObject r) {
        certStatus.removeAllViews();
        keyStatus.removeAllViews();
        warning.removeAllViews();
        boolean hasCert = certSource.sel == 0 ? !certPath.getText().toString().trim().isEmpty()
                : !certText.getText().toString().trim().isEmpty();
        if (hasCert && r.has("valid_chain")) {
            statusTitle(certStatus);
            boolean chain = r.optBoolean("valid_chain", false);
            certStatus.addView(text(chain ? "✓ A tanúsítványlánc érvényes" : "✗ A tanúsítványlánc érvénytelen", 14,
                    chain ? OK_TEXT : BAD_TEXT));
            if (r.optBoolean("valid_cert", false)) {
                if (!r.optString("subject", "").isEmpty()) kv(certStatus, "Tárgy", r.optString("subject", ""));
                if (!r.optString("issuer", "").isEmpty()) kv(certStatus, "Kibocsátó", r.optString("issuer", ""));
                String na = r.optString("not_after", "");
                if (!na.isEmpty() && !na.startsWith("0001-")) kv(certStatus, "Lejár", FilterListsActivity.date(na));
                JSONArray dn = r.optJSONArray("dns_names");
                if (dn != null && dn.length() > 0) kv(certStatus, "Hosztnevek", join(dn).replace("\n", ", "));
            }
        }
        boolean hasKey = keySource.sel == 0 ? !keyPath.getText().toString().trim().isEmpty()
                : (useSavedKey != null && useSavedKey.isChecked()) || !keyText.getText().toString().trim().isEmpty();
        if (hasKey && r.has("valid_key")) {
            statusTitle(keyStatus);
            boolean vk = r.optBoolean("valid_key", false);
            String type = r.optString("key_type", "");
            String ty = type.isEmpty() ? "" : type + " ";
            keyStatus.addView(text(vk ? "✓ Ez egy érvényes " + ty + "privát kulcs" : "✗ Ez egy érvénytelen " + ty + "privát kulcs",
                    14, vk ? OK_TEXT : BAD_TEXT));
        }
        String w = r.optString("warning_validation", "");
        if (!w.isEmpty()) {
            boolean ok = validPair(r);
            TextView tw = text(ok ? "Figyelmeztetés: " + w : w, 14, ok ? MUTED : BAD_TEXT);
            tw.setPadding(0, dp(8), 0, 0);
            warning.addView(tw);
        }
    }

    private void statusTitle(LinearLayout parent) {
        TextView h = text("Állapot", 13, MUTED);
        h.setPadding(0, dp(10), 0, dp(2));
        parent.addView(h);
    }
}
