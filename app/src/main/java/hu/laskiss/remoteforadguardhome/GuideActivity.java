package hu.laskiss.remoteforadguardhome;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Beállítási útmutató: a szerver DNS-címei és eszközönkénti beállítási lépések, a webes felület szerint. */
public class GuideActivity extends BaseActivity {

    static String[] TABS() {

        return new String[]{"Router", "Windows", "macOS", "Android", "iOS", S.s(R.string.s_dns_privacy)};

    }

    static String[][] STEPS() {

        return new String[][]{
            {S.s(R.string.s_open_your_router_s_settings_this_is_us)
                    + S.s(R.string.s_or_http_192_168_1_1_and_it_asks_for_a)
                    + S.s(R.string.s_the_router_s_box_the_label_on_its_unde)
                    + S.s(R.string.s_you_will_probably_lose_all_its_setting)
                    + S.s(R.string.s_and_use_it_to_reach_the_settings),
                    S.s(R.string.s_find_the_dhcp_dns_settings_look_for_th)
                            + S.s(R.string.s_of_13_digits),
                    S.s(R.string.s_enter_the_address_of_the_adguard_home),
                    S.s(R.string.s_on_some_routers_a_custom_dns_server_ca)
                            + S.s(R.string.s_otherwise_check_the_router_s_manual_fo)},
            {S.s(R.string.s_open_the_control_panel_from_the_start),
                    S.s(R.string.s_choose_the_network_and_internet_catego),
                    S.s(R.string.s_in_the_left_panel_click_change_adapter),
                    S.s(R.string.s_right_click_the_active_connection_and),
                    S.s(R.string.s_find_internet_protocol_version_4_tcp_i),
                    S.s(R.string.s_choose_use_the_following_dns_server_ad)},
            {S.s(R.string.s_click_the_apple_icon_and_choose_system),
                    S.s(R.string.s_click_network),
                    S.s(R.string.s_select_the_first_connection_in_the_lis),
                    S.s(R.string.s_choose_the_dns_tab_and_enter_the_addre)},
            {S.s(R.string.s_on_the_android_home_screen_tap_the_set),
                    S.s(R.string.s_tap_wi_fi_all_available_networks_are_s),
                    S.s(R.string.s_long_press_the_network_you_are_connect),
                    S.s(R.string.s_on_some_devices_you_need_to_open_advan)
                            + S.s(R.string.s_switch_the_ip_settings_from_dhcp_to_st),
                    S.s(R.string.s_change_dns_1_and_dns_2_to_the_addresse)},
            {S.s(R.string.s_on_the_home_screen_tap_the_settings_ic),
                    S.s(R.string.s_choose_wi_fi_dns_cannot_be_set_for_mob),
                    S.s(R.string.s_tap_the_name_of_the_network_you_are_us),
                    S.s(R.string.s_in_the_dns_field_enter_the_addresses_o)},
    };

    }

    static String ROUTER_DESC() {

        return S.s(R.string.s_this_setting_automatically_applies_to)
            + S.s(R.string.s_so_you_do_not_need_to_set_them_up_one);

    }

