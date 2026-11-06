package hu.laskiss.remoteforadguardhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * Frissítés keresése és telepítése a nyilvános kiadás-repóból (laskiss-dev/kiadasok).
 *
 * A repóban appokra bontva egy kis JSON-fájl mondja meg a legújabb verziót; az APK a repó
 * Release-ei között van. Telepítés előtt az app ellenőrzi:
 *  - a letöltött fájl SHA-256 értékét a JSON-ban szereplővel,
 *  - hogy az APK csomagneve ezé az appé, a versionCode pedig nagyobb a mostaninál,
 *  - hogy az APK ugyanazzal a kulccsal van aláírva, mint a telepített app.
 * A telepítést a felhasználó indítja az ablak Telepítés gombjával; Android 12-től az
 * Android külön jóváhagyó ablaka nélkül is települhet (önfrissítés).
 * A Google Play Protect a telepítési engedély miatt figyelmeztethet (2.4: „Kártékony
 * alkalmazás letiltva”); a felhasználó ezt elfogadta (2026-11-06).
 *
 * Másik appba átvihető: csak a FILE értékét és a csomagnevet kell átírni.
 */
final class Updater {

    /** A kiadás-repó; a JSON és az APK csak innen fogadható el. */
    static final String REPO = "laskiss-dev/kiadasok";
    static final String FILE = "remote-for-adguard-home.json";
    static final String INDEX_URL = "https://raw.githubusercontent.com/" + REPO + "/main/" + FILE;
    static final String APK_PREFIX = "https://github.com/" + REPO + "/releases/download/";

    static final String PREF_AUTO = "updAuto";
    static final String PREF_LAST = "updLast";
    static final String PREF_SKIP = "updSkip";
    /** Automatikus keresés legfeljebb ilyen gyakran. */
    static final long AUTO_INTERVAL_MS = 24L * 60 * 60 * 1000;
    static final int MAX_INDEX_BYTES = 64 * 1024;
    static final long MAX_APK_BYTES = 100L * 1024 * 1024;

    static final String ACTION_RESULT = "hu.laskiss.remoteforadguardhome.UPDATE_RESULT";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean checkedThisRun = false;
    private static boolean busy = false;

    private Updater() { }

    /** Egy elérhető új verzió adatai. */
    static final class Release {
        String version;
        long versionCode;
        String date;
        String apk;
        String sha256;
        long size;
        String[] changes;
    }

    // ---------------------------------------------------------------- keresés

    /** Induláskor hívódik: ha be van kapcsolva és rég volt, csendben keres, és csak akkor szól, ha van új. */
    static void autoCheck(Activity a, SharedPreferences prefs) {
        if (checkedThisRun || busy) return;
        if (!prefs.getBoolean(PREF_AUTO, true)) return;
        long now = System.currentTimeMillis();
        long last = prefs.getLong(PREF_LAST, 0);
        if (last > now) last = 0; // visszaállított óra
        if (now - last < AUTO_INTERVAL_MS) return;
        checkedThisRun = true;
        check(a, prefs, false);
    }

    /** Kézi keresés (Alkalmazás beállítások): mindig szól az eredményről. */
    static void manualCheck(Activity a, SharedPreferences prefs) {
        if (busy) return;
        check(a, prefs, true);
    }

