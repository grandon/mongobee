package com.github.mongobee.core.exception;

/**
 * Error while connection to MongoDB
 *
 * @author lstolowski
 * @since 27/07/2014
 */
public class MongobeeLockAquireException extends MongobeeException {
  public MongobeeLockAquireException(String message) {
    super(message);
  }

  public MongobeeLockAquireException(String message, Exception baseException) {
    super(message, baseException);
  }
}
