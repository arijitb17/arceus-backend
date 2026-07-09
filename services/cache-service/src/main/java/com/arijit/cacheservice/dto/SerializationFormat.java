package com.arijit.cacheservice.dto;

/**
 * How the transported {@code value} string is converted to/from the bytes
 * actually stored in Redis.
 * <ul>
 *   <li>{@code JSON} - value is JSON text, stored as UTF-8 bytes verbatim</li>
 *   <li>{@code STRING} - value is plain text, stored as UTF-8 bytes verbatim</li>
 *   <li>{@code BINARY} - value is Base64 text; decoded before storage, re-encoded on read</li>
 * </ul>
 */
public enum SerializationFormat {
    JSON,
    STRING,
    BINARY
}
