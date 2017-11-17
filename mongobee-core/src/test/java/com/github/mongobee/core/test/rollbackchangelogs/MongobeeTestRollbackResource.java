package com.github.mongobee.core.test.rollbackchangelogs;

import com.github.mongobee.core.changeset.ChangeLog;
import com.github.mongobee.core.changeset.ChangeSet;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/**
 * @author grandon
 * @since 2017/11/14
 */
@ChangeLog(order = "1")
public class MongobeeTestRollbackResource {

  @ChangeSet(author = "testuser", id = "test1", order = "01")
  public void testChangeSet(MongoDatabase mongoDatabase) {
    Document contact;

    System.out.println("invoked 1");

    contact = new Document();
    contact.put("name", "foo");

    mongoDatabase.getCollection("contact").insertOne(contact);
  }
}
