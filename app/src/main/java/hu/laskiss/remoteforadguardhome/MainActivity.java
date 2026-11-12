package hu.laskiss.remoteforadguardhome;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lekérdezési napló. Álló helyzetben a webes felület mobilos nézete (idő + domain,
 * koppintásra részletek), fekvő helyzetben a teljes négyoszlopos táblázat.
 */
public class MainActivity extends BaseActivity {

    static final String[] FILTER_KEYS = {"all", "filtered", "processed", "blocked", "blocked_services",
            "blocked_safebrowsing", "blocked_parental", "whitelisted", "rewritten", "safe_search"};
    static String[] FILTER_LABELS() {
        return new String[]{S.s(R.string.s_all_requests), S.s(R.string.s_filtered), S.s(R.string.s_processed), S.s(R.string.s_blocked), S.s(R.string.s_blocked_services),
            S.s(R.string.s_blocked_threats), S.s(R.string.s_blocked_by_parental_control), S.s(R.string.s_allowed), S.s(R.string.s_rewritten), S.s(R.string.s_safe_search_2)};
    }
    static final int[] INTERVALS = {0, 5, 10, 30, 60};
    static String[] INTERVAL_LABELS() {
        return new String[]{S.s(R.string.s_off), S.s(R.string.s_5_seconds), S.s(R.string.s_10_seconds), S.s(R.string.s_30_seconds), S.s(R.string.s_1_minute)};
    }

    static final int PAGE = 50;   // első betöltés és görgetéskor
    static final int PEEK = 25;   // frissítéskor ennyi legfrissebbet kér, ebből csak az újak kellenek

    static final Handler MAIN = Net.MAIN;
    static final ExecutorService NET = Net.NET;

    // Az állapot statikus, így forgatáskor nem töltődik le újra semmi.
    static MainActivity current;
    static final ArrayList<Entry> entries = new ArrayList<>();
    static final HashSet<String> keys = new HashSet<>();
    static HashMap<Integer, String> filterNames = new HashMap<>();
    static String oldest = "", search = "", expandedKey = null, status = "";
    static int filterIdx = 0, gen = 0, interval = 10;
    static boolean more = false, busy = false, loadingMore = false, statusError = false, loadedOnce = false;
    static boolean showSub = false, markNew = true, beepNew = true;
    /** A legutóbbi frissítéssel érkezett sorok, ezek ideje és domainje zöld. */
    static final HashSet<String> freshKeys = new HashSet<>();

