package com.arijit.featureflagservice.service;

import com.arijit.featureflagservice.entity.FeatureFlag;
import com.arijit.featureflagservice.entity.FlagType;
import com.arijit.featureflagservice.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The single place that knows how to move a value between "the
 * caller-facing JSON shape" (a plain {@code Object} - Boolean, Long,
 * Double, String, or an arbitrary JSON tree for the JSON type) and "which
 * typed column on {@link FeatureFlag} holds it". Every other class
 * (FlagService, FeatureFlagGrpcServiceImpl, FlagResponse) goes through
 * this rather than touching valueBoolean/valueLong/valueDouble/
 * valueString/valueJson directly, so adding a new FlagType only ever
 * requires a change here.
 */
@Component
@RequiredArgsConstructor
public class FlagValueCodec {

    private final ObjectMapper objectMapper;

    /** Validates {@code rawValue} matches {@code type} and writes it onto the correct column on {@code flag}. */
    public void write(FeatureFlag flag, FlagType type, Object rawValue) {
        flag.setValueBoolean(null);
        flag.setValueLong(null);
        flag.setValueDouble(null);
        flag.setValueString(null);
        flag.setValueJson(null);

        switch (type) {
            case BOOLEAN -> flag.setValueBoolean(asBoolean(rawValue));
            case INTEGER -> flag.setValueLong(asIntegerRangeLong(rawValue));
            case LONG -> flag.setValueLong(asLong(rawValue));
            case DOUBLE -> flag.setValueDouble(asDouble(rawValue));
            case STRING -> flag.setValueString(asString(rawValue));
            case JSON -> flag.setValueJson(asJson(rawValue));
        }
    }

    /** Reads the effective value back out as a plain JSON-friendly Object, for FlagResponse/EvaluationResult/gRPC. */
    public Object read(FeatureFlag flag) {
        return switch (flag.getType()) {
            case BOOLEAN -> flag.getValueBoolean();
            case INTEGER -> flag.getValueLong() == null ? null : flag.getValueLong().intValue();
            case LONG -> flag.getValueLong();
            case DOUBLE -> flag.getValueDouble();
            case STRING -> flag.getValueString();
            case JSON -> readJson(flag.getValueJson());
        };
    }

    /** JSON-encodes the effective value - what goes over the wire in the gRPC {@code value} field. */
    public String toJsonString(FeatureFlag flag) {
        if (flag.getType() == FlagType.JSON) {
            return flag.getValueJson();
        }
        return toJsonString(read(flag));
    }

    public String toJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ApiException("Unable to serialize value: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public Object fromJsonString(String json, FlagType type) {
        if (json == null) {
            return null;
        }
        try {
            return switch (type) {
                case JSON -> readJson(json);
                default -> objectMapper.readValue(json, Object.class);
            };
        } catch (Exception e) {
            throw new ApiException("Invalid JSON value: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private Object readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            throw new ApiException("Stored JSON value is corrupt: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Boolean asBoolean(Object raw) {
        if (raw == null) {
            throw badType(FlagType.BOOLEAN, raw);
        }
        if (raw instanceof Boolean b) return b;
        if (raw instanceof String s) return Boolean.parseBoolean(requireBooleanString(s));
        throw badType(FlagType.BOOLEAN, raw);
    }

    private String requireBooleanString(String s) {
        if (!s.equalsIgnoreCase("true") && !s.equalsIgnoreCase("false")) {
            throw badType(FlagType.BOOLEAN, s);
        }
        return s;
    }

    private Long asIntegerRangeLong(Object raw) {
        long value = asLong(raw);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new ApiException("Value " + value + " does not fit in an INTEGER flag - use LONG instead", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private Long asLong(Object raw) {
        if (raw instanceof Number n) return n.longValue();
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                throw badType(FlagType.LONG, raw);
            }
        }
        throw badType(FlagType.LONG, raw);
    }

    private Double asDouble(Object raw) {
        if (raw instanceof Number n) return n.doubleValue();
        if (raw instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                throw badType(FlagType.DOUBLE, raw);
            }
        }
        throw badType(FlagType.DOUBLE, raw);
    }

    private String asString(Object raw) {
        if (raw == null) {
            throw badType(FlagType.STRING, null);
        }
        return raw.toString();
    }

    private String asJson(Object raw) {
        if (raw == null) {
            throw new ApiException("A JSON-type flag requires a non-null value", HttpStatus.BAD_REQUEST);
        }
        return toJsonString(raw);
    }

    private ApiException badType(FlagType expected, Object raw) {
        return new ApiException("Value '" + raw + "' is not a valid " + expected + " value", HttpStatus.BAD_REQUEST);
    }
}
