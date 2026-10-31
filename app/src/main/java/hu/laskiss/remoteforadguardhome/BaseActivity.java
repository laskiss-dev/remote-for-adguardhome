package hu.laskiss.remoteforadguardhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Közös alap minden oldalhoz: sötét színek, fejléc a BE/KI jelvénnyel,
 * a webes oldalsáv mintájára felépített menü, és űrlap-segédek.
 */
public abstract class BaseActivity extends Activity {

    // Színek: a téma szerint töltődnek be (sötét: a webes sötét téma, világos: a webes világos téma).
    static int BG, CARD, HEAD, LINE, BLOCK, BLOCK_LINE, ALLOW, ALLOW_LINE, REWRITE, REWRITE_LINE,
            TEXT, SUB, MUTED, GREEN, RED, BORDER, DETAIL, BAR_LINE, BLUE_BORDER, BTN_GREEN, BTN_RED, BTN_LINE,
            ICON, HINT, LABEL, NEW, OK_TEXT, BAD_TEXT, INFO_TEXT;
    /** A jelenleg érvényes téma világos-e. */
    static boolean light;

    static {
        palette(false);
    }

    static void palette(boolean l) {
        light = l;
        if (!l) {
            BG = 0xFF1A1A1A; CARD = 0xFF1F1F1F; HEAD = 0xFF3D3D3D; LINE = 0xFF3A3A3A;
            BLOCK = 0xFF5D3D35; BLOCK_LINE = 0xFF6E4A41; ALLOW = 0xFF29402F; ALLOW_LINE = 0xFF395A42;
            REWRITE = 0xFF2B3747; REWRITE_LINE = 0xFF3B4C61; TEXT = 0xFFE8E8E8; SUB = 0xFFC2C2C2;
            MUTED = 0xFF9E9E9E; GREEN = 0xFF67B279; RED = 0xFFE57373; BORDER = 0xFF444444;
            DETAIL = 0xFF282828; BAR_LINE = 0xFF2C2C2C; BLUE_BORDER = 0xFF2F3A4A;
            BTN_GREEN = 0xFF3F7D4F; BTN_RED = 0xFFA2493A; BTN_LINE = 0xFF555555;
            ICON = 0xFFBDBDBD; HINT = 0xFF777777; LABEL = 0xFFA8A8A8; NEW = 0xFF8FD19E;
            OK_TEXT = 0xFF8FD19E; BAD_TEXT = 0xFFF0A08C; INFO_TEXT = 0xFF9FC3EB;
        } else {
            BG = 0xFFF4F5F7; CARD = 0xFFFFFFFF; HEAD = 0xFFE9EBEE; LINE = 0xFFE0E2E5;
            BLOCK = 0xFFF9DCD7; BLOCK_LINE = 0xFFEFC6BE; ALLOW = 0xFFDFF3E5; ALLOW_LINE = 0xFFC4E5CD;
            REWRITE = 0xFFDEE9F7; REWRITE_LINE = 0xFFC5D7EE; TEXT = 0xFF222222; SUB = 0xFF555555;
            MUTED = 0xFF676767; GREEN = 0xFF4E9A5E; RED = 0xFFC62828; BORDER = 0xFFC4C7CC;
            DETAIL = 0xFFF0F1F3; BAR_LINE = 0xFFDDDFE2; BLUE_BORDER = 0xFFB7C6DA;
            BTN_GREEN = 0xFF3F7D4F; BTN_RED = 0xFFA2493A; BTN_LINE = 0xFFB3B6BB;
            ICON = 0xFF5F6368; HINT = 0xFF8A8A8A; LABEL = 0xFF666666; NEW = 0xFF2E7D32;
            OK_TEXT = 0xFF2E7D32; BAD_TEXT = 0xFFB3261E; INFO_TEXT = 0xFF1F5FA8;
        }
    }

