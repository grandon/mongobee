package com.github.mongobee.core.dao;

import com.github.mongobee.core.changeset.ChangeEntry;
import com.github.mongobee.core.exception.MongobeeConfigurationException;
import com.github.mongobee.core.exception.MongobeeConnectionException;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.mongobee.core.changeset.ChangeEntry.*;
import static com.github.mongobee.core.utils.StringUtils.hasText;

/**
 * @author lstolowski
 * @since 27/07/2014
 */
public class ChangeEntryDao {
  private static final Logger logger = LoggerFactory.getLogger("Mongobee dao");

  private MongoDatabase mongoDatabase;
  private DB db;  // only for Jongo driver compatibility - do not use in other contexts
  private MongoClient mongoClient;
  private ChangeEntryIndexDao indexDao;
  private String changelogCollectionName;

  private LockDao lockDao;

  public ChangeEntryDao(String changelogCollectionName, String lockCollectionName) {
	this.indexDao = new ChangeEntryIndexDao(changelogCollectionName);
	this.lockDao = new LockDao(lockCollectionName);
	this.changelogCollectionName = changelogCollectionName;
  }

  public MongoDatabase getMongoDatabase() {
    return mongoDatabase;
  }

  /**
   * @deprecated implemented only for Jongo driver compatibility and backward compatibility - do not use in other contexts
   * @return com.mongodb.DB
   */
  public DB getDb() {
    return db;
  }

  public MongoDatabase connectMongoDb(MongoClient mongo, String dbName) throws MongobeeConfigurationException {
    if (!hasText(dbName)) {
      throw new MongobeeConfigurationException("DB name is not set. Should be defined in MongoDB URI or via setter");
    } else {

      this.mongoClient = mongo;

      db = mongo.getDB(dbName); // for Jongo driver and backward compatibility (constructor has required parameter Jongo(DB) )
      mongoDatabase = mongo.getDatabase(dbName);

      ensureChangeLogCollectionIndex(mongoDatabase.getCollection(changelogCollectionName));
      initializeLock();
      return mongoDatabase;
    }
  }

  public MongoDatabase connectMongoDb(MongoClientURI mongoClientURI, String dbName)
      throws MongobeeConfigurationException, MongobeeConnectionException {

    final MongoClient mongoClient = new MongoClient(mongoClientURI);
    final String database = (!hasText(dbName)) ? mongoClientURI.getDatabase() : dbName;
    return this.connectMongoDb(mongoClient, database);
  }

  /**
   * Try to acquire process lock
   *
   * @return true if successfully acquired, false otherwise
   * @throws MongobeeConnectionException exception
   */
  public boolean acquireProcessLock() throws MongobeeConnectionException {
    verifyDbConnection();
    return lockDao.acquireLock(getMongoDatabase());
  }

  public void releaseProcessLock() throws MongobeeConnectionException {
    verifyDbConnection();
    lockDao.releaseLock(getMongoDatabase());
  }

  public boolean isProcessLockHeld() throws MongobeeConnectionException {
    verifyDbConnection();
    return lockDao.isLockHeld(getMongoDatabase());
  }

  @SuppressWarnings("unchecked")
  public Map<String, List<ChangeEntry>> getAllChangeEntries() throws MongobeeConnectionException {
    verifyDbConnection();

    FindIterable<Document> mongobeeChangeLog = getMongoDatabase().getCollection(changelogCollectionName).find().sort(new BasicDBObject(KEY_TIMESTAMP, -1));

    if (mongobeeChangeLog == null) {
      return null;
    }

    Map<String, List<ChangeEntry>> changeEntryMap = new HashMap<>();
    for (Document d : mongobeeChangeLog) {
      ChangeEntry changeEntry = new ChangeEntry(d.getString(KEY_CHANGEID), d.getString(KEY_AUTHOR), d.getDate(KEY_TIMESTAMP), d.getString(KEY_CHANGELOGCLASS), d.getString(KEY_CHANGESETMETHOD), d.get(KEY_ROLLBACK_COMMANDS, List.class));
      logger.debug("Found change entry in database [{}]", changeEntry.toString());
      if (changeEntryMap.containsKey(changeEntry.getChangeLogClass())) {
        changeEntryMap.get(changeEntry.getChangeLogClass()).add(changeEntry);
      } else {
        List<ChangeEntry> changeEntries = new ArrayList<>();
        changeEntries.add(changeEntry);
        changeEntryMap.put(changeEntry.getChangeLogClass(), changeEntries);
      }
    }

    return changeEntryMap;
  }

  public void save(ChangeEntry changeEntry) throws MongobeeConnectionException {
    verifyDbConnection();

    MongoCollection<Document> mongobeeLog = getMongoDatabase().getCollection(changelogCollectionName);

    logger.debug("Saving ChangeEntry to database [{}]", changeEntry.buildFullDBObject().toJson());

    mongobeeLog.insertOne(changeEntry.buildFullDBObject());
  }

  public void delete(ChangeEntry changeEntry) throws MongobeeConnectionException {
    verifyDbConnection();

    MongoCollection<Document> mongobeeLog = getMongoDatabase().getCollection(changelogCollectionName);

    mongobeeLog.deleteOne(changeEntry.buildFullDBObject());
  }

  private void verifyDbConnection() throws MongobeeConnectionException {
    if (getMongoDatabase() == null) {
      throw new MongobeeConnectionException("Database is not connected. Mongobee has thrown an unexpected error",
          new NullPointerException());
    }
  }

  private void ensureChangeLogCollectionIndex(MongoCollection<Document> collection) {
    Document index = indexDao.findRequiredChangeAndAuthorIndex(mongoDatabase);
    if (index == null) {
      indexDao.createRequiredUniqueIndex(collection);
      logger.debug("Index in collection " + changelogCollectionName + " was created");
    } else if (!indexDao.isUnique(index)) {
      indexDao.dropIndex(collection, index);
      indexDao.createRequiredUniqueIndex(collection);
      logger.debug("Index in collection " + changelogCollectionName + " was recreated");
    }
  }

  public void close() {
      this.mongoClient.close();
  }

  private void initializeLock() {
    lockDao.intitializeLock(mongoDatabase);
  }

  public void setIndexDao(ChangeEntryIndexDao changeEntryIndexDao) {
    this.indexDao = changeEntryIndexDao;
  }

  /* Visible for testing */
  void setLockDao(LockDao lockDao) {
    this.lockDao = lockDao;
  }

  public void setChangelogCollectionName(String changelogCollectionName) {
	this.indexDao.setChangelogCollectionName(changelogCollectionName);
	this.changelogCollectionName = changelogCollectionName;
  }

  public void setLockCollectionName(String lockCollectionName) {
	this.lockDao.setLockCollectionName(lockCollectionName);
  }
  
}
