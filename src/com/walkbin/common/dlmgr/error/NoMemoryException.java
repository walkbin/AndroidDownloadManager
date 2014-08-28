
package com.walkbin.common.dlmgr.error;

/**
 * when the left storage in disc less then file length,throw this exception
 * */
public class NoMemoryException extends DownloadException {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public NoMemoryException(String message) {

        super(message);
        // TODO Auto-generated constructor stub
    }

}
