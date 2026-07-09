package com.arijit.cacheservice.dto;

/**
 * Which native Redis structure backs a given key. Determines how the
 * request/response {@code value} field is interpreted:
 * <ul>
 *   <li>{@code STRING} - value is a single scalar payload</li>
 *   <li>{@code HASH} - value is a JSON object of field -&gt; string value</li>
 *   <li>{@code LIST} - value is a JSON array of string elements, in order</li>
 *   <li>{@code SET} - value is a JSON array of unique string members</li>
 *   <li>{@code SORTED_SET} - value is a JSON array of {@code {"member": ..., "score": ...}}</li>
 * </ul>
 */
public enum CacheDataType {
    STRING,
    HASH,
    LIST,
    SET,
    SORTED_SET
}
