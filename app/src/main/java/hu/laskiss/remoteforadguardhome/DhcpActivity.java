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
        setupPage("DHCP beállítások");
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
        LinearLayout s = section("Állapot", en ? null
                : "A DHCP-szerver ki van kapcsolva. Home Assistant alatt általában a router osztja a címeket, így ez rendben van.");
        kv(s, "DHCP-szerver", en ? "Bekapcsolva" : "Kikapcsolva");
        if (!st.optString("interface_name", "").isEmpty()) kv(s, "Hálózati csatoló", st.optString("interface_name", ""));
        JSONObject v4 = st.optJSONObject("v4");
        if (v4 != null && !v4.optString("gateway_ip", "").isEmpty()) {
            kv(s, "Átjáró", v4.optString("gateway_ip", ""));
            kv(s, "Alhálózati maszk", v4.optString("subnet_mask", ""));
            kv(s, "Címtartomány", v4.optString("range_start", "") + " – " + v4.optString("range_end", ""));
            long sec = v4.optLong("lease_duration", 0);
            if (sec > 0) kv(s, "Bérleti idő", sec >= 3600 ? (sec / 3600) + " óra" : sec + " másodperc");
        }
        TextView note = text("A DHCP beállításait a webes felületen módosíthatod, mert egy rossz beállítás az egész hálózatot érintheti.", 12, MUTED);
        note.setPadding(0, dp(8), 0, 0);
        s.addView(note);

        leases("Kiosztott címek", st.optJSONArray("leases"), true);
        leases("Statikus címek", st.optJSONArray("static_leases"), false);
    }

    private void leases(String title, JSONArray a, boolean dynamic) {
        LinearLayout sec = section(title, null);
        if (a == null || a.length() == 0) {
            emptyNote(sec, "Nincs.");
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
                c.addView(text("Lejár: " + FilterListsActivity.date(l.optString("expires", "")), 12, MUTED));
            }
            sec.addView(c, matchWrap());
        }
    }
}
