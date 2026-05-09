package com.mediamarshal.model.exception;

import com.mediamarshal.model.entity.MediaTask;

public class MediaTaskFailureException extends RuntimeException {

    private final MediaTask.TaskErrorCode errorCode;

    public MediaTaskFailureException(MediaTask.TaskErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MediaTask.TaskErrorCode getErrorCode() {
        return errorCode;
    }
}
