/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.internal.cache;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.gemstone.gemfire.internal.Assert;
import com.gemstone.gemfire.internal.cache.versions.VersionSource;
import com.gemstone.gemfire.internal.cache.versions.VersionTag;

/**
 * This class is used for sorting tombstones by region version number. Because
 * two tombstones with different members are not comparable, the iterator on
 * this class tries to return the tombstones in the order of the timestamps of the tombstones.
 * 
 * The class maintains a map, per member, of the tombstones sorted by the version
 * tag.
 * 
 * When removing entries, we pick from the sorted map that has the lowest timestamp.
 * 
 * This map is not threadsafe.
 * 
 * @author dsmith
 *
 */
public class OrderedTombstoneMap<T> {
  
  /**
   * A map of
   * member id-> sort map of version tag-> region entry
   * 
   */
  private Map<VersionSource, TreeMap<VersionTag, T>> tombstoneMap = new HashMap();
  
  /**
   * Add a new version tag to map
   */
  public void put(VersionTag tag, T entry) {
    //Add the version tag to the appropriate map
    VersionSource member = tag.getMemberID();
    TreeMap<VersionTag, T> memberMap = tombstoneMap.get(member);
    if(memberMap == null) {
      memberMap = new TreeMap<VersionTag, T>(new VersionTagComparator());
      tombstoneMap.put(member, memberMap);
    }
    T oldValue = memberMap.put(tag, entry);
    Assert.assertTrue(oldValue == null);
  }

  /**
   * Remove a version tag from the map.
   */
  public Map.Entry<VersionTag, T> take() {
    if(tombstoneMap.isEmpty()) {
      //if there are no more entries, return null;
      return null;
    } else {
      //Otherwise, look at all of the members and find the tag with the 
      //lowest timestamp.
      long lowestTimestamp = Long.MAX_VALUE;
      TreeMap<VersionTag, T> lowestMap = null;
      for(TreeMap<VersionTag, T> memberMap: tombstoneMap.values()) {
        VersionTag firstTag = memberMap.firstKey();
        long stamp = firstTag.getVersionTimeStamp();
        if(stamp < lowestTimestamp) {
          lowestTimestamp = stamp;
          lowestMap = memberMap;
        }
      }
      if(lowestMap == null) {
        return null;
      }
      //Remove the lowest entry
      Entry<VersionTag, T> result = lowestMap.firstEntry();
      lowestMap.remove(result.getKey());
      if(lowestMap.isEmpty()) {
        //if this is the last entry from a given member,
        //the map for that member
        tombstoneMap.remove(result.getKey().getMemberID());
      }
      
      return result;
    }
  }
  

  /**
   * A comparator that sorts version tags based on the region version, and
   * then on the timestamp.
   * @author dsmith
   *
   */
  public static class VersionTagComparator implements Comparator<VersionTag> {

    @Override
    public int compare(VersionTag o1, VersionTag o2) {
      long result = o1.getRegionVersion() - o2.getRegionVersion();
      if(result == 0) {
        result = o1.getVersionTimeStamp() - o2.getVersionTimeStamp();
      }
      return Long.signum(result);
    }
    
  }
}
