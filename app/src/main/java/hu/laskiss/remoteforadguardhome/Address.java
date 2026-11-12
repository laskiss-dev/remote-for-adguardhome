package hu.laskiss.remoteforadguardhome;

import java.net.URI;
import java.util.Locale;

/**
 * A szervercím szigorú feldolgozása. Csak http és https mehet, a címben nem lehet felhasználónév
 * vagy jelszó, a port érvényes kell legyen, és lekérdező rész sem lehet benne.
 */
final class Address {

    final String scheme;   // "http" vagy "https"
    final String host;     // IPv6 esetén szögletes zárójel nélkül
    final int port;        // -1, ha nincs megadva
    final String path;     // "" vagy "/valami" (fordított proxy mögötti útvonal), záró perjel nélkül

    private Address(String scheme, String host, int port, String path) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.path = path;
    }

    /** Hibás bemenet esetén ezt dobja, magyar, felhasználónak szóló üzenettel. */
    static final class Invalid extends Exception {
        Invalid(String msg) { super(msg); }
    }

    boolean https() {
        return "https".equals(scheme);
    }

    /** A /control/ végpont alapja, pl. http://192.168.1.6:3000/control/ */
    String apiBase() {
        return base() + "/control/";
    }

    /** A szerver alapcíme sémával, záró perjel nélkül. */
    String base() {
        return scheme + "://" + hostPort() + path;
    }

    /** Megjelenítéshez: cím és port, séma nélkül (pl. 192.168.1.6:3000). */
    String hostPort() {
        String h = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        return port > 0 ? h + ":" + port : h;
    }

    /** Megjelenítéshez: hostPort + útvonal. */
    String display() {
        return hostPort() + path;
    }

    /**
     * A beírt cím feldolgozása. Ha a beírt szövegben van séma (http:// vagy https://), az számít;
     * ha nincs, a választott séma (defaultScheme).
     */
    static Address parse(String input, String defaultScheme) throws Invalid {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) throw new Invalid(S.s(R.string.s_enter_the_server_address));
        String lower = s.toLowerCase(Locale.ROOT);
        String scheme;
        if (lower.startsWith("http://")) {
            scheme = "http";
        } else if (lower.startsWith("https://")) {
            scheme = "https";
        } else if (lower.matches("^[a-z][a-z0-9+.-]*://.*")) {
            throw new Invalid(S.s(R.string.s_only_http_or_https_addresses_are_allow));
        } else {
            scheme = "https".equals(defaultScheme) ? "https" : "http";
            s = scheme + "://" + s;
        }
        URI u;
        try {
            u = new URI(s);
        } catch (Exception e) {
            throw new Invalid(S.s(R.string.s_the_address_format_is_invalid));
        }
        if (u.getRawUserInfo() != null) {
            throw new Invalid(S.s(R.string.s_the_address_cannot_contain_a_username));
        }
        if (u.getRawQuery() != null || u.getRawFragment() != null) {
            throw new Invalid(S.s(R.string.s_the_address_cannot_contain_a_part_afte));
        }
        String host = u.getHost();
        if (host == null || host.isEmpty()) {
            throw new Invalid(u.getRawAuthority() == null || u.getRawAuthority().isEmpty()
                    ? S.s(R.string.s_the_address_is_missing_the_server_name)
                    : S.s(R.string.s_the_server_name_ip_address_or_port_is));
        }
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        if (!validHost(host)) throw new Invalid(S.s(R.string.s_the_server_name_or_ip_address_is_inval));
        int port = u.getPort();
        if (port == 0 || port > 65535) throw new Invalid(S.s(R.string.s_the_port_must_be_between_1_and_65535));
        if (port < 0 && hasPortSyntax(s)) throw new Invalid(S.s(R.string.s_the_port_must_be_between_1_and_65535));
        String path = u.getRawPath() == null ? "" : u.getRawPath();
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.endsWith("/control")) path = path.substring(0, path.length() - "/control".length());
        if (path.contains("..")) throw new Invalid(S.s(R.string.s_the_address_path_is_invalid));
        return new Address(scheme, host.toLowerCase(Locale.ROOT), port, path);
    }

    /** Ha a host után kettőspont áll, de a URI nem talált érvényes portot. */
    private static boolean hasPortSyntax(String s) {
        int start = s.indexOf("://") + 3;
        int end = s.indexOf('/', start);
        String auth = end < 0 ? s.substring(start) : s.substring(start, end);
        if (auth.startsWith("[")) return auth.indexOf("]:") >= 0;
        return auth.indexOf(':') >= 0;
    }

    private static boolean validHost(String h) {
        if (h.indexOf(':') >= 0) return h.matches("[0-9a-fA-F:.]+");          // IPv6
        if (h.matches("[0-9.]+")) return ipv4(h) != null;                      // IPv4
        if (h.length() > 253) return false;
        for (String label : h.split("\\.", -1)) {
            if (label.isEmpty() || label.length() > 63) return false;
            if (!label.matches("[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?")) return false;
        }
        return true;
    }

    private static int[] ipv4(String h) {
        String[] p = h.split("\\.", -1);
        if (p.length != 4) return null;
        int[] r = new int[4];
        for (int i = 0; i < 4; i++) {
            if (p[i].isEmpty() || p[i].length() > 3) return null;
            try {
                r[i] = Integer.parseInt(p[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (r[i] > 255) return null;
        }
        return r;
    }

    /**
     * Otthoni vagy belső hálózati cím-e: magánhálózati IPv4 (10/8, 172.16/12, 192.168/16),
     * VPN-ek által használt 100.64/10, helyi gép, link-local, IPv6 ULA és link-local,
     * illetve pont nélküli vagy .local/.lan/.home/.internal/.home.arpa végű név.
     * Ilyen címre a titkosítatlan HTTP külön megerősítés nélkül engedett.
     */
    boolean isLocal() {
        int[] v4 = ipv4(host);
        if (v4 != null) {
            int a = v4[0], b = v4[1];
            return a == 10 || a == 127
                    || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && b == 168)
                    || (a == 100 && b >= 64 && b <= 127)
                    || (a == 169 && b == 254);
        }
        if (host.indexOf(':') >= 0) {
            String h = host.toLowerCase(Locale.ROOT);
            return h.equals("::1") || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe8")
                    || h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb");
        }
        if (host.indexOf('.') < 0) return true; // pl. "nas", "homeassistant"
        return host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home")
                || host.endsWith(".internal") || host.endsWith(".home.arpa") || host.equals("localhost");
    }
}
