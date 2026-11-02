package eu.amadeos.apps.bookeditor.domain.book;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A book as the editor holds it: the details, the look, the cover, an optional dedication, the chapters and
 * the pictures. Cover page, title page and table of contents are made from these when the book is saved.
 */
public final class Book {
    public static final String DEDICATION_ID = "dedication";

    private BookInfo info;
    private Look look;
    private String cover = "";
    private Chapter dedication;
    private final List<Chapter> chapters = new ArrayList<>();
    private final Map<String, Resource> resources = new LinkedHashMap<>();

    public Book(BookInfo info, Look look) {
        this.info = Objects.requireNonNull(info, "info");
        this.look = Objects.requireNonNull(look, "look");
    }

    /** A new book with one empty chapter. */
    public static Book create(BookInfo info, Look look, String firstChapterTitle) {
        Book book = new Book(info, look);
        book.chapters.add(Chapter.create(firstChapterTitle));
        return book;
    }

    public BookInfo info() {
        return info;
    }

    public void setInfo(BookInfo info) {
        this.info = Objects.requireNonNull(info, "info");
    }

    public Look look() {
        return look;
    }

    public void setLook(Look look) {
        this.look = Objects.requireNonNull(look, "look");
    }

    /** The resource name of the cover picture, empty without cover. */
    public Optional<String> cover() {
        return cover.isEmpty() ? Optional.empty() : Optional.of(cover);
    }

    public void setCover(String resourceName) {
        if (resourceName != null && !resourceName.isEmpty() && !resources.containsKey(resourceName)) {
            throw new IllegalArgumentException("no such picture: " + resourceName);
        }
        String old = cover;
        cover = resourceName == null ? "" : resourceName;
        if (!old.isEmpty() && !old.equals(cover) && !used(old)) resources.remove(old);
    }

    public Optional<Chapter> dedication() {
        return Optional.ofNullable(dedication);
    }

    public void setDedication(Chapter chapter) {
        if (chapter != null && !chapter.id().equals(DEDICATION_ID)) {
            chapter = new Chapter(DEDICATION_ID, false, chapter.blocks());
        }
        dedication = chapter;
    }

    public List<Chapter> chapters() {
        return List.copyOf(chapters);
    }

    public void setChapters(List<Chapter> list) {
        chapters.clear();
        chapters.addAll(list);
    }

    public Optional<Chapter> chapter(String id) {
        if (DEDICATION_ID.equals(id)) return dedication();
        return chapters.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public void replace(Chapter chapter) {
        if (chapter.id().equals(DEDICATION_ID)) {
            dedication = chapter;
            return;
        }
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).id().equals(chapter.id())) {
                chapters.set(i, chapter);
                return;
            }
        }
        throw new IllegalArgumentException("no chapter " + chapter.id());
    }

    /** The number shown before the title ("Chapter 3"), 0 for a chapter that is not counted. */
    public int numberOf(String chapterId) {
        int n = 0;
        for (Chapter c : chapters) {
            if (c.numbered()) n++;
            if (c.id().equals(chapterId)) return c.numbered() ? n : 0;
        }
        return 0;
    }

    public Map<String, Resource> resources() {
        return Map.copyOf(resources);
    }

    public Optional<Resource> resource(String name) {
        return Optional.ofNullable(resources.get(name));
    }

    /** Adds a picture under a name not used yet ("lighthouse-2.jpg" if "lighthouse.jpg" exists); returns the name. */
    public String addResource(String wantedName, String mediaType, byte[] data) {
        String name = Resource.safeName(wantedName);
        for (Resource r : resources.values()) {
            if (r.mediaType().equals(mediaType) && java.util.Arrays.equals(r.data(), data)) return r.name();
        }
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 2; resources.containsKey(name); i++) name = base + "-" + i + ext;
        resources.put(name, new Resource(name, mediaType, data));
        return name;
    }

    public void putResource(Resource resource) {
        resources.put(resource.name(), resource);
    }

    /** Drops pictures that neither the cover nor any chapter uses any more. */
    public void dropUnused() {
        resources.keySet().removeIf(name -> !used(name));
    }

    private boolean used(String name) {
        if (name.equals(cover)) return true;
        List<Chapter> all = new ArrayList<>(chapters);
        if (dedication != null) all.add(dedication);
        for (Chapter c : all) {
            for (Block b : c.blocks()) {
                if (b instanceof Block.Picture p && p.resource().equals(name)) return true;
                if (b instanceof Block.Kept k && k.pictures().contains(name)) return true;
            }
        }
        return false;
    }

    public int words() {
        int n = dedication == null ? 0 : dedication.words();
        for (Chapter c : chapters) n += c.words();
        return n;
    }

    /** All pictures in reading order, with the chapter they are in. */
    public List<Map.Entry<Chapter, Block.Picture>> pictures() {
        List<Map.Entry<Chapter, Block.Picture>> list = new ArrayList<>();
        List<Chapter> all = new ArrayList<>();
        if (dedication != null) all.add(dedication);
        all.addAll(chapters);
        for (Chapter c : all) {
            for (Block b : c.blocks()) if (b instanceof Block.Picture p) list.add(Map.entry(c, p));
        }
        return list;
    }
}
