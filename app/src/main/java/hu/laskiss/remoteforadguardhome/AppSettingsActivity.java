package hu.laskiss.remoteforadguardhome;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;

/** Alkalmazás beállítások: téma és képernyő ébren tartása. Ezek a telefonon tárolódnak, a szervert nem érintik. */
public class AppSettingsActivity extends BaseActivity {

    static final String[] THEMES = {"Rendszer szerint", "Sötét", "Világos"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage("Alkalmazás beállítások");
        load();
    }

    @Override
    void load() {
        content.removeAllViews();
        setPageStatus("", false);

        LinearLayout look = section("Megjelenés", null);
        final Choice theme = new Choice(look, "Téma", THEMES, prefs.getInt("theme", 1));
        theme.onChange = () -> {
            prefs.edit().putInt("theme", theme.sel).apply();
            if (wantsLight(this, prefs) != light) {
                palette(wantsLight(this, prefs));
                recreate();
            }
        };

        LinearLayout scr = section("Képernyő", null);
        Switch awake = switchRow(scr, "Képernyő ébren tartása",
                "Amíg az app előtérben van, a telefon nem sötétíti el és nem zárja le a képernyőt. "
                        + "Ha kilépsz az appból, a telefon a szokásos módon működik.",
                prefs.getBoolean("keepAwake", false));
        awake.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean("keepAwake", on).apply();
            applyKeepAwake();
        });
    }
}
