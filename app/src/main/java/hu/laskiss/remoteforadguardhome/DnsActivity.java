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
    static String[] MODE_LABELS() {
        return new String[]{S.s(R.string.s_load_balancing), S.s(R.string.s_parallel_requests), S.s(R.string.s_fastest_ip_address)};
    }
    static final String[] BLOCK = {"default", "refused", "nxdomain", "null_ip", "custom_ip"};
    static String[] BLOCK_LABELS() {
        return new String[]{S.s(R.string.s_default), "REFUSED", "NXDOMAIN", "Null IP (0.0.0.0)", S.s(R.string.s_custom_ip)};
    }

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
            button(row, S.s(R.string.s_save), 1, v -> {
                final JSONObject body;
                try {
                    body = collect();
                } catch (Exception e) {
                    return;
                }
                save(() -> Net.api.post("dns_config", body), S.s(R.string.s_dns_settings_saved));
            });
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage(S.s(R.string.s_dns_settings));
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

        final Group up = new Group(section(S.s(R.string.s_upstream_dns_servers),
                S.s(R.string.s_one_address_per_line_domain_specific_u)));
        up.list("upstream_dns", null, "https://dns10.quad9.net/dns-query", 6);
        up.choice("upstream_mode", S.s(R.string.s_query_mode), MODES, MODE_LABELS(), 0);
        up.number("upstream_timeout", S.s(R.string.s_upstream_timeout_seconds));
        LinearLayout ub = buttonRow(up.box);
        up.saveButton(ub);
        button(ub, S.s(R.string.s_test), 0, v -> testUpstreams(up));

        Group fb = new Group(section(S.s(R.string.s_fallback_dns_servers), S.s(R.string.s_used_when_the_upstreams_do_not_respond)));
        fb.list("fallback_dns", null, "pl. 9.9.9.10", 3);
        fb.saveButton(buttonRow(fb.box));

        Group bs = new Group(section(S.s(R.string.s_bootstrap_dns_servers), S.s(R.string.s_used_to_resolve_the_upstreams_names_pl)));
        bs.list("bootstrap_dns", null, "9.9.9.10", 3);
        bs.saveButton(buttonRow(bs.box));

        Group ptr = new Group(section(S.s(R.string.s_private_reverse_dns), S.s(R.string.s_resolve_names_ptr_for_local_ip_address)));
        ptr.list("local_ptr_upstreams", null, S.s(R.string.s_e_g_192_168_1_1_empty_the_system_s), 2);
        ptr.bool("use_private_ptr_resolvers", S.s(R.string.s_use_private_reverse_resolvers), null);
        ptr.bool("resolve_clients", S.s(R.string.s_resolve_client_ip_addresses), S.s(R.string.s_looks_up_names_for_clients_using_rever));
        ptr.saveButton(buttonRow(ptr.box));

        Group srv = new Group(section(S.s(R.string.s_dns_server), null));
        srv.number("ratelimit", S.s(R.string.s_rate_limit_requests_second_per_client));
        srv.list("ratelimit_whitelist", S.s(R.string.s_ip_addresses_exempt_from_the_limit), S.s(R.string.s_one_per_line), 2);
        srv.bool("edns_cs_enabled", S.s(R.string.s_edns_client_subnet_ecs), null);
        srv.bool("dnssec_enabled", "DNSSEC", null);
        srv.bool("disable_ipv6", S.s(R.string.s_disable_resolving_ipv6_addresses_aaaa), null);
        srv.choice("blocking_mode", S.s(R.string.s_blocking_mode), BLOCK, BLOCK_LABELS(), 0);
        srv.textField("blocking_ipv4", S.s(R.string.s_custom_ipv4_address_for_custom_ip_mode), "pl. 0.0.0.0");
        srv.textField("blocking_ipv6", S.s(R.string.s_custom_ipv6_address_for_custom_ip_mode), "pl. ::");
        srv.number("blocked_response_ttl", S.s(R.string.s_blocked_response_lifetime_ttl_seconds));
        srv.saveButton(buttonRow(srv.box));

        Group cache = new Group(section(S.s(R.string.s_dns_cache), null));
        cache.bool("cache_enabled", S.s(R.string.s_use_cache), null);
        cache.number("cache_size", S.s(R.string.s_cache_size_bytes));
        cache.number("cache_ttl_min", S.s(R.string.s_minimum_ttl_seconds));
        cache.number("cache_ttl_max", S.s(R.string.s_maximum_ttl_seconds_0_none));
        cache.bool("cache_optimistic", S.s(R.string.s_optimistic_cache), S.s(R.string.s_returns_expired_entries_too_while_refr));
        LinearLayout cb = buttonRow(cache.box);
        cache.saveButton(cb);
        button(cb, S.s(R.string.s_clear_cache), 2, v -> confirm(S.s(R.string.s_clear_cache), S.s(R.string.s_clear_the_dns_cache), S.s(R.string.s_delete),
                () -> apply(() -> Net.api.post("cache_clear", null), S.s(R.string.s_cache_cleared))));
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
        setPageStatus(S.s(R.string.s_testing_upstreams), false);
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
                    .setTitle(S.s(R.string.s_test_result))
                    .setMessage(sb.length() == 0 ? S.s(R.string.s_nothing_to_test) : sb.toString().trim())
                    .setPositiveButton(S.s(R.string.s_ok), null)
                    .show();
        });
    }
}
