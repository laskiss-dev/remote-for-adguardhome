package hu.laskiss.remoteforadguardhome;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

/** DNS beállítások. Csak azokat a mezőket mutatja, amelyeket a szerver verziója ismer. */
public class DnsActivity extends BaseActivity {

    static final String[] MODES = {"load_balance", "parallel", "fastest_addr"};
    static final String[] MODE_LABELS = {"Terheléselosztás", "Párhuzamos kérések", "Leggyorsabb IP-cím"};
    static final String[] BLOCK = {"default", "refused", "nxdomain", "null_ip", "custom_ip"};
    static final String[] BLOCK_LABELS = {"Alapértelmezett", "REFUSED", "NXDOMAIN", "Null IP (0.0.0.0)", "Egyéni IP"};

    private JSONObject info;

    /** Egy mező és az, hogyan kerül vissza a JSON-ba. */
    private interface Getter { Object get(); }

    private final class Group {
        final ArrayList<String> keys = new ArrayList<>();
        final ArrayList<Getter> getters = new ArrayList<>();
        final LinearLayout box;

        Group(LinearLayout box) { this.box = box; }

        void list(String key, String label, String hint, int lines) {
            if (!info.has(key)) return;
            final EditText e = area(box, label, hint, join(info.optJSONArray(key)), lines);
            add(key, () -> arrOf(e.getText().toString()));
        }

        void bool(String key, String label, String desc) {
            if (!info.has(key)) return;
            final Switch s = switchRow(box, label, desc, info.optBoolean(key, false));
            add(key, s::isChecked);
        }

        void number(String key, String label) {
            if (!info.has(key)) return;
            final long def = info.optLong(key, 0);
            final EditText e = DnsActivity.this.number(box, label, def);
            add(key, () -> num(e, def));
        }

        void textField(String key, String label, String hint) {
            if (!info.has(key)) return;
            final EditText e = field(box, label, hint, info.optString(key, ""), android.text.InputType.TYPE_CLASS_TEXT);
            add(key, () -> e.getText().toString().trim());
        }

        void choice(String key, String label, final String[] values, String[] labels, int fallback) {
            if (!info.has(key)) return;
            String cur = info.optString(key, "");
            int sel = fallback;
            for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) sel = i;
            final Choice c = new Choice(box, label, labels, sel);
            add(key, () -> values[c.sel]);
        }

        void add(String k, Getter g) {
            keys.add(k);
            getters.add(g);
        }

        JSONObject collect() throws Exception {
            JSONObject o = new JSONObject();
            for (int i = 0; i < keys.size(); i++) o.put(keys.get(i), getters.get(i).get());
            return o;
        }

        void saveButton(LinearLayout row) {
            button(row, "Mentés", 1, v -> {
                final JSONObject body;
                try {
                    body = collect();
                } catch (Exception e) {
                    return;
                }
                save(() -> Net.api.post("dns_config", body), "DNS beállítások mentve");
            });
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage("DNS beállítások");
        load();
    }

    @Override
    void load() {
        loading();
        task(() -> Net.api.getJson("dns_info"), this::render);
    }

