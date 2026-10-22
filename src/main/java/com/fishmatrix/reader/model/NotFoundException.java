package com.fishmatrix.reader.model;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.common.error.ErrorCodes;
import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, message);
    }
}
