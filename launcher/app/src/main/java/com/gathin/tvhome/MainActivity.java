package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Fast, lightweight TV Home Launcher for low-spec smart TVs.
 * Features:
 * - 6 popular Indian streaming apps in Home Bar (customizable via long-press/Menu)
 * - Curated popular content showcase shelf above the dock
 * - Snappy, non-springy fluid animations (critically damped, zero bounce)
 * - Modern dark OLED aesthetics with clean typography
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    static int COLUMNS = 6;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private DockDecoration dockDecoration;
    private FrameLayout root;
    private GlassView glass;
    private View dockStrip;
    private GradientDrawable stripGradient;
    private int stripColor;
    private TextView greeting;
    private TextView btnWifi;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    private int lastFocused = 0;
    private float density;
    private SharedPreferences prefs;

    // Shelf views
    private LinearLayout shelfContainer;
    private View shelfAccentDot;
    private TextView shelfAppTitle;
    private TextView shelfSubtitle;
    private final FrameLayout[] shelfCards = new FrameLayout[4];
    private final TextView[] shelfTags = new TextView[4];
    private final TextView[] shelfTitles = new TextView[4];
    private final TextView[] shelfSubs = new TextView[4];
    private String currentShelfPkg = "";
    private int lastFocusedDockIndex = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();
        prefs = getSharedPreferences("tvhome_v2", MODE_PRIVATE);

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        dockStrip = findViewById(R.id.dockStrip);
        stripGradient = new GradientDrawable();
        stripGradient.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        stripGradient.setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);
        dockStrip.setBackground(stripGradient);
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);

        // Content shelf setup
        initShelf();

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        dockDecoration = new DockDecoration(COLUMNS, density);
        grid.addItemDecoration(dockDecoration);
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.03f));
        adapter = new AppAdapter(this, this, COLUMNS, prefs);
        grid.setAdapter(adapter);

        // Push the dock row to the bottom of the screen; the grid hides below until you press Down.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int dockRow = (int) ((84 + 4 + 14) * density);
            int want = Math.max(0, h - dockRow - (int) (16 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Critically damped spring: 0.96f damping ratio + 500f stiffness = NO BOUNCE, instantaneous glide
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(500f).setDampingRatio(1.0f));
        }

        View btnInputs = findViewById(R.id.btnInputs);
        View btnSettings = findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnSettings.setOnClickListener(v -> showSettingsMenu());

        // Keep focus on the top bar at its ends and at the top
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnInputs) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Return to shelf cards or dock
                if (shelfContainer.getAlpha() > 0.5f) {
                    shelfCards[0].requestFocus();
                } else {
                    focusTile(lastFocused);
                }
                return true;
            }
            return false;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });
    }

    private void initShelf() {
        shelfContainer = findViewById(R.id.shelfContainer);
        shelfAccentDot = findViewById(R.id.shelfAccentDot);
        shelfAppTitle = findViewById(R.id.shelfAppTitle);
        shelfSubtitle = findViewById(R.id.shelfSubtitle);

        int[] cardIds = {R.id.shelfCard0, R.id.shelfCard1, R.id.shelfCard2, R.id.shelfCard3};
        int[] tagIds = {R.id.shelfTag0, R.id.shelfTag1, R.id.shelfTag2, R.id.shelfTag3};
        int[] titleIds = {R.id.shelfTitle0, R.id.shelfTitle1, R.id.shelfTitle2, R.id.shelfTitle3};
        int[] subIds = {R.id.shelfSub0, R.id.shelfSub1, R.id.shelfSub2, R.id.shelfSub3};

        DecelerateInterpolator cardInterp = new DecelerateInterpolator(1.4f);

        for (int i = 0; i < 4; i++) {
            final int cardIndex = i;
            shelfCards[i] = findViewById(cardIds[i]);
            shelfTags[i] = findViewById(tagIds[i]);
            shelfTitles[i] = findViewById(titleIds[i]);
            shelfSubs[i] = findViewById(subIds[i]);

            // Subtle non-springy card focus animation
            shelfCards[i].setOnFocusChangeListener((v, focused) -> {
                float scale = focused ? 1.05f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale)
                        .setDuration(focused ? 130 : 100)
                        .setInterpolator(cardInterp)
                        .start();

                GradientDrawable bg = (GradientDrawable) v.getBackground().mutate();
                if (focused) {
                    bg.setStroke((int) (2.5f * density), stripColor != 0 ? stripColor : 0xFFFFFFFF);
                } else {
                    bg.setStroke((int) (1f * density), 0x26FFFFFF);
                }
            });

            // Navigation from shelf cards
            shelfCards[i].setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusTile(Math.min(cardIndex, adapter.getDockCount() - 1));
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    findViewById(R.id.btnInputs).requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && cardIndex == 0) {
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && cardIndex == 3) {
                    return true;
                }
                return false;
            });

            // Click launches the associated streaming app
            shelfCards[i].setOnClickListener(v -> {
                AppAdapter.Entry e = (AppAdapter.Entry) v.getTag();
                if (e != null) {
                    adapter.launch(e);
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        glassShown = false;
        glass.setAlpha(0f);
        shelfContainer.setAlpha(0f);
        dockStrip.setAlpha(0f);
        currentShelfPkg = "";
        adapter.reload();
        dockDecoration.setDockSize(adapter.getDockCount());
        grid.scheduleLayoutAnimation();
        updateGreeting();
        updateWifiLabel();
        handler.postDelayed(minuteTick, MINUTE_MS);

        grid.post(() -> {
            View focused = getCurrentFocus();
            if (focused != null && grid.findContainingViewHolder(focused) != null) return;
            focusTile(lastFocused);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
    }

    @Override
    public void onBackPressed() {
        // Return focus to dock row if elsewhere
        if (lastFocused >= adapter.getDockCount()) {
            focusTile(0);
        }
    }

    // ---- TileListener implementation -------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        int dockCount = adapter.getDockCount();

        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 4f * density;
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(12f * density * AppAdapter.FOCUS_SCALE + pad);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(120).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }

        View item = (View) card.getParent();
        if (position < dockCount) {
            lastFocusedDockIndex = position;
            int decoratedTop = item.getTop() - (int) (4 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
            updateShelf(position);
        } else {
            hideShelf();
            Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, want, false);
        }
    }

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int dockCount = adapter.getDockCount();
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < dockCount) {
                // Focus moves into the shelf cards!
                int targetCard = Math.min(position, 3);
                shelfCards[targetCard].requestFocus();
                return true;
            }
            int target = position - COLUMNS;
            focusTile(Math.max(0, target));
            return true;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;
            int target = Math.min(position + COLUMNS, count - 1);
            focusTile(target);
            return true;
        }
    }

    @Override
    public void onTileLongClick(int position, View card, AppAdapter.Entry entry) {
        if (entry == null) entry = adapter.getEntry(position);
        if (entry == null) return;
        showCustomizePopup(position, card, entry);
    }

    // ---- Content Shelf ---------------------------------------------------------------------------

    private void updateShelf(int position) {
        AppAdapter.Entry entry = adapter.getEntry(position);
        if (entry == null) return;
        if (entry.pkg.equals(currentShelfPkg) && shelfContainer.getAlpha() > 0.8f) {
            return;
        }
        currentShelfPkg = entry.pkg;

        List<PopularContent.Item> items = PopularContent.getItems(entry.pkg, entry.label);
        int accent = items.get(0).accentColor;
        setStrip(accent);

        shelfContainer.animate().alpha(0.35f).setDuration(80).withEndAction(() -> {
            shelfAppTitle.setText(entry.label);
            shelfSubtitle.setText("• POPULAR ON " + entry.label.toUpperCase());

            GradientDrawable dotBg = (GradientDrawable) shelfAccentDot.getBackground().mutate();
            dotBg.setColor(accent);

            for (int i = 0; i < 4; i++) {
                if (i < items.size()) {
                    PopularContent.Item it = items.get(i);
                    shelfTags[i].setText(it.tag);
                    shelfTitles[i].setText(it.title);
                    shelfSubs[i].setText(it.subtitle);
                    shelfCards[i].setTag(entry);
                    shelfCards[i].setVisibility(View.VISIBLE);
                } else {
                    shelfCards[i].setVisibility(View.INVISIBLE);
                }
            }

            shelfContainer.animate().alpha(1f).setDuration(130).start();
            dockStrip.animate().alpha(0.5f).setDuration(200).start();
        }).start();
    }

    private void hideShelf() {
        currentShelfPkg = "";
        shelfContainer.animate().alpha(0f).setDuration(110).start();
        dockStrip.animate().alpha(0f).setDuration(150).start();
    }

    private void setStrip(int color) {
        stripColor = color;
        int c = color & 0x00FFFFFF;
        stripGradient.setColors(new int[]{0x2A000000 | c, 0x0A000000 | c, 0x00000000});
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(100).start();
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.findViewById(R.id.card).requestFocus();
            return;
        }
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- Customization & Settings ----------------------------------------------------------------

    private void showCustomizePopup(int position, View anchor, AppAdapter.Entry entry) {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = (int) (230 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);

        boolean isDocked = adapter.isDocked(entry.pkg);
        int dockCount = adapter.getDockCount();

        // Header with App name
        TextView header = new TextView(this);
        header.setText(entry.label + (isDocked ? " (In Home Bar)" : ""));
        header.setTextColor(0xFFFFFFFF);
        header.setTextSize(14f);
        header.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (6 * density));
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        rows.addView(header);
        addSeparator(rows);

        if (isDocked) {
            if (position > 0) {
                addMenuOption(rows, popup, "◀ Move Left in Home Bar", () -> {
                    adapter.moveDock(position, position - 1);
                    focusTile(position - 1);
                });
            }
            if (position < dockCount - 1) {
                addMenuOption(rows, popup, "▶ Move Right in Home Bar", () -> {
                    adapter.moveDock(position, position + 1);
                    focusTile(position + 1);
                });
            }
            addMenuOption(rows, popup, "✖ Remove from Home Bar", () -> {
                adapter.removeFromDock(entry.pkg);
                dockDecoration.setDockSize(adapter.getDockCount());
                Toast.makeText(MainActivity.this, "Removed " + entry.label + " from Home Bar", Toast.LENGTH_SHORT).show();
            });
        } else {
            addMenuOption(rows, popup, "★ Add to Home Bar", () -> {
                adapter.addToDock(entry.pkg);
                dockDecoration.setDockSize(adapter.getDockCount());
                Toast.makeText(MainActivity.this, "Pinned " + entry.label + " to Home Bar", Toast.LENGTH_SHORT).show();
                focusTile(adapter.getDockCount() - 1);
            });
        }

        addSeparator(rows);
        addMenuOption(rows, popup, "⚙ App Settings / Info", () -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + entry.pkg));
            open(i);
        });

        panel.setAlpha(0f);
        panel.setScaleY(0.95f);
        popup.showAtLocation(anchor, Gravity.CENTER, 0, 0);
        panel.animate().alpha(1f).scaleY(1f).setDuration(140).start();
        rows.getChildAt(2).requestFocus(); // focus first actionable item
    }

    private void showSettingsMenu() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = (int) (220 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);

        addMenuOption(rows, popup, "⚙ All TV Settings", () -> open(new Intent(Settings.ACTION_SETTINGS)));
        addMenuOption(rows, popup, "📶 Wi-Fi Settings", () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        addSeparator(rows);
        addMenuOption(rows, popup, "↺ Reset Home Bar (Top 6 Indian Apps)", () -> {
            adapter.resetDock();
            dockDecoration.setDockSize(adapter.getDockCount());
            Toast.makeText(MainActivity.this, "Home Bar reset to top Indian streaming apps", Toast.LENGTH_SHORT).show();
            focusTile(0);
        });

        panel.setAlpha(0f);
        panel.setScaleY(0.95f);
        panel.setTranslationY(-4 * density);
        popup.showAsDropDown(anchor, -(int) (50 * density), (int) (8 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(140).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addMenuOption(LinearLayout rows, PopupWindow popup, String title, Runnable action) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(getResources().getColorStateList(R.drawable.pill_text));
        tv.setTextSize(13f);
        tv.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
        tv.setBackgroundResource(R.drawable.input_row_bg);
        tv.setFocusable(true);
        tv.setClickable(true);
        tv.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(tv);
    }

    // ---- Top bar & Inputs -------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        btnWifi.setText(text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTv));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        panel.setAlpha(0f);
        panel.setScaleY(0.95f);
        panel.setTranslationY(-4 * density);
        popup.showAsDropDown(anchor, 0, (int) (8 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(140).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1AFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (4 * density), h = (int) (10 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
