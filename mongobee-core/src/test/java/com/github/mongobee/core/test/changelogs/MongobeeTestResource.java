package com.github.mongobee.core.test.changelogs;

import com.github.mongobee.core.changeset.ChangeLog;
import com.github.mongobee.core.changeset.ChangeSet;
import com.mongodb.DB;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/**
 * @author lstolowski
 * @since 27/07/2014
 */
@ChangeLog(order = "1")
public class MongobeeTestResource {

  @ChangeSet(author = "testuser", id = "test1", order = "01", rollbackScriptName = "mongobee-test-resource-rollback-changeset1.json")
  public void testChangeSet(MongoDatabase mongoDatabase) {
    Document contact = new Document();
    contact.put("name", "foo");
    mongoDatabase.getCollection("contact").insertOne(contact);

    System.out.println("invoked 1");
  }

  @ChangeSet(author = "testuser", id = "test2", order = "02", rollbackScriptName = "mongobee-test-resource-rollback-changeset2.json")
  public void testChangeSet2(MongoDatabase mongoDatabase) {
    Document contact = new Document();
    contact.put("name", "bar");
    mongoDatabase.getCollection("contact").insertOne(contact);

    contact = new Document();
    contact.put("name", "baz");
    mongoDatabase.getCollection("contact").insertOne(contact);

    System.out.println("invoked 2");
  }

  @ChangeSet(author = "testuser", id = "test3", order = "03")
  public void testChangeSet3(DB db) {
    System.out.println("invoked 3 with db=" + db.toString());
  }

  @ChangeSet(author = "testuser", id = "test4", order = "04")
  public void testChangeSet4() {
    System.out.println("invoked 4");
  }

  @ChangeSet(author = "testuser", id = "test5", order = "05")
  public void testChangeSet5(MongoDatabase mongoDatabase) {
    System.out.println("invoked 5 with mongoDatabase=" + mongoDatabase.toString());
  }
}
