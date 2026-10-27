package pl.arixelo.trishot.shizuku;

oneway interface ISwipeListener {
    /**
     * Three fingers moved together. [direction] is in the touch panel's raw axes:
     * 0 = +Y, 1 = -Y, 2 = +X, 3 = -X. The app maps it to the screen using the current rotation.
     */
    void onSwipe(int direction);
}
