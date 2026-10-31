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
    private final String auth;

    Api(String url, String user, String pass) {
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith("/control")) u = u.substring(0, u.length() - 8);
        base = u + "/control/";
        host = u.replaceFirst("^https?://", "");
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
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : readAll(in);
            if (code == 401 || code == 403 || (code >= 300 && code < 400)) {
                throw new IOException("Hibás felhasználónév vagy jelszó (" + code + ")");
            }
            if (code >= 400) {
                String t = text.trim();
                if (t.length() > 120) t = t.substring(0, 120);
                throw new IOException("A szerver hibát jelzett: " + code + (t.isEmpty() ? "" : " – " + t));
            }
            return text;
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
