package se233.work_space.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ImageSession — Groups multiple ImageItems + tracks current index.
 * Acts as the shared "state" between DropZone and MainController.
 * Back/Next = shift currentIndex back and forth.
 */
public class ImageSession {
    private final List<ImageItem> items = new ArrayList<>();
    private int currentIndex = 0;

    public void add(ImageItem item) { items.add(item); }

    /** Returns a read-only list so callers can't mutate our internal items */
    public List<ImageItem> getItems() { return Collections.unmodifiableList(items); }

    public int size() { return items.size(); }
    public boolean isEmpty() { return items.isEmpty(); }

    public ImageItem getCurrent() { return items.isEmpty() ? null : items.get(currentIndex); }
    public int getCurrentIndex() { return currentIndex; }

    public boolean hasNext() { return currentIndex < items.size() - 1; }
    public boolean hasPrevious() { return currentIndex > 0; }
    public void next() { if (hasNext()) currentIndex++; }
    public void previous() { if (hasPrevious()) currentIndex--; }
}