package hu.laskiss.remoteforadguardhome;

import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Titkosítási beállítások: HTTPS, DNS-over-HTTPS/TLS/QUIC, tanúsítvány és privát kulcs,
 * a webes felület Titkosítási beállítások oldalának megfelelően (tls/status, tls/validate, tls/configure).
 */
public class EncryptionActivity extends BaseActivity {

    private JSONObject orig;
    private Switch enabled, plainDns, forceHttps, useSavedKey;
    private EditText serverName, portHttps, portDot, portDoq, certPath, certText, keyPath, keyText;
    private Choice certSource, keySource;
    private LinearLayout certStatus, keyStatus, warning, savedKeyBox;
    private boolean keyWasSaved;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage(S.s(R.string.s_encryption_settings));
        load();
    }

    @Override
    void load() {
        loading();
        task(() -> Net.api.getJson("tls/status"), this::render);
    }

    private void render(JSONObject st) {
        loaded();
        orig = st;

        LinearLayout t = section(S.s(R.string.s_encryption),
                S.s(R.string.s_support_for_encryption_https_quic_tls));
        enabled = switchRow(t, S.s(R.string.s_enable_encryption_https_dns_over_https),
                S.s(R.string.s_when_on_the_admin_interface_also_works)
                        + S.s(R.string.s_and_dns_over_tls),
                st.optBoolean("enabled", false));
        if (st.has("serve_plain_dns")) {
            plainDns = switchRow(t, S.s(R.string.s_enable_plain_dns),
                    S.s(R.string.s_on_by_default_when_off_every_device_is)
                            + S.s(R.string.s_for_that_at_least_one_encrypted_dns_pr),
                    st.optBoolean("serve_plain_dns", true));
        }

        LinearLayout s = section(S.s(R.string.s_server_and_ports), null);
        serverName = field(s, S.s(R.string.s_server_name), S.s(R.string.s_e_g_dns_example_org), st.optString("server_name", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        note(s, S.s(R.string.s_when_set_adguard_home_recognizes_clien)
                + S.s(R.string.s_it_must_match_one_of_the_dns_names_in));
        forceHttps = switchRow(s, S.s(R.string.s_redirect_to_https_automatically),
                S.s(R.string.s_when_on_adguard_home_automatically_red),
                st.optBoolean("force_https", false));
        portHttps = port(s, "HTTPS port", st.optInt("port_https", 443));
        note(s, S.s(R.string.s_the_admin_interface_is_reachable_over));
        portDot = port(s, "DNS-over-TLS port", st.optInt("port_dns_over_tls", 853));
        note(s, S.s(R.string.s_when_set_adguard_home_also_runs_as_a_d));
        portDoq = port(s, "DNS-over-QUIC port", st.optInt("port_dns_over_quic", 853));
        note(s, S.s(R.string.s_when_set_adguard_home_also_runs_as_a_d_2));

        LinearLayout c = section(S.s(R.string.s_certificates),
                S.s(R.string.s_encryption_requires_a_valid_ssl_certif)
                        + S.s(R.string.s_letsencrypt_org_or_buy_one_from_a_trus));
        String chain = decode(st.optString("certificate_chain", ""));
        certSource = new Choice(c, S.s(R.string.s_source), new String[]{S.s(R.string.s_enter_the_path_of_the_certificate_file), S.s(R.string.s_paste_the_certificate_contents)},
                chain.isEmpty() ? 0 : 1);
        certPath = field(c, S.s(R.string.s_certificate_path), S.s(R.string.s_e_g_ssl_fullchain_pem), st.optString("certificate_path", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        certText = area(c, null, S.s(R.string.s_paste_the_pem_encoded_certificate_here), chain, 4);
        certStatus = col();
        c.addView(certStatus, matchWrap());
        certSource.onChange = this::syncSources;

        LinearLayout k = section(S.s(R.string.s_private_key), null);
        keyWasSaved = st.optBoolean("private_key_saved", false);
        keySource = new Choice(k, S.s(R.string.s_source), new String[]{S.s(R.string.s_set_the_path_of_the_private_key_file), S.s(R.string.s_paste_the_private_key_contents)},
                keyWasSaved || !st.optString("private_key", "").isEmpty() ? 1 : 0);
        keyPath = field(k, S.s(R.string.s_private_key_path), S.s(R.string.s_e_g_ssl_privkey_pem), st.optString("private_key_path", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        savedKeyBox = col();
        k.addView(savedKeyBox, matchWrap());
        useSavedKey = keyWasSaved ? switchRow(savedKeyBox, S.s(R.string.s_use_the_previously_saved_key),
                S.s(R.string.s_the_key_stored_on_the_server_is_kept_y), true) : null;
        keyText = area(k, null, S.s(R.string.s_paste_the_pem_encoded_private_key_of_y), "", 4);
        keyStatus = col();
        k.addView(keyStatus, matchWrap());
        keySource.onChange = this::syncSources;
        if (useSavedKey != null) useSavedKey.setOnCheckedChangeListener((v, on) -> syncSources());

        LinearLayout a = section(S.s(R.string.s_check_and_save), null);
        warning = col();
        a.addView(warning, matchWrap());
        LinearLayout br = buttonRow(a);
        button(br, S.s(R.string.s_save_configuration), 1, v -> saveConfig());
        button(br, S.s(R.string.s_check), 0, v -> validate());
        button(buttonRow(a), S.s(R.string.s_reset_settings), 2, v -> confirm(S.s(R.string.s_reset_settings),
                S.s(R.string.s_are_you_sure_you_want_to_reset_the_enc),
                S.s(R.string.s_reset), this::resetConfig));

        syncSources();
        showStatus(st);
    }

    private EditText port(LinearLayout parent, String label, int value) {
        return field(parent, label, "0", value > 0 ? String.valueOf(value) : "", InputType.TYPE_CLASS_NUMBER);
    }

    private void note(LinearLayout parent, String s) {
        TextView d = text(s, 12, MUTED);
        d.setPadding(0, dp(2), 0, dp(4));
        parent.addView(d);
    }

    private void syncSources() {
        boolean certContent = certSource.sel == 1;
        certPath.setVisibility(certContent ? View.GONE : View.VISIBLE);
        certText.setVisibility(certContent ? View.VISIBLE : View.GONE);
        boolean keyContent = keySource.sel == 1;
        keyPath.setVisibility(keyContent ? View.GONE : View.VISIBLE);
        savedKeyBox.setVisibility(keyContent && useSavedKey != null ? View.VISIBLE : View.GONE);
        boolean saved = useSavedKey != null && useSavedKey.isChecked();
        keyText.setVisibility(keyContent && !saved ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- kérés összeállítása

    static String decode(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        try {
            return new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    static String encode(String s) {
        return s.isEmpty() ? "" : Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static int portValue(EditText e) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException x) {
            return -1;
        }
    }

    private static final String[] STATUS_FIELDS = {"valid_cert", "valid_chain", "valid_key", "valid_pair", "subject",
            "issuer", "not_before", "not_after", "dns_names", "key_type", "warning_validation"};

    /** A beírt értékekből a tls/validate és tls/configure kérés teste; a nem látható mezők (pl. DNSCrypt) megmaradnak. */
    JSONObject body() throws Exception {
        JSONObject b = copy(orig);
        for (String f : STATUS_FIELDS) b.remove(f);
        b.put("enabled", enabled.isChecked());
        if (plainDns != null) b.put("serve_plain_dns", plainDns.isChecked());
        b.put("server_name", serverName.getText().toString().trim());
        b.put("force_https", forceHttps.isChecked());
        b.put("port_https", Math.max(0, portValue(portHttps)));
        b.put("port_dns_over_tls", Math.max(0, portValue(portDot)));
        b.put("port_dns_over_quic", Math.max(0, portValue(portDoq)));
        if (certSource.sel == 0) {
            b.put("certificate_chain", "").put("certificate_path", certPath.getText().toString().trim());
        } else {
            b.put("certificate_path", "").put("certificate_chain", encode(certText.getText().toString().trim()));
        }
        if (keySource.sel == 0) {
            b.put("private_key", "").put("private_key_saved", false).put("private_key_path", keyPath.getText().toString().trim());
        } else if (useSavedKey != null && useSavedKey.isChecked()) {
            b.put("private_key_path", "").put("private_key", "").put("private_key_saved", true);
        } else {
            b.put("private_key_path", "").put("private_key_saved", false)
                    .put("private_key", encode(keyText.getText().toString().trim()));
        }
        return b;
    }

    /** Helyi ellenőrzés a webes felület szabályai szerint; hiba esetén a hibaüzenet, különben null. */
    String localError() {
        int[] ports = {portValue(portHttps), portValue(portDot), portValue(portDoq)};
        for (int p : ports) if (p < 0 || p > 65535) return S.s(R.string.s_the_port_must_be_between_0_and_65535);
        if (ports[0] > 0 && ports[0] == ports[1]) return S.s(R.string.s_the_https_and_dns_over_tls_ports_canno);
        if (plainDns != null && !plainDns.isChecked() && !enabled.isChecked()) {
            return S.s(R.string.s_to_disable_plain_dns_enable_at_least_o);
        }
        return null;
    }

    static boolean validPair(JSONObject r) {
        return r.optBoolean("valid_cert", false) && r.optBoolean("valid_key", false) && r.optBoolean("valid_pair", false);
    }

    // ---------------------------------------------------------------- műveletek

    private void validate() {
        final JSONObject b;
        try {
            b = body();
        } catch (Exception e) {
            return;
        }
        setPageStatus(S.s(R.string.s_checking), false);
        task(() -> new JSONObject(Net.api.post("tls/validate", b)), r -> {
            setPageStatus("", false);
            showStatus(r);
        });
    }

    private void saveConfig() {
        String err = localError();
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            return;
        }
        final JSONObject b;
        try {
            b = body();
        } catch (Exception e) {
            return;
        }
        StringBuilder msg = new StringBuilder(S.s(R.string.s_save_the_encryption_settings));
        String risk = connectionRisk(b);
        if (risk != null) msg.append("\n\n").append(risk);
        msg.append(S.s(R.string.s_if_the_settings_are_wrong_both_the_web));
        confirm(S.s(R.string.s_save_configuration), msg.toString(), S.s(R.string.s_save), () -> {
            setPageStatus(S.s(R.string.s_saving), false);
            task(() -> {
                if (b.optBoolean("enabled", false)) {
                    JSONObject v = new JSONObject(Net.api.post("tls/validate", b));
                    if (!validPair(v)) return new Object[]{false, v};
                }
                JSONObject r = new JSONObject(Net.api.post("tls/configure", b));
                return new Object[]{!b.optBoolean("enabled", false) || validPair(r), r};
            }, res -> {
                setPageStatus("", false);
                boolean ok = (Boolean) res[0];
                if (!ok) {
                    showStatus((JSONObject) res[1]);
                    Toast.makeText(this, S.s(R.string.s_the_certificate_or_the_key_is_invalid), Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, S.s(R.string.s_encryption_settings_saved), Toast.LENGTH_SHORT).show();
                load();
            });
        });
    }

    private void resetConfig() {
        final JSONObject b;
        try {
            b = copy(orig);
            for (String f : STATUS_FIELDS) b.remove(f);
            b.put("enabled", false).put("serve_plain_dns", true).put("server_name", "").put("force_https", false)
                    .put("port_https", 443).put("port_dns_over_tls", 853).put("port_dns_over_quic", 853)
                    .put("certificate_chain", "").put("private_key", "").put("certificate_path", "")
                    .put("private_key_path", "").put("private_key_saved", false);
        } catch (Exception e) {
            return;
        }
        save(() -> Net.api.post("tls/configure", b), S.s(R.string.s_encryption_settings_reset));
    }

    /** Figyelmeztetés, ha a mentés után az app a jelenlegi címen nem érné el a szervert. */
    String connectionRisk(JSONObject b) {
        Address a = Net.api == null ? null : Net.api.address;
        if (a == null) return null;
        boolean en = b.optBoolean("enabled", false);
        int https = b.optInt("port_https", 0);
        if (en && b.optBoolean("force_https", false) && !a.https()) {
            return S.s(R.string.s_the_app_currently_connects_over_http_a)
                    + S.s(R.string.s_https) + (https > 0 ? S.s(R.string.s_and_port) + https + S.s(R.string.s_x) : "") + S.s(R.string.s_otherwise_it_cannot_reach_the_server);
        }
        if (a.https() && (!en || https == 0)) {
            return S.s(R.string.s_the_app_currently_connects_over_https)
                    + S.s(R.string.s_switch_the_connection_to_http_in_the_s);
        }
        return null;
    }

    // ---------------------------------------------------------------- állapot megjelenítése

    void showStatus(JSONObject r) {
        certStatus.removeAllViews();
        keyStatus.removeAllViews();
        warning.removeAllViews();
        boolean hasCert = certSource.sel == 0 ? !certPath.getText().toString().trim().isEmpty()
                : !certText.getText().toString().trim().isEmpty();
        if (hasCert && r.has("valid_chain")) {
            statusTitle(certStatus);
            boolean chain = r.optBoolean("valid_chain", false);
            certStatus.addView(text(chain ? S.s(R.string.s_the_certificate_chain_is_valid) : S.s(R.string.s_the_certificate_chain_is_invalid), 14,
                    chain ? OK_TEXT : BAD_TEXT));
            if (r.optBoolean("valid_cert", false)) {
                if (!r.optString("subject", "").isEmpty()) kv(certStatus, S.s(R.string.s_subject), r.optString("subject", ""));
                if (!r.optString("issuer", "").isEmpty()) kv(certStatus, S.s(R.string.s_issuer), r.optString("issuer", ""));
                String na = r.optString("not_after", "");
                if (!na.isEmpty() && !na.startsWith("0001-")) kv(certStatus, S.s(R.string.s_expires_2), FilterListsActivity.date(na));
                JSONArray dn = r.optJSONArray("dns_names");
                if (dn != null && dn.length() > 0) kv(certStatus, S.s(R.string.s_hostnames), join(dn).replace("\n", ", "));
            }
        }
        boolean hasKey = keySource.sel == 0 ? !keyPath.getText().toString().trim().isEmpty()
                : (useSavedKey != null && useSavedKey.isChecked()) || !keyText.getText().toString().trim().isEmpty();
        if (hasKey && r.has("valid_key")) {
            statusTitle(keyStatus);
            boolean vk = r.optBoolean("valid_key", false);
            String type = r.optString("key_type", "");
            String ty = type.isEmpty() ? "" : type + " ";
            keyStatus.addView(text(vk ? S.s(R.string.s_this_is_a_valid) + ty + S.s(R.string.s_private_key_2) : S.s(R.string.s_this_is_an_invalid) + ty + S.s(R.string.s_private_key_2),
                    14, vk ? OK_TEXT : BAD_TEXT));
        }
        String w = r.optString("warning_validation", "");
        if (!w.isEmpty()) {
            boolean ok = validPair(r);
            TextView tw = text(ok ? S.s(R.string.s_warning) + w : w, 14, ok ? MUTED : BAD_TEXT);
            tw.setPadding(0, dp(8), 0, 0);
            warning.addView(tw);
        }
    }

    private void statusTitle(LinearLayout parent) {
        TextView h = text(S.s(R.string.s_status), 13, MUTED);
        h.setPadding(0, dp(10), 0, dp(2));
        parent.addView(h);
    }
}
