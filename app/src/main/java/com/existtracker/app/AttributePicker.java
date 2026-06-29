package com.existtracker.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a dialog letting the user EITHER pick one of their existing Exist
 * attributes from a dropdown, OR create a brand-new custom attribute (with a
 * label and a chosen group). On success it calls back with the chosen
 * attribute's machine name, which the caller stores.
 *
 * This avoids typos: instead of typing a raw attribute name, the user selects
 * a real one, and new attributes are created the moment they're chosen so they
 * always exist before the app tries to write to them.
 */
public class AttributePicker {

    public interface OnPicked {
        void picked(String attributeName);
    }

    // The standard Exist groups a custom attribute can live in.
    private static final String[] GROUP_NAMES = {
            "activity", "productivity", "food", "finance", "health",
            "sleep", "social", "media", "mood", "location", "weather", "custom"
    };
    private static final String[] GROUP_LABELS = {
            "Activity", "Productivity", "Food & drink", "Finances", "Health",
            "Sleep", "Social", "Media", "Mood", "Location", "Weather", "Custom"
    };

    private final Activity activity;
    private final Settings settings;

    public AttributePicker(Activity activity) {
        this.activity = activity;
        this.settings = new Settings(activity);
    }

    /** Entry point: load attributes in the background, then show the chooser. */
    public void choose(String currentName, String suggestedGroup, OnPicked cb) {
        if (!settings.isLoggedIn()) {
            toast("Connect to Exist first (Step 1).");
            return;
        }
        final ProgressDialog pd = new ProgressDialog(activity);
        pd.setMessage("Loading your Exist attributes…");
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            List<ExistApi.AttrInfo> attrs = new ArrayList<>();
            String error = null;
            try {
                attrs = new ExistApi(activity).listAllAttributes();
            } catch (Exception e) {
                error = e.getMessage();
            }
            final List<ExistApi.AttrInfo> finalAttrs = attrs;
            final String finalError = error;
            activity.runOnUiThread(() -> {
                pd.dismiss();
                if (finalError != null) {
                    toast("Couldn't load attributes: " + finalError);
                    // Still allow create-new even if listing failed.
                }
                showChooser(finalAttrs, currentName, suggestedGroup, cb);
            });
        }).start();
    }

    private void showChooser(List<ExistApi.AttrInfo> attrs, String currentName,
                             String suggestedGroup, OnPicked cb) {
        // Build the list of existing attributes (no "create" row here anymore;
        // there's a dedicated button below so it's a single obvious tap).
        List<String> display = new ArrayList<>();
        if (attrs.isEmpty()) {
            display.add("(no existing attributes found)");
        }
        for (ExistApi.AttrInfo a : attrs) {
            String g = a.groupLabel == null ? "" : a.groupLabel;
            String own = a.owned ? "" : "  (owned by another app)";
            display.add(a.label + "  ·  " + g + own);
        }

        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, display);
        spinner.setAdapter(adapter);

        // Pre-select the current attribute if it's in the list.
        for (int i = 0; i < attrs.size(); i++) {
            if (attrs.get(i).name.equals(currentName)) { spinner.setSelection(i); break; }
        }

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16); box.setPadding(p, p, p, p);
        TextView hint = new TextView(activity);
        hint.setText("Pick one of your existing Exist attributes below, or tap "
                + "“Create new” to make a brand-new one.");
        hint.setTextSize(13);
        box.addView(hint);
        box.addView(spinner);

        final boolean hasAttrs = !attrs.isEmpty();
        new AlertDialog.Builder(activity)
                .setTitle("Choose attribute")
                .setView(box)
                // Positive = use the selected existing attribute.
                .setPositiveButton("Use selected", (d, w) -> {
                    if (!hasAttrs) { toast("No existing attributes — tap Create new."); return; }
                    int idx = spinner.getSelectedItemPosition();
                    ExistApi.AttrInfo a = attrs.get(idx);
                    if (!a.owned) {
                        confirmAcquire(a, cb);
                    } else {
                        cb.picked(a.name);
                        toast("Using “" + a.label + "”.");
                    }
                })
                // Neutral = create a brand-new attribute (always available).
                .setNeutralButton("＋ Create new", (d, w) -> showCreateForm(suggestedGroup, cb))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** If the attribute is owned by another app, offer to take ownership. */
    private void confirmAcquire(ExistApi.AttrInfo a, OnPicked cb) {
        new AlertDialog.Builder(activity)
                .setTitle("Take over this attribute?")
                .setMessage("“" + a.label + "” is currently filled by another "
                        + "service. To let this app write to it, this app needs to "
                        + "take ownership. Exist allows only one writer per "
                        + "attribute. Proceed?")
                .setPositiveButton("Take ownership", (d, w) -> {
                    new Thread(() -> {
                        boolean ok = false;
                        try {
                            // ensureAttribute also acquires ownership.
                            new ExistApi(activity).ensureAttribute(a.name, a.label,
                                    a.groupName == null ? "custom" : a.groupName);
                            ok = true;
                        } catch (Exception ignored) {}
                        boolean f = ok;
                        activity.runOnUiThread(() -> {
                            if (f) { cb.picked(a.name); toast("Now using “" + a.label + "”."); }
                            else toast("Couldn't take ownership.");
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Form to create a brand-new custom attribute. */
    private void showCreateForm(String suggestedGroup, OnPicked cb) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16); box.setPadding(p, p, p, p);

        TextView l1 = new TextView(activity);
        l1.setText("Name for the new attribute (what you'll see in Exist):");
        l1.setTextSize(13);
        box.addView(l1);
        EditText labelEt = new EditText(activity);
        labelEt.setHint("e.g. Hospital Time");
        labelEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        box.addView(labelEt);

        TextView l2 = new TextView(activity);
        l2.setText("Category (group) it belongs to:");
        l2.setTextSize(13);
        l2.setPadding(0, dp(10), 0, 0);
        box.addView(l2);
        Spinner groupSpinner = new Spinner(activity);
        ArrayAdapter<String> ga = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, GROUP_LABELS);
        groupSpinner.setAdapter(ga);
        // Preselect suggested group if provided.
        if (suggestedGroup != null) {
            for (int i = 0; i < GROUP_NAMES.length; i++) {
                if (GROUP_NAMES[i].equals(suggestedGroup)) { groupSpinner.setSelection(i); break; }
            }
        }
        box.addView(groupSpinner);

        TextView l3 = new TextView(activity);
        l3.setText("How Exist treats the value:");
        l3.setTextSize(13);
        l3.setPadding(0, dp(10), 0, 0);
        box.addView(l3);
        final String[] vtLabels = {"Time / duration (shows as 8h 5m)",
                "Count / number", "Time of day (e.g. 6:48pm)",
                "Scale 1–9 (for sliders / ratings)"};
        final int[] vtValues = {3, 0, 4, 8};
        Spinner vtSpinner = new Spinner(activity);
        vtSpinner.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, vtLabels));
        vtSpinner.setSelection(0); // default to duration/time
        box.addView(vtSpinner);

        new AlertDialog.Builder(activity)
                .setTitle("Create new attribute")
                .setView(box)
                .setPositiveButton("Create", (d, w) -> {
                    String label = labelEt.getText().toString().trim();
                    if (label.isEmpty()) { toast("Please enter a name."); return; }
                    String group = GROUP_NAMES[groupSpinner.getSelectedItemPosition()];
                    int vt = vtValues[vtSpinner.getSelectedItemPosition()];
                    createNew(label, group, vt, cb);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNew(String label, String group, int valueType, OnPicked cb) {
        final ProgressDialog pd = new ProgressDialog(activity);
        pd.setMessage("Creating “" + label + "” in Exist…");
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            String name = null;
            try {
                name = new ExistApi(activity).createCustomAttribute(label, group, valueType);
            } catch (Exception ignored) {}
            final String finalName = name;
            activity.runOnUiThread(() -> {
                pd.dismiss();
                if (finalName != null) {
                    cb.picked(finalName);
                    toast("Created and selected “" + label + "”.");
                } else {
                    toast("Couldn't create it. The name may already exist — "
                            + "try picking it from the list instead.");
                }
            });
        }).start();
    }

    private void toast(String s) {
        Toast.makeText(activity, s, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density);
    }
}
