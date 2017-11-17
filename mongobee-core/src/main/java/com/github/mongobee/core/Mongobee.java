package com.github.mongobee.core;

import com.github.mongobee.core.changeset.ChangeEntry;
import com.github.mongobee.core.dao.ChangeEntryDao;
import com.github.mongobee.core.exception.*;
import com.github.mongobee.core.utils.ChangeService;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static com.github.mongobee.core.utils.StringUtils.hasText;
import static com.mongodb.ServerAddress.defaultHost;
import static com.mongodb.ServerAddress.defaultPort;

/**
 * Mongobee runner
 *
 * @author lstolowski
 * @since 26 /07/2014
 */
public class Mongobee {
  private static final Logger logger = LoggerFactory.getLogger(Mongobee.class);

  private static final String DEFAULT_CHANGELOG_COLLECTION_NAME = "dbchangelog";
  private static final String DEFAULT_LOCK_COLLECTION_NAME = "mongobeelock";

  private ChangeEntryDao dao;

  private boolean enabled = true;
  protected String changeLogsScanPackage;
  private MongoClientURI mongoClientURI;
  private MongoClient mongoClient;
  protected String dbName;
  private boolean executeRollback = true;
  private boolean failOnLockAcquire = false;
  private Long lockAcquireTimeout = null;

  /**
   * <p>Simple constructor with default configuration of host (localhost) and port (27017). Although
   * <b>the database name need to be provided</b> using {@link Mongobee#setDbName(String)} setter.</p>
   * <p>It is recommended to use constructors with MongoURI</p>
   */
  public Mongobee() {
    this(new MongoClientURI("mongodb://" + defaultHost() + ":" + defaultPort() + "/"));
  }

  /**
   * <p>Constructor takes db.mongodb.MongoClientURI object as a parameter.
   * </p><p>For more details about MongoClientURI please see com.mongodb.MongoClientURI docs
   * </p>
   *
   * @param mongoClientURI uri to your db
   * @see MongoClientURI
   */
  public Mongobee(MongoClientURI mongoClientURI) {
    this.mongoClientURI = mongoClientURI;
    this.setDbName(mongoClientURI.getDatabase());
    this.dao = new ChangeEntryDao(DEFAULT_CHANGELOG_COLLECTION_NAME, DEFAULT_LOCK_COLLECTION_NAME);
  }

  /**
   * <p>Constructor takes db.mongodb.MongoClient object as a parameter.
   * </p><p>For more details about <tt>MongoClient</tt> please see com.mongodb.MongoClient docs
   * </p>
   *
   * @param mongoClient database connection client
   * @see MongoClient
   */
  public Mongobee(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
    this.dao = new ChangeEntryDao(DEFAULT_CHANGELOG_COLLECTION_NAME, DEFAULT_LOCK_COLLECTION_NAME);
  }

  /**
   * <p>Mongobee runner. Correct MongoDB URI should be provided.</p>
   * <p>The format of the URI is:
   * <pre>
   *   mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database[.collection]][?options]]
   * </pre>
   * <ul>
   * <li>{@code mongodb://} Required prefix</li>
   * <li>{@code username:password@} are optional.  If given, the driver will attempt to login to a database after
   * connecting to a database server. For some authentication mechanisms, only the username is specified and the password is not,
   * in which case the ":" after the username is left off as well.</li>
   * <li>{@code host1} Required.  It identifies a server address to connect to. More than one host can be provided.</li>
   * <li>{@code :portX} is optional and defaults to :27017 if not provided.</li>
   * <li>{@code /database} the name of the database to login to and thus is only relevant if the
   * {@code username:password@} syntax is used. If not specified the "admin" database will be used by default.
   * <b>Mongobee will operate on the database provided here or on the database overriden by setter setDbName(String).</b>
   * </li>
   * <li>{@code ?options} are connection options. For list of options please see com.mongodb.MongoClientURI docs</li>
   * </ul>
   * <p>For details, please see com.mongodb.MongoClientURI
   *
   * @param mongoURI with correct format
   * @see com.mongodb.MongoClientURI
   */

  public Mongobee(String mongoURI) {
    this(new MongoClientURI(mongoURI));
  }


