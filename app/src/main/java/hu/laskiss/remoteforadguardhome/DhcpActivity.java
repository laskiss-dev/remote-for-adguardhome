package hu.laskiss.remoteforadguardhome;

import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** DHCP: állapot és címkiosztások megtekintése. */
public class DhcpActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage(S.s(R.string.s_dhcp_settings));
        load();
    }

    @Override
    void load() {
        loading();
        task(() -> Net.api.getJson("dhcp/status"), this::render);
    }

    private void render(JSONObject st) {
        loaded();
        boolean en = st.optBoolean("enabled", false);
        LinearLayout s = section(S.s(R.string.s_status), en ? null
                : S.s(R.string.s_the_dhcp_server_is_off_under_home_assi));
        kv(s, S.s(R.string.s_dhcp_server), en ? S.s(R.string.s_on) : S.s(R.string.s_off));
        if (!st.optString("interface_name", "").isEmpty()) kv(s, S.s(R.string.s_network_interface), st.optString("interface_name", ""));
        JSONObject v4 = st.optJSONObject("v4");
        if (v4 != null && !v4.optString("gateway_ip", "").isEmpty()) {
            kv(s, S.s(R.string.s_gateway), v4.optString("gateway_ip", ""));
            kv(s, S.s(R.string.s_subnet_mask), v4.optString("subnet_mask", ""));
            kv(s, S.s(R.string.s_address_range), v4.optString("range_start", "") + " – " + v4.optString("range_end", ""));
            long sec = v4.optLong("lease_duration", 0);
            if (sec > 0) kv(s, S.s(R.string.s_lease_time), sec >= 3600 ? (sec / 3600) + S.s(R.string.s_hours) : sec + S.s(R.string.s_seconds));
        }
        TextView note = text(S.s(R.string.s_you_can_change_the_dhcp_settings_in_th), 12, MUTED);
        note.setPadding(0, dp(8), 0, 0);
        s.addView(note);

        leases(S.s(R.string.s_leased_addresses), st.optJSONArray("leases"), true);
        leases(S.s(R.string.s_static_addresses), st.optJSONArray("static_leases"), false);
    }

    private void leases(String title, JSONArray a, boolean dynamic) {
        LinearLayout sec = section(title, null);
        if (a == null || a.length() == 0) {
            emptyNote(sec, S.s(R.string.s_none));
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject l = a.optJSONObject(i);
            if (l == null) continue;
            if (i > 0) divider(sec);
            LinearLayout c = col();
            c.setPadding(0, dp(8), 0, dp(8));
            String host = l.optString("hostname", "");
            TextView h = text(host.isEmpty() ? l.optString("ip", "") : host, 15, TEXT);
            h.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(h);
            c.addView(text(l.optString("ip", "") + " · " + l.optString("mac", ""), 13, SUB));
            if (dynamic && !l.optString("expires", "").isEmpty()) {
                c.addView(text(S.s(R.string.s_expires) + FilterListsActivity.date(l.optString("expires", "")), 12, MUTED));
            }
            sec.addView(c, matchWrap());
        }
    }
}
