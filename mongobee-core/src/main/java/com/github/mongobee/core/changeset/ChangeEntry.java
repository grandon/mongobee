package com.github.mongobee.core.changeset;

import java.util.Date;
import java.util.List;

import com.github.mongobee.core.Mongobee;
import org.bson.Document;

/**
 * Entry in the changes collection log {@link Mongobee#DEFAULT_CHANGELOG_COLLECTION_NAME}
 * Type: entity class.
 *
 * @author lstolowski
 * @since 27/07/2014
 */
public class ChangeEntry {
  public static final String KEY_CHANGEID = "changeId";
  public static final String KEY_AUTHOR = "author";
  public static final String KEY_TIMESTAMP = "timestamp";
  public static final String KEY_CHANGELOGCLASS = "changeLogClass";
  public static final String KEY_CHANGESETMETHOD = "changeSetMethod";
  public static final String KEY_ROLLBACK_COMMANDS = "rollbackCommands";

  private String changeId;
  private String author;
  private Date timestamp;
  private String changeLogClass;
  private String changeSetMethodName;
  private List<String> rollbackCommands;
  
  public ChangeEntry(String changeId, String author, Date timestamp, String changeLogClass, String changeSetMethodName, List<String> rollbackCommands) {
    this.changeId = changeId;
    this.author = author;
    this.timestamp = new Date(timestamp.getTime());
    this.changeLogClass = changeLogClass;
    this.changeSetMethodName = changeSetMethodName;
    this.rollbackCommands = rollbackCommands;
  }

  public Document buildFullDBObject() {
    Document entry = new Document();

    entry.append(KEY_CHANGEID, changeId)
        .append(KEY_AUTHOR, author)
        .append(KEY_TIMESTAMP, timestamp)
        .append(KEY_CHANGELOGCLASS, changeLogClass)
        .append(KEY_CHANGESETMETHOD, changeSetMethodName)
        .append(KEY_ROLLBACK_COMMANDS, rollbackCommands);

    return entry;
  }

  public Document buildSearchQueryDBObject() {
    return new Document()
        .append(KEY_CHANGEID, changeId)
        .append(KEY_AUTHOR, author);
  }

  @Override
  public String toString() {
    return "ChangeEntry" + '[' +
        "changeId='" + changeId + '\'' +
        ", author='" + author + '\'' +
        ", timestamp=" + timestamp +
        ", changeLogClass='" + changeLogClass + '\'' +
        ", changeSetMethodName='" + changeSetMethodName + '\'' +
        ", rollbackCommands=" + rollbackCommands +
        ']';
  }

  public String getChangeId() {
    return this.changeId;
  }

  public String getAuthor() {
    return this.author;
  }

  public Date getTimestamp() {
    return this.timestamp;
  }

  public String getChangeLogClass() {
    return this.changeLogClass;
  }

  public String getChangeSetMethodName() {
    return this.changeSetMethodName;
  }

  public List<String> getRollbackCommands() {
    return rollbackCommands;
  }
}