    private List<String> addresses = new ArrayList<>();
    private LinearLayout tabBody;
    private int tab;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage(S.s(R.string.s_setup_guide));
        load();
    }

    @Override
    void load() {
        loading();
        task(() -> Net.api.getJson("status"), this::render);
    }

    private void render(JSONObject st) {
        loaded();
        addresses = addresses(st);

        LinearLayout s = section(S.s(R.string.s_set_up_your_devices),
                S.s(R.string.s_to_use_adguard_home_you_need_to_set_up));
        TextView l = text(S.s(R.string.s_the_adguard_dns_server_listens_on_the), 13, MUTED);
        l.setPadding(0, dp(10), 0, dp(4));
        s.addView(l);
        if (addresses.isEmpty()) emptyNote(s, S.s(R.string.s_the_server_did_not_return_an_address));
        for (final String a : addresses) {
            TextView t = text(a, 15, TEXT);
            t.setTypeface(Typeface.MONOSPACE);
            t.setPadding(0, dp(6), 0, dp(6));
            t.setClickable(true);
            t.setFocusable(true);
            t.setOnClickListener(v -> copy(a));
            s.addView(t);
        }

        LinearLayout g = section(S.s(R.string.s_guide), null);
        final Choice c = new Choice(g, S.s(R.string.s_device), TABS(), tab);
        tabBody = col();
        g.addView(tabBody, matchWrap());
        c.onChange = () -> {
            tab = c.sel;
            showTab();
        };
        showTab();
    }

    /** A szerver címei a status válaszból (dns_addresses). */
    static List<String> addresses(JSONObject st) {
        ArrayList<String> out = new ArrayList<>();
        JSONArray a = st.optJSONArray("dns_addresses");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    static String first(List<String> l, String prefix) {
        for (String s : l) if (s.startsWith(prefix)) return s;
        return null;
    }

    private void showTab() {
        tabBody.removeAllViews();
        if (tab < STEPS().length) {
            if (tab == 0) para(ROUTER_DESC(), MUTED);
            String[] st = STEPS()[tab];
            for (int i = 0; i < st.length; i++) step(i + 1, st[i]);
            return;
        }
        String tls = first(addresses, "tls://");
        String https = first(addresses, "https://");
        if (tls != null) para(S.s(R.string.s_dns_over_tls_use_this_address) + tls, TEXT);
        if (https != null) para(S.s(R.string.s_dns_over_https_use_this_address) + https, TEXT);
        if (tls == null && https == null) {
            para(S.s(R.string.s_to_use_dns_over_https_or_dns_over_tls)
                    + S.s(R.string.s_settings_2), TEXT);
            button(buttonRow(tabBody), S.s(R.string.s_encryption_settings), 0, v -> open(EncryptionActivity.class, 0));
            return;
        }
        para(S.s(R.string.s_apps_you_can_use), MUTED);
        head("Android");
        bullet(S.s(R.string.s_android_9_and_newer_supports_dns_over)
                + S.s(R.string.s_advanced_private_dns_and_enter_the_dom));
        bullet(S.s(R.string.s_adguard_for_android_supports_dns_over));
        bullet(S.s(R.string.s_intra_adds_dns_over_https_support_to_a));
        head("iOS");
        bullet(S.s(R.string.s_adguard_for_ios_supports_dns_over_http));
        bullet(S.s(R.string.s_dnscloak_supports_dns_over_https_but_f)
                + "(dnscrypt.info/stamps).");
        head(S.s(R.string.s_other_implementations));
        bullet(S.s(R.string.s_adguard_home_itself_can_be_a_secure_dn));
        bullet(S.s(R.string.s_dnsproxy_supports_all_the_well_known_s));
        bullet(S.s(R.string.s_dnscrypt_proxy_supports_dns_over_https));
        bullet(S.s(R.string.s_mozilla_firefox_supports_dns_over_http));
        bullet(S.s(R.string.s_more_implementations_dnscrypt_info_imp));
        head(S.s(R.string.s_ios_and_macos_configuration));
        para(S.s(R.string.s_since_ios_14_and_macos_big_sur_a_dns_o)
                + S.s(R.string.s_with_a_mobileconfig_file_you_can_downl), TEXT);
    }

    private void para(String s, int color) {
        TextView t = text(s, 14, color);
        t.setPadding(0, dp(10), 0, 0);
        tabBody.addView(t);
    }

    private void head(String s) {
        TextView t = text(s, 15, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, 0);
        tabBody.addView(t);
    }

    private void bullet(String s) {
        TextView t = text("•  " + s, 14, TEXT);
        t.setPadding(dp(4), dp(6), 0, 0);
        tabBody.addView(t);
    }

    private void step(int n, String s) {
        LinearLayout r = row();
        r.setGravity(android.view.Gravity.TOP);
        r.setPadding(0, dp(10), 0, 0);
        TextView num = text(n + ".", 14, GREEN);
        num.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(num, new LinearLayout.LayoutParams(dp(26), LinearLayout.LayoutParams.WRAP_CONTENT));
        r.addView(text(s, 14, TEXT), weight(1));
        tabBody.addView(r, matchWrap());
    }

    private void copy(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("dns", s));
        Toast.makeText(this, S.s(R.string.s_copied) + s, Toast.LENGTH_SHORT).show();
    }
}
