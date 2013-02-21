package org.ovirt.engine.core.common.businessentities;

import java.io.Serializable;

import javax.validation.constraints.Size;

import org.ovirt.engine.core.common.utils.ObjectUtils;
import org.ovirt.engine.core.compat.Guid;

public class Bookmark extends IVdcQueryable implements Serializable {
    private static final long serialVersionUID = 8177640907822845847L;

    private Guid id;

    @Size(max = BusinessEntitiesDefinitions.BOOKMARK_NAME_SIZE)
    private String name;

    @Size(min = 1, max = BusinessEntitiesDefinitions.BOOKMARK_VALUE_SIZE)
    private String value;


    public String getbookmark_name() {
        return name;
    }

    public String getbookmark_value() {
        return value;
    }

    public void setbookmark_value(String value) {
        this.value = value;
    }


    public void setbookmark_name(String name) {
        this.name = name;
    }


    public Bookmark() {
    }

    public Bookmark(String bookmark_name, String bookmark_value) {
        this.name = bookmark_name;
        this.value = bookmark_value;

    }

    public Bookmark(String bookmark_name, String bookmark_value, Guid bookmark_id) {
        this.name = bookmark_name;
        this.value = bookmark_value;
        this.id = bookmark_id;
    }

    @Override
    public int hashCode() {
        int hash = this.name.hashCode() * 7;
        hash += this.value.hashCode() * 7;
        hash += this.id.hashCode() * 7;

        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != this.getClass()) {
            return false;
        }
        Bookmark other = (Bookmark) obj;
        return (ObjectUtils.objectsEqual(id, other.id)
                && ObjectUtils.objectsEqual(name, other.name)
                && ObjectUtils.objectsEqual(value, other.value));
    }


    public Guid getbookmark_id() {
        return id;
    }

    public void setbookmark_id(Guid id) {
        this.id = id;
    }

    @Override
    public Object getQueryableId() {
        return getbookmark_id();
    }
}
