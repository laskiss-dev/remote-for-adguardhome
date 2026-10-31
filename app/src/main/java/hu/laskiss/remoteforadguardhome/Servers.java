package hu.laskiss.remoteforadguardhome;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * A felvett szerverek listája. A lista (név, cím, felhasználó, beállítások) sima beállításként,
 * a jelszó csak titkosítva (Crypto) tárolódik, vagy ha a felhasználó nem kéri a mentést,
 * csak a memóriában, az app futása alatt.
 */
final class Servers {

    static final class Server {
        String id = UUID.randomUUID().toString();
        String name = "";
        String url = "";          // feldolgozott alapcím sémával, pl. http://192.168.1.6:3000
        String user = "";
        boolean savePass = true;
        String encPass = "";      // titkosított jelszó, ha savePass
        boolean allowPublicHttp;  // nyilvános címre titkosítatlan HTTP külön engedéllyel

        Address address() {
            try {
                return Address.parse(url, "https");
            } catch (Address.Invalid e) {
                return null;
            }
        }

        String label() {
            Address a = address();
            String where = a == null ? url : a.display();
            return name.isEmpty() ? where : name + " · " + where;
        }

        JSONObject toJson() throws Exception {
            return new JSONObject().put("id", id).put("name", name).put("url", url).put("user", user)
                    .put("savePass", savePass).put("encPass", encPass).put("allowPublicHttp", allowPublicHttp);
        }

        static Server fromJson(JSONObject o) {
            Server s = new Server();
            s.id = o.optString("id", s.id);
            s.name = o.optString("name", "");
            s.url = o.optString("url", "");
            s.user = o.optString("user", "");
            s.savePass = o.optBoolean("savePass", true);
            s.encPass = o.optString("encPass", "");
            s.allowPublicHttp = o.optBoolean("allowPublicHttp", false);
            return s;
        }
    }

    private Servers() { }

    /** A nem mentett jelszavak, csak az app futása alatt. */
    private static final HashMap<String, String> memory = new HashMap<>();

    static List<Server> load(SharedPreferences p) {
        migrate(p);
        ArrayList<Server> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("servers", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) out.add(Server.fromJson(o));
            }
        } catch (Exception ignored) {
            // sérült lista: üresnek tekintjük
        }
        return out;
    }

    static void save(SharedPreferences p, List<Server> list) {
        JSONArray a = new JSONArray();
        try {
            for (Server s : list) a.put(s.toJson());
        } catch (Exception ignored) {
            return;
        }
        p.edit().putString("servers", a.toString()).apply();
    }

    static Server find(List<Server> list, String id) {
        if (id == null) return null;
        for (Server s : list) if (s.id.equals(id)) return s;
        return null;
    }

    static Server active(SharedPreferences p) {
        List<Server> list = load(p);
        Server s = find(list, p.getString("activeServer", null));
        if (s == null && !list.isEmpty()) s = list.get(0);
        return s;
    }

    static void setActive(SharedPreferences p, String id) {
        p.edit().putString("activeServer", id).apply();
    }

    /** A jelszó beállítása: mentve titkosítva, vagy csak a memóriában. */
    static void setPassword(Server s, String pass) {
        memory.put(s.id, pass == null ? "" : pass);
        if (s.savePass) {
            try {
                s.encPass = pass == null || pass.isEmpty() ? "" : Crypto.encrypt(pass);
            } catch (Exception e) {
                s.encPass = "";
                s.savePass = false;
            }
        } else {
            s.encPass = "";
        }
    }

    /** A szerver jelszava, ha ismert (mentett vagy ebben a futásban megadott); különben null. */
    static String password(Server s) {
        String m = memory.get(s.id);
        if (m != null) return m;
        if (s.user.isEmpty()) return "";
        if (s.savePass && !s.encPass.isEmpty()) {
            String d = Crypto.decrypt(s.encPass);
            if (d != null) {
                memory.put(s.id, d);
                return d;
            }
        }
        return null;
    }

    static void forget(Server s) {
        memory.remove(s.id);
        s.encPass = "";
    }

    /** Kapcsolat felépítése a szerverhez; ha nincs meg a jelszó vagy hibás a cím, false. */
    static boolean connect(Server s) {
        Address a = s.address();
        String pass = password(s);
        if (a == null || pass == null) return false;
        Net.api = new Api(a, s.user, pass, s.allowPublicHttp);
        Net.server = s;
        return true;
    }

    /**
     * Az 1.x verziók egyetlen szerverbeállítását (url/user/pass, a jelszó titkosítatlanul)
     * átteszi az új listába „Otthon” néven, titkosított jelszóval, majd a régit törli.
     * A régi, séma nélküli cím http-ként működött, ezért úgy is marad.
     */
    static void migrate(SharedPreferences p) {
        String url = p.getString("url", "");
        if (url.isEmpty() || p.getString("servers", null) != null) return;
        Server s = new Server();
        s.name = "Otthon";
        try {
            Address a = Address.parse(url, "http");
            s.url = a.base();
            s.allowPublicHttp = !a.https() && !a.isLocal();
        } catch (Address.Invalid e) {
            s.url = url;
        }
        s.user = p.getString("user", "");
        s.savePass = true;
        setPassword(s, p.getString("pass", ""));
        ArrayList<Server> list = new ArrayList<>();
        list.add(s);
        save(p, list);
        p.edit().putString("activeServer", s.id).remove("url").remove("user").remove("pass").apply();
    }
}
