package com.fishmatrix.reader.error;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.common.error.ErrorCodes;
import org.springframework.http.HttpStatus;

/**
 * 资源不存在。私有内容对无权用户也抛本异常（而不是 403），避免泄露"这条记录存在"。
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, message);
    }
}
