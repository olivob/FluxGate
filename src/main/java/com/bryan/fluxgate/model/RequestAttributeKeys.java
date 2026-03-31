package com.bryan.fluxgate.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RequestAttributeKeys {

    public static final String PROVIDER = "fluxgate.provider";
    public static final String MODEL = "fluxgate.model";
    public static final String ERROR_CODE = "fluxgate.errorCode";
}