  /**
   * Executing migration
   *
   * @throws MongobeeException exception
   */
  public void execute() throws MongobeeException {
    if (!isEnabled()) {
      logger.info("Mongobee is disabled. Exiting.");
      return;
    }

    validateConfig();

    if (this.mongoClient != null) {
      dao.connectMongoDb(this.mongoClient, dbName);
    } else {
      dao.connectMongoDb(this.mongoClientURI, dbName);
    }

    if (!dao.acquireProcessLock()) {
      if (lockAcquireTimeout != null && lockAcquireTimeout > 0L) {
        long lockAcquireStart = System.currentTimeMillis();
        while (!dao.acquireProcessLock()) {
          try {
            if (lockAcquireTimeout < 0L || (System.currentTimeMillis() - lockAcquireStart) < lockAcquireTimeout) {
              Thread.sleep(1000L);
            } else {
              logger.info("Mongobee did not acquire process lock. Timeout [{}] reached. Exiting.", lockAcquireTimeout);
              if (failOnLockAcquire) {
                throw new MongobeeLockAquireException(String.format("Lock acquire timeout [%s] reached.", lockAcquireTimeout));
              } else {
                return;
              }
            }
          } catch (InterruptedException e) {
            throw new MongobeeLockAquireException("Lock acquire thread interrupted");
          }
        }
      } else {
        logger.info("Mongobee did not acquire process lock. Exiting.");
        if (failOnLockAcquire) {
          throw new MongobeeLockAquireException("Mongobee did not acquire process lock");
        } else {
          return;
        }
      }
    }

    logger.info("Mongobee acquired process lock, starting the data migration sequence...");

    try {
      executeMigration();
    } finally {
      logger.info("Mongobee is releasing process lock.");
      dao.releaseProcessLock();
    }

    logger.info("Mongobee finished.");
  }

  private void executeMigration() throws MongobeeException {

    ChangeService service = newChangeService();
    List<Class<?>> classChangeLogs = service.fetchChangeLogs();
    Map<String, List<ChangeEntry>> changeEntryMap = dao.getAllChangeEntries();

    // This section will check for change logs in the database, which do not exist as a class in the executing version
    if (executeRollback) {
      if (changeEntryMap != null && changeEntryMap.size() > 0) {
        for (String dbChangeLogClass : changeEntryMap.keySet()) {
          boolean foundDbChangeLog = false;
          for (Class<?> classChangeLogClass : classChangeLogs) {
            logger.debug("Checking database class [{}] against class in classpath [{}]", dbChangeLogClass, classChangeLogClass.getName());
            if (dbChangeLogClass.equals(classChangeLogClass.getName())) {
              foundDbChangeLog = true;
              break;
            }
          }

          if (!foundDbChangeLog) {
            logger.info("Changelog class [{}] does not exist in this version. Performing rollback actions if they exist.", dbChangeLogClass);

            for (ChangeEntry databaseChangeEntry : changeEntryMap.get(dbChangeLogClass)) {
              if (databaseChangeEntry.getRollbackCommands() != null && !databaseChangeEntry.getRollbackCommands().isEmpty()) {
                for (String rollbackCommand : databaseChangeEntry.getRollbackCommands()) {
                  logger.info("Executing rollback command [{}]", rollbackCommand);
                  dao.getMongoDatabase().runCommand(BasicDBObject.parse(rollbackCommand));
                }
              }

              logger.info("Removing change entry [{}] from database", databaseChangeEntry.getChangeLogClass());
              dao.delete(databaseChangeEntry);
              logger.info("Rollback completed for [{}]", databaseChangeEntry);
            }
          }
        }
      }
    }

    for (Class<?> changeLogClass : classChangeLogs) {
      Object changelogInstance = null;
      try {
        changelogInstance = changeLogClass.getConstructor().newInstance();
        List<Method> changeSetMethods = service.fetchChangeSets(changelogInstance.getClass());
        List<ChangeEntry> databaseChangeEntries = changeEntryMap.get(changeLogClass.getName());

        if (executeRollback && databaseChangeEntries != null && changeSetMethods.size() < databaseChangeEntries.size()) {
          logger.info("Mongobee will perform rollback actions for class [{}] if they exist.", changeLogClass.getSimpleName());

          for (ChangeEntry databaseChangeEntry : databaseChangeEntries) {
            boolean foundChangeEntryForRollback = true;
            ChangeEntry classChangeEntry = null;
            for (Method changeSetMethod : changeSetMethods) {
              classChangeEntry = service.createChangeEntry(changeSetMethod);
              if (classChangeEntry.getChangeId().equals(databaseChangeEntry.getChangeId())) {
                foundChangeEntryForRollback = false;
              }
            }

            if (foundChangeEntryForRollback) {
              if (databaseChangeEntry.getRollbackCommands() != null && !databaseChangeEntry.getRollbackCommands().isEmpty()) {
                for (String rollbackCommand : databaseChangeEntry.getRollbackCommands()) {
                  logger.info("Executing rollback command [{}]", rollbackCommand);
                  dao.getMongoDatabase().runCommand(BasicDBObject.parse(rollbackCommand));
                }
              }

              logger.info("Removing change entry [{}] from database", databaseChangeEntry);
              dao.delete(databaseChangeEntry);
              logger.info("Rollback completed for [{}]", databaseChangeEntry);
            } else {
              logger.info("Rollback skipped for [{}]", databaseChangeEntry);
            }
          }
        } else {
          logger.info("Mongobee will perform upgrade actions for class [{}] if they exist.", changeLogClass.getSimpleName());

          for (Method changeSetMethod : changeSetMethods) {
            ChangeEntry changeEntry = service.createChangeEntry(changeSetMethod);

            try {
              if (!changeEntryListContainsChangeId(databaseChangeEntries, changeEntry.getChangeId())) {
                executeChangeSetMethod(changeSetMethod, changelogInstance, dao.getDb(), dao.getMongoDatabase());
                dao.save(changeEntry);
                logger.info("Applied [{}]", changeEntry);
              } else if (service.isRunAlwaysChangeSet(changeSetMethod)) {
                executeChangeSetMethod(changeSetMethod, changelogInstance, dao.getDb(), dao.getMongoDatabase());
                logger.info("Reapplied [{}]", changeEntry);
              } else {
                logger.info("Skipped [{}]", changeEntry);
              }
            } catch (MongobeeChangeSetException e) {
              logger.error(e.getMessage());
            }
          }
        }
      } catch (NoSuchMethodException | IllegalAccessException | InstantiationException e) {
        throw new MongobeeException(e.getMessage(), e);
      } catch (InvocationTargetException e) {
        Throwable targetException = e.getTargetException();
        throw new MongobeeException(targetException.getMessage(), e);
      }

    }
  }