    /** Témaválasztás: 0 = rendszer szerint, 1 = sötét, 2 = világos. */
    static boolean wantsLight(android.content.Context c, SharedPreferences p) {
        int mode = p.getInt("theme", 1);
        if (mode == 2) return true;
        if (mode == 1) return false;
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night != Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean createdLight;

    // Menüpontok azonosítói
    static final int NAV_DASH = 1, NAV_LOG = 2, NAV_SERVER = 3, NAV_INFO = 4, NAV_APPSET = 5,
            NAV_GENERAL = 10, NAV_CLIENTS = 11, NAV_DNS = 12, NAV_DHCP = 13,
            NAV_BLOCKLISTS = 20, NAV_ALLOWLISTS = 21, NAV_REWRITES = 22, NAV_SERVICES = 23, NAV_RULES = 24;

    protected boolean landscape;
    protected SharedPreferences prefs;
    protected TextView pill, serverLabel, pageStatus;
    protected LinearLayout content;

    interface Job<T> { T run() throws Exception; }
    interface Done<T> { void ok(T r); }

    @Override
    protected void onCreate(Bundle b) {
        prefs = getSharedPreferences("beallitasok", MODE_PRIVATE);
        palette(wantsLight(this, prefs));
        createdLight = light;
        setTheme(light ? R.style.TemaVilagos : R.style.Tema);
        super.onCreate(b);
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (Net.api == null) {
            Servers.Server s = Servers.active(prefs);
            if (s != null) Servers.connect(s);
        }
        Net.top = this;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wantsLight(this, prefs) != createdLight) {
            // Közben a beállításokban témát váltottak: ez az oldal is újrarajzolódik.
            palette(wantsLight(this, prefs));
            recreate();
            return;
        }
        applyKeepAwake();
        Net.top = this;
        showProtection();
        Net.loadProtection();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Net.top == this) Net.top = null;
    }

    /** Képernyő ébren tartása, amíg az app előtérben van (ha be van kapcsolva). */
    void applyKeepAwake() {
        if (prefs.getBoolean("keepAwake", false)) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /** Az oldal adatainak (újra)betöltése; a frissítés gomb ezt hívja. */
    void load() { }

    /** A szerver adatainak megváltozása után. */
    void onServerChanged() { load(); }

    // ---------------------------------------------------------------- oldalváz

    /** Az egyszerű oldalak váza: fejléc, görgethető tartalom címmel és frissítés gombbal. */
    protected void setupPage(String title) {
        LinearLayout root = col();
        root.setBackgroundColor(BG);
        topBar(root);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout body = col();
        int side = dp(landscape ? 28 : 16);
        body.setPadding(side, dp(18), side, dp(32));
        LinearLayout titleRow = row();
        TextView t = text(title, landscape ? 26 : 28, TEXT);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        titleRow.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton refresh = iconButton(R.drawable.ic_refresh, "Frissítés", v -> load());
        refresh.setBackground(box(0, BLUE_BORDER, 2, 8));
        titleRow.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
        body.addView(titleRow, matchWrap());
        pageStatus = text("", 13, MUTED);
        pageStatus.setPadding(0, dp(6), 0, 0);
        pageStatus.setVisibility(View.GONE);
        body.addView(pageStatus, matchWrap());
        content = col();
        body.addView(content, matchWrap());
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        if (Net.api == null) Net.MAIN.post(this::showServerDialog);
    }

    protected void topBar(LinearLayout root) {
        LinearLayout bar = row();
        bar.setPadding(dp(6), 0, dp(16), 0);
        bar.setMinimumHeight(dp(56));
        bar.addView(iconButton(R.drawable.ic_menu, "Menü", this::showMainMenu), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout brand = col();
        TextView name = text("Remote for AdGuard Home", 17, TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setLetterSpacing(0.03f);
        serverLabel = text("", 11, MUTED);
        serverLabel.setSingleLine(true);
        brand.addView(name);
        brand.addView(serverLabel);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        bl.leftMargin = dp(8);
        bar.addView(brand, bl);
        bar.addView(makePill());
        root.addView(bar);
        View sep = new View(this);
        sep.setBackgroundColor(BAR_LINE);
        root.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        showServer();
    }

    protected void setPageStatus(String s, boolean error) {
        if (pageStatus == null) return;
        pageStatus.setText(s);
        pageStatus.setTextColor(error ? RED : MUTED);
        pageStatus.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Háttérben futtat egy kérést; siker után a felületen folytatja, hiba esetén jelzi. */
    protected <T> void task(final Job<T> job, final Done<T> done) {
        if (Net.api == null) {
            // A szerver kiválasztását / jelszókérést az oldal betöltése már feldobta.
            setPageStatus("Nincs kiválasztott szerver. A ☰ menü Szerverek pontjában adhatsz meg egyet.", true);
            return;
        }
        Net.NET.execute(() -> {
            try {
                final T r = job.run();
                Net.MAIN.post(() -> { if (!isDestroyed()) done.ok(r); });
            } catch (Exception e) {
                final String m = Net.msg(e);
                Net.MAIN.post(() -> {
                    if (isDestroyed()) return;
                    setPageStatus("⚠ " + m, true);
                    Toast.makeText(this, m, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Mentés: lefuttatja, kiírja az üzenetet, majd újratölti az oldalt. */
    protected void save(final Job<?> job, final String okText) {
        task(job, r -> {
            Toast.makeText(this, okText, Toast.LENGTH_SHORT).show();
            load();
        });
    }

    /** Azonnali kapcsoló: lefuttatja és kiírja az üzenetet, az oldal nem töltődik újra. */
    protected void apply(final Job<?> job, final String okText) {
        task(job, r -> Toast.makeText(this, okText, Toast.LENGTH_SHORT).show());
    }

    protected void loading() {
        setPageStatus("Betöltés…", false);
    }

    protected void loaded() {
        setPageStatus("", false);
        if (content != null) content.removeAllViews();
    }

    // ---------------------------------------------------------------- menü és navigáció

    void showMainMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, NAV_DASH, 0, "Irányítópult");
        SubMenu set = m.addSubMenu(0, 90, 1, "Beállítások");
        set.add(0, NAV_GENERAL, 0, "Általános beállítások");
        set.add(0, NAV_DNS, 1, "DNS beállítások");
        set.add(0, NAV_CLIENTS, 2, "Kliens beállítások");
        set.add(0, NAV_DHCP, 3, "DHCP beállítások");
        SubMenu fil = m.addSubMenu(0, 91, 2, "Szűrők");
        fil.add(0, NAV_BLOCKLISTS, 0, "DNS blokkolási listák");
        fil.add(0, NAV_ALLOWLISTS, 1, "DNS engedélyezési listák");
        fil.add(0, NAV_REWRITES, 2, "DNS átírások");
        fil.add(0, NAV_SERVICES, 3, "Blokkolt szolgáltatások");
        fil.add(0, NAV_RULES, 4, "Egyéni szűrési szabályok");
        m.add(0, NAV_LOG, 3, "Lekérdezési napló");
        addMenuItems(m);
        m.add(1, NAV_SERVER, 20, "Szerverek");
        m.add(1, NAV_APPSET, 21, "Alkalmazás beállítások");
        m.add(1, NAV_INFO, 22, "Információ");
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 90 || id == 91) return false;
            if (onMenuItem(id)) return true;
            switch (id) {
                case NAV_SERVER: open(ServersActivity.class, 0); return true;
                case NAV_INFO: open(InfoActivity.class, 0); return true;
                case NAV_APPSET: open(AppSettingsActivity.class, 0); return true;
                case NAV_DASH: open(DashboardActivity.class, 0); return true;
                case NAV_LOG: open(MainActivity.class, 0); return true;
                case NAV_GENERAL: open(GeneralActivity.class, 0); return true;
                case NAV_CLIENTS: open(ClientsActivity.class, 0); return true;
                case NAV_DNS: open(DnsActivity.class, 0); return true;
                case NAV_DHCP: open(DhcpActivity.class, 0); return true;
                case NAV_BLOCKLISTS: open(FilterListsActivity.class, 0); return true;
                case NAV_ALLOWLISTS: open(FilterListsActivity.class, 1); return true;
                case NAV_REWRITES: open(RewritesActivity.class, 0); return true;
                case NAV_SERVICES: open(ServicesActivity.class, 0); return true;
                case NAV_RULES: open(RulesActivity.class, 0); return true;
                default: return false;
            }
        });
        pm.show();
    }

    /** Az oldal saját menüpontjai (azonosító 100-tól). */
    void addMenuItems(Menu m) { }

    boolean onMenuItem(int id) { return false; }

    void open(Class<?> c, int mode) {
        if (c == getClass() && getIntent().getIntExtra("mode", 0) == mode) return;
        Intent i = new Intent(this, c);
        i.putExtra("mode", mode);
        // Az Irányítópult a kezdőképernyő: minden oldal fölé nyílik, és vissza gombbal oda jutsz.
        if (c == DashboardActivity.class) i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        if (!(this instanceof DashboardActivity)) finish();
    }

    /** A lekérdezési napló megnyitása egy kereséssel (pl. egy domainre vagy kliensre). */
    void openLog(String search) {
        openLog(search, 0);
    }

    /** A napló megnyitása kereséssel és válasz-szűrővel (a szűrő a MainActivity.FILTER_KEYS sorszáma). */
    void openLog(String search, int filter) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("search", search);
        i.putExtra("filter", filter);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        if (!(this instanceof DashboardActivity) && !(this instanceof MainActivity)) finish();
    }

    // ---------------------------------------------------------------- védelem és szerver

    protected TextView makePill() {
        pill = text("…", 13, 0xFFFFFFFF);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(9), dp(4), dp(9), dp(4));
        pill.setMinWidth(dp(38));
        pill.setOnClickListener(v -> protectionDialog());
        showProtection();
        return pill;
    }

    void showProtection() {
        if (pill == null) return;
        Boolean p = Net.protection;
        if (p == null) {
            pill.setText("…");
            pill.setBackground(box(0xFF555555, 0, 0, 4));
        } else if (p) {
            pill.setText("BE");
            pill.setBackground(box(GREEN, 0, 0, 4));
        } else {
            long min = Net.protectionPause > 0 ? Math.max(1, Math.round(Net.protectionPause / 60000.0)) : 0;
            pill.setText(min > 0 ? "KI · " + min + " p" : "KI");
            pill.setBackground(box(0xFFB0473A, 0, 0, 4));
        }
        pill.setContentDescription(p == null ? "Védelem állapota" : p ? "Védelem bekapcsolva" : "Védelem kikapcsolva");
    }

    void protectionDialog() {
        if (Net.api == null) {
            showServerDialog();
            return;
        }
        if (Net.protection != null && !Net.protection) {
            new AlertDialog.Builder(this)
                    .setTitle("Védelem bekapcsolása")
                    .setMessage("Bekapcsolod a DNS-védelmet?")
                    .setPositiveButton("Bekapcsolás", (d, w) -> Net.setProtection(true, 0))
                    .setNegativeButton("Mégse", null)
                    .show();
            return;
        }
        final String[] labels = {"1 percre", "10 percre", "1 órára", "8 órára", "Amíg vissza nem kapcsolom"};
        final long[] ms = {60_000L, 600_000L, 3_600_000L, 28_800_000L, 0L};
        new AlertDialog.Builder(this)
                .setTitle("Védelem kikapcsolása")
                .setItems(labels, (d, w) -> Net.setProtection(false, ms[w]))
                .setNegativeButton("Mégse", null)
                .show();
    }

    protected void showServer() {
        if (serverLabel == null) return;
        Servers.Server sv = Net.server;
        serverLabel.setText(Net.api == null || sv == null ? "nincs szerver kiválasztva" : sv.label());
    }

    /**
     * Ha nincs kapcsolat: szerver nélkül az új szerver felvétele nyílik meg, mentett jelszó
     * nélküli szervernél a jelszókérés, egyébként a Szerverek oldal.
     */
    void showServerDialog() {
        if (isFinishing()) return;
        Servers.Server active = Servers.active(prefs);
        if (active == null) {
            editServer(null);
        } else if (Servers.password(active) == null) {
            askPassword(active);
        } else if (!(this instanceof ServersActivity)) {
            open(ServersActivity.class, 0);
        }
    }

    /** A szerver aktívvá tétele és a kapcsolat felépítése; minden oldal a nulláról töltődik. */
    void activate(Servers.Server s) {
        Servers.setActive(prefs, s.id);
        Net.api = null;
        Net.server = null;
        Net.protection = null;
        MainActivity.resetState();
        if (!Servers.connect(s)) {
            askPassword(s);
            return;
        }
        showServer();
        showProtection();
        Net.loadProtection();
        onServerChanged();
    }

    /** Jelszókérés olyan szervernél, ahol a jelszó nincs mentve (vagy nem olvasható vissza). */
    void askPassword(final Servers.Server s) {
        LinearLayout f = col();
        f.setPadding(dp(22), dp(8), dp(22), 0);
        TextView who = text(s.label() + (s.user.isEmpty() ? "" : "\nFelhasználó: " + s.user), 14, SUB);
        f.addView(who);
        final EditText pass = field(f, "Jelszó", "", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final Switch save = switchRow(f, "Jelszó mentése", "Titkosítva tárolja ezen a telefonon.", s.savePass);
        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Jelszó szükséges")
                .setView(f)
                .setPositiveButton("Belépés", null)
                .setNegativeButton("Szerverek", (x, w) -> { if (!(this instanceof ServersActivity)) open(ServersActivity.class, 0); })
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<Servers.Server> list = Servers.load(prefs);
            Servers.Server cur = Servers.find(list, s.id);
            if (cur == null) cur = s;
            cur.savePass = save.isChecked();
            Servers.setPassword(cur, pass.getText().toString());
            Servers.save(prefs, list);
            d.dismiss();
            activate(cur);
        }));
        d.show();
    }

    /**
     * Szerver felvétele (s == null) vagy szerkesztése: név, cím, HTTPS/HTTP, felhasználó, jelszó,
     * jelszó mentése, kapcsolat tesztelése. HTTP nyilvános címre csak külön megerősítéssel.
     */
    void editServer(final Servers.Server orig) {
        if (isFinishing()) return;
        final boolean isNew = orig == null;
        final Address cur = isNew ? null : orig.address();
        LinearLayout f = col();
        f.setPadding(dp(22), dp(8), dp(22), dp(8));
        final EditText name = field(f, "Név", "pl. Otthon", isNew ? "" : orig.name, InputType.TYPE_CLASS_TEXT);
        final EditText url = field(f, "Cím", "pl. 192.168.1.10:3000", cur == null ? (isNew ? "" : orig.url) : cur.display(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final Choice proto = new Choice(f, "Kapcsolat", new String[]{"HTTPS (titkosított, ajánlott)", "HTTP (titkosítatlan)"},
                cur == null || cur.https() ? 0 : 1);
        final EditText user = field(f, "Felhasználónév", "", isNew ? "" : orig.user,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final boolean hadSaved = !isNew && orig.savePass && !orig.encPass.isEmpty();
        final EditText pass = field(f, "Jelszó", hadSaved ? "(mentve – üresen változatlan marad)" : "", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final Switch save = switchRow(f, "Jelszó mentése",
                "Titkosítva tárolja ezen a telefonon. Kikapcsolva az app minden indításkor kéri.",
                isNew || orig.savePass);
        TextView note = text("A cím ugyanaz, amin a böngészőben a webes felületet megnyitod. "
                + "HTTP-n a jelszó titkosítatlanul megy át; ezt az app csak otthoni címekre (pl. 192.168.x.x) "
                + "engedi magától.", 12, MUTED);
        note.setPadding(0, dp(10), 0, 0);
        f.addView(note);
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Új szerver" : "Szerver szerkesztése")
                .setView(sv)
                .setPositiveButton("Mentés", null)
                .setNeutralButton("Kapcsolat tesztelése", null)
                .setNegativeButton("Mégse", null)
                .create();
        final boolean[] publicHttpOk = {!isNew && orig.allowPublicHttp};
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                final Address a = readAddress(url, proto);
                if (a == null) return;
                confirmHttp(a, publicHttpOk, () -> {
                    List<Servers.Server> list = Servers.load(prefs);
                    Servers.Server s = isNew ? new Servers.Server() : Servers.find(list, orig.id);
                    if (s == null) s = new Servers.Server();
                    String n = name.getText().toString().trim();
                    s.name = n.isEmpty() ? a.host : n;
                    s.url = a.base();
                    s.allowPublicHttp = publicHttpOk[0] && !a.https() && !a.isLocal();
                    String newUser = user.getText().toString().trim();
                    boolean userChanged = !newUser.equals(s.user);
                    s.user = newUser;
                    String pw = pass.getText().toString();
                    String keep = (pw.isEmpty() && !userChanged) ? Servers.password(s) : null;
                    s.savePass = save.isChecked();
                    Servers.setPassword(s, keep != null ? keep : pw);
                    if (isNew || Servers.find(list, s.id) == null) list.add(s);
                    Servers.save(prefs, list);
                    d.dismiss();
                    boolean isActive = Net.server == null || Net.server.id.equals(s.id) || isNew;
                    if (isActive) activate(s);
                    onServersChanged();
                });
            });
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                final Address a = readAddress(url, proto);
                if (a == null) return;
                confirmHttp(a, publicHttpOk, () -> {
                    String pw = pass.getText().toString();
                    if (pw.isEmpty() && !isNew) {
                        String old = Servers.password(orig);
                        if (old != null) pw = old;
                    }
                    final Api test = new Api(a, user.getText().toString().trim(), pw, publicHttpOk[0]);
                    Toast.makeText(this, "Kapcsolódás…", Toast.LENGTH_SHORT).show();
                    Net.NET.execute(() -> {
                        String res;
                        boolean ok;
                        try {
                            JSONObject st = test.getJson("status");
                            res = "Sikeres kapcsolat.\nAdGuard Home " + st.optString("version", "?");
                            ok = true;
                        } catch (Exception e) {
                            res = Net.msg(e);
                            ok = false;
                        }
                        final String r = res;
                        final boolean good = ok;
                        Net.MAIN.post(() -> {
                            if (isDestroyed()) return;
                            new AlertDialog.Builder(this)
                                    .setTitle(good ? "Kapcsolat rendben" : "Nem sikerült kapcsolódni")
                                    .setMessage(r)
                                    .setPositiveButton("Rendben", null)
                                    .show();
                        });
                    });
                });
            });
        });
        d.show();
    }

    /** A Szerverek oldal frissítése mentés után (más oldalakon nincs teendő). */
    void onServersChanged() { }

    private Address readAddress(EditText url, Choice proto) {
        try {
            Address a = Address.parse(url.getText().toString(), proto.sel == 0 ? "https" : "http");
            // Ha a címbe írt séma eltér a választótól, a választót igazítjuk hozzá.
            proto.set(a.https() ? 0 : 1);
            return a;
        } catch (Address.Invalid e) {
            url.setError(e.getMessage());
            return null;
        }
    }

    /** Titkosítatlan HTTP nyilvános címre: csak kifejezett megerősítés után. */
    private void confirmHttp(Address a, final boolean[] ok, final Runnable then) {
        if (a.https() || a.isLocal() || ok[0]) {
            then.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Titkosítatlan kapcsolat")
                .setMessage("A " + a.display() + " nem otthoni hálózati cím. HTTP-n a felhasználónév és a jelszó "
                        + "titkosítatlanul menne át az interneten, amit bárki láthat, aki a forgalmat figyeli.\n\n"
                        + "Ajánlott helyette HTTPS-t használni. Biztosan HTTP-vel akarsz kapcsolódni?")
                .setPositiveButton("Igen, HTTP-vel", (x, w) -> {
                    ok[0] = true;
                    then.run();
                })
                .setNegativeButton("Mégse", null)
                .show();
    }

    // ---------------------------------------------------------------- űrlap-segédek

    /** Egy kártya címmel és opcionális leírással; a visszaadott elembe kerülnek a sorok. */
    protected LinearLayout section(String title, String desc) {
        LinearLayout card = col();
        card.setBackground(box(CARD, LINE, 1, 10));
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        TextView h = text(title, 18, TEXT);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(h);
        if (desc != null && !desc.isEmpty()) {
            TextView d = text(desc, 13, MUTED);
            d.setPadding(0, dp(4), 0, 0);
            card.addView(d);
        }
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(16);
        content.addView(card, lp);
        return card;
    }

    protected Switch switchRow(LinearLayout parent, String label, String desc, boolean value) {
        LinearLayout r = row();
        r.setPadding(0, dp(10), 0, dp(4));
        LinearLayout c = col();
        c.addView(text(label, 16, TEXT));
        if (desc != null && !desc.isEmpty()) c.addView(text(desc, 12, MUTED));
        r.addView(c, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch s = new Switch(this);
        s.setChecked(value);
        s.setContentDescription(label);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.leftMargin = dp(12);
        r.addView(s, sl);
        parent.addView(r, matchWrap());
        return s;
    }

    protected EditText field(LinearLayout parent, String label, String hint, String value, int type) {
        TextView l = text(label, 13, MUTED);
        l.setPadding(0, dp(12), 0, 0);
        parent.addView(l);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(TEXT);
        e.setHintTextColor(HINT);
        parent.addView(e, matchWrap());
        return e;
    }

    protected EditText number(LinearLayout parent, String label, long value) {
        return field(parent, label, "", String.valueOf(value), InputType.TYPE_CLASS_NUMBER);
    }

    /** Többsoros mező (soronként egy elem), fix szélességű betűvel. */
    protected EditText area(LinearLayout parent, String label, String hint, String value, int minLines) {
        if (label != null) {
            TextView l = text(label, 13, MUTED);
            l.setPadding(0, dp(12), 0, dp(6));
            parent.addView(l);
        }
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setSingleLine(false);
        e.setMinLines(minLines);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setTypeface(Typeface.MONOSPACE);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        e.setTextColor(TEXT);
        e.setHintTextColor(HINT);
        e.setHint(hint);
        e.setText(value);
        e.setBackground(box(DETAIL, BORDER, 1, 6));
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        parent.addView(e, matchWrap());
        return e;
    }

    /** Választó: egy sor a jelenlegi értékkel, koppintásra lista. */
    protected final class Choice {
        int sel;
        final String[] labels;
        final TextView value;

        Choice(LinearLayout parent, final String label, String[] labels, int sel) {
            this.labels = labels;
            this.sel = Math.max(0, Math.min(sel, labels.length - 1));
            TextView l = text(label, 13, MUTED);
            l.setPadding(0, dp(12), 0, dp(6));
            parent.addView(l);
            LinearLayout b = row();
            b.setBackground(box(0, BORDER, 1, 4));
            b.setPadding(dp(14), 0, dp(12), 0);
            b.setMinimumHeight(dp(46));
            b.setClickable(true);
            b.setFocusable(true);
            b.setContentDescription(label);
            ImageView chev = new ImageView(BaseActivity.this);
            chev.setImageResource(R.drawable.ic_chevron);
            chev.setColorFilter(ICON);
            b.addView(chev, new LinearLayout.LayoutParams(dp(18), dp(18)));
            value = text(labels[this.sel], 16, TEXT);
            value.setPadding(dp(12), 0, 0, 0);
            b.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            b.setOnClickListener(v -> new AlertDialog.Builder(BaseActivity.this)
                    .setTitle(label)
                    .setSingleChoiceItems(Choice.this.labels, Choice.this.sel, (d, w) -> {
                        d.dismiss();
                        set(w);
                    })
                    .show());
            parent.addView(b, matchWrap());
        }

        Runnable onChange;

        void set(int w) {
            sel = w;
            value.setText(labels[w]);
            if (onChange != null) onChange.run();
        }
    }

    protected LinearLayout buttonRow(LinearLayout parent) {
        LinearLayout r = row();
        r.setPadding(0, dp(14), 0, 0);
        parent.addView(r, matchWrap());
        return r;
    }

    /** Gomb egy gombsorba; primary: zöld kitöltés, danger: piros, egyébként keretes. */
    protected TextView button(LinearLayout rowParent, String label, int style, View.OnClickListener l) {
        int fill = style == 1 ? BTN_GREEN : style == 2 ? BTN_RED : 0;
        TextView t = pillButton(label, fill, fill == 0 ? BTN_LINE : 0);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = wrapH(44);
        if (rowParent.getChildCount() > 0) lp.leftMargin = dp(8);
        rowParent.addView(t, lp);
        return t;
    }

    protected void kv(LinearLayout parent, String k, String v) {
        LinearLayout line = row();
        line.setGravity(Gravity.TOP);
        line.setPadding(0, dp(4), 0, dp(4));
        line.addView(text(k, 14, LABEL), new LinearLayout.LayoutParams(dp(130), ViewGroup.LayoutParams.WRAP_CONTENT));
        line.addView(text(v, 14, TEXT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(line, matchWrap());
    }

    protected View divider(LinearLayout parent) {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        parent.addView(v, lp);
        return v;
    }

    protected void confirm(String title, String message, String yes, final Runnable r) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(yes, (d, w) -> r.run())
                .setNegativeButton("Mégse", null)
                .show();
    }

    protected void emptyNote(LinearLayout parent, String s) {
        TextView t = text(s, 14, MUTED);
        t.setPadding(0, dp(12), 0, dp(4));
        parent.addView(t);
    }

    // ---------------------------------------------------------------- JSON-segédek

    static List<String> lines(String s) {
        ArrayList<String> out = new ArrayList<>();
        for (String l : s.split("\n")) {
            String t = l.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    static String join(JSONArray a) {
        if (a == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(a.optString(i, ""));
        }
        return sb.toString();
    }

    static JSONArray arr(List<String> l) {
        return new JSONArray(l);
    }

    static JSONArray arrOf(String text) {
        return new JSONArray(lines(text));
    }

    static long num(EditText e, long def) {
        try {
            return Long.parseLong(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    static String fmt(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb;
    }

    // ---------------------------------------------------------------- nézet-segédek

    protected int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    protected TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    protected TextView headText(String s) {
        TextView t = text(s, landscape ? 15 : 17, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    protected TextView pillButton(String s, int fill, int stroke) {
        TextView t = text(s, 15, fill == 0 ? TEXT : 0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), 0, dp(14), 0);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setBackground(box(fill, stroke, stroke == 0 ? 0 : 1, 8));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    protected ImageButton iconButton(int res, String label, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        if (res != R.drawable.ic_refresh) b.setColorFilter(ICON);
        b.setBackground(null);
        b.setContentDescription(label);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setOnClickListener(l);
        return b;
    }

    protected LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    protected LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    protected GradientDrawable box(int fill, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    protected static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    protected static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    protected LinearLayout.LayoutParams wrapH(int hDp) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(hDp));
    }

    /** Egy JSON objektum mély másolata, hogy a módosítás ne érintse az eredetit. */
    static JSONObject copy(JSONObject o) {
        try {
            return new JSONObject(o.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
