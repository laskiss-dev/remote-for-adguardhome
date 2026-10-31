package hu.laskiss.remoteforadguardhome;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Az egész appban közös kapcsolat, háttérszál és védelem-állapot. */
final class Net {

    static final Handler MAIN = new Handler(Looper.getMainLooper());
    static final ExecutorService NET = Executors.newSingleThreadExecutor();

    static Api api;
    /** Az aktív szerver (név, cím, beállítások). */
    static Servers.Server server;
    /** Az éppen előtérben lévő oldal (üzenetekhez, a BE/KI jelvényhez). */
    static BaseActivity top;

    static Boolean protection = null;
    static long protectionPause = 0;

    private Net() { }

    static String msg(Exception e) {
        if (e instanceof JSONException) {
            return "A szerver nem a várt választ adta. Ellenőrizd a címet (pl. 192.168.1.10:3000).";
        }
        if (e instanceof UnknownHostException) return "A szerver nem található.";
        if (e instanceof SocketTimeoutException) return "A szerver nem válaszolt időben.";
        if (e instanceof ConnectException) return "Nem sikerült kapcsolódni a szerverhez.";
        if (e instanceof javax.net.ssl.SSLHandshakeException) {
            return "A szerver HTTPS-tanúsítványa nem megbízható, vagy nem ehhez a címhez szól. "
                    + "Az app ilyenkor biztonsági okból nem kapcsolódik.";
        }
        if (e instanceof javax.net.ssl.SSLException) {
            return "Nem sikerült titkosított (HTTPS) kapcsolatot létesíteni. Lehet, hogy a szerver ezen a porton csak HTTP-t használ.";
        }
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    static void toast(final String text) {
        MAIN.post(() -> {
            BaseActivity a = top;
            if (a != null) Toast.makeText(a, text, Toast.LENGTH_SHORT).show();
        });
    }

    static void loadProtection() {
        final Api a = api;
        if (a == null) return;
        NET.execute(() -> {
            try {
                JSONObject r = a.getJson("status");
                final boolean en = r.optBoolean("protection_enabled", true);
                final long pause = r.optLong("protection_disabled_duration", 0);
                MAIN.post(() -> {
                    if (a != api) return;
                    protection = en;
                    protectionPause = pause;
                    BaseActivity t = top;
                    if (t != null) t.showProtection();
                });
            } catch (Exception ignored) {
                // a hibát az oldal saját betöltése úgyis jelzi
            }
        });
    }

    static void setProtection(final boolean enabled, final long durationMs) {
        final Api a = api;
        if (a == null) return;
        NET.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("enabled", enabled);
                if (!enabled && durationMs > 0) body.put("duration", durationMs);
                try {
                    a.post("protection", body);
                } catch (Exception e) {
                    if (durationMs > 0) throw e;
                    a.post("dns_config", new JSONObject().put("protection_enabled", enabled));
                }
                toast(enabled ? "Védelem bekapcsolva" : "Védelem kikapcsolva");
            } catch (Exception e) {
                toast("Nem sikerült: " + msg(e));
            }
            loadProtection();
        });
    }
}
