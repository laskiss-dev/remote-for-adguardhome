package hu.laskiss.remoteforadguardhome;

import android.content.Context;

/**
 * Szövegek a nyelvi erőforrásokból (res/values: angol, res/values-hu: magyar).
 * Az alkalmazás-kontextust az App állítja be induláskor; a nyelvet a telefon nyelve dönti el.
 */
final class S {
    private static Context app;

    private S() {
    }

    static void init(Context c) {
        app = c.getApplicationContext();
    }

    static String s(int id) {
        return app.getString(id);
    }
}
