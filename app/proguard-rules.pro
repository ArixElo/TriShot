# Shizuku creates the user service by class name inside its own process.
-keep class pl.arixelo.trishot.shizuku.TouchWatcher { <init>(...); }
-keep class pl.arixelo.trishot.shizuku.ITouchWatcher** { *; }
-keep class pl.arixelo.trishot.shizuku.ISwipeListener** { *; }
