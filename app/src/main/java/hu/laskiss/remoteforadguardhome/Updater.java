package hu.laskiss.remoteforadguardhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Frissítés keresése a nyilvános kiadás-repóban (laskiss-dev/kiadasok).
 *
 * A repóban appokra bontva egy kis JSON-fájl mondja meg a legújabb verziót; az APK a repó
 * Release-ei között van. Ha van újabb verzió, az app megmutatja a változásokat, és a
 * Letöltés gomb a böngészőben nyitja meg az APK-t; a telepítést a felhasználó indítja a
 * letöltött fájlra koppintva. Az app szándékosan nem kér telepítési engedélyt
 * (REQUEST_INSTALL_PACKAGES): azzal a Google Play Protect kártékonynak jelölte.
 * Az Android csak ugyanazzal a kulccsal aláírt frissítést enged a telepített appra.
 *
 * Másik appba átvihető: csak a FILE értékét kell átírni.
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
    static final long AUTO_INTERVAL_MS = 6L * 60 * 60 * 1000;
    static final int MAX_INDEX_BYTES = 64 * 1024;
    static final long MAX_APK_BYTES = 200L * 1024 * 1024;

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
        sb.append("\n\nA Letöltés gomb a böngészőben tölti le az új verziót. "
                + "Utána koppints a letöltött fájlra, és az Android frissíti az appot.");
        new AlertDialog.Builder(a)
                .setTitle("Új verzió érhető el")
                .setMessage(sb.toString())
                .setPositiveButton("Letöltés", (d, w) -> {
                    prefs.edit().remove(PREF_SKIP).apply();
                    try {
                        a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.apk)));
                    } catch (Exception e) {
                        Toast.makeText(a, "Nincs böngésző a letöltéshez.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Később", null)
                .setNeutralButton("Kihagyom", (d, w) -> prefs.edit().putLong(PREF_SKIP, r.versionCode).apply())
                .show();
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

}
