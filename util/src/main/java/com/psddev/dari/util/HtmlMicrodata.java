package com.psddev.dari.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import com.google.common.base.CharMatcher;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;

/**
 * @see <a href="http://en.wikipedia.org/wiki/Microdata_(HTML)">Wikipedia</a>
 * @see <a href="http://www.whatwg.org/specs/web-apps/current-work/multipage/microdata.html">WHATWG HTML specification</a>
 */
public class HtmlMicrodata {

    private Set<String> types;
    private String id;
    private Map<String, Object> properties;

    public HtmlMicrodata() {
    }

    public HtmlMicrodata(URL url, Element item) {
        URI uri;

        try {
            uri = url.toURI();
        } catch (URISyntaxException error) {
            uri = null;
        }

        Splitter whitespaceSplitter = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings().trimResults();
        Map<String, Object> properties = getProperties();
        String types = item.attr("itemtype");

        if (!ObjectUtils.isBlank(types)) {
            for (String type : whitespaceSplitter.split(types)) {
                getTypes().add(type);
            }
        }

        setId(item.attr("itemid"));

        PROPERTY: for (Element prop : item.select("[itemprop]")) {
            if (item.equals(prop)) {
                continue;

            } else {
                for (Element p : prop.parents()) {
                    if (p.hasAttr("itemscope")) {
                        if (!item.equals(p)) {
                            continue PROPERTY;
                        } else {
                            break;
                        }
                    }
                }
            }

            String names = prop.attr("itemprop");
            String tagName = " " + prop.tagName() + " ";
            Object value;

            if (prop.hasAttr("itemscope")) {
                value = new HtmlMicrodata(url, prop);

            } else if (" meta ".contains(tagName)) {
                value = prop.attr("content");

            } else if (" audio embed iframe img source track video ".contains(tagName)) {
                try {
                    value = uri.resolve(prop.attr("src")).toString();
                } catch (IllegalArgumentException error) {
                    value = null;
                } catch (NullPointerException error) {
                    value = null;
                }

            } else if (" a area link ".contains(tagName)) {
                try {
                    value = uri.resolve(prop.attr("href")).toString();
                } catch (IllegalArgumentException error) {
                    value = null;
                } catch (NullPointerException error) {
                    value = null;
                }

            } else if (" object ".contains(tagName)) {
                value = prop.attr("data");

            } else if (" data meter ".contains(tagName)) {
                value = prop.attr("value");

            } else if (" time ".contains(tagName)) {
                value = ObjectUtils.to(Date.class, ObjectUtils.coalesce(prop.attr("datetime"), prop.text()));

            } else {
                value = prop.text();
            }

            if (!ObjectUtils.isBlank(names)) {
                for (String name : whitespaceSplitter.split(names)) {
                    if (name.endsWith("s")) {
                        @SuppressWarnings("unchecked")
                        List<Object> values = (List<Object>) properties.get(name);

                        if (values == null) {
                            values = new ArrayList<Object>();
                            properties.put(name, values);
                        }

                        values.add(value);

                    } else {
                        properties.put(name, value);
                    }
                }
            }
        }
    }

    /**
     * @return Never {@code null}. Mutable.
     */
    public Set<String> getTypes() {
        if (types == null) {
            types = new LinkedHashSet<String>();
        }
        return types;
    }

    /**
     * @param types May be {@code null} to clear the set.
     */
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return Never {@code null}. Mutable.
     */
    public Map<String, Object> getProperties() {
        if (properties == null) {
            properties = new CompactMap<String, Object>();
        }
        return properties;
    }

    /**
     * @param properties May be {@code null} to clear the map.
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).
                add("types", getTypes()).
                add("id", getId()).
                add("properties", getProperties()).
                toString();
    }

    /**
     * {@link HtmlMicrodata} utility methods.
     */
    public final static class Static {

        /**
         * Returns all microdata items in the given {@code html}, resolving
         * all relative URLs against the given {@code url}.
         *
         * @param url If {@code null}, relative URLs won't be resolved.
         * @param html If {@code null}, returns an empty list.
         * @return Never {@code null}.
         */
        public static List<HtmlMicrodata> parseString(URL url, String html) {
            List<HtmlMicrodata> datas = new ArrayList<HtmlMicrodata>();

            if (!ObjectUtils.isBlank(html)) {
                for (Element item : Jsoup.parse(html).select("[itemscope]")) {
                    if (!item.hasAttr("itemprop")) {
                        datas.add(new HtmlMicrodata(url, item));
                    }
                }
            }

            return datas;
        }

        /**
         * Returns all microdata items in the HTML output from the given
         * {@code url}.
         *
         * @param url Can't be {@code null}.
         */
        public static List<HtmlMicrodata> parseUrl(URL url) throws IOException {
            return parseString(url, IoUtils.toString(url));
        }
    }
}
