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
            return S.s(R.string.s_the_server_did_not_give_the_expected_r);
        }
        if (e instanceof UnknownHostException) return S.s(R.string.s_the_server_cannot_be_found);
        if (e instanceof SocketTimeoutException) return S.s(R.string.s_the_server_did_not_respond_in_time);
        if (e instanceof ConnectException) return S.s(R.string.s_could_not_connect_to_the_server);
        if (e instanceof javax.net.ssl.SSLHandshakeException) {
            return S.s(R.string.s_the_server_s_https_certificate_is_not)
                    + S.s(R.string.s_for_security_reasons_the_app_does_not);
        }
        if (e instanceof javax.net.ssl.SSLException) {
            return S.s(R.string.s_could_not_establish_an_encrypted_https);
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
                toast(enabled ? S.s(R.string.s_protection_on) : S.s(R.string.s_protection_off));
            } catch (Exception e) {
                toast(S.s(R.string.s_failed) + msg(e));
            }
            loadProtection();
        });
    }
}
