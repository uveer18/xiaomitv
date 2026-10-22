package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * App tiles with customizable dock pinning.
 * Pinned apps are stored in SharedPreferences and appear first in the home bar.
 *
 * The home bar always owns a full row of the grid: when fewer apps are pinned than the grid has
 * columns, an invisible spacer item fills the rest of the row so the rest of the apps never
 * creep into the home bar.
 */
public class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface TileListener {
        void onTileFocused(int position, View card);
        boolean onTileVerticalKey(int position, int keyCode);
        void onTileLongClick(int position, View card, Entry entry);
    }

    public static final class Entry {
        public final String label;
        public final ComponentName component;
        public final String pkg;
        public final Drawable banner;
        public final Drawable icon;
        public int ambient;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
        }
    }

    public static final float FOCUS_SCALE = 1.06f;
    /** Corner radius of an app tile, in dp. The glass highlight mirrors it exactly. */
    public static final float CARD_RADIUS_DP = 16f;

    private static final int TYPE_APP = 0;
    private static final int TYPE_SPACER = 1;

    private final Context ctx;
    private final TileListener listener;
    private final int columns;
    private final int maxDock;
    private final SharedPreferences prefs;
    private final List<String> defaultDock;
    private final List<String> dockList = new ArrayList<>();
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();
    /** How many pinned apps are actually installed, i.e. how wide the home bar is. */
    private int dockCount;

    public AppAdapter(Context ctx, TileListener listener, int columns, SharedPreferences prefs) {
        this.ctx = ctx;
        this.listener = listener;
        this.columns = columns;
        this.maxDock = Math.max(1, ctx.getResources().getInteger(R.integer.max_dock));
        this.prefs = prefs;
        this.defaultDock = Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps));
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
        loadDockList();
    }

    private void loadDockList() {
        dockList.clear();
        boolean truncated = false;
        String saved = prefs.getString("dock_packages_v2", null);
        if (saved != null && !saved.trim().isEmpty()) {
            String[] parts = saved.split(",");
            for (String p : parts) {
                String pkg = p.trim();
                if (!pkg.isEmpty() && !dockList.contains(pkg)) {
                    dockList.add(pkg);
                }
                if (dockList.size() >= maxDock) {
                    truncated = parts.length > maxDock;
                    break;
                }
            }
        }
        if (dockList.isEmpty()) {
            for (String pkg : defaultDock) {
                if (!dockList.contains(pkg)) dockList.add(pkg);
                if (dockList.size() >= maxDock) break;
            }
        }
        if (truncated) saveDockList();
    }

    private void saveDockList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dockList.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(dockList.get(i));
        }
        prefs.edit().putString("dock_packages_v2", sb.toString()).apply();
    }

    public boolean isDocked(String pkg) {
        return dockList.contains(pkg);
    }

    public int getDockCount() {
        return dockCount;
    }

    public int getMaxDock() {
        return maxDock;
    }

    public boolean addToDock(String pkg) {
        if (dockList.contains(pkg) || dockList.size() >= maxDock) return false;
        dockList.add(pkg);
        saveDockList();
        reload();
        return true;
    }

    public void removeFromDock(String pkg) {
        if (dockList.size() > 1 && dockList.remove(pkg)) {
            saveDockList();
            reload();
        }
    }

    public void moveDock(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && fromIndex < dockList.size() && toIndex >= 0 && toIndex < dockList.size()) {
            Collections.swap(dockList, fromIndex, toIndex);
            saveDockList();
            reload();
        }
    }

    public void resetDock() {
        prefs.edit().remove("dock_packages_v2").apply();
        loadDockList();
        reload();
    }

    /** Index into the app list for an adapter position, or -1 for the row spacer. */
    private int itemIndex(int position) {
        if (hasSpacer()) {
            if (position == dockCount) return -1;
            if (position > dockCount) return position - 1;
        }
        return position;
    }

    /** True when the home bar does not fill the row and needs an invisible filler. */
    private boolean hasSpacer() {
        return dockCount > 0 && dockCount < columns;
    }

    /** Span for the grid: the filler takes whatever is left of the home bar row. */
    public int getSpanSize(int position) {
        if (hasSpacer() && position == dockCount) return Math.max(1, columns - dockCount);
        return 1;
    }

    /** Adapter position of the first app that is not in the home bar. */
    public int getFirstAppPosition() {
        return dockCount + (hasSpacer() ? 1 : 0);
    }

    public Entry getEntry(int position) {
        int idx = itemIndex(position);
        return (idx >= 0 && idx < items.size()) ? items.get(idx) : null;
    }

    public void reload() {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        List<Entry> fresh = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, icon));
        }
        Collections.sort(fresh, (a, b) -> {
            int fa = dockList.indexOf(a.pkg), fb = dockList.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        items.clear();
        items.addAll(fresh);

        // Only installed apps take a slot in the home bar.
        dockCount = 0;
        for (Entry e : items) {
            if (dockList.contains(e.pkg)) dockCount++;
            else break;
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SPACER) {
            return new SpacerHolder(inflater.inflate(R.layout.item_spacer, parent, false));
        }
        return new Holder(inflater.inflate(R.layout.item_app, parent, false), listener);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SpacerHolder) return;
        Holder h = (Holder) holder;
        Entry e = items.get(itemIndex(position));
        int dock = dockCount;
        h.name.setText(e.label);
        h.name.setVisibility(position < dock ? View.INVISIBLE : View.VISIBLE);
        h.name.setAlpha(0f);
        h.card.setScaleX(1f);
        h.card.setScaleY(1f);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        }
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onTileLongClick(h.getBindingAdapterPosition(), h.card, e);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemViewType(int position) {
        return (hasSpacer() && position == dockCount) ? TYPE_SPACER : TYPE_APP;
    }

    public void launch(Entry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + (hasSpacer() ? 1 : 0);
    }

    public String labelAt(int position) {
        Entry e = getEntry(position);
        return e != null ? e.label : "";
    }

    public int ambientColorAt(int position) {
        Entry e = getEntry(position);
        if (e == null) return 0xFF333338;
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF333338;
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF333338;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    static final class SpacerHolder extends RecyclerView.ViewHolder {
        SpacerHolder(View v) {
            super(v);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        Holder(View v, TileListener listener) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            final float radius = density * CARD_RADIUS_DP;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int pos = getBindingAdapterPosition();
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    if (listener != null && pos != RecyclerView.NO_POSITION) {
                        listener.onTileLongClick(pos, view, null);
                        return true;
                    }
                }
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            // Smooth, non-springy, instantaneous focus transition
            final DecelerateInterpolator interp = new DecelerateInterpolator(1.4f);
            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                view.animate().scaleX(s).scaleY(s)
                        .setDuration(focused ? 140 : 110)
                        .setInterpolator(interp)
                        .start();
                name.animate().alpha(focused ? 1f : 0f)
                        .translationY(focused ? density * 2f : -density * 4f)
                        .setDuration(120).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
