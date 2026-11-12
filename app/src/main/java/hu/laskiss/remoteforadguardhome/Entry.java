package hu.laskiss.remoteforadguardhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A lekérdezési napló egy sora. */
final class Entry {

    static final int PROCESSED = 0, BLOCKED = 1, ALLOWED = 2, REWRITTEN = 3;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static DateTimeFormatter DATE() {
        return DateTimeFormatter.ofPattern(S.s(R.string.s_date_fmt));
    }

    String key = "";
    String time = "", date = "";
    String domain = "", shown = "", type = "", proto = "";
    String client = "", clientName = "";
    String reason = "", status = S.s(R.string.s_processed), elapsed = "", upstream = "";
    String service = "", ruleText = "", answer = "";
    int filterId = -1;
    int kind = PROCESSED;
    boolean cached;

    static Entry parse(JSONObject o) {
        Entry e = new Entry();
        String raw = o.optString("time", "");
        try {
            ZonedDateTime z = OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault());
            e.time = TIME.format(z);
            e.date = DATE().format(z);
        } catch (Exception ex) {
            e.time = raw;
        }

        JSONObject q = o.optJSONObject("question");
        if (q != null) {
            e.domain = q.optString("name", "");
            String uni = q.optString("unicode_name", "");
            e.shown = uni.isEmpty() ? e.domain : uni;
            e.type = q.optString("type", "");
        }
        if (e.domain.endsWith(".")) e.domain = e.domain.substring(0, e.domain.length() - 1);
        if (e.shown.endsWith(".")) e.shown = e.shown.substring(0, e.shown.length() - 1);

        e.client = o.optString("client", "");
        JSONObject ci = o.optJSONObject("client_info");
        if (ci != null) e.clientName = ci.optString("name", "");
        e.proto = protoName(o.optString("client_proto", ""));
        e.reason = o.optString("reason", "");
        e.elapsed = ms(o.optString("elapsedMs", ""));
        e.upstream = o.optString("upstream", "");
        e.cached = o.optBoolean("cached", false);
        e.service = o.optString("service_name", "");

        JSONArray rules = o.optJSONArray("rules");
        if (rules != null && rules.length() > 0) {
            JSONObject r = rules.optJSONObject(0);
            if (r != null) {
                e.filterId = r.optInt("filter_list_id", -1);
                e.ruleText = r.optString("text", "");
            }
        } else if (o.has("filterId")) {
            e.filterId = o.optInt("filterId", -1);
            e.ruleText = o.optString("rule", "");
        }

        JSONArray ans = o.optJSONArray("answer");
        if (ans != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ans.length() && i < 6; i++) {
                JSONObject a = ans.optJSONObject(i);
                if (a == null) continue;
                String v = a.optString("value", "");
                if (v.isEmpty()) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(v);
            }
            e.answer = sb.toString();
        }

        switch (e.reason) {
            case "FilteredBlackList":
                e.kind = BLOCKED; e.status = S.s(R.string.s_blocked); break;
            case "FilteredSafeBrowsing":
                e.kind = BLOCKED; e.status = S.s(R.string.s_blocked_threat); break;
            case "FilteredParental":
                e.kind = BLOCKED; e.status = S.s(R.string.s_blocked_by_parental_control); break;
            case "FilteredBlockedService":
                e.kind = BLOCKED; e.status = S.s(R.string.s_blocked_service); break;
            case "FilteredInvalid":
                e.kind = BLOCKED; e.status = S.s(R.string.s_invalid_request); break;
            case "NotFilteredWhiteList":
                e.kind = ALLOWED; e.status = S.s(R.string.s_allowed); break;
            case "Rewrite":
            case "RewriteEtcHosts":
            case "RewriteRule":
                e.kind = REWRITTEN; e.status = S.s(R.string.s_rewritten); break;
            case "FilteredSafeSearch":
                e.kind = REWRITTEN; e.status = S.s(R.string.s_safe_search_2); break;
            default:
                e.kind = PROCESSED; e.status = S.s(R.string.s_processed);
        }

        e.key = raw + "|" + e.domain + "|" + e.type + "|" + e.client;
        return e;
    }

    static String protoName(String p) {
        switch (p) {
            case "doh": return "DNS-over-HTTPS";
            case "dot": return "DNS-over-TLS";
            case "doq": return "DNS-over-QUIC";
            case "dnscrypt": return "DNSCrypt";
            default: return S.s(R.string.s_plain_dns);
        }
    }

    static String ms(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            double d = Double.parseDouble(s);
            return d < 10 ? String.format(Locale.US, "%.2f ms", d) : String.format(Locale.US, "%.0f ms", d);
        } catch (NumberFormatException ex) {
            return s + " ms";
        }
    }
}
