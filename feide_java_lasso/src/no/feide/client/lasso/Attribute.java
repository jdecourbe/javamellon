package no.feide.client.lasso;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class is used for storing attributes which are received in a SAML2 Response.
 */
class Attribute implements Iterable<String> {
    /**
     * The name of this attribute.
     */
    private final String name;

    /**
     * The values which are stored in this attribute.
     */
    private final List<String> values;

    /**
     * Creates an Attribute object with the specified name.
     *
     * @param name the name of this attribute.
     */
    Attribute(String name) {
        this.name = name;
        this.values = new ArrayList<String>();
    }

    /**
     * Adds a value to this attribute.
     *
     * @param value the value which should be added to this attribute.
     */
    void addValue(String value) {
        this.values.add(value);
    }

    /**
     * Gets the name of this attribute.
     * 
     * @return the name of this attribute.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Gets the number of values this attribute contains.
     * 
     * @return the number of values this attribute contains.
     */
    public int getValueCount() {
        return this.values.size();
    }

    /**
     * Gets a value with the specified index.
     *
     * @param index index of the value to get.
     * @return the value stored at the specified index.
     */
    public String getValue(int index) {
        return this.values.get(index);
    }

    /**
     * Returns an iterator for iterating over the values of this Attribute object.
     */
    @Override
    public Iterator<String> iterator() {
        return this.values.iterator();
    }

    /**
     * Creates a string from this Attribute object. The string will be on the format:
     * <name>: "<value1>" "<value2>" ...
     */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(this.name).append(':');
        for(String value : this.values) {
            b.append(" \"").append(value).append('"');
        }

        return b.toString();
    }
}