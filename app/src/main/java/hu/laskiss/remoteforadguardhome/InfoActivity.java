package hu.laskiss.remoteforadguardhome;

import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/** Információ: a program neve, verziója, készítője, a szerver adatai és a változásnapló. */
public class InfoActivity extends BaseActivity {

    /** Változásnapló, a legújabb elöl: verzió, dátum, változások. */
    static String[][] CHANGES() {
        return new String[][]{
            {"2.6.1", "2026-11-13",
                    S.s(R.string.upd_log_3)},
            {"2.6.0", "2026-11-13",
                    S.s(R.string.upd_log_1),
                    S.s(R.string.upd_log_2)},
            {"2.5.0", "2026-11-12",
                    S.s(R.string.s_new_english_language_the_app_follows_th),
                    S.s(R.string.s_the_in_app_updater_and_the_install_perm),
                    S.s(R.string.s_a_privacy_policy_was_added_to_the_repos)},
            {"2.3", "2026-11-03",
                    S.s(R.string.s_the_menu_structure_now_matches_the_adg),
                    S.s(R.string.s_new_settings_encryption_settings_https),
                    S.s(R.string.s_new_setup_guide_with_the_server_s_dns)},
            {"2.2", "2026-11-01",
                    S.s(R.string.s_query_log_new_item_in_the_menu_shown_o),
                    S.s(R.string.s_the_domain_is_opened_as_an_https_addre)},
            {"2.1", "2026-11-01",
                    S.s(R.string.s_query_log_short_sound_when_new_green_h),
                    S.s(R.string.s_can_be_turned_off_in_the_menu_if_highl)},
            {"2.0", "2026-10-31",
                    S.s(R.string.s_multiple_servers_in_the_servers_item_o),
                    S.s(R.string.s_connection_test_in_the_server_settings),
                    S.s(R.string.s_passwords_are_stored_encrypted_android),
                    S.s(R.string.s_https_is_the_default_the_app_allows_un),
                    S.s(R.string.s_server_address_validation_shorter_and),
                    S.s(R.string.s_the_server_name_is_also_shown_in_the_h)},
            {"1.9.3", "2026-10-31",
                    S.s(R.string.s_new_name_remote_for_adguard_home_forme),
                    S.s(R.string.s_new_application_id_and_new_signing_key)},
            {"1.9.2", "2026-10-31",
                    S.s(R.string.s_license_gnu_gpl_version_3_the_full_tex),
                    S.s(R.string.s_the_information_page_states_that_this)},
            {"1.9.1", "2026-10-31",
                    S.s(R.string.s_dashboard_smaller_numbers_in_general_s),
                    S.s(R.string.s_the_text_of_the_top_lists_no_longer_ru)},
            {"1.9", "2026-10-31",
                    S.s(R.string.s_new_flat_vector_app_icon_a_house_home),
                    S.s(R.string.s_smaller_apk_the_icon_is_sharp_at_every)},
            {"1.8", "2026-10-31",
                    S.s(R.string.s_dashboard_following_the_web_interface),
                    S.s(R.string.s_the_top_lists_are_in_fixed_height_scro),
                    S.s(R.string.s_tapping_the_charts_and_statistics_open)},
            {"1.7", "2026-10-31",
                    S.s(R.string.s_query_log_when_scrolling_the_title_the),
                    S.s(R.string.s_when_scrolled_down_a_button_appears_in)},
            {"1.6", "2026-10-29",
                    S.s(R.string.s_new_app_icon),
                    S.s(R.string.s_the_apk_file_name_adguardhome_remote)},
            {"1.5", "2026-10-29",
                    S.s(R.string.s_new_app_settings_page_dark_light_or_fo),
                    S.s(R.string.s_keep_the_screen_on_while_the_app_is_in),
                    S.s(R.string.s_fixed_the_query_log_opened_from_the_me)},
            {"1.4", "2026-10-29",
                    S.s(R.string.s_in_the_query_log_the_date_is_also_show),
                    S.s(R.string.s_adding_a_blocklist_like_in_the_web_int),
                    S.s(R.string.s_the_items_of_filters_and_settings_use)},
            {"1.3", "2026-10-29",
                    S.s(R.string.s_new_name_adguardhome_remote),
                    S.s(R.string.s_the_dashboard_became_the_start_screen),
                    S.s(R.string.s_the_query_log_filter_has_a_new_order_a),
                    S.s(R.string.s_new_information_page_with_a_changelog)},
            {"1.2", "2026-10-29",
                    S.s(R.string.s_in_the_query_log_rows_that_arrived_wit),
                    S.s(R.string.s_the_highlighting_can_be_turned_off_in)},
            {"1.1", "2026-10-28",
                    S.s(R.string.s_dashboard_with_statistics_and_top_list),
                    S.s(R.string.s_filters_blocklists_and_allowlists_cust),
                    S.s(R.string.s_client_settings_and_access_lists),
                    S.s(R.string.s_general_dns_and_dhcp_settings),
                    S.s(R.string.s_shared_menu_following_the_web_interfac)},
            {"1.0", "2026-10-28",
                    S.s(R.string.s_query_log_with_portrait_and_landscape),
                    S.s(R.string.s_automatic_refresh_that_downloads_only),
                    S.s(R.string.s_search_and_filtering_on_the_server_blo),
                    S.s(R.string.s_turn_protection_on_and_off_pause_it)},
    };
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage(S.s(R.string.s_information));
        load();
    }

