package hu.laskiss.remoteforadguardhome;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** DNS blokkolási listák (mode 0) és DNS engedélyezési listák (mode 1). */
public class FilterListsActivity extends BaseActivity {

    private boolean whitelist;
    /** A szerveren már felvett listák címei (a választóban ezek ki vannak pipálva és tiltva). */
    private final java.util.HashSet<String> addedUrls = new java.util.HashSet<>();
    /** Az AdGuard listagyűjteménye, amit a webes felület is felkínál; egyszer töltődik le. */
    static final String REGISTRY = "https://adguardteam.github.io/HostlistsRegistry/assets/filters.json";
    static JSONObject registry;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        whitelist = getIntent().getIntExtra("mode", 0) == 1;
        setupPage(whitelist ? S.s(R.string.s_dns_allowlists) : S.s(R.string.s_dns_blocklists));
        load();
    }

    @Override
    void load() {
        loading();
        task(() -> Net.api.getJson("filtering/status"), this::render);
    }

    private void render(JSONObject st) {
        loaded();
        TextView intro = text(whitelist
                ? S.s(R.string.s_domains_on_the_allowlists_are_let_thro)
                : S.s(R.string.s_adguard_home_will_block_the_domains_th), 14, MUTED);
        intro.setPadding(0, dp(8), 0, 0);
        content.addView(intro);

        LinearLayout btns = buttonRow(content);
        button(btns, whitelist ? S.s(R.string.s_add_allowlist) : S.s(R.string.s_add_blocklist), 1, v -> addDialog());
        button(btns, S.s(R.string.s_check_for_updates), 0, v -> refreshLists());

        JSONArray lists = st.optJSONArray(whitelist ? "whitelist_filters" : "filters");
        addedUrls.clear();
        if (lists != null) {
            for (int i = 0; i < lists.length(); i++) {
                JSONObject f = lists.optJSONObject(i);
                if (f != null) addedUrls.add(f.optString("url", ""));
            }
        }
        LinearLayout sec = section(whitelist ? S.s(R.string.s_allowlists) : S.s(R.string.s_blocklists), null);
        if (lists == null || lists.length() == 0) {
            emptyNote(sec, S.s(R.string.s_no_list_added_yet));
            return;
        }
        long sum = 0;
        for (int i = 0; i < lists.length(); i++) {
            final JSONObject f = lists.optJSONObject(i);
            if (f == null) continue;
            if (f.optBoolean("enabled", false)) sum += f.optLong("rules_count", 0);
            if (i > 0) divider(sec);
            LinearLayout r = row();
            r.setPadding(0, dp(10), 0, dp(10));
            r.setGravity(Gravity.CENTER_VERTICAL);
            final Switch sw = new Switch(this);
            sw.setChecked(f.optBoolean("enabled", false));
            sw.setContentDescription(f.optString("name", "") + S.s(R.string.s_enabled));
            sw.setOnCheckedChangeListener((v, on) -> setEnabled(f, on));
            r.addView(sw);
            LinearLayout c = col();
            c.setPadding(dp(12), 0, dp(4), 0);
            TextView name = text(f.optString("name", S.s(R.string.s_no_name)), 15, TEXT);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(name);
            TextView url = text(f.optString("url", ""), 12, MUTED);
            url.setSingleLine(true);
            url.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            c.addView(url);
            c.addView(text(fmt(f.optLong("rules_count", 0)) + S.s(R.string.s_rules_updated)
                    + date(f.optString("last_updated", "")), 12, SUB));
            r.addView(c, weight(1));
            r.addView(iconButton(R.drawable.ic_more, S.s(R.string.s_actions), v -> rowMenu(v, f)), new LinearLayout.LayoutParams(dp(44), dp(44)));
            sec.addView(r, matchWrap());
        }
        TextView total = text(S.s(R.string.s_total_rules_in_enabled_lists) + fmt(sum), 13, MUTED);
        total.setPadding(0, dp(10), 0, 0);
        sec.addView(total);
    }

    private void rowMenu(android.view.View anchor, final JSONObject f) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, S.s(R.string.s_edit));
        pm.getMenu().add(0, 2, 1, S.s(R.string.s_delete));
        pm.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) editDialog(f);
            else confirm(S.s(R.string.s_delete_list), S.s(R.string.s_delete_this_list) + f.optString("name", "") + "\n" + f.optString("url", ""),
                    S.s(R.string.s_delete), () -> save(() -> Net.api.post("filtering/remove_url",
                            new JSONObject().put("url", f.optString("url", "")).put("whitelist", whitelist)), S.s(R.string.s_list_deleted)));
            return true;
        });
        pm.show();
    }

    private void setEnabled(final JSONObject f, final boolean on) {
        task(() -> {
            JSONObject data = new JSONObject()
                    .put("name", f.optString("name", ""))
                    .put("url", f.optString("url", ""))
                    .put("enabled", on);
            Net.api.post("filtering/set_url", new JSONObject()
                    .put("url", f.optString("url", "")).put("whitelist", whitelist).put("data", data));
            return true;
        }, r -> load());
    }

    private void editDialog(final JSONObject f) {
        LinearLayout form = col();
        form.setPadding(dp(22), dp(8), dp(22), 0);
        final EditText name = field(form, S.s(R.string.s_name), S.s(R.string.s_e_g_adguard_dns_filter), f == null ? "" : f.optString("name", ""),
                InputType.TYPE_CLASS_TEXT);
        final EditText url = field(form, S.s(R.string.s_list_address_url_or_file_path), "https://…", f == null ? "" : f.optString("url", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        ScrollView sv = new ScrollView(this);
        sv.addView(form);
        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(f == null ? (whitelist ? S.s(R.string.s_new_allowlist) : S.s(R.string.s_new_blocklist)) : S.s(R.string.s_edit_list))
                .setView(sv)
                .setPositiveButton(S.s(R.string.s_save), null)
                .setNegativeButton(S.s(R.string.s_cancel), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String n = name.getText().toString().trim();
            final String u = url.getText().toString().trim();
            if (u.isEmpty()) {
                url.setError(S.s(R.string.s_enter_the_list_address));
                return;
            }
            d.dismiss();
            if (f == null) {
                setPageStatus(S.s(R.string.s_downloading_list), false);
                save(() -> Net.api.postSlow("filtering/add_url", new JSONObject()
                        .put("name", n).put("url", u).put("whitelist", whitelist)), S.s(R.string.s_list_added));
            } else {
                save(() -> Net.api.post("filtering/set_url", new JSONObject()
                        .put("url", f.optString("url", "")).put("whitelist", whitelist)
                        .put("data", new JSONObject().put("name", n).put("url", u)
                                .put("enabled", f.optBoolean("enabled", true)))), S.s(R.string.s_list_saved));
            }
        }));
        d.show();
    }

    private void refreshLists() {
        setPageStatus(S.s(R.string.s_updating_lists), false);
        task(() -> new JSONObject(Net.api.postSlow("filtering/refresh", new JSONObject().put("whitelist", whitelist))),
                r -> {
                    toastLong(S.s(R.string.s_updated) + r.optLong("updated", 0) + S.s(R.string.s_lists));
                    load();
                });
    }

    private void toastLong(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show();
    }

    /** Mint a webes felületen: választás a listából, vagy egyedi lista címmel. */
    private void addDialog() {
        if (whitelist) {
            editDialog(null);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(S.s(R.string.s_new_blocklist))
                .setItems(new String[]{S.s(R.string.s_choose_from_the_list), S.s(R.string.s_add_custom_list)}, (d, w) -> {
                    if (w == 0) chooseFromRegistry();
                    else editDialog(null);
                })
                .setNegativeButton(S.s(R.string.s_cancel), null)
                .show();
    }

    private void chooseFromRegistry() {
        if (registry != null) {
            showRegistry(registry);
            return;
        }
        setPageStatus(S.s(R.string.s_loading_lists), false);
        Net.NET.execute(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(REGISTRY).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                c.disconnect();
                String text = bo.toString("UTF-8").trim();
                final JSONObject r = text.startsWith("[") ? new JSONObject().put("filters", new JSONArray(text)) : new JSONObject(text);
                Net.MAIN.post(() -> {
                    if (isDestroyed()) return;
                    registry = r;
                    setPageStatus("", false);
                    showRegistry(r);
                });
            } catch (Exception e) {
                Net.MAIN.post(() -> {
                    if (isDestroyed()) return;
                    setPageStatus(S.s(R.string.s_the_list_collection_could_not_be_downl), true);
                });
            }
        });
    }

    /** Egy választható lista a gyűjteményből. */
    private static final class Pick {
        String name, url, desc;
        int group;
    }

    static String groupLabel(String name) {
        switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "general": return S.s(R.string.s_general);
            case "other": return S.s(R.string.s_other);
            case "regional": return S.s(R.string.s_regional);
            case "security": return S.s(R.string.s_security);
            default: return name.isEmpty() ? S.s(R.string.s_lists_2) : name;
        }
    }

    static String groupDesc(String name) {
        switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "general": return S.s(R.string.s_lists_that_block_tracking_and_ads_on_m);
            case "other": return S.s(R.string.s_additional_blocklists);
            case "regional": return S.s(R.string.s_lists_made_for_the_sites_of_a_specific);
            case "security": return S.s(R.string.s_lists_against_malicious_phishing_and_s);
            default: return "";
        }
    }

    private void showRegistry(JSONObject r) {
        // Csoportok: azonosító → név (ha a gyűjtemény megadja)
        java.util.LinkedHashMap<Integer, String> groups = new java.util.LinkedHashMap<>();
        JSONArray ga = r.optJSONArray("groups");
        if (ga != null) {
            for (int i = 0; i < ga.length(); i++) {
                JSONObject g = ga.optJSONObject(i);
                if (g == null) continue;
                int id = g.optInt("groupId", g.optInt("id", -1));
                groups.put(id, g.optString("groupName", g.optString("name", "")));
            }
        }
        java.util.TreeMap<Integer, java.util.ArrayList<Pick>> byGroup = new java.util.TreeMap<>();
        JSONArray fa = r.optJSONArray("filters");
        if (fa != null) {
            for (int i = 0; i < fa.length(); i++) {
                JSONObject f = fa.optJSONObject(i);
                if (f == null || f.optBoolean("deprecated", false) || f.optBoolean("disabled", false)) continue;
                Pick p = new Pick();
                p.name = f.optString("name", "");
                p.url = f.optString("downloadUrl", f.optString("downloadURL", f.optString("url", "")));
                p.desc = f.optString("description", "");
                p.group = f.optInt("groupId", -1);
                if (p.name.isEmpty() || p.url.isEmpty()) continue;
                java.util.ArrayList<Pick> l = byGroup.get(p.group);
                if (l == null) {
                    l = new java.util.ArrayList<>();
                    byGroup.put(p.group, l);
                }
                l.add(p);
            }
        }
        if (byGroup.isEmpty()) {
            android.widget.Toast.makeText(this, S.s(R.string.s_the_list_collection_is_empty_or_in_an), android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        // Sorrend: Általános, Egyéb, Regionális, Biztonság, majd a többi
        java.util.ArrayList<Integer> order = new java.util.ArrayList<>(byGroup.keySet());
        final String[] pref = {"general", "other", "regional", "security"};
        java.util.Collections.sort(order, (a, b) -> Integer.compare(rank(groups.get(a), pref, a), rank(groups.get(b), pref, b)));

        LinearLayout box = col();
        box.setPadding(dp(22), dp(6), dp(22), dp(8));
        final java.util.ArrayList<android.widget.CheckBox> boxes = new java.util.ArrayList<>();
        final java.util.ArrayList<Pick> picks = new java.util.ArrayList<>();
        for (Integer gid : order) {
            String gname = groups.containsKey(gid) ? groups.get(gid) : "";
            TextView gh = text(groupLabel(gname), 17, TEXT);
            gh.setTypeface(Typeface.DEFAULT_BOLD);
            gh.setPadding(0, dp(14), 0, 0);
            box.addView(gh);
            String gd = groupDesc(gname);
            if (!gd.isEmpty()) box.addView(text(gd, 13, MUTED));
            java.util.ArrayList<Pick> l = byGroup.get(gid);
            java.util.Collections.sort(l, (a, b) -> a.name.compareToIgnoreCase(b.name));
            for (Pick p : l) {
                android.widget.CheckBox cb = new android.widget.CheckBox(this);
                boolean have = addedUrls.contains(p.url);
                cb.setText(have ? p.name + S.s(R.string.s_already_added) : p.name);
                cb.setTextColor(have ? MUTED : TEXT);
                cb.setChecked(have);
                cb.setEnabled(!have);
                box.addView(cb, matchWrap());
                if (!p.desc.isEmpty()) {
                    TextView d = text(p.desc, 12, MUTED);
                    d.setMaxLines(2);
                    d.setEllipsize(TextUtils.TruncateAt.END);
                    d.setPadding(dp(32), 0, 0, dp(4));
                    box.addView(d);
                }
                if (!have) {
                    boxes.add(cb);
                    picks.add(p);
                }
            }
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this)
                .setTitle(S.s(R.string.s_choose_blocklist))
                .setView(sv)
                .setPositiveButton(S.s(R.string.s_save), (d, w) -> {
                    final java.util.ArrayList<Pick> chosen = new java.util.ArrayList<>();
                    for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isChecked()) chosen.add(picks.get(i));
                    if (!chosen.isEmpty()) addMany(chosen);
                })
                .setNegativeButton(S.s(R.string.s_cancel), null)
                .show();
    }

    private static int rank(String name, String[] pref, int id) {
        if (name != null) {
            for (int i = 0; i < pref.length; i++) if (pref[i].equalsIgnoreCase(name)) return i;
        }
        return 100 + id;
    }

    private void addMany(final java.util.ArrayList<Pick> chosen) {
        setPageStatus(S.s(R.string.s_downloading_lists) + chosen.size() + ")…", false);
        task(() -> {
            int ok = 0;
            StringBuilder err = new StringBuilder();
            for (Pick p : chosen) {
                try {
                    Net.api.postSlow("filtering/add_url", new JSONObject()
                            .put("name", p.name).put("url", p.url).put("whitelist", false));
                    ok++;
                } catch (Exception e) {
                    err.append("\n").append(p.name).append(": ").append(Net.msg(e));
                }
            }
            return ok + "|" + err;
        }, res -> {
            int bar = res.indexOf('|');
            String errs = res.substring(bar + 1);
            android.widget.Toast.makeText(this, S.s(R.string.s_added) + res.substring(0, bar) + S.s(R.string.s_lists), android.widget.Toast.LENGTH_SHORT).show();
            if (!errs.isEmpty()) {
                new AlertDialog.Builder(this).setTitle(S.s(R.string.s_some_lists_failed)).setMessage(errs.trim())
                        .setPositiveButton(S.s(R.string.s_ok), null).show();
            }
            load();
        });
    }

    static String date(String raw) {
        if (raw == null || raw.isEmpty()) return S.s(R.string.s_not_yet);
        try {
            return DateTimeFormatter.ofPattern(S.s(R.string.s_datetime_fmt))
                    .format(OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()));
        } catch (Exception e) {
            return raw;
        }
    }
}
