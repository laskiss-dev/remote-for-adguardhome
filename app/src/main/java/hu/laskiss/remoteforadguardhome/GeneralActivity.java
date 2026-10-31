package hu.laskiss.remoteforadguardhome;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Általános beállítások: szűrés, biztonsági funkciók, napló és statisztika. */
public class GeneralActivity extends BaseActivity {

    static final long H = 3_600_000L, D = 24 * H;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage("Általános beállítások");
        load();
    }

    @Override
    void load() {
        loading();
        task(() -> {
            JSONObject[] r = new JSONObject[6];
            r[0] = Net.api.getJson("filtering/status");
            r[1] = opt("safebrowsing/status");
            r[2] = opt("parental/status");
            r[3] = opt("safesearch/status");
            r[4] = Net.api.getJson("querylog/config");
            r[5] = Net.api.getJson("stats/config");
            return r;
        }, this::render);
    }

    private static JSONObject opt(String path) {
        try {
            return Net.api.getJson(path);
        } catch (Exception e) {
            return null;
        }
    }

    private void render(JSONObject[] r) {
        loaded();

        // Szűrés
        LinearLayout f = section("Szűrés", null);
        final Switch fEn = switchRow(f, "Szűrés használata", "A blokkolási és engedélyezési listák alkalmazása.",
                r[0].optBoolean("enabled", true));
        final long[] upd = {0, 1, 12, 24, 72, 168};
        final String[] updL = {"Soha", "1 óránként", "12 óránként", "Naponta", "3 naponta", "Hetente"};
        int sel = 3;
        for (int i = 0; i < upd.length; i++) if (upd[i] == r[0].optLong("interval", 24)) sel = i;
        final Choice fInt = new Choice(f, "Szűrőlisták frissítése", updL, sel);
        button(buttonRow(f), "Mentés", 1, v -> save(() -> Net.api.post("filtering/config", new JSONObject()
                .put("enabled", fEn.isChecked()).put("interval", upd[fInt.sel])), "Szűrés mentve"));

        // Biztonság
        LinearLayout s = section("Védelmi funkciók", null);
        if (r[1] != null) {
            Switch sb = switchRow(s, "Biztonságos böngészés", "Az ismert kártékony és adathalász oldalak blokkolása.",
                    r[1].optBoolean("enabled", false));
            sb.setOnCheckedChangeListener((v, on) -> apply(() -> Net.api.post(on ? "safebrowsing/enable" : "safebrowsing/disable", null),
                    on ? "Biztonságos böngészés bekapcsolva" : "Biztonságos böngészés kikapcsolva"));
        }
        if (r[2] != null) {
            Switch pa = switchRow(s, "Szülői felügyelet", "Felnőtt tartalmú oldalak blokkolása.", r[2].optBoolean("enabled", false));
            pa.setOnCheckedChangeListener((v, on) -> apply(() -> Net.api.post(on ? "parental/enable" : "parental/disable", null),
                    on ? "Szülői felügyelet bekapcsolva" : "Szülői felügyelet kikapcsolva"));
        }
        if (r[3] != null) {
            final JSONObject ss = r[3];
            final Switch ssEn = switchRow(s, "Biztonságos keresés", "Kényszeríti a biztonságos keresést a kiválasztott szolgáltatásoknál.",
                    ss.optBoolean("enabled", false));
            final LinkedHashMap<String, Switch> engines = new LinkedHashMap<>();
            String[][] names = {{"google", "Google"}, {"bing", "Bing"}, {"duckduckgo", "DuckDuckGo"}, {"ecosia", "Ecosia"},
                    {"yandex", "Yandex"}, {"youtube", "YouTube"}, {"pixabay", "Pixabay"}};
            for (String[] n : names) {
                if (ss.has(n[0])) engines.put(n[0], switchRow(s, "   " + n[1], null, ss.optBoolean(n[0], true)));
            }
            button(buttonRow(s), "Keresés beállításainak mentése", 1, v -> save(() -> {
                JSONObject body = copy(ss).put("enabled", ssEn.isChecked());
                for (Map.Entry<String, Switch> e : engines.entrySet()) body.put(e.getKey(), e.getValue().isChecked());
                return Net.api.put("safesearch/settings", body);
            }, "Biztonságos keresés mentve"));
        }

        // Lekérdezési napló
        final JSONObject ql = r[4];
        LinearLayout q = section("Lekérdezési napló", null);
        final Switch qEn = switchRow(q, "Napló engedélyezése", null, ql.optBoolean("enabled", true));
        final Switch qAnon = switchRow(q, "Kliens IP-címek anonimizálása", null, ql.optBoolean("anonymize_client_ip", false));
        final long[] qv = {6 * H, D, 7 * D, 30 * D, 90 * D};
        final IntervalChoice qInt = new IntervalChoice(q, "Napló megőrzése", qv,
                new String[]{"6 óra", "1 nap", "7 nap", "30 nap", "90 nap"}, ql.optLong("interval", 90 * D));
        final Switch qIgnEn = ql.has("ignored_enabled") ? switchRow(q, "Kivételek használata", null, ql.optBoolean("ignored_enabled", true)) : null;
        final EditText qIgn = area(q, "Ezeket a domaineket ne naplózza (soronként)", "pl. *.lan", join(ql.optJSONArray("ignored")), 3);
        LinearLayout qb = buttonRow(q);
        button(qb, "Mentés", 1, v -> save(() -> {
            JSONObject body = copy(ql).put("enabled", qEn.isChecked()).put("anonymize_client_ip", qAnon.isChecked())
                    .put("interval", qInt.value()).put("ignored", arrOf(qIgn.getText().toString()));
            if (qIgnEn != null) body.put("ignored_enabled", qIgnEn.isChecked());
            return Net.api.put("querylog/config/update", body);
        }, "Napló beállításai mentve"));
        button(qb, "Napló törlése", 2, v -> confirm("Napló törlése", "Törlöd a teljes lekérdezési naplót? Ez nem vonható vissza.",
                "Törlés", () -> save(() -> {
                    String x = Net.api.post("querylog_clear", null);
                    MainActivity.resetState();
                    return x;
                }, "Napló törölve")));

        // Statisztika
        final JSONObject sc = r[5];
        LinearLayout st = section("Statisztika", null);
        final Switch sEn = switchRow(st, "Statisztika engedélyezése", null, sc.optBoolean("enabled", true));
        final long[] sv = {D, 7 * D, 30 * D, 90 * D};
        final IntervalChoice sInt = new IntervalChoice(st, "Statisztika megőrzése", sv,
                new String[]{"1 nap", "7 nap", "30 nap", "90 nap"}, sc.optLong("interval", D));
        final Switch sIgnEn = sc.has("ignored_enabled") ? switchRow(st, "Kivételek használata", null, sc.optBoolean("ignored_enabled", true)) : null;
        final EditText sIgn = area(st, "Ezeket a domaineket hagyja ki (soronként)", "pl. *.lan", join(sc.optJSONArray("ignored")), 3);
        LinearLayout sbb = buttonRow(st);
        button(sbb, "Mentés", 1, v -> save(() -> {
            JSONObject body = copy(sc).put("enabled", sEn.isChecked()).put("interval", sInt.value())
                    .put("ignored", arrOf(sIgn.getText().toString()));
            if (sIgnEn != null) body.put("ignored_enabled", sIgnEn.isChecked());
            return Net.api.put("stats/config/update", body);
        }, "Statisztika beállításai mentve"));
        button(sbb, "Statisztika törlése", 2, v -> confirm("Statisztika törlése",
                "Törlöd az összes statisztikát? Ez nem vonható vissza.", "Törlés",
                () -> save(() -> Net.api.post("stats_reset", null), "Statisztika törölve")));
    }

    /** Időtartam-választó; ha a szerveren más érték van beállítva, azt is felkínálja. */
    private final class IntervalChoice {
        final long[] values;
        final Choice choice;

        IntervalChoice(LinearLayout parent, String label, long[] vals, String[] labels, long current) {
            ArrayList<Long> v = new ArrayList<>();
            ArrayList<String> l = new ArrayList<>();
            int sel = -1;
            for (int i = 0; i < vals.length; i++) {
                v.add(vals[i]);
                l.add(labels[i]);
                if (vals[i] == current) sel = i;
            }
            if (sel < 0) {
                v.add(current);
                l.add(current % D == 0 ? (current / D) + " nap (jelenlegi)" : (current / H) + " óra (jelenlegi)");
                sel = v.size() - 1;
            }
            values = new long[v.size()];
            for (int i = 0; i < values.length; i++) values[i] = v.get(i);
            choice = new Choice(parent, label, l.toArray(new String[0]), sel);
        }

        long value() {
            return values[choice.sel];
        }
    }
}
