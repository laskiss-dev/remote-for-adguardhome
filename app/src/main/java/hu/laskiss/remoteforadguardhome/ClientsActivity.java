package hu.laskiss.remoteforadguardhome;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Kliens beállítások: állandó kliensek, futás közben észlelt kliensek, hozzáférés. */
public class ClientsActivity extends BaseActivity {

    private final ArrayList<String> serviceIds = new ArrayList<>();
    private final ArrayList<String> serviceNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage("Kliens beállítások");
        load();
    }

    @Override
    void load() {
        loading();
        task(() -> {
            JSONObject[] r = new JSONObject[3];
            r[0] = Net.api.getJson("clients");
            r[1] = Net.api.getJson("access/list");
            try { r[2] = Net.api.getJson("blocked_services/all"); } catch (Exception e) { r[2] = new JSONObject(); }
            return r;
        }, r -> render(r[0], r[1], r[2]));
    }

    private void render(JSONObject cl, JSONObject access, JSONObject services) {
        loaded();
        serviceIds.clear();
        serviceNames.clear();
        JSONArray sv = services.optJSONArray("blocked_services");
        if (sv != null) {
            for (int i = 0; i < sv.length(); i++) {
                JSONObject s = sv.optJSONObject(i);
                if (s == null) continue;
                serviceIds.add(s.optString("id", ""));
                serviceNames.add(s.optString("name", s.optString("id", "")));
            }
        }

        // Állandó kliensek
        LinearLayout pc = section("Állandó kliensek", "Saját névvel és saját beállításokkal felvett eszközök.");
        LinearLayout pb = buttonRow(pc);
        button(pb, "Kliens hozzáadása", 1, v -> editClient(null, null, null));
        JSONArray clients = cl.optJSONArray("clients");
        if (clients == null || clients.length() == 0) {
            emptyNote(pc, "Még nincs állandó kliens.");
        } else {
            for (int i = 0; i < clients.length(); i++) {
                final JSONObject c = clients.optJSONObject(i);
                if (c == null) continue;
                divider(pc);
                LinearLayout r = row();
                r.setPadding(0, dp(10), 0, dp(10));
                r.setClickable(true);
                r.setFocusable(true);
                r.setOnClickListener(v -> editClient(c, null, null));
                LinearLayout col = col();
                TextView n = text(c.optString("name", ""), 15, TEXT);
                n.setTypeface(Typeface.DEFAULT_BOLD);
                col.addView(n);
                TextView ids = text(join(c.optJSONArray("ids")).replace("\n", ", "), 13, SUB);
                ids.setSingleLine(true);
                ids.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(ids);
                col.addView(text(summary(c), 12, MUTED));
                r.addView(col, weight(1));
                r.addView(iconButton(R.drawable.ic_more, "Műveletek", v -> clientMenu(v, c)), new LinearLayout.LayoutParams(dp(44), dp(44)));
                pc.addView(r, matchWrap());
            }
        }

        // Futás közbeni kliensek
        LinearLayout rc = section("Futás közbeni kliensek", "Automatikusan észlelt eszközök. Koppints egyre, hogy állandó klienssé tedd.");
        JSONArray auto = cl.optJSONArray("auto_clients");
        if (auto == null || auto.length() == 0) {
            emptyNote(rc, "Nincs észlelt kliens.");
        } else {
            for (int i = 0; i < auto.length(); i++) {
                final JSONObject a = auto.optJSONObject(i);
                if (a == null) continue;
                if (i > 0) divider(rc);
                LinearLayout r = row();
                r.setPadding(0, dp(8), 0, dp(8));
                r.setMinimumHeight(dp(48));
                r.setClickable(true);
                r.setFocusable(true);
                final String ip = a.optString("ip", "");
                final String name = a.optString("name", "");
                r.setOnClickListener(v -> autoMenu(v, ip, name));
                LinearLayout col = col();
                col.addView(text(name.isEmpty() ? ip : name, 15, TEXT));
                col.addView(text(ip + " · " + a.optString("source", ""), 12, MUTED));
                r.addView(col, weight(1));
                rc.addView(r, matchWrap());
            }
        }

        // Hozzáférési beállítások
        LinearLayout ac = section("Hozzáférési beállítások",
                "Itt korlátozhatod, mely eszközök használhatják a DNS-szervert, és mely domaineket ne szolgálja ki.");
        final EditText allowed = area(ac, "Engedélyezett kliensek (ha kitöltöd, csak ezek használhatják)",
                "IP, CIDR vagy ClientID, soronként", join(access.optJSONArray("allowed_clients")), 3);
        final EditText disallowed = area(ac, "Letiltott kliensek", "IP, CIDR vagy ClientID, soronként",
                join(access.optJSONArray("disallowed_clients")), 3);
        final EditText hosts = area(ac, "Nem engedélyezett domainek", "pl. version.bind, soronként",
                join(access.optJSONArray("blocked_hosts")), 3);
        LinearLayout ab = buttonRow(ac);
        button(ab, "Mentés", 1, v -> save(() -> Net.api.post("access/set", new JSONObject()
                .put("allowed_clients", arrOf(allowed.getText().toString()))
                .put("disallowed_clients", arrOf(disallowed.getText().toString()))
                .put("blocked_hosts", arrOf(hosts.getText().toString()))), "Hozzáférés mentve"));
    }

    private String summary(JSONObject c) {
        if (c.optBoolean("use_global_settings", true)) return "Globális beállítások";
        ArrayList<String> on = new ArrayList<>();
        if (c.optBoolean("filtering_enabled", false)) on.add("szűrés");
        if (c.optBoolean("safebrowsing_enabled", false)) on.add("bizt. böngészés");
        if (c.optBoolean("parental_enabled", false)) on.add("szülői felügyelet");
        JSONObject ss = c.optJSONObject("safe_search");
        if (ss != null && ss.optBoolean("enabled", false)) on.add("bizt. keresés");
        return on.isEmpty() ? "Egyéni: minden szűrés kikapcsolva" : "Egyéni: " + TextUtils.join(", ", on);
    }

    private void clientMenu(View anchor, final JSONObject c) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Szerkesztés");
        pm.getMenu().add(0, 2, 1, "Lekérdezési napló");
        pm.getMenu().add(0, 3, 2, "Törlés");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: editClient(c, null, null); break;
                case 2:
                    JSONArray ids = c.optJSONArray("ids");
                    openLog(ids != null && ids.length() > 0 ? "\"" + ids.optString(0, "") + "\"" : c.optString("name", ""));
                    break;
                default:
                    confirm("Kliens törlése", "Törlöd ezt a klienst?\n\n" + c.optString("name", ""), "Törlés",
                            () -> save(() -> Net.api.post("clients/delete", new JSONObject().put("name", c.optString("name", ""))),
                                    "Kliens törölve"));
            }
            return true;
        });
        pm.show();
    }

    private void autoMenu(View anchor, final String ip, final String name) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Hozzáadás állandó kliensként");
        pm.getMenu().add(0, 2, 1, "Lekérdezési napló");
        pm.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) editClient(null, name, ip);
            else openLog("\"" + ip + "\"");
            return true;
        });
        pm.show();
    }

    /** Kliens szerkesztése (c == null: új kliens, opcionálisan előre kitöltött névvel és IP-vel). */
    private void editClient(final JSONObject c, String presetName, String presetIp) {
        final JSONObject data = c == null ? newClient() : copy(c);
        LinearLayout f = col();
        f.setPadding(dp(22), dp(8), dp(22), dp(8));
        final EditText name = field(f, "Név", "pl. Laci telefonja",
                c == null ? (presetName == null ? "" : presetName) : data.optString("name", ""), InputType.TYPE_CLASS_TEXT);
        final EditText ids = area(f, "Azonosítók (IP, CIDR, MAC vagy ClientID, soronként)", "192.168.1.20",
                c == null ? (presetIp == null ? "" : presetIp) : join(data.optJSONArray("ids")), 2);
        final Switch global = switchRow(f, "Globális beállítások használata", "Ha be van kapcsolva, a lenti szűrési kapcsolók nem számítanak.",
                data.optBoolean("use_global_settings", true));
        final Switch filtering = switchRow(f, "Szűrés", null, data.optBoolean("filtering_enabled", false));
        final Switch safeb = switchRow(f, "Biztonságos böngészés", null, data.optBoolean("safebrowsing_enabled", false));
        final Switch parental = switchRow(f, "Szülői felügyelet", null, data.optBoolean("parental_enabled", false));
        JSONObject ss = data.optJSONObject("safe_search");
        final Switch safes = switchRow(f, "Biztonságos keresés", null, ss != null && ss.optBoolean("enabled", false));
        final Switch globalSvc = switchRow(f, "Globális blokkolt szolgáltatások", null,
                data.optBoolean("use_global_blocked_services", true));
        final HashSet<String> chosen = new HashSet<>();
        JSONArray bs = data.optJSONArray("blocked_services");
        if (bs != null) for (int i = 0; i < bs.length(); i++) chosen.add(bs.optString(i, ""));
        LinearLayout sb = buttonRow(f);
        final TextView svcBtn = button(sb, "Saját szolgáltatások: " + chosen.size(), 0, null);
        svcBtn.setOnClickListener(v -> pickServices(chosen, svcBtn));
        final EditText ups = area(f, "Upstream DNS szerverek (üresen a globálisak)", "pl. https://dns.quad9.net/dns-query",
                join(data.optJSONArray("upstreams")), 2);
        final Switch noLog = switchRow(f, "Kihagyás a lekérdezési naplóból", null, data.optBoolean("ignore_querylog", false));
        final Switch noStats = switchRow(f, "Kihagyás a statisztikából", null, data.optBoolean("ignore_statistics", false));

        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(c == null ? "Új kliens" : "Kliens szerkesztése")
                .setView(sv)
                .setPositiveButton("Mentés", null)
                .setNegativeButton("Mégse", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String n = name.getText().toString().trim();
            final List<String> idl = lines(ids.getText().toString());
            if (n.isEmpty()) { name.setError("Adj nevet a kliensnek"); return; }
            if (idl.isEmpty()) { ids.setError("Legalább egy azonosító kell"); return; }
            try {
                data.put("name", n);
                data.put("ids", arr(idl));
                data.put("use_global_settings", global.isChecked());
                data.put("filtering_enabled", filtering.isChecked());
                data.put("safebrowsing_enabled", safeb.isChecked());
                data.put("parental_enabled", parental.isChecked());
                JSONObject s = data.optJSONObject("safe_search");
                if (s == null) s = defaultSafeSearch();
                s.put("enabled", safes.isChecked());
                data.put("safe_search", s);
                data.put("use_global_blocked_services", globalSvc.isChecked());
                data.put("blocked_services", new JSONArray(new ArrayList<>(chosen)));
                data.put("upstreams", arrOf(ups.getText().toString()));
                data.put("ignore_querylog", noLog.isChecked());
                data.put("ignore_statistics", noStats.isChecked());
                data.remove("whois_info");
                data.remove("ip_addrs");
            } catch (Exception e) {
                return;
            }
            d.dismiss();
            if (c == null) {
                save(() -> Net.api.post("clients/add", data), "Kliens hozzáadva");
            } else {
                final String old = c.optString("name", "");
                save(() -> Net.api.post("clients/update", new JSONObject().put("name", old).put("data", data)), "Kliens mentve");
            }
        }));
        d.show();
    }

    private void pickServices(final HashSet<String> chosen, final TextView btn) {
        if (serviceIds.isEmpty()) {
            android.widget.Toast.makeText(this, "A szolgáltatások listája nem érhető el.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean[] checked = new boolean[serviceIds.size()];
        for (int i = 0; i < checked.length; i++) checked[i] = chosen.contains(serviceIds.get(i));
        new AlertDialog.Builder(this)
                .setTitle("Blokkolt szolgáltatások ennél a kliensnél")
                .setMultiChoiceItems(serviceNames.toArray(new String[0]), checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton("Kész", (d, w) -> {
                    chosen.clear();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) chosen.add(serviceIds.get(i));
                    btn.setText("Saját szolgáltatások: " + chosen.size());
                })
                .setNegativeButton("Mégse", null)
                .show();
    }

    private static JSONObject defaultSafeSearch() {
        JSONObject s = new JSONObject();
        try {
            s.put("enabled", false);
            for (String k : new String[]{"bing", "duckduckgo", "ecosia", "google", "pixabay", "yandex", "youtube"}) s.put(k, true);
        } catch (Exception ignored) { }
        return s;
    }

    private static JSONObject newClient() {
        JSONObject o = new JSONObject();
        try {
            o.put("use_global_settings", true);
            o.put("use_global_blocked_services", true);
            o.put("filtering_enabled", true);
            o.put("safebrowsing_enabled", false);
            o.put("parental_enabled", false);
            o.put("safe_search", defaultSafeSearch());
            o.put("blocked_services", new JSONArray());
            o.put("upstreams", new JSONArray());
            o.put("tags", new JSONArray());
        } catch (Exception ignored) { }
        return o;
    }
}
