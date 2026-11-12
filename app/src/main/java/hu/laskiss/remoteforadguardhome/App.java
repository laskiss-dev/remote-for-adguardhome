package hu.laskiss.remoteforadguardhome;

import android.app.Application;

/** Az app indulásakor beállítja a szövegek forrását (S), mielőtt bármelyik képernyő létrejön. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        S.init(this);
    }
}
