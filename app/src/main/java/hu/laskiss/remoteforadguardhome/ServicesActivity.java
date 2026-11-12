package hu.laskiss.remoteforadguardhome;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Blokkolt szolgáltatások: egész szolgáltatások (pl. TikTok) tiltása minden eszközre. */
public class ServicesActivity extends BaseActivity {

    private final HashSet<String> blocked = new HashSet<>();
    private final HashMap<String, Switch> switches = new HashMap<>();
    private JSONObject current;

    static String[][] GROUP_NAMES() {

        return new String[][]{
            {"ai", S.s(R.string.s_artificial_intelligence)}, {"cdn", "CDN"}, {"dating", S.s(R.string.s_dating)},
            {"gambling", S.s(R.string.s_gambling)}, {"gaming", S.s(R.string.s_games)}, {"hosting", S.s(R.string.s_hosting)},
            {"messenger", S.s(R.string.s_messengers)}, {"music", S.s(R.string.s_music)}, {"privacy", S.s(R.string.s_privacy)},
            {"shopping", S.s(R.string.s_shopping)}, {"social_network", S.s(R.string.s_social_networks)}, {"software", S.s(R.string.s_software)},
            {"streaming", S.s(R.string.s_video_and_streaming)}, {"vpn", "VPN"},
    };

    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage(S.s(R.string.s_blocked_services));
        load();
    }

    @Override
    void load() {
        loading();
        task(() -> new JSONObject[]{Net.api.getJson("blocked_services/all"), Net.api.getJson("blocked_services/get")},
                r -> render(r[0], r[1]));
    }

    private void render(JSONObject all, JSONObject cur) {
        loaded();
        current = cur;
        blocked.clear();
        switches.clear();
        JSONArray ids = cur.optJSONArray("ids");
        if (ids != null) for (int i = 0; i < ids.length(); i++) blocked.add(ids.optString(i, ""));

        TextView intro = text(S.s(R.string.s_adguard_home_blocks_the_enabled_servic)
                + S.s(R.string.s_except_for_clients_that_have_their_own), 14, MUTED);
        intro.setPadding(0, dp(8), 0, 0);
        content.addView(intro);
        LinearLayout top = buttonRow(content);
        button(top, S.s(R.string.s_save), 1, v -> saveAll());
        button(top, S.s(R.string.s_block_all), 0, v -> setAll(true));
        button(top, S.s(R.string.s_none_2), 0, v -> setAll(false));

        // Szolgáltatások csoportonként, a webes felület sorrendjében
        LinkedHashMap<String, List<JSONObject>> groups = new LinkedHashMap<>();
        JSONArray gs = all.optJSONArray("groups");
        if (gs != null) {
            for (int i = 0; i < gs.length(); i++) {
                JSONObject g = gs.optJSONObject(i);
                if (g != null) groups.put(g.optString("id", ""), new ArrayList<>());
            }
        }
        JSONArray svc = all.optJSONArray("blocked_services");
        if (svc != null) {
            for (int i = 0; i < svc.length(); i++) {
                JSONObject s = svc.optJSONObject(i);
                if (s == null) continue;
                String g = s.optString("group_id", "");
                List<JSONObject> l = groups.get(g);
                if (l == null) {
                    l = new ArrayList<>();
                    groups.put(g, l);
                }
                l.add(s);
            }
        }
        for (Map.Entry<String, List<JSONObject>> e : groups.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            LinearLayout sec = section(groupName(e.getKey()), null);
            for (JSONObject s : e.getValue()) {
                final String id = s.optString("id", "");
                Switch sw = switchRow(sec, s.optString("name", id), null, blocked.contains(id));
                sw.setOnCheckedChangeListener((v, on) -> {
                    if (on) blocked.add(id);
                    else blocked.remove(id);
                });
                switches.put(id, sw);
            }
        }
        LinearLayout bottom = buttonRow(content);
        button(bottom, S.s(R.string.s_save), 1, v -> saveAll());
    }

    private void setAll(boolean on) {
        for (Map.Entry<String, Switch> e : switches.entrySet()) e.getValue().setChecked(on);
    }

    private void saveAll() {
        final ArrayList<String> ids = new ArrayList<>(blocked);
        final JSONObject cur = current;
        save(() -> {
            JSONObject body = new JSONObject().put("ids", new JSONArray(ids));
            JSONObject sched = cur == null ? null : cur.optJSONObject("schedule");
            if (sched != null) body.put("schedule", sched);
            return Net.api.put("blocked_services/update", body);
        }, S.s(R.string.s_saved) + ids.size() + S.s(R.string.s_services_blocked));
    }

    static String groupName(String id) {
        for (String[] g : GROUP_NAMES()) if (g[0].equals(id)) return g[1];
        if (id.isEmpty()) return S.s(R.string.s_other);
        return Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' ');
    }
}
