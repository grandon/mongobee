package com.github.mongobee.core;

import com.github.fakemongo.Fongo;
import com.github.mongobee.core.changeset.ChangeEntry;
import com.github.mongobee.core.dao.ChangeEntryDao;
import com.github.mongobee.core.dao.ChangeEntryIndexDao;
import com.github.mongobee.core.exception.MongobeeConfigurationException;
import com.github.mongobee.core.exception.MongobeeException;
import com.github.mongobee.core.exception.MongobeeLockAquireException;
import com.github.mongobee.core.test.changelogs.MongobeeTestResource;
import com.github.mongobee.core.test.rollbackchangelogs.MongobeeTestRollbackResource;
import com.mongodb.DB;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MongobeeTest {

  private static final String CHANGELOG_COLLECTION_NAME = "dbchangelog";
  @InjectMocks
  private Mongobee runner = new Mongobee();

  @Mock
  private ChangeEntryDao dao;

  @Mock
  private ChangeEntryIndexDao indexDao;

  private DB fakeDb;
  private MongoDatabase fakeMongoDatabase;

  @Before
  public void init() throws MongobeeException, UnknownHostException {
    fakeDb = new Fongo("testServer").getDB("mongobeetest");
    fakeMongoDatabase = new Fongo("testServer").getDatabase("mongobeetest");
    when(dao.connectMongoDb(any(MongoClientURI.class), anyString()))
        .thenReturn(fakeMongoDatabase);
    when(dao.getDb()).thenReturn(fakeDb);
    when(dao.getMongoDatabase()).thenReturn(fakeMongoDatabase);
    doCallRealMethod().when(dao).save(any(ChangeEntry.class));
    doCallRealMethod().when(dao).setChangelogCollectionName(anyString());
    doCallRealMethod().when(dao).setIndexDao(any(ChangeEntryIndexDao.class));
    doCallRealMethod().when(dao).getAllChangeEntries();
    doCallRealMethod().when(dao).delete(any(ChangeEntry.class));
    dao.setIndexDao(indexDao);
    dao.setChangelogCollectionName(CHANGELOG_COLLECTION_NAME);

    runner.setDbName("mongobeetest");
    runner.setEnabled(true);
    runner.setChangeLogsScanPackage(MongobeeTestResource.class.getPackage().getName());
  }

  @Test(expected = MongobeeConfigurationException.class)
  public void shouldThrowAnExceptionIfNoDbNameSet() throws Exception {
    Mongobee runner = new Mongobee(new MongoClientURI("mongodb://localhost:27017/"));
    runner.setEnabled(true);
    runner.setChangeLogsScanPackage(MongobeeTestResource.class.getPackage().getName());
    runner.execute();
  }

  @Test
  public void shouldExecuteAllChangeSets() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(true);

    // when
    runner.execute();

    // then
    verify(dao, times(11)).save(any(ChangeEntry.class)); // 11 changesets saved to dbchangelog

    // dbchangelog collection checking
    long change1 = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_CHANGEID, "test1")
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(1, change1);
    long change2 = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_CHANGEID, "test2")
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(1, change2);
    long change3 = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_CHANGEID, "test3")
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(1, change3);
    long change4 = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_CHANGEID, "test4")
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(1, change4);
    long change5 = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_CHANGEID, "test5")
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(1, change5);

    long changeAll = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(11, changeAll);
  }

  @Test
  public void shouldExecuteRemainingChangeSets() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(true);

    Document document = new Document ();
    document.put ("_id", "59fb20c30ec23b24b5466063");
    document.put ("changeId", "test1");
    document.put ("author", "testuser");
    document.put ("timestamp", new Date());
    document.put ("changeLogClass", "com.github.mongobee.core.test.changelogs.MongobeeTestResource");
    document.put ("changeSetMethod", "testChangeSet");

    fakeMongoDatabase.getCollection (CHANGELOG_COLLECTION_NAME).insertOne (document);

    // when
    runner.execute();

    // then
    verify(dao, times(10)).save(any(ChangeEntry.class)); // 11 changesets saved to dbchangelog

    // dbchangelog collection checking
    long change1 = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_CHANGEID, "test1")
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(1, change1);
    long change2 = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_CHANGEID, "test2")
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(1, change2);
    long change3 = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_CHANGEID, "test3")
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(1, change3);
    long change4 = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_CHANGEID, "test4")
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(1, change4);
    long change5 = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_CHANGEID, "test5")
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(1, change5);

    long changeAll = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(11, changeAll);
  }

  @Test
  public void shouldExecuteRollbackChangeSets() throws Exception {
    // given
    runner.setChangeLogsScanPackage(MongobeeTestRollbackResource.class.getPackage().getName());
    when(dao.acquireProcessLock()).thenReturn(true);

    List<String> rollbackCommands = new ArrayList<>();
    rollbackCommands.add("{delete:\"contact\",deletes:[{q:{name:\"foo\"},limit:1}]}");

    Document document = new Document ();
    document.put ("_id", "1");
    document.put ("changeId", "test1");
    document.put ("author", "testuser");
    document.put ("timestamp", new Date());
    document.put ("changeLogClass", "com.github.mongobee.core.test.rollbackchangelogs.MongobeeTestRollbackResource");
    document.put ("changeSetMethod", "testChangeSet");
    document.put ("rollbackCommands", rollbackCommands);
    fakeMongoDatabase.getCollection (CHANGELOG_COLLECTION_NAME).insertOne (document);

    rollbackCommands = new ArrayList<>();
    rollbackCommands.add("{delete:\"contact\",deletes:[{q:{name:\"bar\"},limit:1}]}");

    document = new Document ();
    document.put ("_id", "2");
    document.put ("changeId", "test2");
    document.put ("author", "testuser");
    document.put ("timestamp", new Date());
    document.put ("changeLogClass", "com.github.mongobee.core.test.rollbackchangelogs.MongobeeTestRollbackResource");
    document.put ("changeSetMethod", "testChangeSet2");
    document.put ("rollbackCommands", rollbackCommands);
    fakeMongoDatabase.getCollection (CHANGELOG_COLLECTION_NAME).insertOne (document);

    rollbackCommands = new ArrayList<>();
    rollbackCommands.add("{delete:\"contact\",deletes:[{q:{name:\"baz\"},limit:1}]}");

    document = new Document ();
    document.put ("_id", "3");
    document.put ("changeId", "test3");
    document.put ("author", "testuser");
    document.put ("timestamp", new Date());
    document.put ("changeLogClass", "com.github.mongobee.core.test.rollbackchangelogs.MongobeeTestRollbackResource");
    document.put ("changeSetMethod", "testChangeSet3");
    document.put ("rollbackCommands", rollbackCommands);
    fakeMongoDatabase.getCollection (CHANGELOG_COLLECTION_NAME).insertOne (document);

    document = new Document ();
    document.put ("_id", "1");
    document.put ("name", "foo");
    fakeMongoDatabase.getCollection ("contact").insertOne (document);

    document = new Document ();
    document.put ("_id", "2");
    document.put ("name", "bar");
    fakeMongoDatabase.getCollection ("contact").insertOne (document);

    document = new Document ();
    document.put ("_id", "3");
    document.put ("name", "baz");
    fakeMongoDatabase.getCollection ("contact").insertOne (document);

    // expecting 3 entries (foo, bar and baz) in the collection "contact"
    assertEquals(3, fakeMongoDatabase.getCollection ("contact").count());

    // when
    runner.execute();

    // expecting one entry (foo) in the collection "contact"
    assertEquals(1, fakeMongoDatabase.getCollection ("contact").count());

    // then
    verify(dao, times(2)).delete(any(ChangeEntry.class)); // 2 changesets rollback executed

    // dbchangelog collection checking
    long change1 = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_CHANGEID, "test1")
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(1, change1);

    long changeAll = fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME).count(new Document()
        .append(ChangeEntry.KEY_AUTHOR, "testuser"));
    assertEquals(1, changeAll);
  }

  @Test
  public void shouldPassOverChangeSets() throws Exception {
    // when
    runner.execute();

    // then
    verify(dao, times(0)).save(any(ChangeEntry.class)); // no changesets saved to dbchangelog
  }

  @Test
  public void shouldExecuteProcessWhenLockAcquired() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(true);

    // when
    runner.execute();

    // then
    verify(dao, times(11)).save(any(ChangeEntry.class)); // no changesets saved to dbchangelog
  }

  @Test
  public void shouldReleaseLockAfterWhenLockAcquired() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(true);

    // when
    runner.execute();

    // then
    verify(dao).releaseProcessLock();
  }

  @Test
  public void shouldNotExecuteProcessWhenLockNotAcquired() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(false);

    // when
    runner.execute();

    // then
    verify(dao, never()).save(any(ChangeEntry.class));
  }

  @Test
  public void shouldReturnExecutionStatusBasedOnDao() throws Exception {
    // given
    when(dao.isProcessLockHeld()).thenReturn(true);

    boolean inProgress = runner.isExecutionInProgress();

    // then
    assertTrue(inProgress);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReleaseLockWhenExceptionInMigration() throws Exception {

    // given
    // would be nicer with a mock for the whole execution, but this would mean breaking out to separate class..
    // this should be "good enough"
    when(dao.acquireProcessLock()).thenReturn(true);
    doThrow(RuntimeException.class).when(dao).save(any(ChangeEntry.class));

    // when
    // have to catch the exception to be able to verify after
    try {
      runner.execute();
    } catch (Exception e) {
      // do nothing
    }
    // then
    verify(dao).releaseProcessLock();

  }

  @Test(expected = MongobeeLockAquireException.class)
  public void shouldThrowExceptionWhenLockNotAcquired() throws Exception {
    // given
    runner.setFailOnLockAcquire(true);
    when(dao.acquireProcessLock()).thenReturn(false);

    // when
    runner.execute();

    // then
    verify(dao, never()).save(any(ChangeEntry.class));
  }

  @Test
  public void shouldWaitForLockAndProcess() throws Exception {
    // given
    runner.setLockAcquireTimeout(2000L);
    when(dao.acquireProcessLock()).thenReturn(false).thenReturn(true);

    // when
    runner.execute();

    // then
    verify(dao, times (11)).save(any(ChangeEntry.class));
  }

  @Test
  public void shouldWaitForLockAndReturn() throws Exception {
    // given
    runner.setLockAcquireTimeout(2000L);
    when(dao.acquireProcessLock()).thenReturn(false);

    // when
    runner.execute();

    // then
    verify(dao, never()).save(any(ChangeEntry.class));
  }

  @Test(expected = MongobeeLockAquireException.class)
  public void shouldWaitForLockAndFail() throws Exception {
    // given
    runner.setFailOnLockAcquire(true);
    runner.setLockAcquireTimeout(2000L);
    when(dao.acquireProcessLock()).thenReturn(false);

    // when
    try {
      runner.execute();
    } catch (MongobeeLockAquireException e) {
      assertTrue(e.getMessage().contains("timeout"));
      throw e;
    }

    // then
    verify(dao, never()).save(any(ChangeEntry.class));
  }

  @After
  public void cleanUp() {
    fakeDb.dropDatabase();
  }

}
