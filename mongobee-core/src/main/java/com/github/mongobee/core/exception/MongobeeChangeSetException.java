package com.github.mongobee.core.exception;

/**
 * @author lstolowski
 * @since 27/07/2014
 */
public class MongobeeChangeSetException extends MongobeeException {
  public MongobeeChangeSetException(String message) {
    super(message);
  }

  public MongobeeChangeSetException(String message, Exception baseException) {
    super(message, baseException);
  }
}
