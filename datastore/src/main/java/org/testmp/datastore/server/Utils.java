/*
 * TestMP (Test Management Platform)
 * Copyright 2013 and beyond, Zhaowei Ding.
 *
 * TestMP is free software; you can redistribute it and/or modify it
 * under the terms of the MIT License (MIT).
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */

package org.testmp.datastore.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.testmp.datastore.model.Data;
import org.testmp.datastore.model.DataInfo;
import org.testmp.datastore.model.Property;
import org.testmp.datastore.model.Tag;

import com.sleepycat.collections.StoredMap;
import com.sleepycat.collections.StoredSortedMap;

public class Utils {

    /**
     * Convert the data of specified id to data info
     * 
     * @param id
     * @return
     */
    public static DataInfo convertDataToDataInfo(int id) {
        DataStoreView view = DataStoreManager.getInstance().getDataStoreView();
        StoredMap dataMap = view.getDataMap();
        StoredMap propertyMap = view.getPropertyMap();
        Data data = (Data) dataMap.get(id);
        List<String> relatedTags = new ArrayList<String>(data.getRelatedTags());
        Map<String, Object> relatedProperties = new HashMap<String, Object>();
        for (int propertyId : data.getRelatedPropertyIds()) {
            Property property = (Property) propertyMap.get(propertyId);
            relatedProperties.put(property.getKey(), property.getValue());
        }
        return new DataInfo(id, relatedTags, relatedProperties);
    }

    /**
     * Convert JSON representation to a list of tagged data
     * 
     * @param s
     * @return
     */
    public static List<DataInfo> convertJsonToDataInfoList(String s) {
        List<DataInfo> dataInfoList = new LinkedList<DataInfo>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode dataInfoListNode = mapper.readTree(s);
            for (int i = 0; i < dataInfoListNode.size(); i++) {
                JsonNode dataInfoNode = dataInfoListNode.get(i);
                dataInfoList.add(mapper.readValue(dataInfoNode.toString(), DataInfo.class));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return dataInfoList;
    }

    /**
     * Covert a list of tagged data to JSON representation
     * 
     * @param dataInfoList
     * @return
     */
    public static String converDataInfoListToJson(List<DataInfo> dataInfoList) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean isFirst = true;
        for (DataInfo dataInfo : dataInfoList) {
            if (!isFirst) {
                sb.append(",");
            } else {
                isFirst = false;
            }
            sb.append(dataInfo.toString());
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Convert tag list to its JSON representation
     * 
     * @param tagList
     * @return
     */
    public static String convertTagListToJson(List<Tag> tagList) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean isFirst = true;
        for (Tag tag : tagList) {
            if (!isFirst) {
                sb.append(",");
            } else {
                isFirst = false;
            }
            sb.append(tag.toString());
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Convert JSON representation to a list of property IDs
     * 
     * @param s
     * @return
     */
    @SuppressWarnings("unchecked")
    public static List<Integer> convertJsonToPropertyIdList(String s) {
        List<Integer> propertyIds = new ArrayList<Integer>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            Map<String, Object> m = mapper.readValue(s, Map.class);
            for (Map.Entry<String, Object> e : m.entrySet()) {
                propertyIds.add(calculatePropertyId(e.getKey(), e.getValue()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return propertyIds;
    }

    /**
     * Convert property value list to JSON representation
     * 
     * @param propertyValueList
     * @return
     */
    public static String convertPropertyValueListToJson(List<Object> propertyValueList) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(propertyValueList);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("rawtypes")
    public static Integer calculateDataId(Map<String, Object> data) {
        DataStoreView view = DataStoreManager.getInstance().getDataStoreView();
        StoredSortedMap dataMap = (StoredSortedMap) view.getDataMap();
        if (dataMap.isEmpty()) {
            return 1;
        }
        StoredMap dataMapIndexedByProps = view.getDataMapIndexedByProps();
        List<Integer> relatedPropertyIds = new LinkedList<Integer>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            relatedPropertyIds.add(calculatePropertyId(e.getKey(), e.getValue()));
        }
        Collections.sort(relatedPropertyIds);
        StringBuilder sb = new StringBuilder();
        for (int id : relatedPropertyIds) {
            sb.append(id);
        }
        String index = sb.toString();
        Collection c = dataMapIndexedByProps.duplicates(index);
        if (c.isEmpty()) {
            return (Integer) dataMap.lastKey() + 1;
        } else {
            return ((Data) c.iterator().next()).getId();
        }
    }

    @SuppressWarnings("rawtypes")
    public static Integer calculatePropertyId(String key, Object value) {
        DataStoreView view = DataStoreManager.getInstance().getDataStoreView();
        StoredSortedMap propertyMap = (StoredSortedMap) view.getPropertyMap();
        if (propertyMap.isEmpty()) {
            return 1;
        }
        StoredMap propMapIndexedByKeyValue = view.getPropMapIndexedByKeyValue();
        ObjectMapper mapper = new ObjectMapper();
        try {
            String index = key + mapper.writeValueAsString(value);
            Collection c = propMapIndexedByKeyValue.duplicates(index);
            if (c.isEmpty()) {
                return (Integer) propertyMap.lastKey() + 1;
            } else {
                return ((Property) c.iterator().next()).getId();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String compress(String s) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        gzip.write(s.getBytes("UTF-8"));
        gzip.close();
        return Base64.encode(out.toByteArray());
    }

    public static String decompress(String s) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(Base64.decode(s));
        GZIPInputStream gzip = new GZIPInputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int i = 0;
        while ((i = gzip.read(buf)) >= 0) {
            out.write(buf, 0, i);
        }
        return out.toString("UTF-8");
    }
}
