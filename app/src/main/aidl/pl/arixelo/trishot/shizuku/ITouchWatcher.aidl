package pl.arixelo.trishot.shizuku;

import pl.arixelo.trishot.shizuku.ISwipeListener;

interface ITouchWatcher {
    // Shizuku calls this transaction code when the user service should exit.
    void destroy() = 16777114;

    /** Starts watching the touchscreen; returns a short description of the device or an error. */
    String start(ISwipeListener listener) = 1;
}