    private static void check(final Activity a, final SharedPreferences prefs, final boolean manual) {
        busy = true;
        final Context app = a.getApplicationContext();
        if (manual) Toast.makeText(a, "Frissítés keresése…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Release r = null;
            String error = null;
            try {
                r = fetch(app);
                prefs.edit().putLong(PREF_LAST, System.currentTimeMillis()).apply();
            } catch (Exception e) {
                error = e instanceof IOException && e.getMessage() != null
                        ? e.getMessage() : "Nem sikerült frissítést keresni.";
            }
            final Release found = r;
            final String err = error;
            MAIN.post(() -> {
                busy = false;
                if (a.isFinishing() || a.isDestroyed()) return;
                long current = currentVersionCode(app);
                if (found != null && found.versionCode > current) {
                    if (!manual && prefs.getLong(PREF_SKIP, 0) == found.versionCode) return;
                    offer(a, prefs, found);
                } else if (manual) {
                    if (err != null) Toast.makeText(a, err, Toast.LENGTH_LONG).show();
                    else Toast.makeText(a, "A legfrissebb verzió van telepítve (" + currentVersionName(app) + ").",
                            Toast.LENGTH_LONG).show();
                }
            });
        }, "frissites-kereses").start();
    }

    /** A kiadás-repó JSON-jának letöltése és ellenőrzése. */
    static Release fetch(Context c) throws Exception {
        HttpURLConnection con = open(INDEX_URL);
        byte[] body;
        try {
            int code = con.getResponseCode();
            if (code == 404) throw new IOException("Ehhez az apphoz még nincs kiadás a frissítési tárhelyen.");
            if (code != 200) throw new IOException("A frissítési tárhely nem érhető el (HTTP " + code + ").");
            body = readLimited(con.getInputStream(), MAX_INDEX_BYTES);
        } finally {
            con.disconnect();
        }
        return parse(new String(body, "UTF-8"), c.getPackageName());
    }

    /** A JSON feldolgozása; hibás vagy idegen adatnál kivételt dob. Külön metódus, hogy tesztelhető legyen. */
    static Release parse(String json, String packageName) throws IOException {
        try {
            JSONObject o = new JSONObject(json);
            if (!packageName.equals(o.getString("app"))) throw new IOException("A frissítési adat másik apphoz tartozik.");
            Release r = new Release();
            r.version = o.getString("version");
            r.versionCode = o.getLong("versionCode");
            r.date = o.optString("date", "");
            r.apk = o.getString("apk");
            r.sha256 = o.getString("sha256").toLowerCase(java.util.Locale.ROOT);
            r.size = o.optLong("size", 0);
            JSONArray ch = o.optJSONArray("changes");
            r.changes = new String[ch == null ? 0 : ch.length()];
            for (int i = 0; i < r.changes.length; i++) r.changes[i] = ch.getString(i);
            if (r.versionCode < 1) throw new IOException("Hibás verziószám a frissítési adatban.");
            if (!r.apk.startsWith(APK_PREFIX) || !r.apk.endsWith(".apk") || r.apk.contains(".."))
                throw new IOException("A frissítés címe nem a megengedett helyre mutat.");
            if (!r.sha256.matches("[0-9a-f]{64}")) throw new IOException("Hibás ellenőrzőösszeg a frissítési adatban.");
            if (r.size < 0 || r.size > MAX_APK_BYTES) throw new IOException("Hibás fájlméret a frissítési adatban.");
            return r;
        } catch (org.json.JSONException e) {
            throw new IOException("A frissítési adat hibás.");
        }
    }

    // ---------------------------------------------------------------- párbeszéd

    private static void offer(final Activity a, final SharedPreferences prefs, final Release r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Telepített: ").append(currentVersionName(a)).append("\n");
        sb.append("Új: ").append(r.version);
        if (!r.date.isEmpty()) sb.append("  ·  ").append(r.date);
        if (r.size > 0) sb.append("  ·  ").append(String.format(java.util.Locale.ROOT, "%.1f MB", r.size / 1048576.0));
        if (r.changes.length > 0) {
            sb.append("\n\nÚjdonságok:");
            for (String s : r.changes) sb.append("\n•  ").append(s);
        }
        sb.append("\n\nTelepíted most?");
        new AlertDialog.Builder(a)
                .setTitle("Új verzió érhető el")
                .setMessage(sb.toString())
                .setPositiveButton("Telepítés", (d, w) -> {
                    prefs.edit().remove(PREF_SKIP).apply();
                    startInstall(a, r);
                })
                .setNegativeButton("Később", null)
                .setNeutralButton("Kihagyom", (d, w) -> prefs.edit().putLong(PREF_SKIP, r.versionCode).apply())
                .show();
    }

    // ---------------------------------------------------------------- letöltés és telepítés

    private static void startInstall(final Activity a, final Release r) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle("Engedély szükséges")
                    .setMessage("A frissítés telepítéséhez az Androidban engedélyezni kell, hogy ez az app "
                            + "alkalmazásokat telepíthessen (ismeretlen forrás). Ezt csak egyszer kell megadni.\n\n"
                            + "A következő oldalon kapcsold be, majd lépj vissza, és keress újra frissítést "
                            + "(Alkalmazás beállítások → Frissítés keresése).")
                    .setPositiveButton("Beállítások megnyitása", (d, w) -> {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName())));
                        } catch (Exception e) {
                            Toast.makeText(a, "A beállítás nem nyitható meg.", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Mégse", null)
                    .show();
            return;
        }

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setIndeterminate(r.size <= 0);
        box.addView(bar);
        final TextView label = new TextView(a);
        label.setPadding(0, pad / 3, 0, 0);
        label.setText("Kapcsolódás…");
        box.addView(label);
        final boolean[] cancelled = {false};
        final AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Frissítés letöltése: " + r.version)
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("Mégse", (d, w) -> cancelled[0] = true)
                .show();

        final Context app = a.getApplicationContext();
        busy = true;
        new Thread(() -> {
            File dir = new File(app.getCacheDir(), "frissites");
            File apk = new File(dir, "update.apk");
            String error = null;
            try {
                deleteAll(dir);
                if (!dir.mkdirs() && !dir.isDirectory()) throw new IOException("A letöltési mappa nem hozható létre.");
                download(r, apk, cancelled, (done, total) -> MAIN.post(() -> {
                    if (total > 0) {
                        bar.setIndeterminate(false);
                        bar.setProgress((int) (done * 1000 / total));
                        label.setText(String.format(java.util.Locale.ROOT, "%.1f / %.1f MB", done / 1048576.0, total / 1048576.0));
                    } else {
                        label.setText(String.format(java.util.Locale.ROOT, "%.1f MB", done / 1048576.0));
                    }
                }));
                if (cancelled[0]) throw new InterruptedException();
                MAIN.post(() -> label.setText("Ellenőrzés…"));
                verifyApk(app, apk, r);
                if (cancelled[0]) throw new InterruptedException();
                MAIN.post(() -> label.setText("Telepítés indítása…"));
                install(app, apk);
            } catch (InterruptedException e) {
                error = "";
            } catch (IOException e) {
                error = e.getMessage() == null ? "A frissítés letöltése nem sikerült." : e.getMessage();
            } catch (Exception e) {
                error = "A frissítés nem telepíthető: " + e.getClass().getSimpleName();
            }
            if (error != null) deleteAll(dir);
            final String err = error;
            MAIN.post(() -> {
                busy = false;
                try { dialog.dismiss(); } catch (Exception ignored) { }
                if (err != null && !err.isEmpty()) {
                    if (!a.isFinishing() && !a.isDestroyed()) {
                        new AlertDialog.Builder(a)
                                .setTitle("A frissítés nem sikerült")
                                .setMessage(err)
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        Toast.makeText(app, err, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }, "frissites-letoltes").start();
    }

    interface Progress { void on(long done, long total); }

    private static void download(Release r, File out, boolean[] cancelled, Progress p) throws Exception {
        HttpURLConnection con = open(r.apk);
        con.setReadTimeout(30_000);
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new IOException("A frissítés nem tölthető le (HTTP " + code + ").");
            // A GitHub a letöltést átirányítja a saját tárhelyére; csak HTTPS-en fogadjuk el.
            if (!"https".equals(con.getURL().getProtocol())) throw new IOException("A letöltés nem biztonságos kapcsolaton érkezett.");
            long total = con.getContentLengthLong();
            if (total <= 0) total = r.size;
            if (total > MAX_APK_BYTES) throw new IOException("A letöltendő fájl túl nagy.");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long done = 0, lastReport = 0;
            try (InputStream in = con.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled[0]) throw new InterruptedException();
                    done += n;
                    if (done > MAX_APK_BYTES) throw new IOException("A letöltendő fájl túl nagy.");
                    md.update(buf, 0, n);
                    os.write(buf, 0, n);
                    long now = System.currentTimeMillis();
                    if (now - lastReport > 150) {
                        lastReport = now;
                        p.on(done, total);
                    }
                }
            }
            p.on(done, total);
            if (r.size > 0 && done != r.size) throw new IOException("A letöltés hiányos. Próbáld újra.");
            if (!hex(md.digest()).equals(r.sha256))
                throw new IOException("A letöltött fájl sérült (az ellenőrzőösszeg nem egyezik). Próbáld újra.");
        } finally {
            con.disconnect();
        }
    }

    /** Az APK csomagneve, verziója és aláírása egyezzen a telepített appéval. */
    @SuppressWarnings("deprecation")
    private static void verifyApk(Context c, File apk, Release r) throws Exception {
        PackageManager pm = c.getPackageManager();
        int flags = PackageManager.GET_SIGNATURES;
        if (Build.VERSION.SDK_INT >= 28) flags |= PackageManager.GET_SIGNING_CERTIFICATES;
        PackageInfo neu = pm.getPackageArchiveInfo(apk.getPath(), flags);
        if (neu == null) throw new IOException("A letöltött fájl nem érvényes Android-alkalmazás.");
        if (!c.getPackageName().equals(neu.packageName)) throw new IOException("A letöltött fájl egy másik alkalmazás.");
        long code = Build.VERSION.SDK_INT >= 28 ? neu.getLongVersionCode() : neu.versionCode;
        if (code != r.versionCode || code <= currentVersionCode(c))
            throw new IOException("A letöltött fájl verziója nem a várt.");
        PackageInfo mine = pm.getPackageInfo(c.getPackageName(), flags);
        Set<String> want = signers(mine), got = signers(neu);
        if (want.isEmpty() || got.isEmpty() || !want.equals(got))
            throw new IOException("A letöltött fájl nem ugyanazzal a kulccsal van aláírva, mint a telepített app. "
                    + "Biztonsági okból nem telepítem.");
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signers(PackageInfo p) throws Exception {
        Signature[] sigs = null;
        if (Build.VERSION.SDK_INT >= 28 && p.signingInfo != null) {
            sigs = p.signingInfo.hasMultipleSigners()
                    ? p.signingInfo.getApkContentsSigners()
                    : p.signingInfo.getSigningCertificateHistory();
            // Kulcscsere-előzménynél a legutolsó (jelenlegi) kulcs számít.
            if (!p.signingInfo.hasMultipleSigners() && sigs != null && sigs.length > 1)
                sigs = new Signature[]{sigs[sigs.length - 1]};
        }
        if (sigs == null || sigs.length == 0) sigs = p.signatures;
        Set<String> out = new HashSet<>();
        if (sigs != null) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Signature s : sigs) out.add(hex(md.digest(s.toByteArray())));
        }
        return out;
    }

    /** Átadás az Android csomagtelepítőjének; a jóváhagyó ablakot a Result fogadó nyitja meg. */
    private static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(c.getPackageName());
        sp.setSize(apk.length());
        // Android 12-től az app saját magát jóváhagyó ablak nélkül frissítheti
        // (ha a telefon mégis kéri, a Result fogadó megnyitja a jóváhagyó ablakot).
        if (Build.VERSION.SDK_INT >= 31) sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(sp);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream os = s.openWrite("app.apk", 0, apk.length())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                s.fsync(os);
            }
            Intent i = new Intent(ACTION_RESULT).setClass(c, Result.class);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) fl |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pend = PendingIntent.getBroadcast(c, id, i, fl);
            s.commit(pend.getIntentSender());
        } catch (Exception e) {
            try { pi.abandonSession(id); } catch (Exception ignored) { }
            throw e;
        }
    }

    /** A csomagtelepítő visszajelzése: jóváhagyás kérése, siker vagy hiba. */
    public static final class Result extends BroadcastReceiver {
        @Override
        @SuppressWarnings("deprecation")
        public void onReceive(Context c, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = Build.VERSION.SDK_INT >= 33
                        ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
                        : intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        c.startActivity(confirm);
                    } catch (Exception e) {
                        Toast.makeText(c, "A telepítő nem nyitható meg.", Toast.LENGTH_LONG).show();
                    }
                }
                return;
            }
            deleteAll(new File(c.getCacheDir(), "frissites"));
            if (status == PackageInstaller.STATUS_SUCCESS) return; // az app újraindul
            String msg;
            switch (status) {
                case PackageInstaller.STATUS_FAILURE_ABORTED: msg = "A telepítés megszakadt."; break;
                case PackageInstaller.STATUS_FAILURE_CONFLICT: msg = "A frissítés ütközik a telepített appal (más aláírás?)."; break;
                case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE: msg = "A frissítés nem kompatibilis ezzel a telefonnal."; break;
                case PackageInstaller.STATUS_FAILURE_STORAGE: msg = "Nincs elég hely a telefonon a frissítéshez."; break;
                case PackageInstaller.STATUS_FAILURE_BLOCKED: msg = "A telefon letiltotta a telepítést."; break;
                default: msg = "A frissítés telepítése nem sikerült."; break;
            }
            Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- segédek

    private static HttpURLConnection open(String url) throws IOException {
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new IOException("Csak HTTPS-en keresek frissítést.");
        HttpURLConnection con = (HttpURLConnection) u.openConnection();
        con.setConnectTimeout(15_000);
        con.setReadTimeout(15_000);
        con.setUseCaches(false);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "RemoteForAdGuardHome-Updater");
        con.setRequestProperty("Cache-Control", "no-cache");
        return con;
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                if (bo.size() + n > max) throw new IOException("A frissítési adat túl nagy.");
                bo.write(buf, 0, n);
            }
            return bo.toByteArray();
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    static long currentVersionCode(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) {
            return Long.MAX_VALUE; // inkább ne ajánljon frissítést, mint hogy rosszat ajánljon
        }
    }

    static String currentVersionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static void deleteAll(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
