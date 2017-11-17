package com.github.mongobee.core.utils;

import com.github.mongobee.core.changeset.ChangeEntry;
import com.github.mongobee.core.changeset.ChangeLog;
import com.github.mongobee.core.changeset.ChangeSet;
import com.github.mongobee.core.exception.MongobeeChangeSetException;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.util.*;

import static java.util.Arrays.asList;

/**
 * Utilities to deal with reflections and annotations
 *
 * @author lstolowski
 * @since 27/07/2014
 */
public class ChangeService {

  private final String changeLogsBasePackage;

  public ChangeService(String changeLogsBasePackage) {
    this.changeLogsBasePackage = changeLogsBasePackage;
  }

  public List<Class<?>> fetchChangeLogs(){
    Reflections reflections = new Reflections(changeLogsBasePackage);
    List<Class<?>> changeLogs = new ArrayList<>(reflections.getTypesAnnotatedWith(ChangeLog.class)); // TODO remove dependency, do own method

    Collections.sort(changeLogs, new ChangeLogComparator());

    return changeLogs;
  }

  public List<Method> fetchChangeSets(final Class<?> type) throws MongobeeChangeSetException {
    final List<Method> changeSets = filterChangeSetAnnotation(asList(type.getDeclaredMethods()));

    Collections.sort(changeSets, new ChangeSetComparator());

    return changeSets;
  }

  public boolean isRunAlwaysChangeSet(Method changesetMethod){
    if (changesetMethod.isAnnotationPresent(ChangeSet.class)){
      ChangeSet annotation = changesetMethod.getAnnotation(ChangeSet.class);
      return annotation.runAlways();
    } else {
      return false;
    }
  }

  public List<ChangeEntry> createChangeEntries(List<Method> changeSetMethods) throws MongobeeChangeSetException {
    List<ChangeEntry> changeEntries = null;

    if (changeSetMethods != null) {
      changeEntries = new ArrayList<>();

      for (Method changeSetMethod : changeSetMethods) {
        changeEntries.add(createChangeEntry(changeSetMethod));
      }
    }

    return changeEntries;
  }

  public ChangeEntry createChangeEntry(Method changeSetMethod) throws MongobeeChangeSetException {
    if (changeSetMethod.isAnnotationPresent(ChangeSet.class)){
      ChangeSet annotation = changeSetMethod.getAnnotation(ChangeSet.class);

      List<String> rollbackCommands = null;
      Scanner rollbackScriptScanner = null;

      try {
        if (!annotation.rollbackScriptName().isEmpty()) {
          rollbackScriptScanner = new Scanner(this.getClass().getClassLoader().getResourceAsStream(annotation.rollbackScriptName()), "UTF-8").useDelimiter("\\A");

          if (rollbackScriptScanner.hasNext()) {
            String rollbackScript = rollbackScriptScanner.next();
            rollbackCommands = Arrays.asList(rollbackScript.split("\n\n"));
          } else {
            throw new MongobeeChangeSetException(String.format("Problem processing rollback script [%s]. Verify that the script is in the classpath and contains valid content.", annotation.rollbackScriptName()));
          }
        }
      } catch (Exception e) {
        throw new MongobeeChangeSetException(String.format("Problem processing rollback script [%s]. Verify that the script is in the classpath and contains valid content. Message = [%s]", annotation.rollbackScriptName(), e.getMessage()), e);
      } finally {
        if (rollbackScriptScanner != null) {
          rollbackScriptScanner.close();
        }
      }

      return new ChangeEntry(
          annotation.id(),
          annotation.author(),
          new Date(),
          changeSetMethod.getDeclaringClass().getName(),
          changeSetMethod.getName(),
          rollbackCommands);
    } else {
      return null;
    }
  }

  protected List<Method> filterChangeSetAnnotation(List<Method> allMethods) throws MongobeeChangeSetException {
    final Set<String> changeSetIds = new HashSet<>();
    final List<Method> changeSetMethods = new ArrayList<>();
    for (final Method method : allMethods) {
      if (method.isAnnotationPresent(ChangeSet.class)) {
        ChangeSet currentChangeSet = method.getAnnotation(ChangeSet.class);
          if (changeSetIds.contains(currentChangeSet.id())) {
            throw new MongobeeChangeSetException(String.format("Duplicated changeset id found: [%s]", currentChangeSet.id()));
          }
          changeSetIds.add(currentChangeSet.id());
          changeSetMethods.add(method);
        }
      }
    return changeSetMethods;
  }

}
