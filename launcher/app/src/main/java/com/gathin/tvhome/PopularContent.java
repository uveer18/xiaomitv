package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * Curated popular/trending recommendations for top Indian streaming apps.
 * Displayed in the shelf above the dock instead of an abstract color gradient.
 */
public final class PopularContent {

    public static final class Item {
        public final String title;
        public final String tag;       // e.g. "TRENDING #1", "TOP SERIES", "LIVE", "4K"
        public final String subtitle;  // e.g. "Season 3 • Crime Drama"
        public final int accentColor;
        public final String appPkg;

        public Item(String title, String tag, String subtitle, int accentColor, String appPkg) {
            this.title = title;
            this.tag = tag;
            this.subtitle = subtitle;
            this.accentColor = accentColor;
            this.appPkg = appPkg;
        }
    }

    public static List<Item> getItems(String pkg, String appLabel) {
        List<Item> list = new ArrayList<>();
        if ("com.amazon.amazonvideo.livingroom".equals(pkg)) {
            list.add(new Item("Mirzapur", "TRENDING #1", "Season 3 • Crime Drama", 0xFF00A8E1, pkg));
            list.add(new Item("Panchayat", "TOP RATED", "Season 3 • Comedy Drama", 0xFF00A8E1, pkg));
            list.add(new Item("The Boys", "NEW EPISODES", "Action Superhero • 4K", 0xFF00A8E1, pkg));
            list.add(new Item("Farzi", "BLOCKBUSTER", "Thriller • Shahid Kapoor", 0xFF00A8E1, pkg));
        } else if ("in.startv.hotstar".equals(pkg)) {
            list.add(new Item("Cricket Highlights", "LIVE & SPORTS", "ICC Tournament / IPL", 0xFF0C57D9, pkg));
            list.add(new Item("Aarya", "HOTSTAR SPECIALS", "Crime Drama • Sushmita Sen", 0xFF0C57D9, pkg));
            list.add(new Item("Loki", "MARVEL STUDIOS", "Sci-Fi Fantasy Series", 0xFF0C57D9, pkg));
            list.add(new Item("Special Ops", "TOP THRILLER", "Kay Kay Menon • Espionage", 0xFF0C57D9, pkg));
        } else if ("com.google.android.youtube.tv".equals(pkg)) {
            list.add(new Item("Trending India", "TOP VIDEOS", "Viral Hits & Top Creators", 0xFFFF0000, pkg));
            list.add(new Item("Live News 24/7", "LIVE NOW", "National & Global News", 0xFFFF0000, pkg));
            list.add(new Item("Bollywood Hits", "TOP CHARTS", "Latest Music & Melodies", 0xFFFF0000, pkg));
            list.add(new Item("Tech & Gadgets", "TRENDING TECH", "Smartphones, Reviews & AI", 0xFFFF0000, pkg));
        } else if ("com.netflix.ninja".equals(pkg)) {
            list.add(new Item("Stranger Things", "GLOBAL TOP 10", "Sci-Fi Supernatural • 4K", 0xFFE50914, pkg));
            list.add(new Item("Squid Game", "POPULAR NOW", "Survival Thriller", 0xFFE50914, pkg));
            list.add(new Item("Money Heist", "FAN FAVORITE", "Heist Crime Drama", 0xFFE50914, pkg));
            list.add(new Item("Kota Factory", "INDIAN TOP 10", "Season 3 • Inspiring Drama", 0xFFE50914, pkg));
        } else if ("com.sonyliv".equals(pkg)) {
            list.add(new Item("Scam 1992", "ALL TIME BEST", "The Harshad Mehta Story", 0xFFF26722, pkg));
            list.add(new Item("Gullak", "FAMILY COMEDY", "Season 4 • Mishra Family", 0xFFF26722, pkg));
            list.add(new Item("Rocket Boys", "AWARD WINNING", "Bhabha & Sarabhai Story", 0xFFF26722, pkg));
            list.add(new Item("Shark Tank India", "TRENDING REALITY", "Business Pitches & Deals", 0xFFF26722, pkg));
        } else if ("com.graymatrix.did".equals(pkg)) {
            list.add(new Item("Taj", "HISTORICAL DRAMA", "Divided by Blood • Mughal Era", 0xFF9E1F63, pkg));
            list.add(new Item("Sirf Ek Bandaa", "POPULAR MOVIE", "Manoj Bajpayee Courtroom", 0xFF9E1F63, pkg));
            list.add(new Item("Sunflower", "CRIME COMEDY", "Sunil Grover Murder Mystery", 0xFF9E1F63, pkg));
            list.add(new Item("Sa Re Ga Ma Pa", "TOP REALITY", "Music Singing Show", 0xFF9E1F63, pkg));
        } else {
            list.add(new Item("Open " + appLabel, "LAUNCH", "Launch Application", 0xFF4A5568, pkg));
            list.add(new Item("App Info", "SETTINGS", "Manage permissions & cache", 0xFF4A5568, pkg));
            list.add(new Item("Quick Actions", "SHORTCUT", "Recently used features", 0xFF4A5568, pkg));
            list.add(new Item("Customize Home", "CUSTOMIZE", "Pin or reorder home bar", 0xFF4A5568, pkg));
        }
        return list;
    }
}