  private boolean changeEntryListContainsChangeId(List<ChangeEntry> changeEntries, String changeId) {
    if (changeEntries == null || changeEntries.size() == 0) {
      return false;
    }

    for (ChangeEntry changeEntry : changeEntries) {
      if (changeEntry.getChangeId().equals(changeId)) {
        return true;
      }
    }

    return false;
  }

  protected ChangeService newChangeService() {
    return new ChangeService(changeLogsScanPackage);
  }

  protected Object executeChangeSetMethod(Method changeSetMethod, Object changeLogInstance, DB db, MongoDatabase mongoDatabase)
      throws IllegalAccessException, InvocationTargetException, MongobeeChangeSetException {
    if (changeSetMethod.getParameterTypes().length == 1
        && changeSetMethod.getParameterTypes()[0].equals(DB.class)) {
      logger.debug("method with DB argument");

      return changeSetMethod.invoke(changeLogInstance, db);
    } else if (changeSetMethod.getParameterTypes().length == 1
        && changeSetMethod.getParameterTypes()[0].equals(MongoDatabase.class)) {
      logger.debug("method with DB argument");

      return changeSetMethod.invoke(changeLogInstance, mongoDatabase);
    } else if (changeSetMethod.getParameterTypes().length == 0) {
      logger.debug("method with no params");

      return changeSetMethod.invoke(changeLogInstance);
    } else {
      throw new MongobeeChangeSetException("ChangeSet method " + changeSetMethod.getName() +
          " has wrong arguments list. Please see docs for more info!");
    }
  }

  private void validateConfig() throws MongobeeConfigurationException {
    if (!hasText(dbName)) {
      throw new MongobeeConfigurationException("DB name is not set. It should be defined in MongoDB URI or via setter");
    }
    if (!hasText(changeLogsScanPackage)) {
      throw new MongobeeConfigurationException("Scan package for changelogs is not set: use appropriate setter");
    }
  }

