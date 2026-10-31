package hu.laskiss.remoteforadguardhome;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Kis HTTP kliens az AdGuard Home /control/ API-jához. Külső könyvtár nélkül. */
final class Api {

    final String base;
    final String host;
    final Address address;
    private final String auth;
    private final boolean allowPublicHttp;

    /**
     * @param allowPublicHttp titkosítatlan HTTP nyilvános (nem otthoni) címre csak akkor megy,
     *                        ha a felhasználó ezt a szerver beállításánál külön jóváhagyta.
     */
    Api(Address a, String user, String pass, boolean allowPublicHttp) {
        this.address = a;
        this.allowPublicHttp = allowPublicHttp;
        base = a.apiBase();
        host = a.display();
        if (user == null || user.isEmpty()) {
            auth = null;
        } else {
            String raw = user + ":" + (pass == null ? "" : pass);
            auth = "Basic " + Base64.encodeToString(raw.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }
    }

    /** A lekérdezési napló egy adagja. A tömörítést (gzip) az Android magától kéri és bontja ki. */
    JSONObject queryLog(String search, String status, String olderThan, int limit)
            throws IOException, JSONException {
        StringBuilder q = new StringBuilder("querylog?limit=").append(limit);
        if (search != null && !search.isEmpty()) q.append("&search=").append(enc(search));
        if (status != null && !status.isEmpty() && !"all".equals(status)) {
            q.append("&response_status=").append(enc(status));
        }
        if (olderThan != null && !olderThan.isEmpty()) q.append("&older_than=").append(enc(olderThan));
        return getJson(q.toString());
    }

    JSONObject getJson(String path) throws IOException, JSONException {
        return new JSONObject(request("GET", path, null));
    }

    JSONArray getArray(String path) throws IOException, JSONException {
        return new JSONArray(request("GET", path, null));
    }

    /** POST JSON tartalommal; body == null esetén üres kérés tartalomtípus-fejléc nélkül. */
    String post(String path, JSONObject body) throws IOException {
        return request("POST", path, body == null ? null : body.toString());
    }

    String put(String path, JSONObject body) throws IOException {
        return request("PUT", path, body == null ? null : body.toString());
    }

    /** Hosszabb művelet (pl. szűrőlisták letöltése), bővebb várakozással. */
    String postSlow(String path, JSONObject body) throws IOException {
        return request("POST", path, body == null ? null : body.toString(), 120000);
    }

    String request(String method, String path, String body) throws IOException {
        return request(method, path, body, 15000);
    }

    String request(String method, String path, String body, int readTimeout) throws IOException {
        if (!address.https() && !address.isLocal() && !allowPublicHttp) {
            // Védőháló: nyilvános címre jóváhagyás nélkül nem küldünk jelszót titkosítatlanul.
            throw new IOException("Titkosítatlan (HTTP) kapcsolat nyilvános címre nincs engedélyezve. "
                    + "Használj HTTPS-t, vagy engedélyezd a szerver beállításánál.");
        }
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(readTimeout);
            c.setRequestMethod(method);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept", "application/json");
            if (auth != null) c.setRequestProperty("Authorization", auth);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                OutputStream o = c.getOutputStream();
                o.write(body.getBytes(StandardCharsets.UTF_8));
                o.close();
            }
            int code = c.getResponseCode();
            // Átirányítást nem követünk (a jelszó így nem juthat másik címre), és a szerver
            // hibaszövegét sem jelenítjük meg: csak a kód és egy rövid magyarázat látszik.
            if (code == 401 || code == 403) {
                throw new IOException("Hibás felhasználónév vagy jelszó (" + code + ").");
            }
            if (code >= 300 && code < 400) {
                throw new IOException("A szerver átirányított (" + code + "). Ellenőrizd a címet és a HTTP/HTTPS választást.");
            }
            if (code == 404) {
                throw new IOException("A cím nem AdGuard Home szerverre mutat, vagy ezt a funkciót nem ismeri (404).");
            }
            if (code >= 400) {
                InputStream err = c.getErrorStream();
                if (err != null) err.close();
                throw new IOException("A szerver hibát jelzett (" + code + ").");
            }
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }
}
