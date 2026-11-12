package hu.laskiss.remoteforadguardhome;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** DNS átírások: egy domainre saját választ ad (pl. helyi gépek neve). */
public class RewritesActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupPage(S.s(R.string.s_dns_rewrites));
        load();
    }

    @Override
    void load() {
        loading();
        task(() -> {
            Object[] r = new Object[2];
            r[0] = Net.api.getArray("rewrite/list");
            try { r[1] = Net.api.getJson("rewrite/settings"); } catch (Exception e) { r[1] = null; }
            return r;
        }, r -> render((JSONArray) r[0], (JSONObject) r[1]));
    }

    private void render(JSONArray list, JSONObject settings) {
        loaded();
        TextView intro = text(S.s(R.string.s_for_a_domain_it_answers_with_the_ip_ad)
                + S.s(R.string.s_the_wildcard_can_also_be_used_e_g_home), 14, MUTED);
        intro.setPadding(0, dp(8), 0, 0);
        content.addView(intro);
        if (settings != null && settings.has("enabled")) {
            LinearLayout s = section(S.s(R.string.s_rewrites), null);
            Switch sw = switchRow(s, S.s(R.string.s_dns_rewrites_enabled), null, settings.optBoolean("enabled", true));
            sw.setOnCheckedChangeListener((v, on) -> apply(() ->
                    Net.api.put("rewrite/settings/update", new JSONObject().put("enabled", on)),
                    on ? S.s(R.string.s_rewrites_on) : S.s(R.string.s_rewrites_off)));
        }
        LinearLayout btns = buttonRow(content);
        button(btns, S.s(R.string.s_add_rewrite), 1, v -> editDialog(null));

        LinearLayout sec = section(S.s(R.string.s_list_of_rewrites), null);
        if (list.length() == 0) {
            emptyNote(sec, S.s(R.string.s_no_rewrites_yet));
            return;
        }
        for (int i = 0; i < list.length(); i++) {
            final JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            if (i > 0) divider(sec);
            LinearLayout r = row();
            r.setPadding(0, dp(10), 0, dp(10));
            if (o.has("enabled")) {
                Switch sw = new Switch(this);
                sw.setChecked(o.optBoolean("enabled", true));
                sw.setContentDescription(o.optString("domain", "") + S.s(R.string.s_enabled));
                sw.setOnCheckedChangeListener((v, on) -> update(o, o.optString("domain", ""), o.optString("answer", ""), on));
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
                sl.rightMargin = dp(12);
                r.addView(sw, sl);
            }
            LinearLayout c = col();
            TextView d = text(o.optString("domain", ""), 15, TEXT);
            d.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(d);
            c.addView(text("→ " + o.optString("answer", ""), 14, SUB));
            r.addView(c, weight(1));
            r.addView(iconButton(R.drawable.ic_more, S.s(R.string.s_actions), v -> rowMenu(v, o)), new LinearLayout.LayoutParams(dp(44), dp(44)));
            sec.addView(r, matchWrap());
        }
    }

    private void rowMenu(View anchor, final JSONObject o) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, S.s(R.string.s_edit));
        pm.getMenu().add(0, 2, 1, S.s(R.string.s_delete));
        pm.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) editDialog(o);
            else confirm(S.s(R.string.s_delete_rewrite), S.s(R.string.s_delete_this_rewrite) + o.optString("domain", "") + " → " + o.optString("answer", ""),
                    S.s(R.string.s_delete), () -> save(() -> Net.api.post("rewrite/delete", copy(o)), S.s(R.string.s_rewrite_deleted)));
            return true;
        });
        pm.show();
    }

    private void update(final JSONObject target, final String domain, final String answer, final boolean enabled) {
        save(() -> {
            JSONObject upd = new JSONObject().put("domain", domain).put("answer", answer);
            if (target.has("enabled")) upd.put("enabled", enabled);
            return Net.api.put("rewrite/update", new JSONObject().put("target", copy(target)).put("update", upd));
        }, S.s(R.string.s_rewrite_saved));
    }

    private void editDialog(final JSONObject o) {
        LinearLayout form = col();
        form.setPadding(dp(22), dp(8), dp(22), 0);
        final EditText domain = field(form, "Domain", S.s(R.string.s_e_g_nas_home_or_example_org),
                o == null ? "" : o.optString("domain", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText answer = field(form, S.s(R.string.s_response), S.s(R.string.s_ip_address_or_domain),
                o == null ? "" : o.optString("answer", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        ScrollView sv = new ScrollView(this);
        sv.addView(form);
        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(o == null ? S.s(R.string.s_new_rewrite) : S.s(R.string.s_edit_rewrite))
                .setView(sv)
                .setPositiveButton(S.s(R.string.s_save), null)
                .setNegativeButton(S.s(R.string.s_cancel), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String dm = domain.getText().toString().trim();
            final String an = answer.getText().toString().trim();
            if (dm.isEmpty()) { domain.setError(S.s(R.string.s_enter_the_domain)); return; }
            if (an.isEmpty()) { answer.setError(S.s(R.string.s_enter_the_answer)); return; }
            d.dismiss();
            if (o == null) {
                save(() -> Net.api.post("rewrite/add", new JSONObject().put("domain", dm).put("answer", an)), S.s(R.string.s_rewrite_added));
            } else {
                update(o, dm, an, o.optBoolean("enabled", true));
            }
        }));
        d.show();
    }
}