    private boolean resumed;
    private ListView list;
    private Adapter adapter;
    private TextView statusView, filterLabel, footer;
    private LinearLayout header;
    private int headerHeight;
    private ImageButton toTop;
    /** Ennyi sor után jelenik meg az „ugrás a tetejére” gomb. */
    static final int SHOW_TOP_AFTER = 8;
    private EditText searchEdit;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refreshNew();
            schedule();
        }
    };

    // ---------------------------------------------------------------- életciklus

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        interval = prefs.getInt("interval", 10);
        showSub = prefs.getBoolean("showSub", false);
        markNew = prefs.getBoolean("markNew", true);
        beepNew = prefs.getBoolean("beepNew", true);
        current = this;
        // A menüből nyitva (nem forgatás, nem keresés az Irányítópultról) a napló mindig szűretlenül indul.
        Intent in = getIntent();
        boolean fromSearch = in != null && in.getStringExtra("search") != null;
        boolean reset = b == null && !fromSearch && (!search.isEmpty() || filterIdx != 0);
        if (reset) {
            search = "";
            filterIdx = 0;
            expandedKey = null;
        }
        buildUi();
        showStatus();
        if (Net.api == null) MAIN.post(this::showServerDialog);
        else if (!handleSearchIntent(in) && (reset || (!loadedOnce && !busy))) startFresh();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSearchIntent(intent);
    }

    /** Az Irányítópultról érkező keresés (domain vagy kliens). */
    private boolean handleSearchIntent(Intent i) {
        String s = i == null ? null : i.getStringExtra("search");
        if (s == null) return false;
        i.removeExtra("search");
        search = s;
        filterIdx = Math.max(0, Math.min(FILTER_KEYS.length - 1, i.getIntExtra("filter", 0)));
        i.removeExtra("filter");
        expandedKey = null;
        if (searchEdit != null) searchEdit.setText(s);
        if (filterLabel != null) filterLabel.setText(FILTER_LABELS()[filterIdx]);
        startFresh();
        return true;
    }

    /** Új szerver: a napló elölről indul. */
    static void resetState() {
        gen++;
        entries.clear();
        keys.clear();
        freshKeys.clear();
        filterNames = new HashMap<>();
        loadedOnce = false;
        busy = false;
        loadingMore = false;
        more = false;
        oldest = "";
        expandedKey = null;
        MainActivity m = current;
        if (m != null) m.renderList(0, true);
    }

    @Override
    void onServerChanged() {
        startFresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        current = this;
        resumed = true;
        if (Net.api != null && loadedOnce) refreshNew();
        schedule();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        MAIN.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (current == this) current = null;
    }

    private void schedule() {
        MAIN.removeCallbacks(tick);
        if (resumed && interval > 0) MAIN.postDelayed(tick, interval * 1000L);
    }

    // ---------------------------------------------------------------- hálózat

    static void startFresh() {
        if (Net.api == null) return;
        final int g = ++gen;
        busy = true;
        loadingMore = false;
        final Api a = Net.api;
        final String s = search, f = FILTER_KEYS[filterIdx];
        final boolean needNames = filterNames.isEmpty();
        setStatus(S.s(R.string.s_loading), false);
        NET.execute(() -> {
            try {
                final HashMap<Integer, String> names = needNames ? loadFilterNames(a) : null;
                JSONObject r = a.queryLog(s, f, null, PAGE);
                final List<Entry> got = parseList(r);
                final String old = r.optString("oldest", "");
                MAIN.post(() -> {
                    if (names != null) filterNames = names;
                    if (g != gen) return;
                    busy = false;
                    loadedOnce = true;
                    entries.clear();
                    keys.clear();
                    freshKeys.clear();
                    add(got, false);
                    oldest = old;
                    more = got.size() >= PAGE && !old.isEmpty();
                    okStatus();
                    MainActivity m = current;
                    if (m != null) m.renderList(0, true);
                });
            } catch (Exception e) {
                fail(g, e);
            }
        });
    }

    /** Csak a legfrissebb néhány sort kéri le, és ami ebből új, azt teszi a lista elejére. */
    static void refreshNew() {
        if (Net.api == null || busy) return;
        if (entries.isEmpty()) {
            startFresh();
            return;
        }
        final int g = gen;
        busy = true;
        final Api a = Net.api;
        final String s = search, f = FILTER_KEYS[filterIdx];
        NET.execute(() -> {
            try {
                JSONObject r = a.queryLog(s, f, null, PEEK);
                final List<Entry> got = parseList(r);
                final String old = r.optString("oldest", "");
                MAIN.post(() -> {
                    if (g != gen) return;
                    busy = false;
                    ArrayList<Entry> fresh = new ArrayList<>();
                    boolean met = false;
                    for (Entry e : got) {
                        if (keys.contains(e.key)) { met = true; break; }
                        fresh.add(e);
                    }
                    MainActivity m = current;
                    if (!met && got.size() >= PEEK) {
                        // Túl sok új jött azóta: a lista ezzel az adaggal indul újra.
                        entries.clear();
                        keys.clear();
                        add(got, false);
                        freshKeys.clear();
                        for (Entry e : got) freshKeys.add(e.key);
                        oldest = old;
                        more = !old.isEmpty();
                        if (m != null) m.renderList(0, true);
                        beep();
                    } else if (!fresh.isEmpty()) {
                        add(fresh, true);
                        freshKeys.clear();
                        for (Entry e : fresh) freshKeys.add(e.key);
                        if (m != null) m.renderList(fresh.size(), false);
                        beep();
                    }
                    okStatus();
                });
            } catch (Exception e) {
                fail(g, e);
            }
        });
    }

    /**
     * Rövid csippanás, amikor új zöld (kiemelt) sorok jelennek meg: csak ha a frissítés új sorokat
     * hozott, és az „Új sorok kiemelése” be van kapcsolva. Az értesítési hangerőt követi,
     * így néma vagy rezgő módban nem szól. Engedély nem kell hozzá.
     */
    static void beep() {
        if (!beepNew || !markNew || current == null || !current.resumed) return;
        try {
            final android.media.ToneGenerator tg =
                    new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 60);
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120);
            MAIN.postDelayed(tg::release, 400);
        } catch (RuntimeException ignored) {
            // ha a hang épp nem elérhető, egyszerűen kimarad
        }
    }

    static void loadMore() {
        if (Net.api == null || loadingMore || !more || oldest.isEmpty()) return;
        final int g = gen;
        loadingMore = true;
        final Api a = Net.api;
        final String s = search, f = FILTER_KEYS[filterIdx], old = oldest;
        MainActivity m0 = current;
        if (m0 != null) m0.updateFooter();
        NET.execute(() -> {
            try {
                JSONObject r = a.queryLog(s, f, old, PAGE);
                final List<Entry> got = parseList(r);
                final String next = r.optString("oldest", "");
                MAIN.post(() -> {
                    if (g != gen) return;
                    loadingMore = false;
                    ArrayList<Entry> extra = new ArrayList<>();
                    for (Entry e : got) if (!keys.contains(e.key)) extra.add(e);
                    add(extra, false);
                    oldest = next;
                    more = got.size() >= PAGE && !next.isEmpty() && !next.equals(old);
                    MainActivity m = current;
                    if (m != null) m.renderList(0, false);
                });
            } catch (Exception e) {
                fail(g, e);
            }
        });
    }

    /** Blokkolás vagy feloldás a Saját szabályokban, ugyanúgy, mint a webes felület. */
    static void toggleRule(final Entry e) {
        final Api a = Net.api;
        if (a == null || e.domain.isEmpty()) return;
        final boolean unblock = e.kind == Entry.BLOCKED;
        final String rule = (unblock ? "@@" : "") + "||" + e.domain + "^$important";
        final String opposite = (unblock ? "" : "@@") + "||" + e.domain + "^$important";
        NET.execute(() -> {
            try {
                JSONObject st = a.getJson("filtering/status");
                JSONArray ur = st.optJSONArray("user_rules");
                ArrayList<String> rules = new ArrayList<>();
                if (ur != null) {
                    for (int i = 0; i < ur.length(); i++) {
                        String r = ur.optString(i, "");
                        if (r.equals(opposite) || r.equals(rule)) continue;
                        rules.add(r);
                    }
                }
                while (!rules.isEmpty() && rules.get(rules.size() - 1).trim().isEmpty()) {
                    rules.remove(rules.size() - 1);
                }
                rules.add(rule);
                a.post("filtering/set_rules", new JSONObject().put("rules", new JSONArray(rules)));
                Net.toast((unblock ? S.s(R.string.s_unblocked) : S.s(R.string.s_blocked_2)) + e.domain);
            } catch (Exception ex) {
                Net.toast(S.s(R.string.s_failed) + Net.msg(ex));
            }
        });
    }

    static HashMap<Integer, String> loadFilterNames(Api a) {
        HashMap<Integer, String> m = new HashMap<>();
        m.put(0, S.s(R.string.s_custom_rules));
        try {
            JSONObject st = a.getJson("filtering/status");
            for (String k : new String[]{"filters", "whitelist_filters"}) {
                JSONArray arr = st.optJSONArray(k);
                if (arr == null) continue;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject f = arr.optJSONObject(i);
                    if (f != null) m.put(f.optInt("id", -1), f.optString("name", ""));
                }
            }
        } catch (Exception ignored) {
            // ha nem sikerül, a szabály szövege jelenik meg a lista neve helyett
        }
        return m;
    }

    static List<Entry> parseList(JSONObject r) {
        ArrayList<Entry> out = new ArrayList<>();
        JSONArray d = r.optJSONArray("data");
        if (d == null) return out;
        for (int i = 0; i < d.length(); i++) {
            JSONObject o = d.optJSONObject(i);
            if (o == null) continue;
            try {
                out.add(Entry.parse(o));
            } catch (Exception ignored) {
                // hibás sor kimarad
            }
        }
        return out;
    }

    static void add(List<Entry> got, boolean front) {
        if (front) entries.addAll(0, got);
        else entries.addAll(got);
        for (Entry e : got) keys.add(e.key);
    }

    static void fail(final int g, final Exception e) {
        MAIN.post(() -> {
            if (g == gen) {
                busy = false;
                loadingMore = false;
            }
            setStatus("⚠ " + Net.msg(e), true);
            MainActivity m = current;
            if (m != null) m.updateFooter();
        });
    }

    static void setStatus(String s, boolean error) {
        status = s;
        statusError = error;
        MainActivity m = current;
        if (m != null) m.showStatus();
    }

    static void okStatus() {
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        setStatus(interval > 0
                ? S.s(R.string.s_auto_refresh) + (interval < 60 ? interval + " mp" : S.s(R.string.s_1_minute)) + " · " + now
                : S.s(R.string.s_updated) + now, false);
    }

    // ---------------------------------------------------------------- felület

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        statusView = text("", 12, MUTED);
        statusView.setGravity(Gravity.CENTER);
        statusView.setSingleLine(landscape);
        statusView.setEllipsize(TextUtils.TruncateAt.END);

        if (!landscape) {
            topBar(root);

            // A cím, állapotsor, kereső, szűrő és a táblázat fejléce a lista fölött van, és görgetéskor
            // a listával együtt felfelé kicsúszik (mint a webes felületen).
            header = new LinearLayout(this);
            header.setOrientation(LinearLayout.VERTICAL);
            header.setBackgroundColor(BG);
            header.setClickable(true);

            LinearLayout titleRow = row();
            titleRow.setGravity(Gravity.CENTER);
            titleRow.setPadding(dp(16), dp(20), dp(16), dp(6));
            TextView title = text(S.s(R.string.s_query_log), 30, TEXT);
            title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            titleRow.addView(title);
            ImageButton refresh = iconButton(R.drawable.ic_refresh, S.s(R.string.s_refresh), v -> manualRefresh());
            refresh.setOnLongClickListener(v -> { startFresh(); return true; });
            refresh.setBackground(box(0, BLUE_BORDER, 2, 8));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(dp(48), dp(48));
            rl.leftMargin = dp(14);
            titleRow.addView(refresh, rl);
            header.addView(titleRow);

            statusView.setPadding(dp(16), 0, dp(16), dp(10));
            header.addView(statusView, matchWrap());

            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            sl.leftMargin = dp(28);
            sl.rightMargin = dp(28);
            header.addView(searchBox(true), sl);
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            fl.leftMargin = dp(28);
            fl.rightMargin = dp(28);
            fl.topMargin = dp(14);
            header.addView(filterButton(), fl);

            LinearLayout head = row();
            GradientDrawable hg = new GradientDrawable();
            hg.setColor(HEAD);
            float hr = dp(12);
            hg.setCornerRadii(new float[]{hr, hr, hr, hr, 0, 0, 0, 0});
            head.setBackground(hg);
            head.setPadding(dp(20), 0, dp(20), 0);
            head.addView(headText(S.s(R.string.s_time)), new LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.WRAP_CONTENT));
            head.addView(headText(S.s(R.string.s_request)));
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            hl.topMargin = dp(22);
            hl.leftMargin = dp(10);
            hl.rightMargin = dp(10);
            header.addView(head, hl);

            android.widget.FrameLayout area = new android.widget.FrameLayout(this);
            ListView lv = makeList();
            lv.setClipToPadding(false);
            lv.setPadding(dp(10), 0, dp(10), 0);
            area.addView(lv, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            area.addView(header, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                int h = b - t;
                if (h > 0 && h != headerHeight) {
                    headerHeight = h;
                    list.setPadding(dp(10), h, dp(10), 0);
                    Net.MAIN.post(this::syncHeader);
                }
            });
            addToTop(area);
            root.addView(area, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        } else {
            LinearLayout bar = row();
            bar.setPadding(dp(8), dp(6), dp(16), dp(4));
            bar.addView(iconButton(R.drawable.ic_menu, S.s(R.string.s_menu), this::showMainMenu), new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView title = text(S.s(R.string.s_query_log), 22, TEXT);
            title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            title.setSingleLine(true);
            bar.addView(title);
            ImageButton refresh = iconButton(R.drawable.ic_refresh, S.s(R.string.s_refresh), v -> manualRefresh());
            refresh.setOnLongClickListener(v -> { startFresh(); return true; });
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(dp(44), dp(44));
            rl.leftMargin = dp(6);
            rl.rightMargin = dp(10);
            bar.addView(refresh, rl);
            bar.addView(searchBox(false), new LinearLayout.LayoutParams(0, dp(40), 1));
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(dp(210), dp(40));
            fl.leftMargin = dp(12);
            fl.rightMargin = dp(12);
            bar.addView(filterButton(), fl);
            bar.addView(makePill());
            root.addView(bar);

            statusView.setPadding(dp(20), 0, dp(20), dp(4));
            statusView.setGravity(Gravity.START);
            root.addView(statusView, matchWrap());

            LinearLayout card = card();
            LinearLayout head = row();
            head.setBackgroundColor(HEAD);
            head.setPadding(dp(20), 0, dp(8), 0);
            head.addView(headText(S.s(R.string.s_time)), new LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.WRAP_CONTENT));
            head.addView(headText(S.s(R.string.s_request)), weight(1.5f));
            head.addView(headText(S.s(R.string.s_response)), weight(1f));
            head.addView(headText(S.s(R.string.s_client)), weight(1f));
            head.addView(new View(this), new LinearLayout.LayoutParams(dp(44), 1));
            card.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
            android.widget.FrameLayout area = new android.widget.FrameLayout(this);
            area.addView(makeList(), new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            addToTop(area);
            card.addView(area, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            cl.leftMargin = dp(12);
            cl.rightMargin = dp(12);
            root.addView(card, cl);
        }
        setContentView(root);
    }

    /** „Ugrás a tetejére” gomb a jobb alsó sarokban; lejjebb görgetve jelenik meg. */
    private void addToTop(android.widget.FrameLayout area) {
        toTop = iconButton(R.drawable.ic_arrow_up, S.s(R.string.s_jump_to_top_of_list), v -> {
            list.setSelection(0);
            syncHeader();
            toTop.setVisibility(View.GONE);
        });
        toTop.clearColorFilter();
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(BTN_GREEN);
        toTop.setBackground(g);
        toTop.setElevation(dp(6));
        toTop.setVisibility(View.GONE);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(dp(52), dp(52),
                Gravity.BOTTOM | Gravity.END);
        lp.rightMargin = dp(20);
        lp.bottomMargin = dp(24);
        area.addView(toTop, lp);
    }

    /** A fejléc annyival csúszik fel, amennyit a lista az elejéről elgörgetett. */
    void syncHeader() {
        if (list == null) return;
        int first = list.getFirstVisiblePosition();
        if (header != null && headerHeight > 0) {
            int shift;
            View c = list.getChildAt(0);
            if (first == 0 && c != null) shift = Math.max(0, Math.min(headerHeight, headerHeight - c.getTop()));
            else if (first == 0) shift = 0;
            else shift = headerHeight;
            header.setTranslationY(-shift);
        }
        if (toTop != null) {
            boolean show = first >= SHOW_TOP_AFTER;
            int vis = show ? View.VISIBLE : View.GONE;
            if (toTop.getVisibility() != vis) toTop.setVisibility(vis);
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        float r = dp(landscape ? 10 : 12);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        card.setBackground(g);
        card.setClipToOutline(true);
        return card;
    }

    private ListView makeList() {
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new GradientDrawable());
        list.setCacheColorHint(0);
        footer = text("", 14, MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(16), dp(18), dp(16), dp(18));
        footer.setOnClickListener(v -> loadMore());
        footer.setBackgroundColor(CARD);
        list.addFooterView(footer, null, false);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int s) { }
            @Override public void onScroll(AbsListView v, int first, int visible, int total) {
                syncHeader();
                if (!entries.isEmpty() && first + visible >= total - 4) loadMore();
            }
        });
        updateFooter();
        return list;
    }

    private View searchBox(boolean withHelp) {
        LinearLayout box = row();
        box.setBackground(box(0, BORDER, 1, 4));
        box.setPadding(dp(12), 0, dp(4), 0);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_search);
        icon.setColorFilter(ICON);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        searchEdit = new EditText(this);
        searchEdit.setBackground(null);
        searchEdit.setSingleLine(true);
        searchEdit.setTextColor(TEXT);
        searchEdit.setHintTextColor(HINT);
        searchEdit.setHint(S.s(R.string.s_domain_client_ip_or_name));
        searchEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, landscape ? 15 : 16);
        searchEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchEdit.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchEdit.setPadding(dp(10), 0, dp(4), 0);
        searchEdit.setText(search);
        searchEdit.setOnEditorActionListener((v, id, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (id == EditorInfo.IME_ACTION_SEARCH || enter) {
                applySearch(searchEdit.getText().toString().trim());
                return true;
            }
            return false;
        });
        box.addView(searchEdit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        box.addView(iconButton(R.drawable.ic_close, S.s(R.string.s_clear_search), v -> {
            searchEdit.setText("");
            applySearch("");
        }), new LinearLayout.LayoutParams(dp(40), dp(40)));
        if (withHelp) {
            box.addView(iconButton(R.drawable.ic_help, S.s(R.string.s_search_help), v -> showSearchHelp()),
                    new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
        return box;
    }

    private View filterButton() {
        LinearLayout b = row();
        b.setBackground(box(0, BORDER, 1, 4));
        b.setPadding(dp(14), 0, dp(12), 0);
        b.setClickable(true);
        b.setFocusable(true);
        b.setContentDescription(S.s(R.string.s_filter_by_response));
        ImageView chev = new ImageView(this);
        chev.setImageResource(R.drawable.ic_chevron);
        chev.setColorFilter(ICON);
        b.addView(chev, new LinearLayout.LayoutParams(dp(18), dp(18)));
        filterLabel = text(FILTER_LABELS()[filterIdx], landscape ? 15 : 16, TEXT);
        filterLabel.setSingleLine(true);
        filterLabel.setEllipsize(TextUtils.TruncateAt.END);
        filterLabel.setPadding(dp(12), 0, 0, 0);
        b.addView(filterLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        b.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(S.s(R.string.s_filter_by_response_2))
                .setSingleChoiceItems(FILTER_LABELS(), filterIdx, (d, w) -> {
                    d.dismiss();
                    if (w != filterIdx) {
                        filterIdx = w;
                        filterLabel.setText(FILTER_LABELS()[w]);
                        expandedKey = null;
                        startFresh();
                    }
                })
                .show());
        return b;
    }

    private void manualRefresh() {
        if (Net.api == null) {
            showServerDialog();
            return;
        }
        Net.loadProtection();
        if (busy) return;
        if (entries.isEmpty()) startFresh();
        else refreshNew();
    }

    private void applySearch(String s) {
        hideKeyboard();
        if (!s.equals(search) || entries.isEmpty()) {
            search = s;
            expandedKey = null;
            startFresh();
        } else {
            refreshNew();
        }
    }

    private void filterClient(Entry e) {
        String s = "\"" + e.client + "\"";
        if (searchEdit != null) searchEdit.setText(s);
        applySearch(s);
    }

    private void filterDomain(Entry e) {
        String s = "\"" + e.domain + "\"";
        if (searchEdit != null) searchEdit.setText(s);
        applySearch(s);
    }

    void renderList(int prepended, boolean toTop) {
        if (adapter == null) return;
        int first = list.getFirstVisiblePosition();
        View c = list.getChildAt(0);
        int top = c == null ? 0 : c.getTop();
        adapter.notifyDataSetChanged();
        updateFooter();
        if (toTop) {
            list.setSelection(0);
        } else if (prepended > 0 && (first > 0 || top < 0)) {
            list.setSelectionFromTop(first + prepended, top);
        }
        syncHeader();
    }

    void updateFooter() {
        if (footer == null) return;
        String t;
        if (loadingMore) t = S.s(R.string.s_loading_more);
        else if (entries.isEmpty()) t = loadedOnce && !busy ? S.s(R.string.s_no_results) : "";
        else if (more) t = S.s(R.string.s_load_more);
        else t = S.s(R.string.s_no_more_entries);
        footer.setText(t);
    }

    void showStatus() {
        if (statusView == null) return;
        statusView.setText(status);
        statusView.setTextColor(statusError ? RED : MUTED);
        statusView.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---------------------------------------------------------------- menük, párbeszédek

    @Override
    void addMenuItems(Menu m) {
        m.add(1, 101, 10, S.s(R.string.s_auto_refreshing));
        MenuItem sub = m.add(1, 102, 11, S.s(R.string.s_second_line_in_portrait_view));
        sub.setCheckable(true);
        sub.setChecked(showSub);
        MenuItem mk = m.add(1, 104, 12, S.s(R.string.s_highlight_new_rows));
        mk.setCheckable(true);
        mk.setChecked(markNew);
        MenuItem bp = m.add(1, 105, 13, S.s(R.string.s_sound_for_green_rows));
        bp.setCheckable(true);
        bp.setChecked(beepNew);
        bp.setEnabled(markNew);
        m.add(1, 103, 14, S.s(R.string.s_full_reload));
    }

    @Override
    boolean onMenuItem(int id) {
        switch (id) {
            case 101: intervalDialog(); return true;
            case 102:
                showSub = !showSub;
                prefs.edit().putBoolean("showSub", showSub).apply();
                if (adapter != null) adapter.notifyDataSetChanged();
                return true;
            case 103: startFresh(); return true;
            case 105:
                beepNew = !beepNew;
                prefs.edit().putBoolean("beepNew", beepNew).apply();
                if (beepNew && markNew) beep();
                return true;
            case 104:
                markNew = !markNew;
                prefs.edit().putBoolean("markNew", markNew).apply();
                if (adapter != null) adapter.notifyDataSetChanged();
                return true;
            default: return false;
        }
    }

    private void intervalDialog() {
        int sel = 0;
        for (int i = 0; i < INTERVALS.length; i++) if (INTERVALS[i] == interval) sel = i;
        new AlertDialog.Builder(this)
                .setTitle(S.s(R.string.s_auto_refresh_2))
                .setSingleChoiceItems(INTERVAL_LABELS(), sel, (d, w) -> {
                    d.dismiss();
                    interval = INTERVALS[w];
                    prefs.edit().putInt("interval", interval).apply();
                    okStatus();
                    schedule();
                })
                .show();
    }

    private void showSearchHelp() {
        new AlertDialog.Builder(this)
                .setTitle(S.s(R.string.s_search))
                .setMessage(S.s(R.string.s_you_can_search_by_domain_the_client_s)
                        + S.s(R.string.s_in_quotes_it_looks_for_an_exact_match)
                        + S.s(R.string.s_without_quotes_192_168_0_1_also_finds)
                        + S.s(R.string.s_the_search_runs_on_the_server_so_only))
                .setPositiveButton(S.s(R.string.s_ok), null)
                .show();
    }

    private void confirmRule(Entry e) {
        boolean unblock = e.kind == Entry.BLOCKED;
        new AlertDialog.Builder(this)
                .setTitle(unblock ? S.s(R.string.s_unblock) : S.s(R.string.s_block))
                .setMessage((unblock ? S.s(R.string.s_unblock_this_domain) : S.s(R.string.s_block_this_domain)) + e.domain
                        + S.s(R.string.s_the_rule_is_added_to_custom_rules))
                .setPositiveButton(unblock ? S.s(R.string.s_unblock) : S.s(R.string.s_block), (d, w) -> toggleRule(e))
                .setNegativeButton(S.s(R.string.s_cancel), null)
                .show();
    }

    private void copyDomain(Entry e) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("domain", e.domain));
        Toast.makeText(this, S.s(R.string.s_copied) + e.domain, Toast.LENGTH_SHORT).show();
    }

    private void rowMenu(View anchor, Entry e) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, e.kind == Entry.BLOCKED ? S.s(R.string.s_unblock) : S.s(R.string.s_block));
        m.add(0, 2, 1, S.s(R.string.s_only_this_client));
        m.add(0, 3, 2, S.s(R.string.s_only_this_domain));
        m.add(0, 4, 3, S.s(R.string.s_copy_domain));
        if (webDomain(e) != null) m.add(0, 6, 4, S.s(R.string.s_open_in_external_browser));
        m.add(0, 5, 5, S.s(R.string.s_details));
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: confirmRule(e); break;
                case 2: filterClient(e); break;
                case 3: filterDomain(e); break;
                case 4: copyDomain(e); break;
                case 5: detailDialog(e); break;
                case 6: openInBrowser(e); break;
                default: return false;
            }
            return true;
        });
        pm.show();
    }

    /** A domain, ha weboldalként megnyitható név (nem IP-visszakeresés, nem üres); különben null. */
    static String webDomain(Entry e) {
        String d = e.domain == null ? "" : e.domain.trim().toLowerCase(java.util.Locale.ROOT);
        if (d.isEmpty() || d.endsWith(".arpa") || d.indexOf('.') < 0) return null;
        if (!d.matches("[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?(\\.[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?)+")) return null;
        return d;
    }

    /** A domain megnyitása a telefon alapértelmezett böngészőjében (https://domain). */
    private void openInBrowser(Entry e) {
        String d = webDomain(e);
        if (d == null) return;
        Uri uri = new Uri.Builder().scheme("https").authority(d).path("/").build();
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, S.s(R.string.s_there_is_no_browser_on_the_phone), Toast.LENGTH_SHORT).show();
        }
    }

    private void detailDialog(Entry e) {
        StringBuilder sb = new StringBuilder();
        for (String[] r : detailRows(e)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(r[0]).append(": ").append(r[1]);
        }
        new AlertDialog.Builder(this)
                .setTitle(e.shown)
                .setMessage(sb.toString())
                .setPositiveButton(e.kind == Entry.BLOCKED ? S.s(R.string.s_unblock) : S.s(R.string.s_block), (d, w) -> confirmRule(e))
                .setNeutralButton(S.s(R.string.s_only_this_client), (d, w) -> filterClient(e))
                .setNegativeButton(S.s(R.string.s_close), null)
                .show();
    }

    // ---------------------------------------------------------------- sorok tartalma

    String listName(Entry e) {
        if (e.filterId >= 0) {
            String n = filterNames.get(e.filterId);
            if (n != null && !n.isEmpty()) return n;
        }
        return e.ruleText;
    }

    /** A válasz alatti második sor: blokkoló lista, szolgáltatás, átírás vagy válaszidő. */
    String detail(Entry e) {
        switch (e.kind) {
            case Entry.BLOCKED:
                if (!e.service.isEmpty()) return e.service;
                return listName(e);
            case Entry.ALLOWED:
                return listName(e);
            case Entry.REWRITTEN:
                return !e.answer.isEmpty() ? e.answer : listName(e);
            default:
                return e.cached ? e.elapsed + S.s(R.string.s_cached) : e.elapsed;
        }
    }

    List<String[]> detailRows(Entry e) {
        ArrayList<String[]> r = new ArrayList<>();
        r.add(new String[]{S.s(R.string.s_date), e.date + " " + e.time});
        r.add(new String[]{S.s(R.string.s_type), e.type + ", " + e.proto});
        r.add(new String[]{S.s(R.string.s_response), e.status});
        String ln = listName(e);
        if (e.filterId >= 0 && !ln.isEmpty() && !ln.equals(e.ruleText)) r.add(new String[]{S.s(R.string.s_list), ln});
        if (!e.service.isEmpty()) r.add(new String[]{S.s(R.string.s_service), e.service});
        if (!e.ruleText.isEmpty()) r.add(new String[]{S.s(R.string.s_rule), e.ruleText});
        if (!e.answer.isEmpty()) r.add(new String[]{S.s(R.string.s_response_ip), e.answer});
        if (!e.elapsed.isEmpty()) r.add(new String[]{S.s(R.string.s_response_time), e.elapsed + (e.cached ? S.s(R.string.s_from_cache) : "")});
        if (!e.upstream.isEmpty()) r.add(new String[]{"Upstream", e.upstream});
        r.add(new String[]{S.s(R.string.s_client), e.client + (e.clientName.isEmpty() ? "" : " (" + e.clientName + ")")});
        return r;
    }

    static int rowColor(Entry e) {
        switch (e.kind) {
            case Entry.BLOCKED: return BLOCK;
            case Entry.ALLOWED: return ALLOW;
            case Entry.REWRITTEN: return REWRITE;
            default: return CARD;
        }
    }

    static int lineColor(Entry e) {
        switch (e.kind) {
            case Entry.BLOCKED: return BLOCK_LINE;
            case Entry.ALLOWED: return ALLOW_LINE;
            case Entry.REWRITTEN: return REWRITE_LINE;
            default: return LINE;
        }
    }

    private final class Holder {
        LinearLayout main, detail;
        TextView time, date, domain, sub, status, statusSub, client, clientSub;
        View line;
        ImageButton more;
        Entry entry;
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int i) { return entries.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Holder h;
            View v = convert;
            if (v == null) {
                h = new Holder();
                v = landscape ? landscapeRow(h) : portraitRow(h);
                v.setTag(h);
            } else {
                h = (Holder) v.getTag();
            }
            Entry e = entries.get(pos);
            h.entry = e;
            h.main.setBackgroundColor(rowColor(e));
            h.line.setBackgroundColor(lineColor(e));
            h.time.setText(e.time);
            h.domain.setText(e.shown);
            int fg = markNew && freshKeys.contains(e.key) ? NEW : TEXT;
            h.time.setTextColor(fg);
            h.domain.setTextColor(fg);
            if (landscape) {
                h.date.setText(e.date);
                h.sub.setText(S.s(R.string.s_type_2) + e.type + ", " + e.proto);
                h.status.setText(e.status);
                h.statusSub.setText(detail(e));
                h.client.setText(e.client);
                h.clientSub.setText(e.clientName);
                h.clientSub.setVisibility(e.clientName.isEmpty() ? View.GONE : View.VISIBLE);
            } else {
                if (showSub) {
                    String d = detail(e);
                    String who = e.clientName.isEmpty() ? e.client : e.clientName;
                    h.sub.setText(e.status + (d.isEmpty() ? "" : " · " + d) + " · " + who);
                    h.sub.setVisibility(View.VISIBLE);
                    h.date.setText(e.date);
                    h.date.setVisibility(View.VISIBLE);
                } else {
                    h.sub.setVisibility(View.GONE);
                    h.date.setVisibility(View.GONE);
                }
                boolean open = e.key.equals(expandedKey);
                h.detail.setVisibility(open ? View.VISIBLE : View.GONE);
                if (open) fillDetail(h.detail, e);
            }
            return v;
        }
    }

    private View portraitRow(final Holder h) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        h.main = row();
        h.main.setMinimumHeight(dp(56));
        h.main.setPadding(dp(20), dp(10), dp(20), dp(10));
        h.main.setClickable(true);
        h.main.setFocusable(true);
        h.main.setOnClickListener(v -> {
            Entry e = h.entry;
            if (e == null) return;
            expandedKey = e.key.equals(expandedKey) ? null : e.key;
            adapter.notifyDataSetChanged();
        });
        h.main.setOnLongClickListener(v -> {
            if (h.entry != null) rowMenu(v, h.entry);
            return true;
        });
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        h.time = text("", 16, TEXT);
        h.time.setFontFeatureSettings("tnum");
        h.date = text("", 12, SUB);
        h.date.setFontFeatureSettings("tnum");
        tcol.addView(h.time);
        tcol.addView(h.date);
        h.main.addView(tcol, new LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        h.domain = text("", 16, TEXT);
        h.domain.setSingleLine(true);
        h.domain.setEllipsize(TextUtils.TruncateAt.END);
        h.sub = text("", 12, SUB);
        h.sub.setSingleLine(true);
        h.sub.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(h.domain);
        col.addView(h.sub);
        h.main.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(h.main, matchWrap());
        h.detail = new LinearLayout(this);
        h.detail.setOrientation(LinearLayout.VERTICAL);
        h.detail.setBackgroundColor(DETAIL);
        h.detail.setPadding(dp(20), dp(12), dp(20), dp(14));
        root.addView(h.detail, matchWrap());
        h.line = new View(this);
        root.addView(h.line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        return root;
    }

    private void fillDetail(LinearLayout box, final Entry e) {
        box.removeAllViews();
        for (String[] r : detailRows(e)) {
            LinearLayout line = row();
            line.setGravity(Gravity.TOP);
            line.setPadding(0, dp(3), 0, dp(3));
            TextView k = text(r[0], 14, LABEL);
            line.addView(k, new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView val = text(r[1], 14, TEXT);
            if (S.s(R.string.s_response).equals(r[0])) {
                val.setTypeface(Typeface.DEFAULT_BOLD);
                val.setTextColor(e.kind == Entry.BLOCKED ? BAD_TEXT : e.kind == Entry.PROCESSED ? OK_TEXT : INFO_TEXT);
            }
            val.setTextIsSelectable(false);
            line.addView(val, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            box.addView(line, matchWrap());
        }
        LinearLayout buttons = row();
        buttons.setPadding(0, dp(10), 0, 0);
        boolean unblock = e.kind == Entry.BLOCKED;
        TextView act = pillButton(unblock ? S.s(R.string.s_unblock) : S.s(R.string.s_block), unblock ? BTN_GREEN : BTN_RED, 0);
        act.setOnClickListener(v -> confirmRule(e));
        buttons.addView(act, wrapH(44));
        TextView only = pillButton(S.s(R.string.s_only_this_client), 0, BTN_LINE);
        only.setOnClickListener(v -> filterClient(e));
        LinearLayout.LayoutParams ol = wrapH(44);
        ol.leftMargin = dp(8);
        buttons.addView(only, ol);
        ImageButton copy = iconButton(R.drawable.ic_copy, S.s(R.string.s_copy_domain), v -> copyDomain(e));
        copy.setBackground(box(0, BTN_LINE, 1, 8));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(44), dp(44));
        cl.leftMargin = dp(8);
        buttons.addView(copy, cl);
        box.addView(buttons, matchWrap());
    }

    private View landscapeRow(final Holder h) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        h.main = row();
        h.main.setMinimumHeight(dp(60));
        h.main.setPadding(dp(20), dp(6), dp(8), dp(6));
        h.main.setClickable(true);
        h.main.setFocusable(true);
        h.main.setOnClickListener(v -> { if (h.entry != null) detailDialog(h.entry); });

        LinearLayout c1 = col();
        h.time = text("", 15, TEXT);
        h.time.setFontFeatureSettings("tnum");
        h.date = text("", 12, SUB);
        c1.addView(h.time);
        c1.addView(h.date);
        h.main.addView(c1, new LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout c2 = col();
        h.domain = text("", 15, TEXT);
        h.domain.setSingleLine(true);
        h.domain.setEllipsize(TextUtils.TruncateAt.END);
        h.sub = text("", 12, SUB);
        h.sub.setSingleLine(true);
        c2.addView(h.domain);
        c2.addView(h.sub);
        c2.setPadding(0, 0, dp(10), 0);
        h.main.addView(c2, weight(1.5f));

        LinearLayout c3 = col();
        h.status = text("", 15, TEXT);
        h.status.setSingleLine(true);
        h.statusSub = text("", 12, SUB);
        h.statusSub.setSingleLine(true);
        h.statusSub.setEllipsize(TextUtils.TruncateAt.END);
        c3.addView(h.status);
        c3.addView(h.statusSub);
        c3.setPadding(0, 0, dp(10), 0);
        h.main.addView(c3, weight(1f));

        LinearLayout c4 = col();
        h.client = text("", 15, TEXT);
        h.client.setSingleLine(true);
        h.clientSub = text("", 12, SUB);
        h.clientSub.setSingleLine(true);
        h.clientSub.setEllipsize(TextUtils.TruncateAt.END);
        c4.addView(h.client);
        c4.addView(h.clientSub);
        h.main.addView(c4, weight(1f));

        h.more = iconButton(R.drawable.ic_more, S.s(R.string.s_actions), v -> { if (h.entry != null) rowMenu(v, h.entry); });
        h.main.addView(h.more, new LinearLayout.LayoutParams(dp(44), dp(44)));

        root.addView(h.main, matchWrap());
        h.line = new View(this);
        root.addView(h.line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        return root;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && searchEdit != null) imm.hideSoftInputFromWindow(searchEdit.getWindowToken(), 0);
        if (searchEdit != null) searchEdit.clearFocus();
    }
}
