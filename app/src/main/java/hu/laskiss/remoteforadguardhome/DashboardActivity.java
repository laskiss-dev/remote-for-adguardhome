package hu.laskiss.remoteforadguardhome;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

/** Irányítópult: összesítők kis grafikonokkal és a toplisták. */
public class DashboardActivity extends BaseActivity {

    static final int C_QUERIES = 0xFF5B9BD5, C_BLOCKED = 0xFFE57373, C_SAFE = 0xFFF0A35E, C_PARENTAL = 0xFFD9C35B;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage("Irányítópult");
        load();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        load();
    }

    /** Melyik szerver adataival töltöttük be az oldalt; ha közben szervert váltottak, újratöltjük. */
    private String loadedFor;

    @Override
    protected void onResume() {
        super.onResume();
        String now = Net.server == null ? null : Net.server.id;
        if (loadedFor != null && now != null && !now.equals(loadedFor)) load();
        showServer();
    }

    @Override
    void load() {
        loadedFor = Net.server == null ? null : Net.server.id;
        loading();
        task(() -> {
            Api a = Net.api;
            JSONObject[] r = new JSONObject[3];
            r[0] = a.getJson("stats");
            try { r[1] = a.getJson("stats/config"); } catch (Exception e) { r[1] = new JSONObject(); }
            try { r[2] = a.getJson("clients"); } catch (Exception e) { r[2] = new JSONObject(); }
            return r;
        }, this::render);
    }

    // A lekérdezési napló szűrőinek sorszámai (MainActivity.FILTER_KEYS)
    static final int F_ALL = 0, F_BLOCKED = 3, F_THREATS = 5, F_PARENTAL = 6, F_SAFESEARCH = 9;

    /** A toplisták dobozainak legnagyobb magassága; ha több sor van, a dobozon belül görgethető. */
    static final int LIST_MAX_DP = 320, LIST_ROWS_FIT = 6;

    private void render(JSONObject[] r) {
        loaded();
        JSONObject st = r[0];
        HashMap<String, String> names = clientNames(r[2]);

        long total = st.optLong("num_dns_queries", 0);
        long blocked = st.optLong("num_blocked_filtering", 0);
        long safe = st.optLong("num_replaced_safebrowsing", 0);
        long parental = st.optLong("num_replaced_parental", 0);
        long safesearch = st.optLong("num_replaced_safesearch", 0);
        double avg = st.optDouble("avg_processing_time", 0) * 1000;

        long interval = r[1].optLong("interval", 0);
        TextView period = text(periodText(interval, st.optString("time_units", "hours")), 14, MUTED);
        period.setPadding(0, dp(8), 0, 0);
        content.addView(period);

        // Grafikonok, mint a webes felület tetején
        LinearLayout grid = col();
        content.addView(grid, matchWrap());
        int perRow = landscape ? 4 : 2;
        LinearLayout line = null;
        Object[][] tiles = {
                {"DNS-lekérdezések", total, -1.0, "dns_queries", C_QUERIES, F_ALL},
                {"Szűrők által blokkolva", blocked, pct(blocked, total), "blocked_filtering", C_BLOCKED, F_BLOCKED},
                {"Blokkolt fenyegetések", safe, pct(safe, total), "replaced_safebrowsing", C_SAFE, F_THREATS},
                {"Szülői felügyelet által blokkolva", parental, pct(parental, total), "replaced_parental", C_PARENTAL, F_PARENTAL},
        };
        for (int i = 0; i < tiles.length; i++) {
            if (i % perRow == 0) {
                line = row();
                line.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams lp = matchWrap();
                lp.topMargin = dp(10);
                grid.addView(line, lp);
            }
            Object[] t = tiles[i];
            View tile = tile((String) t[0], (Long) t[1], (Double) t[2], series(st.optJSONArray((String) t[3])), (Integer) t[4]);
            final int filter = (Integer) t[5];
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setOnClickListener(x -> openLog("", filter));
            LinearLayout.LayoutParams tl = weight(1);
            if (i % perRow > 0) tl.leftMargin = dp(10);
            line.addView(tile, tl);
        }

        // Általános statisztikák
        LinearLayout gen = section("Általános statisztikák", null);
        statRow(gen, "DNS-lekérdezések", fmt(total), C_QUERIES, F_ALL, false);
        statRow(gen, "Szűrők által blokkolva", fmt(blocked), C_BLOCKED, F_BLOCKED, true);
        statRow(gen, "Blokkolt fenyegetések", fmt(safe), C_SAFE, F_THREATS, true);
        statRow(gen, "Szülői felügyelet által blokkolva", fmt(parental), C_PARENTAL, F_PARENTAL, true);
        statRow(gen, "Biztonságos keresés kényszerítve", fmt(safesearch), C_SAFESEARCH, F_SAFESEARCH, true);
        statRow(gen, "Átlagos feldolgozási idő", String.format(Locale.US, "%.0f ms", avg), C_AVG, -1, true);

        topList("Legaktívabb kliensek", "Kliens", "Kérések", st.optJSONArray("top_clients"), total, names, false, true);
        topList("Leglátogatottabb domainek", "Domain", "Kérések", st.optJSONArray("top_queried_domains"), total, null, false, true);
        topList("Legtöbbet blokkolt domainek", "Domain", "Kérések", st.optJSONArray("top_blocked_domains"), blocked, null, false, true);
        topList("Top upstream szerverek", "Upstream", "Válaszok", st.optJSONArray("top_upstreams_responses"), total, null, false, false);
        topList("Átlagos upstream válaszidő", "Upstream", "Válaszidő", st.optJSONArray("top_upstreams_avg_time"), 0, null, true, false);
    }

    static final int C_SAFESEARCH = 0xFF8E7CC3, C_AVG = 0xFF9E9E9E;

    /** Egy sor az Általános statisztikákban: szám, felirat; koppintásra a napló az adott szűrővel nyílik. */
    private void statRow(LinearLayout parent, String label, String value, int color, final int filter, boolean sep) {
        if (sep) divider(parent);
        LinearLayout r = row();
        r.setMinimumHeight(dp(48));
        r.setPadding(0, dp(8), 0, dp(8));
        TextView v = text(value, 16, light ? darker(color) : color);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setFontFeatureSettings("tnum");
        v.setMinWidth(dp(84));
        r.addView(v);
        TextView l = text(label, 15, TEXT);
        l.setPadding(dp(12), 0, 0, 0);
        r.addView(l, weight(1));
        if (filter >= 0) {
            r.setClickable(true);
            r.setFocusable(true);
            r.setOnClickListener(x -> openLog("", filter));
        }
        parent.addView(r, matchWrap());
    }

    private View tile(String label, long value, double pct, float[] data, int color) {
        LinearLayout t = col();
        t.setBackground(box(DETAIL, LINE, 1, 8));
        t.setPadding(dp(12), dp(10), dp(12), 0);
        TextView v = text(fmt(value), 22, light ? darker(color) : color);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setFontFeatureSettings("tnum");
        t.addView(v);
        TextView l = text(label, 12, SUB);
        l.setMinLines(2);
        t.addView(l);
        TextView p = text(pct >= 0 ? String.format(Locale.US, "%.2f %%", pct) : " ", 12, MUTED);
        t.addView(p);
        Spark s = new Spark(this);
        s.set(data, color, dp(1));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        sl.topMargin = dp(6);
        t.addView(s, sl);
        return t;
    }

    /**
     * Toplista egy fix magasságú dobozban: ha több sor van, mint ami elfér, a dobozon belül görgethető
     * (mint a hivatalos mobilos webes felületen), így az oldal nem lesz túl hosszú.
     */
    private void topList(String title, String colLeft, String colRight, JSONArray a, long total,
                         HashMap<String, String> names, boolean seconds, boolean clickable) {
        LinearLayout sec = section(title, null);
        if (a == null || a.length() == 0) {
            emptyNote(sec, "Nincs adat");
            return;
        }
        // A jobb szélen helyet hagyunk a görgetősávnak, a fejléc is ehhez igazodik.
        int bar = dp(14);
        LinearLayout head = row();
        head.setPadding(0, dp(8), bar, dp(6));
        TextView hl = text(colLeft, 13, MUTED);
        hl.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(hl, weight(1));
        TextView hr = text(colRight, 13, MUTED);
        hr.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(hr);
        sec.addView(head, matchWrap());
        divider(sec);

        LinearLayout rows = col();
        rows.setPadding(0, 0, bar, 0);
        int n = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Iterator<String> it = o.keys();
            if (!it.hasNext()) continue;
            final String key = it.next();
            double val = o.optDouble(key, 0);
            LinearLayout r = row();
            r.setMinimumHeight(dp(48));
            r.setPadding(0, dp(6), 0, dp(6));
            LinearLayout left = col();
            String name = names == null ? null : names.get(key);
            TextView k = text(name != null && !name.isEmpty() ? name : key, 15, TEXT);
            k.setSingleLine(true);
            k.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            left.addView(k);
            if (name != null && !name.isEmpty()) left.addView(text(key, 12, MUTED));
            r.addView(left, weight(1));
            String right;
            if (seconds) {
                right = String.format(Locale.US, "%.1f ms", val * 1000);
            } else {
                right = fmt((long) val) + (total > 0 ? String.format(Locale.US, "  ·  %.1f %%", 100.0 * val / total) : "");
            }
            TextView v = text(right, 14, SUB);
            v.setFontFeatureSettings("tnum");
            v.setPadding(dp(10), 0, 0, 0);
            r.addView(v);
            if (clickable) {
                r.setClickable(true);
                r.setFocusable(true);
                r.setOnClickListener(x -> openLog("\"" + key + "\""));
            }
            if (n > 0) divider(rows);
            rows.addView(r, matchWrap());
            n++;
        }
        android.widget.ScrollView box = new android.widget.ScrollView(this);
        box.setVerticalScrollBarEnabled(true);
        box.setScrollbarFadingEnabled(false);
        box.setNestedScrollingEnabled(true);
        box.addView(rows);
        // A belső görgetés elsőbbséget kap, amíg az ujj a dobozban van.
        box.setOnTouchListener((v, ev) -> {
            int act = ev.getActionMasked();
            boolean hold = act == android.view.MotionEvent.ACTION_DOWN || act == android.view.MotionEvent.ACTION_MOVE;
            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(hold);
            return false;
        });
        int h = n > LIST_ROWS_FIT ? dp(LIST_MAX_DP) : ViewGroup.LayoutParams.WRAP_CONTENT;
        sec.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
    }

    /** Világos háttéren a csempék számai sötétebb árnyalatot kapnak, hogy jól olvashatók legyenek. */
    static int darker(int c) {
        int r = (int) (((c >> 16) & 0xFF) * 0.55), g = (int) (((c >> 8) & 0xFF) * 0.55), b = (int) ((c & 0xFF) * 0.55);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static double pct(long a, long total) {
        return total > 0 ? 100.0 * a / total : 0;
    }

    static float[] series(JSONArray a) {
        if (a == null) return new float[0];
        float[] f = new float[a.length()];
        for (int i = 0; i < f.length; i++) f[i] = (float) a.optDouble(i, 0);
        return f;
    }

    static String periodText(long intervalMs, String units) {
        if (intervalMs > 0) {
            long hours = intervalMs / 3_600_000L;
            if (hours >= 48) return "Az elmúlt " + (hours / 24) + " nap";
            if (hours > 0) return "Az elmúlt " + hours + " óra";
        }
        return "days".equals(units) ? "Napi bontás" : "Óránkénti bontás";
    }

    /** IP → név a kliens-beállításokból, hogy a toplistán nevek jelenjenek meg. */
    static HashMap<String, String> clientNames(JSONObject c) {
        HashMap<String, String> m = new HashMap<>();
        JSONArray auto = c.optJSONArray("auto_clients");
        if (auto != null) {
            for (int i = 0; i < auto.length(); i++) {
                JSONObject o = auto.optJSONObject(i);
                if (o != null && !o.optString("name", "").isEmpty()) m.put(o.optString("ip", ""), o.optString("name", ""));
            }
        }
        JSONArray cl = c.optJSONArray("clients");
        if (cl != null) {
            for (int i = 0; i < cl.length(); i++) {
                JSONObject o = cl.optJSONObject(i);
                if (o == null) continue;
                JSONArray ids = o.optJSONArray("ids");
                if (ids == null) continue;
                for (int j = 0; j < ids.length(); j++) m.put(ids.optString(j, ""), o.optString("name", ""));
            }
        }
        return m;
    }
}
