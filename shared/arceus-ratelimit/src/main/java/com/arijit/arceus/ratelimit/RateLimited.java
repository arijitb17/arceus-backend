package com.arijit.arceus.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(RateLimited.List.class)
public @interface RateLimited {

    String bucket();

    String key() default "";

    int limit();

    long windowSeconds();

    boolean perIp() default false;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        RateLimited[] value();
    }
}
