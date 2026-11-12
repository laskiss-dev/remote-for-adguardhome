package hu.laskiss.remoteforadguardhome;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;

/** Alkalmazás beállítások: téma, képernyő ébren tartása és frissítések. Ezek a telefonon tárolódnak, a szervert nem érintik. */
public class AppSettingsActivity extends BaseActivity {

    static String[] THEMES() {

        return new String[]{S.s(R.string.s_follow_system), S.s(R.string.s_dark), S.s(R.string.s_light)};

    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage(S.s(R.string.s_app_settings));
        load();
    }

    @Override
    void load() {
        content.removeAllViews();
        setPageStatus("", false);

        LinearLayout look = section(S.s(R.string.s_appearance), null);
        final Choice theme = new Choice(look, S.s(R.string.s_theme), THEMES(), prefs.getInt("theme", 1));
        theme.onChange = () -> {
            prefs.edit().putInt("theme", theme.sel).apply();
            if (wantsLight(this, prefs) != light) {
                palette(wantsLight(this, prefs));
                recreate();
            }
        };

        LinearLayout scr = section(S.s(R.string.s_screen), null);
        Switch awake = switchRow(scr, S.s(R.string.s_keep_screen_on),
                S.s(R.string.s_while_the_app_is_in_the_foreground_the)
                        + S.s(R.string.s_when_you_leave_the_app_the_phone_works),
                prefs.getBoolean("keepAwake", false));
        awake.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean("keepAwake", on).apply();
            applyKeepAwake();
        });

        String ver = "?";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        section(S.s(R.string.s_about), S.s(R.string.s_installed_version) + ver + S.s(R.string.s_the_app_does_not_download_or_install)
                + S.s(R.string.s_updates_you_get_new_versions_from_goog));
    }
}
