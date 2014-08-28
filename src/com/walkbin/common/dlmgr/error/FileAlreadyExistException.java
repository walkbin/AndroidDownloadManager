
package com.walkbin.common.dlmgr.error;

/**
 * if target file is existing,downloadTask will throw this exception
 * */
public class FileAlreadyExistException extends DownloadException {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public FileAlreadyExistException(String message) {

        super(message);
    }

}
