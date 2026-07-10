package com.arijit.featureflagservice.entity;

/**
 * The wire/storage shape of a flag's value. A flag's type is immutable
 * after creation (see FlagValidationService) - changing what kind of
 * value a key holds is modeled as delete + recreate, not an update, so
 * every historical version of a flag can be deserialized the same way.
 */
public enum FlagType {
    BOOLEAN,
    INTEGER,
    LONG,
    DOUBLE,
    STRING,
    JSON
}
