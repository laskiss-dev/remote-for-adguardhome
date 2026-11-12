package hu.laskiss.remoteforadguardhome;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.List;

/** Szerverek: a felvett AdGuard Home szerverek listája, váltás, felvétel, szerkesztés, törlés. */
public class ServersActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage(S.s(R.string.s_servers));
        load();
    }

    @Override
    void load() {
        content.removeAllViews();
        setPageStatus("", false);
        List<Servers.Server> list = Servers.load(prefs);
        Servers.Server active = Servers.active(prefs);

        TextView intro = text(S.s(R.string.s_tap_a_server_to_manage_it_passwords_ar)
                + S.s(R.string.s_or_if_you_do_not_want_them_saved_the_a), 14, MUTED);
        intro.setPadding(0, dp(8), 0, 0);
        content.addView(intro);
        button(buttonRow(content), S.s(R.string.s_add_server), 1, v -> editServer(null));

        LinearLayout sec = section(S.s(R.string.s_added_servers), null);
        if (list.isEmpty()) {
            emptyNote(sec, S.s(R.string.s_no_server_added_yet));
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            final Servers.Server s = list.get(i);
            if (i > 0) divider(sec);
            boolean isActive = active != null && active.id.equals(s.id) && Net.api != null;
            LinearLayout r = row();
            r.setPadding(0, dp(10), 0, dp(10));
            r.setMinimumHeight(dp(56));
            r.setClickable(true);
            r.setFocusable(true);
            r.setContentDescription(s.name + (isActive ? S.s(R.string.s_active) : ""));
            r.setOnClickListener(v -> {
                if (Net.server != null && Net.server.id.equals(s.id) && Net.api != null) {
                    open(DashboardActivity.class, 0);
                } else {
                    activate(s);
                }
            });

            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.ic_check);
            mark.setColorFilter(GREEN);
            mark.setVisibility(isActive ? View.VISIBLE : View.INVISIBLE);
            mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            r.addView(mark, new LinearLayout.LayoutParams(dp(24), dp(24)));

            LinearLayout col = col();
            col.setPadding(dp(12), 0, dp(4), 0);
            TextView n = text(s.name, 16, TEXT);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            col.addView(n);
            Address a = s.address();
            TextView addr = text(a == null ? s.url : a.display(), 13, SUB);
            addr.setSingleLine(true);
            addr.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            col.addView(addr);
            String proto = a == null ? "" : a.https() ? "HTTPS" : S.s(R.string.s_http_unencrypted);
            String pw = s.user.isEmpty() ? S.s(R.string.s_no_password) : s.savePass ? S.s(R.string.s_password_saved) : S.s(R.string.s_asks_for_password_at_startup);
            TextView meta = text(proto + " · " + pw, 12, a != null && !a.https() ? BAD_TEXT : MUTED);
            col.addView(meta);
            r.addView(col, weight(1));
            r.addView(iconButton(R.drawable.ic_more, S.s(R.string.s_actions), v -> rowMenu(v, s)), new LinearLayout.LayoutParams(dp(44), dp(44)));
            sec.addView(r, matchWrap());
        }
    }

    @Override
    void onServersChanged() {
        load();
    }

    @Override
    void onServerChanged() {
        load();
        showServer();
    }

    private void rowMenu(View anchor, final Servers.Server s) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, S.s(R.string.s_edit));
        pm.getMenu().add(0, 2, 1, S.s(R.string.s_delete));
        pm.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) editServer(s);
            else confirm(S.s(R.string.s_delete_server), S.s(R.string.s_delete_the_server) + s.name + S.s(R.string.s_its_saved_password_is_deleted_too),
                    S.s(R.string.s_delete), () -> delete(s));
            return true;
        });
        pm.show();
    }

    private void delete(Servers.Server s) {
        List<Servers.Server> list = Servers.load(prefs);
        Servers.Server cur = Servers.find(list, s.id);
        if (cur != null) {
            Servers.forget(cur);
            list.remove(cur);
        }
        Servers.save(prefs, list);
        boolean wasActive = Net.server != null && Net.server.id.equals(s.id);
        if (wasActive) {
            Net.api = null;
            Net.server = null;
            Net.protection = null;
            MainActivity.resetState();
            if (!list.isEmpty()) {
                activate(list.get(0));
            } else {
                Servers.setActive(prefs, null);
                showServer();
                showProtection();
            }
        }
        load();
    }
}
