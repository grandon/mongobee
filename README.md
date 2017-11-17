![mongobee](https://raw.githubusercontent.com/mongobee/mongobee/master/misc/mongobee_min.png)

[![Build Status](https://travis-ci.org/mongobee/mongobee.svg?branch=master)](https://travis-ci.org/mongobee/mongobee) [![Coverity Scan Build Status](https://scan.coverity.com/projects/2721/badge.svg)](https://scan.coverity.com/projects/2721) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.mongobee/mongobee/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.mongobee/mongobee) [![Licence](https://img.shields.io/hexpm/l/plug.svg)](https://github.com/mongobee/mongobee/blob/master/LICENSE)
---


**mongobee** is a Java tool which helps you to *manage changes* in your MongoDB and *synchronize* them with your application.
The concept is very similar to other db migration tools such as [Liquibase](http://www.liquibase.org) or [Flyway](http://flywaydb.org) but *without using XML/JSON/YML files*.

The goal is to keep this tool simple and comfortable to use.


**mongobee** provides new approach for adding changes (change sets) based on Java classes and methods with appropriate annotations.

## Getting started

### Add a dependency

With Maven
```xml
<dependency>
  <groupId>com.github.mongobee</groupId>
  <artifactId>mongobee</artifactId>
  <version>0.13</version>
</dependency>
```
With Gradle
```groovy
compile 'org.javassist:javassist:3.18.2-GA' // workaround for ${javassist.version} placeholder issue*
compile 'com.github.mongobee:mongobee:0.13'
```

### Usage with Spring

You need to instantiate Mongobee object and provide some configuration.
If you use Spring can be instantiated as a singleton bean in the Spring context. 
In this case the migration process will be executed automatically on startup.

```java
@Bean
public Mongobee mongobee(){
  Mongobee runner = new Mongobee("mongodb://YOUR_DB_HOST:27017/DB_NAME");
  runner.setDbName("yourDbName");         // host must be set if not set in URI
  runner.setChangeLogsScanPackage(
       "com.example.yourapp.changelogs"); // the package to be scanned for changesets
  
  return runner;
}
```


### Usage without Spring
Using mongobee without a spring context has similar configuration but you have to remember to run `execute()` method to start a migration process.

```java
Mongobee runner = new Mongobee("mongodb://YOUR_DB_HOST:27017/DB_NAME");
runner.setDbName("yourDbName");         // host must be set if not set in URI
runner.setChangeLogsScanPackage(
     "com.example.yourapp.changelogs"); // package to scan for changesets

runner.execute();         //  ------> starts migration changesets
```

Above examples provide minimal configuration. `Mongobee` object provides some other possibilities (setters) to make the tool more flexible:

```java
runner.setChangelogCollectionName(logColName);   // default is dbchangelog, collection with applied change sets
runner.setLockCollectionName(lockColName);       // default is mongobeelock, collection used during migration process
runner.setEnabled(shouldBeEnabled);              // default is true, migration won't start if set to false
```

MongoDB URI format:
```
mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database[.collection]][?options]]
```
[More about URI](http://mongodb.github.io/mongo-java-driver/3.5/javadoc/)


### Creating change logs

`ChangeLog` contains bunch of `ChangeSet`s. `ChangeSet` is a single task (set of instructions made on a database). In other words `ChangeLog` is a class annotated with `@ChangeLog` and containing methods annotated with `@ChangeSet`.

```java 
package com.example.yourapp.changelogs;

@ChangeLog
public class DatabaseChangelog {
  
  @ChangeSet(order = "001", id = "someChangeId", author = "testAuthor")
  public void importantWorkToDo(DB db){
     // task implementation
  }


}
```
#### @ChangeLog

Class with change sets must be annotated by `@ChangeLog`. There can be more than one change log class but in that case `order` argument should be provided:

```java
@ChangeLog(order = "001")
public class DatabaseChangelog {
  //...
}
```
ChangeLogs are sorted alphabetically by `order` argument and changesets are applied due to this order.

#### @ChangeSet

Method annotated by @ChangeSet is taken and applied to the database. History of applied change sets is stored in a collection called `dbchangelog` (by default) in your MongoDB

##### Annotation parameters:

`order` - string for sorting change sets in one changelog. Sorting in alphabetical order, ascending. It can be a number, a date etc.

`id` - name of a change set, **must be unique** for all change logs in a database

`author` - author of a change set

`runAlways` - _[optional, default: false]_ changeset will always be executed but only first execution event will be stored in dbchangelog collection

`rollbackScriptName` - _[optional, default: ""]_ the script file name containing mongodb commands to rollback the change set. See section [Rollback](#rollback)

##### Defining ChangeSet methods
Method annotated by `@ChangeSet` can have one of the following definition:

```java
@ChangeSet(order = "001", id = "someChangeWithoutArgs", author = "testAuthor")
public void someChange1() {
   // method without arguments can do some non-db changes
}

@ChangeSet(order = "002", id = "someChangeWithMongoDatabase", author = "testAuthor")
public void someChange2(MongoDatabase db) {
  // type: com.mongodb.client.MongoDatabase : original MongoDB driver v. 3.x, operations allowed by driver are possible
  // example: 
  MongoCollection<Document> mycollection = db.getCollection("mycollection");
  Document doc = new Document("testName", "example").append("test", "1");
  mycollection.insertOne(doc);
}

@ChangeSet(order = "003", id = "someChangeWithDb", author = "testAuthor")
public void someChange3(DB db) {
  // This is deprecated in mongo-java-driver 3.x, use MongoDatabase instead
  // type: com.mongodb.DB : original MongoDB driver v. 2.x, operations allowed by driver are possible
  // example: 
  DBCollection mycollection = db.getCollection("mycollection");
  BasicDBObject doc = new BasicDBObject().append("test", "1");
  mycollection .insert(doc);
}

@ChangeSet(order = "004", id = "someChangeWithJongo", author = "testAuthor")
public void someChange4(Jongo jongo) {
  // type: org.jongo.Jongo : Jongo driver can be used, used for simpler notation
  // example:
  MongoCollection mycollection = jongo.getCollection("mycollection");
  mycollection.insert("{test : 1}");
}

@ChangeSet(order = "005", id = "someChangeWithSpringDataTemplate", author = "testAuthor")
public void someChange5(MongoTemplate mongoTemplate) {
  // type: org.springframework.data.mongodb.core.MongoTemplate
  // Spring Data integration allows using MongoTemplate in the ChangeSet
  // example:
  mongoTemplate.save(myEntity);
}

@ChangeSet(order = "006", id = "someChangeWithSpringDataTemplate", author = "testAuthor")
public void someChange5(MongoTemplate mongoTemplate, Environment environment) {
  // type: org.springframework.data.mongodb.core.MongoTemplate
  // type: org.springframework.core.env.Environment
  // Spring Data integration allows using MongoTemplate and Environment in the ChangeSet
}
```

### Rollback

Since the change sets are written in Java classes, a rollback is not possible due to the fact that the previous versions can't be aware of what was done in future versions.
Therefore the rollback is supported by providing MongoDB script files.
The script content is saved in the mongobee changelog collection, together with the change set method information.
An annotation field `rollbackScriptName` was added to `@ChangeSet` to provide the script name, which needs to be available in the classpath.
These scripts are a set of MongoDB Commands - see [MongoDB Manual - Command](https://docs.mongodb.com/manual/reference/command/)

The rollback feature is enabled by default and can be controlled by setting the following:
```java
setExecuteRollback(boolean executeRollback)
```

The rollback will be executed in the following cases:
* When a change change log in the DB is found, but not anymore in the corresponding class and a `rollbackScriptName` was set on the not existing change log entries
* When a change change log in the DB is found, but the corresponding class does not exist at all the whole change log will be rolled back

_Example - Rollback ChangeLog_: makes use of the `rollbackScriptName` annotation parameter
```java
  @ChangeSet(author = "testuser", id = "test1", order = "01", rollbackScriptName = "mongobee-test-resource-rollback-changeset1.json")
  public void testChangeSet(MongoDatabase mongoDatabase) {
    Document contact = new Document();
    contact.put("name", "foo");
    mongoDatabase.getCollection("contact").insertOne(contact);
  }

  @ChangeSet(author = "testuser", id = "test2", order = "02", rollbackScriptName = "mongobee-test-resource-rollback-changeset2.json")
  public void testChangeSet2(MongoDatabase mongoDatabase) {
    Document contact = new Document();
    contact.put("name", "bar");
    mongoDatabase.getCollection("contact").insertOne(contact);

    contact = new Document();
    contact.put("name", "baz");
    mongoDatabase.getCollection("contact").insertOne(contact);
  }
```
_Example - mongobee-test-resource-rollback-changeset1.json_: mongodb commands to rollback `foo`
```
{
  delete: "contact",
  deletes: [
    {
      q: {
        name: "foo"
      },
      limit: 1
    }
  ]
}
```

_Example - mongobee-test-resource-rollback-changeset2.json_: mongodb commands to rollback `bar` and `baz`
```
{
  delete: "contact",
  deletes: [
    {
      q: {
        name: "bar"
      },
      limit: 1
    }
  ]
}

{
  delete: "contact",
  deletes: [
    {
      q: {
        name: "baz"
      },
      limit: 1
    }
  ]
}
```

Multiple mongodb commands **must be** separated by a new line!

### Controlling Lock Acquire

It is possible to control the lock acquire behaviour.
By default Mongobee will skip the processing if the lock can't be acquired without throwing an Exception or blocking while another Mongobee process executes.

This can be changed to throw a `MongobeeLockAquireException` by setting the following:
```java
setFailOnLockAcquire(boolean failOnLockAcquire)
```

A lock acquire timeout (in milliseconds) can be set to block the process and wait for the lock to be released.
If the lockAcquireTimeout is set to `-1` it will block forever.
The default is `null` (not blocking/waiting):
```java
setLockAcquireTimeout(Long lockAcquireTimeout)
```

### Using Spring profiles
     
**mongobee** accepts Spring's `org.springframework.context.annotation.Profile` annotation. If a change log or change set class is annotated  with `@Profile`, 
then it is activated for current application profiles.

_Example 1_: annotated change set will be invoked for a `dev` profile
```java
@Profile("dev")
@ChangeSet(author = "testuser", id = "myDevChangest", order = "01")
public void devEnvOnly(DB db){
  // ...
}
```
_Example 2_: all change sets in a changelog will be invoked for a `test` profile
```java
@ChangeLog(order = "1")
@Profile("test")
public class ChangelogForTestEnv{
  @ChangeSet(author = "testuser", id = "myTestChangest", order = "01")
  public void testingEnvOnly(DB db){
    // ...
  } 
}
```

#### Enabling @Profile annotation (option)
      
To enable the `@Profile` integration, please inject `org.springframework.core.env.Environment` to you runner.

```java      
@Bean @Autowired
public Mongobee mongobee(Environment environment) {
  Mongobee runner = new Mongobee(uri);
  runner.setSpringEnvironment(environment)
  //... etc
}
```

## Known issues

##### Mongo java driver conflicts

**mongobee** depends on `mongo-java-driver`. If your application has mongo-java-driver dependency too, there could be a library conflicts in some cases.

**Exception**:
```
com.mongodb.WriteConcernException: { "serverUsed" : "localhost" , 
"err" : "invalid ns to index" , "code" : 10096 , "n" : 0 , 
"connectionId" : 955 , "ok" : 1.0}
```

**Workaround**:

You can exclude mongo-java-driver from **mongobee**  and use your dependency only. Maven example (pom.xml) below:
```xml
<dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>mongo-java-driver</artifactId>
    <version>3.0.0</version>
</dependency>

<dependency>
  <groupId>com.github.mongobee</groupId>
  <artifactId>mongobee</artifactId>
  <version>0.9</version>
  <exclusions>
    <exclusion>
      <groupId>org.mongodb</groupId>
      <artifactId>mongo-java-driver</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```