    private void render(JSONObject r) {
        loaded();
        info = r;

        final Group up = new Group(section("Upstream DNS szerverek",
                "Soronként egy cím. Domain-specifikus upstream: [/example.local/]192.168.1.1"));
        up.list("upstream_dns", null, "https://dns10.quad9.net/dns-query", 6);
        up.choice("upstream_mode", "Lekérdezési mód", MODES, MODE_LABELS, 0);
        up.number("upstream_timeout", "Upstream időkorlát (másodperc)");
        LinearLayout ub = buttonRow(up.box);
        up.saveButton(ub);
        button(ub, "Tesztelés", 0, v -> testUpstreams(up));

        Group fb = new Group(section("Tartalék DNS szerverek", "Akkor használja, ha az upstreamek nem válaszolnak."));
        fb.list("fallback_dns", null, "pl. 9.9.9.10", 3);
        fb.saveButton(buttonRow(fb.box));

        Group bs = new Group(section("Bootstrap DNS szerverek", "Az upstreamek nevének feloldásához (sima IP-címek)."));
        bs.list("bootstrap_dns", null, "9.9.9.10", 3);
        bs.saveButton(buttonRow(bs.box));

        Group ptr = new Group(section("Privát fordított DNS", "Helyi IP-címekhez tartozó nevek (PTR) feloldása."));
        ptr.list("local_ptr_upstreams", null, "pl. 192.168.1.1 (üresen a rendszeré)", 2);
        ptr.bool("use_private_ptr_resolvers", "Privát fordított feloldók használata", null);
        ptr.bool("resolve_clients", "Kliensek IP-címeinek feloldása", "Nevet keres a kliensekhez a fordított DNS-sel.");
        ptr.saveButton(buttonRow(ptr.box));

        Group srv = new Group(section("DNS szerver", null));
        srv.number("ratelimit", "Lekérdezési korlát (kérés/másodperc kliensenként, 0 = nincs)");
        srv.list("ratelimit_whitelist", "Korlát alól kivett IP-címek", "soronként", 2);
        srv.bool("edns_cs_enabled", "EDNS kliens-alhálózat (ECS)", null);
        srv.bool("dnssec_enabled", "DNSSEC", null);
        srv.bool("disable_ipv6", "IPv6-címek (AAAA) feloldásának tiltása", null);
        srv.choice("blocking_mode", "Blokkolási mód", BLOCK, BLOCK_LABELS, 0);
        srv.textField("blocking_ipv4", "Egyéni IPv4-cím (Egyéni IP módhoz)", "pl. 0.0.0.0");
        srv.textField("blocking_ipv6", "Egyéni IPv6-cím (Egyéni IP módhoz)", "pl. ::");
        srv.number("blocked_response_ttl", "Blokkolt válasz élettartama (TTL, másodperc)");
        srv.saveButton(buttonRow(srv.box));

        Group cache = new Group(section("DNS gyorsítótár", null));
        cache.bool("cache_enabled", "Gyorsítótár használata", null);
        cache.number("cache_size", "Gyorsítótár mérete (bájt)");
        cache.number("cache_ttl_min", "Minimális TTL (másodperc)");
        cache.number("cache_ttl_max", "Maximális TTL (másodperc, 0 = nincs)");
        cache.bool("cache_optimistic", "Optimista gyorsítótár", "Lejárt bejegyzést is visszaad, közben frissíti.");
        LinearLayout cb = buttonRow(cache.box);
        cache.saveButton(cb);
        button(cb, "Gyorsítótár törlése", 2, v -> confirm("Gyorsítótár törlése", "Törlöd a DNS gyorsítótárat?", "Törlés",
                () -> apply(() -> Net.api.post("cache_clear", null), "Gyorsítótár törölve")));
    }

    private void testUpstreams(Group up) {
        final JSONObject body;
        try {
            body = up.collect();
            body.remove("upstream_mode");
            body.remove("upstream_timeout");
            if (info.has("bootstrap_dns")) body.put("bootstrap_dns", info.optJSONArray("bootstrap_dns"));
            if (info.has("fallback_dns")) body.put("fallback_dns", info.optJSONArray("fallback_dns"));
            if (info.has("local_ptr_upstreams")) body.put("private_upstream", info.optJSONArray("local_ptr_upstreams"));
        } catch (Exception e) {
            return;
        }
        setPageStatus("Upstreamek tesztelése…", false);
        task(() -> new JSONObject(Net.api.postSlow("test_upstream_dns", body)), r -> {
            setPageStatus("", false);
            StringBuilder sb = new StringBuilder();
            Iterator<String> it = r.keys();
            while (it.hasNext()) {
                String k = it.next();
                String v = r.optString(k, "");
                sb.append("OK".equalsIgnoreCase(v) ? "✓ " : "✗ ").append(k);
                if (!"OK".equalsIgnoreCase(v)) sb.append("\n    ").append(v);
                sb.append("\n\n");
            }
            new AlertDialog.Builder(this)
                    .setTitle("Teszt eredménye")
                    .setMessage(sb.length() == 0 ? "Nincs mit tesztelni." : sb.toString().trim())
                    .setPositiveButton("Rendben", null)
                    .show();
        });
    }
}
