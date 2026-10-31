package hu.laskiss.remoteforadguardhome;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/** Egyéni szűrési szabályok szerkesztése és a szűrés ellenőrzése egy domainre. */
public class RulesActivity extends BaseActivity {

    private EditText rules;
    private LinearLayout result;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage("Egyéni szűrési szabályok");
        load();
    }

    @Override
    void load() {
        loading();
        task(() -> Net.api.getJson("filtering/status"), this::render);
    }

    private void render(JSONObject st) {
        loaded();
        LinearLayout sec = section("Saját szabályok", "Soronként egy szabály.");
        rules = area(sec, null, "||example.org^", join(st.optJSONArray("user_rules")), 14);
        TextView help = text("||example.org^  blokkolja a domaint és az aldomainjeit\n"
                + "@@||example.org^  feloldja a blokkolást\n"
                + "127.0.0.1 example.org  hosts formátum\n"
                + "! vagy #  megjegyzés", 12, MUTED);
        help.setTypeface(android.graphics.Typeface.MONOSPACE);
        help.setPadding(0, dp(10), 0, 0);
        sec.addView(help);
        LinearLayout b = buttonRow(sec);
        button(b, "Mentés", 1, v -> {
            final JSONArray arr = new JSONArray(java.util.Arrays.asList(rules.getText().toString().split("\n", -1)));
            save(() -> Net.api.post("filtering/set_rules", new JSONObject().put("rules", trimEnd(arr))), "Szabályok mentve");
        });

        LinearLayout chk = section("Szűrés ellenőrzése", "Megmutatja, mi történne egy domainnel, és melyik szabály dönt róla.");
        final EditText host = field(chk, "Domain", "pl. example.org", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout cb = buttonRow(chk);
        result = col();
        chk.addView(result, matchWrap());
        button(cb, "Ellenőrzés", 0, v -> {
            final String h = host.getText().toString().trim();
            if (h.isEmpty()) {
                host.setError("Adj meg egy domaint");
                return;
            }
            task(() -> Net.api.getJson("filtering/check_host?name=" + URLEncoder.encode(h, "UTF-8")), r -> showCheck(h, r));
        });
    }

    private JSONArray trimEnd(JSONArray a) {
        int n = a.length();
        while (n > 0 && a.optString(n - 1, "").trim().isEmpty()) n--;
        java.util.ArrayList<String> l = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) l.add(a.optString(i, ""));
        return new JSONArray(l);
    }

    private void showCheck(String host, JSONObject r) {
        result.removeAllViews();
        TextView sp = text("", 6, MUTED);
        result.addView(sp);
        String reason = r.optString("reason", "");
        String status;
        try {
            status = Entry.parse(new JSONObject().put("reason", reason)).status;
        } catch (Exception e) {
            status = reason;
        }
        kv(result, "Domain", host);
        kv(result, "Eredmény", status);
        JSONArray rl = r.optJSONArray("rules");
        if (rl != null) {
            for (int i = 0; i < rl.length(); i++) {
                JSONObject o = rl.optJSONObject(i);
                if (o != null && !o.optString("text", "").isEmpty()) kv(result, "Szabály", o.optString("text", ""));
            }
        }
        if (!r.optString("service_name", "").isEmpty()) kv(result, "Szolgáltatás", r.optString("service_name", ""));
        if (!r.optString("cname", "").isEmpty()) kv(result, "CNAME", r.optString("cname", ""));
        String ips = join(r.optJSONArray("ip_addrs")).replace("\n", ", ");
        if (!ips.isEmpty()) kv(result, "IP-címek", ips);
    }
}
