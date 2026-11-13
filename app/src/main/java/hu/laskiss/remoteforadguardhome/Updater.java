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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Frissítés keresése és telepítése az app saját, nyilvános GitHub-kiadásaiból.
 *
 * Nincs külön kiadás-repó, JSON vagy token: az app a GitHub nyilvános API-jától kérdezi a
 * legfrissebb kiadást (https://api.github.com/repos/REPO/releases/latest). Az APK a kiadás
 * melléklete. Telepítés előtt az app ellenőrzi:
 *  - a letöltött fájl SHA-256 értékét a GitHub által megadottal (ha a GitHub megadja),
 *  - hogy az APK csomagneve ezé az appé, a verziója a kiadás címkéjével egyezik, és újabb a mostaninál,
 *  - hogy az APK ugyanazzal a kulccsal van aláírva, mint a telepített app (ez a döntő védelem).
 * A telepítést a felhasználó indítja az ablak Telepítés gombjával; Android 12-től az
 * Android külön jóváhagyó ablaka nélkül is települhet (önfrissítés).
 *
 * Másik appba átvihető: csak a REPO értékét kell átírni (és a kiadás címkéje legyen v + versionName).
 */
final class Updater {

    /** Az app nyilvános kiadás-repója; az APK csak innen fogadható el. */
    static final String REPO = "laskiss-dev/remote-for-adguardhome";
    static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    static final String APK_PREFIX = "https://github.com/" + REPO + "/releases/download/";

    static final String PREF_AUTO = "updAuto";
    static final String PREF_LAST = "updLast";
    static final String PREF_SKIP = "updSkipVersion";
    /** Automatikus keresés legfeljebb ilyen gyakran. */
    static final long AUTO_INTERVAL_MS = 24L * 60 * 60 * 1000;
    static final int MAX_INDEX_BYTES = 512 * 1024;
    static final long MAX_APK_BYTES = 100L * 1024 * 1024;

    static final String ACTION_RESULT = "hu.laskiss.remoteforadguardhome.UPDATE_RESULT";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean checkedThisRun = false;
    private static boolean busy = false;

    private Updater() { }

    /** Egy elérhető új verzió adatai. */
    static final class Release {
        String version;
        String date;
        String apk;
        /** Üres, ha a GitHub nem adott ellenőrzőösszeget; ilyenkor az aláírás-ellenőrzés védi a telepítést. */
        String sha256 = "";
        long size;
        String[] changes;
    }

    private static String str(Context c, int id, Object... args) {
        return c.getString(id, args);
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
        if (manual) Toast.makeText(a, str(a, R.string.upd_checking), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Release r = null;
            String error = null;
            try {
                r = fetch(app);
                prefs.edit().putLong(PREF_LAST, System.currentTimeMillis()).apply();
            } catch (Exception e) {
                error = e instanceof IOException && e.getMessage() != null
                        ? e.getMessage() : str(app, R.string.upd_search_failed);
            }
            final Release found = r;
            final String err = error;
            MAIN.post(() -> {
                busy = false;
                if (a.isFinishing() || a.isDestroyed()) return;
                if (found != null && isNewer(found.version, currentVersionName(app))) {
                    if (!manual && found.version.equals(prefs.getString(PREF_SKIP, ""))) return;
                    offer(a, prefs, found);
                } else if (manual) {
                    if (err != null) Toast.makeText(a, err, Toast.LENGTH_LONG).show();
                    else Toast.makeText(a, str(a, R.string.upd_latest, currentVersionName(app)),
                            Toast.LENGTH_LONG).show();
                }
            });
        }, "frissites-kereses").start();
    }

    /** A legfrissebb kiadás lekérése a GitHubról és ellenőrzése. */
    static Release fetch(Context c) throws Exception {
        HttpURLConnection con = open(API_URL);
        con.setRequestProperty("Accept", "application/vnd.github+json");
        byte[] body;
        try {
            int code = con.getResponseCode();
            if (code == 404) throw new IOException(str(c, R.string.upd_no_release));
            if (code != 200) throw new IOException(str(c, R.string.upd_unreachable, code));
            body = readLimited(con.getInputStream(), MAX_INDEX_BYTES, c);
        } finally {
            con.disconnect();
        }
        return parse(c, new String(body, "UTF-8"));
    }

    /** A GitHub kiadás-JSON feldolgozása; hibás vagy idegen adatnál kivételt dob. */
    static Release parse(Context c, String json) throws IOException {
        try {
            JSONObject o = new JSONObject(json);
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) throw new IOException(str(c, R.string.upd_no_release));
            Release r = new Release();
            String tag = o.getString("tag_name");
            r.version = tag.startsWith("v") ? tag.substring(1) : tag;
            if (!r.version.matches("[0-9]+(\\.[0-9]+)*")) throw new IOException(str(c, R.string.upd_bad_data));
            String pub = o.optString("published_at", "");
            r.date = pub.length() >= 10 ? pub.substring(0, 10) : "";

            JSONArray assets = o.getJSONArray("assets");
            JSONObject apk = null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject as = assets.getJSONObject(i);
                if (as.getString("name").endsWith(".apk")) { apk = as; break; }
            }
            if (apk == null) throw new IOException(str(c, R.string.upd_no_release));
            r.apk = apk.getString("browser_download_url");
            r.size = apk.optLong("size", 0);
            String digest = apk.optString("digest", "");
            if (digest.startsWith("sha256:")) r.sha256 = digest.substring(7).toLowerCase(Locale.ROOT);

            r.changes = changes(o.optString("body", ""));
            if (!r.apk.startsWith(APK_PREFIX) || !r.apk.endsWith(".apk") || r.apk.contains(".."))
                throw new IOException(str(c, R.string.upd_bad_url));
            if (!r.sha256.isEmpty() && !r.sha256.matches("[0-9a-f]{64}")) throw new IOException(str(c, R.string.upd_bad_data));
            if (r.size < 0 || r.size > MAX_APK_BYTES) throw new IOException(str(c, R.string.upd_bad_data));
            return r;
        } catch (org.json.JSONException e) {
            throw new IOException(str(c, R.string.upd_bad_data));
        }
    }

    /** A kiadás leírásából a tartalmas sorok (a címsor nélkül, a felsorolásjel levágva, legfeljebb 10). */
    private static String[] changes(String body) {
        List<String> out = new ArrayList<>();
        for (String line : body.split("\r?\n")) {
            String t = line.trim();
            if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ")) t = t.substring(2).trim();
            if (t.isEmpty() || t.startsWith("Remote for AdGuard Home")) continue;
            if (out.size() < 10) out.add(t.length() > 300 ? t.substring(0, 300) + "…" : t);
        }
        return out.toArray(new String[0]);
    }

    /** Verziószámok összehasonlítása számonként: 2.10 újabb, mint 2.9. */
    static boolean isNewer(String candidate, String current) {
        String[] a = candidate.split("\\.");
        String[] b = current.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            long x = i < a.length ? num(a[i]) : 0;
            long y = i < b.length ? num(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static long num(String s) {
        try {
            return Long.parseLong(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- párbeszéd

    private static void offer(final Activity a, final SharedPreferences prefs, final Release r) {
        StringBuilder sb = new StringBuilder();
        sb.append(str(a, R.string.upd_installed, currentVersionName(a))).append("\n");
        sb.append(str(a, R.string.upd_new, r.version));
        if (!r.date.isEmpty()) sb.append("  ·  ").append(r.date);
        if (r.size > 0) sb.append("  ·  ").append(String.format(Locale.ROOT, "%.1f MB", r.size / 1048576.0));
        if (r.changes.length > 0) {
            sb.append("\n\n").append(str(a, R.string.upd_whats_new));
            for (String s : r.changes) sb.append("\n•  ").append(s);
        }
        sb.append("\n\n").append(str(a, R.string.upd_install_now));
        new AlertDialog.Builder(a)
                .setTitle(str(a, R.string.upd_new_title))
                .setMessage(sb.toString())
                .setPositiveButton(str(a, R.string.upd_install), (d, w) -> {
                    prefs.edit().remove(PREF_SKIP).apply();
                    startInstall(a, r);
                })
                .setNegativeButton(str(a, R.string.upd_later), null)
                .setNeutralButton(str(a, R.string.upd_skip), (d, w) -> prefs.edit().putString(PREF_SKIP, r.version).apply())
                .show();
    }

    // ---------------------------------------------------------------- letöltés és telepítés

    private static void startInstall(final Activity a, final Release r) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle(str(a, R.string.upd_perm_title))
                    .setMessage(str(a, R.string.upd_perm_msg))
                    .setPositiveButton(str(a, R.string.upd_open_settings), (d, w) -> {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName())));
                        } catch (Exception e) {
                            Toast.makeText(a, str(a, R.string.upd_cannot_open_settings), Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton(str(a, R.string.upd_cancel), null)
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
        label.setText(str(a, R.string.upd_connecting));
        box.addView(label);
        final boolean[] cancelled = {false};
        final AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(str(a, R.string.upd_downloading, r.version))
                .setView(box)
                .setCancelable(false)
                .setNegativeButton(str(a, R.string.upd_cancel), (d, w) -> cancelled[0] = true)
                .show();

        final Context app = a.getApplicationContext();
        busy = true;
        new Thread(() -> {
            File dir = new File(app.getCacheDir(), "frissites");
            File apk = new File(dir, "update.apk");
            String error = null;
            try {
                deleteAll(dir);
                if (!dir.mkdirs() && !dir.isDirectory()) throw new IOException(str(app, R.string.upd_no_folder));
                download(app, r, apk, cancelled, (done, total) -> MAIN.post(() -> {
                    if (total > 0) {
                        bar.setIndeterminate(false);
                        bar.setProgress((int) (done * 1000 / total));
                        label.setText(String.format(Locale.ROOT, "%.1f / %.1f MB", done / 1048576.0, total / 1048576.0));
                    } else {
                        label.setText(String.format(Locale.ROOT, "%.1f MB", done / 1048576.0));
                    }
                }));
                if (cancelled[0]) throw new InterruptedException();
                MAIN.post(() -> label.setText(str(app, R.string.upd_verifying)));
                verifyApk(app, apk, r);
                if (cancelled[0]) throw new InterruptedException();
                MAIN.post(() -> label.setText(str(app, R.string.upd_starting)));
                install(app, apk);
            } catch (InterruptedException e) {
                error = "";
            } catch (IOException e) {
                error = e.getMessage() == null ? str(app, R.string.upd_dl_failed) : e.getMessage();
            } catch (Exception e) {
                error = str(app, R.string.upd_cannot_install, e.getClass().getSimpleName());
            }
            if (error != null) deleteAll(dir);
            final String err = error;
            MAIN.post(() -> {
                busy = false;
                try { dialog.dismiss(); } catch (Exception ignored) { }
                if (err != null && !err.isEmpty()) {
                    if (!a.isFinishing() && !a.isDestroyed()) {
                        new AlertDialog.Builder(a)
                                .setTitle(str(app, R.string.upd_failed_title))
                                .setMessage(err)
                                .setPositiveButton(str(app, R.string.upd_ok), null)
                                .show();
                    } else {
                        Toast.makeText(app, err, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }, "frissites-letoltes").start();
    }

    interface Progress { void on(long done, long total); }

    private static void download(Context c, Release r, File out, boolean[] cancelled, Progress p) throws Exception {
        HttpURLConnection con = open(r.apk);
        con.setReadTimeout(30_000);
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new IOException(str(c, R.string.upd_dl_http, code));
            // A GitHub a letöltést átirányítja a saját tárhelyére; csak HTTPS-en fogadjuk el.
            if (!"https".equals(con.getURL().getProtocol())) throw new IOException(str(c, R.string.upd_insecure));
            long total = con.getContentLengthLong();
            if (total <= 0) total = r.size;
            if (total > MAX_APK_BYTES) throw new IOException(str(c, R.string.upd_too_big));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long done = 0, lastReport = 0;
            try (InputStream in = con.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled[0]) throw new InterruptedException();
                    done += n;
                    if (done > MAX_APK_BYTES) throw new IOException(str(c, R.string.upd_too_big));
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
            if (r.size > 0 && done != r.size) throw new IOException(str(c, R.string.upd_incomplete));
            if (!r.sha256.isEmpty() && !hex(md.digest()).equals(r.sha256))
                throw new IOException(str(c, R.string.upd_corrupt));
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
        if (neu == null) throw new IOException(str(c, R.string.upd_not_valid_apk));
        if (!c.getPackageName().equals(neu.packageName)) throw new IOException(str(c, R.string.upd_other_app));
        long code = Build.VERSION.SDK_INT >= 28 ? neu.getLongVersionCode() : neu.versionCode;
        if (!r.version.equals(neu.versionName) || code <= currentVersionCode(c))
            throw new IOException(str(c, R.string.upd_wrong_version));
        PackageInfo mine = pm.getPackageInfo(c.getPackageName(), flags);
        Set<String> want = signers(mine), got = signers(neu);
        if (want.isEmpty() || got.isEmpty() || !want.equals(got))
            throw new IOException(str(c, R.string.upd_wrong_key));
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
                        Toast.makeText(c, str(c, R.string.upd_r_cannot_open), Toast.LENGTH_LONG).show();
                    }
                }
                return;
            }
            deleteAll(new File(c.getCacheDir(), "frissites"));
            if (status == PackageInstaller.STATUS_SUCCESS) return; // az app újraindul
            int msg;
            switch (status) {
                case PackageInstaller.STATUS_FAILURE_ABORTED: msg = R.string.upd_r_aborted; break;
                case PackageInstaller.STATUS_FAILURE_CONFLICT: msg = R.string.upd_r_conflict; break;
                case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE: msg = R.string.upd_r_incompatible; break;
                case PackageInstaller.STATUS_FAILURE_STORAGE: msg = R.string.upd_r_storage; break;
                case PackageInstaller.STATUS_FAILURE_BLOCKED: msg = R.string.upd_r_blocked; break;
                default: msg = R.string.upd_r_failed; break;
            }
            Toast.makeText(c, str(c, msg), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- segédek

    private static HttpURLConnection open(String url) throws IOException {
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new IOException("HTTPS only");
        HttpURLConnection con = (HttpURLConnection) u.openConnection();
        con.setConnectTimeout(15_000);
        con.setReadTimeout(15_000);
        con.setUseCaches(false);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "RemoteForAdGuardHome-Updater");
        con.setRequestProperty("Cache-Control", "no-cache");
        return con;
    }

    private static byte[] readLimited(InputStream in, int max, Context c) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                if (bo.size() + n > max) throw new IOException(str(c, R.string.upd_bad_data));
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
            return "0";
        }
    }

    private static void deleteAll(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