  /**
   * @return true if an execution is in progress, in any process.
   * @throws MongobeeConnectionException exception
   */
  public boolean isExecutionInProgress() throws MongobeeConnectionException {
    return dao.isProcessLockHeld();
  }

  /**
   * Used DB name should be set here or via MongoDB URI (in a constructor)
   *
   * @param dbName database name
   * @return Mongobee object for fluent interface
   */
  public Mongobee setDbName(String dbName) {
    this.dbName = dbName;
    return this;
  }

  /**
   * Sets uri to MongoDB
   *
   * @param mongoClientURI object with defined mongo uri
   * @return Mongobee object for fluent interface
   */
  public Mongobee setMongoClientURI(MongoClientURI mongoClientURI) {
    this.mongoClientURI = mongoClientURI;
    return this;
  }

  /**
   * Package name where @ChangeLog-annotated classes are kept.
   *
   * @param changeLogsScanPackage package where your changelogs are
   * @return Mongobee object for fluent interface
   */
  public Mongobee setChangeLogsScanPackage(String changeLogsScanPackage) {
    this.changeLogsScanPackage = changeLogsScanPackage;
    return this;
  }

  /**
   * @return true if Mongobee runner is enabled and able to run, otherwise false
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Feature which enables/disables Mongobee runner execution
   *
   * @param enabled Mongobee will run only if this option is set to true
   * @return Mongobee object for fluent interface
   */
  public Mongobee setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /**
   * Overwrites a default mongobee changelog collection hardcoded in DEFAULT_CHANGELOG_COLLECTION_NAME.
   *
   * CAUTION! Use this method carefully - when changing the name on a existing system,
   * your changelogs will be executed again on your MongoDB instance
   *
   * @param changelogCollectionName a new changelog collection name
   * @return Mongobee object for fluent interface
   */
  public Mongobee setChangelogCollectionName(String changelogCollectionName) {
    this.dao.setChangelogCollectionName(changelogCollectionName);
    return this;
  }

  /**
   * Overwrites a default mongobee lock collection hardcoded in DEFAULT_LOCK_COLLECTION_NAME
   *
   * @param lockCollectionName a new lock collection name
   * @return Mongobee object for fluent interface
   */
  public Mongobee setLockCollectionName(String lockCollectionName) {
    this.dao.setLockCollectionName(lockCollectionName);
    return this;
  }

  /**
   * Closes the Mongo instance used by Mongobee.
   * This will close either the connection Mongobee was initiated with or that which was internally created.
   */
  public void close() {
    dao.close();
  }

  /**
   * @return true if the rollback scripts in the changeset shall be executed during the migration
   */
  public boolean isExecuteRollback() {
    return executeRollback;
  }

  /**
   * Feature which enables/disables Mongobee runner rollback execution
   *
   * @param executeRollback will only execute the rollback if this option is set to true
   * @return Mongobee object for fluent interface
   */
  public Mongobee setExecuteRollback(boolean executeRollback) {
    this.executeRollback = executeRollback;
    return this;
  }

  /**
   * true if Mongobee shall throw a {@link MongobeeLockAquireException} instead of returning when the lock can't be acquired.
   *
   * @return the boolean
   */
  public boolean isFailOnLockAcquire() {
    return failOnLockAcquire;
  }

  /**
   * Sets fail on lock acquire.
   *
   * @param failOnLockAcquire the fail on lock acquire
   * @return Mongobee object for fluent interface
   */
  public Mongobee setFailOnLockAcquire(boolean failOnLockAcquire) {
    this.failOnLockAcquire = failOnLockAcquire;
    return this;
  }

  /**
   * Gets lock acquire timeout.
   *
   * @return the lock acquire timeout
   */
  public Long getLockAcquireTimeout() {
    return lockAcquireTimeout;
  }

  /**
   * Sets lock acquire timeout in milliseconds.
   *
   * @param lockAcquireTimeout the lock acquire timeout
   * @return Mongobee object for fluent interface
   */
  public Mongobee setLockAcquireTimeout(Long lockAcquireTimeout) {
    this.lockAcquireTimeout = lockAcquireTimeout;
    return this;
  }
}
