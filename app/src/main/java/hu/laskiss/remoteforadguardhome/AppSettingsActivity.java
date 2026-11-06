package hu.laskiss.remoteforadguardhome;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;

/** Alkalmazás beállítások: téma, képernyő ébren tartása és frissítések. Ezek a telefonon tárolódnak, a szervert nem érintik. */
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

        LinearLayout upd = section("Frissítések",
                "Telepített verzió: " + Updater.currentVersionName(this) + ". Az új verziókat a GitHubról "
                        + "(" + Updater.REPO + ") tölti le; telepítés előtt ellenőrzi, hogy sértetlen és "
                        + "ugyanazzal a kulccsal van aláírva, mint ez az app.");
        Switch auto = switchRow(upd, "Frissítések automatikus keresése",
                "Induláskor, naponta legfeljebb egyszer megnézi, van-e új verzió, és ha van, megkérdezi, telepítsd-e.",
                prefs.getBoolean(Updater.PREF_AUTO, true));
        auto.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean(Updater.PREF_AUTO, on).apply());
        button(buttonRow(upd), "Frissítés keresése most", 1, v -> Updater.manualCheck(this, prefs));
    }
}
