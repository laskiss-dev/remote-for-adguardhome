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
        setupPage(S.s(R.string.s_general_settings));
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
        LinearLayout f = section(S.s(R.string.s_filtering_2), null);
        final Switch fEn = switchRow(f, S.s(R.string.s_use_filtering), S.s(R.string.s_apply_the_blocklists_and_allowlists),
                r[0].optBoolean("enabled", true));
        final long[] upd = {0, 1, 12, 24, 72, 168};
        final String[] updL = {S.s(R.string.s_never), S.s(R.string.s_every_hour), S.s(R.string.s_every_12_hours), S.s(R.string.s_daily), S.s(R.string.s_every_3_days), S.s(R.string.s_weekly)};
        int sel = 3;
        for (int i = 0; i < upd.length; i++) if (upd[i] == r[0].optLong("interval", 24)) sel = i;
        final Choice fInt = new Choice(f, S.s(R.string.s_filter_list_update_interval), updL, sel);
        button(buttonRow(f), S.s(R.string.s_save), 1, v -> save(() -> Net.api.post("filtering/config", new JSONObject()
                .put("enabled", fEn.isChecked()).put("interval", upd[fInt.sel])), S.s(R.string.s_filtering_saved)));

        // Biztonság
        LinearLayout s = section(S.s(R.string.s_protection_features), null);
        if (r[1] != null) {
            Switch sb = switchRow(s, S.s(R.string.s_safe_browsing_2), S.s(R.string.s_block_known_malicious_and_phishing_sit),
                    r[1].optBoolean("enabled", false));
            sb.setOnCheckedChangeListener((v, on) -> apply(() -> Net.api.post(on ? "safebrowsing/enable" : "safebrowsing/disable", null),
                    on ? S.s(R.string.s_safe_browsing_on) : S.s(R.string.s_safe_browsing_off)));
        }
        if (r[2] != null) {
            Switch pa = switchRow(s, S.s(R.string.s_parental_control_2), S.s(R.string.s_block_adult_content_sites), r[2].optBoolean("enabled", false));
            pa.setOnCheckedChangeListener((v, on) -> apply(() -> Net.api.post(on ? "parental/enable" : "parental/disable", null),
                    on ? S.s(R.string.s_parental_control_on) : S.s(R.string.s_parental_control_off)));
        }
        if (r[3] != null) {
            final JSONObject ss = r[3];
            final Switch ssEn = switchRow(s, S.s(R.string.s_safe_search_2), S.s(R.string.s_enforces_safe_search_for_the_selected),
                    ss.optBoolean("enabled", false));
            final LinkedHashMap<String, Switch> engines = new LinkedHashMap<>();
            String[][] names = {{"google", "Google"}, {"bing", "Bing"}, {"duckduckgo", "DuckDuckGo"}, {"ecosia", "Ecosia"},
                    {"yandex", "Yandex"}, {"youtube", "YouTube"}, {"pixabay", "Pixabay"}};
            for (String[] n : names) {
                if (ss.has(n[0])) engines.put(n[0], switchRow(s, "   " + n[1], null, ss.optBoolean(n[0], true)));
            }
            button(buttonRow(s), S.s(R.string.s_save_search_settings), 1, v -> save(() -> {
                JSONObject body = copy(ss).put("enabled", ssEn.isChecked());
                for (Map.Entry<String, Switch> e : engines.entrySet()) body.put(e.getKey(), e.getValue().isChecked());
                return Net.api.put("safesearch/settings", body);
            }, S.s(R.string.s_safe_search_saved)));
        }

        // Lekérdezési napló
        final JSONObject ql = r[4];
        LinearLayout q = section(S.s(R.string.s_query_log), null);
        final Switch qEn = switchRow(q, S.s(R.string.s_enable_log), null, ql.optBoolean("enabled", true));
        final Switch qAnon = switchRow(q, S.s(R.string.s_anonymize_client_ip_addresses), null, ql.optBoolean("anonymize_client_ip", false));
        final long[] qv = {6 * H, D, 7 * D, 30 * D, 90 * D};
        final IntervalChoice qInt = new IntervalChoice(q, S.s(R.string.s_log_retention), qv,
                new String[]{S.s(R.string.s_6_hours), S.s(R.string.s_1_day), S.s(R.string.s_7_days), S.s(R.string.s_30_days), S.s(R.string.s_90_days)}, ql.optLong("interval", 90 * D));
        final Switch qIgnEn = ql.has("ignored_enabled") ? switchRow(q, S.s(R.string.s_use_exceptions), null, ql.optBoolean("ignored_enabled", true)) : null;
        final EditText qIgn = area(q, S.s(R.string.s_do_not_log_these_domains_one_per_line), S.s(R.string.s_e_g_lan), join(ql.optJSONArray("ignored")), 3);
        LinearLayout qb = buttonRow(q);
        button(qb, S.s(R.string.s_save), 1, v -> save(() -> {
            JSONObject body = copy(ql).put("enabled", qEn.isChecked()).put("anonymize_client_ip", qAnon.isChecked())
                    .put("interval", qInt.value()).put("ignored", arrOf(qIgn.getText().toString()));
            if (qIgnEn != null) body.put("ignored_enabled", qIgnEn.isChecked());
            return Net.api.put("querylog/config/update", body);
        }, S.s(R.string.s_log_settings_saved)));
        button(qb, S.s(R.string.s_clear_log), 2, v -> confirm(S.s(R.string.s_clear_log), S.s(R.string.s_clear_the_entire_query_log_this_cannot),
                S.s(R.string.s_delete), () -> save(() -> {
                    String x = Net.api.post("querylog_clear", null);
                    MainActivity.resetState();
                    return x;
                }, S.s(R.string.s_log_cleared))));

        // Statisztika
        final JSONObject sc = r[5];
        LinearLayout st = section(S.s(R.string.s_statistics), null);
        final Switch sEn = switchRow(st, S.s(R.string.s_enable_statistics), null, sc.optBoolean("enabled", true));
        final long[] sv = {D, 7 * D, 30 * D, 90 * D};
        final IntervalChoice sInt = new IntervalChoice(st, S.s(R.string.s_statistics_retention), sv,
                new String[]{S.s(R.string.s_1_day), S.s(R.string.s_7_days), S.s(R.string.s_30_days), S.s(R.string.s_90_days)}, sc.optLong("interval", D));
        final Switch sIgnEn = sc.has("ignored_enabled") ? switchRow(st, S.s(R.string.s_use_exceptions), null, sc.optBoolean("ignored_enabled", true)) : null;
        final EditText sIgn = area(st, S.s(R.string.s_exclude_these_domains_one_per_line), S.s(R.string.s_e_g_lan), join(sc.optJSONArray("ignored")), 3);
        LinearLayout sbb = buttonRow(st);
        button(sbb, S.s(R.string.s_save), 1, v -> save(() -> {
            JSONObject body = copy(sc).put("enabled", sEn.isChecked()).put("interval", sInt.value())
                    .put("ignored", arrOf(sIgn.getText().toString()));
            if (sIgnEn != null) body.put("ignored_enabled", sIgnEn.isChecked());
            return Net.api.put("stats/config/update", body);
        }, S.s(R.string.s_statistics_settings_saved)));
        button(sbb, S.s(R.string.s_clear_statistics), 2, v -> confirm(S.s(R.string.s_clear_statistics),
                S.s(R.string.s_clear_all_statistics_this_cannot_be_un), S.s(R.string.s_delete),
                () -> save(() -> Net.api.post("stats_reset", null), S.s(R.string.s_statistics_cleared))));
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
                l.add(current % D == 0 ? (current / D) + S.s(R.string.s_days_current) : (current / H) + S.s(R.string.s_hours_current));
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