    @Override
    void load() {
        content.removeAllViews();
        setPageStatus("", false);

        LinearLayout app = section("Remote for AdGuard Home", null);
        kv(app, S.s(R.string.s_version), versionName());
        kv(app, S.s(R.string.s_developed_by), S.s(R.string.s_laszlo_kiss));
        TextView about = text(S.s(R.string.s_manage_your_own_adguard_home_server_fr)
                + S.s(R.string.s_filters_clients_and_settings), 13, MUTED);
        about.setPadding(0, dp(8), 0, 0);
        app.addView(about);
        TextView indep = text(S.s(R.string.s_unofficial_app_an_independent_project)
                + S.s(R.string.s_developers_or_the_adguard_team_and_was)
                + S.s(R.string.s_adguard_is_a_trademark_of_adguard_and), 13, MUTED);
        indep.setPadding(0, dp(8), 0, 0);
        app.addView(indep);

        final LinearLayout srv = section(S.s(R.string.s_server), null);
        kv(srv, S.s(R.string.s_name), Net.server == null ? S.s(R.string.s_not_selected) : Net.server.name);
        kv(srv, S.s(R.string.s_address), Net.api == null ? "–" : (Net.api.address.https() ? "https://" : "http://") + Net.api.host);
        final TextView ver = text("…", 14, TEXT);
        LinearLayout line = row();
        line.setPadding(0, dp(4), 0, dp(4));
        line.addView(text("AdGuard Home", 14, LABEL), new LinearLayout.LayoutParams(dp(130), -2));
        line.addView(ver, weight(1));
        srv.addView(line, matchWrap());
        if (Net.api != null) {
            task(() -> Net.api.getJson("status"), (JSONObject r) -> ver.setText(r.optString("version", S.s(R.string.s_unknown))));
        } else {
            ver.setText("–");
        }

        LinearLayout lic = section(S.s(R.string.s_license), null);
        kv(lic, S.s(R.string.s_license), S.s(R.string.s_gnu_gpl_version_3_gplv3));
        kv(lic, S.s(R.string.s_copyright), S.s(R.string.s_2026_laszlo_kiss));
        TextView notice = text(S.s(R.string.s_this_program_is_free_software_you_can)
                + S.s(R.string.s_license_version_3_as_published_by_the)
                + S.s(R.string.s_that_it_will_be_useful_but_without_any)
                + S.s(R.string.s_for_a_particular_purpose_see_the_full), 13, MUTED);
        notice.setPadding(0, dp(8), 0, 0);
        lic.addView(notice);
        button(buttonRow(lic), S.s(R.string.s_full_license_text_english), 0, v -> showLicense());

        LinearLayout log = section(S.s(R.string.s_changelog), null);
        final String[][] changes = CHANGES();
        for (int i = 0; i < changes.length; i++) {
            String[] v = changes[i];
            if (i > 0) divider(log);
            LinearLayout c = col();
            c.setPadding(0, dp(10), 0, dp(8));
            TextView h = text(v[0] + "  ·  " + java.time.LocalDate.parse(v[1])
                    .format(java.time.format.DateTimeFormatter.ofPattern(S.s(R.string.s_date_fmt))), 15, TEXT);
            h.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(h);
            for (int j = 2; j < v.length; j++) {
                TextView t = text("•  " + v[j], 14, SUB);
                t.setPadding(dp(4), dp(3), 0, 0);
                c.addView(t);
            }
            log.addView(c, matchWrap());
        }
    }

    /** A GPLv3 teljes, eredeti angol szövege az app csomagjából (assets/LICENSE.txt). */
    private void showLicense() {
        String text;
        try {
            java.io.InputStream in = getAssets().open("LICENSE.txt");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            text = bo.toString("UTF-8");
        } catch (Exception e) {
            text = S.s(R.string.s_the_license_text_cannot_be_read_it_is);
        }
        TextView t = text(text, 11, TEXT);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(dp(18), dp(8), dp(18), dp(8));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(t);
        new android.app.AlertDialog.Builder(this)
                .setTitle("GNU General Public License v3")
                .setView(sv)
                .setPositiveButton(S.s(R.string.s_close), null)
                .show();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return CHANGES()[0][0];
        }
    }
}
