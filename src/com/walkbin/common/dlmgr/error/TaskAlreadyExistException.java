
package com.walkbin.common.dlmgr.error;

/**
 * reject adding a existing download task
 * */
public class TaskAlreadyExistException extends DownloadException {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public TaskAlreadyExistException(String message) {

        super(message);
        // TODO Auto-generated constructor stub
    }

}
